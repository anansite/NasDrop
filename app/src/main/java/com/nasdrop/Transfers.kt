package com.nasdrop

import java.util.Locale
import java.util.UUID

/** 传输状态与真实网络操作一致，已取消与失败分别展示。 */
enum class TransferStatus {
	QUEUED, RUNNING, CONFIRMING, PAUSED, COMPLETED, FAILED, CANCELLED,
}

/** 上传/下载统一展示记录；resultUri 仅在本机下载成功发布后赋值。 */
data class TransferTask(
	val id: String = UUID.randomUUID().toString(),
	val name: String,
	val path: String,
	val isUpload: Boolean = false,
	var totalBytes: Long = 0L,
	var sentBytes: Long = 0L,
	var speedBytesPerSec: Long = 0L,
	var status: TransferStatus = TransferStatus.QUEUED,
	var error: String? = null,
	var lastUpdated: Long = System.currentTimeMillis(),
	val resultUri: String? = null,
	val directory: String? = null,
	val seq: Long = 0L,
	val activeOrder: Long = Long.MAX_VALUE,
) {
	val finished: Boolean get() = status in setOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)
	val progress: Float
		get() = if (status == TransferStatus.COMPLETED) 1f
		else if (totalBytes > 0) (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
	val speedStr: String
		get() {
			if (speedBytesPerSec <= 0) return "--"
			val mb = speedBytesPerSec / (1024.0 * 1024.0)
			return if (mb >= 1.0) String.format(Locale.ROOT, "%.1f MB/s", mb)
			else String.format(Locale.ROOT, "%.0f KB/s", speedBytesPerSec / 1024.0)
		}
}
