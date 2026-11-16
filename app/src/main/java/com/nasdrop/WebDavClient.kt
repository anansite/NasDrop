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

data class DavEntry(
	val name: String,
	/** 相对 WebDAV 根的路径，不带首尾斜杠，例如 "Photos/2026" */
	val path: String,
	val isDir: Boolean,
	val size: Long,
	val modified: Long,
) {
	val ext: String get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
	val isImage: Boolean get() = !isDir && ext in IMAGES
	val isVideo: Boolean get() = !isDir && ext in VIDEOS

	companion object {
		private val IMAGES = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg")
		private val VIDEOS = setOf("mp4", "mkv", "mov", "webm", "avi", "m4v", "ts", "flv", "3gp")
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

	fun delete(path: String, isDir: Boolean = false) = exec("DELETE", urlFor(path, dir = isDir))

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

		val rootDecoded = decode(base.encodedPath)
		val out = ArrayList<DavEntry>()

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
				val raw = href
				if (raw != null) {
					val decoded = decode(raw)
					val rel = decoded.removePrefix(rootDecoded).trim('/')
					// 第一条通常是目录自身，rel 为空，跳过
					if (rel.isNotEmpty()) {
						out += DavEntry(
							name = rel.substringAfterLast('/'),
							path = rel,
							isDir = isDir || decoded.endsWith("/"),
							size = size,
							modified = modified,
						)
					}
				}
				inResponse = false
			}
			event = parser.next()
		}

		return out.sortedWith(
			compareByDescending<DavEntry> { it.isDir }.thenBy { it.name.lowercase(Locale.ROOT) },
		)
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
