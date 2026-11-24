package com.nasdrop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

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
	val size: Long = 0L,
	val modified: Long = System.currentTimeMillis(),
	val takenAt: Long = 0L,
	val created: Long = 0L,
) {
	/**
	 * 对象的真实创建/拍摄时间：
	 * 优先 拍摄时间(EXIF/文件名时间) > 服务端创建时间(creationdate) > 兜底修改/上传时间(modified)
	 */
	val effectiveDate: Long get() = when {
		takenAt > 0L -> takenAt
		else -> PhotoTimeParser.parseFromFilename(name) ?: (if (created > 0L) created else modified)
	}
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
		val DOCUMENTS = setOf("pdf", "doc", "docx", "xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "ppt", "pptx", "txt", "md", "csv", "tsv", "json", "xml", "log", "wps", "et", "dps", "rtf", "odt", "ods", "odp")
		val AUDIOS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "ape")
		val ARCHIVES = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
	}
}

/** 保留可定位的 HTTP 码，同时给出用户可以采取的恢复操作。 */
class DavException(val code: Int, what: String) : IOException(
	when (code) {
		401 -> "认证失败，请检查账号或密码（HTTP 401）"
		403 -> "当前账号没有操作权限，请检查账号权限或目标目录（HTTP 403）"
		404 -> "$what：文件或目录不存在，请检查地址或刷新重试（HTTP 404）"
		507 -> "NAS 存储空间不足，请清理空间后重试（HTTP 507）"
		else -> "$what（HTTP $code）"
	},
)

/** 续传响应无法安全追加；调用方必须关闭并清空临时文件，再从零下载。 */
class ResumeRestartRequiredException(val code: Int, what: String) : IOException("$what（HTTP $code）")

/** 显式取消必须直接结束传输，不能落入网络失败的重试分支。 */
class TransferCanceledException(cause: IOException? = null) : IOException("传输已取消", cause)

/**
 * WebDAV 客户端：封装文件操作、流式传输和连接检查。
 *
 * base 既可能是真实服务器地址，也可能是 127.0.0.1 上的 bridge 端口——对这里完全透明，
 * 这正是把 Tailscale 关在 Go 侧换来的好处。
 */
class WebDavClient(
	val base: HttpUrl,
	private val http: OkHttpClient,
	private val apiOrigin: HttpUrl? = null,
	private val apiUser: String = "",
	private val apiPassword: String = "",
) {
	val baseUrl: HttpUrl get() = base
	/** OpenList 服务端缩略图：path(相对WebDAV根,无首尾斜杠) -> 带 sign 的 /d/...?type=thumb 直链 */
	private val thumbUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
	/** OpenList 原生直链签名：path -> sign（来自 fs/list），用于 /p/<path>?sign= 原图/原视频直链 */
	private val signUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
	/** 记录加载失败的缩略图 key，避免每次滑动重复尝试拉取已失效的缩略图 */
	private val failedThumbs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

	fun markThumbnailFailed(path: String) {
		val key = path.trim('/')
		failedThumbs.add(key)
		thumbUrls.remove(key)
	}

	fun unmarkThumbnailFailed(path: String) {
		val key = path.trim('/')
		failedThumbs.remove(key)
	}

	fun authHeaderValue(): String? {
		if (apiUser.isNotEmpty() && apiPassword.isNotEmpty()) {
			return okhttp3.Credentials.basic(apiUser, apiPassword)
		}
		return null
	}

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

	/**
	 * 实际生效的 WebDAV 根地址：若配置未带 /dav/ 但服务端要求 /dav/（如 OpenList/AList），
	 * 或配置带了 /dav/ 但服务端是原生 WebDAV，握手自适应后会更新此地址，实现双向自动适配。
	 */
	@Volatile var effectiveBase: HttpUrl = base
		private set

	/**
	 * 智能 /dav 路径自适应：
	 * 当根路径 PROPFIND 返回 404/405 时，若当前 base 不带 /dav 则尝试追加 /dav/；若带 /dav 则尝试去掉 /dav/。
	 * 握手成功后更新 effectiveBase，彻底自适应 OpenList/AList 与原生 WebDAV 的路径差异。
	 */
	private fun tryAlternativeDavUrl(): HttpUrl? {
		val encoded = effectiveBase.encodedPath
		return if (encoded.endsWith("/dav/") || encoded.endsWith("/dav")) {
			// 当前带有 /dav，尝试去掉 /dav/
			val segments = effectiveBase.pathSegments.filter { it.isNotEmpty() && it != "dav" }
			val builder = effectiveBase.newBuilder().encodedPath("/")
			segments.forEach { builder.addPathSegment(it) }
			builder.addPathSegment("")
			val newBase = builder.build()
			if (newBase != effectiveBase) newBase else null
		} else {
			// 当前不带 /dav，尝试追加 /dav/
			val builder = effectiveBase.newBuilder()
			if (!effectiveBase.encodedPath.endsWith("/")) builder.addPathSegment("")
			builder.addPathSegment("dav").addPathSegment("")
			builder.build()
		}
	}

	fun urlFor(path: String, dir: Boolean = false): HttpUrl {
		val b = effectiveBase.newBuilder()
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
				val rootBase = (apiOrigin ?: effectiveBase).newBuilder().encodedPath("/").build()
				val b = rootBase.newBuilder().addPathSegment("p")
				path.trim('/').split('/').forEach { if (it.isNotEmpty()) b.addPathSegment(it) }
				b.addQueryParameter("sign", sign)
				// origin 换成本客户端入口：直连模式=原域名；bridge 模式=127.0.0.1:port
				return b.build().newBuilder()
					.scheme(effectiveBase.scheme).host(effectiveBase.host).port(effectiveBase.port)
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
	fun serverThumbUrl(path: String): HttpUrl? {
		val key = path.trim('/')
		if (failedThumbs.contains(key)) return null
		return thumbUrls[key]?.toHttpUrlOrNull()
	}

	/**
	 * 列表缩略图专用：命中服务端缩略图返回真图片直链（20KB~30KB），绝不直接拉取几十MB原图。
	 * 下载/播放/删除/查看器等仍走 urlFor() / directUrlFor()，清晰度不受影响。
	 */
	fun thumbnailUrl(path: String): HttpUrl? {
		val key = path.trim('/')
		if (failedThumbs.contains(key)) return null
		thumbUrls[key]?.toHttpUrlOrNull()?.let { return it }
		val ext = path.substringAfterLast('.', "").lowercase()
		if (ext !in DavEntry.IMAGES && ext !in DavEntry.VIDEOS) return null

		// 服务端轻量缩略图端点：/api/thumbnail?path=<key>&dim=320
		val b = effectiveBase.newBuilder().encodedPath("/api/thumbnail")
		b.addQueryParameter("path", key)
		b.addQueryParameter("dim", "320")
		return b.build()
	}

	/**
	 * 元数据请求专用：20 秒硬截止（call 级 deadline）。
	 * 全局 okHttp 的 readTimeout 是 5 分钟（为大文件传输留的），列目录/删改/缩略图
	 * 这类小请求一旦继承它，隧道抖动时页面就会假死好几分钟。这里单独收紧。
	 */
	internal fun metaCall(req: Request) = http.newCall(req).also {
		it.timeout().timeout(20, TimeUnit.SECONDS)
	}

	var isNasDropServer: Boolean = false
		private set

	fun checkServerCapability(): Boolean {
		return try {
			val pingUrl = effectiveBase.newBuilder().encodedPath("/api/ping").build()
			val req = Request.Builder().url(pingUrl).get().build()
			metaCall(req).execute().use { resp ->
				if (resp.isSuccessful) {
					val body = resp.body?.string().orEmpty()
					isNasDropServer = body.contains("nasdrop-server")
					isNasDropServer
				} else {
					isNasDropServer = false
					false
				}
			}
		} catch (_: Exception) {
			isNasDropServer = false
			false
		}
	}

	fun reportMetadata(metadata: List<PhotoMetadata>): Boolean {
		return try {
			val json = org.json.JSONArray()
			for (m in metadata) {
				val obj = org.json.JSONObject().apply {
					put("path", m.path)
					put("name", m.name)
					put("dir", m.dir)
					put("size", m.size)
					put("modified", m.modified)
					put("taken_at", m.takenAt)
					put("width", m.width)
					put("height", m.height)
					put("has_thumb", m.hasThumbnail)
				}
				json.put(obj)
			}
			val targetUrl = effectiveBase.newBuilder().encodedPath("/api/metadata").build()
			val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
			val req = Request.Builder()
				.url(targetUrl)
				.post(body)
				.build()
			metaCall(req).execute().use { it.isSuccessful }
		} catch (_: Exception) {
			false
		}
	}

	fun fetchServerTimeline(limit: Int = 100, offset: Int = 0, dir: String = ""): List<DavEntry>? {
		return try {
			val urlBuilder = effectiveBase.newBuilder().encodedPath("/api/timeline")
				.addQueryParameter("limit", limit.toString())
				.addQueryParameter("offset", offset.toString())
			if (dir.isNotBlank()) urlBuilder.addQueryParameter("dir", dir)

			val req = Request.Builder().url(urlBuilder.build()).get().build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val body = resp.body?.string() ?: return null
				val obj = org.json.JSONObject(body)
				val items = obj.optJSONArray("items") ?: return emptyList()
				val list = mutableListOf<DavEntry>()
				for (i in 0 until items.length()) {
					val item = items.getJSONObject(i)
					val p = item.getString("path")
					val n = item.getString("name")
					val s = item.optLong("size", 0L)
					val m = item.optLong("modified", 0L)
					val t = item.optLong("taken_at", 0L)
					list.add(DavEntry(name = n, path = p, isDir = false, size = s, modified = m, takenAt = t))
				}
				list
			}
		} catch (_: Exception) {
			null
		}
	}

	fun fetchServerMemories(date: String = "", limit: Int = 300): List<DavEntry>? {
		return try {
			val urlBuilder = effectiveBase.newBuilder().encodedPath("/api/memories")
				.addQueryParameter("limit", limit.toString())
			if (date.isNotBlank()) urlBuilder.addQueryParameter("date", date)

			val req = Request.Builder().url(urlBuilder.build()).get().build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val body = resp.body?.string() ?: return null
				val obj = org.json.JSONObject(body)
				val items = obj.optJSONArray("items") ?: return emptyList()
				val list = mutableListOf<DavEntry>()
				for (i in 0 until items.length()) {
					val item = items.getJSONObject(i)
					val p = item.getString("path")
					val n = item.getString("name")
					val s = item.optLong("size", 0L)
					val m = item.optLong("modified", 0L)
					val t = item.optLong("taken_at", 0L)
					list.add(DavEntry(name = n, path = p, isDir = false, size = s, modified = m, takenAt = t))
				}
				list
			}
		} catch (_: Exception) {
			null
		}
	}

	/** 获取 NAS 宿主机系统资源与运行状态指标（CPU、内存、存储池空间、运行时间、主机名等） */
	fun fetchSystemInfo(): NasSystemInfo? {
		return try {
			val url = effectiveBase.newBuilder().encodedPath("/api/system").build()
			val req = Request.Builder().url(url).get().build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val body = resp.body?.string() ?: return null
				val obj = org.json.JSONObject(body)
				val memObj = obj.optJSONObject("memory")
				val diskObj = obj.optJSONObject("disk")
				val loadAvgArr = obj.optJSONArray("load_avg")
				val loadAvg = mutableListOf<Double>()
				if (loadAvgArr != null) {
					for (i in 0 until loadAvgArr.length()) {
						loadAvg.add(loadAvgArr.optDouble(i, 0.0))
					}
				}
				NasSystemInfo(
					service = obj.optString("service", "nasdrop-server"),
					version = obj.optString("version", ""),
					hostname = obj.optString("hostname", ""),
					os = obj.optString("os", ""),
					arch = obj.optString("arch", ""),
					cpuCores = obj.optInt("cpu_cores", 0),
					cpuPercent = obj.optDouble("cpu_percent", 0.0),
					loadAvg = loadAvg,
					uptimeSeconds = obj.optLong("uptime_seconds", 0L),
					serverUptimeSeconds = obj.optLong("server_uptime_seconds", 0L),
					memory = NasSystemInfo.MemoryMetrics(
						total = memObj?.optLong("total", 0L) ?: 0L,
						used = memObj?.optLong("used", 0L) ?: 0L,
						available = memObj?.optLong("available", 0L) ?: 0L,
						percent = memObj?.optDouble("percent", 0.0) ?: 0.0,
					),
					disk = NasSystemInfo.DiskMetrics(
						path = diskObj?.optString("path", "") ?: "",
						total = diskObj?.optLong("total", 0L) ?: 0L,
						used = diskObj?.optLong("used", 0L) ?: 0L,
						free = diskObj?.optLong("free", 0L) ?: 0L,
						percent = diskObj?.optDouble("percent", 0.0) ?: 0.0,
					),
				)
			}
		} catch (_: Exception) {
			null
		}
	}

	/** 列目录。path 为空表示根。只做 PROPFIND，绝不在此阻塞拉缩略图。 */
	fun list(path: String): List<DavEntry> {
		val req = Request.Builder()
			.url(urlFor(path, dir = true))
			.header("Depth", "1")
			.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
			.build()

		metaCall(req).execute().use { resp ->
			if (!resp.isSuccessful) {
				// 根目录 (path 为空) 遇到 404/405 时触发 /dav 智能双向自适应探测
				if (path.isEmpty() && (resp.code == 404 || resp.code == 405)) {
					val altBase = tryAlternativeDavUrl()
					if (altBase != null) {
						val altReq = Request.Builder()
							.url(altBase)
							.header("Depth", "1")
							.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
							.build()
						try {
							metaCall(altReq).execute().use { altResp ->
								if (altResp.isSuccessful) {
									val stream = altResp.body?.byteStream() ?: throw DavException(altResp.code, "空响应")
									effectiveBase = altBase
									logger.info("已自动适配 WebDAV 路径前缀为: $altBase")
									return parse(stream, path)
								}
							}
						} catch (_: Exception) {
							// 自适应探测失败，继续向下抛出原 resp 异常
						}
					}
				}
				throw DavException(resp.code, "列目录失败")
			}
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
			if (!resp.isSuccessful) {
				if (resp.code == 404 || resp.code == 405) {
					val altBase = tryAlternativeDavUrl()
					if (altBase != null) {
						val altReq = Request.Builder()
							.url(altBase)
							.header("Depth", "0")
							.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
							.build()
						try {
							val altCall = http.newCall(altReq)
							altCall.timeout().timeout(8, TimeUnit.SECONDS)
							altCall.execute().use { altResp ->
								if (altResp.isSuccessful) {
									effectiveBase = altBase
									logger.info("探测时自动适配 WebDAV 路径前缀为: $altBase")
									return
								}
							}
						} catch (_: Exception) {}
					}
				}
				val reason = when (resp.code) {
					401 -> "认证失败，请检查用户名和密码"
					403 -> "当前账号无权访问 WebDAV"
					404 -> "WebDAV 路径不存在，请检查服务器地址"
					else -> "WebDAV 连通性测试失败"
				}
				logger.warning("WebDAV 探测失败 code=${resp.code} reason=$reason")
				throw DavException(resp.code, reason)
			}
		}
	}

	/** 仅查询目标是否存在；只有明确的 404 表示不存在，权限和网络错误交由调用方处理。 */
	fun exists(path: String): Boolean {
		val req = Request.Builder().url(urlFor(path)).header("Depth", "0")
			.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
			.build()
		metaCall(req).execute().use { resp ->
			if (resp.code == 404) return false
			if (!resp.isSuccessful) {
				logger.warning("WebDAV 存在性检查失败 path=$path code=${resp.code}")
				throw DavException(resp.code, "检查远端文件失败")
			}
			return true
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
				val cleanDir = dirPath.trim('/')
				val prefix = if (cleanDir.isEmpty()) "" else "$cleanDir/"
				val added = HashSet<String>()
				for (i in 0 until content.length()) {
					val item = content.optJSONObject(i) ?: continue
					val name = item.optString("name")
					val thumb = item.optString("thumb")
					val key = (prefix + name).trim('/')
					val sign = item.optString("sign").takeIf { it.isNotBlank() }
					if (sign != null) {
						signUrls[key] = sign
					}
					var resolvedThumb: String? = null
					if (thumb.isNotBlank()) {
						resolvedThumb = if (thumb.startsWith("http")) {
							rewriteThumbOrigin(thumb)
						} else if (thumb.startsWith("/")) {
							try {
								val cleanPath = thumb.substringBefore('?')
								val query = thumb.substringAfter('?', "")
								val rootBase = (apiOrigin ?: effectiveBase).newBuilder().encodedPath("/").build()
								val b = rootBase.newBuilder().encodedPath(cleanPath)
								if (query.isNotEmpty()) {
									query.split('&').forEach { param ->
										val kv = param.split('=', limit = 2)
										if (kv.size == 2) b.addQueryParameter(kv[0], kv[1])
									}
								}
								// origin 换成本客户端入口：直连模式=原域名；bridge 模式=127.0.0.1:port
								b.build().newBuilder()
									.scheme(effectiveBase.scheme).host(effectiveBase.host).port(effectiveBase.port)
									.build().toString()
							} catch (_: Exception) { null }
						} else null
					}
					if (name.isNotEmpty() && !resolvedThumb.isNullOrBlank()) {
						thumbUrls[key] = resolvedThumb
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
				.scheme(effectiveBase.scheme)
				.host(effectiveBase.host)
				.port(effectiveBase.port)
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
		val prefix = (davRootPrefix ?: effectiveBase.encodedPath).trimEnd('/')
		// 对目标路径各个段进行严格的 URL 编码，避免 HTTP Header 中出现中文字符触发 OkHttp 的 IllegalArgumentException
		val encodedTarget = to.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") {
			java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
		}
		val destination = "$prefix/$encodedTarget"
		val req = Request.Builder()
			.url(urlFor(from))
			.method("MOVE", null)
			.header("Destination", destination)
			.header("Overwrite", "T")
			.build()
		metaCall(req).execute().use {
			if (!it.isSuccessful) throw DavException(it.code, "移动失败: HTTP ${it.code}")
		}
	}

	/**
	 * 下载并写入目标流；续传只接受起点、范围和长度一致的 206，绝不把完整响应追加到旧片段。
	 * [onCall] 在执行请求前登记可取消的调用；[onProgress] 返回整个文件的已完成字节数和总大小。
	 * [validator] 是旧片段对应的强 ETag 或 Last-Modified；[onValidator] 在写入前回传新身份，便于安全暂停恢复。
	 */
	fun download(
		path: String,
		output: java.io.OutputStream,
		startByte: Long = 0L,
		onCall: (Call) -> Unit = {},
		validator: String? = null,
		onValidator: (String?) -> Unit = {},
		onProgress: (Long, Long) -> Unit,
	) {
		require(startByte >= 0L) { "下载起点不能为负数" }
		val expectedValidator = validator?.trim()?.takeIf { it.isNotEmpty() }
		val targetUrl = runCatching { directUrlFor(path) }.getOrNull() ?: urlFor(path)
		val reqBuilder = Request.Builder().url(targetUrl).header("Accept-Encoding", "identity").get()
		if (startByte > 0L) {
			reqBuilder.header("Range", "bytes=$startByte-")
			if (expectedValidator != null) reqBuilder.header("If-Range", expectedValidator)
		}
		val call = http.newCall(reqBuilder.build())
		onCall(call)
		try {
			call.throwIfCanceled()
			call.execute().use { resp ->
				call.throwIfCanceled()
				if (startByte > 0L && resp.code in listOf(200, 416)) {
					throw ResumeRestartRequiredException(resp.code, "服务器无法继续当前下载，需要重新下载")
				}
				if (!resp.isSuccessful) throw DavException(resp.code, "下载失败")
				val body = resp.body ?: throw DavException(resp.code, "响应体为空")
				val contentLength = body.contentLength()
				var expectedLength = contentLength
				var total = contentLength
				if (resp.code == 206) {
					val range = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
						.matchEntire(resp.header("Content-Range").orEmpty().trim())
					val rangeStart = range?.groupValues?.get(1)?.toLongOrNull()
					val rangeEnd = range?.groupValues?.get(2)?.toLongOrNull()
					val rangeTotal = range?.groupValues?.get(3)?.toLongOrNull()
					val rangeLength = if (rangeStart != null && rangeEnd != null && rangeEnd >= rangeStart) rangeEnd - rangeStart + 1 else -1L
					// 开放尾请求只执行一次；总长未知时无法确认响应已经覆盖文件末尾。
					if (rangeTotal == null) throw ResumeRestartRequiredException(resp.code, "服务器未提供有效的文件总长，需要重新下载")
					if (rangeStart != startByte || rangeLength <= 0 || rangeEnd != rangeTotal - 1 || rangeTotal <= 0 ||
						(contentLength >= 0 && contentLength != rangeLength)) {
						throw ResumeRestartRequiredException(resp.code, "服务器返回的续传范围或长度不匹配，需要重新下载")
					}
					expectedLength = rangeLength
					total = rangeTotal
				} else if (startByte > 0L) {
					throw ResumeRestartRequiredException(resp.code, "服务器未返回有效的续传响应，需要重新下载")
				}
				// 弱 ETag 不代表字节完全相同；优先使用强 ETag，否则退回修改时间。
				val responseValidator = resp.header("ETag")?.trim()?.takeIf {
					it.length >= 2 && it.startsWith('"') && it.endsWith('"')
				} ?: resp.header("Last-Modified")?.trim()?.takeIf { it.isNotEmpty() }
				if (startByte > 0L && expectedValidator != null && responseValidator != null && responseValidator != expectedValidator) {
					throw ResumeRestartRequiredException(resp.code, "远端文件已变化，需要重新下载")
				}
				onValidator(responseValidator)
				body.byteStream().use { ins ->
					val buf = ByteArray(128 * 1024)
					var received = 0L
					var lastReport = 0L
					while (true) {
						call.throwIfCanceled()
						val n = ins.read(buf)
						if (n < 0) break
						if (n == 0) continue
						call.throwIfCanceled()
						if (expectedLength >= 0 && n.toLong() > expectedLength - received) throw IOException("下载响应超过声明长度")
						output.write(buf, 0, n)
						received += n
						val now = System.currentTimeMillis()
						if (now - lastReport >= 100 || (expectedLength >= 0 && received == expectedLength)) {
							onProgress(startByte + received, total)
							lastReport = now
						}
					}
					if (expectedLength >= 0 && received != expectedLength) throw IOException("下载响应提前结束：预期 $expectedLength 字节，实际 $received 字节")
					call.throwIfCanceled()
					output.flush()
					onProgress(startByte + received, total)
				}
			}
		} catch (e: IOException) {
			call.throwIfCanceled(e)
			logger.log(Level.WARNING, "下载失败 path=$path startByte=$startByte", e)
			throw e
		}
	}

	/**
	 * 下载远端文本文件内容（例如伴生文字备忘 .*.memo）
	 */
	fun downloadText(path: String): String? {
		return try {
			val targetUrl = runCatching { directUrlFor(path) }.getOrNull() ?: urlFor(path)
			val req = Request.Builder().url(targetUrl).header("Accept-Encoding", "identity").get().build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) null
				else resp.body?.string()
			}
		} catch (_: Exception) {
			null
		}
	}

	/**
	 * 读取远端配置与相册元数据 (.nasdrop_meta.json)
	 */
	fun loadRemoteMetadata(): RemoteMetadata? {
		return try {
			val url = urlFor(".nasdrop_meta.json")
			val req = Request.Builder().url(url).get().build()
			metaCall(req).execute().use { resp ->
				if (!resp.isSuccessful) return null
				val body = resp.body?.string() ?: return null
				parseRemoteMetadata(body)
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * 保存配置与相册元数据至远端 (.nasdrop_meta.json)
	 */
	fun saveRemoteMetadata(meta: RemoteMetadata): Boolean {
		return try {
			val url = urlFor(".nasdrop_meta.json")
			val json = serializeRemoteMetadata(meta)
			val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
			val req = Request.Builder().url(url).put(body).build()
			metaCall(req).execute().use { resp ->
				resp.isSuccessful
			}
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * 上传自定义文件夹图标至 NAS 的 .nasdrop_icons 目录
	 */
	fun uploadCustomIcon(fileName: String, bytes: ByteArray): String? {
		return try {
			runCatching { mkcol(".nasdrop_icons") }
			val path = ".nasdrop_icons/$fileName"
			val url = urlFor(path)
			val body = bytes.toRequestBody("image/png".toMediaTypeOrNull())
			val req = Request.Builder().url(url).put(body).build()
			metaCall(req).execute().use { resp ->
				if (resp.isSuccessful) path else null
			}
		} catch (e: Exception) {
			null
		}
	}

	/**
	 * 上传一个 content:// 或 file:// 的 Uri 到 remoteDir 下，保留原文件名。
	 * 进度回调 (已发送, 总大小)，总大小未知时为 -1。
	 *
	 * [onBodySent] 在**全部字节写进 socket** 的那一刻回调一次（只回调一次）。
	 * 它与"服务端返回 201"之间可能隔着一段落盘/rename 的时间，UI 用它在 100% 时
	 * 立刻把文件乐观显示出来，而不是卡在"等待服务端写入"。
	 * [onCall] 在每次 PUT 或父目录预检前登记调用，取消后不再重试。
	 */
	fun upload(
		context: Context,
		uri: Uri,
		remoteDir: String,
		onProgress: (Long, Long) -> Unit,
		onBodySent: () -> Unit = {},
		onCall: (Call) -> Unit = {},
	) {
		val name = Query.nameOf(context, uri)
		val total = Query.sizeOf(context, uri)
		uploadStream(name, total, context.contentResolver.getType(uri), remoteDir,
			openInput = { context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取上传文件") },
			onProgress = onProgress, onBodySent = onBodySent, onCall = onCall)
	}

	fun uploadBytes(
		bytes: ByteArray,
		remoteDir: String,
		name: String,
		mimeType: String = "image/jpeg",
	) {
		uploadStream(
			name = name,
			total = bytes.size.toLong(),
			mimeType = mimeType,
			remoteDir = remoteDir,
			openInput = { java.io.ByteArrayInputStream(bytes) },
			onProgress = { _, _ -> },
		)
	}

	/** 流式 PUT；仅安全重试时重开输入源，每次 HTTP 调用都通过 [onCall] 登记，发送回调与远端确认独立。 */
	internal fun uploadStream(
		name: String,
		total: Long,
		mimeType: String?,
		remoteDir: String,
		openInput: () -> java.io.InputStream,
		onProgress: (Long, Long) -> Unit,
		onBodySent: () -> Unit = {},
		onCall: (Call) -> Unit = {},
	) {
		val path = listOf(remoteDir.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/")
		val bodySentOnce = java.util.concurrent.atomic.AtomicBoolean(false)

		// body 工厂：每次 PUT 调用都新建（重试时需要重新读 Uri 输入流）。
		// written 记录"这次尝试实际写出去多少字节"——决定失败后能不能安全重传，
		// 见下面 putOnce 的注释。
		fun newBody(written: java.util.concurrent.atomic.AtomicLong, checkCanceled: () -> Unit) = object : RequestBody() {
			override fun contentType() = mimeType?.toMediaTypeOrNull()
			override fun contentLength() = if (total > 0) total else -1L
			// 禁止 OkHttp 在响应丢失后悄悄重放大文件；仅由下面的显式策略决定重试。
			override fun isOneShot() = true
			override fun writeTo(sink: BufferedSink) {
				val input = openInput()
				var sent = 0L
				input.use { ins ->
					val buf = ByteArray(32 * 1024)
					var lastReportTime = 0L
					while (true) {
						checkCanceled()
						val n = ins.read(buf)
						if (n <= 0) break
						sink.write(buf, 0, n)
						// 强制 flush 到底层 Socket，利用阻塞 I/O 形成背压：
						// 当网络较慢时阻塞等待网络 ACK 腾出窗口，确保进度与物理网速 100% 同步
						sink.flush()
						sent += n
						written.addAndGet(n.toLong())
						val now = System.currentTimeMillis()
						if (now - lastReportTime >= 100L || (total > 0 && sent >= total)) {
							lastReportTime = now
							onProgress(sent, if (total > 0) total else sent)
						}
					}
				}
				// 冲出末尾缓冲并向外汇报已完成物理发送
				sink.flush()
				checkCanceled()
				val finalTotal = if (total > 0) total else sent
				onProgress(sent, finalTotal)
				if (bodySentOnce.compareAndSet(false, true)) onBodySent()
			}
		}

		val url = urlFor(path)

		/**
		 * 执行一次 PUT，返回 (是否成功, 本次已写出字节数, HTTP 码；0 表示传输层失败)。
		 *
		 * 已写出字节数是关键：旧实现把所有 IOException 都当成"可能缺父目录"从而无条件
		 * 重传一遍，于是一个传到 99% 断掉的视频会**从头再传一次**——用户看到的就是
		 * "传完了又从头开始/传完还要等很久"。现在只有明确 409，或连接失败且写出
		 * 不足 1MB 时允许再试；大文件已传出较多数据就如实失败，避免整段重传。
		 */
		fun putOnce(): PutResult {
			val written = java.util.concurrent.atomic.AtomicLong(0)
			lateinit var call: Call
			call = http.newCall(Request.Builder().url(url).put(newBody(written) { call.throwIfCanceled() }).build())
			onCall(call)
			return try {
				call.throwIfCanceled()
				call.execute().use {
					call.throwIfCanceled()
					when {
						it.isSuccessful -> PutResult(true, written.get(), it.code)
						it.code == 409 -> PutResult(false, written.get(), 409)
						else -> throw DavException(it.code, "上传失败")
					}
				}
			} catch (e: DavException) {
				call.throwIfCanceled(e)
				logger.log(Level.WARNING, "上传被服务端拒绝 path=$path code=${e.code}", e)
				throw e
			} catch (e: java.io.IOException) {
				call.throwIfCanceled(e)
				logger.log(Level.WARNING, "上传请求失败 path=$path written=${written.get()}", e)
				PutResult(false, written.get(), 0, e)
			}
		}

		// 大文件先确认父目录存在。
		// 关键：WebDAV 的 409 是服务端判断出来的，但很多实现要**把整个 body 收完**才回
		// 409。几十上百 MB 的视频因此会白传一遍、再建目录重传一遍，等于双倍时间——
		// 用户感知就是"传了很久，又从头开始"。小文件不预检，省掉这次 RTT。
		if (total <= 0 || total >= 8L * 1024 * 1024) {
			try {
				ensureParentDirs(remoteDir, onCall)
			} catch (e: TransferCanceledException) {
				throw e
			} catch (e: IOException) {
				// 父目录预检是优化；普通失败仍尝试 PUT，让服务端给出最终结果。
				logger.log(Level.WARNING, "上传父目录预检失败 dir=$remoteDir", e)
			}
		}

		// 先直接 PUT；只有明确 409 或写出不足 1MB 的连接失败才按显式策略重试。
		val first = putOnce()
		if (!first.ok) {
			val retryable = first.code == 409 || first.written < 1L * 1024 * 1024
			if (!retryable) {
				// 已经传了实打实的数据再断：重传等于让用户重新等一遍，不如如实报错。
				throw IOException("传输中断（已发送 ${first.written / 1024 / 1024}MB），请重试", first.error)
			}
			ensureParentDirs(remoteDir, onCall)
			val second = putOnce()
			if (!second.ok) {
				if (second.code == 0) throw IOException("上传连接失败，请重试", second.error)
				throw DavException(second.code.takeIf { it > 0 } ?: 409, "上传失败：父目录创建后仍失败")
			}
		}
	}

	/** 单次 PUT 的结果：成功与否、已写出字节数、HTTP 码（0 = 传输层异常） */
	private class PutResult(val ok: Boolean, val written: Long, val code: Int, val error: IOException? = null)

	/** 逐级创建远端父目录：MKCOL 每级路径，405（已存在）/409 都算成功。空目录直接返回。 */
	private fun ensureParentDirs(remoteDir: String, onCall: (Call) -> Unit = {}) {
		val parts = remoteDir.trim('/').split('/').filter { it.isNotEmpty() }
		var cur = ""
		for (part in parts) {
			cur = if (cur.isEmpty()) part else "$cur/$part"
			try {
				exec("MKCOL", urlFor(cur, dir = true), onCall)
			} catch (e: DavException) {
				// 405 Method Not Allowed = 目录已存在；部分实现返回 409。都视为已就绪。
				if (e.code != 405 && e.code != 409) throw e
			}
		}
	}

	/** 元数据请求也登记到传输任务，取消预检时不得继续后续 PUT。 */
	private fun exec(method: String, url: HttpUrl, onCall: (Call) -> Unit = {}) {
		val req = Request.Builder().url(url).method(method, null).build()
		val call = metaCall(req)
		onCall(call)
		try {
			call.throwIfCanceled()
			call.execute().use {
				call.throwIfCanceled()
				if (!it.isSuccessful) throw DavException(it.code, "$method 失败")
			}
		} catch (e: IOException) {
			call.throwIfCanceled(e)
			throw e
		}
	}

	/** 统一将 OkHttp 的取消结果与可重试的传输异常区分，保留底层异常原因。 */
	private fun Call.throwIfCanceled(cause: IOException? = null) {
		if (isCanceled()) throw if (cause is TransferCanceledException) cause else TransferCanceledException(cause)
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
		var created = 0L
		var inResponse = false

		var event = parser.eventType
		while (event != XmlPullParser.END_DOCUMENT) {
			if (event == XmlPullParser.START_TAG) {
				when (parser.name) {
					"response" -> {
						inResponse = true; href = null; isDir = false; size = -1L; modified = 0L; created = 0L
					}
					"href" -> if (inResponse && href == null) href = parser.nextText()
					"collection" -> isDir = true
					"getcontentlength" -> size = parser.nextText().trim().toLongOrNull() ?: -1L
					"getlastmodified" -> modified = parseDate(parser.nextText())
					"creationdate" -> created = parseIsoDate(parser.nextText())
				}
			} else if (event == XmlPullParser.END_TAG && parser.name == "response" && inResponse) {
				responses += Raw(href, isDir, size, modified, created)
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
		val cleanRoot = rootPrefix.trim('/')
		// 记住服务端真实的 WebDAV 根（如 /dav），MOVE 的 Destination 要用它
		if (cleanRoot.isNotEmpty()) davRootPrefix = "/$cleanRoot"

		val cleanTargetRel = targetRel.trim('/')
		for (r in responses) {
			val raw = r.href
			if (raw != null) {
				val decoded = decode(raw)
				val trimmed = decoded.trimEnd('/')
				val rel = when {
					cleanRoot.isNotEmpty() && trimmed == "/$cleanRoot" -> "" // WebDAV 根自身
					cleanRoot.isNotEmpty() && trimmed.startsWith("/$cleanRoot/") ->
						trimmed.removePrefix("/$cleanRoot/")
					cleanRoot.isEmpty() -> trimmed.trim('/')
					else -> decoded.trim('/')
				}
				// 排除空路径以及当前所请求目录自身
				if (rel.isNotEmpty() && rel != cleanTargetRel) {
					val entryName = rel.substringAfterLast('/')
					val nameTaken = PhotoTimeParser.parseFromFilename(entryName) ?: 0L
					out += DavEntry(
						name = entryName,
						path = rel,
						isDir = r.isDir || decoded.endsWith("/"),
						size = r.size,
						modified = r.modified,
						takenAt = nameTaken,
						created = r.created,
					)
				}
			}
		}

		return out.sortedWith(
			compareByDescending<DavEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) },
		)
	}

	/**
	 * 从 PROPFIND 响应推断服务端的 WebDAV 根前缀（形如 "dav" 或 ""，不带首尾斜杠）。
	 *
	 * 优先路径：找一条 href，其解码后路径去掉尾斜杠，恰好以 "/$cleanTarget" 结尾，
	 * 那么把它去掉 "/$cleanTarget" 就是根。这样 AList/OpenList(/dav)、原生 WebDAV(/)、
	 * 甚至挂在子路径的服务都能自动适配，且不受配置里 base 写什么影响。
	 * 兜底：取所有 href 的最长公共路径段前缀；再兜底用 base 的编码路径。
	 */
	private fun inferRootPrefix(hrefs: List<String>, targetRel: String, basePath: String): String {
		fun norm(p: String) = p.trim().trimEnd('/').let { if (it.isEmpty()) "/" else it }

		// 1) 目录自身那条：/dav/Photos → 去掉 /Photos 得到 dav；/Photos → 去掉 /Photos 得到 ""
		val cleanTarget = targetRel.trim('/')
		if (cleanTarget.isNotEmpty()) {
			val suffix = "/$cleanTarget"
			hrefs.firstOrNull { h ->
				val n = norm(h)
				n == suffix || n.endsWith(suffix)
			}?.let { h ->
				val n = norm(h)
				return n.removeSuffix(suffix).trim('/')
			}
		} else {
			// 根目录：目录自身的 href 就是根，取层级最浅的一条
			hrefs.map { norm(it).trim('/') }.minByOrNull { it.count { c -> c == '/' } }?.let { return it }
		}

		// 2) 最长公共段前缀兜底
		val paths = hrefs.map { norm(it).split('/').filter { it.isNotEmpty() } }
		if (paths.isNotEmpty()) {
			val first = paths.first()
			var common = first.size
			for (p in paths) {
				var i = 0
				while (i < common && i < p.size && first[i] == p[i]) i++
				common = i
			}
			if (common > 0) {
				val commonPrefix = first.take(common).joinToString("/")
				if (cleanTarget.isNotEmpty()) {
					if (commonPrefix == cleanTarget || commonPrefix.endsWith("/$cleanTarget")) {
						return commonPrefix.removeSuffix("/$cleanTarget").removeSuffix(cleanTarget).trim('/')
					}
				}
				return commonPrefix.trim('/')
			}
		}

		// 3) 退回 base 路径
		return basePath.trim('/')
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

	private fun parseIsoDate(raw: String): Long = try {
		val clean = raw.trim()
		val pattern = if (clean.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSSX" else "yyyy-MM-dd'T'HH:mm:ssX"
		SimpleDateFormat(pattern, Locale.US).parse(clean)?.time
			?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(clean.take(19))?.time ?: 0L
	} catch (_: Exception) {
		0L
	}

	companion object {
		private val logger = Logger.getLogger("WebDavClient")
		private data class Raw(val href: String?, val isDir: Boolean, val size: Long, val modified: Long, val created: Long)

		private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop>
<d:resourcetype/><d:getcontentlength/><d:getlastmodified/><d:creationdate/>
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
						val s = c.getLong(i)
						if (s > 0) return s
					}
				}
		}
		runCatching {
			context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
				val s = pfd.statSize
				if (s > 0) return s
			}
		}
		return -1L
	}
}

/**
 * 远端持久化元数据结构 (.nasdrop_meta.json)
 */
data class RemoteMetadata(
	val folderIcons: Map<String, String> = emptyMap(),
	val albumOrder: List<String> = emptyList(),
	val mediaOrder: List<String> = emptyList(),
)

fun parseRemoteMetadata(jsonStr: String): RemoteMetadata {
	return try {
		val obj = JSONObject(jsonStr)
		val iconsMap = mutableMapOf<String, String>()
		if (obj.has("folder_icons")) {
			val iconsObj = obj.getJSONObject("folder_icons")
			val keys = iconsObj.keys()
			while (keys.hasNext()) {
				val k = keys.next()
				iconsMap[k] = iconsObj.optString(k, "")
			}
		}
		val albumList = mutableListOf<String>()
		if (obj.has("album_order")) {
			val arr = obj.getJSONArray("album_order")
			for (i in 0 until arr.length()) {
				val v = arr.optString(i)
				if (v.isNotEmpty()) albumList.add(v)
			}
		}
		val mediaList = mutableListOf<String>()
		if (obj.has("media_order")) {
			val arr = obj.getJSONArray("media_order")
			for (i in 0 until arr.length()) {
				val v = arr.optString(i)
				if (v.isNotEmpty()) mediaList.add(v)
			}
		}
		RemoteMetadata(iconsMap, albumList, mediaList)
	} catch (e: Exception) {
		RemoteMetadata()
	}
}

fun serializeRemoteMetadata(meta: RemoteMetadata): String {
	val obj = JSONObject()
	val iconsObj = JSONObject()
	meta.folderIcons.forEach { (k, v) -> iconsObj.put(k, v) }
	obj.put("folder_icons", iconsObj)
	val albumArr = JSONArray()
	meta.albumOrder.forEach { albumArr.put(it) }
	obj.put("album_order", albumArr)
	val mediaArr = JSONArray()
	meta.mediaOrder.forEach { mediaArr.put(it) }
	obj.put("media_order", mediaArr)
	return obj.toString(2)
}
