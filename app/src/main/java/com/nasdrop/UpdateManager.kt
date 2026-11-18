package com.nasdrop

import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

	private const val RELEASES_API = "https://api.github.com/repos/anansite/private_filemanager/releases/latest"

	// 国内直连 api.github.com 常超时，按序尝试高质量镜像；全部失败才放弃。
	private val API_CANDIDATES = listOf(
		"https://gh-proxy.com/https://api.github.com/repos/anansite/private_filemanager/releases/latest",
		"https://ghfast.top/https://api.github.com/repos/anansite/private_filemanager/releases/latest",
		"https://ghproxy.net/https://api.github.com/repos/anansite/private_filemanager/releases/latest",
		"https://api.github.com/repos/anansite/private_filemanager/releases/latest",
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
				"https://gh-proxy.com/$apkUrl",
				"https://ghfast.top/$apkUrl",
				"https://ghfile.com/$apkUrl",
				"https://ghproxy.net/$apkUrl",
				apkUrl,
			)
		} else listOf(apkUrl)

		var lastException: Exception? = null
		for (url in candidateUrls) {
			try {
				Log.i("UpdateManager", "Attempting download from mirror: $url")
				val headReq = Request.Builder()
					.url(url)
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
					.build()
				val headResp = downloadHttp.newCall(headReq).execute()
				if (!headResp.isSuccessful) continue
				val body = headResp.body ?: continue
				val total = body.contentLength()

				// 如果文件大于 4MB 且有明确长度，开启 3 线程并发分块下载加速（可跑满 4~10 MB/s）
				if (total > 4L * 1024 * 1024) {
					try {
						val numChunks = 3
						val chunkSize = (total + numChunks - 1) / numChunks
						val downloadedAtomic = AtomicLong(0L)
						var lastReportTime = System.currentTimeMillis()
						var lastReportBytes = 0L

						RandomAccessFile(destFile, "rw").use { raf ->
							raf.setLength(total)
						}

						coroutineScope {
							(0 until numChunks).map { index ->
								val start = index * chunkSize
								val end = minOf(total - 1L, (index + 1) * chunkSize - 1L)
								async(Dispatchers.IO) {
									val chunkReq = Request.Builder()
										.url(url)
										.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
										.header("Range", "bytes=$start-$end")
										.build()
									val chunkResp = downloadHttp.newCall(chunkReq).execute()
									if (chunkResp.code != 206 && chunkResp.code != 200) {
										throw IllegalStateException("Chunk $index HTTP ${chunkResp.code}")
									}
									val chunkBody = chunkResp.body ?: throw IllegalStateException("Chunk $index body empty")
									chunkBody.byteStream().use { stream ->
										RandomAccessFile(destFile, "rw").use { raf ->
											raf.seek(start)
											val buffer = ByteArray(64 * 1024)
											var read: Int
											while (stream.read(buffer).also { read = it } != -1) {
												raf.write(buffer, 0, read)
												val cur = downloadedAtomic.addAndGet(read.toLong())
												val now = System.currentTimeMillis()
												val dt = now - lastReportTime
												if (dt >= 200) {
													val speed = ((cur - lastReportBytes) * 1000) / dt
													lastReportTime = now
													lastReportBytes = cur
													onProgress(cur, total, speed)
												}
											}
										}
									}
								}
							}.awaitAll()
						}
						onProgress(total, total, 0L)
						return@withContext destFile
					} catch (e: Exception) {
						Log.w("UpdateManager", "Chunked download failed, falling back to single stream: ${e.message}")
						if (destFile.exists()) destFile.delete()
					}
				}

				// 单流常规下载（小文件或分块不支持时的兜底）
				val singleReq = Request.Builder()
					.url(url)
					.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NasDrop")
					.build()
				val singleResp = downloadHttp.newCall(singleReq).execute()
				if (!singleResp.isSuccessful) continue
				val singleBody = singleResp.body ?: continue
				val singleTotal = singleBody.contentLength()

				singleBody.byteStream().use { input ->
					FileOutputStream(destFile).use { output ->
						val buffer = ByteArray(64 * 1024)
						var read: Int
						var downloaded = 0L
						var lastTime = System.currentTimeMillis()
						var lastDownloaded = 0L

						while (input.read(buffer).also { read = it } != -1) {
							output.write(buffer, 0, read)
							downloaded += read

							val now = System.currentTimeMillis()
							val dt = now - lastTime
							if (dt >= 250) {
								val speed = ((downloaded - lastDownloaded) * 1000) / dt
								lastTime = now
								lastDownloaded = downloaded
								onProgress(downloaded, singleTotal, speed)
							}
						}
						onProgress(downloaded, singleTotal, 0L)
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
