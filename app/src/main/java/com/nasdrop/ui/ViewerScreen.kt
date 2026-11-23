package com.nasdrop.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import android.content.ClipboardManager
import android.content.ClipData
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.OutlinedTextField
import com.nasdrop.PhotoMetadataStore
import com.nasdrop.CompanionAudioPlayer
import com.nasdrop.CompanionAudioRecorder
import com.nasdrop.hasAudio
import com.nasdrop.hasMemo
import com.nasdrop.memoText
import com.nasdrop.audioDurationText
import com.nasdrop.photoMeta
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Place
import com.nasdrop.MediaExifHelper
import com.nasdrop.MediaExifData
import com.nasdrop.NasDb
import com.nasdrop.DavEntry
import com.nasdrop.DlnaDevice
import com.nasdrop.DlnaManager
import com.nasdrop.DlnaPlaybackState
import com.nasdrop.app
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
	fallbackUrlOf: ((String) -> String?)? = null,
	/** 列表已加载的服务端缩略图直链，作为原图加载期间/失败时的即时垫底图 */
	thumbOf: ((String) -> String?)? = null,
	audioUrlOf: ((String) -> String?)? = null,
	onBack: (DavEntry?) -> Unit,
	onSaveToGallery: ((DavEntry, (Boolean, String) -> Unit) -> Unit)? = null,
	onShare: ((DavEntry, (java.io.File?) -> Unit) -> Unit)? = null,
	onDelete: ((DavEntry) -> Unit)? = null,
	onSaveAudio: ((DavEntry, ByteArray, Long, (Boolean, String?) -> Unit) -> Unit)? = null,
	onDeleteAudio: ((DavEntry, (Boolean, String?) -> Unit) -> Unit)? = null,
	onSaveMemo: ((DavEntry, String) -> Unit)? = null,
	onDeleteMemo: ((DavEntry) -> Unit)? = null,
) {
	val context = LocalContext.current
	var imageList by remember(images) { mutableStateOf(images) }
	if (imageList.isEmpty()) {
		LaunchedEffect(Unit) { onBack(null) }
		return
	}

	val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, imageList.lastIndex)) {
		imageList.size
	}
	val current = imageList.getOrNull(pager.currentPage.coerceIn(0, imageList.lastIndex))

	BackHandler {
		onBack(current)
	}

	if (current == null) {
		LaunchedEffect(Unit) { onBack(null) }
		return
	}

	var isCurrentZoomed by remember { mutableStateOf(false) }
	LaunchedEffect(pager.currentPage) {
		isCurrentZoomed = false
	}

	var controlsVisible by remember { mutableStateOf(true) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var exifData by remember(current.path) { mutableStateOf<MediaExifData?>(null) }
	var loadingExif by remember(current.path) { mutableStateOf(false) }

	LaunchedEffect(showInfoDialog, current?.path) {
		if (showInfoDialog && exifData == null && current != null) {
			loadingExif = true
			val activeUrl = urlOf(current.path) ?: fallbackUrlOf?.invoke(current.path) ?: thumbOf?.invoke(current.path)
			exifData = MediaExifHelper.extractExif(context, current, activeUrl, context.app.repo.http())
			loadingExif = false
		}
	}
	var showDeleteDialog by remember { mutableStateOf(false) }
	var isSaving by remember { mutableStateOf(false) }
	var isSharing by remember { mutableStateOf(false) }

	// 照片伴随记忆资产状态
	var showMemoDialog by remember { mutableStateOf(false) }
	var memoInput by remember { mutableStateOf("") }
	var showRecordSheet by remember { mutableStateOf(false) }
	var showDeleteAudioConfirm by remember { mutableStateOf(false) }
	var isMemoryExpanded by remember(pager.currentPage) { mutableStateOf(false) }

	val audioPlayer = remember { CompanionAudioPlayer(context) }
	val audioRecorder = remember { CompanionAudioRecorder(context) }

	val micPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			audioRecorder.start()
		} else {
			Toast.makeText(context, "需要麦克风权限才能录制伴随解说", Toast.LENGTH_SHORT).show()
		}
	}

	DisposableEffect(current.path) {
		val companionPath = PhotoMetadataStore.computeCompanionAudioPath(current.path)
		val directAudio = audioUrlOf?.invoke(companionPath) ?: (audioUrlOf ?: urlOf).invoke(current.path)
		if (current.hasAudio && directAudio != null) {
			audioPlayer.prepare(directAudio)
		} else {
			audioPlayer.prepare(directAudio ?: "")
		}
		onDispose {
			audioPlayer.stop()
			audioRecorder.cancel()
		}
	}

	BoxWithConstraints(
		Modifier
			.fillMaxSize()
			.background(MediaBackdrop),
	) {
		// 统一解码尺寸 = 当前约束的实际像素。
		val targetSize = with(LocalDensity.current) {
			Size(maxWidth.roundToPx().coerceAtLeast(1), maxHeight.roundToPx().coerceAtLeast(1))
		}

		// 预热：进入看图/翻页时仅预取相邻两张的极速缩略图（每张仅约20KB，30ms极速就绪，绝不抢占当前主图的高清大图网络带宽）
		val imageLoader = context.app.repo.imageLoader
		val currentPage = pager.currentPage
		LaunchedEffect(currentPage) {
			val center = currentPage.coerceIn(0, imageList.lastIndex)
			for (i in listOf(center + 1, center - 1)) {
				val e = imageList.getOrNull(i) ?: continue
				val tu = thumbOf?.invoke(e.path) ?: continue
				imageLoader.enqueue(
					ImageRequest.Builder(context)
						.data(tu)
						.memoryCacheKey("thumb:${e.path.trim('/')}")
						.diskCacheKey("thumb:${e.path.trim('/')}")
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
				fallbackUrl = fallbackUrlOf?.invoke(entry.path),
				thumbUrl = thumbOf?.invoke(entry.path),
				path = entry.path,
				name = entry.name,
				sizeHuman = entry.sizeHuman,
				targetSize = targetSize,
				onToggleControls = {
					if (isMemoryExpanded) {
						isMemoryExpanded = false
					} else {
						controlsVisible = !controlsVisible
					}
				},
				onZoomChanged = { zoomed ->
					if (page == pager.currentPage) {
						isCurrentZoomed = zoomed
					}
				},
				onDismiss = { onBack(current) },
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
				modifier = Modifier.pointerInput(Unit) {
					detectTapGestures(onTap = { controlsVisible = !controlsVisible })
				},
				title = {
					Column {
						Text(
							current.name,
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
					IconButton(onClick = { onBack(current) }) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
					}
				},
				actions = {
					IconButton(
						onClick = {
							memoInput = current.memoText ?: ""
							showMemoDialog = true
						}
					) {
						Icon(
							Icons.Rounded.EditNote,
							contentDescription = "照片备注",
							tint = if (current.hasMemo) Color(0xFF34D399) else OnMediaBackdrop,
							modifier = Modifier.size(24.dp),
						)
					}
					IconButton(
						onClick = {
							showRecordSheet = true
						}
					) {
						Icon(
							Icons.Rounded.Mic,
							contentDescription = "录制旁白",
							tint = if (current.hasAudio) Color(0xFF38BDF8) else OnMediaBackdrop,
							modifier = Modifier.size(24.dp),
						)
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
				modifier = Modifier
					.fillMaxWidth()
					.pointerInput(Unit) {
						detectTapGestures(onTap = { controlsVisible = !controlsVisible })
					},
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

		// ==========================================
		// 伴随记忆资产（方案 1：右下角极简常驻微胶囊 + 按需展开）
		// ==========================================
		val currentAudioText = current.audioDurationText
		val currentMemo = current.memoText
		val hasAudio = current.hasAudio && !currentAudioText.isNullOrEmpty()
		val hasMemo = current.hasMemo && !currentMemo.isNullOrEmpty()

		// 1. 折叠状态：右下角 26dp 呼吸微胶囊（零遮挡，轻触即播/展开）
		AnimatedVisibility(
			visible = controlsVisible && (hasAudio || hasMemo) && !isMemoryExpanded,
			enter = fadeIn() + scaleIn(),
			exit = fadeOut() + scaleOut(),
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(end = 16.dp, bottom = 92.dp),
		) {
			Surface(
				shape = RoundedCornerShape(14.dp),
				color = Color(0xDD0F172A),
				border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
				shadowElevation = 6.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (hasAudio) {
						// 原地播放/暂停小圆钮
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primary,
							onClick = {
								if (audioPlayer.currentUrl.isEmpty()) {
									val companionPath = PhotoMetadataStore.computeCompanionAudioPath(current.path)
									val audioUrl = audioUrlOf?.invoke(companionPath)
									if (audioUrl != null) audioPlayer.prepare(audioUrl)
								}
								audioPlayer.togglePlayPause(current.photoMeta?.audioDurationMs ?: 0L)
							},
							modifier = Modifier.size(22.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = if (audioPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
									contentDescription = if (audioPlayer.isPlaying) "暂停" else "播放",
									tint = Color.White,
									modifier = Modifier.size(13.dp),
								)
							}
						}
						// 播放状态与时长
						val curSec = audioPlayer.currentPositionMs / 1000
						val totalSec = ((if (audioPlayer.durationMs > 0) audioPlayer.durationMs.toLong() else current.photoMeta?.audioDurationMs ?: 0L) / 1000).toInt()
						Text(
							text = if (audioPlayer.isPlaying) {
								String.format(java.util.Locale.ROOT, "%02d:%02d", curSec / 60, curSec % 60)
							} else {
								currentAudioText ?: String.format(java.util.Locale.ROOT, "%02d:%02d", totalSec / 60, totalSec % 60)
							},
							fontSize = 11.sp,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
							modifier = Modifier.clickable { isMemoryExpanded = true },
						)
					}

					if (hasAudio && hasMemo) {
						Box(
							modifier = Modifier
								.width(1.dp)
								.height(10.dp)
								.background(Color.White.copy(alpha = 0.25f))
						)
					}

					if (hasMemo) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
							modifier = Modifier.clickable { isMemoryExpanded = true },
						) {
							Text("📝", fontSize = 11.sp)
							Text(
								text = "故事",
								fontSize = 11.sp,
								fontWeight = FontWeight.Medium,
								color = Color(0xFF34D399),
							)
						}
					}

					Icon(
						imageVector = Icons.Rounded.KeyboardArrowUp,
						contentDescription = "展开详情",
						tint = Color(0xFF94A3B8),
						modifier = Modifier
							.size(14.dp)
							.clickable { isMemoryExpanded = true },
					)
				}
			}
		}

		// 2. 展开状态：微型悬浮控制条与故事备注（按需呼出，支持收起）
		AnimatedVisibility(
			visible = controlsVisible && (hasAudio || hasMemo) && isMemoryExpanded,
			enter = fadeIn() + slideInVertically { it / 2 },
			exit = fadeOut() + slideOutVertically { it / 2 },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(horizontal = 16.dp)
				.padding(bottom = 92.dp)
				.fillMaxWidth(),
		) {
			Column(
				verticalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				// 顶部收起栏
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = Color(0xDE0F172A),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						onClick = { isMemoryExpanded = false },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Text("收起记忆", fontSize = 11.sp, color = Color(0xFF94A3B8))
							Icon(
								Icons.Rounded.KeyboardArrowDown,
								contentDescription = "收起",
								tint = Color(0xFF94A3B8),
								modifier = Modifier.size(14.dp),
							)
						}
					}
				}

				if (hasAudio) {
					Surface(
						shape = RoundedCornerShape(20.dp),
						color = Color(0xEE1E293B),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						shadowElevation = 8.dp,
						modifier = Modifier
							.fillMaxWidth()
							.height(56.dp),
					) {
						Row(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 12.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							// 播放 / 暂停按钮
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.primary,
								onClick = {
									if (audioPlayer.currentUrl.isEmpty()) {
										val companionPath = PhotoMetadataStore.computeCompanionAudioPath(current.path)
										val audioUrl = audioUrlOf?.invoke(companionPath)
										if (audioUrl != null) audioPlayer.prepare(audioUrl)
									}
									audioPlayer.togglePlayPause(current.photoMeta?.audioDurationMs ?: 0L)
								},
								modifier = Modifier.size(36.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (audioPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
										contentDescription = if (audioPlayer.isPlaying) "暂停伴随解说" else "播放伴随解说",
										tint = Color.White,
										modifier = Modifier.size(20.dp),
									)
								}
							}

							// 进度与微轨（无多余小圆点，干净流畅）
							Column(modifier = Modifier.weight(1f)) {
								BoxWithConstraints(
									modifier = Modifier
										.fillMaxWidth()
										.height(24.dp)
										.pointerInput(audioPlayer.durationMs) {
											detectTapGestures { offset ->
												val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
												audioPlayer.seekTo(frac)
											}
										}
										.pointerInput(audioPlayer.durationMs) {
											detectDragGestures { change, _ ->
												change.consume()
												val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
												audioPlayer.seekTo(frac)
											}
										},
									contentAlignment = Alignment.CenterStart,
								) {
									val wPx = constraints.maxWidth.toFloat()
									val progress = audioPlayer.progress.coerceIn(0f, 1f)
									val playW = (progress * wPx).coerceIn(0f, wPx)

									Box(
										modifier = Modifier
											.fillMaxWidth()
											.height(4.dp)
											.clip(RoundedCornerShape(2.dp))
											.background(Color.White.copy(alpha = 0.22f))
									)
									Box(
										modifier = Modifier
											.width(with(LocalDensity.current) { playW.toDp() })
											.height(4.dp)
											.clip(RoundedCornerShape(2.dp))
											.background(MaterialTheme.colorScheme.primary)
									)
								}

								Spacer(Modifier.height(2.dp))
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("伴随语音旁白", fontSize = 10.sp, color = Color(0xFF94A3B8))
									val curSec = audioPlayer.currentPositionMs / 1000
									val totalSec = ((if (audioPlayer.durationMs > 0) audioPlayer.durationMs.toLong() else current.photoMeta?.audioDurationMs ?: 0L) / 1000).toInt()
									Text(
										text = String.format(java.util.Locale.ROOT, "%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totalSec / 60, totalSec % 60),
										fontSize = 10.sp,
										color = Color(0xFF94A3B8),
									)
								}
							}

							// 单独删除伴随录音
							IconButton(
								onClick = { showDeleteAudioConfirm = true },
								modifier = Modifier.size(32.dp),
							) {
								Icon(
									Icons.Rounded.DeleteOutline,
									contentDescription = "删除伴随录音",
									tint = Color(0xFFEF4444),
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}

				if (hasMemo) {
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = Color(0xDE0F172A),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						shadowElevation = 6.dp,
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(16.dp))
							.clickable {
								memoInput = currentMemo ?: ""
								showMemoDialog = true
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Box(
								modifier = Modifier
									.width(3.dp)
									.height(14.dp)
									.clip(RoundedCornerShape(1.5.dp))
									.background(Color(0xFF34D399))
							)
							Text(
								text = currentMemo ?: "",
								fontSize = 13.sp,
								lineHeight = 17.sp,
								color = Color(0xFFF1F5F9),
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}
		}

		// 底部图片属性与参数抽屉
		if (showInfoDialog) {
			ModalBottomSheet(
				onDismissRequest = { showInfoDialog = false },
				containerColor = MaterialTheme.colorScheme.surface,
			) {
				val scrollState = rememberScrollState()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(scrollState)
						.padding(horizontal = 24.dp)
						.padding(bottom = 36.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text("图片属性详情", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
						IconButton(onClick = { showInfoDialog = false }) {
							Icon(Icons.Rounded.Close, contentDescription = "关闭")
						}
					}

					// 1. 基础属性卡片
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
							Text("基础属性", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

							ImageDetailItem(label = "文件名称", value = current.name)

							val rawSize = current.size
							val sizeDetail = if (rawSize > 0) {
								"${current.sizeHuman} (${java.text.NumberFormat.getNumberInstance(Locale.CHINA).format(rawSize)} 字节)"
							} else {
								current.sizeHuman.ifBlank { formatSize(current.size) }
							}
							ImageDetailItem(label = "文件大小", value = sizeDetail)

							ImageDetailItem(
								label = "存储路径",
								value = current.path,
								canCopy = true,
								onCopy = {
									val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
									clipboard?.setPrimaryClip(ClipData.newPlainText("NAS Path", current.path))
									Toast.makeText(context, "已复制存储路径", Toast.LENGTH_SHORT).show()
								}
							)

							val createTime = current.effectiveDate
							val createTimeStr = if (createTime > 0) {
								SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(createTime))
							} else "未知"
							ImageDetailItem(label = "创建/拍摄时间", value = createTimeStr)

							if (current.modified > 0 && kotlin.math.abs(current.modified - createTime) > 60_000L) {
								val uploadTimeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(current.modified))
								ImageDetailItem(label = "修改/上传时间", value = uploadTimeStr)
							}

							val res = exifData?.resolution
							val mp = exifData?.megapixels
							val resStr = if (res != null && mp != null) "$res ($mp)" else res
							if (resStr != null) {
								ImageDetailItem(label = "图像分辨率", value = resStr)
							}

							ImageDetailItem(
								label = "文件类型",
								value = current.name.substringAfterLast('.', "未知").uppercase(Locale.ROOT)
							)
						}
					}

					// 2. 拍摄参数卡片（EXIF）
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
								Text("拍摄参数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
							}

							if (loadingExif) {
								LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
								Text("正在提取 EXIF 参数...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							} else if (exifData != null && exifData!!.hasShootingParams) {
								val d = exifData!!
								if (!d.camera.isNullOrBlank()) {
									ImageDetailItem(label = "相机型号", value = d.camera)
								}
								if (!d.lens.isNullOrBlank()) {
									ImageDetailItem(label = "镜头型号", value = d.lens)
								}
								if (!d.aperture.isNullOrBlank()) {
									ImageDetailItem(label = "光圈大小", value = d.aperture)
								}
								if (!d.shutterSpeed.isNullOrBlank()) {
									ImageDetailItem(label = "快门速度", value = d.shutterSpeed)
								}
								if (!d.iso.isNullOrBlank()) {
									ImageDetailItem(label = "感光度", value = d.iso)
								}
								if (!d.focalLength.isNullOrBlank()) {
									ImageDetailItem(label = "镜头焦距", value = d.focalLength)
								}
							} else {
								Text("未读取到该图片的相机拍摄参数", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
					}

					// 3. 拍摄地点卡片（GPS）
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
								Text("拍摄地点", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
							}

							if (loadingExif) {
								LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
							} else if (exifData != null && exifData!!.hasGps) {
								val d = exifData!!
								ImageDetailItem(
									label = "GPS 坐标",
									value = d.gpsFormatted ?: "${d.latitude}, ${d.longitude}",
									canCopy = true,
									onCopy = {
										val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
										clipboard?.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", "${d.latitude}, ${d.longitude}"))
										Toast.makeText(context, "已复制 GPS 坐标", Toast.LENGTH_SHORT).show()
									}
								)
								if (d.altitude != null) {
									ImageDetailItem(label = "海拔高度", value = String.format(Locale.ROOT, "%.1f 米", d.altitude))
								}

								Row(
									modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
									horizontalArrangement = Arrangement.End,
								) {
									OutlinedButton(
										onClick = {
											try {
												val geoUri = Uri.parse("geo:${d.latitude},${d.longitude}?q=${d.latitude},${d.longitude}(${Uri.encode(current.name)})")
												val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
												context.startActivity(mapIntent)
											} catch (_: Exception) {
												Toast.makeText(context, "未找到可用地图应用", Toast.LENGTH_SHORT).show()
											}
										},
										contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
									) {
										Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(16.dp))
										Spacer(Modifier.width(6.dp))
										Text("在地图中查看", fontSize = 13.sp)
									}
								}
							} else {
								Text("未读取到该图片的地理位置信息", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
					}
				}
			}
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
								onBack(null)
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

		// 照片文字备注修改对话框
		if (showMemoDialog) {
			AlertDialog(
				onDismissRequest = { showMemoDialog = false },
				title = { Text("照片故事与备注") },
				text = {
					Column {
						OutlinedTextField(
							value = memoInput,
							onValueChange = { memoInput = it },
							placeholder = { Text("记录这张照片背后的故事…") },
							modifier = Modifier.fillMaxWidth(),
							maxLines = 5,
						)
					}
				},
				confirmButton = {
					Button(
						onClick = {
							showMemoDialog = false
							val cleanText = memoInput.trim()
							if (cleanText.isNotEmpty()) {
								PhotoMetadataStore.updateMemo(current.path, cleanText)
								onSaveMemo?.invoke(current, cleanText)
								Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show()
							} else {
								PhotoMetadataStore.updateMemo(current.path, null)
								onDeleteMemo?.invoke(current)
								Toast.makeText(context, "备注已清空", Toast.LENGTH_SHORT).show()
							}
						}
					) {
						Text("保存")
					}
				},
				dismissButton = {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						if (!current.memoText.isNullOrEmpty()) {
							TextButton(
								onClick = {
									showMemoDialog = false
									PhotoMetadataStore.updateMemo(current.path, null)
									onDeleteMemo?.invoke(current)
									Toast.makeText(context, "备注已清空", Toast.LENGTH_SHORT).show()
								}
							) {
								Text("清空删除", color = MaterialTheme.colorScheme.error)
							}
						}
						TextButton(onClick = { showMemoDialog = false }) {
							Text("取消")
						}
					}
				}
			)
		}

		// 照片录音中浮层 Sheet
		if (showRecordSheet) {
			ModalBottomSheet(
				onDismissRequest = { showRecordSheet = false },
				containerColor = MaterialTheme.colorScheme.surface,
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 24.dp)
						.padding(bottom = 36.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Text("为照片录制伴随旁白", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
					Text(
						"录音文件存放在照片同级目录下以'.'开头（默认隐藏），数据库仅记指针，零二进制",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					val seconds = audioRecorder.elapsedSeconds
					Text(
						text = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
						fontSize = 32.sp,
						fontWeight = FontWeight.Bold,
						fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically,
					) {
						TextButton(
							onClick = {
								audioRecorder.cancel()
								showRecordSheet = false
							}
						) {
							Text("取消")
						}

						Button(
							onClick = {
								if (!audioRecorder.isRecording) {
									if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
										val ok = audioRecorder.start()
										if (!ok) Toast.makeText(context, "无法启动录音", Toast.LENGTH_SHORT).show()
									} else {
										micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
									}
								} else {
									val result = audioRecorder.stop()
									showRecordSheet = false
									if (result != null) {
										val (bytes, durMs) = result
										val companionPath = PhotoMetadataStore.computeCompanionAudioPath(current.path)
										PhotoMetadataStore.updateAudio(current.path, companionPath, durMs)
										val directAudio = audioUrlOf?.invoke(companionPath)
										if (directAudio != null) {
											audioPlayer.prepare(directAudio)
										}
										onSaveAudio?.invoke(current, bytes, durMs) { ok, err ->
											Toast.makeText(context, if (ok) "伴随旁白已保存 (${durMs/1000}秒)" else (err ?: "保存失败"), Toast.LENGTH_SHORT).show()
										}
									} else {
										Toast.makeText(context, "未录制有效声音", Toast.LENGTH_SHORT).show()
									}
								}
							},
							colors = ButtonDefaults.buttonColors(
								containerColor = if (audioRecorder.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
							)
						) {
							Text(if (audioRecorder.isRecording) "停止并保存" else "开始录制")
						}
					}
				}
			}
		}

		// 删除照片伴随录音确认对话框
		if (showDeleteAudioConfirm) {
			AlertDialog(
				onDismissRequest = { showDeleteAudioConfirm = false },
				title = { Text("确认删除伴随旁白？") },
				text = { Text("将删除照片绑定的语音解说文件，照片本体保留完好。此操作不可撤销。") },
				confirmButton = {
					Button(
						onClick = {
							showDeleteAudioConfirm = false
							audioPlayer.stop()
							PhotoMetadataStore.updateAudio(current.path, null, 0L)
							onDeleteAudio?.invoke(current) { ok, err ->
								Toast.makeText(context, if (ok) "伴随旁白已删除" else (err ?: "删除失败"), Toast.LENGTH_SHORT).show()
							}
						},
						colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
					) {
						Text("确认删除")
					}
				},
				dismissButton = {
					TextButton(onClick = { showDeleteAudioConfirm = false }) {
						Text("取消")
					}
				}
			)
		}
	}
}

@Composable
private fun ImageDetailItem(
	label: String,
	value: String,
	canCopy: Boolean = false,
	onCopy: (() -> Unit)? = null,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			Spacer(modifier = Modifier.height(2.dp))
			Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
		}
		if (canCopy && onCopy != null) {
			IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
			}
		}
	}
}

@Composable
private fun ZoomableImage(
	url: String?,
	fallbackUrl: String? = null,
	thumbUrl: String?,
	path: String = "",
	name: String,
	sizeHuman: String = "",
	targetSize: Size,
	onToggleControls: () -> Unit,
	onZoomChanged: (Boolean) -> Unit = {},
	onDismiss: () -> Unit = {},
) {
	var scale by remember { mutableFloatStateOf(1f) }
	var dx by remember { mutableFloatStateOf(0f) }
	var dy by remember { mutableFloatStateOf(0f) }
	var dismissOffsetY by remember { mutableFloatStateOf(0f) }
	val animatedDismissOffsetY by animateFloatAsState(
		targetValue = dismissOffsetY,
		animationSpec = spring(stiffness = Spring.StiffnessMedium),
		label = "dismiss_offset"
	)
	var lastTapTime by remember { mutableLongStateOf(0L) }
	val context = LocalContext.current

	// 点开默认立即拉取原图；原图就绪前以缩略图垫底秒开展示，并附带轻巧加载指示
	var activeUrl by remember(url) { mutableStateOf(url) }
	var triedFallback by remember(url) { mutableStateOf(false) }
	var isFullLoading by remember(activeUrl) { mutableStateOf(true) }
	var isFullLoaded by remember(activeUrl) { mutableStateOf(false) }
	var fullLoadFailed by remember(activeUrl) { mutableStateOf(false) }
	var retryKey by remember(activeUrl) { mutableIntStateOf(0) }

	LaunchedEffect(scale) {
		onZoomChanged(scale > 1.05f)
	}

	// 每次 activeUrl / retryKey 变化重建请求；支持重试与坏缓存穿透
	val fullRequest = remember(activeUrl, retryKey, targetSize) {
		ImageRequest.Builder(context)
			.data(activeUrl)
			.size(targetSize)
			.precision(coil.size.Precision.INEXACT)
			.allowHardware(true)
			.crossfade(true)
			.placeholderMemoryCacheKey("thumb:$path")
			.memoryCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.diskCachePolicy(if (retryKey > 0) coil.request.CachePolicy.WRITE_ONLY else coil.request.CachePolicy.ENABLED)
			.build()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				detectTapGestures(
					onDoubleTap = {
						if (scale > 1.05f) {
							scale = 1f
							dx = 0f
							dy = 0f
						} else {
							scale = 2.5f
						}
					},
					onTap = {
						onToggleControls()
					}
				)
			}
			.pointerInput(scale > 1.05f) {
				if (scale > 1.05f) {
					// 放大状态：支持自由平移拖拽与多指缩放
					detectTransformGestures { _, pan, zoom, _ ->
						val newScale = (scale * zoom).coerceIn(1f, 5f)
						scale = newScale
						if (newScale > 1.05f) {
							dx += pan.x
							dy += pan.y
						} else {
							scale = 1f
							dx = 0f
							dy = 0f
						}
					}
				} else {
					// 正常比例：仅监听双指 Pinch 缩放手势，单指左右滑动绝不拦截消费，完全留给 HorizontalPager 左右翻页！
					awaitEachGesture {
						do {
							val event = awaitPointerEvent()
							if (event.changes.size > 1) {
								val zoom = event.calculateZoom()
								if (zoom > 1.05f) {
									scale = (scale * zoom).coerceIn(1f, 5f)
									event.changes.forEach { it.consume() }
								}
							}
						} while (event.changes.any { it.pressed })
					}
				}
			}
			.pointerInput(scale > 1.05f) {
				if (scale <= 1.05f) {
					detectVerticalDragGestures(
						onDragEnd = {
							if (dismissOffsetY > 160f) {
								onDismiss()
							} else {
								dismissOffsetY = 0f
							}
						},
						onDragCancel = {
							dismissOffsetY = 0f
						},
						onVerticalDrag = { change, dragAmount ->
							if ((dragAmount > 0 && dismissOffsetY == 0f) || dismissOffsetY > 0f) {
								change.consume()
								dismissOffsetY = (dismissOffsetY + dragAmount).coerceAtLeast(0f)
							}
						}
					)
				}
			},
		contentAlignment = Alignment.Center,
	) {
		// 第 1 层：优先完整展示低带宽缩略图（原比例 Fit，跟随缩放手势），保证进入即刻看清画面，绝不黑屏/白屏
		if (!thumbUrl.isNullOrBlank() && !isFullLoaded) {
			AsyncImage(
				model = ImageRequest.Builder(LocalContext.current)
					.data(thumbUrl)
					.memoryCacheKey("thumb:${path.trim('/')}")
					.diskCacheKey("thumb:${path.trim('/')}")
					.placeholderMemoryCacheKey("thumb:${path.trim('/')}")
					.crossfade(false)
					.build(),
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						val dragFraction = (animatedDismissOffsetY / 1500f).coerceIn(0f, 0.35f)
						scaleX = scale * (1f - dragFraction)
						scaleY = scale * (1f - dragFraction)
						translationX = dx
						translationY = dy + animatedDismissOffsetY
					},
			)
		}

		// 第 2 层：高清原图（默认立即拉取，到位后自然淡入取代缩略图，提供极致清晰度）
		if (!activeUrl.isNullOrBlank() && !fullLoadFailed) {
			AsyncImage(
				model = fullRequest,
				contentDescription = name,
				contentScale = ContentScale.Fit,
				onLoading = { isFullLoading = true; fullLoadFailed = false },
				onSuccess = { isFullLoading = false; isFullLoaded = true; fullLoadFailed = false },
				onError = { errorState ->
					android.util.Log.e("NasDropViewer", "Full image load failed: url=$activeUrl (fallback=$fallbackUrl)", errorState.result.throwable)
					if (!triedFallback && !fallbackUrl.isNullOrBlank() && fallbackUrl != activeUrl) {
						triedFallback = true
						activeUrl = fallbackUrl
						isFullLoading = true
						fullLoadFailed = false
					} else {
						isFullLoading = false
						fullLoadFailed = true
					}
				},
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						val dragFraction = (animatedDismissOffsetY / 1500f).coerceIn(0f, 0.35f)
						scaleX = scale * (1f - dragFraction)
						scaleY = scale * (1f - dragFraction)
						translationX = dx
						translationY = dy + animatedDismissOffsetY
					},
			)
		}

		// 原图加载中状态：保留缩略图背景，底部悬浮轻盈胶囊指示加载动画
		if (isFullLoading && !isFullLoaded) {
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = Color.Black.copy(alpha = 0.65f),
				border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = 92.dp),
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
						text = if (!thumbUrl.isNullOrBlank()) "正在载入高清原图…" else "正在加载图片…",
						fontSize = 12.sp,
						fontWeight = FontWeight.Medium,
						color = Color.White,
					)
				}
			}
		}

		// 原图加载失败时的处理
		if (fullLoadFailed) {
			if (!thumbUrl.isNullOrBlank()) {
				// 若缩略图仍在显示，在底部提示失败并允许点击重试原图
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = Color.Black.copy(alpha = 0.7f),
					border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = 92.dp)
						.clickable {
							fullLoadFailed = false
							isFullLoading = true
							triedFallback = false
							activeUrl = url
							retryKey++
						},
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							text = "高清原图加载失败，点击重试",
							fontSize = 12.sp,
							fontWeight = FontWeight.Medium,
							color = Color(0xFFFFB4AB),
						)
					}
				}
			} else {
				// 无缩略图且原图失败：居中大卡片重试
				Column(
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(12.dp),
					modifier = Modifier
						.clip(RoundedCornerShape(12.dp))
						.background(MediaOverlay.copy(alpha = 0.9f))
						.clickable {
							fullLoadFailed = false
							isFullLoading = true
							triedFallback = false
							activeUrl = url
							retryKey++
						}
						.padding(horizontal = 24.dp, vertical = 18.dp),
				) {
					Text("图片加载失败", color = OnMediaBackdrop, fontWeight = FontWeight.Bold)
					Text("点击重试", color = MediaAccent, fontSize = 13.sp)
				}
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
	onSeeking: ((Long, Boolean) -> Unit)? = null,
) {
	var isDragging by remember { mutableStateOf(false) }
	var dragFraction by remember { mutableFloatStateOf(0f) }

	val currentFraction = if (duration > 0) {
		if (currentPosition >= duration) 1f else (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
	} else 0f
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
						val target = (frac * duration).toLong()
						onSeek(target)
						onSeeking?.invoke(target, false)
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
							onSeeking?.invoke((dragFraction * duration).toLong(), true)
						}
						onInteraction()
					},
					onDrag = { change, _ ->
						change.consume()
						if (duration > 0 && size.width > 0) {
							dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
							onSeeking?.invoke((dragFraction * duration).toLong(), true)
						}
						onInteraction()
					},
					onDragEnd = {
						isDragging = false
						if (duration > 0) {
							val target = (dragFraction * duration).toLong()
							onSeek(target)
							onSeeking?.invoke(target, false)
						}
						onInteraction()
					},
					onDragCancel = {
						isDragging = false
						onSeeking?.invoke(0L, false)
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

			// 4. 游标白点 Thumb（仅在主动拖拽定位时显现扩散光晕与指示白点，播放时保持流线纯净无多余小点）
			if (isDragging) {
				val thumbX = playW.coerceIn(0f, w)
				val tr = animatedThumbRadius.toPx()
				drawCircle(
					color = Color(0x4DA855F7),
					radius = tr * 2.2f,
					center = Offset(thumbX, cy)
				)
				drawCircle(
					color = Color.White,
					radius = tr,
					center = Offset(thumbX, cy)
				)
			}
		}
	}
}

private fun setImmersiveFullscreen(activity: Activity?, enabled: Boolean) {
	val window = activity?.window ?: return
	val controller = WindowCompat.getInsetsController(window, window.decorView)
	if (enabled) {
		controller?.hide(WindowInsetsCompat.Type.systemBars())
		controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
	} else {
		controller?.show(WindowInsetsCompat.Type.systemBars())
	}
}

/**
 * 商业级专业网盘视频播放器：
 * - 手势调节：左侧上下滑调节亮度、右侧上下滑调节音量、水平横滑快进快退预览
 * - 极速预览：长按屏幕任意处触发 2.0x 极速快进，松手恢复原速
 * - 倍速切换：0.75x、1.0x、1.25x、1.5x、2.0x
 * - 画面比例切换：适应屏幕 (FIT) vs 铺满 (FILL) vs 居中缩放 (ZOOM)
 * - 悬浮手势 HUD 实时反馈
 * - 沉浸式真全屏与无线投屏串流中心
 * 播放器配置沿用 Media3 的 UnstableApi，在此入口显式确认以满足 Android Lint。
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
	entry: DavEntry,
	url: String?,
	audioUrlOf: ((String) -> String?)? = null,
	onBack: () -> Unit,
	onSaveToGallery: ((DavEntry, (Boolean, String) -> Unit) -> Unit)? = null,
	onSaveAudio: ((DavEntry, ByteArray, Long, (Boolean, String?) -> Unit) -> Unit)? = null,
	onDeleteAudio: ((DavEntry, (Boolean, String?) -> Unit) -> Unit)? = null,
	onSaveMemo: ((DavEntry, String) -> Unit)? = null,
	onDeleteMemo: ((DavEntry) -> Unit)? = null,
) {
	val context = LocalContext.current
	val activity = context as? Activity
	val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
	val http = context.app.repo.http()

	var currentEntry by remember(entry) { mutableStateOf(entry) }
	var showControls by remember { mutableStateOf(true) }
	var isPlaying by remember { mutableStateOf(true) }
	var isSaving by remember { mutableStateOf(false) }
	var currentPosition by remember { mutableLongStateOf(0L) }
	var bufferedPosition by remember { mutableLongStateOf(0L) }
	var duration by remember { mutableLongStateOf(0L) }
	var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
	var normalPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
	var lastTapTimestamp by remember { mutableLongStateOf(0L) }
	var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
	var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
	var showCastSheet by remember { mutableStateOf(false) }

	// 视频记忆资产状态
	var showMemoDialog by remember { mutableStateOf(false) }
	var memoInput by remember { mutableStateOf("") }
	var showRecordSheet by remember { mutableStateOf(false) }
	var showDeleteAudioConfirm by remember { mutableStateOf(false) }
	var isMemoryExpanded by remember(currentEntry.path) { mutableStateOf(false) }

	val audioPlayer = remember { com.nasdrop.CompanionAudioPlayer(context) }
	val audioRecorder = remember { com.nasdrop.CompanionAudioRecorder(context) }

	val micPermissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			audioRecorder.start()
		} else {
			Toast.makeText(context, "需要麦克风权限才能录制伴随解说", Toast.LENGTH_SHORT).show()
		}
	}

	DisposableEffect(currentEntry.path) {
		val companionPath = PhotoMetadataStore.computeCompanionAudioPath(currentEntry.path)
		val directAudioUrl = audioUrlOf?.invoke(companionPath) ?: audioUrlOf?.invoke(currentEntry.path) ?: ""
		if (currentEntry.hasAudio && directAudioUrl.isNotEmpty()) {
			audioPlayer.prepare(directAudioUrl)
		}
		onDispose {
			audioPlayer.stop()
			audioRecorder.cancel()
		}
	}

	val configuration = androidx.compose.ui.platform.LocalConfiguration.current
	val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

	// 拦截系统返回键：横屏全屏时优先退出全屏回竖屏；竖屏时才退出播放器
	BackHandler {
		if (isLandscape) {
			activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		} else {
			onBack()
		}
	}

	// 横屏联动沉浸式全屏（隐藏状态栏与虚拟导航键，100% 满屏无黑边沉浸）
	LaunchedEffect(isLandscape) {
		setImmersiveFullscreen(activity, isLandscape)
	}

	// 退出播放器时自动恢复系统方向与系统栏显示
	DisposableEffect(Unit) {
		onDispose {
			setImmersiveFullscreen(activity, false)
			activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
		}
	}

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
				if (state == Player.STATE_ENDED) {
					if (duration > 0) currentPosition = duration
					isPlaying = false
				}
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
			if (player.playbackState == Player.STATE_ENDED) {
				if (duration > 0) currentPosition = duration
			} else if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY) {
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

		// 全屏透明交互手势层（位于 PlayerView 之上，优先捕捉长按 2.0x、双击快退/快进、点击显隐控制栏与滑动手势）
		Box(
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(Unit) {
					awaitEachGesture {
						val down = awaitFirstDown(requireUnconsumed = false)
						val startTime = System.currentTimeMillis()
						val startPos = down.position
						var isDrag = false
						var totalDx = 0f
						var totalDy = 0f
						val isRightSide = startPos.x > size.width / 2f
						var isSeeking = false
						var longPressTriggered = false

						while (true) {
							val event = awaitPointerEvent()
							val change = event.changes.firstOrNull { it.id == down.id } ?: break
							val elapsed = System.currentTimeMillis() - startTime

							if (!change.pressed) {
								// 松手（UP）
								if (longPressTriggered) {
									// 长按松手：立即平滑恢复原播放速度
									player.playbackParameters = PlaybackParameters(normalPlaybackSpeed)
									hudVisible = false
								} else {
									val distSq = (change.position.x - startPos.x) * (change.position.x - startPos.x) +
										(change.position.y - startPos.y) * (change.position.y - startPos.y)
									if (!isDrag && distSq < 900 && elapsed < 400) {
										val now = System.currentTimeMillis()
										if (now - lastTapTimestamp < 350) {
											// 双击手势：左右两侧快退/快进 10s，中间切换播放/暂停
											lastTapTimestamp = 0L
											if (startPos.x < size.width * 0.35f) {
												val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
												player.seekTo(target)
												currentPosition = target
												hudText = "⏪ 快退 10 秒"
												hudVisible = true
											} else if (startPos.x > size.width * 0.65f) {
												val target = (player.currentPosition + 10000L).coerceAtMost(duration.coerceAtLeast(1L))
												player.seekTo(target)
												currentPosition = target
												hudText = "⏩ 快进 10 秒"
												hudVisible = true
											} else {
												if (player.isPlaying) player.pause() else player.play()
												hudText = if (player.isPlaying) "⏸ 暂停" else "▶ 播放"
												hudVisible = true
											}
											lastInteractionTime = System.currentTimeMillis()
										} else {
											lastTapTimestamp = now
											showControls = !showControls
											lastInteractionTime = System.currentTimeMillis()
										}
									}
									if (isSeeking) {
										val seekDelta = (totalDx * 150).toLong()
										val target = (player.currentPosition + seekDelta).coerceIn(0L, duration.coerceAtLeast(1L))
										player.seekTo(target)
										currentPosition = target
										lastInteractionTime = System.currentTimeMillis()
									}
									if (!longPressTriggered) {
										hudVisible = false
									}
								}
								break
							} else {
								val dx = change.position.x - startPos.x
								val dy = change.position.y - startPos.y
								val distSq = dx * dx + dy * dy

								// 长按 2.0x 极速检测：未滑动且持续按压超过 400ms 触发
								if (!isDrag && distSq < 900 && elapsed >= 400 && !longPressTriggered) {
									longPressTriggered = true
									normalPlaybackSpeed = player.playbackParameters.speed
									player.playbackParameters = PlaybackParameters(2.0f)
									hudText = "▶▶ 2.0X 极速快进中"
									hudVisible = true
								}

								if (!longPressTriggered) {
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
						else if (hudText.contains("快退") || hudText.contains("⏪")) Icons.Rounded.Replay10
						else if (hudText.contains("快进") || hudText.contains("⏩")) Icons.Rounded.Forward10
						else if (hudText.contains("2.0X")) Icons.Rounded.FastForward
						else if (hudText.contains("暂停")) Icons.Rounded.Pause
						else if (hudText.contains("播放")) Icons.Rounded.PlayArrow
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
					.pointerInput(Unit) {
						detectTapGestures(onTap = { showControls = !showControls })
					}
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
						IconButton(onClick = {
							if (isLandscape) {
								activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
							} else {
								onBack()
							}
						}) {
							Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = OnMediaBackdrop)
						}
						Text(
							text = entry.name,
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
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						// 视频文字备注按钮
						IconButton(
							onClick = {
								lastInteractionTime = System.currentTimeMillis()
								memoInput = currentEntry.memoText ?: ""
								showMemoDialog = true
							}
						) {
							Icon(
								Icons.Rounded.EditNote,
								contentDescription = "视频备注",
								tint = if (currentEntry.hasMemo) Color(0xFF34D399) else OnMediaBackdrop,
								modifier = Modifier.size(24.dp),
							)
						}

						// 视频伴随录音解说按钮
						IconButton(
							onClick = {
								lastInteractionTime = System.currentTimeMillis()
								showRecordSheet = true
							}
						) {
							Icon(
								Icons.Rounded.Mic,
								contentDescription = "录制视频旁白",
								tint = if (currentEntry.hasAudio) Color(0xFF38BDF8) else OnMediaBackdrop,
								modifier = Modifier.size(24.dp),
							)
						}

						// 保存到手机本地相册/视频目录
						IconButton(
							onClick = {
								lastInteractionTime = System.currentTimeMillis()
								if (isSaving) return@IconButton
								isSaving = true
								Toast.makeText(context, "正在下载保存视频…", Toast.LENGTH_SHORT).show()
								if (onSaveToGallery != null) {
									onSaveToGallery(entry) { ok, msg ->
										isSaving = false
										Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
									}
								} else {
									isSaving = false
									Toast.makeText(context, "暂不支持保存", Toast.LENGTH_SHORT).show()
								}
							}
						) {
							Icon(
								Icons.Rounded.Download,
								contentDescription = "保存到本地",
								tint = OnMediaBackdrop,
								modifier = Modifier.size(24.dp),
							)
						}

						// 投屏与串流直链中心按钮
						IconButton(
							onClick = {
								lastInteractionTime = System.currentTimeMillis()
								showCastSheet = true
							}
						) {
							Icon(
								Icons.Rounded.Cast,
								contentDescription = "投屏与串流直链",
								tint = OnMediaBackdrop,
								modifier = Modifier.size(24.dp),
							)
						}

						// 全屏 / 横竖屏旋转切换按钮
						IconButton(
							onClick = {
								lastInteractionTime = System.currentTimeMillis()
								activity?.let { act ->
									val isCurrentlyLandscape = act.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
									if (isCurrentlyLandscape) {
										act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
									} else {
										act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
									}
								}
							}
						) {
							val isLandscape = activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
							Icon(
								imageVector = if (isLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
								contentDescription = if (isLandscape) "退出横屏" else "横屏全屏",
								tint = OnMediaBackdrop,
								modifier = Modifier.size(26.dp),
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
					.pointerInput(Unit) {
						detectTapGestures(onTap = { showControls = !showControls })
					}
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
							onSeeking = { target, active ->
								if (active) {
									hudText = "${formatTime(target)} / ${formatTime(duration)}"
									hudVisible = true
								} else {
									hudVisible = false
								}
								lastInteractionTime = System.currentTimeMillis()
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

						// 画幅与倍速药丸组合
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							// 画幅比例切换药丸 (FIT 自适应 / ZOOM 居中裁剪铺满 / FILL 拉伸满屏)
							val resizeModes = listOf(
								Triple(AspectRatioFrameLayout.RESIZE_MODE_FIT, "自适应 (原始)", "FIT"),
								Triple(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "居中裁剪铺满", "ZOOM"),
								Triple(AspectRatioFrameLayout.RESIZE_MODE_FILL, "拉伸铺满全屏", "FILL")
							)
							Surface(
								shape = RoundedCornerShape(16.dp),
								color = OnMediaBackdrop.copy(alpha = 0.2f),
								onClick = {
									val currentIdx = resizeModes.indexOfFirst { it.first == resizeMode }.coerceAtLeast(0)
									val next = resizeModes[(currentIdx + 1) % resizeModes.size]
									resizeMode = next.first
									hudText = "画幅: ${next.second}"
									hudVisible = true
									lastInteractionTime = System.currentTimeMillis()
								},
							) {
								Text(
									text = "画幅 " + (resizeModes.firstOrNull { it.first == resizeMode }?.third ?: "FIT"),
									color = OnMediaBackdrop,
									fontSize = 12.sp,
									fontWeight = FontWeight.Bold,
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
								)
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
									normalPlaybackSpeed = nextSpeed
									player.playbackParameters = PlaybackParameters(nextSpeed)
									hudText = "播放倍速: ${if (nextSpeed == 1.0f) "1.0X 正常" else "${nextSpeed}X"}"
									hudVisible = true
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
		}

		// ==========================================
		// 视频伴随记忆资产（方案 1：右下角极简常驻微胶囊 + 按需展开）
		// ==========================================
		val currentAudioText = currentEntry.audioDurationText
		val currentMemo = currentEntry.memoText
		val hasAudio = currentEntry.hasAudio && !currentAudioText.isNullOrEmpty()
		val hasMemo = currentEntry.hasMemo && !currentMemo.isNullOrEmpty()

		// 1. 折叠状态：右下角 26dp 呼吸微胶囊（零遮挡，轻触即播/展开）
		AnimatedVisibility(
			visible = showControls && (hasAudio || hasMemo) && !isMemoryExpanded,
			enter = fadeIn() + scaleIn(),
			exit = fadeOut() + scaleOut(),
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.padding(end = 16.dp, bottom = 116.dp),
		) {
			Surface(
				shape = RoundedCornerShape(14.dp),
				color = Color(0xDD0F172A),
				border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
				shadowElevation = 6.dp,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (hasAudio) {
						// 原地播放/暂停小圆钮
						Surface(
							shape = CircleShape,
							color = MaterialTheme.colorScheme.primary,
							onClick = {
								if (audioPlayer.currentUrl.isEmpty()) {
									val companionPath = PhotoMetadataStore.computeCompanionAudioPath(currentEntry.path)
									val audioUrl = audioUrlOf?.invoke(companionPath)
									if (audioUrl != null) audioPlayer.prepare(audioUrl)
								}
								audioPlayer.togglePlayPause(currentEntry.photoMeta?.audioDurationMs ?: 0L)
							},
							modifier = Modifier.size(22.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(
									imageVector = if (audioPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
									contentDescription = if (audioPlayer.isPlaying) "暂停" else "播放",
									tint = Color.White,
									modifier = Modifier.size(13.dp),
								)
							}
						}
						// 播放状态与时长
						val curSec = audioPlayer.currentPositionMs / 1000
						val totalSec = ((if (audioPlayer.durationMs > 0) audioPlayer.durationMs.toLong() else currentEntry.photoMeta?.audioDurationMs ?: 0L) / 1000).toInt()
						Text(
							text = if (audioPlayer.isPlaying) {
								String.format(java.util.Locale.ROOT, "%02d:%02d", curSec / 60, curSec % 60)
							} else {
								currentAudioText ?: String.format(java.util.Locale.ROOT, "%02d:%02d", totalSec / 60, totalSec % 60)
							},
							fontSize = 11.sp,
							fontWeight = FontWeight.SemiBold,
							color = Color.White,
							modifier = Modifier.clickable { isMemoryExpanded = true },
						)
					}

					if (hasAudio && hasMemo) {
						Box(
							modifier = Modifier
								.width(1.dp)
								.height(10.dp)
								.background(Color.White.copy(alpha = 0.25f))
						)
					}

					if (hasMemo) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
							modifier = Modifier.clickable { isMemoryExpanded = true },
						) {
							Text("📝", fontSize = 11.sp)
							Text(
								text = "故事",
								fontSize = 11.sp,
								fontWeight = FontWeight.Medium,
								color = Color(0xFF38BDF8),
							)
						}
					}

					Icon(
						imageVector = Icons.Rounded.KeyboardArrowUp,
						contentDescription = "展开详情",
						tint = Color(0xFF94A3B8),
						modifier = Modifier
							.size(14.dp)
							.clickable { isMemoryExpanded = true },
					)
				}
			}
		}

		// 2. 展开状态：微型悬浮控制条与故事备注（按需呼出，支持收起）
		AnimatedVisibility(
			visible = showControls && (hasAudio || hasMemo) && isMemoryExpanded,
			enter = fadeIn() + slideInVertically { it / 2 },
			exit = fadeOut() + slideOutVertically { it / 2 },
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(horizontal = 16.dp)
				.padding(bottom = 116.dp)
				.fillMaxWidth(),
		) {
			Column(
				verticalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				// 顶部收起栏
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = Color(0xDE0F172A),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						onClick = { isMemoryExpanded = false },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Text("收起记忆", fontSize = 11.sp, color = Color(0xFF94A3B8))
							Icon(
								Icons.Rounded.KeyboardArrowDown,
								contentDescription = "收起",
								tint = Color(0xFF94A3B8),
								modifier = Modifier.size(14.dp),
							)
						}
					}
				}

				if (hasAudio) {
					Surface(
						shape = RoundedCornerShape(20.dp),
						color = Color(0xDE0F172A),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						shadowElevation = 8.dp,
						modifier = Modifier
							.fillMaxWidth()
							.height(56.dp),
					) {
						Row(
							modifier = Modifier
								.fillMaxSize()
								.padding(horizontal = 12.dp, vertical = 6.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							// 播放 / 暂停按钮
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.primary,
								onClick = {
									if (audioPlayer.currentUrl.isEmpty()) {
										val companionPath = PhotoMetadataStore.computeCompanionAudioPath(currentEntry.path)
										val audioUrl = audioUrlOf?.invoke(companionPath)
										if (audioUrl != null) audioPlayer.prepare(audioUrl)
									}
									audioPlayer.togglePlayPause(currentEntry.photoMeta?.audioDurationMs ?: 0L)
								},
								modifier = Modifier.size(36.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										imageVector = if (audioPlayer.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
										contentDescription = if (audioPlayer.isPlaying) "暂停伴随解说" else "播放伴随解说",
										tint = Color.White,
										modifier = Modifier.size(20.dp),
									)
								}
							}

							// 进度与微轨
							Column(modifier = Modifier.weight(1f)) {
								BoxWithConstraints(
									modifier = Modifier
										.fillMaxWidth()
										.height(24.dp)
										.pointerInput(audioPlayer.durationMs) {
											detectTapGestures { offset ->
												val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
												audioPlayer.seekTo(frac)
											}
										}
										.pointerInput(audioPlayer.durationMs) {
											detectDragGestures { change, _ ->
												change.consume()
												val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
												audioPlayer.seekTo(frac)
											}
										},
									contentAlignment = Alignment.CenterStart,
								) {
									val wPx = constraints.maxWidth.toFloat()
									val progress = audioPlayer.progress.coerceIn(0f, 1f)
									val playW = (progress * wPx).coerceIn(0f, wPx)

									Box(
										modifier = Modifier
											.fillMaxWidth()
											.height(4.dp)
											.clip(RoundedCornerShape(2.dp))
											.background(Color.White.copy(alpha = 0.22f))
									)
									Box(
										modifier = Modifier
											.width(with(androidx.compose.ui.platform.LocalDensity.current) { playW.toDp() })
											.height(4.dp)
											.clip(RoundedCornerShape(2.dp))
											.background(MaterialTheme.colorScheme.primary)
									)
								}

								Spacer(Modifier.height(2.dp))
								Row(
									modifier = Modifier.fillMaxWidth(),
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text("伴随语音旁白", fontSize = 10.sp, color = Color(0xFF94A3B8))
									val curSec = audioPlayer.currentPositionMs / 1000
									val totalSec = ((if (audioPlayer.durationMs > 0) audioPlayer.durationMs.toLong() else currentEntry.photoMeta?.audioDurationMs ?: 0L) / 1000).toInt()
									Text(
										text = String.format(java.util.Locale.ROOT, "%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totalSec / 60, totalSec % 60),
										fontSize = 10.sp,
										color = Color(0xFF94A3B8),
									)
								}
							}

							// 单独删除伴随录音
							androidx.compose.material3.IconButton(
								onClick = { showDeleteAudioConfirm = true },
								modifier = Modifier.size(32.dp),
							) {
								Icon(
									Icons.Rounded.DeleteOutline,
									contentDescription = "删除伴随录音",
									tint = Color(0xFFEF4444),
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
				}

				if (hasMemo) {
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = Color(0xDE0F172A),
						border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
						shadowElevation = 6.dp,
						modifier = Modifier
							.fillMaxWidth()
							.clip(RoundedCornerShape(16.dp))
							.clickable {
								memoInput = currentMemo ?: ""
								showMemoDialog = true
							},
					) {
						Row(
							modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Box(
								modifier = Modifier
									.width(3.dp)
									.height(14.dp)
									.clip(RoundedCornerShape(1.5.dp))
									.background(Color(0xFF38BDF8))
							)
							Text(
								text = currentMemo ?: "",
								fontSize = 13.sp,
								lineHeight = 17.sp,
								color = Color(0xFFF1F5F9),
								maxLines = 2,
								overflow = TextOverflow.Ellipsis,
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

		// 无线投屏与串流直链中心底部抽屉
		if (showCastSheet) {
			val dlnaManager = remember { DlnaManager.getInstance(context) }
			val dlnaDevices by dlnaManager.devices.collectAsState()
			val isDlnaSearching by dlnaManager.isSearching.collectAsState()
			val activeDlnaDevice by dlnaManager.activeDevice.collectAsState()
			val dlnaPlaybackState by dlnaManager.playbackState.collectAsState()
			val dlnaStatusMessage by dlnaManager.statusMessage.collectAsState()
			val castScope = rememberCoroutineScope()

			LaunchedEffect(Unit) {
				dlnaManager.searchDevices()
			}

			ModalBottomSheet(
				onDismissRequest = { showCastSheet = false },
				containerColor = MaterialTheme.colorScheme.surface,
			) {
				val scrollState = rememberScrollState()
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.verticalScroll(scrollState)
						.padding(horizontal = 24.dp)
						.padding(bottom = 36.dp),
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					// 顶部标题栏 + 状态与关闭
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.primaryContainer,
								modifier = Modifier.size(36.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										Icons.Rounded.Cast,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.onPrimaryContainer,
										modifier = Modifier.size(20.dp),
									)
								}
							}
							Column {
								Text("DLNA 电视投屏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
								Text(
									text = dlnaStatusMessage.ifBlank { "局域网智能电视 · 盒子 · 投影仪直投" },
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						IconButton(onClick = { showCastSheet = false }) {
							Icon(Icons.Rounded.Close, contentDescription = "关闭")
						}
					}

					// 投屏中状态与大屏播控卡片（如果有设备正在投屏）
					if (activeDlnaDevice != null) {
						Surface(
							shape = RoundedCornerShape(16.dp),
							color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
							border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(
								modifier = Modifier.padding(16.dp),
								verticalArrangement = Arrangement.spacedBy(10.dp),
							) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(8.dp),
									) {
										Icon(Icons.Rounded.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
										Column {
											Text(
												text = activeDlnaDevice?.name ?: "已连接电视",
												style = MaterialTheme.typography.titleSmall,
												fontWeight = FontWeight.Bold,
											)
											Text(
												text = when (dlnaPlaybackState) {
													DlnaPlaybackState.CONNECTING -> "正在投屏连接中…"
													DlnaPlaybackState.PLAYING -> "大屏正在播放中"
													DlnaPlaybackState.PAUSED -> "大屏已暂停"
													DlnaPlaybackState.ERROR -> "投屏异常"
													else -> "已连接"
												},
												style = MaterialTheme.typography.labelSmall,
												color = MaterialTheme.colorScheme.primary,
											)
										}
									}

									// 大屏播控按钮组
									Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
										IconButton(
											onClick = {
												castScope.launch {
													if (dlnaPlaybackState == DlnaPlaybackState.PLAYING) {
														dlnaManager.pause()
													} else {
														dlnaManager.resume()
													}
												}
											},
											modifier = Modifier
												.size(36.dp)
												.background(MaterialTheme.colorScheme.primary, CircleShape),
										) {
											Icon(
												imageVector = if (dlnaPlaybackState == DlnaPlaybackState.PLAYING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
												contentDescription = "播放/暂停",
												tint = MaterialTheme.colorScheme.onPrimary,
												modifier = Modifier.size(20.dp),
											)
										}

										IconButton(
											onClick = {
												castScope.launch {
													dlnaManager.stop()
												}
											},
											modifier = Modifier
												.size(36.dp)
												.background(MaterialTheme.colorScheme.errorContainer, CircleShape),
										) {
											Icon(
												Icons.Rounded.Stop,
												contentDescription = "退出投屏",
												tint = MaterialTheme.colorScheme.onErrorContainer,
												modifier = Modifier.size(20.dp),
											)
										}
									}
								}
							}
						}
					}

					// 局域网 DLNA 电视直投专区
					Surface(
						shape = RoundedCornerShape(16.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
						modifier = Modifier.fillMaxWidth(),
					) {
						Column(
							modifier = Modifier.padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(12.dp),
						) {
							Row(
								modifier = Modifier.fillMaxWidth(),
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.SpaceBetween,
							) {
								Column {
									Text("局域网电视设备 (DLNA 直投)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
									Text("支持小米电视/华为/索尼/海信/电视盒子/投影仪", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
								}
								TextButton(
									onClick = { dlnaManager.searchDevices() },
									enabled = !isDlnaSearching,
								) {
									Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
									Spacer(modifier = Modifier.width(4.dp))
									Text(if (isDlnaSearching) "扫描中" else "刷新")
								}
							}

							if (isDlnaSearching) {
								LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
							}

							if (dlnaDevices.isEmpty()) {
								Column(
									modifier = Modifier
										.fillMaxWidth()
										.padding(vertical = 12.dp),
									horizontalAlignment = Alignment.CenterHorizontally,
									verticalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Text(
										text = if (isDlnaSearching) "正在扫描局域网智能电视设备…" else "暂未发现局域网电视设备",
										style = MaterialTheme.typography.bodySmall,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
									)
									if (!isDlnaSearching) {
										Text(
											text = "请确认电视或电视盒子已开机且已连接至同一 Wi-Fi",
											style = MaterialTheme.typography.labelSmall,
											color = MaterialTheme.colorScheme.outline,
										)
									}
								}
							} else {
								Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
									dlnaDevices.forEach { device ->
										val isCurrent = device.id == activeDlnaDevice?.id
										Surface(
											shape = RoundedCornerShape(12.dp),
											color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
											border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
											modifier = Modifier
												.fillMaxWidth()
												.clickable {
													if (!url.isNullOrBlank()) {
														castScope.launch {
															dlnaManager.cast(device, url, entry.name)
														}
													} else {
														Toast.makeText(context, "无法获取视频播放直链", Toast.LENGTH_SHORT).show()
													}
												},
										) {
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.padding(horizontal = 14.dp, vertical = 10.dp),
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.SpaceBetween,
											) {
												Row(
													verticalAlignment = Alignment.CenterVertically,
													horizontalArrangement = Arrangement.spacedBy(10.dp),
												) {
													Icon(
														Icons.Rounded.Tv,
														contentDescription = null,
														tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
													)
													Column {
														Text(
															text = device.name,
															style = MaterialTheme.typography.bodyMedium,
															fontWeight = FontWeight.Medium,
														)
														Text(
															text = "${device.host} · DLNA 播放器",
															style = MaterialTheme.typography.labelSmall,
															color = MaterialTheme.colorScheme.onSurfaceVariant,
														)
													}
												}
												if (isCurrent) {
													Text("已连接", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
												} else {
													Text("点击投屏", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}

		// 视频备注修改对话框
		if (showMemoDialog) {
			androidx.compose.material3.AlertDialog(
				onDismissRequest = { showMemoDialog = false },
				title = { Text("视频故事与备注") },
				text = {
					Column {
						OutlinedTextField(
							value = memoInput,
							onValueChange = { memoInput = it },
							placeholder = { Text("记录这段视频背后的难忘故事…") },
							modifier = Modifier.fillMaxWidth(),
							maxLines = 5,
						)
					}
				},
				confirmButton = {
					Button(
						onClick = {
							showMemoDialog = false
							val cleanText = memoInput.trim()
							if (cleanText.isNotEmpty()) {
								PhotoMetadataStore.updateMemo(currentEntry.path, cleanText)
								onSaveMemo?.invoke(currentEntry, cleanText)
								Toast.makeText(context, "视频备注已保存", Toast.LENGTH_SHORT).show()
							} else {
								PhotoMetadataStore.updateMemo(currentEntry.path, null)
								onDeleteMemo?.invoke(currentEntry)
								Toast.makeText(context, "视频备注已清空", Toast.LENGTH_SHORT).show()
							}
						}
					) {
						Text("保存")
					}
				},
				dismissButton = {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						if (!currentEntry.memoText.isNullOrEmpty()) {
							TextButton(
								onClick = {
									showMemoDialog = false
									PhotoMetadataStore.updateMemo(currentEntry.path, null)
									onDeleteMemo?.invoke(currentEntry)
									Toast.makeText(context, "视频备注已清空", Toast.LENGTH_SHORT).show()
								}
							) {
								Text("清空删除", color = MaterialTheme.colorScheme.error)
							}
						}
						TextButton(onClick = { showMemoDialog = false }) {
							Text("取消")
						}
					}
				}
			)
		}

		// 视频录音中浮层 Sheet
		if (showRecordSheet) {
			ModalBottomSheet(
				onDismissRequest = { showRecordSheet = false },
				containerColor = MaterialTheme.colorScheme.surface,
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 24.dp)
						.padding(bottom = 36.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(16.dp),
				) {
					Text("为视频录制伴随旁白", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
					Text(
						"录音文件存放在视频同级目录下以'.'开头（默认隐藏），数据库仅记指针，零二进制",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)

					val seconds = audioRecorder.elapsedSeconds
					Text(
						text = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60),
						fontSize = 32.sp,
						fontWeight = FontWeight.Bold,
						fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
					)

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically,
					) {
						TextButton(
							onClick = {
								audioRecorder.cancel()
								showRecordSheet = false
							}
						) {
							Text("取消")
						}

						Button(
							onClick = {
								if (!audioRecorder.isRecording) {
									if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
										val ok = audioRecorder.start()
										if (!ok) Toast.makeText(context, "无法启动录音", Toast.LENGTH_SHORT).show()
									} else {
										micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
									}
								} else {
									val result = audioRecorder.stop()
									showRecordSheet = false
									if (result != null) {
										val (bytes, durMs) = result
										val companionPath = PhotoMetadataStore.computeCompanionAudioPath(currentEntry.path)
										PhotoMetadataStore.updateAudio(currentEntry.path, companionPath, durMs)
										val directAudio = audioUrlOf?.invoke(companionPath)
										if (directAudio != null) {
											audioPlayer.prepare(directAudio)
										}
										onSaveAudio?.invoke(currentEntry, bytes, durMs) { ok, err ->
											Toast.makeText(context, if (ok) "伴随旁白已保存 (${durMs/1000}秒)" else (err ?: "保存失败"), Toast.LENGTH_SHORT).show()
										}
									} else {
										Toast.makeText(context, "未录制有效声音", Toast.LENGTH_SHORT).show()
									}
								}
							},
							colors = androidx.compose.material3.ButtonDefaults.buttonColors(
								containerColor = if (audioRecorder.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
							)
						) {
							Text(if (audioRecorder.isRecording) "停止并保存" else "开始录制")
						}
					}
				}
			}
		}

		// 删除伴随录音确认对话框
		if (showDeleteAudioConfirm) {
			androidx.compose.material3.AlertDialog(
				onDismissRequest = { showDeleteAudioConfirm = false },
				title = { Text("确认删除伴随旁白？") },
				text = { Text("将删除视频绑定的语音解说文件，视频本体保留完好。此操作不可撤销。") },
				confirmButton = {
					Button(
						onClick = {
							showDeleteAudioConfirm = false
							audioPlayer.stop()
							PhotoMetadataStore.updateAudio(currentEntry.path, null, 0L)
							onDeleteAudio?.invoke(currentEntry) { ok, err ->
								Toast.makeText(context, if (ok) "伴随旁白已删除" else (err ?: "删除失败"), Toast.LENGTH_SHORT).show()
							}
						},
						colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
					) {
						Text("确认删除")
					}
				},
				dismissButton = {
					TextButton(onClick = { showDeleteAudioConfirm = false }) {
						Text("取消")
					}
				}
			)
		}
	}
}

private fun formatTime(millis: Long): String {
	val totalSecs = millis / 1000
	val mins = totalSecs / 60
	val secs = totalSecs % 60
	return String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}
