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
	 * 单个文件的上传生命周期。
	 *
	 * 为什么要分三态，而不是只在 PUT 返回后广播一次：
	 * 字节写完（SENT）≠ 服务端返回 201（CONFIRMED）。实测服务端在大文件收完后还要
	 * 落盘/rename 一小段时间，这段时间旧实现只能干等，UI 上就是"已发送完成，等待服务端写入…"
	 * 卡很久。现在 SENT 的那一刻就把文件乐观插进列表（本地缩略图已经就绪），
	 * 201 到达后再做一次静默校正；真失败则回滚掉这条，不留幻影。
	 *
	 * 不用 replay —— 增量事件，重放会让 ViewModel 重复插入同一条。
	 */
	val fileLifecycle = MutableSharedFlow<FileEvent>(extraBufferCapacity = 64)

	enum class FilePhase { SENT, CONFIRMED, FAILED }

	data class FileEvent(
		val dir: String,
		val name: String,
		val size: Long,
		val phase: FilePhase,
	)

	fun notifyFile(dir: String, name: String, size: Long, phase: FilePhase) {
		fileLifecycle.tryEmit(FileEvent(dir.trim('/'), name, size, phase))
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
