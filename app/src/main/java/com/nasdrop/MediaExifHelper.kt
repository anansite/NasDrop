package com.nasdrop

import android.content.Context
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.Locale

data class MediaExifData(
	val camera: String? = null,
	val lens: String? = null,
	val aperture: String? = null,
	val shutterSpeed: String? = null,
	val iso: String? = null,
	val focalLength: String? = null,
	val latitude: Double? = null,
	val longitude: Double? = null,
	val altitude: Double? = null,
	val resolution: String? = null,
	val megapixels: String? = null,
) {
	val hasShootingParams: Boolean
		get() = !camera.isNullOrBlank() || !aperture.isNullOrBlank() || !shutterSpeed.isNullOrBlank() || !iso.isNullOrBlank() || !focalLength.isNullOrBlank()

	val hasGps: Boolean
		get() = latitude != null && longitude != null

	val gpsFormatted: String?
		get() {
			val lat = latitude ?: return null
			val lng = longitude ?: return null
			val latDir = if (lat >= 0) "N" else "S"
			val lngDir = if (lng >= 0) "E" else "W"
			return String.format(Locale.ROOT, "%.4f° %s, %.4f° %s", kotlin.math.abs(lat), latDir, kotlin.math.abs(lng), lngDir)
		}
}

object MediaExifHelper {
	private const val TAG = "MediaExifHelper"

	suspend fun extractExif(
		context: Context,
		entry: DavEntry,
		url: String?,
		okHttpClient: OkHttpClient?,
	): MediaExifData? = withContext(Dispatchers.IO) {
		try {
			// 1. 仅尝试本地原图磁盘缓存（绝不使用 LocalThumbs / thumb: 缩略图缓存，因为缩略图已剥离 EXIF 与 GPS）
			val localFile = findLocalOriginalFile(context, url)
			if (localFile != null && localFile.exists() && localFile.length() > 0) {
				val parsed = parseExifFromFile(localFile)
				if (parsed != null && (parsed.hasShootingParams || parsed.hasGps)) {
					return@withContext parsed
				}
			}

			// 2. 通过 HTTP Range 请求极速抓取原图头部 256KB（兼容 JPEG/HEIC 等含较大 XMP/MakerNote 的 APP1 头）
			if (!url.isNullOrBlank() && okHttpClient != null) {
				val req = Request.Builder()
					.url(url)
					.header("Range", "bytes=0-262143")
					.build()
				try {
					okHttpClient.newCall(req).execute().use { resp ->
						if (resp.isSuccessful || resp.code == 206) {
							resp.body?.byteStream()?.let { stream ->
								return@withContext parseExifFromStream(stream)
							}
						}
					}
				} catch (e: Exception) {
					Log.v(TAG, "HTTP Range EXIF fetch failed for $url: ${e.message}")
				}
			}
			null
		} catch (e: Exception) {
			Log.w(TAG, "EXIF extract error: ${e.message}")
			null
		}
	}

	private fun findLocalOriginalFile(context: Context, url: String?): File? {
		if (url.isNullOrBlank()) return null
		try {
			val diskCache = context.app.repo.imageLoader.diskCache
			val snap = diskCache?.openSnapshot(url)
			snap?.use { return it.data.toFile() }
		} catch (_: Exception) {}
		return null
	}

	fun parseExifFromFile(file: File): MediaExifData? {
		return try {
			val exif = ExifInterface(file.absolutePath)
			parseExif(exif)
		} catch (_: Exception) {
			null
		}
	}

	fun parseExifFromStream(stream: InputStream): MediaExifData? {
		return try {
			val exif = ExifInterface(stream)
			parseExif(exif)
		} catch (_: Exception) {
			null
		}
	}

	private fun parseExif(exif: ExifInterface): MediaExifData {
		val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
		val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
		val camera = when {
			!make.isNullOrBlank() && !model.isNullOrBlank() -> {
				if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
			}
			!model.isNullOrBlank() -> model
			!make.isNullOrBlank() -> make
			else -> null
		}

		val lens = exif.getAttribute("LensModel")?.trim()
			?: exif.getAttribute("LensMake")?.trim()

		val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.toDoubleOrNull()
		val aperture = fNumber?.let { String.format(Locale.ROOT, "f/%.1f", it).replace(".0", "") }

		val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.toDoubleOrNull()
		val shutterSpeed = expTime?.let {
			if (it >= 1.0) {
				String.format(Locale.ROOT, "%.1fs", it).replace(".0s", "s")
			} else if (it > 0.0) {
				val denom = kotlin.math.round(1.0 / it).toInt()
				"1/${denom}s"
			} else null
		}

		val isoVal = exif.getAttribute("PhotographicSensitivity")
			?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
		val iso = isoVal?.let { "ISO $it" }

		val focalRaw = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.toDoubleOrNull()
		val focalIn35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM)
		val focalLength = when {
			focalRaw != null && !focalIn35mm.isNullOrBlank() -> {
				val rawStr = String.format(Locale.ROOT, "%.1fmm", focalRaw).replace(".0mm", "mm")
				"$rawStr (等效 ${focalIn35mm}mm)"
			}
			focalRaw != null -> String.format(Locale.ROOT, "%.1fmm", focalRaw).replace(".0mm", "mm")
			!focalIn35mm.isNullOrBlank() -> "${focalIn35mm}mm"
			else -> null
		}

		val latLong = FloatArray(2)
		val hasGps = exif.getLatLong(latLong)
		val lat = if (hasGps) latLong[0].toDouble() else null
		val lng = if (hasGps) latLong[1].toDouble() else null
		val alt = exif.getAltitude(0.0).takeIf { it != 0.0 }

		val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
		val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
		val resolution = if (width > 0 && height > 0) "$width × $height" else null
		val megapixels = if (width > 0 && height > 0) {
			val mp = (width.toLong() * height) / 1_000_000.0
			String.format(Locale.ROOT, "%.1f MP", mp)
		} else null

		return MediaExifData(
			camera = camera,
			lens = lens,
			aperture = aperture,
			shutterSpeed = shutterSpeed,
			iso = iso,
			focalLength = focalLength,
			latitude = lat,
			longitude = lng,
			altitude = alt,
			resolution = resolution,
			megapixels = megapixels,
		)
	}
}
