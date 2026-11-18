package com.nasdrop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 后台上传队列。
 *
 * 之所以做成前台服务而不是在 Activity 里直接传：用户分享完就会切回微信/相册，
 * 大文件（视频）传完前进程不能被杀。这里不带 VPN、不做网络代理，
 * 网络出口全部复用 [Repository]（内嵌 Tailscale 已经在那层起好）。
 */
class UploadService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	// 所有批次共享三路通道，连续添加文件也不会按批次放大并发数。
	private val semaphore = Semaphore(MAX_PARALLEL)
	private var activeBatches = 0
	private val pathLocks = ConcurrentHashMap<String, Mutex>()

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		ensureChannel()
	}

	override fun onDestroy() {
		scope.cancel()
		val interrupted = Uploads.tasks.value.filter { !it.done }
		if (interrupted.isNotEmpty()) {
			Log.w("UploadService", "服务结束，上传中断 count=${interrupted.size}")
			interrupted.forEach { task ->
				Uploads.clearCall(task.id, cancel = true)
				Uploads.update(task.id) { it.copy(done = true, error = "上传已中断，请重新选择文件") }
			}
		}
		super.onDestroy()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val dir = intent?.getStringExtra(EXTRA_DIR).orEmpty()
		val batchId = intent?.getStringExtra(EXTRA_BATCH) ?: UUID.randomUUID().toString()
		val uris = intent?.getStringArrayListExtra(EXTRA_URIS)
			?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
			.orEmpty()

		if (uris.isEmpty()) {
			stopSelf(startId)
			return START_NOT_STICKY
		}

		val app = applicationContext as NasDropApp
		activeBatches++

		ServiceCompat.startForeground(
			this,
			NOTIF_ID,
			build("准备上传", null, true),
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
		)

		scope.launch { upload(app, uris, dir, batchId) }
		return START_NOT_STICKY
	}

	/** 批次独立计数与稳定任务 ID；缩略图生成不参与上传完成判定。 */
	private suspend fun upload(app: NasDropApp, uris: List<Uri>, dir: String, batchId: String) {
		val tasks = Uploads.begin(uris.map { it.toString() to Query.nameOf(this, it) }, dir, batchId)
		Log.i("UploadService", "批次开始 batch=$batchId dir=$dir count=${tasks.size}")
		val client = try {
			val settings = app.prefs.current()
			tasks.forEach { task -> Uploads.update(task.id) { it.copy(ownerKey = settings.serverIdentity) } }
			app.repo.client(settings)
		} catch (t: Exception) {
			if (t is CancellationException) throw t
			Log.e("UploadService", "批次连接失败 batch=$batchId dir=$dir", t)
			tasks.forEach { task ->
				Uploads.update(task.id) { it.copy(done = true, error = t.message ?: "连接失败") }
			}
			finishBatch(tasks, batchId)
			return
		}

		// 大文件优先拿到通道，减少在队尾等待；所有批次仍共享三路并发上限。
		val queue = tasks.map { task -> Triple(task, Uri.parse(task.uri), Query.sizeOf(this@UploadService, Uri.parse(task.uri))) }
			.sortedByDescending { it.third }

		val jobs = queue.map { (task, uri, size) ->
			scope.launch {
				// 同名目标串行写入，防止两个 PUT 同时覆盖同一个远端文件。
				pathLocks.getOrPut("${task.dir}/${task.name}") { Mutex() }.withLock {
				semaphore.withPermit {
					if (Uploads.tasks.value.firstOrNull { it.id == task.id }?.cancelled == true) return@withPermit
					val name = task.name
					val startedAt = System.currentTimeMillis()
					Uploads.markStarted(task.id, size)
					Log.i("UploadService", "发送开始 batch=$batchId task=${task.id} dir=$dir bytes=$size")
					val dirKey = dir.trim('/')
					val entryPath = if (dirKey.isEmpty()) name else "$dirKey/$name"
					val ext = name.substringAfterLast('.', "").lowercase()
					val isVideo = ext in DavEntry.VIDEOS
					val isImage = ext in DavEntry.IMAGES

					// 本地缩略图与上传并行跑：等字节传完时它早就好了，
					// 于是"传完那一刻"列表里就是真图，而不是一片灰（视频连图都没有）。
					// 万一它晚到，LocalThumbs.revision 自增会触发列表重组自动换上。
					scope.launch {
						runCatching {
							if (isVideo || isImage) {
								LocalThumbs.ensure(this@UploadService, uri, entryPath, isVideo)
							}
						}
					}

					var lastNotified = 0L
					try {
						client.upload(
							this@UploadService,
							uri,
							dir,
							onProgress = { sent, total ->
								Uploads.update(task.id) { it.copy(sent = sent, total = total) }
								if (sent - lastNotified >= 512 * 1024 || (total > 0 && sent >= total)) {
									lastNotified = sent
									post(build("正在上传「$name」", if (total > 0) sent to total else null, false))
								}
							},
							onBodySent = {
								// 已发送与服务端成功分别记录；列表立即可见，任务继续等待 PUT 响应。
								Uploads.update(task.id) { it.copy(bodySent = true, total = if (it.total >= 0) it.total else it.sent) }
								Log.i("UploadService", "发送结束，等待确认 batch=$batchId task=${task.id} elapsedMs=${System.currentTimeMillis() - startedAt}")
							},
							onCall = { call -> Uploads.registerCall(task.id, call) },
						)
						Uploads.update(task.id) { it.copy(done = true, bodySent = true) }
						Log.i("UploadService", "服务端确认 batch=$batchId task=${task.id} elapsedMs=${System.currentTimeMillis() - startedAt}")
					} catch (t: Exception) {
						if (t is CancellationException) throw t
						if (Uploads.tasks.value.firstOrNull { it.id == task.id }?.cancelled == true) {
							Log.i("UploadService", "上传已取消 batch=$batchId task=${task.id}")
						} else {
							Uploads.update(task.id) { it.copy(done = true, error = t.message ?: t.javaClass.simpleName) }
							Log.e("UploadService", "上传失败 batch=$batchId task=${task.id} dir=$dir", t)
						}
					} finally {
						Uploads.clearCall(task.id)
					}
				}
				}
			}
		}
		jobs.joinAll()

		finishBatch(tasks, batchId)
	}

	/** 按本批最终记录统计，取消不会被误算成失败或“零个文件上传成功”。 */
	private fun finishBatch(tasks: List<UploadTask>, batchId: String) {
		val ids = tasks.map { it.id }.toSet()
		val results = Uploads.tasks.value.filter { it.id in ids }
		finish(results.count { it.done && it.error == null && !it.cancelled },
			results.count { it.error != null && !it.cancelled }, batchId, results.count { it.cancelled })
	}

	/** 主线程串行结束批次，仍有其他批次时保留前台服务与通知。 */
	private fun finish(ok: Int, failed: Int, batchId: String, cancelled: Int = 0) {
		Handler(Looper.getMainLooper()).post { finishOnMain(ok, failed, batchId, cancelled) }
	}

	private fun finishOnMain(ok: Int, failed: Int, batchId: String, cancelled: Int) {
		activeBatches--
		Log.i("UploadService", "批次结束 batch=$batchId success=$ok failed=$failed cancelled=$cancelled activeBatches=$activeBatches")
		val text = when {
			cancelled > 0 -> "$ok 个成功，$failed 个失败，$cancelled 个已取消"
			failed == 0 -> "$ok 个文件已存入"
			ok == 0 -> "上传失败，共 $failed 个"
			else -> "$ok 个成功，$failed 个失败"
		}
		val largeIcon = this.largeIcon
		val n = NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_notification)
			.apply { if (largeIcon != null) setLargeIcon(largeIcon) }
			.setContentTitle("NasDrop")
			.setContentText(text)
			.setAutoCancel(true)
			.setContentIntent(openApp())
			.build()
		manager().notify(NOTIF_ID + 1, n)
		if (activeBatches == 0) {
			ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
			stopSelf()
		}
	}

	private fun post(n: Notification) = manager().notify(NOTIF_ID, n)

	/**
	 * 通知大图标只解码一次。
	 * 旧实现在每次进度回调（每 512KB 一次）里都 decodeResource 一遍，上传 300MB 就是
	 * 几百次解码，全部压在上传线程上——纯属自己拖慢自己。
	 */
	private val largeIcon by lazy {
		runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
	}

	private fun build(text: String, progress: Pair<Long, Long>?, indeterminate: Boolean): Notification {
		val b = NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_notification)
			.apply { largeIcon?.let { setLargeIcon(it) } }
			.setContentTitle("NasDrop")
			.setContentText(text)
			.setOngoing(indeterminate)
			.setOnlyAlertOnce(true)
			.setContentIntent(openApp())
			.setPriority(NotificationCompat.PRIORITY_LOW)

		if (progress != null && progress.second > 0) {
			val pct = ((progress.first * 100) / progress.second).toInt().coerceIn(0, 100)
			b.setProgress(100, pct, false)
			b.setSubText("$pct%")
		} else {
			b.setProgress(0, 0, true)
		}
		return b.build()
	}

	private fun openApp(): PendingIntent {
		val i = Intent(this, MainActivity::class.java).apply {
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
			putExtra(MainActivity.EXTRA_SHOW_TRANSFERS, true)
		}
		return PendingIntent.getActivity(
			this, 0, i,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private fun manager(): NotificationManager = getSystemService(NotificationManager::class.java)

	private fun ensureChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val ch = NotificationChannel(CHANNEL, "上传", NotificationManager.IMPORTANCE_LOW).apply {
			description = "分享文件到 NasDrop 时的上传进度"
			setShowBadge(false)
		}
		manager().createNotificationChannel(ch)
	}

	companion object {
		private const val CHANNEL = "uploads"
		private const val NOTIF_ID = 0x4E44
		private const val EXTRA_URIS = "uris"
		private const val EXTRA_DIR = "dir"
		private const val EXTRA_BATCH = "batch"

		/** 并行上传通道数：3 路能跑满上行带宽，又不会因为过多连接抢占导致单文件变慢。 */
		private const val MAX_PARALLEL = 3

		/** 返回批次 ID，分享页据此仅展示自己提交的文件。 */
		fun start(context: Context, uris: List<Uri>, dir: String): String {
			val batchId = UUID.randomUUID().toString()
			val i = Intent(context, UploadService::class.java).apply {
				putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
				putExtra(EXTRA_DIR, dir)
				putExtra(EXTRA_BATCH, batchId)
			}
			ContextCompat.startForegroundService(context, i)
			return batchId
		}
	}
}
