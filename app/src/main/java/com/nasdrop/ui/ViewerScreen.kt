package com.nasdrop.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.nasdrop.NasDb
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
	/** 列表已加载的服务端缩略图直链，作为原图加载期间/失败时的即时垫底图 */
	thumbOf: ((String) -> String?)? = null,
	onBack: () -> Unit,
	onSaveToGallery: ((DavEntry, (Boolean, String) -> Unit) -> Unit)? = null,
	onShare: ((DavEntry, (java.io.File?) -> Unit) -> Unit)? = null,
	onDelete: ((DavEntry) -> Unit)? = null,
) {
	val context = LocalContext.current
	var imageList by remember(images) { mutableStateOf(images) }
	if (imageList.isEmpty()) {
		LaunchedEffect(Unit) { onBack() }
		return
	}

	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, imageList.lastIndex)) {
		imageList.size
	}
	val current = imageList.getOrNull(pager.currentPage.coerceIn(0, imageList.lastIndex)) ?: return

	// 统一解码尺寸 = 屏幕像素。预取和实际显示用**同一个 Size**，
	// Coil 的内存缓存 key 才能对上：否则预取按原始尺寸解码、显示按 view 尺寸解码，
	// 两份结果互不命中，预取等于白做（还得再解码一次，就是"变清晰慢"的真实原因）。
	val config = LocalConfiguration.current
	val targetSize = remember(config.screenWidthPx, config.screenHeightPx) {
		Size(config.screenWidthPx.toFloat(), config.screenHeightPx.toFloat())
	}

	// 预热原图：进入看图/翻页时就把 当前±1 张原图提交给 Coil 预加载。
	// 带上 targetSize 后预取结果直接落在内存缓存，翻页/回看时命中即显示，无需再解码。
	val imageLoader = context.app.repo.imageLoader
	val currentPage = pager.currentPage
	LaunchedEffect(currentPage, targetSize) {
		val center = currentPage.coerceIn(0, imageList.lastIndex)
		for (i in (center - 1)..(center + 1)) {
			val e = imageList.getOrNull(i) ?: continue
			val u = urlOf(e.path) ?: continue
			imageLoader.enqueue(
				ImageRequest.Builder(context)
					.data(u)
					.size(targetSize)
					.build()
			)
		}
	}

	var controlsVisible by remember { mutableStateOf(true) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var showDeleteDialog by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var isSharing by remember { mutableStateOf(false) }

	Box(
		Modifier
			.fillMaxSize()
			.background(Color.Black)
			.pointerInput(Unit) {
				detectTapGestures(onTap = { controlsVisible = !controlsVisible })
			},
	) {
		HorizontalPager(
			state = pager,
			modifier = Modifier.fillMaxSize(),
			userScrollEnabled = true,
		) { page ->
			val entry = imageList.getOrNull(page) ?: return@HorizontalPager
			ZoomableImage(
				url = urlOf(entry.path),
				thumbUrl = thumbOf?.invoke(entry.path),
				name = entry.name,
				targetSize = targetSize,
			)
		}

		// 顶栏
		AnimatedVisibility(
			visible = controlsVisible,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.TopCenter),
		) {
			TopAppBar(
				title = {
					Column {
						Text(
							current.displayName,
							style = MaterialTheme.typography.titleSmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Text(
							"${pager.currentPage + 1} / ${imageList.size}",
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
					containerColor = Color.Black.copy(alpha = 0.55f),
					titleContentColor = Color.White,
					navigationIconContentColor = Color.White,
				),
			)
		}

		// 底部大厂级专业操作栏
		AnimatedVisibility(
			visible = controlsVisible,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.BottomCenter),
		) {
			Surface(
				color = Color.Black.copy(alpha = 0.65f),
				contentColor = Color.White,
				modifier = Modifier.fillMaxWidth(),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp),
					horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically,
				) {
					// 1. 保存原图到手机相册
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures {
									if (isSaving) return@detectTapGestures
									isSaving = true
									Toast.makeText(context, "正在下载保存原图…", Toast.LENGTH_SHORT).show()
									if (onSaveToGallery != null) {
										onSaveToGallery(current) { ok, msg ->
											isSaving = false
											Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
										}
									} else {
										isSaving = false
										Toast.makeText(context, "暂不支持保存", Toast.LENGTH_SHORT).show()
									}
								}
							}
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Download, contentDescription = "存相册", tint = Color.White)
						Spacer(Modifier.height(4.dp))
						Text("存相册", style = MaterialTheme.typography.labelSmall, color = Color.White)
					}

					// 2. 一键系统微信/QQ分享
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures {
									if (isSharing) return@detectTapGestures
									isSharing = true
									Toast.makeText(context, "正在准备分享…", Toast.LENGTH_SHORT).show()
									if (onShare != null) {
										onShare(current) { file ->
											isSharing = false
											if (file != null && file.exists()) {
												try {
													val uri = FileProvider.getUriForFile(
														context,
														"${context.packageName}.fileprovider",
														file,
													)
													val intent = Intent(Intent.ACTION_SEND).apply {
														type = "image/*"
														putExtra(Intent.EXTRA_STREAM, uri)
														addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
													}
													context.startActivity(Intent.createChooser(intent, "分享图片至"))
												} catch (e: Exception) {
													Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_SHORT).show()
												}
											} else {
												Toast.makeText(context, "获取图片失败", Toast.LENGTH_SHORT).show()
											}
										}
									} else {
										isSharing = false
									}
								}
							}
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Share, contentDescription = "分享", tint = Color.White)
						Spacer(Modifier.height(4.dp))
						Text("分享", style = MaterialTheme.typography.labelSmall, color = Color.White)
					}

					// 3. 原图详情信息
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures { showInfoDialog = true }
							}
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Info, contentDescription = "详情", tint = Color.White)
						Spacer(Modifier.height(4.dp))
						Text("详情", style = MaterialTheme.typography.labelSmall, color = Color.White)
					}

					// 4. 删除当前图片
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures { showDeleteDialog = true }
							}
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = Color(0xFFFF5252))
						Spacer(Modifier.height(4.dp))
						Text("删除", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF5252))
					}
				}
			}
		}

		// 详情弹窗
		if (showInfoDialog) {
			AlertDialog(
				onDismissRequest = { showInfoDialog = false },
				title = { Text("图片详情", fontWeight = FontWeight.Bold) },
				text = {
					Column {
						Text("名称: ${current.name}", style = MaterialTheme.typography.bodyMedium)
						Spacer(Modifier.height(8.dp))
						Text("大小: ${current.sizeHuman}", style = MaterialTheme.typography.bodyMedium)
						Spacer(Modifier.height(8.dp))
						Text("路径: ${current.path}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
					}
				},
				confirmButton = {
					TextButton(onClick = { showInfoDialog = false }) { Text("关闭") }
				},
			)
		}

		// 删除确认弹窗
		if (showDeleteDialog) {
			AlertDialog(
				onDismissRequest = { showDeleteDialog = false },
				title = { Text("确认彻底删除？") },
				text = { Text("确定要从 NAS 中删除该图片吗？此操作无法撤销。\n${current.name}") },
				confirmButton = {
					Button(
						onClick = {
							showDeleteDialog = false
							val target = current
							onDelete?.invoke(target)
							val remaining = imageList.filterNot { it.path == target.path }
							if (remaining.isEmpty()) {
								onBack()
							} else {
								imageList = remaining
							}
						},
						colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
					) { Text("彻底删除") }
				},
				dismissButton = {
					TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
				},
			)
		}
	}
}

@Composable
private fun ZoomableImage(url: String?, thumbUrl: String?, name: String, targetSize: Size) {
	var scale by remember { mutableFloatStateOf(1f) }
	var dx by remember { mutableFloatStateOf(0f) }
	var dy by remember { mutableFloatStateOf(0f) }
	val context = LocalContext.current

	// 原图加载态：loading / 失败重试。切图（url 变化）时全部复位。
	var loadFailed by remember(url) { mutableStateOf(false) }
	var retryKey by remember(url) { mutableIntStateOf(0) }
	var isLoading by remember(url) { mutableStateOf(true) }

	// 每次 url / retryKey 变化重建请求；retryKey 让失败后能强制重新拉取（带 CachePolicy 绕过坏缓存）
	val fullRequest = remember(url, retryKey, targetSize) {
		ImageRequest.Builder(context)
			.data(url)
			// 明确按屏幕像素解码：Coil 默认会拿原始尺寸（手机上动辄 4000px 宽的大图），
			// 解码时间和内存都翻几倍，这正是"点开后要等一会儿才清晰"的开销。
			// 不设 Precision.EXACT：目标尺寸已接近原图，默认的 inSampleSize 路径最快，
			// 只有降采样到小于目标尺寸才会发虚，而 Coil 不会那样做。
			.size(targetSize)
			// 关闭交叉淡入：全局 imageLoader 默认 crossfade(true)，
			// 淡入的 100~300ms 在用户感知里就是"慢慢变清晰"，去掉后原图一到瞬间就锐利。
			.crossfade(false)
			.memoryCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.diskCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.build()
	}

	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		// 第 1 层：列表已缓存的服务端缩略图（144px）放大铺底。
		// 加高斯模糊 + 压暗后，它看起来是"有意的磨砂预览"而不是"糊掉的原图"，
		// 原图瞬间切入时观感是从模糊背景干净地变清晰，不会有一段刺眼的低清过渡。
		// 模糊依赖 RenderEffect，仅 API 31+ 生效；低版本直接不模糊，靠压暗遮罩过渡。
		if (!thumbUrl.isNullOrBlank() && thumbUrl != url) {
			val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
			AsyncImage(
				model = thumbUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier
					.fillMaxSize()
					.then(
						if (canBlur) {
							Modifier.blur(28.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
						} else {
							Modifier
						}
					),
			)
			Box(
				Modifier
					.fillMaxSize()
					.background(Color.Black.copy(alpha = if (canBlur) 0.18f else 0.35f)),
			)
		}

		// 第 2 层：原图（可缩放）。缩略图垫底时即使原图还没到也有画面。
		if (!url.isNullOrBlank() && !loadFailed) {
			AsyncImage(
				model = fullRequest,
				contentDescription = name,
				contentScale = ContentScale.Fit,
				onLoading = { isLoading = true },
				onSuccess = { isLoading = false; loadFailed = false },
				onError = { isLoading = false; loadFailed = true },
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
					},
			)
		}

		// 原图仍在加载且没有缩略图垫底时，给个明确的菊花，避免"点了没反应"
		if (isLoading && !loadFailed && thumbUrl.isNullOrBlank()) {
			androidx.compose.material3.CircularProgressIndicator(
				color = Color.White,
				modifier = Modifier.size(36.dp),
			)
		}

		// 加载失败（且无缩略图兜底）：明确提示 + 点击重试，绝不静默黑屏
		if (loadFailed && thumbUrl.isNullOrBlank()) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier
					.clip(RoundedCornerShape(12.dp))
					.background(Color(0xCC1E293B))
					.pointerInput(Unit) {
						detectTapGestures(onTap = {
							loadFailed = false
							isLoading = true
							retryKey++
						})
					}
					.padding(horizontal = 24.dp, vertical = 18.dp),
			) {
				Text("图片加载失败", color = Color.White, fontWeight = FontWeight.Bold)
				Text("点击重试", color = Color(0xFF9DB4D0), fontSize = 13.sp)
			}
		}
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

	val nasDb = remember { NasDb.getInstance(context) }
	val savedProgress = remember(entry.path) { nasDb.getVideoProgress(entry.path) }
	var showResumeTip by remember { mutableStateOf(savedProgress > 3000L) }

	val player = remember(url) {
		val factory = DefaultMediaSourceFactory(context)
			.setDataSourceFactory(OkHttpDataSource.Factory(http))
		// 缓冲调优：慢速隧道下默认起播门槛(2.5s)太高，一直转圈。
		// 降到 0.5s 起播、0.75s 恢复，同时把最大缓冲拉到 30s 减少二次卡顿。
		// 原生 /p/?sign= 直链支持 Range，配合大缓冲能稳定边下边播。
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				10_000,   // minBufferMs
				30_000,   // maxBufferMs
				500,      // bufferForPlaybackMs：攒够 0.5s 就起播
				750,      // bufferForPlaybackAfterRebufferMs
			)
			.setPrioritizeTimeOverSizeThresholds(true)
			.build()
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(factory)
			.setLoadControl(loadControl)
			.build()
			.apply {
				playWhenReady = true
				setMediaItem(MediaItem.fromUri(url ?: ""))
				if (savedProgress > 3000L) {
					seekTo(savedProgress)
				}
				prepare()
			}
	}

	// 自动隐藏续播提示
	LaunchedEffect(showResumeTip) {
		if (showResumeTip) {
			delay(3000)
			showResumeTip = false
		}
	}

	// 周期性同步播放进度到 NasDb
	LaunchedEffect(player) {
		while (true) {
			delay(3000)
			if (player.isPlaying) {
				nasDb.saveVideoProgress(entry.path, player.currentPosition, player.duration)
			}
		}
	}

	// 播放器状态监听与退出记忆
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
			nasDb.saveVideoProgress(entry.path, player.currentPosition, player.duration)
			player.removeListener(listener)
			player.release()
		}
	}

	// 监听应用与屏幕生命周期：锁屏/后台暂停，解锁/前台恢复播放与重新挂接渲染
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner, player) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE -> {
					player.pause()
				}
				Lifecycle.Event.ON_RESUME -> {
					if (isPlaying) {
						player.play()
					}
				}
				else -> {}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
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
				if (view.player != player) {
					view.player = player
				}
				view.resizeMode = resizeMode
				view.onResume()
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
					// 沉浸式全屏下给状态栏/刘海留白，避免返回键和标题被"眉毛"遮挡
					.statusBarsPadding()
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
							text = entry.displayName,
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

		// 自动续播浮动提示胶囊
		AnimatedVisibility(
			visible = showResumeTip,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 20.dp, bottom = 90.dp),
		) {
			Surface(
				color = Color(0xCC1E293B),
				shape = RoundedCornerShape(20.dp),
				shadowElevation = 4.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "⏱️ 已自动恢复至上次播放进度 ${formatTime(savedProgress)}",
						color = Color.White,
						fontSize = 12.sp,
						fontWeight = FontWeight.Medium,
					)
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
