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

	// 上传全部结束通知流（通知前台页面自动刷新）。
	// replay = 1：上传服务在后台完成时，前台 Activity/ViewModel 可能刚刚重建、
	// 尚未订阅；replay=0 会把事件直接丢掉，表现为"传完了要等一会儿/手动刷新才出现"。
	val uploadCompletedEvent = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 2)

	/**
	 * 单个文件上传成功（PUT 已拿到成功响应）就立刻广播一条。
	 *
	 * 之所以不等整批结束：用户传 3 个视频时，第一个传完就该立刻出现在列表里，
	 * 而不是等最后一个、再等一次网络刷新。ViewModel 收到后直接把这条记录
	 * **乐观插入**目录缓存与当前列表，做到"传完即见"，网络刷新只做后台校正。
	 *
	 * 不用 replay —— 它是增量事件，重放会让 ViewModel 重复插入同一条。
	 */
	val fileUploadedEvent = MutableSharedFlow<UploadedFile>(extraBufferCapacity = 32)

	data class UploadedFile(val dir: String, val name: String, val size: Long)

	fun notifyFileUploaded(dir: String, name: String, size: Long) {
		fileUploadedEvent.tryEmit(UploadedFile(dir.trim('/'), name, size))
	}

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
