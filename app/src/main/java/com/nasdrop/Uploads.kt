package com.nasdrop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
	val id: String = UUID.randomUUID().toString(),
	val batchId: String = "",
	val dir: String = "",
	val started: Boolean = false,
	val bodySent: Boolean = false,
	val createdAt: Long = System.currentTimeMillis(),
	/** 已被某次远端列表确认；新的页面无需再把历史结果当作新增文件重放。 */
	val listed: Boolean = false,
	/** 后续删除/移动成功后关闭列表贡献，保留传输结果供查阅。 */
	val listingActive: Boolean = true,
	val dismissed: Boolean = false,
	val cancelled: Boolean = false,
	val postProcessed: Boolean = false,
	val writeOrder: Long = 0L,
	val seq: Long = 0L,
	val ownerKey: String = "",
) {
	val progress: Float
		get() = when {
			done && error == null -> 1f
			total > 0 -> (sent.toFloat() / total).coerceIn(0f, 1f)
			else -> 0f
		}
}

/** 进程内上传记录；稳定 ID 隔离批次，状态快照供页面重建后恢复进度与列表。 */
object Uploads {
	/** 用于识别分享页进程重建；旧进程的内存队列不能继续显示为上传中。 */
	val sessionId: String = UUID.randomUUID().toString()

	private val _tasks = MutableStateFlow<List<UploadTask>>(emptyList())
	val tasks: StateFlow<List<UploadTask>> = _tasks
	private val calls = ConcurrentHashMap<String, Call>()
	private val writeSequence = AtomicLong()
	private val taskSequence = AtomicLong()

	/** 同路径串行写入时记录实际次序，合并进度快照不能把较早内容当成最终覆盖。 */
	fun markStarted(id: String, total: Long) {
		val order = writeSequence.incrementAndGet()
		update(id) { it.copy(started = true, total = total, writeOrder = order) }
	}

	/** 追加一批任务并返回其稳定标识；若上一批次已经全部结束，新批次自动清理上一批次历史。 */
	@Synchronized
	fun begin(
		items: List<Pair<String, String>>,
		dir: String = "",
		batchId: String = UUID.randomUUID().toString(),
	): List<UploadTask> {
		val added = items.map { (uri, name) ->
			UploadTask(uri = uri, name = name, batchId = batchId, dir = dir.trim('/'), seq = taskSequence.incrementAndGet())
		}
		// 若已有任务均已结束（即上一批次已全部完成），新批次开始时清理上一批次历史，避免百分比和列表叠加
		val existing = if (_tasks.value.isNotEmpty() && _tasks.value.all { it.done }) {
			emptyList()
		} else {
			_tasks.value.filterNot { it.dismissed }
		}
		_tasks.value = existing + added
		return added
	}

	/** 按任务 ID 更新进度，避免并行批次共享数组下标导致串写。 */
	@Synchronized
	fun update(id: String, block: (UploadTask) -> UploadTask) {
		_tasks.value = _tasks.value.map { if (it.id == id && !it.cancelled) block(it) else it }
	}

	/** 注册当前预检/PUT 请求；取消发生在注册前时同样立即终止新请求。 */
	@Synchronized
	fun registerCall(id: String, call: Call) {
		calls[id] = call
		if (_tasks.value.firstOrNull { it.id == id }?.cancelled == true) call.cancel()
	}

	/** 批次工作结束后释放请求引用，不保留已完成网络对象。 */
	fun clearCall(id: String, cancel: Boolean = false) {
		val call = calls.remove(id)
		if (cancel) call?.cancel()
	}

	/** 用户取消既终止实际请求，也将状态固定为已取消；其他批次不受影响。 */
	@Synchronized
	fun cancel(id: String) {
		if (_tasks.value.none { it.id == id && !it.done }) return
		_tasks.value = _tasks.value.map {
			if (it.id == id) it.copy(done = true, cancelled = true, error = "已取消", listingActive = false) else it
		}
		calls[id]?.cancel()
	}

	/** 仅隐藏已结束结果，运行中任务与目录同步元数据保留。 */
	@Synchronized
	fun clearFinished() {
		_tasks.value = _tasks.value.map { if (it.done) it.copy(dismissed = true) else it }
	}

	/** 仅清除已结束的指定记录，运行中的网络请求不受影响。 */
	@Synchronized
	fun remove(id: String) {
		// ponytail: 清除仅隐藏结果，进程期保留少量同步元数据；海量历史再迁移独立持久化队列。
		_tasks.value = _tasks.value.map { if (it.id == id && it.done) it.copy(dismissed = true) else it }
	}

	/** 跨页面共享目录确认状态，避免重建 ViewModel 时重放已经同步的成功历史。 */
	@Synchronized
	fun markListed(ids: Set<String>) {
		if (ids.isEmpty()) return
		_tasks.value = _tasks.value.map { if (it.id in ids && it.done && it.error == null) it.copy(listed = true) else it }
	}

	/** 删除或移动成功才使旧上传项失效；新上传使用新 ID，仍能正常显示同名文件。 */
	@Synchronized
	fun forgetPaths(paths: Collection<String>) {
		_tasks.value = _tasks.value.map { task ->
			val path = listOf(task.dir, task.name).filter { it.isNotEmpty() }.joinToString("/")
			if (paths.any { path == it || path.startsWith("$it/") }) task.copy(listingActive = false) else task
		}
	}

	@Synchronized
	fun markPostProcessed(ids: Collection<String>) {
		if (ids.isEmpty()) return
		val idSet = ids.toSet()
		_tasks.value = _tasks.value.map { if (it.id in idSet) it.copy(postProcessed = true) else it }
	}

	fun running(): Boolean = _tasks.value.any { !it.done }
}
