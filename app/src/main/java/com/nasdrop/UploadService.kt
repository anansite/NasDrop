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
import kotlinx.coroutines.joinAll

/**
 * 后台上传队列。
 *
 * 之所以做成前台服务而不是在 Activity 里直接传：用户分享完就会切回微信/相册，
 * 大文件（视频）传完前进程不能被杀。这里不带 VPN、不做网络代理，
 * 网络出口全部复用 [Repository]（内嵌 Tailscale 已经在那层起好）。
 */
class UploadService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		ensureChannel()
	}

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val dir = intent?.getStringExtra(EXTRA_DIR).orEmpty()
		val uris = intent?.getStringArrayListExtra(EXTRA_URIS)
			?.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
			.orEmpty()

		if (uris.isEmpty()) {
			stopSelf(startId)
			return START_NOT_STICKY
		}

		val app = applicationContext as NasDropApp
		Uploads.begin(uris.map { it.toString() to Query.nameOf(this, it) })

		ServiceCompat.startForeground(
			this,
			NOTIF_ID,
			build("准备上传", null, true),
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
		)

		scope.launch { upload(app, uris, dir, startId) }
		return START_NOT_STICKY
	}

	private suspend fun upload(app: NasDropApp, uris: List<Uri>, dir: String, startId: Int) {
		val settings = app.prefs.current()
		val client = try {
			app.repo.client(settings)
		} catch (t: Throwable) {
			uris.forEachIndexed { i, _ ->
				Uploads.update(i) { it.copy(done = true, error = t.message ?: "连接失败") }
			}
			finish(0, uris.size, startId)
			return
		}

		var ok = 0
		var failed = 0
		val semaphore = Semaphore(MAX_PARALLEL) // 最多 3 个任务并行上传，跑满上行带宽且不造成手机卡顿

		// 按文件大小降序调度（LPT：最长处理时间优先）。
		// 用户一次选多个视频时，如果大文件排在队尾，前面几个小文件把带宽占满、
		// 大文件迟迟开始不了，整体完成时间被最后那个大文件决定——观感就是"传完了还在等"。
		// 让大文件先拿到通道，整体完成时间最短，也最早让用户看到第一个结果。
		val queue = uris.mapIndexed { i, uri -> Triple(i, uri, Query.sizeOf(this@UploadService, uri)) }
			.sortedByDescending { it.third }

		val jobs = queue.map { (i, uri, size) ->
			scope.launch {
				semaphore.withPermit {
					val name = Uploads.tasks.value.getOrNull(i)?.name ?: "文件"
					val dirKey = dir.trim('/')
					val entryPath = if (dirKey.isEmpty()) name else "$dirKey/$name"
					val ext = name.substringAfterLast('.', "").lowercase()
					val isVideo = ext in DavEntry.VIDEOS
					val isImage = ext in DavEntry.IMAGES

					// 本地缩略图与上传并行跑：等字节传完时它早就好了，
					// 于是"传完那一刻"列表里就是真图，而不是一片灰（视频连图都没有）。
					// 万一它晚到，LocalThumbs.revision 自增会触发列表重组自动换上。
					launch {
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
								Uploads.update(i) { it.copy(sent = sent, total = total) }
								if (sent - lastNotified >= 512 * 1024 || (total > 0 && sent >= total)) {
									lastNotified = sent
									post(build("正在上传「$name」", if (total > 0) sent to total else null, false))
								}
							},
							onBodySent = {
								// 字节已全部写出：立刻乐观插入列表，把"等服务端写入"这段
								// 不可控的等待从用户视野里去掉。201 到了再做一次静默校正。
								Uploads.notifyFile(dirKey, name, size, Uploads.FilePhase.SENT)
							},
						)
						Uploads.update(i) { it.copy(done = true) }
						Uploads.notifyFile(dirKey, name, size, Uploads.FilePhase.CONFIRMED)
						synchronized(this@UploadService) { ok++ }
					} catch (t: Throwable) {
						Uploads.update(i) { it.copy(done = true, error = t.message ?: t.javaClass.simpleName) }
						// 回滚乐观插入，不留一个点开就 404 的幻影文件
						Uploads.notifyFile(dirKey, name, size, Uploads.FilePhase.FAILED)
						synchronized(this@UploadService) { failed++ }
					}
				}
			}
		}
		jobs.joinAll()

		finish(ok, failed, startId)
	}

	private fun finish(ok: Int, failed: Int, startId: Int) {
		// 通知前台页面自动刷新
		Uploads.notifyFinished()

		val text = when {
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
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		stopSelf(startId)
		// 通知栏那条“完成”留一会儿，让用户看得到；用 Handler 而不是协程，
		// 因为 onDestroy 会 cancel 掉 scope。
		Handler(Looper.getMainLooper()).postDelayed({
			manager().cancel(NOTIF_ID + 1)
			Uploads.clear()
		}, 4000)
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
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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

		/** 并行上传通道数：3 路能跑满上行带宽，又不会因为过多连接抢占导致单文件变慢。 */
		private const val MAX_PARALLEL = 3

		fun start(context: Context, uris: List<Uri>, dir: String) {
			val i = Intent(context, UploadService::class.java).apply {
				putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
				putExtra(EXTRA_DIR, dir)
			}
			ContextCompat.startForegroundService(context, i)
		}
	}
}
