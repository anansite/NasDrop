package com.nasdrop.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

	var isCurrentZoomed by remember { mutableStateOf(false) }
	LaunchedEffect(pager.currentPage) {
		isCurrentZoomed = false
	}

	var controlsVisible by remember { mutableStateOf(true) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var showDeleteDialog by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var isSharing by remember { mutableStateOf(false) }

	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.background(MediaBackdrop)
			.pointerInput(Unit) {
				detectTapGestures(onTap = { controlsVisible = !controlsVisible })
			},
	) {
		// 统一解码尺寸 = 当前约束的实际像素。
		// 预取和实际显示用**同一个 Size**，Coil 的内存缓存 key 才能对上：否则预取按原始
		// 尺寸解码、显示按 view 尺寸解码，两份结果互不命中，预取等于白做——那正是
		// "点开后还要等一会儿才清晰"的真实原因。而且 Coil 默认会按原始尺寸解码手机
		// 上动辄 4000px 宽的大图，解码时间和内存都翻几倍。
		val targetSize = with(LocalDensity.current) {
			Size(maxWidth.roundToPx().coerceAtLeast(1), maxHeight.roundToPx().coerceAtLeast(1))
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
		HorizontalPager(
			state = pager,
			modifier = Modifier.fillMaxSize(),
			userScrollEnabled = !isCurrentZoomed,
		) { page ->
			val entry = imageList.getOrNull(page) ?: return@HorizontalPager
			ZoomableImage(
				url = urlOf(entry.path),
				thumbUrl = thumbOf?.invoke(entry.path),
				name = entry.name,
				targetSize = targetSize,
				onToggleControls = { controlsVisible = !controlsVisible },
				onZoomChanged = { zoomed ->
					if (page == pager.currentPage) {
						isCurrentZoomed = zoomed
					}
				},
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
					containerColor = MediaBackdrop.copy(alpha = 0.55f),
					titleContentColor = OnMediaBackdrop,
					navigationIconContentColor = OnMediaBackdrop,
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
				color = MediaBackdrop.copy(alpha = 0.65f),
				contentColor = OnMediaBackdrop,
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
							.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Download, contentDescription = "存相册", tint = OnMediaBackdrop)
						Spacer(Modifier.height(4.dp))
						Text("存相册", style = MaterialTheme.typography.labelSmall, color = OnMediaBackdrop)
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
							.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Share, contentDescription = "分享", tint = OnMediaBackdrop)
						Spacer(Modifier.height(4.dp))
						Text("分享", style = MaterialTheme.typography.labelSmall, color = OnMediaBackdrop)
					}

					// 3. 原图详情信息
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures { showInfoDialog = true }
							}
							.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Info, contentDescription = "详情", tint = OnMediaBackdrop)
						Spacer(Modifier.height(4.dp))
						Text("详情", style = MaterialTheme.typography.labelSmall, color = OnMediaBackdrop)
					}

					// 4. 删除当前图片
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(8.dp))
							.pointerInput(Unit) {
								detectTapGestures { showDeleteDialog = true }
							}
							.sizeIn(minWidth = 56.dp, minHeight = 56.dp)
							.padding(horizontal = 8.dp, vertical = 4.dp),
					) {
						Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MediaDanger)
						Spacer(Modifier.height(4.dp))
						Text("删除", style = MaterialTheme.typography.labelSmall, color = MediaDanger)
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
						Text("路径: ${current.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
							colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
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
private fun ZoomableImage(
	url: String?,
	thumbUrl: String?,
	name: String,
	targetSize: Size,
	onToggleControls: () -> Unit,
	onZoomChanged: (Boolean) -> Unit = {},
) {
	var scale by remember { mutableFloatStateOf(1f) }
	var dx by remember { mutableFloatStateOf(0f) }
	var dy by remember { mutableFloatStateOf(0f) }
	val context = LocalContext.current

	LaunchedEffect(scale) {
		onZoomChanged(scale > 1.05f)
	}

	// 原图加载态：loading / 失败重试。切图（url 变化）时全部复位。
	var loadFailed by remember(url) { mutableStateOf(false) }
	var retryKey by remember(url) { mutableIntStateOf(0) }
	var isLoading by remember(url) { mutableStateOf(true) }

	// 每次 url / retryKey 变化重建请求；retryKey 让失败后能强制重新拉取（带 CachePolicy 绕过坏缓存）
	val fullRequest = remember(url, retryKey, targetSize) {
		ImageRequest.Builder(context)
			.data(url)
			.size(targetSize)
			.crossfade(false)
			.memoryCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.diskCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.build()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				detectTapGestures(onTap = { onToggleControls() })
			},
		contentAlignment = Alignment.Center,
	) {
		// 第 1 层：先完整展示已缓存的缩略图（ContentScale.Fit，原比例无裁剪、不加重度磨砂暗罩）
		// 让用户第一时间看清画面主体，高清原图到货后自然淡入取代
		if (!thumbUrl.isNullOrBlank() && (isLoading || loadFailed)) {
			AsyncImage(
				model = thumbUrl,
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						scaleX = scale
						scaleY = scale
						translationX = dx
						translationY = dy
					},
			)
		}

		// 第 2 层：原图（可自由缩放与多指手势）
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
					.then(
						if (scale > 1.05f) {
							Modifier.pointerInput(scale > 1.05f) {
								detectTransformGestures { _, pan, zoom, _ ->
									val newScale = (scale * zoom).coerceIn(1f, 6f)
									scale = newScale
									if (newScale > 1f) {
										dx += pan.x
										dy += pan.y
									} else {
										dx = 0f
										dy = 0f
									}
								}
							}
						} else {
							Modifier
						}
					)
					.pointerInput(Unit) {
						detectTapGestures(
							onTap = { onToggleControls() },
							onDoubleTap = {
								if (scale > 1.05f) {
									scale = 1f; dx = 0f; dy = 0f
								} else {
									scale = 2.5f
								}
							},
						)
					},
			)
		}

		// 动态加载动画指示器：不加全屏遮罩，底部胶囊优雅提示高清加载进度
		if (isLoading && !loadFailed) {
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = Color.Black.copy(alpha = 0.65f),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 88.dp),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					CircularProgressIndicator(
						strokeWidth = 2.dp,
						color = Color.White,
						modifier = Modifier.size(13.dp),
					)
					Text(
						text = if (!thumbUrl.isNullOrBlank()) "正在载入高清细节…" else "正在加载图片…",
						fontSize = 11.sp,
						fontWeight = FontWeight.Medium,
						color = Color.White,
					)
				}
			}
		}

		// 加载失败：明确提示 + 点击重试，绝不静默黑屏
		if (loadFailed && thumbUrl.isNullOrBlank()) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier
					.clip(RoundedCornerShape(12.dp))
					.background(MediaOverlay.copy(alpha = 0.9f))
					.pointerInput(Unit) {
						detectTapGestures(onTap = {
							loadFailed = false
							isLoading = true
							retryKey++
						})
					}
					.padding(horizontal = 24.dp, vertical = 18.dp),
			) {
				Text("图片加载失败", color = OnMediaBackdrop, fontWeight = FontWeight.Bold)
				Text("点击重试", color = MediaAccent, fontSize = 13.sp)
			}
		}
	}
}

/**
 * 现代流媒体级超清视频进度条：
 * - 纤巧微轨设计，交互拖拽时灵动微扩
 * - 支持底层缓冲轨（实际已缓冲视频流）
 * - 渐变高光已播放进度条
 * - 精致发光游标白点，支持直接点触跳转与横向精准洗切 (scrubbing)
 */
@Composable
fun ModernVideoScrubber(
	currentPosition: Long,
	bufferedPosition: Long,
	duration: Long,
	onSeek: (Long) -> Unit,
	modifier: Modifier = Modifier,
	onInteraction: () -> Unit = {},
) {
	var isDragging by remember { mutableStateOf(false) }
	var dragFraction by remember { mutableFloatStateOf(0f) }

	val currentFraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
	val bufferedFraction = if (duration > 0) (bufferedPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
	val displayFraction = if (isDragging) dragFraction else currentFraction

	val animatedTrackHeight by animateDpAsState(
		targetValue = if (isDragging) 6.dp else 4.dp,
		label = "trackHeight"
	)
	val animatedThumbRadius by animateDpAsState(
		targetValue = if (isDragging) 8.dp else 6.dp,
		label = "thumbRadius"
	)

	Box(
		modifier = modifier
			.height(36.dp)
			.fillMaxWidth()
			.pointerInput(duration) {
				detectTapGestures { offset ->
					if (duration > 0 && size.width > 0) {
						val frac = (offset.x / size.width).coerceIn(0f, 1f)
						onSeek((frac * duration).toLong())
						onInteraction()
					}
				}
			}
			.pointerInput(duration) {
				detectDragGestures(
					onDragStart = { offset ->
						isDragging = true
						if (duration > 0 && size.width > 0) {
							dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
						}
						onInteraction()
					},
					onDrag = { change, _ ->
						change.consume()
						if (duration > 0 && size.width > 0) {
							dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
						}
						onInteraction()
					},
					onDragEnd = {
						isDragging = false
						if (duration > 0) {
							onSeek((dragFraction * duration).toLong())
						}
						onInteraction()
					},
					onDragCancel = {
						isDragging = false
					}
				)
			},
		contentAlignment = Alignment.CenterStart
	) {
		Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
			val w = size.width
			val h = size.height
			val cy = h / 2f
			val th = animatedTrackHeight.toPx()
			val r = th / 2f

			// 1. 底层背景轨
			drawRoundRect(
				color = Color(0x38FFFFFF),
				topLeft = Offset(0f, cy - r),
				size = GeometrySize(w, th),
				cornerRadius = CornerRadius(r, r)
			)

			// 2. 缓冲轨
			val bufW = (w * bufferedFraction).coerceIn(0f, w)
			if (bufW > 0f) {
				drawRoundRect(
					color = Color(0x70FFFFFF),
					topLeft = Offset(0f, cy - r),
					size = GeometrySize(bufW, th),
					cornerRadius = CornerRadius(r, r)
				)
			}

			// 3. 播放进度轨（活力紫色高光渐变）
			val playW = (w * displayFraction).coerceIn(0f, w)
			if (playW > 0f) {
				drawRoundRect(
					brush = Brush.horizontalGradient(
						listOf(Color(0xFF8B5CF6), Color(0xFFA855F7), Color(0xFFC084FC))
					),
					topLeft = Offset(0f, cy - r),
					size = GeometrySize(playW, th),
					cornerRadius = CornerRadius(r, r)
				)
			}

			// 4. 游标白点 Thumb（拖拽时带外扩散晕圈）
			val thumbX = playW.coerceIn(0f, w)
			val tr = animatedThumbRadius.toPx()
			if (isDragging) {
				drawCircle(
					color = Color(0x4DA855F7),
					radius = tr * 2.2f,
					center = Offset(thumbX, cy)
				)
			}
			drawCircle(
				color = Color.White,
				radius = tr,
				center = Offset(thumbX, cy)
			)
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
	 * 播放器配置沿用 Media3 的 UnstableApi，在此入口显式确认以满足 Android Lint。
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun VideoPlayer(entry: DavEntry, url: String?, onBack: () -> Unit) {
	val context = LocalContext.current
	val activity = context as? Activity
	val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
	val http = context.app.repo.http()

	var showControls by remember { mutableStateOf(true) }
	var isPlaying by remember { mutableStateOf(true) }
	var currentPosition by remember { mutableLongStateOf(0L) }
	var bufferedPosition by remember { mutableLongStateOf(0L) }
	var duration by remember { mutableLongStateOf(0L) }
	var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
	var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
	var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

	// 周期性轮询播放进度与缓冲进度
	LaunchedEffect(player) {
		while (true) {
			if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY) {
				currentPosition = player.currentPosition.coerceAtLeast(0L)
				bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
				duration = player.duration.coerceAtLeast(0L)
			}
			delay(250)
		}
	}

	// 控制栏自动隐藏定时器（4秒无操作自动隐藏）
	LaunchedEffect(showControls, lastInteractionTime) {
		if (showControls) {
			delay(4000)
			showControls = false
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(MediaBackdrop),
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

		// 全屏透明交互手势层（位于 PlayerView 之上，优先捕捉点击唤出/隐藏控制栏与滑动手势）
		Box(
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(duration) {
					awaitEachGesture {
						val down = awaitFirstDown(requireUnconsumed = false)
						val startTime = System.currentTimeMillis()
						val startPos = down.position
						var isDrag = false
						var totalDx = 0f
						var totalDy = 0f
						val isRightSide = startPos.x > size.width / 2f
						var isSeeking = false

						while (true) {
							val event = awaitPointerEvent()
							val change = event.changes.firstOrNull { it.id == down.id } ?: break
							if (!change.pressed) {
								// 松手（UP）
								val elapsed = System.currentTimeMillis() - startTime
								val distSq = (change.position.x - startPos.x) * (change.position.x - startPos.x) +
									(change.position.y - startPos.y) * (change.position.y - startPos.y)
								if (!isDrag && distSq < 900 && elapsed < 400) {
									// 确认无大幅滑动、短时抬手：100% 触发控制栏显隐切换
									showControls = !showControls
									lastInteractionTime = System.currentTimeMillis()
								}
								if (isSeeking) {
									val seekDelta = (totalDx * 150).toLong()
									val target = (player.currentPosition + seekDelta).coerceIn(0L, duration.coerceAtLeast(1L))
									player.seekTo(target)
									currentPosition = target
									lastInteractionTime = System.currentTimeMillis()
								}
								hudVisible = false
								break
							} else {
								val dx = change.position.x - startPos.x
								val dy = change.position.y - startPos.y
								val distSq = dx * dx + dy * dy
								// 移动超过 30 像素才判定为拖拽，彻底避免单击产生微抖误触快进
								if (!isDrag && distSq > 900) {
									isDrag = true
								}
								if (isDrag) {
									change.consume()
									totalDx = dx
									totalDy = dy
									lastInteractionTime = System.currentTimeMillis()
									if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy) * 1.5f || isSeeking) {
										// 水平滑动：快进 / 快退
										isSeeking = true
										val seekDelta = (totalDx * 150).toLong()
										val target = (player.currentPosition + seekDelta).coerceIn(0L, duration.coerceAtLeast(1L))
										hudText = "${formatTime(target)} / ${formatTime(duration)}"
										hudVisible = true
									} else {
										// 垂直滑动：左侧调节屏幕亮度，右侧调节媒体音量
										val deltaFraction = -dy / size.height.toFloat()
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
								}
							}
						}
					}
				},
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
				color = MediaBackdrop.copy(alpha = 0.75f),
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
						tint = OnMediaBackdrop,
						modifier = Modifier.size(24.dp),
					)
					Text(
						text = hudText,
						color = OnMediaBackdrop,
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
							colors = listOf(MediaBackdrop.copy(alpha = 0.85f), Color.Transparent),
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
							Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = OnMediaBackdrop)
						}
						Text(
							text = entry.displayName,
								modifier = Modifier.weight(1f),
							color = OnMediaBackdrop,
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
							color = OnMediaBackdrop.copy(alpha = 0.2f),
							onClick = {
								resizeMode = when (resizeMode) {
									AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
									AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
									else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
								}
							},
						) {
							Row(
								modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
									.padding(horizontal = 8.dp, vertical = 4.dp),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(4.dp),
							) {
								Icon(Icons.Rounded.AspectRatio, contentDescription = "画幅", tint = OnMediaBackdrop, modifier = Modifier.size(14.dp))
								Text(
									text = when (resizeMode) {
										AspectRatioFrameLayout.RESIZE_MODE_FIT -> "适应"
										AspectRatioFrameLayout.RESIZE_MODE_FILL -> "铺满"
										else -> "居中"
									},
									color = OnMediaBackdrop,
									fontSize = 11.sp,
									fontWeight = FontWeight.Bold,
								)
							}
						}

						// 直接播放原文件，不根据容器格式宣称实际分辨率。
						Surface(
							shape = RoundedCornerShape(14.dp),
							color = MediaAccent.copy(alpha = 0.85f),
						) {
							Text(
								text = "原始文件",
								color = MediaBackdrop,
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
							colors = listOf(Color.Transparent, MediaBackdrop.copy(alpha = 0.9f)),
						),
					)
					.padding(horizontal = 16.dp, vertical = 12.dp),
			) {
				Column(modifier = Modifier.fillMaxWidth()) {
					// 现代高质感微轨进度条与时间戳
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = formatTime(currentPosition),
							color = OnMediaBackdrop,
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
						)

						ModernVideoScrubber(
							currentPosition = currentPosition,
							bufferedPosition = bufferedPosition,
							duration = duration,
							onSeek = { target ->
								player.seekTo(target)
								currentPosition = target
							},
							onInteraction = { lastInteractionTime = System.currentTimeMillis() },
							modifier = Modifier
								.weight(1f)
								.padding(horizontal = 12.dp),
						)

						Text(
							text = formatTime(duration),
							color = OnMediaBackdrop.copy(alpha = 0.7f),
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
								color = OnMediaBackdrop,
								onClick = {
									if (player.isPlaying) player.pause() else player.play()
									lastInteractionTime = System.currentTimeMillis()
								},
								modifier = Modifier.size(40.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
										contentDescription = if (isPlaying) "暂停" else "播放",
										tint = MediaBackdrop,
										modifier = Modifier.size(24.dp),
									)
								}
							}

							// 快退 10 秒
							IconButton(onClick = {
								val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
								player.seekTo(target)
								lastInteractionTime = System.currentTimeMillis()
							}) {
								Icon(Icons.Rounded.Replay10, contentDescription = "快退10秒", tint = OnMediaBackdrop)
							}

							// 快进 10 秒
							IconButton(onClick = {
								val target = (player.currentPosition + 10_000L).coerceAtMost(duration)
								player.seekTo(target)
								lastInteractionTime = System.currentTimeMillis()
							}) {
								Icon(Icons.Rounded.Forward10, contentDescription = "快进10秒", tint = OnMediaBackdrop)
							}
						}

						// 倍速切换药丸
						val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
						Surface(
							shape = RoundedCornerShape(16.dp),
							color = OnMediaBackdrop.copy(alpha = 0.2f),
							onClick = {
								val nextIdx = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
								val nextSpeed = speeds[nextIdx]
								playbackSpeed = nextSpeed
								player.playbackParameters = PlaybackParameters(nextSpeed)
								lastInteractionTime = System.currentTimeMillis()
							},
						) {
							Text(
								text = if (playbackSpeed == 1.0f) "1.0x" else "${playbackSpeed}x",
								color = OnMediaBackdrop,
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
				color = MediaOverlay.copy(alpha = 0.9f),
				shape = RoundedCornerShape(20.dp),
				shadowElevation = 4.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = "⏱️ 已自动恢复至上次播放进度 ${formatTime(savedProgress)}",
						color = OnMediaBackdrop,
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
