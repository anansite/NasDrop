package com.nasdrop

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import okhttp3.HttpUrl
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
class WebDavClient(private val base: HttpUrl, private val http: OkHttpClient) {

	fun urlFor(path: String, dir: Boolean = false): HttpUrl {
		val b = base.newBuilder()
		path.trim('/').split('/').forEach { if (it.isNotEmpty()) b.addPathSegment(it) }
		if (dir && path.isNotEmpty()) b.addPathSegment("")
		return b.build()
	}

	/** 列目录。path 为空表示根 */
	fun list(path: String): List<DavEntry> {
		val req = Request.Builder()
			.url(urlFor(path, dir = true))
			.header("Depth", "1")
			.method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaTypeOrNull()))
			.build()

		http.newCall(req).execute().use { resp ->
			if (!resp.isSuccessful) throw DavException(resp.code, "列目录失败")
			val stream = resp.body?.byteStream() ?: throw DavException(resp.code, "空响应")
			return parse(stream, path)
		}
	}

	fun mkcol(path: String) = exec("MKCOL", urlFor(path, dir = true))

	fun delete(path: String, isDir: Boolean = false) {
		val url = urlFor(path, dir = isDir)
		val req = Request.Builder()
			.url(url)
			.method("DELETE", null)
			.header("Depth", "infinity")
			.build()
		http.newCall(req).execute().use { resp ->
			// 200, 204 为成功；404 表示文件在远端已不存在，也视为成功
			if (!resp.isSuccessful && resp.code != 404) {
				throw DavException(resp.code, "删除失败: HTTP ${resp.code}")
			}
		}
	}

	fun move(from: String, to: String) {
		val req = Request.Builder()
			.url(urlFor(from))
			.method("MOVE", null)
			.header("Destination", urlFor(to).toString())
			.header("Overwrite", "T")
			.build()
		http.newCall(req).execute().use {
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
	 */
	fun upload(context: Context, uri: Uri, remoteDir: String, onProgress: (Long, Long) -> Unit) {
		val name = Query.nameOf(context, uri)
		val total = Query.sizeOf(context, uri)
		val path = listOf(remoteDir.trim('/'), name).filter { it.isNotEmpty() }.joinToString("/")

		val body = object : RequestBody() {
			override fun contentType() = context.contentResolver.getType(uri)?.toMediaTypeOrNull()

			override fun contentLength() = total

			override fun writeTo(sink: BufferedSink) {
				val input = context.contentResolver.openInputStream(uri)
					?: throw IOException("无法读取：$uri")
				input.use { ins ->
					val buf = ByteArray(64 * 1024)
					var sent = 0L
					while (true) {
						val n = ins.read(buf)
						if (n <= 0) break
						sink.write(buf, 0, n)
						sent += n
						onProgress(sent, total)
					}
				}
			}
		}

		val req = Request.Builder().url(urlFor(path)).put(body).build()
		http.newCall(req).execute().use {
			if (!it.isSuccessful) throw DavException(it.code, "上传失败")
		}
	}

	private fun exec(method: String, url: HttpUrl) {
		val req = Request.Builder().url(url).method(method, null).build()
		http.newCall(req).execute().use {
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
