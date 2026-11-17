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
		val semaphore = Semaphore(3) // 最多 3 个任务并行上传，跑满上行带宽且不造成手机卡顿

		val jobs = uris.mapIndexed { i, uri ->
			scope.launch {
				semaphore.withPermit {
					val name = Uploads.tasks.value.getOrNull(i)?.name ?: "文件"
					var lastNotified = 0L
					try {
						client.upload(this@UploadService, uri, dir) { sent, total ->
							Uploads.update(i) { it.copy(sent = sent, total = total) }
							if (sent - lastNotified >= 512 * 1024 || (total > 0 && sent >= total)) {
								lastNotified = sent
								post(build("正在上传「$name」", if (total > 0) sent to total else null, false))
							}
						}
						Uploads.update(i) { it.copy(done = true) }
						synchronized(this@UploadService) { ok++ }
					} catch (t: Throwable) {
						Uploads.update(i) { it.copy(done = true, error = t.message ?: t.javaClass.simpleName) }
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
		val largeIcon = runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
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

	private fun build(text: String, progress: Pair<Long, Long>?, indeterminate: Boolean): Notification {
		val largeIcon = runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
		val b = NotificationCompat.Builder(this, CHANNEL)
			.setSmallIcon(R.drawable.ic_notification)
			.apply { if (largeIcon != null) setLargeIcon(largeIcon) }
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

		fun start(context: Context, uris: List<Uri>, dir: String) {
			val i = Intent(context, UploadService::class.java).apply {
				putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
				putExtra(EXTRA_DIR, dir)
			}
			ContextCompat.startForegroundService(context, i)
		}
	}
}
