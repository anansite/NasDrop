package com.nasdrop.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nasdrop.DavEntry
import com.nasdrop.app
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 高清看图：左右滑动切换当前目录里的图片，双指缩放、双击复位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewer(
	images: List<DavEntry>,
	startIndex: Int,
	urlOf: (String) -> String?,
	onBack: () -> Unit,
) {
	if (images.isEmpty()) return
	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) {
		images.size
	}
	val current = images[pager.currentPage.coerceIn(0, images.lastIndex)]

	Box(Modifier.fillMaxSize().background(Color.Black)) {
		HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
			val entry = images[page]
			ZoomableImage(url = urlOf(entry.path), name = entry.name)
		}

		TopAppBar(
			title = {
				Column {
					Text(
						current.name,
						style = MaterialTheme.typography.titleSmall,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						"${pager.currentPage + 1} / ${images.size}",
						style = MaterialTheme.typography.bodySmall,
					)
				}
			},
			navigationIcon = {
				IconButton(onClick = onBack) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
				}
			},
			colors = TopAppBarDefaults.topAppBarColors(
				containerColor = Color.Black.copy(alpha = 0.45f),
				titleContentColor = Color.White,
				navigationIconContentColor = Color.White,
			),
		)
	}
}

@Composable
private fun ZoomableImage(url: String?, name: String) {
	var scale by remember { mutableFloatStateOf(1f) }
	var dx by remember { mutableFloatStateOf(0f) }
	var dy by remember { mutableFloatStateOf(0f) }

	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		AsyncImage(
			model = url,
			contentDescription = name,
			contentScale = ContentScale.Fit,
			modifier = Modifier
				.fillMaxSize()
				.graphicsLayer {
					scaleX = scale
					scaleY = scale
					translationX = dx
					translationY = dy
				}
				.pointerInput(Unit) {
					detectTransformGestures { _, pan, zoom, _ ->
						scale = (scale * zoom).coerceIn(1f, 6f)
						if (scale > 1f) {
							dx += pan.x
							dy += pan.y
						} else {
							dx = 0f
							dy = 0f
						}
					}
				}
				.pointerInput(Unit) {
					detectTapGestures(
						onDoubleTap = {
							if (scale > 1f) {
								scale = 1f; dx = 0f; dy = 0f
							} else {
								scale = 2.5f
							}
						},
					)
				}.padding(0.dp),
		)
	}
}

/**
 * 商业级专业网盘视频播放器：
 * - 手势调节：左侧上下滑调节亮度、右侧上下滑调节音量、水平横滑快进快退预览
 * - 极速预览：长按屏幕任意处触发 2.0x 极速快进，松手恢复原速
 * - 倍速切换：0.75x、1.0x、1.25x、1.5x、2.0x
 * - 画面比例切换：适应屏幕 (FIT) vs 铺满 (FILL) vs 居中缩放 (ZOOM)
 * - 悬浮手势 HUD 实时反馈
 */
@Composable
fun VideoPlayer(entry: DavEntry, url: String?, onBack: () -> Unit) {
	val context = LocalContext.current
	val activity = context as? Activity
	val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
	val http = context.app.repo.http()

	var showControls by remember { mutableStateOf(true) }
	var isPlaying by remember { mutableStateOf(true) }
	var currentPosition by remember { mutableLongStateOf(0L) }
	var duration by remember { mutableLongStateOf(0L) }
	var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
	var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

	// 手势 HUD 状态
	var hudText by remember { mutableStateOf("") }
	var hudVisible by remember { mutableStateOf(false) }

	val player = remember(url) {
		val factory = DefaultMediaSourceFactory(context)
			.setDataSourceFactory(OkHttpDataSource.Factory(http))
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(factory)
			.build()
			.apply {
				playWhenReady = true
				setMediaItem(MediaItem.fromUri(url ?: ""))
				prepare()
			}
	}

	// 播放器状态监听
	DisposableEffect(player) {
		val listener = object : Player.Listener {
			override fun onIsPlayingChanged(playing: Boolean) {
				isPlaying = playing
			}
			override fun onPlaybackStateChanged(state: Int) {
				duration = player.duration.coerceAtLeast(0L)
			}
		}
		player.addListener(listener)
		onDispose {
			player.removeListener(listener)
			player.release()
		}
	}

	// 周期性轮询播放进度
	LaunchedEffect(player) {
		while (true) {
			if (player.isPlaying) {
				currentPosition = player.currentPosition.coerceAtLeast(0L)
				duration = player.duration.coerceAtLeast(0L)
			}
			delay(500)
		}
	}

	// 控制栏自动隐藏定时器（5秒无操作自动隐藏）
	LaunchedEffect(showControls) {
		if (showControls) {
			delay(5000)
			showControls = false
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black)
			// 手势交互层：点击切换控制栏、双击暂停、长按2.0x、拖拽调节
			.pointerInput(Unit) {
				detectTapGestures(
					onTap = { showControls = !showControls },
					onDoubleTap = {
						if (player.isPlaying) player.pause() else player.play()
					},
					onLongPress = {
						player.playbackParameters = PlaybackParameters(2.0f)
						hudText = "2.0x 极速快进中"
						hudVisible = true
					},
					onPress = {
						tryAwaitRelease()
						player.playbackParameters = PlaybackParameters(playbackSpeed)
						hudVisible = false
					},
				)
			}
			.pointerInput(Unit) {
				var totalDx = 0f
				var totalDy = 0f
				var isRightSide = false
				var isSeeking = false

				detectDragGestures(
					onDragStart = { offset ->
						totalDx = 0f
						totalDy = 0f
						isRightSide = offset.x > size.width / 2f
						isSeeking = false
					},
					onDrag = { change, dragAmount ->
						change.consume()
						totalDx += dragAmount.x
						totalDy += dragAmount.y

						if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy) * 1.5f || isSeeking) {
							// 水平滑动：快进 / 快退
							isSeeking = true
							val seekDelta = (totalDx * 150).toLong()
							val target = (player.currentPosition + seekDelta).coerceIn(0L, duration.coerceAtLeast(1L))
							hudText = "${formatTime(target)} / ${formatTime(duration)}"
							hudVisible = true
						} else {
							// 垂直滑动：左侧调节屏幕亮度，右侧调节媒体音量
							val deltaFraction = -dragAmount.y / size.height.toFloat()
							if (isRightSide) {
								val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
								val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
								val newVol = (curVol + (deltaFraction * maxVol * 1.5f).toInt()).coerceIn(0, maxVol)
								audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
								val percent = (newVol * 100) / maxVol
								hudText = "音量: $percent%"
								hudVisible = true
							} else {
								activity?.window?.let { win ->
									val lp = win.attributes
									val curB = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
									val newB = (curB + deltaFraction * 1.2f).coerceIn(0.05f, 1f)
									lp.screenBrightness = newB
									win.attributes = lp
									hudText = "亮度: ${(newB * 100).toInt()}%"
									hudVisible = true
								}
							}
						}
					},
					onDragEnd = {
						if (isSeeking) {
							val seekDelta = (totalDx * 150).toLong()
							val target = (player.currentPosition + seekDelta).coerceIn(0L, duration.coerceAtLeast(1L))
							player.seekTo(target)
						}
						hudVisible = false
					},
					onDragCancel = { hudVisible = false },
				)
			},
	) {
		// 底层 Android ExoPlayer 视窗
		AndroidView(
			factory = { ctx ->
				PlayerView(ctx).apply {
					this.player = player
					useController = false
					this.resizeMode = resizeMode
					setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
				}
			},
			update = { view ->
				view.resizeMode = resizeMode
			},
			modifier = Modifier.fillMaxSize(),
		)

		// 居中手势 HUD 浮动指示窗
		AnimatedVisibility(
			visible = hudVisible,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.Center),
		) {
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = Color.Black.copy(alpha = 0.75f),
				shadowElevation = 8.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Icon(
						imageVector = if (hudText.contains("音量")) Icons.Rounded.VolumeUp
						else if (hudText.contains("亮度")) Icons.Rounded.BrightnessMedium
						else Icons.Rounded.FastForward,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.size(24.dp),
					)
					Text(
						text = hudText,
						color = Color.White,
						fontSize = 14.sp,
						fontWeight = FontWeight.Bold,
					)
				}
			}
		}

		// 沉浸渐变顶栏控制（带清晰度与画幅切换）
		AnimatedVisibility(
			visible = showControls,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
						),
					)
					.padding(horizontal = 12.dp, vertical = 8.dp),
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.weight(1f),
					) {
						IconButton(onClick = onBack) {
							Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = Color.White)
						}
						Text(
							text = entry.name,
							color = Color.White,
							fontSize = 15.sp,
							fontWeight = FontWeight.Bold,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}

					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						// 画面比例切换按钮
						Surface(
							shape = RoundedCornerShape(14.dp),
							color = Color.White.copy(alpha = 0.2f),
							onClick = {
								resizeMode = when (resizeMode) {
									AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
									AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
									else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
								}
							},
						) {
							Row(
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(Icons.Rounded.AspectRatio, contentDescription = "画幅", tint = Color.White, modifier = Modifier.size(14.dp))
								Text(
									text = when (resizeMode) {
										AspectRatioFrameLayout.RESIZE_MODE_FIT -> "适应"
										AspectRatioFrameLayout.RESIZE_MODE_FILL -> "铺满"
										else -> "居中"
									},
									color = Color.White,
									fontSize = 11.sp,
									fontWeight = FontWeight.Bold,
								)
							}
						}

						// 超高清原画标签
						Surface(
							shape = RoundedCornerShape(14.dp),
							color = Color(0xFF2A72F6).copy(alpha = 0.85f),
						) {
							Text(
								text = "原画 4K",
								color = Color.White,
								fontSize = 11.sp,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}

		// 沉浸渐变底栏控制（进度条、播放暂停、倍速药丸）
		AnimatedVisibility(
			visible = showControls,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
						),
					)
					.padding(horizontal = 16.dp, vertical = 12.dp),
			) {
				Column(modifier = Modifier.fillMaxWidth()) {
					// 进度条与时间戳
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = formatTime(currentPosition),
							color = Color.White,
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
						)

						Slider(
							value = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
							onValueChange = { frac ->
								if (duration > 0) {
									val target = (frac * duration).toLong()
									player.seekTo(target)
									currentPosition = target
								}
							},
							colors = SliderDefaults.colors(
								thumbColor = Color.White,
								activeTrackColor = Color(0xFF2A72F6),
								inactiveTrackColor = Color.White.copy(alpha = 0.3f),
							),
							modifier = Modifier
								.weight(1f)
								.padding(horizontal = 10.dp),
						)

						Text(
							text = formatTime(duration),
							color = Color.White.copy(alpha = 0.7f),
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
						)
					}

					// 播放控制按钮群
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							// 播放 / 暂停大按钮
							Surface(
								shape = CircleShape,
								color = Color.White,
								onClick = {
									if (player.isPlaying) player.pause() else player.play()
								},
								modifier = Modifier.size(40.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
										contentDescription = if (isPlaying) "暂停" else "播放",
										tint = Color.Black,
										modifier = Modifier.size(24.dp),
									)
								}
							}

							// 快退 10 秒
							IconButton(onClick = {
								val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
								player.seekTo(target)
							}) {
								Icon(Icons.Rounded.Replay10, contentDescription = "快退10秒", tint = Color.White)
							}

							// 快进 10 秒
							IconButton(onClick = {
								val target = (player.currentPosition + 10_000L).coerceAtMost(duration)
								player.seekTo(target)
							}) {
								Icon(Icons.Rounded.Forward10, contentDescription = "快进10秒", tint = Color.White)
							}
						}

						// 倍速切换药丸
						val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
						Surface(
							shape = RoundedCornerShape(16.dp),
							color = Color.White.copy(alpha = 0.2f),
							onClick = {
								val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
								val nextSpeed = speeds[nextIdx]
								playbackSpeed = nextSpeed
								player.playbackParameters = PlaybackParameters(nextSpeed)
							},
						) {
							Text(
								text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
								color = Color.White,
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
							)
						}
					}
				}
			}
		}
	}
}

private fun formatTime(millis: Long): String {
	val totalSecs = millis / 1000
	val mins = totalSecs / 60
	val secs = totalSecs % 60
	return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}
