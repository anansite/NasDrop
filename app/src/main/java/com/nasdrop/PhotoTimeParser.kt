package com.nasdrop

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ExifInfo(
	val takenAt: Long? = null,
	val width: Int = 0,
	val height: Int = 0,
	val orientation: Int = 0,
)

/**
 * 手机端拍摄时间与图片元数据解析器。
 * 纯手机端本地运行，零网络开销，支持从文件名和 EXIF 标签中提取真实拍摄时间。
 */
object PhotoTimeParser {

	private const val TAG = "PhotoTimeParser"

	// 常见文件名正则匹配模式
	// 模式 1: 常见相机/手机前缀 + yyyyMMdd_HHmmss (如 IMG_20240315_143022, VID_20231001_091500, PXL_20220812_...)
	private val PATTERN_PREFIX_DATE_TIME = Pattern.compile(
		"(?:IMG|VID|PXL|PHOTO|MOV|PIC|IMAGE|DSC)[-_]?(\\d{4})(\\d{2})(\\d{2})[-_](\\d{2})(\\d{2})(\\d{2})",
		Pattern.CASE_INSENSITIVE
	)

	// 模式 2: yyyy-MM-dd HH.mm.ss / yyyyMMdd_HHmmss / yyyy.MM.dd_HH.mm.ss
	private val PATTERN_DATE_TIME = Pattern.compile(
		"(\\d{4})[-_.]?(\\d{2})[-_.]?(\\d{2})[-_ ]+(\\d{2})[-_.:](\\d{2})[-_.:](\\d{2})"
	)

	// 模式 3: 纯年月日 (如 2024-03-15, 20240315, 2024.03.15)
	private val PATTERN_DATE_ONLY = Pattern.compile(
		"(?:^|[-_ \\(\\[])(\\d{4})[-_.]?(\\d{2})[-_.]?(\\d{2})(?:$|[-_ \\.\\]\\)])"
	)

	// 模式 4: 微信/社交应用时间戳 (13位毫秒时间戳, 如 wx_camera_1684038645000, mmexport1684038645000)
	private val PATTERN_TIMESTAMP_MS = Pattern.compile(
		"(?:wx_camera_|mmexport|time_)?(1[5-7]\\d{11})"
	)

	// 模式 5: 10位秒级时间戳 (如 1684038645)
	private val PATTERN_TIMESTAMP_SEC = Pattern.compile(
		"(?:^|[-_])(1[5-7]\\d{8})(?:$|[-_\\.])"
	)

	private val EXIF_DATE_FORMAT = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT)

	/**
	 * 从文件名解析拍摄时间（毫秒时间戳）。
	 * 若未匹配到合理的时间（1990年~2040年之间），返回 null。
	 */
	fun parseFromFilename(filename: String): Long? {
		if (filename.isBlank()) return null
		try {
			// 1. 尝试前缀+完整年月日时间
			val m1 = PATTERN_PREFIX_DATE_TIME.matcher(filename)
			if (m1.find()) {
				val cal = buildCalendar(
					m1.group(1).toInt(), m1.group(2).toInt(), m1.group(3).toInt(),
					m1.group(4).toInt(), m1.group(5).toInt(), m1.group(6).toInt()
				)
				if (isValidDate(cal)) return cal.timeInMillis
			}

			// 2. 尝试无前缀标准格式
			val m2 = PATTERN_DATE_TIME.matcher(filename)
			if (m2.find()) {
				val cal = buildCalendar(
					m2.group(1).toInt(), m2.group(2).toInt(), m2.group(3).toInt(),
					m2.group(4).toInt(), m2.group(5).toInt(), m2.group(6).toInt()
				)
				if (isValidDate(cal)) return cal.timeInMillis
			}

			// 3. 尝试 13 位毫秒时间戳
			val m4 = PATTERN_TIMESTAMP_MS.matcher(filename)
			if (m4.find()) {
				val ts = m4.group(1).toLongOrNull()
				if (ts != null && isValidTimestamp(ts)) return ts
			}

			// 4. 尝试纯年月日
			val m3 = PATTERN_DATE_ONLY.matcher(filename)
			if (m3.find()) {
				val cal = buildCalendar(
					m3.group(1).toInt(), m3.group(2).toInt(), m3.group(3).toInt(),
					12, 0, 0 // 默认设为中午
				)
				if (isValidDate(cal)) return cal.timeInMillis
			}

			// 5. 尝试 10 位秒级时间戳
			val m5 = PATTERN_TIMESTAMP_SEC.matcher(filename)
			if (m5.find()) {
				val ts = m5.group(1).toLongOrNull()?.times(1000L)
				if (ts != null && isValidTimestamp(ts)) return ts
			}
		} catch (e: Exception) {
			Log.v(TAG, "文件名时间解析异常: $filename", e)
		}
		return null
	}

	/**
	 * 从本地 Uri 中提取 EXIF 拍摄时间、宽高及旋转信息（用于手机上传时就地读取）
	 */
	fun extractExif(context: Context, uri: Uri): ExifInfo {
		val targetUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
			uri.authority == "media" &&
			androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_MEDIA_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
		) {
			runCatching { android.provider.MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
		} else uri
		return try {
			(runCatching { context.contentResolver.openInputStream(targetUri) }.getOrNull()
				?: context.contentResolver.openInputStream(uri))?.use { ins ->
				val exif = ExifInterface(ins)
				val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
					?: exif.getAttribute(ExifInterface.TAG_DATETIME)
				val takenAt = dateStr?.let { parseExifDate(it) }
				val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
				val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
				val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
				ExifInfo(takenAt = takenAt, width = width, height = height, orientation = orientation)
			} ?: ExifInfo()
		} catch (e: Exception) {
			Log.v(TAG, "Uri EXIF 提取失败: $uri", e)
			ExifInfo()
		}
	}

	private fun parseExifDate(dateStr: String): Long? {
		return try {
			synchronized(EXIF_DATE_FORMAT) {
				EXIF_DATE_FORMAT.parse(dateStr)?.time
			}
		} catch (_: Exception) {
			null
		}
	}

	private fun buildCalendar(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int): Calendar {
		return Calendar.getInstance().apply {
			set(Calendar.YEAR, year)
			set(Calendar.MONTH, month - 1)
			set(Calendar.DAY_OF_MONTH, day)
			set(Calendar.HOUR_OF_DAY, hour)
			set(Calendar.MINUTE, min)
			set(Calendar.SECOND, sec)
			set(Calendar.MILLISECOND, 0)
		}
	}

	private fun isValidDate(cal: Calendar): Boolean {
		val year = cal.get(Calendar.YEAR)
		val month = cal.get(Calendar.MONTH) + 1
		val day = cal.get(Calendar.DAY_OF_MONTH)
		return year in 1990..2040 && month in 1..12 && day in 1..31
	}

	private fun isValidTimestamp(ts: Long): Boolean {
		// 2000-01-01 到 2040-01-01 之间的有效毫秒数
		return ts in 946684800000L..2208988800000L
	}
}
