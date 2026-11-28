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
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AudioPlayMode(val label: String) {
	SEQUENTIAL("顺序播放"),
	LOOP_ONE("单曲循环"),
	LOOP_ALL("列表循环"),
}

data class AudioPlaybackUiState(
	val activeEntry: DavEntry? = null,
	val playlist: List<DavEntry> = emptyList(),
	val currentIndex: Int = 0,
	val activeUrl: String? = null,
	val isPlaying: Boolean = false,
	val isBuffering: Boolean = false,
	val currentPosition: Long = 0L,
	val bufferedPosition: Long = 0L,
	val duration: Long = 0L,
	val playbackSpeed: Float = 1.0f,
	val playMode: AudioPlayMode = AudioPlayMode.SEQUENTIAL,
	val playbackError: String? = null,
	val trackTitle: String? = null,
	val trackArtist: String? = null,
	val trackAlbum: String? = null,
	val artworkBytes: ByteArray? = null,
	val resumeSavedMs: Long = 0L,
)

@OptIn(UnstableApi::class)
internal fun createConfiguredExoPlayer(
	context: Context,
	http: okhttp3.OkHttpClient,
	handleAudioFocus: Boolean = false,
): ExoPlayer {
	val factory = DefaultMediaSourceFactory(context)
		.setDataSourceFactory(VideoCacheManager.createDataSourceFactory(context, http))
	val loadControl = DefaultLoadControl.Builder()
		.setBufferDurationsMs(
			15_000,   // minBufferMs (15s)
			60_000,   // maxBufferMs (60s)
			1_500,    // bufferForPlaybackMs (1.5s 安全起播)
			3_000,    // bufferForPlaybackAfterRebufferMs (3s 稳定恢复)
		)
		.setBackBuffer(15_000, true)
		.setPrioritizeTimeOverSizeThresholds(true)
		.build()

	val renderersFactory = DefaultRenderersFactory(context)
		.setEnableDecoderFallback(true)

	return ExoPlayer.Builder(context, renderersFactory)
		.setMediaSourceFactory(factory)
		.setLoadControl(loadControl)
		.apply {
			if (handleAudioFocus) {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
						.build(),
					true,
				)
			}
		}
		.build()
}

/**
 * 进程级音频后台播放控制器。
 * 返回相册/文件列表或锁屏切后台后音频持续播放，仅在用户手动停止或队列播放结束时释放资源。
 */
@OptIn(UnstableApi::class)
object BackgroundAudioController {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val _state = MutableStateFlow(AudioPlaybackUiState())
	val state: StateFlow<AudioPlaybackUiState> = _state.asStateFlow()

	private var appContext: Context? = null
	private var nasDb: NasDb? = null
	private var player: ExoPlayer? = null
	private var urlResolver: ((String) -> String?)? = null
	private var progressTickerJob: Job? = null
	private var lastProgressSaveAt = 0L

	private fun ensurePlayer(context: Context): ExoPlayer {
		val appCtx = context.applicationContext
		appContext = appCtx
		if (nasDb == null) {
			nasDb = NasDb.getInstance(appCtx)
		}
		player?.let { return it }

		val http = (appCtx as NasDropApp).repo.http()
		val created = createConfiguredExoPlayer(appCtx, http, handleAudioFocus = true)
		created.addListener(object : Player.Listener {
			override fun onIsPlayingChanged(playing: Boolean) {
				_state.value = _state.value.copy(isPlaying = playing)
				syncForegroundNotification()
			}

			override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
				val cur = _state.value
				_state.value = cur.copy(
					trackTitle = mediaMetadata.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: cur.trackTitle,
					trackArtist = mediaMetadata.artist?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: cur.trackArtist,
					trackAlbum = mediaMetadata.albumTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: cur.trackAlbum,
					artworkBytes = mediaMetadata.artworkData?.takeIf { it.isNotEmpty() } ?: cur.artworkBytes,
				)
			}

			override fun onPlaybackStateChanged(playbackState: Int) {
				val cur = _state.value
				val dur = created.duration.coerceAtLeast(0L)
				val buffering = playbackState == Player.STATE_BUFFERING
				_state.value = cur.copy(
					duration = dur,
					isBuffering = buffering,
				)
				if (playbackState == Player.STATE_ENDED) {
					val entry = cur.activeEntry ?: return
					if (dur > 0L) {
						_state.value = _state.value.copy(currentPosition = dur)
						nasDb?.saveVideoProgress(entry.path, dur, dur)
					}
					when (cur.playMode) {
						AudioPlayMode.LOOP_ONE -> {
							created.seekTo(0L)
							created.play()
						}
						AudioPlayMode.SEQUENTIAL -> {
							val list = cur.playlist
							val nextIdx = cur.currentIndex + 1
							if (list.size > 1 && nextIdx in list.indices) {
								switchToIndex(nextIdx)
							} else {
								_state.value = _state.value.copy(isPlaying = false, isBuffering = false)
								syncForegroundNotification()
							}
						}
						AudioPlayMode.LOOP_ALL -> {
							val list = cur.playlist
							if (list.size > 1) {
								val nextIdx = (cur.currentIndex + 1) % list.size
								switchToIndex(nextIdx)
							} else {
								created.seekTo(0L)
								created.play()
							}
						}
					}
				}
			}

			override fun onPlayerError(error: PlaybackException) {
				val cur = _state.value
				val extUpper = cur.activeEntry?.ext?.uppercase()?.ifBlank { "AUDIO" } ?: "AUDIO"
				_state.value = cur.copy(
					isBuffering = false,
					isPlaying = false,
					playbackError = "内置解码器暂不支持或加载失败 ($extUpper)：${error.errorCodeName}",
				)
				syncForegroundNotification()
			}
		})
		player = created
		return created
	}

	private fun startProgressTicker() {
		if (progressTickerJob?.isActive == true) return
		progressTickerJob = scope.launch {
			while (isActive) {
				delay(300)
				val p = player ?: continue
				val cur = _state.value
				val entry = cur.activeEntry ?: continue
				val dur = p.duration.coerceAtLeast(0L)
				val pos = if (p.playbackState == Player.STATE_ENDED && dur > 0L) dur else p.currentPosition.coerceAtLeast(0L)
				val buf = p.bufferedPosition.coerceAtLeast(0L)
				if (pos != cur.currentPosition || buf != cur.bufferedPosition || dur != cur.duration) {
					_state.value = cur.copy(
						currentPosition = pos,
						bufferedPosition = buf,
						duration = dur,
					)
				}
				val now = System.currentTimeMillis()
				if (p.isPlaying && now - lastProgressSaveAt >= 3000L) {
					lastProgressSaveAt = now
					nasDb?.saveVideoProgress(entry.path, pos, dur)
				}
			}
		}
	}

	fun openOrPlay(
		context: Context,
		entry: DavEntry,
		playlist: List<DavEntry>,
		urlOf: (String) -> String?,
	) {
		urlResolver = urlOf
		val p = ensurePlayer(context)
		val effectiveList = if (playlist.any { it.path == entry.path }) playlist else listOf(entry) + playlist
		val cur = _state.value
		if (cur.activeEntry?.path == entry.path && cur.playbackError == null && p.playbackState != Player.STATE_IDLE) {
			val idx = effectiveList.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
			_state.value = cur.copy(playlist = effectiveList, currentIndex = idx)
			if (!p.isPlaying && p.playbackState != Player.STATE_ENDED) {
				p.play()
			}
			startProgressTicker()
			syncForegroundNotification()
			return
		}
		val targetIdx = effectiveList.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
		_state.value = cur.copy(playlist = effectiveList, currentIndex = targetIdx)
		loadTrack(effectiveList[targetIdx], effectiveList, targetIdx)
	}

	fun switchToIndex(index: Int) {
		val cur = _state.value
		if (index !in cur.playlist.indices) return
		loadTrack(cur.playlist[index], cur.playlist, index)
	}

	fun stepTrack(delta: Int) {
		val cur = _state.value
		if (cur.playlist.size <= 1) return
		val targetIdx = ((cur.currentIndex + delta) % cur.playlist.size + cur.playlist.size) % cur.playlist.size
		switchToIndex(targetIdx)
	}

	private fun loadTrack(target: DavEntry, playlist: List<DavEntry>, index: Int) {
		val ctx = appContext ?: return
		val p = ensurePlayer(ctx)
		val prevEntry = _state.value.activeEntry
		if (prevEntry != null) {
			nasDb?.saveVideoProgress(prevEntry.path, p.currentPosition, p.duration)
		}
		val directUrl = urlResolver?.invoke(target.path)
		val resumeMs = nasDb?.getVideoProgress(target.path) ?: 0L
		val speed = _state.value.playbackSpeed
		if (directUrl.isNullOrBlank()) {
			p.stop()
			_state.value = _state.value.copy(
				activeEntry = target,
				playlist = playlist,
				currentIndex = index,
				activeUrl = null,
				isPlaying = false,
				isBuffering = false,
				currentPosition = 0L,
				bufferedPosition = 0L,
				duration = 0L,
				playbackError = "无法获取音频直链",
				trackTitle = null,
				trackArtist = null,
				trackAlbum = null,
				artworkBytes = null,
				resumeSavedMs = 0L,
			)
			syncForegroundNotification()
			return
		}

		_state.value = _state.value.copy(
			activeEntry = target,
			playlist = playlist,
			currentIndex = index,
			activeUrl = directUrl,
			isBuffering = true,
			playbackError = null,
			currentPosition = resumeMs.coerceAtLeast(0L),
			bufferedPosition = 0L,
			duration = 0L,
			trackTitle = null,
			trackArtist = null,
			trackAlbum = null,
			artworkBytes = null,
			resumeSavedMs = if (resumeMs > 3000L) resumeMs else 0L,
		)

		p.playWhenReady = true
		p.playbackParameters = PlaybackParameters(speed)
		p.setMediaItem(MediaItem.fromUri(directUrl))
		if (resumeMs > 3000L) {
			p.seekTo(resumeMs)
		}
		p.prepare()
		startProgressTicker()
		syncForegroundNotification(ensureServiceStarted = true)
	}

	fun togglePlayPause() {
		val p = player ?: return
		if (p.isPlaying) {
			p.pause()
		} else {
			if (_state.value.playbackError != null) {
				_state.value = _state.value.copy(playbackError = null, isBuffering = true)
				p.prepare()
			} else if (p.playbackState == Player.STATE_ENDED) {
				p.seekTo(0L)
			}
			p.play()
			syncForegroundNotification(ensureServiceStarted = true)
		}
	}

	fun pauseIfPlaying() {
		val p = player ?: return
		if (p.isPlaying) {
			p.pause()
		}
	}

	fun retryCurrent() {
		val p = player ?: return
		val cur = _state.value
		val entry = cur.activeEntry ?: return
		if (cur.activeUrl.isNullOrBlank()) {
			loadTrack(entry, cur.playlist, cur.currentIndex)
			return
		}
		_state.value = cur.copy(playbackError = null, isBuffering = true)
		p.prepare()
		p.play()
		syncForegroundNotification(ensureServiceStarted = true)
	}

	fun seekTo(targetMs: Long) {
		val p = player ?: return
		val clamped = targetMs.coerceAtLeast(0L)
		p.seekTo(clamped)
		_state.value = _state.value.copy(currentPosition = clamped)
	}

	fun seekBy(deltaMs: Long) {
		val p = player ?: return
		val dur = _state.value.duration
		val maxDur = if (dur > 0L) dur else Long.MAX_VALUE
		val target = (p.currentPosition + deltaMs).coerceIn(0L, maxDur)
		seekTo(target)
	}

	fun cycleSpeed(): Float {
		val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
		val curSpeed = _state.value.playbackSpeed
		val next = speeds[(speeds.indexOf(curSpeed) + 1) % speeds.size]
		player?.playbackParameters = PlaybackParameters(next)
		_state.value = _state.value.copy(playbackSpeed = next)
		return next
	}

	fun cyclePlayMode(): AudioPlayMode {
		val modes = AudioPlayMode.entries
		val curMode = _state.value.playMode
		val next = modes[(modes.indexOf(curMode) + 1) % modes.size]
		_state.value = _state.value.copy(playMode = next)
		return next
	}

	fun stopAndClear() {
		progressTickerJob?.cancel()
		progressTickerJob = null
		val p = player
		val entry = _state.value.activeEntry
		if (p != null) {
			if (entry != null) {
				nasDb?.saveVideoProgress(entry.path, p.currentPosition, p.duration)
			}
			p.stop()
			p.clearMediaItems()
		}
		_state.value = AudioPlaybackUiState()
		AudioPlaybackService.stopIfRunning(appContext)
	}

	private fun syncForegroundNotification(ensureServiceStarted: Boolean = false) {
		val ctx = appContext ?: return
		val cur = _state.value
		val entry = cur.activeEntry
		if (entry == null) {
			AudioPlaybackService.stopIfRunning(ctx)
			return
		}
		val service = AudioPlaybackService.activeInstance
		if (service != null) {
			service.refreshNotification()
		} else if (ensureServiceStarted || cur.isPlaying) {
			runCatching {
				ContextCompat.startForegroundService(ctx, Intent(ctx, AudioPlaybackService::class.java))
			}
		}
	}
}

class AudioPlaybackService : Service() {

	private val largeIcon by lazy {
		runCatching { BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher) }.getOrNull()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		activeInstance = this
		ensureChannel()
	}

	override fun onDestroy() {
		if (activeInstance === this) activeInstance = null
		super.onDestroy()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_PLAY_PAUSE -> BackgroundAudioController.togglePlayPause()
			ACTION_PREV -> BackgroundAudioController.stepTrack(-1)
			ACTION_NEXT -> BackgroundAudioController.stepTrack(1)
			ACTION_STOP -> {
				BackgroundAudioController.stopAndClear()
				stopForegroundAndSelf()
				return START_NOT_STICKY
			}
		}

		val st = BackgroundAudioController.state.value
		if (st.activeEntry == null) {
			stopForegroundAndSelf()
			return START_NOT_STICKY
		}

		ServiceCompat.startForeground(
			this,
			NOTIF_ID,
			buildNotification(st),
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
		)
		return START_NOT_STICKY
	}

	fun refreshNotification() {
		val st = BackgroundAudioController.state.value
		if (st.activeEntry == null) {
			stopForegroundAndSelf()
			return
		}
		val manager = getSystemService(NotificationManager::class.java)
		manager.notify(NOTIF_ID, buildNotification(st))
	}

	fun stopForegroundAndSelf() {
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun buildNotification(st: AudioPlaybackUiState): Notification {
		val entry = st.activeEntry
		val title = st.trackTitle ?: entry?.name?.substringBeforeLast('.', entry.name) ?: "NasDrop 音频"
		val folder = entry?.path?.substringBeforeLast('/', "")?.substringAfterLast('/').orEmpty().ifBlank { "NasDrop" }
		val statusText = when {
			st.playbackError != null -> "播放异常"
			st.isPlaying -> "$folder · 正在播放"
			st.isBuffering -> "$folder · 缓冲中"
			else -> "$folder · 已暂停"
		}

		val openIntent = PendingIntent.getActivity(
			this,
			0,
			Intent(this, MainActivity::class.java).apply {
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				putExtra(MainActivity.EXTRA_OPEN_AUDIO, true)
			},
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)

		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.apply { largeIcon?.let { setLargeIcon(it) } }
			.setContentTitle(title)
			.setContentText(statusText)
			.setContentIntent(openIntent)
			.setOngoing(st.isPlaying)
			.setOnlyAlertOnce(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

		if (st.playlist.size > 1) {
			builder.addAction(0, "上一首", actionPendingIntent(ACTION_PREV, 1))
		}
		builder.addAction(0, if (st.isPlaying) "暂停" else "播放", actionPendingIntent(ACTION_PLAY_PAUSE, 2))
		if (st.playlist.size > 1) {
			builder.addAction(0, "下一首", actionPendingIntent(ACTION_NEXT, 3))
		}
		builder.addAction(0, "停止", actionPendingIntent(ACTION_STOP, 4))

		return builder.build()
	}

	private fun actionPendingIntent(action: String, reqCode: Int): PendingIntent {
		val intent = Intent(this, AudioPlaybackService::class.java).apply { this.action = action }
		return PendingIntent.getService(
			this,
			reqCode,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
		)
	}

	private fun ensureChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val ch = NotificationChannel(CHANNEL_ID, "音频后台播放", NotificationManager.IMPORTANCE_LOW).apply {
			description = "音频与故事后台播放控制通知"
			setShowBadge(false)
		}
		getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
	}

	companion object {
		private const val CHANNEL_ID = "nasdrop_audio_playback"
		private const val NOTIF_ID = 0x4E46
		private const val ACTION_PLAY_PAUSE = "com.nasdrop.audio.PLAY_PAUSE"
		private const val ACTION_PREV = "com.nasdrop.audio.PREV"
		private const val ACTION_NEXT = "com.nasdrop.audio.NEXT"
		private const val ACTION_STOP = "com.nasdrop.audio.STOP"

		@Volatile
		var activeInstance: AudioPlaybackService? = null

		fun stopIfRunning(context: Context?) {
			val inst = activeInstance
			if (inst != null) {
				inst.stopForegroundAndSelf()
			} else if (context != null) {
				runCatching { context.stopService(Intent(context, AudioPlaybackService::class.java)) }
				runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) }
			}
		}
	}
}
