package com.nasdrop

/**
 * NAS 系统运行状态与资源监控指标数据模型
 */
data class NasSystemInfo(
	val service: String = "",
	val version: String = "",
	val hostname: String = "",
	val os: String = "",
	val arch: String = "",
	val cpuCores: Int = 0,
	val cpuPercent: Double = 0.0,
	val loadAvg: List<Double> = emptyList(),
	val uptimeSeconds: Long = 0L,
	val serverUptimeSeconds: Long = 0L,
	val memory: MemoryMetrics = MemoryMetrics(),
	val disk: DiskMetrics = DiskMetrics(),
) {
	data class MemoryMetrics(
		val total: Long = 0L,
		val used: Long = 0L,
		val available: Long = 0L,
		val percent: Double = 0.0,
	)

	data class DiskMetrics(
		val path: String = "",
		val total: Long = 0L,
		val used: Long = 0L,
		val free: Long = 0L,
		val percent: Double = 0.0,
	)

	/** 格式化持续运行时间：例如 "2 天 14 小时 23 分" */
	fun formattedUptime(): String {
		val sec = if (uptimeSeconds > 0) uptimeSeconds else serverUptimeSeconds
		if (sec <= 0) return "刚启动"
		val days = sec / 86400
		val hours = (sec % 86400) / 3600
		val minutes = (sec % 3600) / 60
		return when {
			days > 0 -> "${days} 天 ${hours} 小时 ${minutes} 分"
			hours > 0 -> "${hours} 小时 ${minutes} 分"
			else -> "${minutes} 分"
		}
	}

	companion object {
		fun formatBytes(bytes: Long): String {
			if (bytes <= 0) return "0 B"
			val gb = bytes / (1024.0 * 1024.0 * 1024.0)
			if (gb >= 1.0) {
				return String.format(java.util.Locale.US, "%.1f GB", gb)
			}
			val mb = bytes / (1024.0 * 1024.0)
			if (mb >= 1.0) {
				return String.format(java.util.Locale.US, "%.1f MB", mb)
			}
			val kb = bytes / 1024.0
			return String.format(java.util.Locale.US, "%.0f KB", kb)
		}
	}
}
