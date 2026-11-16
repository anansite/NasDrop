package com.nasdrop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 一队上传任务。分享流程和上传服务之间通过它传递进度，
 * 所以 ShareActivity 能在上传期间保持在最前面（这样 content:// 的读取授权才不会失效）。
 */
data class UploadTask(
	val uri: String,
	val name: String,
	val sent: Long = 0,
	val total: Long = -1,
	val done: Boolean = false,
	val error: String? = null,
) {
	val progress: Float
		get() = when {
			done && error == null -> 1f
			total > 0 -> (sent.toFloat() / total).coerceIn(0f, 1f)
			else -> 0f
		}
}

object Uploads {

	private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
	val tasks: StateFlow<List<UploadTask>> = _tasks

	// 上传全部结束通知流（通知前台页面自动刷新）
	val uploadCompletedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 2)

	@Synchronized
	fun begin(items: List<Pair<String, String>>) {
		_tasks.value = items.map { UploadTask(uri = it.first, name = it.second) }
	}

	@Synchronized
	fun update(index: Int, block: (UploadTask) -> UploadTask) {
		val cur = _tasks.value
		if (index !in cur.indices) return
		val copy = cur.toMutableList()
		copy[index] = block(copy[index])
		_tasks.value = copy
	}

	fun notifyFinished() {
		uploadCompletedEvent.tryEmit(Unit)
	}

	@Synchronized
	fun clear() {
		_tasks.value = emptyList()
	}

	fun running(): Boolean = _tasks.value.any { !it.done }
}
