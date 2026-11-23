package com.nasdrop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 应用内三路下载队列。暂停/取消真正关闭 Call，继续前等待旧写者退出。
 * 每个任务使用独立临时文件；publish 负责把完整文件发布到本机存储并返回可打开的 URI。
 */
class DownloadQueue(
	private val scope: CoroutineScope,
	private val tempDirectory: File,
	private val client: suspend () -> WebDavClient,
	private val publish: suspend (TransferTask, File) -> String,
) {
	private val state = MutableStateFlow<List<TransferTask>>(emptyList())
	val tasks: StateFlow<List<TransferTask>> = state
	private val jobs = ConcurrentHashMap<String, Job>()
	private val calls = ConcurrentHashMap<String, Call>()
	private val controls = Mutex()
	private val permits = Semaphore(3)
	private val log = Logger.getLogger("DownloadQueue")

	/** 相同远端路径运行中只保留一个任务；同名、不同目录的文件各自独立。 */
	@Synchronized
	fun enqueue(name: String, path: String, size: Long): TransferTask {
		state.value.firstOrNull { it.path == path && !it.finished }?.let { return it }
		val task = TransferTask(name = name, path = path, totalBytes = size)
		state.value = state.value + task
		start(task.id)
		return task
	}

	/** 暂停后保留片段和版本身份，返回前确保旧网络请求和写者已经结束。 */
	suspend fun pause(id: String) = controls.withLock {
		val task = find(id) ?: return@withLock
		if (task.status !in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED)) return@withLock
		change(id) { it.copy(status = TransferStatus.PAUSED, speedBytesPerSec = 0) }
		stop(id)
		log.info("下载已暂停 task=$id")
	}

	/** 单写者恢复：先等待旧工作结束，再读取片段长度并重新入队。 */
	suspend fun resume(id: String) = controls.withLock {
		if (find(id)?.status != TransferStatus.PAUSED) return@withLock
		stop(id)
		if (find(id)?.status != TransferStatus.PAUSED) return@withLock
		change(id) { it.copy(status = TransferStatus.QUEUED, error = null) }
		start(id)
	}

	/** 取消会保留结果行供查看/重试；尚未发布的临时片段在写者退出后移除。 */
	suspend fun cancel(id: String) = controls.withLock {
		if (find(id)?.finished != false) return@withLock
		change(id) { it.copy(status = TransferStatus.CANCELLED, error = null, speedBytesPerSec = 0) }
		stop(id)
		// 本机保存已提交时，真实完成结果优先于最后一刻到达的取消。
		if (find(id)?.status != TransferStatus.COMPLETED) clearPartial(id)
		log.info("下载取消结束 task=$id")
	}

	/** 失败保留的片段可安全续传；取消后的重试从零开始。 */
	suspend fun retry(id: String) = controls.withLock {
		val task = find(id) ?: return@withLock
		if (task.status !in setOf(TransferStatus.FAILED, TransferStatus.CANCELLED)) return@withLock
		check(state.value.none { it.id != id && it.path == task.path && !it.finished }) { "同一文件已在下载中" }
		stop(id)
		change(id) { it.copy(status = TransferStatus.QUEUED, error = null, sentBytes = partial(id).length()) }
		start(id)
	}

	/** 清除结果不会取消或隐藏仍在运行的传输。 */
	@Synchronized
	fun remove(id: String) { state.value = state.value.filterNot { it.id == id && it.finished } }

	@Synchronized
	fun clearFinished() { state.value = state.value.filterNot { it.finished } }

	/** ViewModel 销毁时同步终止阻塞 IO；应用内队列不承诺跨进程恢复。 */
	fun close() {
		jobs.values.forEach { it.cancel() }
		calls.values.forEach { it.cancel() }
	}

	private fun start(id: String) {
		if (jobs[id]?.isActive == true) return
		val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
			permits.withPermit {
				val task = find(id) ?: return@withPermit
				if (task.status != TransferStatus.QUEUED) return@withPermit
				change(id) { it.copy(status = TransferStatus.RUNNING, error = null) }
				try {
					tempDirectory.mkdirs()
					val c = client()
					val part = partial(id)
					val identity = validatorFile(id).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
					var offset = part.length()
					if (offset > 0 && identity == null) {
						// 没有身份标识无法证明内容未变化，继续操作安全重下。
						clearPartial(id)
						offset = 0
						log.info("下载无续传身份，重新读取 task=$id")
					}
					try {
						read(c, task, part, offset, identity)
					} catch (e: ResumeRestartRequiredException) {
						currentCoroutineContext().ensureActive()
						clearPartial(id)
						log.info("下载范围或身份变化，重新读取 task=$id http=${e.code}")
						read(c, task, part, 0, null)
					}
					currentCoroutineContext().ensureActive()
					val uri = publish(find(id) ?: task, part)
					// publish 返回即已保存成功；此后迟到的取消不应丢失可打开的结果。
					change(id) { it.copy(status = TransferStatus.COMPLETED, resultUri = uri,
						sentBytes = part.length(), totalBytes = part.length(), speedBytesPerSec = 0) }
					clearPartial(id)
					log.info("下载已保存 task=$id")
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					currentCoroutineContext().ensureActive()
					change(id) { current ->
						if (current.status == TransferStatus.RUNNING) current.copy(status = TransferStatus.FAILED,
							error = e.message ?: "下载失败", speedBytesPerSec = 0) else current
					}
					log.log(Level.WARNING, "下载失败 task=$id", e)
				} finally {
					calls.remove(id)
				}
			}
		}
		jobs[id] = job
		job.invokeOnCompletion { jobs.remove(id, job) }
		job.start()
	}

	/** 网络进度发布在每次真实读取后，取消信号在后续写入前再次检查。平滑 EMA 限频更新网速，告别跳变。 */
	private suspend fun read(c: WebDavClient, task: TransferTask, part: File, start: Long, validator: String?) {
		var lastBytes = start
		var lastTime = System.currentTimeMillis()
		var smoothedSpeed = 0L
		var lastEmitTime = 0L
		FileOutputStream(part, start > 0).use { output ->
			c.download(task.path, output, startByte = start,
				onCall = { call ->
					calls[task.id] = call
					if (find(task.id)?.status != TransferStatus.RUNNING) call.cancel()
				},
				validator = validator,
				onValidator = { value ->
					if (value == null) validatorFile(task.id).delete() else validatorFile(task.id).writeText(value)
				},
			) { sent, total ->
				val now = System.currentTimeMillis()
				val elapsed = now - lastTime
				if (elapsed >= 250 || sent == total) {
					val dt = elapsed.coerceAtLeast(1)
					val dBytes = (sent - lastBytes).coerceAtLeast(0)
					val instantSpeed = (dBytes * 1000L / dt)
					smoothedSpeed = if (smoothedSpeed <= 0L) instantSpeed else ((smoothedSpeed * 0.7) + (instantSpeed * 0.3)).toLong()
					lastBytes = sent
					lastTime = now

					if (now - lastEmitTime >= 200 || sent == total) {
						change(task.id) { current ->
							if (current.status != TransferStatus.RUNNING) current
							else current.copy(sentBytes = sent, totalBytes = total, speedBytesPerSec = smoothedSpeed)
						}
						lastEmitTime = now
					}
				}
			}
		}
	}

	private suspend fun stop(id: String) {
		val job = jobs[id]
		// 先标记协程取消，阻塞 IO 的异常才不会抢先把暂停/取消覆盖成失败。
		job?.cancel()
		calls[id]?.cancel()
		job?.join()
	}
	private fun partial(id: String) = File(tempDirectory, "$id.part")
	private fun validatorFile(id: String) = File(tempDirectory, "$id.validator")
	private fun clearPartial(id: String) { partial(id).delete(); validatorFile(id).delete() }
	private fun find(id: String) = state.value.firstOrNull { it.id == id }
	@Synchronized
	private fun change(id: String, block: (TransferTask) -> TransferTask) {
		state.value = state.value.map { if (it.id == id) block(it) else it }
	}
}
