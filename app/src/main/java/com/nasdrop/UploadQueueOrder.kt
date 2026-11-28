package com.nasdrop

/**
 * 统一的上传与传输列表稳定排序。
 *
 * 将"正在传输"与"等待服务端确认"归在同一活跃层级，并按进入并发槽位次序/提交序号排列，
 * 避免切换至确认阶段或瞬时速率变化时行与行之间来回跳动。
 */
internal object UploadQueueOrder {

	fun sortUploadTasks(tasks: List<UploadTask>): List<UploadTask> =
		tasks.sortedWith(
			compareBy<UploadTask> { uploadTier(it) }
				.thenBy { if (it.started && !it.done && !it.cancelled && it.error == null) it.writeOrder else Long.MAX_VALUE }
				.thenBy { it.seq }
				.thenBy { it.createdAt }
				.thenBy { it.id },
		)

	fun sortTransferTasks(tasks: List<TransferTask>): List<TransferTask> =
		tasks.sortedWith(
			compareBy<TransferTask> { transferTier(it) }
				.thenBy {
					if (it.status == TransferStatus.RUNNING || it.status == TransferStatus.CONFIRMING) {
						it.activeOrder
					} else {
						Long.MAX_VALUE
					}
				}
				.thenBy { it.seq }
				.thenBy { it.lastUpdated }
				.thenBy { it.id },
		)

	private fun uploadTier(task: UploadTask): Int = when {
		task.started && !task.done && !task.cancelled && task.error == null -> 0
		!task.started && !task.done && !task.cancelled && task.error == null -> 1
		task.error != null && !task.cancelled -> 2
		task.cancelled -> 3
		else -> 4
	}

	private fun transferTier(task: TransferTask): Int = when (task.status) {
		TransferStatus.RUNNING, TransferStatus.CONFIRMING -> 0
		TransferStatus.QUEUED -> 1
		TransferStatus.PAUSED -> 2
		TransferStatus.FAILED -> 3
		TransferStatus.CANCELLED -> 4
		TransferStatus.COMPLETED -> 5
	}
}
