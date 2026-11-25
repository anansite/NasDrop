package com.nasdrop

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File

/**
 * 伴随录音真实录制与播放控制器（基于 Media3 ExoPlayer + 业务 OkHttpClient）。
 * 1. 真实 MediaRecorder 录制高清 AAC/M4A 伴随原声；
 * 2. ExoPlayer 复用业务 OkHttpClient（含认证头/bridge/直连），杜绝原生 MediaPlayer -38 错误；
 * 3. 切换照片时彻底重置时长与播放进度，绝不串用上一张的时长或语音。
 */
@OptIn(UnstableApi::class)
class CompanionAudioPlayer(private val context: Context) {
	private var exoPlayer: ExoPlayer? = null
	private val handler = Handler(Looper.getMainLooper())
	private var progressRunnable: Runnable? = null

	var isPlaying by mutableStateOf(false)
		private set

	var progress by mutableFloatStateOf(0f)
		private set

	var currentPositionMs by mutableIntStateOf(0)
		private set

	var durationMs by mutableIntStateOf(0)
		private set

	var onDurationResolved: ((Long) -> Unit)? = null

	var currentUrl: String = ""
		private set

	fun prepare(audioUrlOrPath: String) {
		stop()
		currentUrl = audioUrlOrPath
	}

	fun togglePlayPause(fallbackDurationMs: Long = 0L) {
		val player = exoPlayer
		if (isPlaying) {
			pause()
		} else if (player != null) {
			if (currentPositionMs >= (durationMs - 300).coerceAtLeast(0) || progress >= 0.98f) {
				seekTo(0f)
			}
			resume()
		} else if (currentUrl.isNotBlank()) {
			play(currentUrl, fallbackDurationMs)
		}
	}

	fun play(audioUrlOrPath: String, fallbackDurationMs: Long = 0L) {
		stop()
		if (audioUrlOrPath.isBlank()) {
			Log.w("AudioPlayer", "Cannot play empty or blank audio source")
			return
		}
		currentUrl = audioUrlOrPath
		try {
			val httpFactory = OkHttpDataSource.Factory(context.app.repo.http())
			val dsFactory = DefaultDataSource.Factory(context, httpFactory)
			val player = ExoPlayer.Builder(context)
				.setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
				.build()
				.apply {
					setAudioAttributes(
						AudioAttributes.Builder()
							.setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
							.setUsage(C.USAGE_MEDIA)
							.build(),
						true
					)
					val uri = if (audioUrlOrPath.startsWith("http://") || audioUrlOrPath.startsWith("https://")) {
						Uri.parse(audioUrlOrPath)
					} else {
						Uri.fromFile(File(audioUrlOrPath))
					}
					setMediaItem(MediaItem.fromUri(uri))
					addListener(object : Player.Listener {
						override fun onPlaybackStateChanged(playbackState: Int) {
							when (playbackState) {
								Player.STATE_READY -> {
									val rawDur = duration
									val dur = if (rawDur > 0 && rawDur != C.TIME_UNSET) rawDur.toInt() else fallbackDurationMs.toInt()
									this@CompanionAudioPlayer.durationMs = dur
									if (dur > 0) {
										onDurationResolved?.invoke(dur.toLong())
									}
								}
								Player.STATE_ENDED -> {
									stop()
								}
								else -> {}
							}
						}

						override fun onIsPlayingChanged(playing: Boolean) {
							this@CompanionAudioPlayer.isPlaying = playing
							if (playing) startProgressTracker() else stopProgressTracker()
						}

						override fun onPlayerError(error: PlaybackException) {
							Log.w("AudioPlayer", "ExoPlayer error for $audioUrlOrPath: ${error.errorCodeName}", error)
							stop()
						}
					})
					prepare()
					playWhenReady = true
				}
			exoPlayer = player
		} catch (e: Exception) {
			Log.e("AudioPlayer", "Failed to start audio playback for $audioUrlOrPath", e)
			stop()
		}
	}

	fun pause() {
		try {
			exoPlayer?.pause()
		} catch (_: Throwable) {}
		isPlaying = false
		stopProgressTracker()
	}

	fun resume() {
		try {
			exoPlayer?.play()
			isPlaying = true
			startProgressTracker()
		} catch (_: Throwable) {
			stop()
		}
	}

	fun togglePlay(audioUrlOrPath: String, fallbackDurationMs: Long = 0L) {
		togglePlayPause(fallbackDurationMs)
	}

	fun seekTo(fraction: Float) {
		exoPlayer?.let {
			try {
				val target = (fraction.coerceIn(0f, 1f) * durationMs).toLong()
				it.seekTo(target)
				currentPositionMs = target.toInt()
				progress = fraction.coerceIn(0f, 1f)
			} catch (_: Throwable) {}
		}
	}

	fun stop() {
		stopProgressTracker()
		val player = exoPlayer
		exoPlayer = null
		isPlaying = false
		progress = 0f
		currentPositionMs = 0
		durationMs = 0
		currentUrl = ""
		if (player != null) {
			try {
				player.stop()
			} catch (_: Throwable) {}
			try {
				player.release()
			} catch (_: Throwable) {}
		}
	}

	private fun startProgressTracker() {
		stopProgressTracker()
		val runnable = object : Runnable {
			override fun run() {
				val p = exoPlayer
				if (p != null && p.isPlaying) {
					try {
						val pos = p.currentPosition.toInt().coerceAtLeast(0)
						val rawDur = p.duration
						val dur = if (durationMs > 0) durationMs else if (rawDur > 0 && rawDur != C.TIME_UNSET) rawDur.toInt() else 0
						if (dur > 0) {
							currentPositionMs = pos
							durationMs = dur
							progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
						}
						handler.postDelayed(this, 100)
					} catch (_: Throwable) {
						stop()
					}
				} else {
					isPlaying = false
				}
			}
		}
		progressRunnable = runnable
		handler.post(runnable)
	}

	private fun stopProgressTracker() {
		progressRunnable?.let { handler.removeCallbacks(it) }
		progressRunnable = null
	}
}

/**
 * 真实高清伴随录音器
 */
class CompanionAudioRecorder(private val context: Context) {
	private var mediaRecorder: MediaRecorder? = null
	private var tempFile: File? = null
	private var startTimeMs = 0L

	var isRecording by mutableStateOf(false)
		private set

	var elapsedSeconds by mutableIntStateOf(0)
		private set

	private val handler = Handler(Looper.getMainLooper())
	private var timerRunnable: Runnable? = null

	fun start(): Boolean {
		stopRecordingInternal(false)
		return try {
			val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.m4a")
			tempFile = file

			val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				MediaRecorder(context)
			} else {
				@Suppress("DEPRECATION")
				MediaRecorder()
			}
			recorder.apply {
				setAudioSource(MediaRecorder.AudioSource.MIC)
				setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
				setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
				setAudioEncodingBitRate(64000)
				setAudioSamplingRate(44100)
				setOutputFile(file.absolutePath)
				prepare()
				start()
			}
			mediaRecorder = recorder
			isRecording = true
			startTimeMs = System.currentTimeMillis()
			elapsedSeconds = 0

			timerRunnable = object : Runnable {
				override fun run() {
					if (isRecording) {
						elapsedSeconds = ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
						handler.postDelayed(this, 500)
					}
				}
			}
			handler.post(timerRunnable!!)
			true
		} catch (e: Exception) {
			Log.e("AudioRecorder", "Failed to start MediaRecorder: ${e.message}", e)
			stopRecordingInternal(false)
			false
		}
	}

	/**
	 * 停止并获取录音字节与毫秒时长
	 */
	fun stop(): Pair<ByteArray, Long>? {
		if (!isRecording && tempFile == null) return null
		val file = tempFile
		val totalDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1000L)
		stopRecordingInternal(true)

		return if (file != null && file.exists() && file.length() > 0) {
			val bytes = file.readBytes()
			file.delete()
			Pair(bytes, totalDurationMs)
		} else {
			null
		}
	}

	fun cancel() {
		stopRecordingInternal(false)
		tempFile?.delete()
		tempFile = null
	}

	private fun stopRecordingInternal(saveFile: Boolean) {
		timerRunnable?.let { handler.removeCallbacks(it) }
		timerRunnable = null
		isRecording = false

		try {
			mediaRecorder?.apply {
				try { stop() } catch (_: Exception) {}
				release()
			}
		} catch (_: Exception) {}
		mediaRecorder = null

		if (!saveFile) {
			tempFile?.delete()
			tempFile = null
		}
	}
}
