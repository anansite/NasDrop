package com.nasdrop

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
	val versionName: String,
	val releaseNotes: String,
	val apkUrl: String,
	val apkSize: Long,
)

object UpdateManager {

	private const val RELEASES_API = "https://api.github.com/repos/anansite/private_filemanager/releases/latest"

	// 国内直连 api.github.com 常超时，按序尝试镜像；全部失败才放弃。
	// 镜像只做公开 releases API 的反代，不涉及任何隐私数据。
	private val API_CANDIDATES = listOf(
		"https://api.github.com/repos/anansite/private_filemanager/releases/latest",
		"https://gh-proxy.com/https://api.github.com/repos/anansite/private_filemanager/releases/latest",
		"https://ghproxy.net/https://api.github.com/repos/anansite/private_filemanager/releases/latest",
	)

	private val http = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.build()

	// 大文件专用下载客户端：高容忍度、长读取超时、连接自动重试
	private val downloadHttp = OkHttpClient.Builder()
		.connectTimeout(20, TimeUnit.SECONDS)
		.readTimeout(5, TimeUnit.MINUTES)
		.writeTimeout(5, TimeUnit.MINUTES)
		.retryOnConnectionFailure(true)
		.build()

	suspend fun checkUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
		runCatching {
			Log.i("UpdateManager", "Starting checkUpdate, current: $currentVersion")
			var jsonStr: String? = null
			for (url in API_CANDIDATES) {
				try {
					val request = Request.Builder()
						.url(url)
						.header("Accept", "application/vnd.github.v3+json")
						.header("User-Agent", "NasDrop-Android-App")
						.build()
					val response = http.newCall(request).execute()
					Log.i("UpdateManager", "API ${url.take(40)} -> ${response.code}")
					if (response.isSuccessful) {
						response.body?.string()?.let {
							if (it.startsWith("{") && it.contains("tag_name")) { jsonStr = it }
						}
						if (jsonStr != null) break
					}
				} catch (e: Exception) {
					Log.w("UpdateManager", "API candidate failed: ${e.message}")
				}
			}
			val json = jsonStr ?: return@withContext null
			Log.i("UpdateManager", "Got body length: ${json.length}")
			val obj = JSONObject(json)

			val tagName = obj.optString("tag_name", "").trim().removePrefix("v").removePrefix("V")
			val releaseNotes = obj.optString("body", "优化与常规 Bug 修复")

			// 查找 .apk 资源
			val assets = obj.optJSONArray("assets") ?: return@withContext null
			var apkUrl = ""
			var apkSize = 0L
			for (i in 0 until assets.length()) {
				val asset = assets.getJSONObject(i)
				val name = asset.optString("name", "")
				if (name.endsWith(".apk", ignoreCase = true)) {
					apkUrl = asset.optString("browser_download_url", "")
					apkSize = asset.optLong("size", 0L)
					break
				}
			}

			if (apkUrl.isBlank()) return@withContext null

			// 简单版本比较
			if (isNewer(tagName, currentVersion)) {
				UpdateInfo(
					versionName = tagName,
					releaseNotes = releaseNotes,
					apkUrl = apkUrl,
					apkSize = apkSize,
				)
			} else {
				null
			}
		}.onFailure {
			Log.e("UpdateManager", "checkUpdate failed with exception", it)
		}.getOrNull()
	}

	suspend fun downloadApk(
		context: Context,
		apkUrl: String,
		onProgress: (downloaded: Long, total: Long, speedBps: Long) -> Unit,
	): File = withContext(Dispatchers.IO) {
		val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
		val destFile = File(updateDir, "nasdrop_latest.apk")
		if (destFile.exists()) destFile.delete()

		val candidateUrls = if (apkUrl.contains("github.com")) {
			listOf(
				"https://ghproxy.net/$apkUrl",
				"https://gh-proxy.com/$apkUrl",
				"https://ghps.cc/$apkUrl",
				"https://mirror.ghproxy.com/$apkUrl",
				"https://ghp.ci/$apkUrl",
				apkUrl,
			)
		} else listOf(apkUrl)

		var lastException: Exception? = null
		for (url in candidateUrls) {
			try {
				Log.i("UpdateManager", "Attempting download from mirror: $url")
				val request = Request.Builder()
					.url(url)
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
					.build()
				val response = downloadHttp.newCall(request).execute()
				if (!response.isSuccessful) continue
				val body = response.body ?: continue
				val total = body.contentLength()

				body.byteStream().use { input ->
					FileOutputStream(destFile).use { output ->
						val buffer = ByteArray(64 * 1024)
						var read: Int
						var downloaded = 0L
						var lastTime = System.currentTimeMillis()
						var lastDownloaded = 0L
						var speed = 0L

						while (input.read(buffer).also { read = it } != -1) {
							output.write(buffer, 0, read)
							downloaded += read

							val now = System.currentTimeMillis()
							val dt = now - lastTime
							if (dt >= 300) {
								speed = ((downloaded - lastDownloaded) * 1000) / dt
								lastTime = now
								lastDownloaded = downloaded
								onProgress(downloaded, total, speed)
							}
						}
						onProgress(downloaded, total, 0L)
					}
				}
				return@withContext destFile
			} catch (e: Exception) {
				lastException = e
			}
		}
		throw lastException ?: IllegalStateException("所有镜像下载通道均失败")
	}

	fun installApk(context: Context, apkFile: File) {
		val authority = "${context.packageName}.fileprovider"
		val uri = FileProvider.getUriForFile(context, authority, apkFile)
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(uri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		context.startActivity(intent)
	}

	private fun isNewer(latest: String, current: String): Boolean {
		val vLatest = latest.split(".").mapNotNull { it.toIntOrNull() }
		val vCurrent = current.split(".").mapNotNull { it.toIntOrNull() }
		val len = maxOf(vLatest.size, vCurrent.size)
		for (i in 0 until len) {
			val l = vLatest.getOrElse(i) { 0 }
			val c = vCurrent.getOrElse(i) { 0 }
			if (l > c) return true
			if (l < c) return false
		}
		return false
	}
}
