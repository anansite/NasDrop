package com.nasdrop

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 伴随录音真实录制与播放控制器。
 * 1. 真实 MediaRecorder 录制高清 AAC/M4A 伴随原声；
 * 2. 真实 MediaPlayer 流式/本地音频播放与进度同步；
 * 3. 伴随音频文件以 "." 开头就地缓存与存储，绝不将音频二进制存入数据库。
 */
class CompanionAudioPlayer(private val context: Context) {
	private var mediaPlayer: MediaPlayer? = null
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
		if (isPlaying) {
			pause()
		} else if (mediaPlayer != null) {
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
		if (audioUrlOrPath.startsWith("http://") || audioUrlOrPath.startsWith("https://")) {
			Thread {
				try {
					val cacheDir = File(context.cacheDir, "audio_cache")
					if (!cacheDir.exists()) cacheDir.mkdirs()
					val cacheKey = audioUrlOrPath.hashCode().toUInt().toString(16)
					val cachedFile = File(cacheDir, "dl_$cacheKey.m4a")
					if (!cachedFile.exists() || cachedFile.length() == 0L) {
						val client = okhttp3.OkHttpClient.Builder()
							.followRedirects(true)
							.followSslRedirects(true)
							.build()
						val reqBuilder = okhttp3.Request.Builder().url(audioUrlOrPath)
						runCatching {
							val conf = kotlinx.coroutines.runBlocking { context.app.prefs.current() }
							if (conf.user.isNotBlank() && conf.password.isNotBlank()) {
								reqBuilder.header("Authorization", okhttp3.Credentials.basic(conf.user, conf.password))
							}
						}
						client.newCall(reqBuilder.build()).execute().use { resp ->
							if (!resp.isSuccessful) {
								Log.w("AudioPlayer", "HTTP download failed: ${resp.code} for $audioUrlOrPath")
								return@Thread
							}
							val bytes = resp.body?.bytes()
							if (bytes == null || bytes.isEmpty()) {
								Log.w("AudioPlayer", "Empty audio response for $audioUrlOrPath")
								return@Thread
							}
							cachedFile.writeBytes(bytes)
						}
					}
					handler.post {
						if (currentUrl == audioUrlOrPath) {
							startLocalMediaPlayer(cachedFile.absolutePath, fallbackDurationMs)
						}
					}
				} catch (e: Exception) {
					Log.e("AudioPlayer", "Failed to download audio: $audioUrlOrPath", e)
				}
			}.start()
		} else {
			startLocalMediaPlayer(audioUrlOrPath, fallbackDurationMs)
		}
	}

	private fun startLocalMediaPlayer(localPath: String, fallbackDurationMs: Long = 0L) {
		try {
			val player = MediaPlayer().apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
						.setUsage(AudioAttributes.USAGE_MEDIA)
						.build()
				)
				setDataSource(localPath)
				setOnPreparedListener { mp ->
					val dur = if (mp.duration > 0) mp.duration else fallbackDurationMs.toInt()
					this@CompanionAudioPlayer.durationMs = dur
					if (dur > 0) {
						onDurationResolved?.invoke(dur.toLong())
					}
					mp.start()
					this@CompanionAudioPlayer.isPlaying = true
					startProgressTracker()
				}
				setOnCompletionListener {
					stop()
				}
				setOnErrorListener { _, what, extra ->
					Log.w("AudioPlayer", "MediaPlayer error: what=$what extra=$extra")
					stop()
					true
				}
				prepareAsync()
			}
			mediaPlayer = player
		} catch (e: Exception) {
			Log.e("AudioPlayer", "Failed to start audio playback for $localPath", e)
			stop()
		}
	}

	fun pause() {
		try {
			mediaPlayer?.let {
				if (it.isPlaying) {
					it.pause()
				}
			}
		} catch (_: Throwable) {}
		isPlaying = false
		stopProgressTracker()
	}

	fun resume() {
		try {
			mediaPlayer?.let {
				it.start()
				isPlaying = true
				startProgressTracker()
			}
		} catch (_: Throwable) {
			stop()
		}
	}

	fun togglePlay(audioUrlOrPath: String, fallbackDurationMs: Long = 0L) {
		if (isPlaying) {
			pause()
		} else if (mediaPlayer != null) {
			if (currentPositionMs >= (durationMs - 300).coerceAtLeast(0) || progress >= 0.98f) {
				seekTo(0f)
			}
			resume()
		} else {
			play(audioUrlOrPath, fallbackDurationMs)
		}
	}

	fun seekTo(fraction: Float) {
		mediaPlayer?.let {
			try {
				val target = (fraction.coerceIn(0f, 1f) * durationMs).toInt()
				it.seekTo(target)
				currentPositionMs = target
				progress = fraction
			} catch (_: Throwable) {}
		}
	}

	fun stop() {
		stopProgressTracker()
		val player = mediaPlayer
		mediaPlayer = null
		isPlaying = false
		progress = 0f
		currentPositionMs = 0
		durationMs = 0
		currentUrl = ""
		if (player != null) {
			try {
				player.reset()
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
				val mp = mediaPlayer
				if (mp != null) {
					try {
						val pos = mp.currentPosition
						val dur = if (durationMs > 0) durationMs else mp.duration
						if (dur > 0) {
							currentPositionMs = pos
							durationMs = dur
							progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
						}
						// 只要播放器不在播放状态且接近末尾，或者播放器已经停止，即判定完成
						if (!mp.isPlaying && (pos >= (dur - 300).coerceAtLeast(0) || dur <= 0)) {
							stop()
							return
						}
						if (isPlaying && mp.isPlaying) {
							handler.postDelayed(this, 100)
						} else if (!mp.isPlaying) {
							isPlaying = false
						}
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
