package com.nasdrop

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateInfo(
	val versionName: String,
	val releaseNotes: String,
	val apkUrl: String,
	val apkSize: Long,
	val apkName: String = "",
)

data class ApkAsset(
	val name: String,
	val url: String,
	val size: Long,
)

object UpdateManager {

	private const val RELEASES_API = "https://api.github.com/repos/anansite/NasDrop/releases/latest"

	// 路由器局域网 CDN 优先（在家秒级响应），国内加速镜像与官方直连兜底。
	private val API_CANDIDATES = listOf(
		"https://cdn.205002.xyz/nasdrop/latest.json",
		"https://ghproxy.net/https://api.github.com/repos/anansite/NasDrop/releases/latest",
		"https://ghfast.top/https://api.github.com/repos/anansite/NasDrop/releases/latest",
		"https://mirror.ghproxy.com/https://api.github.com/repos/anansite/NasDrop/releases/latest",
		"https://hub.gitmirror.com/https://api.github.com/repos/anansite/NasDrop/releases/latest",
		"https://gh-proxy.com/https://api.github.com/repos/anansite/NasDrop/releases/latest",
		"https://api.github.com/repos/anansite/NasDrop/releases/latest",
	)

	private val http = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(20, TimeUnit.SECONDS)
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

			// 查找 .apk 资源并按架构智能选择
			val assets = obj.optJSONArray("assets") ?: return@withContext null
			val apkList = mutableListOf<ApkAsset>()
			for (i in 0 until assets.length()) {
				val asset = assets.getJSONObject(i)
				val name = asset.optString("name", "")
				if (name.endsWith(".apk", ignoreCase = true)) {
					val url = asset.optString("browser_download_url", "")
					val size = asset.optLong("size", 0L)
					if (url.isNotBlank()) {
						apkList.add(ApkAsset(name, url, size))
					}
				}
			}

			val bestApk = selectBestApk(apkList) ?: return@withContext null

			// 简单版本比较
			if (isNewer(tagName, currentVersion)) {
				UpdateInfo(
					versionName = tagName,
					releaseNotes = releaseNotes,
					apkUrl = bestApk.url,
					apkSize = bestApk.size,
					apkName = bestApk.name,
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
				"https://ghfast.top/$apkUrl",
				"https://mirror.ghproxy.com/$apkUrl",
				"https://hub.gitmirror.com/$apkUrl",
				"https://ghps.cc/$apkUrl",
				"https://gh-proxy.com/$apkUrl",
				apkUrl,
			)
		} else listOf(apkUrl)

		// 多镜像并发竞速探测：优先挑选国内响应最快且可读的 CDN 源
		val sortedUrls = if (candidateUrls.size > 1) {
			val channel = kotlinx.coroutines.channels.Channel<String>(candidateUrls.size)
			coroutineScope {
				candidateUrls.forEach { u ->
					launch(Dispatchers.IO) {
						try {
							val req = Request.Builder().url(u).head()
								.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
								.build()
							downloadHttp.newCall(req).execute().use { resp ->
								if (resp.isSuccessful && (resp.body?.contentLength() ?: 0L) > 1024 * 1024) {
									channel.trySend(u)
								}
							}
						} catch (_: Exception) {}
					}
				}
			}
			val fastest = mutableListOf<String>()
			while (true) {
				val res = channel.tryReceive().getOrNull() ?: break
				if (!fastest.contains(res)) fastest.add(res)
			}
			if (fastest.isNotEmpty()) {
				Log.i("UpdateManager", "Fastest mirror detected: ${fastest.first()}")
				fastest + (candidateUrls - fastest.toSet())
			} else candidateUrls
		} else candidateUrls

		var lastException: Exception? = null
		for (url in sortedUrls) {
			for (attempt in 1..2) {
				try {
					Log.i("UpdateManager", "Attempting download (try $attempt) from: $url")
					val req = Request.Builder()
						.url(url)
						.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
						.build()
					val resp = downloadHttp.newCall(req).execute()
					if (!resp.isSuccessful) {
						if (attempt < 2) {
							kotlinx.coroutines.delay(1500)
							continue
						}
						continue
					}
					val body = resp.body ?: continue
					val total = body.contentLength()

					body.byteStream().use { input ->
						FileOutputStream(destFile).use { output ->
							val buffer = ByteArray(128 * 1024)
							var read: Int
							var downloaded = 0L
							var lastTime = System.currentTimeMillis()
							var lastDownloaded = 0L

							while (input.read(buffer).also { read = it } != -1) {
								output.write(buffer, 0, read)
								downloaded += read

								val now = System.currentTimeMillis()
								val dt = now - lastTime
								if (dt >= 200 || (total > 0 && downloaded >= total)) {
									val speed = if (dt > 0) ((downloaded - lastDownloaded) * 1000) / dt else 0L
									lastTime = now
									lastDownloaded = downloaded
									onProgress(downloaded, total, speed)
								}
							}
							onProgress(downloaded, total, 0L)
						}
					}
					if (destFile.exists() && destFile.length() > 1024 * 1024) {
						return@withContext destFile
					}
				} catch (e: Exception) {
					lastException = e
					Log.w("UpdateManager", "Download failed (try $attempt) from $url: ${e.message}")
					if (destFile.exists()) destFile.delete()
					if (attempt < 2) {
						kotlinx.coroutines.delay(1500)
					}
				}
			}
		}
		throw lastException ?: IllegalStateException("所有下载通道均失败")
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

	fun selectBestApk(
		assets: List<ApkAsset>,
		supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS ?: emptyArray(),
	): ApkAsset? {
		if (assets.isEmpty()) return null
		if (assets.size == 1) return assets.first()

		// 1. 优先按设备支持的 ABI 顺序匹配（如 arm64-v8a, x86_64 等）
		for (abi in supportedAbis) {
			val clean = abi.lowercase()
			val match = assets.firstOrNull { it.name.lowercase().contains(clean) }
			if (match != null) return match
		}

		// 2. 匹配 universal 全架构包
		val universal = assets.firstOrNull { it.name.lowercase().contains("universal") }
		if (universal != null) return universal

		// 3. 过滤掉明显与设备不兼容架构的包
		val isDeviceArm = supportedAbis.any { it.lowercase().contains("arm") }
		val isDeviceX86 = supportedAbis.any { it.lowercase().contains("x86") }
		val compatible = assets.filter { asset ->
			val n = asset.name.lowercase()
			if (isDeviceArm && !isDeviceX86 && (n.contains("x86") || n.contains("amd64"))) return@filter false
			if (isDeviceX86 && !isDeviceArm && n.contains("arm")) return@filter false
			true
		}
		if (compatible.isNotEmpty()) return compatible.first()

		// 4. 兜底返回第一个
		return assets.first()
	}

	fun isNewer(latest: String, current: String): Boolean {
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
