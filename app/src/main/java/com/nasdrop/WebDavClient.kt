package com.nasdrop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class FileCategory(val label: String, val icon: String) {
	All("全部", "📁"),
	Image("图片", "🖼️"),
	Video("视频", "🎬"),
	Document("文档", "📄"),
	Audio("音频", "🎵"),
	Archive("压缩包", "📦"),
}

data class DavEntry(
	val name: String,
	/** 相对 WebDAV 根的路径，不带首尾斜杠，例如 "Photos/2026" */
	val path: String,
	val isDir: Boolean,
	val size: Long,
	val modified: Long,
) {
	val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
	val sizeHuman: String get() {
		if (size < 0) return "--"
		val kb = size / 1024.0
		val mb = kb / 1024.0
		val gb = mb / 1024.0
		return when {
			gb >= 1.0 -> String.format(Locale.ROOT, "%.1f GB", gb)
			mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB", mb)
			kb >= 1.0 -> String.format(Locale.ROOT, "%.0f KB", kb)
			else -> "${size} B"
		}
	}
	/** 用于 UI 主标题展示的文件纯净名称（去除扩展名，避免与副标题格式如 PNG、MP4 重复显示） */
	val displayName: String get() {
		if (isDir) return name
		val dot = name.lastIndexOf('.')
		return if (dot > 0) name.substring(0, dot) else name
	}
	val isAlbum: Boolean get() = isDir && (name.contains("相册") || name.contains("Photo", ignoreCase = true) || name.contains("Album", ignoreCase = true))
	val isImage: Boolean get() = !isDir && ext in IMAGES
	val isVideo: Boolean get() = !isDir && ext in VIDEOS
	val isDocument: Boolean get() = !isDir && ext in DOCUMENTS
	val isAudio: Boolean get() = !isDir && ext in AUDIOS
	val isArchive: Boolean get() = !isDir && ext in ARCHIVES

	val category: FileCategory get() = when {
		isImage -> FileCategory.Image
		isVideo -> FileCategory.Video
		isDocument -> FileCategory.Document
		isAudio -> FileCategory.Audio
		isArchive -> FileCategory.Archive
		else -> FileCategory.All
	}

	companion object {
		val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg")
		val VIDEOS = setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "ts", "flv", "3gp", "rmvb", "wmv")
		val DOCUMENTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "log")
		val AUDIOS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "ape")
		val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
	}
}

class DavException(val code: Int, what: String) : IOException("$what（HTTP $code）")

/**
 * 极简 WebDAV 客户端：只做 NasDrop 需要的五个动作。
 *
 * base 既可能是真实服务器地址，也可能是 127.0.0.1 上的 bridge 端口——对这里完全透明，
 * 这正是把 Tailscale 关在 Go 侧换来的好处。
 */
class WebDavClient(
	private val base: HttpUrl,
	private val http: OkHttpClient,
	private val apiOrigin: HttpUrl? = null,
	private val apiUser: String = "",
	private val apiPassword: String = "",
) {
	/** OpenList 服务端缩略图：path(相对WebDAV根,无首尾斜杠) -> 带 sign 的 /d/...?type=thumb 直链 */
	private val thumbUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
	/** OpenList 原生直链签名：path -> sign（来自 fs/list），用于 /p/<path>?sign= 原图/原视频直链 */
	private val signUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

	/**
	 * 服务端真实的 WebDAV 根前缀（例如 "/dav"），从 PROPFIND 的 href 推断。
	 *
	 * 为什么需要：客户端填的 base 可能是反代路径（如 /nas/），而 MOVE 的 Destination
	 * 头不会被反代重写，必须写服务端自己的 WebDAV 根，否则 404。
	 */
	@Volatile private var davRootPrefix: String? = null
	@Volatile private var openListToken: String? = null
	/** 上次登录失败的时间戳：冷却期内不重复打卡，但过期后必须重试（见 ensureOpenListToken） */
	@Volatile private var thumbAuthFailedAt = 0L
	/**
	 * 缩略图批量就绪回调，参数是本次新增了缩略图直链的相对路径集合。
	 * ViewModel 收到后自增 Compose 状态触发重组——否则 thumbUrls 只是普通 Map，
	 * 异步补齐后 Compose 无从得知，表现为"缩略图明明加载了却不显示"。
	 */
	var onThumbnailsLoaded: ((Set<String>) -> Unit)? = null

	fun urlFor(path: String, dir: Boolean = false): HttpUrl {
		val b = base.newBuilder()
		path.trim('/').split('/').forEach { if (it.isNotEmpty()) b.addPathSegment(it) }
		if (dir && path.isNotEmpty()) b.addPathSegment("")
		return b.build()
	}

	/**
	 * OpenList 原生直链（/p/<path>?sign=xxx），用于查看器加载原图/原视频。
	 * 相比 WebDAV 的 /dav/ 路径，sign 直链无需每次带 Basic Auth，是标准 HTTP GET，
	 * Coil / ExoPlayer 的磁盘缓存、Range 续传命中率更高；慢速隧道下重复打开同一张图
	 * 能直接吃缓存，不再反复从服务端拉。
	 * 未拿到 sign（非 OpenList 服务/还没预取）时回退到 WebDAV urlFor，绝不返回 null。
	 */
	fun directUrlFor(path: String): HttpUrl {
		val sign = signUrls[path.trim('/')]
		if (!sign.isNullOrBlank()) {
			try {
				val b = apiOrigin!!.newBuilder()
					.addPathSegment("p")
				path.trim('/').split('/').forEach { if (it.isNotEmpty()) b.addPathSegment(it) }
				b.addQueryParameter("sign", sign)
				// origin 换成本客户端入口：直连模式=原域名；bridge 模式=127.0.0.1:port
				return b.build().newBuilder()
					.scheme(base.scheme).host(base.host).port(base.port)
					.build()
			} catch (_: Exception) {
				// 落到下面的 WebDAV 回退
			}
		}
		return urlFor(path)
	}

	/**
	 * 只返回**服务端真实生成**的缩略图直链（OpenList /d/...?type=thumb），没有就是 null。
	 *
	 * 与 [thumbnailUrl] 的区别：后者对图片会回退到原图地址，那是给列表网格用的；
	 * 看图器如果拿到"回退后的原图地址"，就会在缩略图垫底层和原图层**各下一次原图**
	 * （/dav/ 与 /p/ 两个不同 URL，缓存互不命中），白白翻倍流量、拖慢清晰时间。
	 */
	fun serverThumbUrl(path: String): HttpUrl? = thumbUrls[path.trim('/')]?.toHttpUrlOrNull()

	/**
	 * 列表缩略图专用：命中服务端缩略图返回真图片直链，否则图片回退原图地址（视频返回 null）。
	 * 下载/播放/删除等仍走 urlFor()，绝不受影响。
	 */
	fun thumbnailUrl(path: String): HttpUrl? {
		thumbUrls[path.trim('/')]?.toHttpUrlOrNull()?.let { return it }
		// 图片没有服务端缩略图时回退原图（图片本身体积小）；
		// 视频没有服务端缩略图时返回 null——绝不让 Coil 去拉取整个视频当图片解码。
		val ext = path.substringAfterLast('.', "").lowercase()
		return if (ext in DavEntry.IMAGES) urlFor(path) else null
	}

	/**
	 * 元数据请求专用：20 秒硬截止（call 级 deadline）。
	 * 全局 okHttp 的 readTimeout 是 5 分钟（为大文件传输留的），列目录/删改/缩略图
	 * 这类小请求一旦继承它，隧道抖动时页面就会假死好几分钟。这里单独收紧。
	 */
	private fun metaCall(req: Request) = http.newCall(req).also {
		it.timeout().timeout(20, TimeUnit.SECONDS)
	}

	/** 列目录。path 为空表示根。只做 PROPFIND，绝不在此阻塞拉缩略图。 */
	fun list(path: String): List<DavEntry> {
		val req = Request.Builder()
			.url(urlFor(path, dir = true))
			.header("Depth", "1")
			.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
			.build()

		metaCall(req).execute().use { resp ->
			if (!resp.isSuccessful) throw DavException(resp.code, "列目录失败")
			val stream = resp.body?.byteStream() ?: throw DavException(resp.code, "空响应")
			return parse(stream, path)
		}
	}

	/**
	 * 轻量连通性探测：只发一个 Depth:0 的 PROPFIND，8 秒硬截止。
	 * 专供设置页「测试连通性」使用——不列目录、不拉缩略图、不改任何连接状态。
	 */
	fun probe() {
		val req = Request.Builder()
			.url(urlFor("", dir = true))
			.header("Depth", "0")
			.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
			.build()
		val call = http.newCall(req)
		call.timeout().timeout(8, TimeUnit.SECONDS)
		call.execute().use { resp ->
			// WebDAV 成功一般是 207 Multi-Status；2xx/3xx/4xx 都算"服务器可达且协议正常"
			if (resp.code !in 200..499) throw DavException(resp.code, "服务器返回异常状态 ${resp.code}")
		}
	}

	/**
	 * 通过 OpenList 私有 API 拉取当前目录的缩略图直链，建立 path -> thumbUrl 映射。
	 * 由 ViewModel 在列目录拿到数据、UI 已经渲染出来之后，放到后台线程调用，
	 * 这样慢速隧道里"列目录"和"缩略图"两条链路互不阻塞（这是缩略图慢的根因之一）。
	 * 纯增强：任何失败（非 OpenList 服务、无网络、无缩略图）都静默跳过，列表照常显示。
	 */
	fun prefetchThumbnails(dirPath: String, entries: List<DavEntry>) {
		val origin = apiOrigin ?: return
		// 只为包含图片/视频的目录发起请求，纯文档目录零开销
		if (entries.none { it.isImage || it.isVideo }) return
		try {
			val token = ensureOpenListToken(origin) ?: return
			val apiPath = if (dirPath.isEmpty()) "/" else "/$dirPath"
			val body = """{"path":"${jsonEscape(apiPath)}","password":""}"""
				.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
			val req = Request.Builder()
				.url(origin.newBuilder().addPathSegment("api").addPathSegment("fs").addPathSegment("list").build())
				.header("Authorization", token)
				.post(body)
				.build()
			metaCall(req).execute().use { resp ->
				// token 过期：立刻作废，下次 prefetch 会重新登录，避免整个会话再无缩略图
				if (resp.code == 401) openListToken = null
				if (!resp.isSuccessful) return
				val text = resp.body?.string() ?: return
				val content = org.json.JSONObject(text).optJSONObject("data")?.optJSONArray("content") ?: return
				val prefix = if (dirPath.isEmpty()) "" else "$dirPath/"
				val added = HashSet<String>()
				for (i in 0 until content.length()) {
					val item = content.optJSONObject(i) ?: continue
					val name = item.optString("name")
					val thumb = item.optString("thumb")
					val key = prefix + name
					// 顺带缓存原生直链签名：查看器原图/视频用它走 /p/?sign=，无 auth、缓存友好
					item.optString("sign").takeIf { it.isNotBlank() }?.let { signUrls[key] = it }
					if (name.isNotEmpty() && thumb.startsWith("http")) {
						// thumb 直链是服务端返回的公网绝对地址。统一把 origin 换成本客户端的
						// base origin：直连模式=原域名；bridge 模式=127.0.0.1:port。
						// 否则 bridge 模式下 Coil 会拿公网域名直连 100.x 而失败。
						val key = prefix + name
						thumbUrls[key] = rewriteThumbOrigin(thumb)
						added += key
					}
				}
				if (added.isNotEmpty()) onThumbnailsLoaded?.invoke(added)
			}
		} catch (_: Exception) {
			// 缩略图是可选增强，任何异常都不影响主流程
		}
	}

	/** 登录 OpenList 换 token；token 进程内复用。失败只进入 30s 冷却，冷却结束后一定会重试。 */
	@Synchronized
	private fun ensureOpenListToken(origin: HttpUrl): String? {
		openListToken?.let { return it }
		if (apiUser.isEmpty()) return null
		val now = System.currentTimeMillis()
		if (now - thumbAuthFailedAt < 30_000L) return null
		thumbAuthFailedAt = now
		return try {
			val body = """{"username":"${jsonEscape(apiUser)}","password":"${jsonEscape(apiPassword)}"}"""
				.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
			val req = Request.Builder()
				.url(origin.newBuilder().addPathSegment("api").addPathSegment("auth").addPathSegment("login").build())
				.post(body)
				.build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val token = org.json.JSONObject(resp.body?.string() ?: return null)
					.optJSONObject("data")?.optString("token")
				if (!token.isNullOrEmpty()) {
					openListToken = token
					token
				} else null
			}
		} catch (_: Exception) {
			null
		}
	}

	private fun rewriteThumbOrigin(thumb: String): String {
		return try {
			val t = thumb.toHttpUrlOrNull() ?: return thumb
			t.newBuilder()
				.scheme(base.scheme)
				.host(base.host)
				.port(base.port)
				.build()
				.toString()
		} catch (_: Exception) {
			thumb
		}
	}

	private fun jsonEscape(s: String) = s
		.replace("\\", "\\\\")
		.replace("\"", "\\\"")
		.replace("\n", "\\n")
		.replace("\r", "\\r")

	fun mkcol(path: String) = exec("MKCOL", urlFor(path, dir = true))

	fun delete(path: String, isDir: Boolean = false) {
		val url = urlFor(path, dir = isDir)
		val req = Request.Builder()
			.url(url)
			.method("DELETE", null)
			.header("Depth", "infinity")
			.build()
		metaCall(req).execute().use { resp ->
			// 200, 204 为成功；404 表示文件在远端已不存在，也视为成功
			if (!resp.isSuccessful && resp.code != 404) {
				throw DavException(resp.code, "删除失败: HTTP ${resp.code}")
			}
		}
	}

	/**
	 * 移动 / 重命名。
	 *
	 * Destination 必须是**服务端视角**的绝对路径，而且主机名不能带（实测：带上任何
	 * 与请求 Host 不一致的主机会被 nginx 直接 502）。
	 *
	 * 更坑的是路径前缀：客户端配置的 base 可能是反代路径（实测这台是
	 * https://nas.205002.xyz/nas/，nginx 会把 /nas/ 重写成 /dav/），
	 * 但 **nginx 只重写请求行，不重写 Destination 头**。所以 Destination 写
	 * /nas/movies/x 会 404（实测），必须写服务端真实的 WebDAV 根 /dav/movies/x（实测 201）。
	 * 这里用列目录时从 href 推断出的真实前缀，推断不到才退回 base 自身路径。
	 */
	fun move(from: String, to: String) {
		val prefix = (davRootPrefix ?: base.encodedPath).trimEnd('/')
		val destination = "$prefix/" + to.trim('/')
		val req = Request.Builder()
			.url(urlFor(from))
			.method("MOVE", null)
			.header("Destination", destination)
			.header("Overwrite", "T")
			.build()
		metaCall(req).execute().use {
			if (!it.isSuccessful) throw DavException(it.code, "移动失败")
		}
	}

	/**
	 * 下载指定路径的文件并写入本地目标输出流，支持断点续传（Range 请求）。
	 */
	fun download(
		path: String,
		output: java.io.OutputStream,
		startByte: Long = 0L,
		onProgress: (Long, Long) -> Unit,
	) {
		val reqBuilder = Request.Builder().url(urlFor(path)).get()
		if (startByte > 0L) {
			reqBuilder.header("Range", "bytes=$startByte-")
		}
		val req = reqBuilder.build()
		http.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful && resp.code != 206) throw DavException(resp.code, "下载失败")
			val body = resp.body ?: throw DavException(resp.code, "响应体为空")
			val contentLength = body.contentLength()
			val total = if (resp.code == 206) contentLength + startByte else if (contentLength > 0) contentLength else -1L
			body.byteStream().use { ins ->
				val buf = ByteArray(64 * 1024)
				var sent = if (resp.code == 206) startByte else 0L
				while (true) {
					val n = ins.read(buf)
					if (n <= 0) break
					output.write(buf, 0, n)
					sent += n
					onProgress(sent, total)
				}
				output.flush()
			}
		}
	}

	/**
	 * 上传一个 content:// 或 file:// 的 Uri 到 remoteDir 下，保留原文件名。
	 * 进度回调 (已发送, 总大小)，总大小未知时为 -1。
	 *
	 * [onBodySent] 在**全部字节写进 socket** 的那一刻回调一次（只回调一次）。
	 * 它与"服务端返回 201"之间可能隔着一段落盘/rename 的时间，UI 用它在 100% 时
	 * 立刻把文件乐观显示出来，而不是卡在"等待服务端写入"。
	 */
	fun upload(
		context: Context,
		uri: Uri,
		remoteDir: String,
		onProgress: (Long, Long) -> Unit,
		onBodySent: () -> Unit = {},
	) {
		val name = Query.nameOf(context, uri)
		val total = Query.sizeOf(context, uri)
		val path = listOf(remoteDir.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/")
		val bodySentOnce = java.util.concurrent.atomic.AtomicBoolean(false)

		// body 工厂：每次 PUT 调用都新建（重试时需要重新读 Uri 输入流）。
		// written 记录"这次尝试实际写出去多少字节"——决定失败后能不能安全重传，
		// 见下面 putOnce 的注释。
		fun newBody(written: java.util.concurrent.atomic.AtomicLong) = object : RequestBody() {
			override fun contentType() = context.contentResolver.getType(uri)?.toMediaTypeOrNull()
			override fun contentLength() = total
			override fun writeTo(sink: BufferedSink) {
				val input = context.contentResolver.openInputStream(uri)
					?: throw IOException("无法读取：$uri")
				var sent = 0L
				input.use { ins ->
					val buf = ByteArray(64 * 1024)
					var lastReport = 0L
					while (true) {
						val n = ins.read(buf)
						if (n <= 0) break
						sink.write(buf, 0, n)
						sent += n
						written.addAndGet(n.toLong())
						// 进度节流：每 1MB 或结束时才回调，避免每 64KB 一次
						// Uploads.update（Compose 状态写入）拖慢上传、卡顿界面。
						if (sent - lastReport >= 1024 * 1024 || (total > 0 && sent >= total)) {
							lastReport = sent
							onProgress(sent, total)
						}
					}
				}
				if (total <= 0 || sent >= total) {
					if (bodySentOnce.compareAndSet(false, true)) onBodySent()
				}
			}
		}

		val url = urlFor(path)

		/**
		 * 执行一次 PUT，返回 (是否成功, 本次已写出字节数, HTTP 码；0 表示传输层失败)。
		 *
		 * 已写出字节数是关键：旧实现把所有 IOException 都当成"可能缺父目录"从而无条件
		 * 重传一遍，于是一个传到 99% 断掉的视频会**从头再传一次**——用户看到的就是
		 * "传完了又从头开始/传完还要等很久"。现在只有"服务端明确 409"或"一个字节都没
		 * 写出去"（连接是死的）才重试，写过数据就如实失败，让用户决定而不是偷偷重传。
		 */
		fun putOnce(): PutResult {
			val written = java.util.concurrent.atomic.AtomicLong(0)
			return try {
				http.newCall(Request.Builder().url(url).put(newBody(written)).build()).execute().use {
					when {
						it.isSuccessful -> PutResult(true, written.get(), it.code)
						it.code == 409 -> PutResult(false, written.get(), 409)
						else -> throw DavException(it.code, "上传失败")
					}
				}
			} catch (e: DavException) {
				if (e.code == 409) PutResult(false, written.get(), 409) else throw e
			} catch (e: java.io.IOException) {
				PutResult(false, written.get(), 0)
			}
		}

		// 大文件先确认父目录存在。
		// 关键：WebDAV 的 409 是服务端判断出来的，但很多实现要**把整个 body 收完**才回
		// 409。几十上百 MB 的视频因此会白传一遍、再建目录重传一遍，等于双倍时间——
		// 用户感知就是"传了很久，又从头开始"。小文件不预检，省掉这次 RTT。
		if (total <= 0 || total >= 8L * 1024 * 1024) {
			runCatching { ensureParentDirs(remoteDir) }
		}

		// 先直接 PUT（正常目录零额外开销）；只有"明确 409"或"一个字节都没发出去"才重试。
		val first = putOnce()
		if (!first.ok) {
			val retryable = first.code == 409 || first.written < 1L * 1024 * 1024
			if (!retryable) {
				// 已经传了实打实的数据再断：重传等于让用户重新等一遍，不如如实报错。
				throw IOException("传输中断（已发送 ${first.written / 1024 / 1024}MB），请重试")
			}
			ensureParentDirs(remoteDir)
			val second = putOnce()
			if (!second.ok) {
				throw DavException(second.code.takeIf { it > 0 } ?: 409, "上传失败：父目录创建后仍失败")
			}
		}
	}

	/** 单次 PUT 的结果：成功与否、已写出字节数、HTTP 码（0 = 传输层异常） */
	private class PutResult(val ok: Boolean, val written: Long, val code: Int)

	/** 逐级创建远端父目录：MKCOL 每级路径，405（已存在）/409 都算成功。空目录直接返回。 */
	private fun ensureParentDirs(remoteDir: String) {
		val parts = remoteDir.trim('/').split('/').filter { it.isNotEmpty() }
		var cur = ""
		for (part in parts) {
			cur = if (cur.isEmpty()) part else "$cur/$part"
			try {
				exec("MKCOL", urlFor(cur, dir = true))
			} catch (e: DavException) {
				// 405 Method Not Allowed = 目录已存在；部分实现返回 409。都视为已就绪。
				if (e.code != 405 && e.code != 409) throw e
			}
		}
	}

	private fun exec(method: String, url: HttpUrl) {
		val req = Request.Builder().url(url).method(method, null).build()
		metaCall(req).execute().use {
			if (!it.isSuccessful) throw DavException(it.code, "$method 失败")
		}
	}

	private fun parse(input: java.io.InputStream, basePath: String): List<DavEntry> {
		val parser = Xml.newPullParser()
		parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
		parser.setInput(input, null)

		val targetRel = basePath.trim('/')
		val out = ArrayList<DavEntry>()

		// 先收集本页所有 <response>，再统一决定如何剥离 href 前缀。
		// 不能直接用配置里 base 的路径当前缀：AList/OpenList 的 WebDAV 固定把根回报成
		// /dav/…，而客户端 base 可能写的是 /nas/（反代路径）或 /dav/。硬用 base 路径去
		// 剥会剥不掉，于是每个条目的 path 都带上一个多余的 "dav/"，后续删除/下载/进入
		// 子目录就会拼成 /nas/dav/xxx → 404。这里改为从响应自身推断真实前缀。
		val responses = ArrayList<Raw>()

		var href: String? = null
		var isDir = false
		var size = -1L
		var modified = 0L
		var inResponse = false

		var event = parser.eventType
		while (event != XmlPullParser.END_DOCUMENT) {
			if (event == XmlPullParser.START_TAG) {
				when (parser.name) {
					"response" -> {
						inResponse = true; href = null; isDir = false; size = -1L; modified = 0L
					}
					"href" -> if (inResponse && href == null) href = parser.nextText()
					"collection" -> isDir = true
					"getcontentlength" -> size = parser.nextText().trim().toLongOrNull() ?: -1L
					"getlastmodified" -> modified = parseDate(parser.nextText())
				}
			} else if (event == XmlPullParser.END_TAG && parser.name == "response" && inResponse) {
				responses += Raw(href, isDir, size, modified)
				inResponse = false
			}
			event = parser.next()
		}

		// 推断服务端 WebDAV 根的 href 前缀。 Depth:1 的响应里，第一条通常是目录自身
		// （如 /dav/ 或 /dav/Photos/）；取所有 href 的最长公共目录前缀即可得到一个稳定的根。
		// 更稳妥的做法：找到"目录自身"那条（其相对路径恰好等于 targetRel），
		// 它去掉 targetRel 后的部分就是根前缀。找不到时再退回最长公共前缀。
		val decodedHrefs = responses.mapNotNull { it.href }.map { decode(it) }
		val rootPrefix = inferRootPrefix(decodedHrefs, targetRel, base.encodedPath)
		// 记住服务端真实的 WebDAV 根（如 /dav），MOVE 的 Destination 要用它
		if (rootPrefix.isNotBlank()) davRootPrefix = rootPrefix.trimEnd('/')

		for (r in responses) {
			val raw = r.href
			if (raw != null) {
				val decoded = decode(raw)
				val trimmed = decoded.trimEnd('/')
				val rel = when {
					rootPrefix.isNotEmpty() && trimmed == rootPrefix -> "" // 目录自身
					rootPrefix.isNotEmpty() && trimmed.startsWith("$rootPrefix/") ->
						trimmed.removePrefix("$rootPrefix/")
					else -> decoded.trim('/')
				}
				// 排除空路径以及当前所请求目录自身
				if (rel.isNotEmpty() && rel != targetRel) {
					out += DavEntry(
						name = rel.substringAfterLast('/'),
						path = rel,
						isDir = r.isDir || decoded.endsWith("/"),
						size = r.size,
						modified = r.modified,
					)
				}
			}
		}

		return out.sortedWith(
			compareByDescending<DavEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) },
		)
	}

	/**
	 * 从 PROPFIND 响应推断服务端的 WebDAV 根前缀（形如 "/dav"，不带结尾斜杠）。
	 *
	 * 优先路径：找一条 href，其解码后路径去掉尾斜杠，恰好以 "/$targetRel" 结尾，
	 * 那么把它去掉 "/$targetRel" 就是根。这样 AList/OpenList(/dav)、原生 WebDAV(/nas)、
	 * 甚至挂在子路径的服务都能自动适配，且不受配置里 base 写什么影响。
	 * 兜底：取所有 href 的最长公共路径段前缀；再兜底用 base 的编码路径。
	 */
	private fun inferRootPrefix(hrefs: List<String>, targetRel: String, basePath: String): String {
		fun norm(p: String) = p.trim().trimEnd('/').let { if (it.isEmpty()) "/" else it }

		// 1) 目录自身那条：/dav/Photos → 去掉 /Photos 得到 /dav
		if (targetRel.isNotEmpty()) {
			val suffix = "/$targetRel"
			hrefs.firstOrNull { h ->
				val n = norm(h)
				n == suffix || n.endsWith(suffix)
			}?.let { h ->
				val n = norm(h)
				val cut = n.removeSuffix(suffix).trimEnd('/')
				if (cut.isNotEmpty()) return cut
			}
		} else {
			// 根目录：目录自身的 href 就是根，取层级最浅的一条
			hrefs.map { norm(it) }.minByOrNull { it.count { c -> c == '/' } }?.let { return it }
		}

		// 2) 最长公共段前缀
		val paths = hrefs.map { norm(it).split('/').filter { it.isNotEmpty() } }
		if (paths.isNotEmpty()) {
			val first = paths.first()
			var common = first.size
			for (p in paths) {
				var i = 0
				while (i < common && i < p.size && first[i] == p[i]) i++
				common = i
			}
			if (common > 0) return "/" + first.take(common).joinToString("/")
		}

		// 3) 退回 base 路径
		return norm(basePath)
	}

	private fun decode(raw: String): String = try {
		URI(raw).path ?: raw
	} catch (_: Exception) {
		raw
	}

	private fun parseDate(raw: String): Long = try {
		SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(raw.trim())?.time ?: 0L
	} catch (_: Exception) {
		0L
	}

	companion object {
		private data class Raw(val href: String?, val isDir: Boolean, val size: Long, val modified: Long)

		private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop>
<d:resourcetype/><d:getcontentlength/><d:getlastmodified/>
</d:prop></d:propfind>"""
	}
}

internal object Query {

	fun nameOf(context: Context, uri: Uri): String {
		if (uri.scheme == "file") return uri.lastPathSegment ?: "unnamed"
		runCatching {
			context.contentResolver
				.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
				?.use { c ->
					val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (i >= 0 && c.moveToFirst()) {
						c.getString(i)?.let { return it }
					}
				}
		}
		return uri.lastPathSegment?.substringAfterLast('/') ?: "unnamed"
	}

	fun sizeOf(context: Context, uri: Uri): Long {
		runCatching {
			context.contentResolver
				.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
				?.use { c ->
					val i = c.getColumnIndex(OpenableColumns.SIZE)
					if (i >= 0 && c.moveToFirst() && !c.isNull(i)) {
						return c.getLong(i)
					}
				}
		}
		return -1L
	}
}
