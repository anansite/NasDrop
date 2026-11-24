@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nasdrop.ui

import android.widget.Toast
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
import com.nasdrop.MediaFilterType
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Mic
import com.nasdrop.PhotoMetadataStore
import com.nasdrop.hasAudio
import com.nasdrop.hasMemo
import com.nasdrop.NasFolderIcon
import com.nasdrop.videoDurationMs
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import com.nasdrop.MainTab
import com.nasdrop.NasSystemInfo
import com.nasdrop.RouteInfo
import com.nasdrop.SortOrder
import com.nasdrop.TransferStatus
import com.nasdrop.TransferTask
import com.nasdrop.UploadService
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import com.nasdrop.ViewMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BrowserMode {
	Browse,
	Pick,
}

/**
 * 针对各品牌手机（尤其是华为鸿蒙、小米MIUI/HyperOS、OPPO、vivo等）优化的相册媒体选择契约。
 * 优先唤起系统原生图库相册（支持相册分类：相机、微信、屏幕截图、视频等），避免文档管理默认锁定空 Pictures 目录。
 */
class GalleryAlbumPickerContract : ActivityResultContract<String, List<Uri>>() {
	override fun createIntent(context: android.content.Context, input: String): android.content.Intent {
		val isVideo = input.startsWith("video")
		val mimeTypes = if (isVideo) arrayOf("video/*") else arrayOf("image/*", "video/*")
		val targetUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

		// 1. 优先调用 ACTION_PICK 呼起系统原生图库相册（华为图库直接展示所有相簿列表）
		val pickIntent = android.content.Intent(android.content.Intent.ACTION_PICK, targetUri).apply {
			setDataAndType(targetUri, if (isVideo) "video/*" else "image/*")
			putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
			putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
		}
		if (pickIntent.resolveActivity(context.packageManager) != null) {
			return pickIntent
		}

		// 2. 备用：ACTION_GET_CONTENT 开启多选与媒体过滤
		return android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
			addCategory(android.content.Intent.CATEGORY_OPENABLE)
			type = if (isVideo) "video/*" else "*/*"
			putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes)
			putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true)
		}
	}

	override fun parseResult(resultCode: Int, intent: android.content.Intent?): List<Uri> {
		if (resultCode != android.app.Activity.RESULT_OK || intent == null) {
			return emptyList()
		}
		val uris = LinkedHashSet<Uri>()
		intent.clipData?.let { clipData ->
			for (i in 0 until clipData.itemCount) {
				clipData.getItemAt(i).uri?.let { uris.add(it) }
			}
		}
		intent.data?.let { uris.add(it) }
		return uris.toList()
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
	vm: BrowserViewModel,
	mode: BrowserMode = BrowserMode.Browse,
	lastDir: String = "",
	onOpenImage: (DavEntry) -> Unit = {},
	onOpenVideo: (DavEntry) -> Unit = {},
	onOpenSettings: () -> Unit = {},
	onLogin: () -> Unit = {},
	onConfirm: (String) -> Unit = {},
	transferRequest: Int = 0,
) {
	val context = LocalContext.current
	val routeInfo by vm.routeInfo.collectAsState()

	// 一次性操作提示（移动/重命名/删除/新建的真实结果）。
	// 旧版把失败信息写进 vm.error，而 error 从未被渲染——用户看到的只是"点了没反应"。
	val pendingMessage = vm.uiMessage
	LaunchedEffect(pendingMessage) {
		if (pendingMessage != null) {
			Toast.makeText(context, pendingMessage, Toast.LENGTH_SHORT).show()
			vm.consumeMessage()
		}
	}

	// 模态弹窗状态
	var activeEntryForAction by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForRename by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForMove by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForDelete by remember { mutableStateOf<DavEntry?>(null) }
	// showCreateDialog replaced by activeCreateType
	var showTransferSheet by rememberSaveable { mutableStateOf(false) }
	var showNetSheet by remember { mutableStateOf(false) }

	var activeCreateType by remember { mutableStateOf<CreateDialogType?>(null) }
	var activeFolderForDelete by remember { mutableStateOf<Pair<DavEntry, Boolean>?>(null) }
	var showBatchDeleteConfirm by remember { mutableStateOf(false) }
	var showBatchMoveSheet by remember { mutableStateOf(false) }
	var activeEntryForIconPicker by remember { mutableStateOf<DavEntry?>(null) }
	var activeMemoryYearGroup by remember { mutableStateOf<com.nasdrop.MemoryYearGroup?>(null) }
	var showGameCenterSheet by remember { mutableStateOf(false) }
	var showRandomPhotoGame by remember { mutableStateOf(false) }
	LaunchedEffect(transferRequest) {
		if (transferRequest > 0) showTransferSheet = true
	}

	// 三个入口共用提交行为：保留读取授权，提交成功后立即显示进度与结果。
	fun startUpload(uris: List<android.net.Uri>, dir: String) {
		if (uris.isEmpty()) return
		runCatching {
			uris.forEach { uri ->
				runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
			}
			vm.startUploads(uris, dir) { showTransferSheet = true }
		}.onFailure {
			Toast.makeText(context, "无法开始上传：${it.message}", Toast.LENGTH_LONG).show()
		}
	}

	// 系统文件选择器（全部文件上传，支持顶部横向分类条自由切换）
	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetMultipleContents(),
	) { uris ->
		startUpload(uris, vm.path)
	}

	val photoPicker = rememberLauncherForActivityResult(
		contract = GalleryAlbumPickerContract(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedAlbumPath.isNotBlank() && vm.selectedAlbumPath != "__ALL_FOLDERS__") vm.selectedAlbumPath else "Photos/默认相册"
			startUpload(uris, targetDir)
		}
	}

	val videoPicker = rememberLauncherForActivityResult(
		contract = GalleryAlbumPickerContract(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedMediaPath.isNotBlank() && vm.selectedMediaPath != "__ALL_FOLDERS__") vm.selectedMediaPath else "movies/默认影集"
			startUpload(uris, targetDir)
		}
	}

	val folderIconImagePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent(),
	) { uri ->
		if (uri != null && activeEntryForIconPicker != null) {
			val target = activeEntryForIconPicker!!
			activeEntryForIconPicker = null
			Toast.makeText(context, "正在上传自定义图标…", Toast.LENGTH_SHORT).show()
			vm.uploadCustomFolderIcon(target, uri) { ok, msg ->
				Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
			}
		}
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		bottomBar = {},
		floatingActionButton = {},
	) { innerPadding ->
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(top = innerPadding.calculateTopPadding()),
		) {
			Column(
				modifier = Modifier.fillMaxSize(),
			) {
				// 各 Tab 专属顶栏：首页展示 NAS 概况与连接；文件/相册各司其职
				when (vm.currentTab) {
					MainTab.Home -> {
						HomeTopBar(
							vm = vm,
							activeTransferCount = vm.activeTransferCount,
							hasUpdate = vm.hasUpdate,
							onOpenUpdateDialog = { vm.openUpdateDialog() },
							onOpenTransferSheet = { showTransferSheet = true },
							routeInfo = routeInfo,
							onOpenSettings = onOpenSettings,
							onShowNetworkSheet = { showNetSheet = true },
						)
					}

					MainTab.Files -> {
						FilesTopBar(
							vm = vm,
							onUpload = { filePicker.launch("*/*") },
							onOpenTransferSheet = { showTransferSheet = true },
							onToggleViewMode = { vm.cycleViewMode() },
							onToggleMultiSelect = {
								if (vm.isMultiSelectMode) vm.exitMultiSelect()
								else vm.enterMultiSelect()
							},
							onCreateFolder = { activeCreateType = CreateDialogType.Folder },
							onNavigate = { vm.open(it) },
							onUp = { vm.up() },
							routeInfo = routeInfo,
							onOpenSettings = onOpenSettings,
							onShowNetworkSheet = { showNetSheet = true },
						)
					}

					MainTab.Photos -> {
						PhotosTopBar(
							count = vm.currentAlbumPhotos.size,
							selectedAlbumPath = vm.selectedAlbumPath,
							allAlbumsCount = vm.allAlbumFolders.size,
							activeTransferCount = vm.activeTransferCount,
							hasUpdate = vm.hasUpdate,
							onOpenUpdateDialog = { vm.openUpdateDialog() },
							onUploadPhoto = { photoPicker.launch("image/*") },
							onUpAlbum = { vm.upAlbum() },
							onCreateAlbum = { activeCreateType = CreateDialogType.Album },
							onDeleteCurrentAlbum = if (vm.selectedAlbumPath == "Photos/默认相册" || vm.selectedAlbumPath.endsWith("/默认相册")) null else {
								{
									val cur = vm.allAlbumFolders.find { it.path == vm.selectedAlbumPath }
									if (cur != null) activeFolderForDelete = Pair(cur, true)
								}
							},
							onOpenTransferSheet = { showTransferSheet = true },
							routeInfo = routeInfo,
							onOpenSettings = onOpenSettings,
							onShowNetworkSheet = { showNetSheet = true },
						)
					}

					MainTab.Media -> {
						MediaTopBar(
							count = vm.currentMediaVideos.size,
							selectedMediaPath = vm.selectedMediaPath,
							allMediaCount = vm.allMediaFolders.size,
							activeTransferCount = vm.activeTransferCount,
							hasUpdate = vm.hasUpdate,
							onOpenUpdateDialog = { vm.openUpdateDialog() },
							onUploadVideo = { videoPicker.launch("video/*") },
							onUpMedia = { vm.upMedia() },
							onCreateMediaFolder = { activeCreateType = CreateDialogType.MediaFolder },
							onDeleteCurrentMedia = if (vm.selectedMediaPath == "movies/默认影集" || vm.selectedMediaPath.endsWith("/默认影集") || vm.selectedMediaPath.endsWith("/默认影音")) null else {
								{
									val cur = vm.allMediaFolders.find { it.path == vm.selectedMediaPath }
									if (cur != null) activeFolderForDelete = Pair(cur, false)
								}
							},
							onOpenTransferSheet = { showTransferSheet = true },
							routeInfo = routeInfo,
							onOpenSettings = onOpenSettings,
							onShowNetworkSheet = { showNetSheet = true },
						)
					}

					MainTab.Settings -> {
						SettingsTopBar(
							vm = vm,
							onOpenTransferSheet = { showTransferSheet = true },
							routeInfo = routeInfo,
							onShowNetworkSheet = { showNetSheet = true },
						)
					}

					MainTab.Me -> {
						NetworkTopBar(
							routeInfo = routeInfo,
							versionName = vm.currentVersionName,
							hasUpdate = vm.hasUpdate,
							onOpenUpdateDialog = { vm.openUpdateDialog() },
							onOpenSettings = onOpenSettings,
						)
					}
				}

			LaunchedEffect(vm.currentTab) {
				if (vm.currentTab == MainTab.Photos || vm.currentTab == MainTab.Media) {
					vm.ensureMediaScanned()
				}
			}

			// 主内容区域根据 Tab 呈现
			Box(modifier = Modifier.weight(1f)) {
				val mediaTab = vm.currentTab == MainTab.Photos || vm.currentTab == MainTab.Media
				val contentError = if (mediaTab) vm.mediaError else vm.error
				val initialLoading = (if (mediaTab) vm.mediaScanning else vm.loading) && vm.currentTabEntries.isEmpty()
				when {
					vm.needsLogin -> BrowserStatus(
						title = "需要授权内网连接",
						detail = contentError ?: "请先完成 Tailscale 授权，再访问 NAS。",
						onPrimary = onLogin, primaryLabel = "前往登录", onSettings = onOpenSettings,
					)
					contentError != null && vm.currentTabEntries.isEmpty() -> {
						PullToRefreshBox(
							isRefreshing = vm.isRefreshing,
							onRefresh = { vm.pullRefresh() },
							modifier = Modifier.fillMaxSize(),
						) {
							BrowserStatus(
								title = if (mediaTab) "媒体加载失败" else "文件加载失败",
								detail = contentError,
								onPrimary = { if (mediaTab) vm.ensureMediaScanned(force = true) else vm.refresh() },
								primaryLabel = "重试", onSettings = onOpenSettings,
							)
						}
					}
					initialLoading -> {
						PullToRefreshBox(
							isRefreshing = vm.isRefreshing,
							onRefresh = { vm.pullRefresh() },
							modifier = Modifier.fillMaxSize(),
						) {
							BrowserStatus(
								title = if (mediaTab) "正在扫描媒体…" else "正在加载文件…", loading = true,
							)
						}
					}
					else -> when (vm.currentTab) {
						MainTab.Home -> {
							HomeScreenContent(
								vm = vm,
								memoryYearGroups = vm.memoryYearGroups,
								onSelectMemoryGroup = { activeMemoryYearGroup = it },
								onOpenImage = onOpenImage,
								onOpenVideo = onOpenVideo,
								urlOf = { vm.thumbnailUrlFor(it) },
								directUrlOf = { vm.directUrlFor(it) },
								onOpenSettings = onOpenSettings,
								onOpenGameCenter = { showGameCenterSheet = true },
							)
						}

						MainTab.Files -> {
							PullToRefreshBox(
								isRefreshing = vm.isRefreshing,
								onRefresh = { vm.pullRefresh() },
								modifier = Modifier.fillMaxSize(),
							) {
								FileBrowserContent(
									vm = vm,
									onOpenEntry = { entry ->
										if (vm.isMultiSelectMode) {
											vm.toggleSelect(entry)
										} else if (entry.isDir) {
											vm.cd(entry)
										} else if (entry.isImage) {
											onOpenImage(entry)
										} else if (entry.isVideo) {
											onOpenVideo(entry)
										} else {
											activeEntryForAction = entry
										}
									},
									onLongClickEntry = { entry ->
										if (!vm.isMultiSelectMode) {
											vm.enterMultiSelect(entry)
										}
									},
									onOpenAction = { entry -> activeEntryForAction = entry },
								)
							}
						}

						MainTab.Photos -> {
							PhotoTimelineContent(
								state = vm.photoGridState,
								groups = vm.photoTimelineGroups,
								allAlbums = vm.allAlbumFolders,
								allPhotos = vm.allPhotos,
								memoryYearGroups = vm.memoryYearGroups,
								selectedAlbumPath = vm.selectedAlbumPath,
								folderIcons = vm.folderIcons,
								mediaFilterType = vm.mediaFilterType,
								onClearFilter = { vm.mediaFilterType = MediaFilterType.All },
								isRefreshing = vm.isRefreshing,
								onRefresh = { vm.pullRefresh() },
								isMulti = vm.isMultiSelectMode,
								selectedPaths = vm.selectedPaths,
								onToggleSelect = { vm.toggleSelect(it) },
								onEnterMultiSelect = { vm.enterMultiSelect(it) },
								onUpdateSelectionPaths = { vm.updateSelectionByPaths(it) },
								onToggleSelectByPath = { path, select -> vm.toggleSelectByPath(path, select) },
								onToggleSelectGroup = { group, select -> vm.toggleSelectGroup(group, select) },
								onOpenAction = { activeEntryForAction = it },
								onSelectAlbum = { vm.openAlbum(it) },
								onRenameAlbum = { activeEntryForRename = it },
								onChangeIcon = { activeEntryForIconPicker = it },
								onDeleteAlbum = { folder -> activeFolderForDelete = Pair(folder, true) },
								onReorderAlbums = { from, to -> vm.reorderAlbums(from, to) },
								onCreateAlbum = { activeCreateType = CreateDialogType.Album },
								urlOf = { vm.thumbnailUrlFor(it) },
								directUrlOf = { vm.directUrlFor(it) },
								onThumbnailError = { vm.markThumbnailFailed(it) },
								onOpenImage = onOpenImage,
								onOpenVideo = onOpenVideo,
							)
						}

						MainTab.Media -> {
							MediaStreamContent(
								state = vm.mediaGridState,
								videos = vm.currentMediaVideos,
								groups = vm.mediaTimelineGroups,
								allMediaFolders = vm.allMediaFolders,
								allVideos = vm.allVideos,
								selectedMediaPath = vm.selectedMediaPath,
								folderIcons = vm.folderIcons,
								isRefreshing = vm.isRefreshing,
								onRefresh = { vm.pullRefresh() },
								isMulti = vm.isMultiSelectMode,
								selectedPaths = vm.selectedPaths,
								onToggleSelect = { vm.toggleSelect(it) },
								onEnterMultiSelect = { vm.enterMultiSelect(it) },
								onUpdateSelectionPaths = { vm.updateSelectionByPaths(it) },
								onToggleSelectGroup = { group, select -> vm.toggleSelectGroup(group, select) },
								onOpenAction = { activeEntryForAction = it },
								onSelectMediaFolder = { vm.openMedia(it) },
								onRenameMediaFolder = { activeEntryForRename = it },
								onChangeIcon = { activeEntryForIconPicker = it },
								onDeleteMediaFolder = { folder -> activeFolderForDelete = Pair(folder, false) },
								onReorderMediaFolders = { from, to -> vm.reorderMediaFolders(from, to) },
								onCreateMediaFolder = { activeCreateType = CreateDialogType.MediaFolder },
								urlOf = { vm.thumbnailUrlFor(it) },
								directUrlOf = { vm.directUrlFor(it) },
								onThumbnailError = { vm.markThumbnailFailed(it) },
								onOpenVideo = onOpenVideo,
							)
						}

						MainTab.Settings -> {
							SettingsScreen(
								vm = vm,
								onBack = { vm.currentTab = MainTab.Home },
								isInsideTab = true,
							)
						}

						MainTab.Me -> {
							NetworkIntelligenceConsole(
								routeInfo = routeInfo,
								isPunching = vm.isPunching,
								onTriggerPunch = {
									vm.punchHole { result ->
										Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
									}
								},
								onOpenSettings = onOpenSettings,
								logs = vm.logs(),
							)
						}
					}
				}

				// 可自由拖拽吸附的悬浮上传球（按住任意拖动，松手平滑贴边，绝不挡列表底部；首页不展示上传悬浮球）
				if (!vm.isMultiSelectMode && mode == BrowserMode.Browse && vm.currentTab != MainTab.Home) {
					DraggableUploadFab(
						currentTab = vm.currentTab,
						onUploadFile = { filePicker.launch("*/*") },
						onUploadPhoto = { photoPicker.launch("image/*") },
						onUploadVideo = { videoPicker.launch("video/*") },
					)
				}

				// 主界面全局悬浮传输灵动胶囊：在传输进行中常驻底部，实时展示任务数与速率，点击直达传输中心
				androidx.compose.animation.AnimatedVisibility(
					visible = vm.activeTransferCount > 0 && !showTransferSheet,
					enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
					exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = 84.dp),
				) {
					Surface(
						shape = RoundedCornerShape(24.dp),
						color = MaterialTheme.colorScheme.primaryContainer,
						shadowElevation = 8.dp,
						tonalElevation = 6.dp,
						border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
						onClick = { showTransferSheet = true },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(
								imageVector = Icons.Rounded.Sync,
								contentDescription = "传输中",
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp),
							)
							Text(
								text = "传输中 (${vm.activeTransferCount}) · ${if (vm.totalSpeedBps > 0) "${formatSize(vm.totalSpeedBps)}/s" else "正在处理…"}",
								fontSize = 13.sp,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onPrimaryContainer,
							)
							Icon(
								imageVector = Icons.Rounded.ChevronRight,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
								modifier = Modifier.size(16.dp),
							)
						}
					}
				}

				// 多选状态信息条：悬浮在内容上方，完全避免内容列表因插入组件而发生任何高度抖动或位移
				androidx.compose.animation.AnimatedVisibility(
					visible = vm.isMultiSelectMode,
					enter = fadeIn() + slideInVertically { -it },
					exit = fadeOut() + slideOutVertically { -it },
					modifier = Modifier
						.align(Alignment.TopCenter)
						.padding(top = 8.dp, start = 14.dp, end = 14.dp),
				) {
					MultiSelectHeaderStrip(
						selectedCount = vm.selectedEntries.size,
						onSelectAll = { vm.selectAll(vm.currentTabEntries) },
						onComplete = { vm.exitMultiSelect() },
					)
				}
			}
		}

		// 底部悬浮毛玻璃 Dock 栏 / 批量操作栏 / 选择确认栏
		// 真正绝对悬浮于屏幕最下方，背后的照片、文件和影音列表在滑动时可透过半透明毛玻璃优雅穿透显示
		Box(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth(),
		) {
			if (mode == BrowserMode.Pick) {
				PickBar(
					path = vm.path,
					lastDir = lastDir,
					onCreateFolder = { activeCreateType = CreateDialogType.Folder },
					onConfirm = {
						vm.rememberDir(vm.path)
						onConfirm(vm.path)
					},
				)
			} else if (vm.isMultiSelectMode) {
				BatchActionTray(
					selectedCount = vm.selectedEntries.size,
					onDownload = {
						vm.batchDownload(context) {
							Toast.makeText(context, "已将选中文件加入下载队列", Toast.LENGTH_SHORT).show()
							showTransferSheet = true
						}
					},
					onMove = {
						showBatchMoveSheet = true
					},
					onDelete = {
						showBatchDeleteConfirm = true
					},
					onCancel = { vm.exitMultiSelect() },
				)
			} else {
				CommercialNavigationBar(
					currentTab = vm.currentTab,
					onTabSelect = { tab ->
						vm.currentTab = tab
						if (tab == MainTab.Home || tab == MainTab.Photos || tab == MainTab.Media) {
							vm.ensureMediaScanned()
						}
					},
				)
			}
		}
	}
}

	// ==================== 抽屉与对话框组件 ====================
	// 确认窗口只在发现同名冲突时预览有限文件名，无冲突时后台平滑起传，彻底消除弹窗闪烁
	val overwriteNames = vm.pendingUploadOverwriteNames
	if (overwriteNames.isNotEmpty()) {
		AlertDialog(
			onDismissRequest = { vm.dismissUploadOverwrite() },
			title = { Text("发现 ${overwriteNames.size} 个同名文件") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState())) {
					Text("继续上传将覆盖 NAS 中的同名文件：")
					overwriteNames.take(5).forEach { name -> Text("• $name", maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
					if (overwriteNames.size > 5) Text("还有 ${overwriteNames.size - 5} 个文件", modifier = Modifier.padding(top = 8.dp))
				}
			},
			confirmButton = { Button(onClick = { vm.confirmUploadOverwrite() }) { Text("覆盖上传") } },
			dismissButton = { TextButton(onClick = { vm.dismissUploadOverwrite() }) { Text("取消") } },
		)
	}

	if (activeMemoryYearGroup != null) {
		MemoryDetailModalSheet(
			yearGroup = activeMemoryYearGroup!!,
			onDismiss = { activeMemoryYearGroup = null },
			onSelectAlbum = {
				activeMemoryYearGroup = null
				vm.currentTab = MainTab.Photos
				vm.openAlbum(it)
			},
			onOpenImage = onOpenImage,
			onOpenVideo = onOpenVideo,
			urlOf = { vm.thumbnailUrlFor(it) },
			directUrlOf = { vm.directUrlFor(it) },
		)
	}

	if (showGameCenterSheet) {
		GameCenterSheet(
			onDismiss = { showGameCenterSheet = false },
			onSelectRandomPhotoGame = {
				showGameCenterSheet = false
				showRandomPhotoGame = true
			},
		)
	}

	if (showRandomPhotoGame) {
		RandomPhotoGameScreen(
			vm = vm,
			onBack = { showRandomPhotoGame = false },
			onLocate = { entry, album ->
				showRandomPhotoGame = false
				vm.currentTab = MainTab.Photos
				vm.selectedAlbumPath = album?.path ?: ""
				onOpenImage(entry)
				Toast.makeText(context, "已在「${album?.name ?: "全部照片"}」中定位该照片", Toast.LENGTH_SHORT).show()
			},
			urlOf = { vm.thumbnailUrlFor(it) },
			directUrlOf = { vm.directUrlFor(it) },
		)
	}

	// 1. 统一底部文件操作抽屉
	activeEntryForAction?.let { entry ->
		ActionBottomSheet(
			entry = entry,
			urlOf = { vm.thumbnailUrlFor(it) },
			customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null,
			onDismiss = { activeEntryForAction = null },
			onDownload = {
				activeEntryForAction = null
				vm.enqueueDownload(context, entry)
				Toast.makeText(context, "已加入下载队列: ${entry.name}", Toast.LENGTH_SHORT).show()
				showTransferSheet = true
			},
			onMove = {
				activeEntryForAction = null
				activeEntryForMove = entry
			},
			onRename = {
				activeEntryForAction = null
				activeEntryForRename = entry
			},
			onDelete = {
				activeEntryForAction = null
				activeEntryForDelete = entry
			},
			onChangeIcon = if (entry.isDir) {
				{
					val target = entry
					activeEntryForAction = null
					activeEntryForIconPicker = target
				}
			} else null,
		)
	}

	// 文件夹图标选择器
	activeEntryForIconPicker?.let { entry ->
		FolderIconPickerSheet(
			folderName = entry.displayName,
			currentIconKey = vm.folderIcons[entry.path.trim('/')],
			onSelect = { icon ->
				vm.updateFolderIcon(entry, icon)
				activeEntryForIconPicker = null
			},
			onUploadCustomIcon = {
				folderIconImagePicker.launch("image/*")
			},
			onDismiss = { activeEntryForIconPicker = null },
		)
	}

	// 2. 传输中心管理抽屉
	if (showTransferSheet) {
		TransferCenterSheet(
			tasks = vm.transferTasks,
			totalSpeedBps = vm.totalSpeedBps,
			routeInfo = routeInfo,
			onShowNetworkSheet = { showNetSheet = true },
			onDismiss = { showTransferSheet = false },
			onPauseTask = { vm.pauseTask(it) },
			onResumeTask = { vm.resumeTask(it) },
			onCancelTask = { vm.cancelTask(it) },
			onPauseAll = { vm.pauseAll() },
			onResumeAll = { vm.resumeAll() },
			onRetryTask = { vm.retryTask(it) },
			onClearFinished = { vm.clearFinishedTransfers() },
			onOpenResult = { taskId ->
				vm.openTransferResult(context, taskId)
				if (vm.transferTasks.any { it.id == taskId && it.isUpload }) showTransferSheet = false
			},
		)
	}

	// 3. 智能网络管家抽屉
	if (showNetSheet) {
		NetworkManagerSheet(
			routeInfo = routeInfo,
			currentMode = vm.connectionMode,
			onDismiss = { showNetSheet = false },
			onTriggerPunch = {
				vm.punchHole { msg ->
					Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
				}
			},
			onSwitchMode = { mode ->
				vm.setConnectionMode(mode) { msg ->
					Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
				}
				showNetSheet = false
			},
			onOpenSettings = onOpenSettings,
		)
	}

	// 4. 重命名对话框（带文件后缀名保护）
	activeEntryForRename?.let { entry ->
		val originalName = entry.name
		val isFile = !entry.isDir
		val dotIndex = originalName.lastIndexOf('.')
		val hasExt = isFile && dotIndex > 0
		val baseName = if (hasExt) originalName.substring(0, dotIndex) else originalName
		val extension = if (hasExt) originalName.substring(dotIndex) else ""

		var newBaseName by remember(entry) { mutableStateOf(baseName) }

		AlertDialog(
			onDismissRequest = { activeEntryForRename = null },
			title = { Text(if (entry.isDir) "重命名文件夹" else "重命名文件", fontWeight = FontWeight.Bold) },
			text = {
				Column {
					OutlinedTextField(
						value = newBaseName,
						onValueChange = { newBaseName = it },
						singleLine = true,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(if (hasExt) "文件名称" else "名称") },
						trailingIcon = if (hasExt) {
							{
								Surface(
									color = MaterialTheme.colorScheme.surfaceVariant,
									shape = RoundedCornerShape(6.dp),
									modifier = Modifier.padding(end = 8.dp),
								) {
									Text(
										text = extension,
										style = MaterialTheme.typography.labelMedium,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										fontWeight = FontWeight.Bold,
										modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
									)
								}
							}
						} else null,
					)

				}
			},
			confirmButton = {
				Button(
					onClick = {
						val target = (newBaseName.trim() + extension).trim()
						if (target.isNotBlank() && target != entry.name) {
							vm.rename(entry, target) { ok, msg ->
								Toast.makeText(
									context,
									if (ok) "已重命名为: $target" else "重命名失败: $msg",
									Toast.LENGTH_SHORT,
								).show()
							}
						}
						activeEntryForRename = null
					},
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
				) { Text("确定") }
			},
			dismissButton = {
				TextButton(onClick = { activeEntryForRename = null }) { Text("取消") }
			},
		)
	}

	// 5. 移动文件选择目录对话框
	activeEntryForMove?.let { entry ->
		MoveFolderSheet(
			folderPaths = vm.allFolderPaths,
			currentPath = vm.path,
			onSelectTarget = { destDir ->
				activeEntryForMove = null
				// 结果异步回传：只有真的 MOVE 成功才说"已移动"
				vm.move(entry, destDir) { ok, msg ->
					Toast.makeText(
						context,
						if (ok) "已移动到: /${destDir.ifEmpty { "根目录" }}" else "移动失败: $msg",
						Toast.LENGTH_SHORT,
					).show()
				}
			},
			onDismiss = { activeEntryForMove = null },
		)
	}

	// 批量彻底删除确认对话框
	if (showBatchDeleteConfirm) {
		AlertDialog(
			onDismissRequest = { showBatchDeleteConfirm = false },
			title = { Text("批量彻底删除", fontWeight = FontWeight.Bold) },
			text = { Text("确定从网盘中删除选中的 ${vm.selectedEntries.size} 项内容？此操作不可撤销。") },
			confirmButton = {
				Button(
					onClick = {
						vm.batchDelete { okCount, failCount ->
							if (failCount == 0) {
								Toast.makeText(context, "已成功删除 $okCount 项内容", Toast.LENGTH_SHORT).show()
							} else {
								Toast.makeText(context, "成功删除 $okCount 项，失败 $failCount 项", Toast.LENGTH_SHORT).show()
							}
						}
						showBatchDeleteConfirm = false
					},
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
				) { Text("确认彻底删除") }
			},
			dismissButton = {
				TextButton(onClick = { showBatchDeleteConfirm = false }) { Text("取消") }
			},
		)
	}

	// 批量移动对话框
	if (showBatchMoveSheet) {
		MoveFolderSheet(
			folderPaths = vm.allFolderPaths,
			currentPath = vm.path,
			onSelectTarget = { destDir ->
				showBatchMoveSheet = false
				vm.batchMove(destDir) { okCount, failCount ->
					Toast.makeText(
						context,
						if (failCount == 0) {
							"已移动 $okCount 项到: /${destDir.ifEmpty { "根目录" }}"
						} else {
							"移动完成: 成功 $okCount 项, 失败 $failCount 项"
						},
						Toast.LENGTH_SHORT,
					).show()
				}
			},
			onDismiss = { showBatchMoveSheet = false },
		)
	}

	// 删除子相册/子影集确认对话框
	activeFolderForDelete?.let { (folder, isAlbum) ->
		AlertDialog(
			onDismissRequest = { activeFolderForDelete = null },
			title = { Text(if (isAlbum) "删除相册" else "删除影集", fontWeight = FontWeight.Bold) },
			text = { Text("确认删除「${folder.name}」吗？相册内的文件也将被移除。") },
			confirmButton = {
				Button(
					onClick = {
						val target = folder
						activeFolderForDelete = null
						if (target.name == "默认相册" || target.name == "默认影集" || target.name == "默认影音") {
							Toast.makeText(context, "默认系统目录不允许删除", Toast.LENGTH_SHORT).show()
							return@Button
						}
						runCatching {
							if (isAlbum) vm.deleteAlbum(target) else vm.deleteMediaFolder(target)
							Toast.makeText(context, "已删除: ${target.name}", Toast.LENGTH_SHORT).show()
						}.onFailure { err ->
							Toast.makeText(context, "删除异常: ${err.message}", Toast.LENGTH_SHORT).show()
						}
					},
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
				) { Text("确认删除") }
			},
			dismissButton = {
				TextButton(onClick = { activeFolderForDelete = null }) { Text("取消") }
			},
		)
	}

	// 6. 新建文件夹/专属相册对话框
	activeCreateType?.let { type ->
		CreateFolderDialog(
			type = type,
			onConfirm = { name ->
				activeCreateType = null
				when (type) {
					CreateDialogType.Folder -> vm.mkdir(name)
					CreateDialogType.Album -> {
						vm.createAlbum(name)
						Toast.makeText(context, "已创建相册: $name", Toast.LENGTH_SHORT).show()
					}
					CreateDialogType.MediaFolder -> {
						vm.createMediaFolder(name)
						Toast.makeText(context, "已创建影集分类: $name", Toast.LENGTH_SHORT).show()
					}
				}
			},
			onDismiss = { activeCreateType = null },
		)
	}

	// 7. 删除确认对话框
	activeEntryForDelete?.let { entry ->
		AlertDialog(
			onDismissRequest = { activeEntryForDelete = null },
			title = { Text("删除确认", fontWeight = FontWeight.Bold) },
			text = { Text("确定从网盘删除「${entry.name}」？此操作不可撤销。") },
			confirmButton = {
				TextButton(onClick = {
					val targetName = entry.name
					activeEntryForDelete = null
					vm.delete(entry) { success, err ->
						if (success) {
							Toast.makeText(context, "已删除「$targetName」", Toast.LENGTH_SHORT).show()
						} else {
							Toast.makeText(context, "删除失败: ${err ?: "网络错误"}", Toast.LENGTH_LONG).show()
						}
					}
				}) { Text("彻底删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
			},
			dismissButton = { TextButton(onClick = { activeEntryForDelete = null }) { Text("取消") } },
		)
	}
}


private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun CompactNetworkBadge(routeInfo: RouteInfo, onClick: () -> Unit) {
	val (dotColor, label) = when {
		routeInfo.isProbing -> {
			Color(0xFF3B82F6) to "连接中"
		}
		routeInfo.isLan -> {
			val speed = if (routeInfo.latencyMs > 0) "局域网 · ${routeInfo.latencyMs}ms" else "局域网"
			Color(0xFF10B981) to speed
		}
		routeInfo.isDirect -> {
			val speed = if (routeInfo.latencyMs > 0) "直连 · ${routeInfo.latencyMs}ms" else "直连"
			Color(0xFF10B981) to speed
		}
		routeInfo.isRelay -> {
			val speed = if (routeInfo.latencyMs > 0) "中继 · ${routeInfo.latencyMs}ms" else "中继"
			Color(0xFFF59E0B) to speed
		}
		routeInfo.label.contains("等待登录") -> {
			Color(0xFFF59E0B) to "待登录"
		}
		routeInfo.label.contains("不可达") || routeInfo.label.contains("异常") || routeInfo.label.contains("失败") -> {
			Color(0xFFEF4444) to "未连通"
		}
		else -> {
			Color(0xFF3B82F6) to if (routeInfo.label.isNotBlank() && !routeInfo.label.contains("正在")) "连通中" else "连接中"
		}
	}

	Surface(
		shape = RoundedCornerShape(10.dp),
		color = dotColor.copy(alpha = 0.09f),
		modifier = Modifier
			.clip(RoundedCornerShape(10.dp))
			.clickable(onClick = onClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 5.5.dp, vertical = 2.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(3.5.dp),
		) {
			Box(contentAlignment = Alignment.Center) {
				Box(
					modifier = Modifier
						.size(8.dp)
						.clip(CircleShape)
						.background(dotColor.copy(alpha = 0.25f)),
				)
				Box(
					modifier = Modifier
						.size(5.dp)
						.clip(CircleShape)
						.background(dotColor),
				)
			}
			Text(
				text = label,
				fontSize = 10.sp,
				fontWeight = FontWeight.Bold,
				color = dotColor,
				lineHeight = 12.sp,
			)
		}
	}
}

@Composable
private fun HumanNetworkPill(routeInfo: RouteInfo, onClick: () -> Unit) {
	val (bgColor, textColor, dotColor, label) = when {
		routeInfo.isProbing -> {
			Quadruple(
				Color(0xFFE3F2FD),
				Color(0xFF1565C0),
				Color(0xFF1E88E5),
				"连接中",
			)
		}
		routeInfo.isLan -> {
			Quadruple(
				Color(0xFFE8F5E9),
				Color(0xFF2E7D32),
				Color(0xFF43A047),
				"局域网",
			)
		}
		routeInfo.isDirect -> {
			val text = if (routeInfo.latencyMs > 0) "直连 ${routeInfo.latencyMs}ms" else "直连"
			Quadruple(
				Color(0xFFE0F2F1),
				Color(0xFF00695C),
				Color(0xFF00897B),
				text,
			)
		}
		routeInfo.isRelay -> {
			Quadruple(
				Color(0xFFFFF3E0),
				Color(0xFFE65100),
				Color(0xFFFB8C00),
				"中继",
			)
		}
		routeInfo.label.contains("等待登录") -> {
			Quadruple(
				Color(0xFFFFF8E1),
				Color(0xFFF57F17),
				Color(0xFFFBC02D),
				"待登录",
			)
		}
		routeInfo.label.contains("不可达") || routeInfo.label.contains("异常") || routeInfo.label.contains("失败") -> {
			Quadruple(
				Color(0xFFFFEBEE),
				Color(0xFFC62828),
				Color(0xFFE53935),
				routeInfo.label,
			)
		}
		else -> {
			Quadruple(
				Color(0xFFE3F2FD),
				Color(0xFF1565C0),
				Color(0xFF1E88E5),
				routeInfo.label.ifBlank { "连接中" },
			)
		}
	}

	Box(
		modifier = Modifier
			.clip(RoundedCornerShape(6.dp))
			.background(bgColor)
			.clickable(onClick = onClick)
			.padding(horizontal = 6.dp, vertical = 2.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Box(
				modifier = Modifier
					.size(4.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Text(
				text = label,
				fontSize = 10.sp,
				fontWeight = FontWeight.Medium,
				color = textColor,
			)
		}
	}
}

@Composable
private fun PickBar(
	path: String,
	lastDir: String,
	onCreateFolder: () -> Unit,
	onConfirm: () -> Unit,
) {
	Surface(
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		color = MaterialTheme.colorScheme.surface,
		shadowElevation = 12.dp,
	) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						"存入位置: /${path.ifEmpty { "根目录" }}",
						fontSize = 14.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						"可点击下方文件夹进入子目录，选定后点击确认存入",
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				OutlinedButton(
					onClick = onCreateFolder,
					shape = RoundedCornerShape(12.dp),
					contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
				) {
					Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(modifier = Modifier.width(4.dp))
					Text("新建", fontSize = 12.sp, fontWeight = FontWeight.Bold)
				}
			}

			Spacer(Modifier.height(12.dp))

			Button(
				onClick = onConfirm,
				colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth().height(46.dp),
			) {
				Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
				Spacer(modifier = Modifier.width(6.dp))
				Text("存入当前目录", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp)
			}
		}
	}
}

// ==================== 极简服务配置入口与网络状态圆点 ====================

@Composable
private fun SettingsWithStatusButton(
	routeInfo: RouteInfo,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// 连接状态只做极轻量的底色提示，不再用右上角圆点——那会被误认成"有新消息"。
	// 用户无需关心直连/中继，这里只区分"在线 / 离线"两种状态。
	val indicatorColor = if (routeInfo.isDirect || routeInfo.isRelay || routeInfo.isLan) {
		MaterialTheme.colorScheme.tertiary
	} else {
		Color(0xFF1E88E5) // 连接中（柔和蓝，不用灰色未连接）
	}

	IconButton(
		onClick = onClick,
		modifier = modifier.size(48.dp),
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = Icons.Rounded.Settings,
				contentDescription = "服务与配置",
				tint = MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.size(20.dp),
			)
			// 齿轮正下方一条极细状态线，弱化存在感，绝不像通知角标
			Box(
				modifier = Modifier
					.width(10.dp)
					.height(2.dp)
					.clip(CircleShape)
					.background(indicatorColor)
					.align(Alignment.BottomCenter),
			)
		}
	}
}

// ==================== 顶部导航栏与状态胶囊 ====================

/** 相册与影音共用稳定顶栏；与文件顶栏严格保持 54dp 高度与排版风格对齐。 */
@Composable
private fun CollectionTopBar(
	title: String,
	subtitle: String,
	isInside: Boolean,
	uploadLabel: String = "上传文件",
	activeTransferCount: Int,
	hasUpdate: Boolean = false,
	onOpenUpdateDialog: (() -> Unit)? = null,
	onUp: () -> Unit,
	onUpload: () -> Unit,
	onCreate: () -> Unit,
	onDelete: (() -> Unit)?,
	onTransfer: () -> Unit,
	onSettings: () -> Unit,
	routeInfo: RouteInfo? = null,
	onShowNetworkSheet: () -> Unit = {},
) {
	Surface(
		color = MaterialTheme.colorScheme.surface,
		tonalElevation = 1.dp,
	) {
		Row(
			Modifier
				.fillMaxWidth()
				.heightIn(min = 54.dp)
				.padding(horizontal = 14.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (isInside) {
				IconButton(onClick = onUp, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
				}
			}
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = if (isInside) 2.dp else 2.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.titleMedium,
						fontSize = 16.sp,
						fontWeight = FontWeight.ExtraBold,
						letterSpacing = (-0.3).sp,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					if (routeInfo != null) {
						CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
					}
				}
				if (subtitle.isNotBlank()) {
					Spacer(Modifier.height(1.dp))
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						fontSize = 11.sp,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				TransferButton(activeTransferCount, onTransfer)
				if (hasUpdate && onOpenUpdateDialog != null) {
					UpdateBadgeButton(onClick = onOpenUpdateDialog)
				}
			}
		}
	}
}

/** 传输入口保留独立触控区，使用双向流转箭头图标，数量提示不参与标题宽度分配。 */
@Composable
private fun TransferButton(count: Int, onClick: () -> Unit) {
	IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				imageVector = Icons.Rounded.SwapVert,
				contentDescription = if (count > 0) "传输中心，$count 个任务进行中" else "传输中心",
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			if (count > 0) {
				Box(
					modifier = Modifier
						.size(8.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.primary)
						.align(Alignment.TopEnd)
				)
			}
		}
	}
}

/** 专属独立升级提醒入口，区别于传输与上传，胶囊红点视觉辨识度极高。 */
@Composable
private fun UpdateBadgeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
	Surface(
		shape = RoundedCornerShape(14.dp),
		color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
		border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
		modifier = modifier
			.height(30.dp)
			.clip(RoundedCornerShape(14.dp))
			.clickable { onClick() },
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(horizontal = 8.dp),
		) {
			Icon(
				imageVector = Icons.Rounded.Upgrade,
				contentDescription = "发现新版本",
				tint = MaterialTheme.colorScheme.primary,
				modifier = Modifier.size(16.dp),
			)
			Spacer(Modifier.width(4.dp))
			Text(
				text = "升级",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
			)
			Spacer(Modifier.width(4.dp))
			Box(
				modifier = Modifier
					.size(6.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.error),
			)
		}
	}
}

@Composable
private fun PhotosTopBar(
	count: Int,
	selectedAlbumPath: String,
	allAlbumsCount: Int = 0,
	activeTransferCount: Int = 0,
	hasUpdate: Boolean = false,
	onOpenUpdateDialog: (() -> Unit)? = null,
	onUploadPhoto: () -> Unit,
	onUpAlbum: () -> Unit,
	onCreateAlbum: () -> Unit,
	onDeleteCurrentAlbum: (() -> Unit)? = null,
	onOpenTransferSheet: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit = {},
) {
	val (title, subtitle, isInside) = when (selectedAlbumPath) {
		"__ALL_FOLDERS__" -> Triple("全部相册", if (allAlbumsCount > 0) "$allAlbumsCount 个相册" else "相册分类总览", true)
		"" -> Triple("云端相册", "$count 张照片", false)
		else -> {
			val name = selectedAlbumPath.substringAfterLast('/')
			Triple(name.ifBlank { "相册" }, "相册 · $count 张照片", true)
		}
	}
	CollectionTopBar(
		title = title,
		subtitle = subtitle,
		isInside = isInside,
		uploadLabel = "上传照片",
		activeTransferCount = activeTransferCount,
		hasUpdate = hasUpdate,
		onOpenUpdateDialog = onOpenUpdateDialog,
		onUp = onUpAlbum, onUpload = onUploadPhoto, onCreate = onCreateAlbum,
		onDelete = onDeleteCurrentAlbum, onTransfer = onOpenTransferSheet, onSettings = onOpenSettings,
		routeInfo = routeInfo, onShowNetworkSheet = onShowNetworkSheet,
	)
}

@Composable
private fun MediaTopBar(
	count: Int,
	selectedMediaPath: String,
	allMediaCount: Int = 0,
	activeTransferCount: Int = 0,
	hasUpdate: Boolean = false,
	onOpenUpdateDialog: (() -> Unit)? = null,
	onUploadVideo: () -> Unit,
	onUpMedia: () -> Unit,
	onCreateMediaFolder: () -> Unit,
	onDeleteCurrentMedia: (() -> Unit)? = null,
	onOpenTransferSheet: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit = {},
) {
	val (title, subtitle, isInside) = when (selectedMediaPath) {
		"__ALL_FOLDERS__" -> Triple("全部影集", if (allMediaCount > 0) "$allMediaCount 个影集" else "影集分类总览", true)
		"" -> Triple("影音视界", "$count 个视频", false)
		else -> {
			val name = selectedMediaPath.substringAfterLast('/')
			Triple(name.ifBlank { "影集" }, "影集 · $count 个视频", true)
		}
	}
	CollectionTopBar(
		title = title,
		subtitle = subtitle,
		isInside = isInside,
		uploadLabel = "上传视频",
		activeTransferCount = activeTransferCount,
		hasUpdate = hasUpdate,
		onOpenUpdateDialog = onOpenUpdateDialog,
		onUp = onUpMedia, onUpload = onUploadVideo, onCreate = onCreateMediaFolder,
		onDelete = onDeleteCurrentMedia, onTransfer = onOpenTransferSheet, onSettings = onOpenSettings,
		routeInfo = routeInfo, onShowNetworkSheet = onShowNetworkSheet,
	)
}

@Composable
private fun NetworkTopBar(
	routeInfo: RouteInfo,
	versionName: String = "",
	hasUpdate: Boolean,
	onOpenUpdateDialog: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			Text("服务与设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
			if (versionName.isNotBlank()) {
				Surface(
					shape = RoundedCornerShape(10.dp),
					color = MaterialTheme.colorScheme.primaryContainer,
				) {
					Text(
						text = "v$versionName",
						modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
						fontSize = 11.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onPrimaryContainer,
					)
				}
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			HumanNetworkPill(routeInfo = routeInfo, onClick = {})

			if (hasUpdate) {
				UpdateBadgeButton(onClick = onOpenUpdateDialog)
			}

			IconButton(onClick = onOpenSettings) {
				Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface)
			}
		}
	}
}

@Composable
private fun SettingsTopBar(
	vm: BrowserViewModel,
	onOpenTransferSheet: () -> Unit = {},
	routeInfo: RouteInfo? = null,
	onShowNetworkSheet: () -> Unit = {},
) {
	Surface(
		color = MaterialTheme.colorScheme.surface,
		tonalElevation = 1.dp,
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 54.dp)
				.padding(horizontal = 14.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = 2.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = "系统与配置",
						style = MaterialTheme.typography.titleMedium,
						fontSize = 16.sp,
						fontWeight = FontWeight.ExtraBold,
						letterSpacing = (-0.3).sp,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					if (routeInfo != null) {
						CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
					}
				}
				Spacer(Modifier.height(1.dp))
				Text(
					text = "NAS 服务状态与偏好设置",
					style = MaterialTheme.typography.bodySmall,
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				TransferButton(
					count = vm.activeTransferCount,
					onClick = onOpenTransferSheet,
				)

				if (vm.hasUpdate) {
					UpdateBadgeButton(onClick = { vm.openUpdateDialog() })
				}
			}
		}
	}
}

@Composable
private fun HomeTopBar(
	vm: BrowserViewModel,
	activeTransferCount: Int,
	hasUpdate: Boolean,
	onOpenUpdateDialog: () -> Unit,
	onOpenTransferSheet: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit,
) {
	Surface(
		color = MaterialTheme.colorScheme.surface,
		tonalElevation = 1.dp,
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.heightIn(min = 54.dp)
				.padding(horizontal = 14.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = 2.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						text = "NasDrop",
						style = MaterialTheme.typography.titleMedium,
						fontSize = 16.sp,
						fontWeight = FontWeight.ExtraBold,
						letterSpacing = (-0.3).sp,
						color = MaterialTheme.colorScheme.onSurface,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
				}
				Spacer(Modifier.height(1.dp))
				Text(
					text = "智能家庭私有云存储",
					style = MaterialTheme.typography.bodySmall,
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				TransferButton(
					count = activeTransferCount,
					onClick = onOpenTransferSheet,
				)

				if (hasUpdate) {
					UpdateBadgeButton(onClick = onOpenUpdateDialog)
				}
			}
		}
	}
}

/** 文件顶栏把搜索、标题与固定操作区分开，长路径只影响标题省略。 */
@Composable
private fun FilesTopBar(
	vm: BrowserViewModel,
	onUpload: () -> Unit,
	onOpenTransferSheet: () -> Unit,
	onToggleViewMode: () -> Unit,
	onToggleMultiSelect: () -> Unit,
	onCreateFolder: () -> Unit,
	onNavigate: (String) -> Unit,
	onUp: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit = {},
) {
	var isSearchOpen by remember { mutableStateOf(false) }
	var menuOpen by remember { mutableStateOf(false) }
	val parts = remember(vm.path) { vm.path.trim('/').split('/').filter { it.isNotBlank() } }
	Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
		Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
			if (parts.isNotEmpty()) {
				IconButton(onClick = onUp, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回上一级")
				}
			}
			Column(
				modifier = Modifier.weight(1f).padding(start = if (parts.isEmpty()) 10.dp else 2.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Text(
						if (parts.isEmpty()) "全部文件" else parts.last(),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.ExtraBold,
						letterSpacing = (-0.3).sp,
						maxLines = 1, overflow = TextOverflow.Ellipsis,
					)
					CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
				}
				val count = vm.filteredEntries.size
				Text(
					if (parts.isEmpty()) {
						when (vm.selectedCategory) {
							FileCategory.Image -> "全部照片 · 共 $count 项"
							FileCategory.Video -> "全部视频 · 共 $count 项"
							FileCategory.Document -> "全部文档 · 共 $count 项"
							FileCategory.Audio -> "全部音频 · 共 $count 项"
							FileCategory.Archive -> "全部压缩包 · 共 $count 项"
							else -> "私有云极速直连 · 共 $count 项内容"
						}
					} else {
						"${vm.filteredEntries.size} 项文件与目录"
					},
					style = MaterialTheme.typography.bodySmall,
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1, overflow = TextOverflow.Ellipsis,
				)
			}
			IconButton(onClick = { isSearchOpen = !isSearchOpen }, modifier = Modifier.size(40.dp)) {
				Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
			}
			TransferButton(vm.activeTransferCount, onOpenTransferSheet)
			if (vm.hasUpdate) {
				UpdateBadgeButton(onClick = { vm.openUpdateDialog() })
			}
		}

		// 按需展开搜索栏：平时零占位，点击右上角搜索图标时优雅展开
		AnimatedVisibility(
			visible = isSearchOpen,
			enter = expandVertically() + fadeIn(),
			exit = shrinkVertically() + fadeOut(),
		) {
			Surface(
				shape = RoundedCornerShape(20.dp),
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
				border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 3.dp),
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
				) {
					Icon(
						Icons.Rounded.Search,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(16.dp),
					)
					Spacer(Modifier.width(8.dp))
					BasicTextField(
						value = vm.searchQuery,
						onValueChange = { vm.searchQuery = it },
						singleLine = true,
						textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
						modifier = Modifier.weight(1f),
						decorationBox = { innerTextField ->
							if (vm.searchQuery.isEmpty()) {
								Text(
									"搜索文件名...",
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
								)
							}
							innerTextField()
						}
					)
					IconButton(
						onClick = {
							if (vm.searchQuery.isNotEmpty()) {
								vm.searchQuery = ""
							} else {
								isSearchOpen = false
							}
						},
						modifier = Modifier.size(20.dp)
					) {
						Icon(Icons.Rounded.Close, contentDescription = "关闭搜索", modifier = Modifier.size(14.dp))
					}
				}
			}
		}

		// 分类胶囊行（全部、图片相册、4K视频、文档资料、音频、压缩包）
		CategoryFilterRow(
			selected = vm.selectedCategory,
			onSelect = { vm.selectCategory(it) },
		)

		// 面包屑导航：仅在进入子目录时紧凑呈现，根目录无需多余占位整行
		if (parts.isNotEmpty()) {
			LazyRow(
				contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
			) {
				item {
					Surface(
						shape = RoundedCornerShape(6.dp),
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						onClick = { onNavigate("") },
					) {
						Text(
							"NAS",
							fontSize = 12.sp,
							color = MaterialTheme.colorScheme.primary,
							fontWeight = FontWeight.Medium,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
						)
					}
				}
				items(parts.indices.toList()) { i ->
					Icon(
						Icons.Rounded.ChevronRight,
						contentDescription = null,
						modifier = Modifier.size(13.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
					)
					val isLast = i == parts.lastIndex
					Surface(
						shape = RoundedCornerShape(6.dp),
						color = if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
						onClick = { onNavigate(parts.take(i + 1).joinToString("/")) },
					) {
						Text(
							parts[i],
							fontSize = 12.sp,
							color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
							fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(max = 140.dp),
						)
					}
				}
			}
		}

		// 排序与视图切换紧凑行：消除多余上下留白
		Row(
			Modifier
				.fillMaxWidth()
				.height(36.dp)
				.padding(horizontal = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Surface(
				shape = RoundedCornerShape(6.dp),
				color = Color.Transparent,
				onClick = { vm.cycleSortOrder() },
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
				) {
					Text(
						vm.sortOrder.label,
						fontSize = 12.sp,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
			Spacer(Modifier.weight(1f))
			IconButton(
				onClick = onToggleMultiSelect,
				modifier = Modifier.size(32.dp),
			) {
				Icon(
					Icons.Rounded.Checklist,
					contentDescription = "多选",
					modifier = Modifier.size(18.dp),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Spacer(Modifier.width(4.dp))
			IconButton(
				onClick = onToggleViewMode,
				modifier = Modifier.size(32.dp),
			) {
				Icon(
					if (vm.viewMode == ViewMode.Cards) Icons.Rounded.GridView else Icons.Rounded.ViewHeadline,
					contentDescription = "切换视图",
					modifier = Modifier.size(18.dp),
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

// ==================== 分类胶囊行（纯矢量图标） ====================

@Composable
private fun CategoryFilterRow(
	selected: FileCategory,
	onSelect: (FileCategory) -> Unit,
) {
	val categories = listOf(
		Triple(FileCategory.All, "全部", Icons.Rounded.Folder),
		Triple(FileCategory.Image, "图片", Icons.Rounded.Image),
		Triple(FileCategory.Video, "视频", Icons.Rounded.Movie),
		Triple(FileCategory.Document, "文档", Icons.Rounded.Description),
		Triple(FileCategory.Audio, "音频", Icons.Rounded.MusicNote),
		Triple(FileCategory.Archive, "压缩包", Icons.Rounded.FolderZip),
	)

	LazyRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		items(categories) { (cat, title, icon) ->
			val active = cat == selected
			Surface(
				shape = RoundedCornerShape(14.dp),
				color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
				border = BorderStroke(
					0.5.dp,
					if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
				),
				shadowElevation = if (active) 1.5.dp else 0.dp,
				onClick = { onSelect(cat) },
			) {
				Row(
					modifier = Modifier
						.height(28.dp)
						.padding(horizontal = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(5.dp),
				) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(13.dp),
					)
					Text(
						text = title,
						fontSize = 11.5.sp,
						fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
						color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}
		}
	}
}

// ==================== 多选状态条与批量操作托盘 ====================

@Composable
private fun MultiSelectHeaderStrip(
	selectedCount: Int,
	onSelectAll: () -> Unit,
	onComplete: () -> Unit,
) {
	Surface(
		shape = RoundedCornerShape(22.dp),
		color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
		shadowElevation = 8.dp,
		tonalElevation = 4.dp,
		border = BorderStroke(
			1.dp,
			Brush.verticalGradient(
				listOf(
					Color.White.copy(alpha = 0.5f),
					MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
				)
			)
		),
		modifier = Modifier
			.fillMaxWidth()
			.graphicsLayer { clip = true; shape = RoundedCornerShape(22.dp) },
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
				Text(
					text = "已选 $selectedCount 项",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Text(
					text = "· 可滑动连选",
					fontSize = 11.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Text(
					text = "全选",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.clickable { onSelectAll() },
				)
				Text(
					text = "完成",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary,
					modifier = Modifier.clickable { onComplete() },
				)
			}
		}
	}
}

@Composable
private fun BatchActionTray(
	selectedCount: Int,
	onDownload: () -> Unit,
	onMove: () -> Unit,
	onDelete: () -> Unit,
	onCancel: () -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.navigationBarsPadding()
			.padding(horizontal = 20.dp, vertical = 8.dp),
		contentAlignment = Alignment.Center,
	) {
		Surface(
			shape = RoundedCornerShape(28.dp),
			color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
			shadowElevation = 12.dp,
			tonalElevation = 0.dp,
			border = BorderStroke(
				1.dp,
				Brush.verticalGradient(
					listOf(
						Color.White.copy(alpha = 0.60f),
						MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
					)
				)
			),
			modifier = Modifier
				.fillMaxWidth()
				.graphicsLayer { clip = true; shape = RoundedCornerShape(28.dp) },
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 12.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceAround,
			) {
				BatchActionItem(icon = Icons.Rounded.CloudDownload, label = "批量下载", onClick = onDownload)
				BatchActionItem(icon = Icons.Rounded.DriveFileMove, label = "批量移动", onClick = onMove)
				BatchActionItem(icon = Icons.Rounded.Delete, label = "彻底删除", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
				BatchActionItem(icon = Icons.Rounded.ArrowBack, label = "取消", onClick = onCancel)
			}
		}
	}
}

@Composable
private fun BatchActionItem(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	tint: Color = MaterialTheme.colorScheme.onSurface,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 48.dp).clickable { onClick() },
	) {
		Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
		Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
	}
}

// ==================== 文件浏览内容区 ====================

@OptIn(ExperimentalFoundationApi::class)
/** 加载、授权与网络失败分别说明后续操作，错误详情允许完整换行。 */
@Composable
private fun BrowserStatus(
	title: String,
	detail: String? = null,
	loading: Boolean = false,
	onPrimary: (() -> Unit)? = null,
	primaryLabel: String = "重试",
	onSettings: (() -> Unit)? = null,
) {
	Column(
		Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 40.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		if (loading) CircularProgressIndicator(Modifier.padding(bottom = 20.dp))
		else Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
		Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
		if (!detail.isNullOrBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
		if (onPrimary != null) Button(onClick = onPrimary, modifier = Modifier.padding(top = 20.dp)) { Text(primaryLabel) }
		if (onSettings != null) TextButton(onClick = onSettings) { Text("检查连接设置") }
	}
}

private fun findItemIndexAt(state: LazyListState, pointerY: Float): Int? {
	val visibleItems = state.layoutInfo.visibleItemsInfo
	if (visibleItems.isEmpty()) return null
	val targetY = pointerY.toInt()
	val hit = visibleItems.firstOrNull { info ->
		targetY in info.offset..(info.offset + info.size)
	}
	if (hit != null) return hit.index
	val first = visibleItems.first()
	val last = visibleItems.last()
	return when {
		targetY < first.offset -> first.index
		targetY > last.offset + last.size -> last.index
		else -> visibleItems.minByOrNull { info ->
			val center = info.offset + info.size / 2
			kotlin.math.abs(targetY - center)
		}?.index
	}
}

private fun findGridItemIndexAt(state: LazyGridState, pointerX: Float, pointerY: Float): Int? {
	val visibleItems = state.layoutInfo.visibleItemsInfo
	if (visibleItems.isEmpty()) return null
	val px = pointerX.toInt()
	val py = pointerY.toInt()
	val hit = visibleItems.firstOrNull { info ->
		px in info.offset.x..(info.offset.x + info.size.width) &&
		py in info.offset.y..(info.offset.y + info.size.height)
	}
	if (hit != null) return hit.index
	return visibleItems.minByOrNull { info ->
		val cx = info.offset.x + info.size.width / 2
		val cy = info.offset.y + info.size.height / 2
		val dx = (px - cx).toFloat()
		val dy = (py - cy).toFloat()
		dx * dx + dy * dy
	}?.index
}

private fun findGridItemKeyAt(state: LazyGridState, pointerX: Float, pointerY: Float): Any? {
	val visibleItems = state.layoutInfo.visibleItemsInfo
	if (visibleItems.isEmpty()) return null
	val px = pointerX.toInt()
	val py = pointerY.toInt()
	val hit = visibleItems.firstOrNull { info ->
		px in info.offset.x..(info.offset.x + info.size.width) &&
		py in info.offset.y..(info.offset.y + info.size.height)
	}
	if (hit != null) return hit.key
	return visibleItems.minByOrNull { info ->
		val cx = info.offset.x + info.size.width / 2
		val cy = info.offset.y + info.size.height / 2
		val dx = (px - cx).toFloat()
		val dy = (py - cy).toFloat()
		dx * dx + dy * dy
	}?.key
}

private fun findGridKeyAt(
	state: LazyGridState,
	pointerX: Float,
	pointerY: Float,
	validKeySet: Set<String>,
): String? {
	val visibleItems = state.layoutInfo.visibleItemsInfo
	if (visibleItems.isEmpty()) return null
	val items = visibleItems.filter { it.key is String && it.key in validKeySet }
	if (items.isEmpty()) return null
	val px = pointerX.toInt()
	val py = pointerY.toInt()
	val hit = items.firstOrNull { info ->
		px in info.offset.x..(info.offset.x + info.size.width) &&
		py in info.offset.y..(info.offset.y + info.size.height)
	}
	if (hit != null) return hit.key as? String
	return items.minByOrNull { info ->
		val cx = info.offset.x + info.size.width / 2
		val cy = info.offset.y + info.size.height / 2
		val dx = (px - cx).toFloat()
		val dy = (py - cy).toFloat()
		dx * dx + dy * dy
	}?.key as? String
}

private fun findPhotoKeyAt(state: LazyGridState, pointerX: Float, pointerY: Float): String? {
	val visibleItems = state.layoutInfo.visibleItemsInfo
	if (visibleItems.isEmpty()) return null
	val photoItems = visibleItems.filter { it.key is String }
	if (photoItems.isEmpty()) return null
	val px = pointerX.toInt()
	val py = pointerY.toInt()
	val hit = photoItems.firstOrNull { info ->
		px in info.offset.x..(info.offset.x + info.size.width) &&
		py in info.offset.y..(info.offset.y + info.size.height)
	}
	if (hit != null) return hit.key as? String
	return photoItems.minByOrNull { info ->
		val cx = info.offset.x + info.size.width / 2
		val cy = info.offset.y + info.size.height / 2
		val dx = (px - cx).toFloat()
		val dy = (py - cy).toFloat()
		dx * dx + dy * dy
	}?.key as? String
}

/**
 * 工业级 LazyVerticalGrid 流畅滑动多选修饰符
 * - 长按触发震动后无需松手，手指顺势拖拽即可连续框选连选；
 * - 多选模式下轻按/拖动即刻进入连选；
 * - 84dp 宽域动态边界自动滚屏，且滚屏每一帧按当前手指停留位置实时拾取新滚入的项目并伴有触觉反馈；
 * - 支持右侧时间轴擦洗条避让。
 */
@JvmName("dragSelectableGridKeys")
@Composable
private fun Modifier.dragSelectableGrid(
	state: LazyGridState,
	allKeys: List<String>,
	isMulti: Boolean,
	selectedPaths: Set<String>,
	onEnterMultiSelect: (String) -> Unit,
	onUpdateSelectionPaths: (Set<String>) -> Unit,
	rightEdgeIgnoreWidthDp: Dp = 44.dp,
): Modifier {
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val density = LocalDensity.current
	val currentAllKeys by rememberUpdatedState(allKeys)
	val currentIsMulti by rememberUpdatedState(isMulti)
	val currentSelectedPaths by rememberUpdatedState(selectedPaths)
	val currentOnEnterMultiSelect by rememberUpdatedState(onEnterMultiSelect)
	val currentOnUpdateSelectionPaths by rememberUpdatedState(onUpdateSelectionPaths)

	return this.pointerInput(Unit) {
		val rightIgnorePx = with(density) { rightEdgeIgnoreWidthDp.toPx() }
		val edgeZonePx = with(density) { 84.dp.toPx() }

		awaitEachGesture {
			val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
			val downPos = down.position
			if (rightIgnorePx > 0f && downPos.x >= size.width - rightIgnorePx) return@awaitEachGesture

			val keySet = currentAllKeys.toSet()
			var dragStartIndex = -1
			var dragCurrentIndex = -1
			var baseSelection = emptySet<String>()
			var isDragging = false
			val touchSlop = viewConfiguration.touchSlop
			val isMultiAtStart = currentIsMulti
			val longPressTimeout = if (isMultiAtStart) 120L else 280L

			var isTriggered = false
			try {
				withTimeout(longPressTimeout) {
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						val pointer = event.changes.firstOrNull { it.id == down.id }
						if (pointer == null || !pointer.pressed) return@withTimeout
						val diff = pointer.position - downPos
						if (isMultiAtStart) {
							if (kotlin.math.abs(diff.x) > touchSlop || diff.getDistance() > touchSlop) {
								isTriggered = true
								return@withTimeout
							}
						} else {
							if (diff.getDistance() > touchSlop) return@withTimeout
						}
					}
				}
			} catch (e: TimeoutCancellationException) {
				isTriggered = true
			}

			if (isTriggered) {
				val hitKey = findGridKeyAt(state, downPos.x, downPos.y, keySet)
				val hitIndex = if (hitKey != null) currentAllKeys.indexOf(hitKey) else -1
				if (hitIndex in currentAllKeys.indices) {
					down.consume()
					dragStartIndex = hitIndex
					dragCurrentIndex = hitIndex
					baseSelection = if (isMultiAtStart) currentSelectedPaths else emptySet()
					isDragging = true
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					if (!isMultiAtStart) {
						currentOnEnterMultiSelect(hitKey!!)
					} else {
						currentOnUpdateSelectionPaths(baseSelection + hitKey!!)
					}
				}
			}

			if (isDragging && dragStartIndex in currentAllKeys.indices) {
				var autoScrollVelocity = 0f
				var latestPos = downPos

				val autoScrollJob = scope.launch {
					while (isActive) {
						val vel = autoScrollVelocity
						if (vel != 0f) {
							state.scrollBy(vel)
							val curPos = latestPos
							val hitKey = findGridKeyAt(state, curPos.x, curPos.y, keySet)
							val hitIndex = if (hitKey != null) currentAllKeys.indexOf(hitKey) else -1
							if (hitIndex in currentAllKeys.indices && hitIndex != dragCurrentIndex) {
								dragCurrentIndex = hitIndex
								val start = minOf(dragStartIndex, dragCurrentIndex)
								val end = maxOf(dragStartIndex, dragCurrentIndex)
								val rangePaths = currentAllKeys.subList(start, end + 1).toSet()
								currentOnUpdateSelectionPaths(baseSelection + rangePaths)
								haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							}
						}
						delay(16)
					}
				}

				try {
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						val pointer = event.changes.firstOrNull { it.id == down.id }
						if (pointer == null || !pointer.pressed) {
							pointer?.consume()
							event.changes.forEach { it.consume() }
							break
						}
						pointer.consume()
						event.changes.forEach { it.consume() }

						latestPos = pointer.position
						val curX = pointer.position.x
						val curY = pointer.position.y
						val vh = state.layoutInfo.viewportSize.height.toFloat()

						if (curY < edgeZonePx) {
							val factor = ((edgeZonePx - curY) / edgeZonePx).coerceIn(0f, 1f)
							autoScrollVelocity = -(12f + factor * 38f)
						} else if (vh > 0f && curY > vh - edgeZonePx) {
							val factor = ((curY - (vh - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
							autoScrollVelocity = (12f + factor * 38f)
						} else {
							autoScrollVelocity = 0f
						}

						val hitKey = findGridKeyAt(state, curX, curY, keySet)
						val hitIndex = if (hitKey != null) currentAllKeys.indexOf(hitKey) else -1
						if (hitIndex in currentAllKeys.indices && hitIndex != dragCurrentIndex) {
							dragCurrentIndex = hitIndex
							val start = minOf(dragStartIndex, dragCurrentIndex)
							val end = maxOf(dragStartIndex, dragCurrentIndex)
							val rangePaths = currentAllKeys.subList(start, end + 1).toSet()
							currentOnUpdateSelectionPaths(baseSelection + rangePaths)
							haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						}
					}
				} finally {
					autoScrollJob.cancel()
				}
			}
		}
	}
}

/**
 * DavEntry 列表适配重载
 */
@JvmName("dragSelectableGridEntries")
@Composable
private fun Modifier.dragSelectableGrid(
	state: LazyGridState,
	items: List<DavEntry>,
	isMulti: Boolean,
	selectedEntries: Set<DavEntry>,
	onEnterMultiSelect: (DavEntry) -> Unit,
	onUpdateSelection: (Set<DavEntry>) -> Unit,
	rightEdgeIgnoreWidthDp: Dp = 0.dp,
): Modifier {
	val entryMap = remember(items) { items.associateBy { it.path } }
	return this.dragSelectableGrid(
		state = state,
		allKeys = items.map { it.path },
		isMulti = isMulti,
		selectedPaths = selectedEntries.map { it.path }.toSet(),
		onEnterMultiSelect = { path ->
			entryMap[path]?.let { onEnterMultiSelect(it) }
		},
		onUpdateSelectionPaths = { paths ->
			val selected = paths.mapNotNull { entryMap[it] }.toSet()
			onUpdateSelection(selected)
		},
		rightEdgeIgnoreWidthDp = rightEdgeIgnoreWidthDp,
	)
}

/**
 * 为相册瀑布流/时间轴赋能真正的流畅滑动多选 (支持长按连选、多选模式下直接滑动跨选、边界自动滚屏)
 */
@Composable
private fun Modifier.dragSelectablePhotoGrid(
	state: LazyGridState,
	allPhotos: List<DavEntry>,
	isMulti: Boolean,
	selectedPaths: Set<String>,
	onEnterMultiSelect: (DavEntry) -> Unit,
	onUpdateSelectionPaths: (Set<String>) -> Unit,
): Modifier {
	val entryMap = remember(allPhotos) { allPhotos.associateBy { it.path } }
	return this.dragSelectableGrid(
		state = state,
		allKeys = allPhotos.map { it.path },
		isMulti = isMulti,
		selectedPaths = selectedPaths,
		onEnterMultiSelect = { path ->
			entryMap[path]?.let { onEnterMultiSelect(it) }
		},
		onUpdateSelectionPaths = onUpdateSelectionPaths,
		rightEdgeIgnoreWidthDp = 44.dp,
	)
}

/**
 * 为 LazyColumn 赋予流畅的滑动多选能力 (包含长按进入多选、顺势拖动连选、边界自动滚屏、震动反馈)
 */
@Composable
private fun Modifier.dragSelectableList(
	state: LazyListState,
	items: List<DavEntry>,
	isMulti: Boolean,
	selectedEntries: Set<DavEntry>,
	onEnterMultiSelect: (DavEntry) -> Unit,
	onUpdateSelection: (Set<DavEntry>) -> Unit,
): Modifier {
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val density = LocalDensity.current
	val currentItems by rememberUpdatedState(items)
	val currentIsMulti by rememberUpdatedState(isMulti)
	val currentSelectedEntries by rememberUpdatedState(selectedEntries)
	val currentOnEnterMultiSelect by rememberUpdatedState(onEnterMultiSelect)
	val currentOnUpdateSelection by rememberUpdatedState(onUpdateSelection)

	return this.pointerInput(Unit) {
		val edgeZonePx = with(density) { 84.dp.toPx() }
		awaitEachGesture {
			val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
			val downPos = down.position
			if (downPos.x >= size.width - with(density) { 44.dp.toPx() }) return@awaitEachGesture
			var dragStartIndex = -1
			var dragCurrentIndex = -1
			var baseSelection = emptySet<DavEntry>()
			var isDragging = false
			val touchSlop = viewConfiguration.touchSlop
			val isMultiAtStart = currentIsMulti
			val longPressTimeout = if (isMultiAtStart) 120L else 280L

			var isTriggered = false
			try {
				withTimeout(longPressTimeout) {
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						val pointer = event.changes.firstOrNull { it.id == down.id }
						if (pointer == null || !pointer.pressed) return@withTimeout
						val diff = pointer.position - downPos
						if (isMultiAtStart) {
							if (kotlin.math.abs(diff.x) > touchSlop || diff.getDistance() > touchSlop) {
								isTriggered = true
								return@withTimeout
							}
						} else {
							if (diff.getDistance() > touchSlop) return@withTimeout
						}
					}
				}
			} catch (e: TimeoutCancellationException) {
				isTriggered = true
			}

			if (isTriggered) {
				val hitIndex = findItemIndexAt(state, downPos.y)
				if (hitIndex != null && hitIndex in currentItems.indices) {
					down.consume()
					val entry = currentItems[hitIndex]
					dragStartIndex = hitIndex
					dragCurrentIndex = hitIndex
					baseSelection = if (isMultiAtStart) currentSelectedEntries else emptySet()
					isDragging = true
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)
					if (!isMultiAtStart) {
						currentOnEnterMultiSelect(entry)
					} else {
						currentOnUpdateSelection(baseSelection + entry)
					}
				}
			}

			if (isDragging && dragStartIndex in currentItems.indices) {
				var autoScrollVelocity = 0f
				var latestY = downPos.y

				val autoScrollJob = scope.launch {
					while (isActive) {
						val vel = autoScrollVelocity
						if (vel != 0f) {
							state.scrollBy(vel)
							val hitIndex = findItemIndexAt(state, latestY)
							if (hitIndex != null && hitIndex in currentItems.indices && hitIndex != dragCurrentIndex) {
								dragCurrentIndex = hitIndex
								val start = minOf(dragStartIndex, dragCurrentIndex)
								val end = maxOf(dragStartIndex, dragCurrentIndex)
								val rangeSet = currentItems.subList(start, end + 1).toSet()
								currentOnUpdateSelection(baseSelection + rangeSet)
								haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							}
						}
						delay(16)
					}
				}

				try {
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						val pointer = event.changes.firstOrNull { it.id == down.id }
						if (pointer == null || !pointer.pressed) {
							pointer?.consume()
							event.changes.forEach { it.consume() }
							break
						}
						pointer.consume()
						event.changes.forEach { it.consume() }

						latestY = pointer.position.y
						val vh = state.layoutInfo.viewportSize.height.toFloat()

						if (latestY < edgeZonePx) {
							val factor = ((edgeZonePx - latestY) / edgeZonePx).coerceIn(0f, 1f)
							autoScrollVelocity = -(12f + factor * 36f)
						} else if (vh > 0f && latestY > vh - edgeZonePx) {
							val factor = ((latestY - (vh - edgeZonePx)) / edgeZonePx).coerceIn(0f, 1f)
							autoScrollVelocity = (12f + factor * 36f)
						} else {
							autoScrollVelocity = 0f
						}

						val hitIndex = findItemIndexAt(state, latestY)
						if (hitIndex != null && hitIndex in currentItems.indices && hitIndex != dragCurrentIndex) {
							dragCurrentIndex = hitIndex
							val start = minOf(dragStartIndex, dragCurrentIndex)
							val end = maxOf(dragStartIndex, dragCurrentIndex)
							val rangeSet = currentItems.subList(start, end + 1).toSet()
							currentOnUpdateSelection(baseSelection + rangeSet)
							haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						}
					}
				} finally {
					autoScrollJob.cancel()
				}
			}
		}
	}
}

@Composable
private fun FileBrowserContent(
	vm: BrowserViewModel,
	onOpenEntry: (DavEntry) -> Unit,
	onLongClickEntry: (DavEntry) -> Unit,
	onOpenAction: (DavEntry) -> Unit,
) {
	val items = vm.filteredEntries
	val isMulti = vm.isMultiSelectMode

	if (items.isEmpty() && !vm.loading) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.verticalScroll(rememberScrollState()),
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(8.dp),
			) {
				FileIcon(category = FileCategory.All, isDir = true, size = 64.dp)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = if (vm.searchQuery.isNotBlank()) "未找到匹配「${vm.searchQuery}」的文件" else "此文件夹空空如也",
					fontSize = 14.sp,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
		return
	}

	when (vm.viewMode) {
		ViewMode.Cards -> {
			// 纯净极简列表：白底、细致缩进分割线、长按滑动多选（无卡片边框包围）
			LazyColumn(
				state = vm.fileListState,
				contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.surface)
					.dragSelectableList(
						state = vm.fileListState,
						items = items,
						isMulti = isMulti,
						selectedEntries = vm.selectedEntries,
						onEnterMultiSelect = { vm.enterMultiSelect(it) },
						onUpdateSelection = { vm.updateSelection(it) },
					),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
							.combinedClickable(
								onClick = {
									if (isMulti) {
										vm.toggleSelect(entry)
									} else {
										onOpenEntry(entry)
									}
								},
								onLongClick = {
									if (!isMulti) {
										vm.enterMultiSelect(entry)
									} else {
										vm.toggleSelect(entry)
									}
								},
							)
							.padding(horizontal = 14.dp, vertical = 7.5.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						FileThumbnail(
							entry = entry,
							urlOf = { vm.thumbnailUrlFor(it) },
							directUrlOf = { vm.directUrlFor(it) },
							onThumbnailError = { vm.markThumbnailFailed(it) },
							size = 42.dp,
							customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null,
						)

						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = entry.name,
								fontSize = 14.sp,
								fontWeight = FontWeight.Medium,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 11.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						if (isMulti) {
							SelectCheckbox(isSelected = isSelected)
						} else {
							IconButton(
								onClick = { onOpenAction(entry) },
								modifier = Modifier.size(40.dp),
							) {
								Icon(
									Icons.Rounded.MoreVert,
									contentDescription = "更多",
									tint = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier.size(18.dp),
								)
							}
						}
					}
					HorizontalDivider(
						color = MaterialTheme.colorScheme.surfaceVariant,
						thickness = 0.5.dp,
						modifier = Modifier.padding(start = 68.dp),
					)
				}

				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 18.dp),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "共 ${items.size} 项",
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}

		ViewMode.Compact -> {
			LazyColumn(
				state = vm.fileCompactState,
				contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.surface)
					.dragSelectableList(
						state = vm.fileCompactState,
						items = items,
						isMulti = isMulti,
						selectedEntries = vm.selectedEntries,
						onEnterMultiSelect = { vm.enterMultiSelect(it) },
						onUpdateSelection = { vm.updateSelection(it) },
					),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
							.combinedClickable(
								onClick = {
									if (isMulti) {
										vm.toggleSelect(entry)
									} else {
										onOpenEntry(entry)
									}
								},
								onLongClick = {
									if (!isMulti) {
										vm.enterMultiSelect(entry)
									} else {
										vm.toggleSelect(entry)
									}
								},
							)
							.padding(horizontal = 14.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						if (isMulti) {
							SelectCheckbox(isSelected = isSelected)
						}

						FileThumbnail(
							entry = entry,
							urlOf = { vm.thumbnailUrlFor(it) },
							directUrlOf = { vm.directUrlFor(it) },
							onThumbnailError = { vm.markThumbnailFailed(it) },
							size = 32.dp,
							customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null,
						)

						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = entry.displayName,
								fontSize = 13.sp,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 11.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						if (!isMulti) {
							IconButton(onClick = { onOpenAction(entry) }, modifier = Modifier.size(48.dp)) {
								Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
							}
						}
					}
					HorizontalDivider(
						color = MaterialTheme.colorScheme.surfaceVariant,
						thickness = 0.5.dp,
						modifier = Modifier.padding(start = 56.dp),
					)
				}
			}
		}

		ViewMode.Grid -> {
			LazyVerticalGrid(
				state = vm.fileGridState,
				columns = GridCells.Fixed(2),
				contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 88.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier
					.fillMaxSize()
					.dragSelectableGrid(
						state = vm.fileGridState,
						items = items,
						isMulti = isMulti,
						selectedEntries = vm.selectedEntries,
						onEnterMultiSelect = { vm.enterMultiSelect(it) },
						onUpdateSelection = { vm.updateSelection(it) },
					),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Card(
						shape = RoundedCornerShape(14.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
						modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = {
									if (isMulti) {
										vm.toggleSelect(entry)
									} else {
										onOpenEntry(entry)
									}
								},
								onLongClick = {
									if (!isMulti) {
										vm.enterMultiSelect(entry)
									} else {
										vm.toggleSelect(entry)
									}
								},
							),
					) {
						Column(
							modifier = Modifier.padding(10.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Box(modifier = Modifier.fillMaxWidth()) {
								if (isMulti) {
									SelectCheckbox(isSelected = isSelected, modifier = Modifier.align(Alignment.TopStart))
								}
								if (!isMulti) {
									IconButton(
										onClick = { onOpenAction(entry) },
										modifier = Modifier
											.size(48.dp)
											.align(Alignment.TopEnd),
									) {
										Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
									}
								}
							}

							Spacer(modifier = Modifier.height(2.dp))
							if (entry.isImage) {
								val thumbTarget = vm.thumbnailUrlFor(entry.path)
								var thumbFailed by remember(entry.path) { mutableStateOf(false) }
								val actualData = if (!thumbFailed) thumbTarget else null
								if (!actualData.isNullOrBlank()) {
									Box(
										modifier = Modifier
											.fillMaxWidth()
											.aspectRatio(1.2f)
											.clip(RoundedCornerShape(8.dp))
											.background(MaterialTheme.colorScheme.outlineVariant),
									) {
										AsyncImage(
											model = ImageRequest.Builder(LocalContext.current)
												.data(actualData)
												.memoryCacheKey("thumb:${entry.path.trim('/')}")
												.diskCacheKey("thumb:${entry.path.trim('/')}")
												.size(240, 240)
												.precision(coil.size.Precision.INEXACT)
												.allowRgb565(true)
												.crossfade(false)
												.build(),
											contentDescription = entry.name,
											contentScale = ContentScale.Crop,
											onError = {
												thumbFailed = true
												vm.markThumbnailFailed(entry.path)
											},
											modifier = Modifier.fillMaxSize(),
										)
										MediaThumbBadges(
											entry = entry,
											modifier = Modifier.fillMaxSize().padding(4.dp),
											compact = false,
										)
									}
								} else {
									FileIconByEntry(
										entry = entry,
										size = 52.dp,
										customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null,
										urlOf = { vm.thumbnailUrlFor(it) },
										directUrlOf = null,
									)
								}
							} else {
								FileIconByEntry(
									entry = entry,
									size = 52.dp,
									customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null,
									urlOf = { vm.thumbnailUrlFor(it) },
									directUrlOf = { vm.directUrlFor(it) },
								)
							}
							Spacer(modifier = Modifier.height(8.dp))

							Text(
								text = entry.displayName,
								fontSize = 13.sp,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 10.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SelectCheckbox(isSelected: Boolean, modifier: Modifier = Modifier) {
	Surface(
		shape = CircleShape,
		color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
		border = BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
		modifier = modifier.size(20.dp),
	) {
		if (isSelected) {
			Box(contentAlignment = Alignment.Center) {
				Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
			}
		}
	}
}

@Composable
private fun HomeScreenContent(
	vm: BrowserViewModel,
	memoryYearGroups: List<com.nasdrop.MemoryYearGroup>,
	onSelectMemoryGroup: (com.nasdrop.MemoryYearGroup) -> Unit,
	onOpenImage: (DavEntry) -> Unit,
	onOpenVideo: (DavEntry) -> Unit,
	urlOf: (String) -> String?,
	directUrlOf: (String) -> String?,
	onOpenSettings: () -> Unit = {},
	onOpenGameCenter: () -> Unit = {},
) {
	val allMedia = remember(vm.allPhotos, vm.allVideos) {
		(vm.allPhotos + vm.allVideos).distinctBy { it.path }
			.sortedByDescending { it.effectiveDate }
	}
	val recentItems = remember(allMedia) { allMedia.take(12) }

	PullToRefreshBox(
		isRefreshing = vm.isRefreshing,
		onRefresh = { vm.pullRefresh() },
		modifier = Modifier.fillMaxSize(),
	) {
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp),
			contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
		) {
			// 1. NAS 数据资产概览卡片（支持点击数字快捷筛选相册内容）
			item(key = "home_stats_card") {
				Surface(
					shape = RoundedCornerShape(20.dp),
					color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
					modifier = Modifier.fillMaxWidth(),
				) {
					Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Icon(
									Icons.Rounded.PieChart,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(20.dp),
								)
								Text(
									text = "数据资产概览",
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold,
								)
							}
						}

						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceAround,
							verticalAlignment = Alignment.CenterVertically,
						) {
							HomeStatItem(
								number = vm.allPhotos.size.toString(),
								label = "照片",
								onClick = {
									vm.currentTab = MainTab.Photos
									vm.selectedAlbumPath = ""
									vm.mediaFilterType = MediaFilterType.PhotosOnly
								},
							)
							HomeStatDivider()
							HomeStatItem(
								number = vm.allVideos.size.toString(),
								label = "视频",
								onClick = {
									vm.currentTab = MainTab.Photos
									vm.selectedAlbumPath = ""
									vm.mediaFilterType = MediaFilterType.VideosOnly
								},
							)
							HomeStatDivider()
							HomeStatItem(
								number = vm.allAlbumFolders.size.toString(),
								label = "相册",
								onClick = {
									vm.currentTab = MainTab.Photos
									vm.selectedAlbumPath = "__ALL_FOLDERS__"
									vm.mediaFilterType = MediaFilterType.All
								},
							)
							HomeStatDivider()
							val memoCount = remember(vm.allPhotos, vm.allVideos, PhotoMetadataStore.revision) {
								allMedia.count { it.hasAudio || it.hasMemo }
							}
							HomeStatItem(
								number = memoCount.toString(),
								label = "伴生记忆",
								onClick = {
									vm.currentTab = MainTab.Photos
									vm.selectedAlbumPath = ""
									vm.mediaFilterType = MediaFilterType.WithAudioMemo
								},
							)
						}
					}
				}
			}

			// 2. 家庭互动游戏入口卡片（点击选择游戏）
			item(key = "home_game_entry") {
				Surface(
					shape = RoundedCornerShape(20.dp),
					color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
					onClick = onOpenGameCenter,
					modifier = Modifier.fillMaxWidth(),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(14.dp),
							modifier = Modifier.weight(1f),
						) {
							Surface(
								shape = CircleShape,
								color = MaterialTheme.colorScheme.primaryContainer,
								modifier = Modifier.size(42.dp),
							) {
								Box(contentAlignment = Alignment.Center) {
									Icon(
										Icons.Rounded.SportsEsports,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(24.dp),
									)
								}
							}
							Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
								Row(
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(6.dp),
								) {
									Text(
										text = "时光杂货铺",
										style = MaterialTheme.typography.titleMedium,
										fontWeight = FontWeight.Bold,
									)
									Surface(
										shape = RoundedCornerShape(8.dp),
										color = Color(0xFFEF4444).copy(alpha = 0.15f),
									) {
										Text(
											"趣味小店",
											fontSize = 10.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFFEF4444),
											modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
										)
									}
								}
								Text(
									text = "翻翻老相册 · 点击挑选玩法",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
						Icon(
							Icons.Rounded.ChevronRight,
							contentDescription = "进入时光杂货铺",
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}

			// 3. 那年今日（仅在存在往年今日历史回忆时呈现，无数据时完全隐藏）
			if (memoryYearGroups.isNotEmpty()) {
				item(key = "home_memories_section") {
					Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(6.dp),
							) {
								Icon(
									Icons.Rounded.AutoAwesome,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(18.dp),
								)
								Text(
									"那年今日",
									style = MaterialTheme.typography.titleMedium,
									fontWeight = FontWeight.Bold,
									color = MaterialTheme.colorScheme.onSurface,
								)
							}
							Text(
								"共 ${memoryYearGroups.size} 个历史时刻",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}

						MemoriesBanner(
							memoryYearGroups = memoryYearGroups,
							onClickGroup = onSelectMemoryGroup,
							urlOf = urlOf,
							directUrlOf = directUrlOf,
						)
					}
				}
			}

			// 4. 近期精彩动态（动态焦点画卷 + 水平滑轨）
			if (recentItems.isNotEmpty()) {
				item(key = "home_recent_header") {
					Row(
						modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp),
						) {
							Icon(
								Icons.Rounded.FlashOn,
								contentDescription = null,
								tint = Color(0xFFEF4444),
								modifier = Modifier.size(18.dp),
							)
							Text(
								"近期精彩动态",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.onSurface,
							)
						}
						TextButton(onClick = { vm.currentTab = MainTab.Photos }) {
							Text("查看全部 (${allMedia.size})", fontSize = 13.sp)
						}
					}
				}

				// 4.1 高清精选相册网格（三列高清排版，自动兜底保障不漏图、不虚焦）
				item(key = "home_recent_grid") {
					val displayItems = remember(recentItems) { recentItems.take(6) }
					val rows = remember(displayItems) { displayItems.chunked(3) }
					Column(
						modifier = Modifier.fillMaxWidth(),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						rows.forEach { rowItems ->
							Row(
								modifier = Modifier.fillMaxWidth(),
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								rowItems.forEach { entry ->
									Box(
										modifier = Modifier
											.weight(1f)
											.aspectRatio(1f)
											.clip(RoundedCornerShape(12.dp))
											.background(MaterialTheme.colorScheme.surfaceVariant)
											.clickable {
												if (entry.isVideo) onOpenVideo(entry)
												else onOpenImage(entry)
											},
									) {
										val thumbTarget = urlOf(entry.path)
										val directTarget = vm.directUrlFor(entry.path)
										var thumbFailed by remember(entry.path) { mutableStateOf(false) }
										val actualData = if (!thumbFailed) (thumbTarget ?: directTarget) else directTarget

										if (!actualData.isNullOrBlank()) {
											AsyncImage(
												model = ImageRequest.Builder(LocalContext.current)
													.data(actualData)
													.memoryCacheKey("media_thumb:${entry.path.trim('/')}")
													.diskCacheKey("media_thumb:${entry.path.trim('/')}")
													.size(480, 480)
													.precision(coil.size.Precision.AUTOMATIC)
													.allowRgb565(false)
													.crossfade(true)
													.build(),
												contentDescription = entry.name,
												contentScale = ContentScale.Crop,
												onError = {
													if (!thumbFailed) {
														thumbFailed = true
													} else {
														vm.markThumbnailFailed(entry.path)
													}
												},
												modifier = Modifier.fillMaxSize(),
											)
										} else {
											Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
												FileIcon(
													category = if (entry.isVideo) FileCategory.Video else FileCategory.Image,
													isDir = false,
													size = 36.dp,
												)
											}
										}

										// 角标（伴生记忆、记事、视频与时长）
										MediaThumbBadges(
											entry = entry,
											modifier = Modifier
												.fillMaxSize()
												.padding(4.dp),
											compact = true,
										)
									}
								}
								val emptySlots = 3 - rowItems.size
								repeat(emptySlots) {
									Spacer(modifier = Modifier.weight(1f))
								}
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun HomeStatItem(
	number: String,
	label: String,
	onClick: () -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.clickable(onClick = onClick)
			.padding(horizontal = 4.dp, vertical = 2.dp),
	) {
		Text(
			text = number,
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.ExtraBold,
			color = MaterialTheme.colorScheme.primary,
		)
		Text(
			text = label,
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun HomeStatDivider() {
	Box(
		modifier = Modifier
			.width(1.dp)
			.height(24.dp)
			.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
	)
}

@Composable
private fun HomeQuickCard(
	title: String,
	sub: String,
	icon: ImageVector,
	color: Color,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Surface(
		shape = RoundedCornerShape(16.dp),
		color = color.copy(alpha = 0.08f),
		border = BorderStroke(1.dp, color.copy(alpha = 0.20f)),
		onClick = onClick,
		modifier = modifier,
	) {
		Row(
			modifier = Modifier.padding(14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Surface(
				shape = CircleShape,
				color = color.copy(alpha = 0.18f),
				modifier = Modifier.size(38.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
				}
			}
			Column {
				Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
				Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
}

// ==================== 相册与影音 Tab ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTimelineContent(
	state: LazyGridState,
	groups: List<com.nasdrop.TimelineGroup>,
	allAlbums: List<DavEntry>,
	allPhotos: List<DavEntry> = emptyList(),
	memoryYearGroups: List<com.nasdrop.MemoryYearGroup> = emptyList(),
	selectedAlbumPath: String,
	folderIcons: Map<String, String> = emptyMap(),
	mediaFilterType: MediaFilterType = MediaFilterType.All,
	onClearFilter: () -> Unit = {},
	isRefreshing: Boolean = false,
	onRefresh: () -> Unit = {},
	isMulti: Boolean = false,
	selectedPaths: Set<String> = emptySet(),
	onToggleSelect: (DavEntry) -> Unit = {},
	onEnterMultiSelect: (DavEntry) -> Unit = {},
	onUpdateSelectionPaths: ((Set<String>) -> Unit)? = null,
	onToggleSelectByPath: ((String, Boolean) -> Unit)? = null,
	onToggleSelectGroup: ((com.nasdrop.TimelineGroup, Boolean) -> Unit)? = null,
	onOpenAction: (DavEntry) -> Unit = {},
	onSelectAlbum: (String) -> Unit,
	onRenameAlbum: (DavEntry) -> Unit = {},
	onChangeIcon: (DavEntry) -> Unit = {},
	onDeleteAlbum: (DavEntry) -> Unit = {},
	onReorderAlbums: (fromPath: String, toPath: String) -> Unit = { _, _ -> },
	onCreateAlbum: () -> Unit,
	urlOf: (String) -> String?,
	directUrlOf: ((String) -> String?)? = null,
	onThumbnailError: ((String) -> Unit)? = null,
	onOpenImage: (DavEntry) -> Unit,
	onOpenVideo: (DavEntry) -> Unit = {},
) {
	val currentPhotos = remember(groups) { groups.flatMap { it.items } }
	val displayAlbums = remember(allAlbums) {
		val visible = allAlbums.filterNot {
			it.name.startsWith(".") || it.path.contains(".thumbnails") ||
				it.name == "默认影集" || it.name == "默认影音" ||
				it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
				it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
		}
		val custom = visible.filterNot { it.name == "默认相册" || it.path == "Photos/默认相册" }
		val defaults = visible.filter { it.name == "默认相册" || it.path == "Photos/默认相册" }
		custom + defaults
	}
	val currentDisplayAlbums by rememberUpdatedState(displayAlbums)
	val haptic = LocalHapticFeedback.current
	var draggingIndex by remember { mutableStateOf<Int?>(null) }
	var dragOffsetXPx by remember { mutableFloatStateOf(0f) }
	var activeMemoryYearGroup by remember { mutableStateOf<com.nasdrop.MemoryYearGroup?>(null) }

	Column(modifier = Modifier.fillMaxSize()) {
		// 1. 顶部子相册分类横滑条（高质感 34dp 统一高度胶囊，彻底解决高度跳动）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// 位置 1: "全部相册" 胶囊
			item {
				val isFolders = selectedAlbumPath == "__ALL_FOLDERS__"
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = if (isFolders) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isFolders) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isFolders) 2.dp else 0.dp,
					onClick = { onSelectAlbum("__ALL_FOLDERS__") },
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 11.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(
							Icons.Rounded.FolderCopy,
							contentDescription = null,
							tint = if (isFolders) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"全部相册",
							fontSize = 12.sp,
							fontWeight = if (isFolders) FontWeight.Bold else FontWeight.Medium,
							color = if (isFolders) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			// 位置 2: "全部照片" 胶囊（紧凑精炼，不占首屏）
			item {
				val isAll = selectedAlbumPath.isBlank()
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = if (isAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isAll) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isAll) 2.dp else 0.dp,
					onClick = { onSelectAlbum("") },
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 11.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(
							Icons.Rounded.PhotoLibrary,
							contentDescription = null,
							tint = if (isAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"全部照片",
							fontSize = 12.sp,
							fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
							color = if (isAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			// 位置 3..N: 用户创建的子相册（长按按住直接横向拖动滑块换位置；尾部三个点轻量菜单）
			itemsIndexed(displayAlbums, key = { _, it -> it.path }) { index, album ->
				val isSelected = selectedAlbumPath == album.path
				var menuOpen by remember { mutableStateOf(false) }
				val isDragging = draggingIndex == index

				Surface(
					modifier = Modifier
						.height(34.dp)
						.zIndex(if (isDragging) 10f else 1f)
						.graphicsLayer {
							if (isDragging) {
								translationX = dragOffsetXPx
								scaleX = 1.08f
								scaleY = 1.08f
								shadowElevation = 8.dp.toPx()
							}
						}
						.pointerInput(album.path) {
							detectDragGesturesAfterLongPress(
								onDragStart = {
									draggingIndex = index
									dragOffsetXPx = 0f
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								},
								onDrag = { change, dragAmount ->
									change.consume()
									dragOffsetXPx += dragAmount.x
									val curr = draggingIndex ?: return@detectDragGesturesAfterLongPress
									val list = currentDisplayAlbums
									val stepPx = 70.dp.toPx()
									if (dragOffsetXPx > stepPx && curr < list.size - 1) {
										val fromPath = list[curr].path
										val toPath = list[curr + 1].path
										onReorderAlbums(fromPath, toPath)
										draggingIndex = curr + 1
										dragOffsetXPx -= stepPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									} else if (dragOffsetXPx < -stepPx && curr > 0) {
										val fromPath = list[curr].path
										val toPath = list[curr - 1].path
										onReorderAlbums(fromPath, toPath)
										draggingIndex = curr - 1
										dragOffsetXPx += stepPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									}
								},
								onDragEnd = {
									draggingIndex = null
									dragOffsetXPx = 0f
								},
								onDragCancel = {
									draggingIndex = null
									dragOffsetXPx = 0f
								}
							)
						}
						.clickable { onSelectAlbum(album.path) },
					shape = RoundedCornerShape(17.dp),
					color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isSelected) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isDragging) 6.dp else if (isSelected) 2.dp else 0.dp,
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(start = 10.dp, end = 3.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						FileIconByEntry(
							entry = album,
							customIconKey = folderIcons[album.path],
							size = 15.dp,
							urlOf = urlOf,
							directUrlOf = directUrlOf,
						)
						Text(
							album.name,
							fontSize = 12.sp,
							fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
							color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.widthIn(max = 130.dp)
						)

						// 胶囊末尾三点按钮（轻量 Box 容器，绝无 M3 48dp 扩张）
						Box(
							modifier = Modifier
								.size(24.dp)
								.clip(CircleShape)
								.clickable { menuOpen = true },
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Rounded.MoreVert,
								contentDescription = "相册选项",
								tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(15.dp)
							)
							DropdownMenu(
								expanded = menuOpen,
								onDismissRequest = { menuOpen = false },
							) {
								val isDefault = album.name == "默认相册" || album.path == "Photos/默认相册" || album.path.endsWith("/默认相册")
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("重命名相册") },
										onClick = { menuOpen = false; onRenameAlbum(album) },
										leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
									)
								}
								DropdownMenuItem(
									text = { Text("更换图标") },
									onClick = { menuOpen = false; onChangeIcon(album) },
									leadingIcon = { Icon(Icons.Rounded.Edit, null) },
								)
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("删除相册", color = MaterialTheme.colorScheme.error) },
										onClick = { menuOpen = false; onDeleteAlbum(album) },
										leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
									)
								}
							}
						}
					}
				}
			}

			// 尾部新建专属相册快捷胶囊
			item {
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
					onClick = onCreateAlbum,
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(
							Icons.Rounded.Add,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"新建相册",
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.primary
						)
					}
				}
			}
		}

		if (mediaFilterType != MediaFilterType.All) {
			Surface(
				shape = RoundedCornerShape(10.dp),
				color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 14.dp, vertical = 4.dp),
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(
							imageVector = Icons.Rounded.Tune,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(16.dp),
						)
						Text(
							text = "当前筛选：${mediaFilterType.label} (${currentPhotos.size})",
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					Surface(
						shape = RoundedCornerShape(6.dp),
						color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
						modifier = Modifier.clickable { onClearFilter() },
					) {
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(2.dp),
							modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
						) {
							Icon(
								imageVector = Icons.Rounded.Close,
								contentDescription = "清除",
								modifier = Modifier.size(12.dp),
								tint = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Text(
								text = "清除筛选",
								fontSize = 10.5.sp,
								fontWeight = FontWeight.Medium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
					}
				}
			}
		}

		if (selectedAlbumPath == "__ALL_FOLDERS__") {
			AlbumFoldersGrid(
				allAlbums = allAlbums,
				allPhotos = allPhotos,
				folderIcons = folderIcons,
				isRefreshing = isRefreshing,
				onRefresh = onRefresh,
				onSelectAlbum = onSelectAlbum,
				onRenameAlbum = onRenameAlbum,
				onChangeIcon = onChangeIcon,
				onDeleteAlbum = onDeleteAlbum,
				onReorderAlbums = onReorderAlbums,
				onCreateAlbum = onCreateAlbum,
				urlOf = urlOf,
				directUrlOf = directUrlOf,
			)
		} else {
			if (selectedAlbumPath.isNotBlank()) {
				val currentAlbum = allAlbums.firstOrNull { it.path == selectedAlbumPath }
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 2.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						"当前相册：${currentAlbum?.name ?: "自定义相册"}",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
					)
					Spacer(Modifier.weight(1f))
					Text(
						"点击标签末尾 ··· 可管理相册",
						style = MaterialTheme.typography.labelSmall,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
					)
				}
			}

			// 2. 照片内容展示（外层包裹 PullToRefreshBox，确保空相册同样支持下拉刷新）
			PullToRefreshBox(
				isRefreshing = isRefreshing,
				onRefresh = onRefresh,
				modifier = Modifier.fillMaxSize(),
			) {
				if (groups.isEmpty()) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.verticalScroll(rememberScrollState()),
						contentAlignment = Alignment.Center,
					) {
						Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
							FileIcon(category = FileCategory.Image, isDir = false, size = 56.dp)
							Text("当前相册暂无照片", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							Text("点击上传按钮，添加照片到当前相册", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
				} else {
					var isScrubbing by remember { mutableStateOf(false) }
					var scrubFraction by remember { mutableFloatStateOf(0f) }
					var currentScrubGroup by remember { mutableStateOf<com.nasdrop.TimelineGroup?>(null) }
					var scrubberTrackHeightPx by remember { mutableFloatStateOf(1f) }

					Box(modifier = Modifier.fillMaxSize()) {
						LazyVerticalGrid(
							state = state,
							columns = GridCells.Fixed(3),
							contentPadding = PaddingValues(start = 4.dp, end = 26.dp, top = 4.dp, bottom = 88.dp),
							horizontalArrangement = Arrangement.spacedBy(3.dp),
							verticalArrangement = Arrangement.spacedBy(3.dp),
							modifier = Modifier
								.fillMaxSize()
								.dragSelectablePhotoGrid(
									state = state,
									allPhotos = currentPhotos,
									isMulti = isMulti,
									selectedPaths = selectedPaths,
									onEnterMultiSelect = onEnterMultiSelect,
									onUpdateSelectionPaths = { onUpdateSelectionPaths?.invoke(it) },
								),
						) {
							groups.forEach { group ->
								item(span = { GridItemSpan(3) }) {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 10.dp, vertical = 6.dp),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween,
									) {
										Row(
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.spacedBy(8.dp),
										) {
											Text(group.dateTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
											Text("${group.count}张", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
										}
										if (isMulti) {
											val isAllGroupSelected = group.items.isNotEmpty() && group.items.all { selectedPaths.contains(it.path) }
											Surface(
												shape = RoundedCornerShape(12.dp),
												color = if (isAllGroupSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
												onClick = {
													onToggleSelectGroup?.invoke(group, !isAllGroupSelected)
												},
											) {
												Text(
													text = if (isAllGroupSelected) "取消全选" else "全选",
													fontSize = 12.sp,
													fontWeight = FontWeight.Bold,
													color = if (isAllGroupSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
													modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
												)
											}
										}
									}
								}

								items(group.items, key = { it.path }) { item ->
									val isSelected = selectedPaths.contains(item.path)
									Box(
										modifier = Modifier
											.aspectRatio(1f)
											.clip(RoundedCornerShape(6.dp))
											.background(MaterialTheme.colorScheme.outlineVariant)
											.combinedClickable(
												onClick = {
													if (isMulti) {
														onToggleSelect(item)
													} else {
														if (item.isVideo) {
															onOpenVideo(item)
														} else {
															onOpenImage(item)
														}
													}
												},
												onLongClick = {
													if (!isMulti) {
														onEnterMultiSelect(item)
													} else {
														onToggleSelect(item)
													}
												},
											),
									) {
										val thumbTarget = urlOf(item.path)
										var thumbFailed by remember(item.path) { mutableStateOf(false) }
										val actualData = if (!thumbFailed) thumbTarget else null
										if (!actualData.isNullOrBlank()) {
											AsyncImage(
												model = ImageRequest.Builder(LocalContext.current)
													.data(actualData)
													.memoryCacheKey("thumb:${item.path.trim('/')}")
													.diskCacheKey("thumb:${item.path.trim('/')}")
													.size(240, 240)
													.precision(coil.size.Precision.INEXACT)
													.allowRgb565(true)
													.crossfade(false)
													.build(),
												onError = {
													thumbFailed = true
													onThumbnailError?.invoke(item.path)
												},
												contentDescription = item.name,
												contentScale = ContentScale.Crop,
												modifier = Modifier.fillMaxSize(),
											)
										} else {
											Box(
												modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
												contentAlignment = Alignment.Center,
											) {
												FileIcon(category = if (item.isVideo) FileCategory.Video else FileCategory.Image, isDir = false, size = 32.dp)
											}
										}

										// 媒体角标覆盖层（麦克风伴随语、文字备忘、视频指示与时长）
										MediaThumbBadges(
											entry = item,
											modifier = Modifier.fillMaxSize().padding(3.dp),
											compact = false,
										)

										// 多选选中时显示精致主题色边框与半透明遮罩
										if (isMulti && isSelected) {
											Box(
												modifier = Modifier
													.fillMaxSize()
													.border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
													.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
											)
										}

										// 仅在多选模式下显示右上角勾选框
										if (isMulti) {
											Box(
												modifier = Modifier
													.align(Alignment.TopEnd)
													.padding(6.dp)
											) {
												SelectCheckbox(isSelected = isSelected)
											}
										}
									}
								}
							}
						}

						// 拖动时间轴时悬浮在右侧的时间胶囊（避免 NPE）
						if (isScrubbing && currentScrubGroup != null) {
							Surface(
								shape = RoundedCornerShape(20.dp),
								color = MaterialTheme.colorScheme.inverseSurface,
								tonalElevation = 6.dp,
								shadowElevation = 8.dp,
								border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
								modifier = Modifier
									.align(Alignment.TopEnd)
									.padding(end = 46.dp)
									.offset {
										val maxTravel = (scrubberTrackHeightPx - 44.dp.toPx()).coerceAtLeast(0f)
										IntOffset(
											x = 0,
											y = (scrubFraction * maxTravel + 20.dp.toPx()).roundToInt().coerceAtLeast(0)
										)
									}
							) {
								Row(
									modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Icon(
										Icons.Rounded.DateRange,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.inverseOnSurface,
										modifier = Modifier.size(17.dp),
									)
									Text(
										text = "${currentScrubGroup?.dateTitle ?: ""} · ${currentScrubGroup?.count ?: 0}张",
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.inverseOnSurface,
									)
								}
							}
						}

						// 右侧竖向时间轴快捷滑动条
						TimelineDateScrubber(
							state = state,
							groups = groups,
							onScrubStateChanged = { dragging, fraction, group, heightPx ->
								isScrubbing = dragging
								scrubFraction = fraction
								currentScrubGroup = group
								scrubberTrackHeightPx = heightPx
							},
							modifier = Modifier
								.align(Alignment.CenterEnd)
								.fillMaxHeight()
								.padding(end = 2.dp, top = 8.dp, bottom = 96.dp),
						)
					}
				}
			}
		}
	}

	if (activeMemoryYearGroup != null) {
		MemoryDetailModalSheet(
			yearGroup = activeMemoryYearGroup!!,
			onDismiss = { activeMemoryYearGroup = null },
			onSelectAlbum = {
				activeMemoryYearGroup = null
				onSelectAlbum(it)
			},
			onOpenImage = onOpenImage,
			onOpenVideo = onOpenVideo,
			urlOf = urlOf,
			directUrlOf = directUrlOf,
		)
	}
}

/** 全部文件夹网格展示 */
@Composable
private fun AlbumFoldersGrid(
	allAlbums: List<DavEntry>,
	allPhotos: List<DavEntry>,
	folderIcons: Map<String, String>,
	isRefreshing: Boolean,
	onRefresh: () -> Unit,
	onSelectAlbum: (String) -> Unit,
	onRenameAlbum: (DavEntry) -> Unit,
	onChangeIcon: (DavEntry) -> Unit,
	onDeleteAlbum: (DavEntry) -> Unit,
	onReorderAlbums: (fromPath: String, toPath: String) -> Unit = { _, _ -> },
	onCreateAlbum: () -> Unit,
	urlOf: (String) -> String? = { null },
	directUrlOf: ((String) -> String?)? = null,
	modifier: Modifier = Modifier,
) {
	val validAlbums = remember(allAlbums) {
		allAlbums.filterNot {
			it.name.startsWith(".") || it.path.contains(".thumbnails") ||
				it.name == "默认影集" || it.name == "默认影音" ||
				it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
				it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
		}
	}
	var draggingAlbumPath by remember { mutableStateOf<String?>(null) }
	var dragOffsetXPx by remember { mutableFloatStateOf(0f) }
	var dragOffsetYPx by remember { mutableFloatStateOf(0f) }
	val haptic = LocalHapticFeedback.current
	val currentValidAlbums by rememberUpdatedState(validAlbums)

	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = modifier.fillMaxSize(),
	) {
		LazyVerticalGrid(
			columns = GridCells.Fixed(2),
			contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 88.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier.fillMaxSize(),
		) {
			item(key = "__CREATE_ALBUM__") {
				Card(
					shape = RoundedCornerShape(14.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
					border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(14.dp))
						.clickable { onCreateAlbum() },
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(10.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Box(
							modifier = Modifier
								.size(42.dp)
								.clip(RoundedCornerShape(10.dp))
								.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Rounded.Add,
								contentDescription = "新建相册",
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(24.dp),
							)
						}
						Spacer(modifier = Modifier.width(10.dp))
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.Center,
						) {
							Text(
								text = "新建相册",
								fontSize = 13.5.sp,
								fontWeight = FontWeight.Bold,
								color = MaterialTheme.colorScheme.primary,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = "创建专属相册",
								fontSize = 11.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
					}
				}
			}

			items(validAlbums, key = { it.path }) { album ->
				var menuOpen by remember { mutableStateOf(false) }
				val photoCount = remember(album.path, allPhotos) {
					allPhotos.count { it.path.startsWith(album.path + "/") }
				}
				val customKey = folderIcons[album.path]
				val coverPhoto = remember(album.path, allPhotos, customKey) {
					if (customKey.isNullOrBlank()) {
						allPhotos.firstOrNull { it.path.startsWith(album.path.trim('/') + "/") }
					} else null
				}
				val isDragging = draggingAlbumPath == album.path
				Card(
					shape = RoundedCornerShape(14.dp),
					colors = CardDefaults.cardColors(
						containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
					),
					border = BorderStroke(
						if (isDragging) 1.5.dp else 1.dp,
						if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
					),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(14.dp))
						.graphicsLayer {
							if (isDragging) {
								translationX = dragOffsetXPx
								translationY = dragOffsetYPx
								scaleX = 1.05f
								scaleY = 1.05f
								shadowElevation = 8.dp.toPx()
							}
						}
						.pointerInput(album.path) {
							detectDragGesturesAfterLongPress(
								onDragStart = {
									draggingAlbumPath = album.path
									dragOffsetXPx = 0f
									dragOffsetYPx = 0f
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								},
								onDrag = { change, dragAmount ->
									change.consume()
									dragOffsetXPx += dragAmount.x
									dragOffsetYPx += dragAmount.y
									val currPath = draggingAlbumPath ?: return@detectDragGesturesAfterLongPress
									val list = currentValidAlbums
									val curr = list.indexOfFirst { it.path == currPath }
									if (curr < 0) return@detectDragGesturesAfterLongPress

									val stepXPx = 100.dp.toPx()
									val stepYPx = 60.dp.toPx()

									if (dragOffsetXPx > stepXPx && curr < list.size - 1) {
										val fromPath = list[curr].path
										val toPath = list[curr + 1].path
										onReorderAlbums(fromPath, toPath)
										dragOffsetXPx -= stepXPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									} else if (dragOffsetXPx < -stepXPx && curr > 0) {
										val fromPath = list[curr].path
										val toPath = list[curr - 1].path
										onReorderAlbums(fromPath, toPath)
										dragOffsetXPx += stepXPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									} else if (dragOffsetYPx > stepYPx && curr + 2 < list.size) {
										val fromPath = list[curr].path
										val toPath = list[curr + 2].path
										onReorderAlbums(fromPath, toPath)
										dragOffsetYPx -= stepYPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									} else if (dragOffsetYPx < -stepYPx && curr - 2 >= 0) {
										val fromPath = list[curr].path
										val toPath = list[curr - 2].path
										onReorderAlbums(fromPath, toPath)
										dragOffsetYPx += stepYPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									}
								},
								onDragEnd = {
									draggingAlbumPath = null
									dragOffsetXPx = 0f
									dragOffsetYPx = 0f
								},
								onDragCancel = {
									draggingAlbumPath = null
									dragOffsetXPx = 0f
									dragOffsetYPx = 0f
								}
							)
						}
						.clickable { onSelectAlbum(album.path) },
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(10.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						if (coverPhoto != null) {
							FileThumbnail(
								entry = coverPhoto,
								urlOf = urlOf,
								directUrlOf = directUrlOf,
								size = 42.dp,
							)
						} else {
							FileIconByEntry(
								entry = album,
								customIconKey = customKey,
								size = 42.dp,
								urlOf = urlOf,
								directUrlOf = directUrlOf,
							)
						}
						Spacer(modifier = Modifier.width(10.dp))
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.Center,
						) {
							Text(
								text = album.name,
								fontSize = 13.5.sp,
								fontWeight = FontWeight.SemiBold,
								color = MaterialTheme.colorScheme.onSurface,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = "$photoCount 张照片",
								fontSize = 11.5.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}
						Box {
							Box(
								modifier = Modifier
									.size(26.dp)
									.clip(CircleShape)
									.clickable { menuOpen = true },
								contentAlignment = Alignment.Center,
							) {
								Icon(
									Icons.Rounded.MoreVert,
									contentDescription = "更多",
									tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
									modifier = Modifier.size(16.dp)
								)
							}
							DropdownMenu(
								expanded = menuOpen,
								onDismissRequest = { menuOpen = false },
							) {
								val isDefault = album.name == "默认相册" || album.path == "Photos/默认相册" || album.path.endsWith("/默认相册")
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("重命名相册") },
										onClick = { menuOpen = false; onRenameAlbum(album) },
										leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
									)
								}
								DropdownMenuItem(
									text = { Text("更换图标") },
									onClick = { menuOpen = false; onChangeIcon(album) },
									leadingIcon = { Icon(Icons.Rounded.Edit, null) },
								)
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("删除相册", color = MaterialTheme.colorScheme.error) },
										onClick = { menuOpen = false; onDeleteAlbum(album) },
										leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
									)
								}
							}
						}
					}
				}
			}
		}
	}
}

private data class ScrubberMilestone(
	val groupIndex: Int,
	val label: String,
	val fraction: Float,
)

/**
 * 相册右侧快捷滑动竖向时间轴。
 * 1. 动态展示时间刻度（如近期、11月、10月、2025等清晰刻度标签与圆点）；
 * 2. 支持手指按压、上下快速拖动，实时平滑跳转到对应日期的照片分组；
 * 3. 拖动时通知父容器弹出全局单行时间气泡与震动触感反馈；
 * 4. 正常滚动时指示器滑块自动联动同步当前屏幕所在日期。
 */
@Composable
private fun TimelineDateScrubber(
	state: LazyGridState,
	groups: List<com.nasdrop.TimelineGroup>,
	onScrubStateChanged: (dragging: Boolean, fraction: Float, group: com.nasdrop.TimelineGroup, heightPx: Float) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (groups.isEmpty()) return

	val coroutineScope = rememberCoroutineScope()
	val haptic = LocalHapticFeedback.current

	// 计算每个 group 在 LazyVerticalGrid 里的全局起始 item index
	val groupStartIndices = remember(groups) {
		val list = ArrayList<Int>(groups.size)
		var currentIdx = 0
		for (g in groups) {
			list.add(currentIdx)
			currentIdx += 1 + g.items.size // 1 对应 item(header) + 该组内图片数量
		}
		list
	}

	// 智能提取时间轴刻度（月份在上面，所属年份在底部收尾；按倒序时间流分布）
	val milestones = remember(groups) {
		val result = mutableListOf<ScrubberMilestone>()
		val total = groups.size

		// 预先定位每个年份在倒序列表中的最底端组（即该年最早的一组）
		val yearBottomIndices = mutableMapOf<String, Int>()
		for (i in groups.indices) {
			val title = groups[i].dateTitle
			if (title.contains("-")) {
				val y = title.split("-").getOrNull(0) ?: ""
				if (y.isNotEmpty()) {
					yearBottomIndices[y] = i
				}
			}
		}

		var lastMonth = ""
		var hasRecent = false

		for (i in groups.indices) {
			val title = groups[i].dateTitle
			val tag: String? = when {
				title == "今天" || title == "昨天" -> {
					if (!hasRecent) {
						hasRecent = true
						"近期"
					} else null
				}
				title == "更早之前" -> {
					if (result.none { it.label == "更早" }) "更早" else null
				}
				title.contains("-") -> {
					val parts = title.split("-")
					val y = parts.getOrNull(0) ?: ""
					val m = parts.getOrNull(1)?.toIntOrNull()?.toString() ?: parts.getOrNull(1) ?: ""
					if (y.isNotEmpty()) {
						// 倒序时间轴：各月份在上方，而所属年份标在该年最底部的收尾组上
						if (i == yearBottomIndices[y]) {
							"${y.takeLast(2)}年"
						} else if (m.isNotEmpty() && m != lastMonth) {
							lastMonth = m
							"${m}月"
						} else {
							null
						}
					} else null
				}
				else -> {
					val short = title.take(4)
					if (result.none { it.label == short }) short else null
				}
			}

			if (tag != null) {
				val frac = if (total > 1) i.toFloat() / (total - 1) else 0f
				result.add(ScrubberMilestone(i, tag, frac))
			}
		}

		if (result.size > 6) {
			val sampled = mutableSetOf<ScrubberMilestone>()
			// 1. 首尾里程碑保留
			sampled.add(result.first())
			sampled.add(result.last())
			// 2. 所有年份锚点（如 "26年"、"25年"）必须强制保留，绝不能因下采样漏掉
			sampled.addAll(result.filter { it.label.endsWith("年") })

			// 3. 按均匀步长补充中间月份刻度
			val step = (result.size - 1) / 5.0
			for (k in 1..4) {
				val idx = (k * step).roundToInt().coerceIn(0, result.size - 1)
				sampled.add(result[idx])
			}
			sampled.sortedBy { it.groupIndex }
		} else {
			result
		}
	}

	var isDragging by remember { mutableStateOf(false) }
	var dragFraction by remember { mutableFloatStateOf(0f) }
	var activeGroupIdx by remember { mutableIntStateOf(0) }
	var trackHeightPx by remember { mutableFloatStateOf(1f) }

	// 联动计算：未拖动时跟随列表当前首个可见 item 对应的 group
	val visibleGroupIdx by remember(groups, state.firstVisibleItemIndex) {
		derivedStateOf {
			val first = state.firstVisibleItemIndex
			var matched = 0
			for (i in groupStartIndices.indices) {
				if (groupStartIndices[i] <= first) {
					matched = i
				} else {
					break
				}
			}
			matched
		}
	}

	val currentSelectedIdx = if (isDragging) activeGroupIdx else visibleGroupIdx
	val currentGroup = groups.getOrNull(currentSelectedIdx) ?: groups.first()

	Box(
		modifier = modifier
			.width(42.dp)
			.onSizeChanged { size ->
				if (size.height > 0) trackHeightPx = size.height.toFloat()
			}
			.pointerInput(groups) {
				awaitEachGesture {
					val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
					down.consume()
					isDragging = true
					var currentY = down.position.y
					val f0 = (currentY / trackHeightPx).coerceIn(0f, 1f)
					dragFraction = f0
					val target0 = (f0 * (groups.size - 1)).roundToInt().coerceIn(0, groups.size - 1)
					activeGroupIdx = target0
					onScrubStateChanged(true, f0, groups[target0], trackHeightPx)
					haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
					coroutineScope.launch {
						state.scrollToItem(groupStartIndices[target0])
					}
					while (true) {
						val event = awaitPointerEvent(pass = PointerEventPass.Initial)
						val change = event.changes.firstOrNull { it.id == down.id } ?: break
						if (!change.pressed) break
						change.consume()
						currentY = change.position.y
						val f = (currentY / trackHeightPx).coerceIn(0f, 1f)
						dragFraction = f
						val target = (f * (groups.size - 1)).roundToInt().coerceIn(0, groups.size - 1)
						if (target != activeGroupIdx) {
							activeGroupIdx = target
							haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							coroutineScope.launch {
								state.scrollToItem(groupStartIndices[target])
							}
						}
						onScrubStateChanged(true, f, groups[target], trackHeightPx)
					}
					isDragging = false
					onScrubStateChanged(false, dragFraction, currentGroup, trackHeightPx)
				}
			},
		contentAlignment = Alignment.CenterEnd,
	) {
		// 1. 竖向轨道细线与刻度点/文字
		Box(
			modifier = Modifier
				.fillMaxHeight()
				.fillMaxWidth(),
			contentAlignment = Alignment.CenterEnd,
		) {
			// 背景细轨道线 (位于右侧 5.dp 处)
			Box(
				modifier = Modifier
					.padding(end = 5.dp)
					.width(3.dp)
					.fillMaxHeight()
					.background(
						color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
						shape = RoundedCornerShape(1.5.dp),
					)
			)

			// 时间刻度里程碑（文字标签 + 刻度圆点，清晰展示时间轴上的时间分布）
			milestones.forEach { m ->
				Row(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.offset {
							val yOffset = ((trackHeightPx - 20.dp.toPx()).coerceAtLeast(0f) * m.fraction).roundToInt()
							IntOffset(x = 0, y = yOffset)
						}
						.padding(end = 10.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(3.dp),
				) {
					Text(
						text = m.label,
						fontSize = 9.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
					)
					Box(
						modifier = Modifier
							.size(3.5.dp)
							.background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
					)
				}
			}

			// 随当前位置滑动的圆润指示胶囊
			val targetFraction = if (isDragging) dragFraction else (currentSelectedIdx.toFloat() / (groups.size - 1).coerceAtLeast(1))
			val animatedFraction by animateFloatAsState(
				targetValue = targetFraction.coerceIn(0f, 1f),
				animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
				label = "scrubber_fraction"
			)

			Box(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(end = 3.dp)
					.offset {
						val maxTravel = (trackHeightPx - 24.dp.toPx()).coerceAtLeast(0f)
						IntOffset(x = 0, y = (animatedFraction * maxTravel).roundToInt())
					}
					.size(width = if (isDragging) 9.dp else 7.dp, height = if (isDragging) 28.dp else 20.dp)
					.background(
						color = MaterialTheme.colorScheme.primary,
						shape = RoundedCornerShape(4.dp),
					),
				contentAlignment = Alignment.Center,
			) {
				Box(
					modifier = Modifier
						.width(2.5.dp)
						.height(8.dp)
						.background(MaterialTheme.colorScheme.onPrimary, CircleShape)
				)
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaStreamContent(
	state: LazyGridState,
	videos: List<DavEntry>,
	groups: List<com.nasdrop.TimelineGroup> = emptyList(),
	allMediaFolders: List<DavEntry>,
	allVideos: List<DavEntry> = emptyList(),
	selectedMediaPath: String,
	folderIcons: Map<String, String> = emptyMap(),
	isRefreshing: Boolean = false,
	onRefresh: () -> Unit = {},
	isMulti: Boolean = false,
	selectedPaths: Set<String> = emptySet(),
	onToggleSelect: (DavEntry) -> Unit = {},
	onEnterMultiSelect: (DavEntry) -> Unit = {},
	onUpdateSelectionPaths: ((Set<String>) -> Unit)? = null,
	onToggleSelectGroup: ((com.nasdrop.TimelineGroup, Boolean) -> Unit)? = null,
	onOpenAction: (DavEntry) -> Unit = {},
	onSelectMediaFolder: (String) -> Unit,
	onRenameMediaFolder: (DavEntry) -> Unit = {},
	onChangeIcon: (DavEntry) -> Unit = {},
	onDeleteMediaFolder: (DavEntry) -> Unit = {},
	onReorderMediaFolders: (fromPath: String, toPath: String) -> Unit = { _, _ -> },
	onCreateMediaFolder: () -> Unit,
	urlOf: (String) -> String?,
	directUrlOf: ((String) -> String?)? = null,
	onThumbnailError: ((String) -> Unit)? = null,
	onOpenVideo: (DavEntry) -> Unit,
) {
	val displayMediaFolders = remember(allMediaFolders) {
		val visible = allMediaFolders.filterNot { it.name.startsWith(".") || it.path.contains(".thumbnails") }
		val custom = visible.filterNot { it.name == "默认影集" || it.name == "默认影音" || it.path == "movies/默认影集" || it.path == "movies/默认影音" }
		val defaults = visible.filter { it.name == "默认影集" || it.name == "默认影音" || it.path == "movies/默认影集" || it.path == "movies/默认影音" }
		custom + defaults
	}
	val currentDisplayMediaFolders by rememberUpdatedState(displayMediaFolders)
	val haptic = LocalHapticFeedback.current
	var draggingIndex by remember { mutableStateOf<Int?>(null) }
	var dragOffsetXPx by remember { mutableFloatStateOf(0f) }

	Column(modifier = Modifier.fillMaxSize()) {
		// 1. 顶部影集/影视分类横滑栏（高质感 34dp 统一高度胶囊，彻底解决高度跳动）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// 位置 1: "全部影集" 胶囊
			item {
				val isFolders = selectedMediaPath == "__ALL_FOLDERS__"
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = if (isFolders) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isFolders) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isFolders) 2.dp else 0.dp,
					onClick = { onSelectMediaFolder("__ALL_FOLDERS__") },
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 11.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(
							Icons.Rounded.VideoLibrary,
							contentDescription = null,
							tint = if (isFolders) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"全部影集",
							fontSize = 12.sp,
							fontWeight = if (isFolders) FontWeight.Bold else FontWeight.Medium,
							color = if (isFolders) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			// 位置 2: "全部影视" 胶囊（紧凑精炼，不占首屏）
			item {
				val isAll = selectedMediaPath.isBlank()
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = if (isAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isAll) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isAll) 2.dp else 0.dp,
					onClick = { onSelectMediaFolder("") },
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 11.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(
							Icons.Rounded.Movie,
							contentDescription = null,
							tint = if (isAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"全部影视",
							fontSize = 12.sp,
							fontWeight = if (isAll) FontWeight.Bold else FontWeight.Medium,
							color = if (isAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
						)
					}
				}
			}

			// 位置 3..N: 各个影集分类胶囊（长按按住直接横向拖动滑块换位置；尾部三个点轻量菜单）
			itemsIndexed(displayMediaFolders, key = { _, it -> it.path }) { index, folder ->
				val isSelected = selectedMediaPath == folder.path
				var menuOpen by remember { mutableStateOf(false) }
				val isDragging = draggingIndex == index

				Surface(
					modifier = Modifier
						.height(34.dp)
						.zIndex(if (isDragging) 10f else 1f)
						.graphicsLayer {
							if (isDragging) {
								translationX = dragOffsetXPx
								scaleX = 1.08f
								scaleY = 1.08f
								shadowElevation = 8.dp.toPx()
							}
						}
						.pointerInput(folder.path) {
							detectDragGesturesAfterLongPress(
								onDragStart = {
									draggingIndex = index
									dragOffsetXPx = 0f
									haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								},
								onDrag = { change, dragAmount ->
									change.consume()
									dragOffsetXPx += dragAmount.x
									val curr = draggingIndex ?: return@detectDragGesturesAfterLongPress
									val list = currentDisplayMediaFolders
									val stepPx = 70.dp.toPx()
									if (dragOffsetXPx > stepPx && curr < list.size - 1) {
										val fromPath = list[curr].path
										val toPath = list[curr + 1].path
										onReorderMediaFolders(fromPath, toPath)
										draggingIndex = curr + 1
										dragOffsetXPx -= stepPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									} else if (dragOffsetXPx < -stepPx && curr > 0) {
										val fromPath = list[curr].path
										val toPath = list[curr - 1].path
										onReorderMediaFolders(fromPath, toPath)
										draggingIndex = curr - 1
										dragOffsetXPx += stepPx
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
									}
								},
								onDragEnd = {
									draggingIndex = null
									dragOffsetXPx = 0f
								},
								onDragCancel = {
									draggingIndex = null
									dragOffsetXPx = 0f
								}
							)
						}
						.clickable { onSelectMediaFolder(folder.path) },
					shape = RoundedCornerShape(17.dp),
					color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
					border = BorderStroke(
						1.dp,
						if (isSelected) Color.Transparent
						else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
					),
					shadowElevation = if (isDragging) 6.dp else if (isSelected) 2.dp else 0.dp,
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(start = 10.dp, end = 3.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						FileIconByEntry(
							entry = folder,
							customIconKey = folderIcons[folder.path],
							size = 15.dp,
							urlOf = urlOf,
							directUrlOf = directUrlOf,
						)
						Text(
							folder.name,
							fontSize = 12.sp,
							fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
							color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							modifier = Modifier.widthIn(max = 130.dp)
						)

						// 胶囊末尾三点按钮（轻量 Box 容器，绝无 M3 48dp 扩张）
						Box(
							modifier = Modifier
								.size(24.dp)
								.clip(CircleShape)
								.clickable { menuOpen = true },
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Rounded.MoreVert,
								contentDescription = "影集选项",
								tint = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
								modifier = Modifier.size(15.dp)
							)
							DropdownMenu(
								expanded = menuOpen,
								onDismissRequest = { menuOpen = false },
							) {
								val isDefault = folder.name == "默认影集" || folder.name == "默认影音" || folder.path == "movies/默认影集" || folder.path == "movies/默认影音" || folder.path.endsWith("/默认影集") || folder.path.endsWith("/默认影音")
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("重命名影集") },
										onClick = { menuOpen = false; onRenameMediaFolder(folder) },
										leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
									)
								}
								DropdownMenuItem(
									text = { Text("更换图标") },
									onClick = { menuOpen = false; onChangeIcon(folder) },
									leadingIcon = { Icon(Icons.Rounded.Edit, null) },
								)
								if (!isDefault) {
									DropdownMenuItem(
										text = { Text("删除影集", color = MaterialTheme.colorScheme.error) },
										onClick = { menuOpen = false; onDeleteMediaFolder(folder) },
										leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
									)
								}
							}
						}
					}
				}
			}

			// 尾部新建影集快捷胶囊
			item {
				Surface(
					modifier = Modifier.height(34.dp),
					shape = RoundedCornerShape(17.dp),
					color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
					onClick = onCreateMediaFolder,
				) {
					Row(
						modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(
							Icons.Rounded.Add,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(15.dp)
						)
						Text(
							"新建影集",
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
							color = MaterialTheme.colorScheme.primary
						)
					}
				}
			}
		}

		if (selectedMediaPath == "__ALL_FOLDERS__") {
			MediaFoldersGrid(
				allMediaFolders = allMediaFolders,
				allVideos = allVideos,
				folderIcons = folderIcons,
				isRefreshing = isRefreshing,
				onRefresh = onRefresh,
				onSelectMediaFolder = onSelectMediaFolder,
				onRenameMediaFolder = onRenameMediaFolder,
				onChangeIcon = onChangeIcon,
				onDeleteMediaFolder = onDeleteMediaFolder,
				onCreateMediaFolder = onCreateMediaFolder,
				urlOf = urlOf,
				directUrlOf = directUrlOf,
			)
		} else {
			if (selectedMediaPath.isNotBlank()) {
				val currentFolder = allMediaFolders.firstOrNull { it.path == selectedMediaPath }
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 2.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						"当前影集：${currentFolder?.name ?: "自定义影集"}",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.primary,
					)
					Spacer(Modifier.weight(1f))
					Text(
						"点击标签末尾 ··· 可管理分类",
						style = MaterialTheme.typography.labelSmall,
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
					)
				}
			}

			// 2. 视频内容展示（带下拉刷新包裹，确保各类状态均支持下拉刷新）
			PullToRefreshBox(
				isRefreshing = isRefreshing,
				onRefresh = onRefresh,
				modifier = Modifier.fillMaxSize(),
			) {
				if (videos.isEmpty()) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.verticalScroll(rememberScrollState()),
						contentAlignment = Alignment.Center,
					) {
						Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
							FileIcon(category = FileCategory.Video, isDir = false, size = 56.dp)
							Text("当前分类暂无视频", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							Text("点击上传按钮，添加视频到当前影集", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}
				} else {
					var isScrubbing by remember { mutableStateOf(false) }
					var scrubFraction by remember { mutableFloatStateOf(0f) }
					var currentScrubGroup by remember { mutableStateOf<com.nasdrop.TimelineGroup?>(null) }
					var scrubberTrackHeightPx by remember { mutableFloatStateOf(1f) }

					Box(modifier = Modifier.fillMaxSize()) {
						LazyVerticalGrid(
							state = state,
							columns = GridCells.Fixed(2),
							contentPadding = PaddingValues(start = 12.dp, end = if (groups.isNotEmpty()) 28.dp else 12.dp, top = 6.dp, bottom = 88.dp),
							horizontalArrangement = Arrangement.spacedBy(10.dp),
							verticalArrangement = Arrangement.spacedBy(10.dp),
							modifier = Modifier
								.fillMaxSize()
								.dragSelectableGrid(
									state = state,
									allKeys = videos.map { it.path },
									isMulti = isMulti,
									selectedPaths = selectedPaths,
									onEnterMultiSelect = { path ->
										videos.firstOrNull { it.path == path }?.let { onEnterMultiSelect(it) }
									},
									onUpdateSelectionPaths = { onUpdateSelectionPaths?.invoke(it) },
									rightEdgeIgnoreWidthDp = if (groups.isNotEmpty()) 44.dp else 0.dp,
								),
						) {
							if (groups.isNotEmpty()) {
								groups.forEach { group ->
									item(span = { GridItemSpan(2) }) {
										Row(
											modifier = Modifier
												.fillMaxWidth()
												.padding(horizontal = 4.dp, vertical = 6.dp),
											verticalAlignment = Alignment.CenterVertically,
											horizontalArrangement = Arrangement.SpaceBetween,
										) {
											Row(
												verticalAlignment = Alignment.CenterVertically,
												horizontalArrangement = Arrangement.spacedBy(8.dp),
											) {
												Text(
													text = group.dateTitle,
													fontSize = 15.sp,
													fontWeight = FontWeight.Bold,
													color = MaterialTheme.colorScheme.onSurface,
												)
												Text(
													text = "${group.count}部",
													fontSize = 12.sp,
													color = MaterialTheme.colorScheme.onSurfaceVariant,
												)
											}
											if (isMulti) {
												val isAllGroupSelected = group.items.isNotEmpty() && group.items.all { selectedPaths.contains(it.path) }
												Surface(
													shape = RoundedCornerShape(12.dp),
													color = if (isAllGroupSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
													onClick = {
														onToggleSelectGroup?.invoke(group, !isAllGroupSelected)
													},
												) {
													Text(
														text = if (isAllGroupSelected) "取消全选" else "全选",
														fontSize = 12.sp,
														fontWeight = FontWeight.Medium,
														color = if (isAllGroupSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
														modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
													)
												}
											}
										}
									}

									items(group.items, key = { it.path }) { video ->
										VideoCardItem(
											video = video,
											isSelected = selectedPaths.contains(video.path),
											isMulti = isMulti,
											urlOf = urlOf,
											onThumbnailError = onThumbnailError,
											onToggleSelect = onToggleSelect,
											onOpenVideo = onOpenVideo,
											onOpenAction = onOpenAction,
											onEnterMultiSelect = onEnterMultiSelect,
										)
									}
								}
							} else {
								items(videos, key = { it.path }) { video ->
									VideoCardItem(
										video = video,
										isSelected = selectedPaths.contains(video.path),
										isMulti = isMulti,
										urlOf = urlOf,
										onThumbnailError = onThumbnailError,
										onToggleSelect = onToggleSelect,
										onOpenVideo = onOpenVideo,
										onOpenAction = onOpenAction,
										onEnterMultiSelect = onEnterMultiSelect,
									)
								}
							}
						}

						// 拖动时浮现居中偏上的高质感毛玻璃时间气泡（防止空指针）
						if (isScrubbing && currentScrubGroup != null) {
							Surface(
								shape = RoundedCornerShape(20.dp),
								color = MaterialTheme.colorScheme.inverseSurface,
								tonalElevation = 6.dp,
								shadowElevation = 8.dp,
								border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
								modifier = Modifier
									.align(Alignment.TopEnd)
									.padding(end = 46.dp)
									.offset {
										val maxTravel = (scrubberTrackHeightPx - 44.dp.toPx()).coerceAtLeast(0f)
										IntOffset(
											x = 0,
											y = (scrubFraction * maxTravel + 20.dp.toPx()).roundToInt().coerceAtLeast(0)
										)
									}
							) {
								Row(
									modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.spacedBy(8.dp),
								) {
									Icon(
										Icons.Rounded.DateRange,
										contentDescription = null,
										tint = MaterialTheme.colorScheme.inverseOnSurface,
										modifier = Modifier.size(17.dp),
									)
									Text(
										text = "${currentScrubGroup?.dateTitle ?: ""} · ${currentScrubGroup?.count ?: 0}部",
										style = MaterialTheme.typography.labelLarge,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.inverseOnSurface,
									)
								}
							}
						}

						// 右侧竖向时间轴快捷滑动条（只要 groups 不为空即启用）
						if (groups.isNotEmpty()) {
							TimelineDateScrubber(
								state = state,
								groups = groups,
								onScrubStateChanged = { dragging, fraction, group, heightPx ->
									isScrubbing = dragging
									scrubFraction = fraction
									currentScrubGroup = group
									scrubberTrackHeightPx = heightPx
								},
								modifier = Modifier
									.align(Alignment.CenterEnd)
									.fillMaxHeight()
									.padding(end = 2.dp, top = 8.dp, bottom = 96.dp),
							)
						}
					}
				}
			}
		}
	}
}

/** 全部影集文件夹网格展示 */
@Composable
private fun MediaFoldersGrid(
	allMediaFolders: List<DavEntry>,
	allVideos: List<DavEntry>,
	folderIcons: Map<String, String>,
	isRefreshing: Boolean,
	onRefresh: () -> Unit,
	onSelectMediaFolder: (String) -> Unit,
	onRenameMediaFolder: (DavEntry) -> Unit,
	onChangeIcon: (DavEntry) -> Unit,
	onDeleteMediaFolder: (DavEntry) -> Unit,
	onCreateMediaFolder: () -> Unit,
	urlOf: (String) -> String? = { null },
	directUrlOf: ((String) -> String?)? = null,
	modifier: Modifier = Modifier,
) {
	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		modifier = modifier.fillMaxSize(),
	) {
		LazyVerticalGrid(
			columns = GridCells.Fixed(2),
			contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 88.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			modifier = Modifier.fillMaxSize(),
		) {
			item(key = "__CREATE_MEDIA__") {
				Card(
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
					border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(16.dp))
						.clickable { onCreateMediaFolder() },
				) {
					Column(modifier = Modifier.padding(14.dp)) {
						Box(
							modifier = Modifier
								.size(46.dp)
								.clip(RoundedCornerShape(12.dp))
								.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Rounded.Add,
								contentDescription = "新建影集",
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(26.dp),
							)
						}
						Spacer(modifier = Modifier.height(12.dp))
						Text(
							text = "新建影集",
							fontSize = 15.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.primary,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Spacer(modifier = Modifier.height(4.dp))
						Text(
							text = "创建专属影音分类",
							fontSize = 12.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}

			items(allMediaFolders, key = { it.path }) { folder ->
				var menuOpen by remember { mutableStateOf(false) }
				val videoCount = remember(folder.path, allVideos) {
					allVideos.count { it.path.startsWith(folder.path + "/") }
				}
				val customKey = folderIcons[folder.path]
				val coverVideo = remember(folder.path, allVideos, customKey) {
					if (customKey.isNullOrBlank()) {
						allVideos.firstOrNull { it.path.startsWith(folder.path.trim('/') + "/") }
					} else null
				}
				Card(
					shape = RoundedCornerShape(16.dp),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
					border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(16.dp))
						.clickable { onSelectMediaFolder(folder.path) },
				) {
					Column(modifier = Modifier.padding(14.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.Top,
						) {
							if (coverVideo != null) {
								FileThumbnail(
									entry = coverVideo,
									urlOf = urlOf,
									directUrlOf = directUrlOf,
									size = 46.dp,
								)
							} else {
								FileIconByEntry(
									entry = folder,
									customIconKey = customKey,
									size = 46.dp,
									urlOf = urlOf,
									directUrlOf = directUrlOf,
								)
							}
							Box {
								Box(
									modifier = Modifier
										.size(28.dp)
										.clip(CircleShape)
										.clickable { menuOpen = true },
									contentAlignment = Alignment.Center,
								) {
									Icon(
										Icons.Rounded.MoreVert,
										contentDescription = "更多",
										tint = MaterialTheme.colorScheme.onSurfaceVariant,
										modifier = Modifier.size(18.dp)
									)
								}
								DropdownMenu(
									expanded = menuOpen,
									onDismissRequest = { menuOpen = false },
								) {
									val isDefault = folder.name == "默认影集" || folder.name == "默认影音" || folder.path == "movies/默认影集" || folder.path == "movies/默认影音" || folder.path.endsWith("/默认影集") || folder.path.endsWith("/默认影音")
									if (!isDefault) {
										DropdownMenuItem(
											text = { Text("重命名分类") },
											onClick = { menuOpen = false; onRenameMediaFolder(folder) },
											leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
										)
									}
									DropdownMenuItem(
										text = { Text("更换图标") },
										onClick = { menuOpen = false; onChangeIcon(folder) },
										leadingIcon = { Icon(Icons.Rounded.Edit, null) },
									)
									if (!isDefault) {
										DropdownMenuItem(
											text = { Text("删除分类", color = MaterialTheme.colorScheme.error) },
											onClick = { menuOpen = false; onDeleteMediaFolder(folder) },
											leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
										)
									}
								}
							}
						}
						Spacer(modifier = Modifier.height(12.dp))
						Text(
							text = folder.name,
							fontSize = 15.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						Spacer(modifier = Modifier.height(4.dp))
						Text(
							text = "$videoCount 部视频",
							fontSize = 12.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCardItem(
	video: DavEntry,
	isSelected: Boolean,
	isMulti: Boolean,
	urlOf: (String) -> String?,
	onThumbnailError: ((String) -> Unit)?,
	onToggleSelect: (DavEntry) -> Unit,
	onOpenVideo: (DavEntry) -> Unit,
	onOpenAction: (DavEntry) -> Unit,
	onEnterMultiSelect: (DavEntry) -> Unit = {},
) {
	Card(
		shape = RoundedCornerShape(12.dp),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
		elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(
				onClick = {
					if (isMulti) {
						onToggleSelect(video)
					} else {
						onOpenVideo(video)
					}
				},
				onLongClick = {
					if (!isMulti) {
						onEnterMultiSelect(video)
					} else {
						onToggleSelect(video)
					}
				},
			),
	) {
		// 16:9 院线质感海报视窗：如同相册页面，纯净展现画面，不显示冗余名称与时间标签
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(16f / 9f)
				.background(
					Brush.linearGradient(
						listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.surfaceVariant)
					)
				),
			contentAlignment = Alignment.Center,
		) {
			var thumbFailed by remember(video.path) { mutableStateOf(false) }
			val thumbUrl = urlOf(video.path)
			if (!thumbFailed && !thumbUrl.isNullOrBlank()) {
				AsyncImage(
					model = ImageRequest.Builder(LocalContext.current)
						.data(thumbUrl)
						.memoryCacheKey("thumb:${video.path.trim('/')}")
						.diskCacheKey("thumb:${video.path.trim('/')}")
						.size(320, 180)
						.precision(coil.size.Precision.INEXACT)
						.allowRgb565(true)
						.crossfade(false)
						.build(),
					contentDescription = video.name,
					contentScale = ContentScale.Crop,
					onError = {
						thumbFailed = true
						onThumbnailError?.invoke(video.path)
					},
					modifier = Modifier.fillMaxSize(),
				)
			}
			Icon(
				imageVector = Icons.Rounded.Movie,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.35f),
				modifier = Modifier.size(52.dp),
			)

			// 微质感播放指示器
			Surface(
				shape = CircleShape,
				color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
				modifier = Modifier.size(32.dp),
			) {
				Box(contentAlignment = Alignment.Center) {
					Icon(
						Icons.Rounded.PlayArrow,
						contentDescription = "播放",
						tint = MaterialTheme.colorScheme.onSecondary,
						modifier = Modifier.size(20.dp),
					)
				}
			}

			// 右下角格式/清晰度微徽标
			Surface(
				shape = RoundedCornerShape(4.dp),
				color = MediaBackdrop.copy(alpha = 0.65f),
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(6.dp),
			) {
				Text(
					text = video.ext.uppercase(Locale.ROOT).ifEmpty { "HD" },
					fontSize = 9.sp,
					fontWeight = FontWeight.Bold,
					color = OnMediaBackdrop,
					modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
				)
			}

			// 仅在多选模式下显示右上角勾选框
			if (isMulti) {
				Box(
					modifier = Modifier
						.align(Alignment.TopEnd)
						.padding(6.dp)
				) {
					SelectCheckbox(isSelected = isSelected)
				}
			}
		}

		// 视频文件名与规格信息展示
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 8.dp)
		) {
			Text(
				text = video.name,
				style = MaterialTheme.typography.bodyMedium,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				color = MaterialTheme.colorScheme.onSurface,
			)
			Spacer(modifier = Modifier.height(2.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				val durText = video.videoDurationMs.let { ms ->
					if (ms > 0) {
						val totalSec = ms / 1000
						String.format(Locale.ROOT, "%02d:%02d", totalSec / 60, totalSec % 60)
					} else null
				}
				Text(
					text = listOfNotNull(durText, video.sizeHuman.ifEmpty { null }).joinToString(" · "),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
		}
	}
}

/**
 * 纯净流线型无小圆点进度条（告别末尾突兀圆点，优雅圆角，硬件加速）
 */
@Composable
private fun SleekLinearProgressBar(
	progress: Float,
	color: Color,
	trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
	modifier: Modifier = Modifier,
) {
	val p = progress.coerceIn(0f, 1f)
	Box(
		modifier = modifier
			.clip(RoundedCornerShape(50))
			.background(trackColor)
	) {
		if (p > 0f) {
			Box(
				modifier = Modifier
					.fillMaxHeight()
					.fillMaxWidth(p)
					.clip(RoundedCornerShape(50))
					.background(color)
			)
		}
	}
}

/** 传输列表按方向或完成结果筛选；只有实际可执行的操作才显示。 */
@Composable
private fun TransferCenterSheet(
	tasks: List<TransferTask>,
	totalSpeedBps: Long = 0L,
	onDismiss: () -> Unit,
	onPauseTask: (String) -> Unit,
	onResumeTask: (String) -> Unit,
	onCancelTask: (String) -> Unit,
	onPauseAll: () -> Unit,
	onResumeAll: () -> Unit,
	onRetryTask: (String) -> Unit,
	onClearFinished: () -> Unit,
	onOpenResult: (String) -> Unit,
	routeInfo: RouteInfo? = null,
	onShowNetworkSheet: () -> Unit = {},
) {
	var filter by rememberSaveable { mutableIntStateOf(0) }
	val haptic = LocalHapticFeedback.current
	val completedCount = tasks.count { it.status == TransferStatus.COMPLETED }
	val finishedStates = setOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)

	// 监听完成项计数增长，触发一次轻微触觉震动反馈，给予实时的成就感与爽快感
	LaunchedEffect(completedCount) {
		if (completedCount > 0) {
			haptic.performHapticFeedback(HapticFeedbackType.LongPress)
		}
	}

	val totalCount = tasks.size
	val runningTasks = tasks.filter { it.status == TransferStatus.RUNNING || it.status == TransferStatus.CONFIRMING }
	val hasActiveRunning = runningTasks.isNotEmpty()
	val isAllCompleted = totalCount > 0 && tasks.all { it.status == TransferStatus.COMPLETED }

	// 平滑且准确的总进度算法：按所有任务综合计算，杜绝单文件完成后总进度假跳 99%
	val rawBatchProgress = run {
		if (isAllCompleted) return@run 1f
		if (totalCount == 0) return@run 0f
		var totalTaskProgress = 0f
		for (task in tasks) {
			val p = when (task.status) {
				TransferStatus.COMPLETED -> 1f
				TransferStatus.CONFIRMING -> 0.98f
				TransferStatus.RUNNING -> {
					if (task.totalBytes > 0) (task.sentBytes.toFloat() / task.totalBytes).coerceIn(0f, 0.95f)
					else 0.05f
				}
				TransferStatus.QUEUED -> 0f
				TransferStatus.PAUSED -> {
					if (task.totalBytes > 0) (task.sentBytes.toFloat() / task.totalBytes).coerceIn(0f, 0.95f)
					else 0f
				}
				TransferStatus.FAILED, TransferStatus.CANCELLED -> 0f
			}
			totalTaskProgress += p
		}
		(totalTaskProgress / totalCount).coerceIn(0f, if (isAllCompleted) 1f else 0.99f)
	}

	var maxBatchProgress by remember(totalCount) { mutableFloatStateOf(0f) }
	val stableBatchProgress = if (isAllCompleted) 1f else {
		if (rawBatchProgress > maxBatchProgress) maxBatchProgress = rawBatchProgress
		maxBatchProgress
	}

	val animatedBatchProgress by animateFloatAsState(
		targetValue = stableBatchProgress,
		animationSpec = spring(stiffness = Spring.StiffnessLow),
		label = "batch_progress"
	)

	// 任务排布：进行中的任务严格排在最上面（按实时速率降序，速度越快越靠前），完成项整齐排到最下面
	val visibleTasks = remember(tasks, filter) {
		val filtered = tasks.filter {
			when (filter) {
				1 -> it.isUpload
				2 -> !it.isUpload
				3 -> it.status == TransferStatus.COMPLETED
				else -> true
			}
		}
		filtered.sortedWith(
			compareBy<TransferTask> { task ->
				when (task.status) {
					TransferStatus.RUNNING -> 0     // 🚀 正在高速传输，绝对置顶第1位！
					TransferStatus.CONFIRMING -> 1  // 🔄 正在落盘写入存储中
					TransferStatus.QUEUED -> 2      // ⏳ 排队等待中
					TransferStatus.PAUSED -> 3      // ⏸️ 已暂停
					TransferStatus.FAILED -> 4      // ❌ 失败
					TransferStatus.CANCELLED -> 5   // ⏹️ 取消
					TransferStatus.COMPLETED -> 6   // ✅ 已完成整齐沉底，排到最下面！
				}
			}.thenBy { it.id } // 关键：稳定任务顺序，绝不因为瞬时速度微调导致正在传输的任务互相交换位置抖动！
		)
	}
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
	) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).fillMaxHeight(0.88f)) {
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text("传输中心", style = MaterialTheme.typography.titleLarge)
					if (routeInfo != null) {
						CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
					}
				}
				IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "关闭传输中心") }
			}

			// 传输批次总览 Hero Card
			if (totalCount > 0) {
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = if (isAllCompleted) Color(0xFF2E7D32).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
					border = BorderStroke(1.dp, if (isAllCompleted) Color(0xFF2E7D32).copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
					modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
				) {
					Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(8.dp),
							) {
								Icon(
									if (isAllCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.Sync,
									contentDescription = null,
									tint = if (isAllCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
									modifier = Modifier.size(18.dp)
								)
								Text(
									if (isAllCompleted) "本批次任务已全部完成 🎉"
									else "批次传输中 (${(stableBatchProgress * 100).toInt()}%)",
									style = MaterialTheme.typography.titleSmall,
									fontWeight = FontWeight.Bold,
									color = if (isAllCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
								)
							}
							Text(
								if (hasActiveRunning && totalSpeedBps > 0) "${formatSize(totalSpeedBps)}/s"
								else if (isAllCompleted) "共 $totalCount 项"
								else "${completedCount} / $totalCount 项",
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.Bold,
								color = if (isAllCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
							)
						}
						Spacer(modifier = Modifier.height(10.dp))
						SleekLinearProgressBar(
							progress = if (isAllCompleted) 1f else animatedBatchProgress,
							color = if (isAllCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
							trackColor = MaterialTheme.colorScheme.surfaceVariant,
							modifier = Modifier.fillMaxWidth().height(6.dp),
						)
						Spacer(modifier = Modifier.height(6.dp))
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
						) {
							Text(
								"已完成 $completedCount / $totalCount 项",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							val knownByteTasks = tasks.filter { it.totalBytes > 0 }
							val totalKnownBytes = knownByteTasks.sumOf { it.totalBytes }
							val sentKnownBytes = knownByteTasks.sumOf { if (it.status == TransferStatus.COMPLETED) it.totalBytes else it.sentBytes }
							if (totalKnownBytes > 0) {
								Text(
									"${formatSize(sentKnownBytes)} / ${formatSize(totalKnownBytes)}",
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
								)
							}
						}
					}
				}
			} else {
				Text(
					"${tasks.count { it.status !in finishedStates }} 个进行中 · ${tasks.count { it.status == TransferStatus.COMPLETED }} 个完成 · ${tasks.count { it.status == TransferStatus.FAILED }} 个失败 · ${tasks.count { it.status == TransferStatus.CANCELLED }} 个取消",
					style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			val hasActiveRunning = tasks.any { it.status == TransferStatus.RUNNING || it.status == TransferStatus.CONFIRMING }
			if (hasActiveRunning) {
				Text(
					if (totalSpeedBps > 0) "当前总速率 ${formatSize(totalSpeedBps)}/s" else "正在传输中…",
					color = MaterialTheme.colorScheme.primary,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.padding(top = 8.dp),
				)
			}
			LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
				items(listOf("全部", "上传", "下载", "已完成").withIndex().toList()) { item ->
					FilterChip(selected = filter == item.index, onClick = { filter = item.index }, label = { Text(item.value) })
				}
			}
			LazyRow {
				if (tasks.any { !it.isUpload && it.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED) }) {
					item { TextButton(onClick = onPauseAll) { Text("暂停下载") } }
				}
				if (tasks.any { !it.isUpload && it.status == TransferStatus.PAUSED }) {
					item { TextButton(onClick = onResumeAll) { Text("继续下载") } }
				}
				if (tasks.any { it.status in finishedStates }) {
					item { TextButton(onClick = onClearFinished) { Text("清除结束任务") } }
				}
			}
			if (visibleTasks.isEmpty()) {
				Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
					Text(if (tasks.isEmpty()) "暂无传输任务" else "此筛选下暂无任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			} else {
				LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
					items(visibleTasks, key = { it.id }) { task ->
						val isCompleted = task.status == TransferStatus.COMPLETED
						val terminal = task.status in finishedStates
						Surface(
							color = if (isCompleted) Color(0xFF2E7D32).copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
							shape = RoundedCornerShape(10.dp),
							border = if (isCompleted) BorderStroke(1.dp, Color(0xFF2E7D32).copy(alpha = 0.35f)) else null,
						) {
							Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
								// 第1行：图标 + 文件名（单行省略） + 实时速率/状态徽标 + 紧凑操作按钮
								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically
								) {
									Icon(
										if (isCompleted) Icons.Rounded.CheckCircle
										else if (task.isUpload) Icons.Rounded.CloudUpload
										else Icons.Rounded.CloudDownload,
										contentDescription = if (task.isUpload) "上传" else "下载",
										tint = if (isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
										modifier = Modifier.size(16.dp),
									)
									Text(
										task.name,
										style = MaterialTheme.typography.bodyMedium,
										fontWeight = FontWeight.SemiBold,
										modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis
									)
									// 实时状态/速率徽章
									if (task.status == TransferStatus.RUNNING) {
										Text(
											if (task.speedStr != "--") task.speedStr else "传输中",
											style = MaterialTheme.typography.labelSmall,
											fontWeight = FontWeight.Bold,
											color = MaterialTheme.colorScheme.primary,
										)
									} else if (task.status == TransferStatus.CONFIRMING) {
										Text("写入存储…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
									} else if (isCompleted) {
										Text("✓ 完成", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
									} else if (task.status == TransferStatus.FAILED) {
										Text("失败", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
									} else if (task.status == TransferStatus.PAUSED) {
										Text("已暂停", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
									} else if (task.status == TransferStatus.QUEUED) {
										Text("排队中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
									} else if (task.status == TransferStatus.CANCELLED) {
										Text("已取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
									}

									// 紧凑快捷操作按钮
									if (!task.isUpload && task.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED)) {
										IconButton(onClick = { onPauseTask(task.id) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
											Icon(Icons.Rounded.Pause, contentDescription = "暂停", modifier = Modifier.size(14.dp))
										}
									} else if (!task.isUpload && task.status == TransferStatus.PAUSED) {
										IconButton(onClick = { onResumeTask(task.id) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
											Icon(Icons.Rounded.PlayArrow, contentDescription = "继续", modifier = Modifier.size(14.dp))
										}
									} else if (task.status in setOf(TransferStatus.FAILED, TransferStatus.CANCELLED)) {
										IconButton(onClick = { onRetryTask(task.id) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
											Icon(Icons.Rounded.Refresh, contentDescription = "重试", modifier = Modifier.size(14.dp))
										}
									} else if (task.status == TransferStatus.COMPLETED) {
										IconButton(onClick = { onOpenResult(task.id) }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
											Icon(Icons.Rounded.FolderOpen, contentDescription = "打开", modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
										}
									}
									IconButton(onClick = { onCancelTask(task.id) }, modifier = Modifier.size(24.dp).padding(start = 2.dp)) {
										Icon(Icons.Rounded.Close, contentDescription = "清除", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
									}
								}

								// 第2行：超窄流线型进度条（3dp 高度，无末尾突兀圆点）
								if (isCompleted) {
									SleekLinearProgressBar(
										progress = 1f,
										color = Color(0xFF2E7D32),
										trackColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
										modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp),
									)
								} else if (!terminal) {
									val animProgress by animateFloatAsState(
										targetValue = if (task.totalBytes > 0) task.progress.coerceIn(0.01f, 1f) else 0.05f,
										animationSpec = tween(durationMillis = 300, easing = LinearEasing),
										label = "task_progress_${task.id}"
									)
									if (task.status == TransferStatus.CONFIRMING) {
										SleekLinearProgressBar(
											progress = 0.99f,
											color = MaterialTheme.colorScheme.primary,
											modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp)
										)
									} else if (task.status == TransferStatus.RUNNING && task.totalBytes <= 0) {
										SleekLinearProgressBar(
											progress = 0.5f,
											color = MaterialTheme.colorScheme.primary,
											modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp)
										)
									} else {
										SleekLinearProgressBar(
											progress = animProgress,
											color = MaterialTheme.colorScheme.primary,
											trackColor = MaterialTheme.colorScheme.surfaceVariant,
											modifier = Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp)
										)
									}
								}

								// 第3行：极简大小进度与百分比
								Row(
									modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
									horizontalArrangement = Arrangement.SpaceBetween,
									verticalAlignment = Alignment.CenterVertically
								) {
									if (task.status == TransferStatus.FAILED && !task.error.isNullOrBlank()) {
										Text(task.error.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
									} else {
										val sizeText = "${formatSize(task.sentBytes)} / ${if (task.totalBytes > 0) formatSize(task.totalBytes) else "大小未知"}"
										Text(sizeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
									}
									val percentText = if (task.totalBytes > 0 && !isCompleted && !terminal) {
										"${(task.progress * 100).toInt()}%"
									} else ""
									if (percentText.isNotEmpty()) {
										Text(percentText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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

/**
 * 智能网络状态小抽屉（极简优雅，超轻量无遮挡）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkManagerSheet(
	routeInfo: RouteInfo,
	currentMode: com.nasdrop.ConnectionMode,
	onDismiss: () -> Unit,
	onTriggerPunch: () -> Unit,
	onSwitchMode: (com.nasdrop.ConnectionMode) -> Unit,
	onOpenSettings: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
		dragHandle = {
			Box(
				modifier = Modifier
					.padding(top = 8.dp, bottom = 4.dp)
					.size(width = 30.dp, height = 3.dp)
					.clip(RoundedCornerShape(1.5.dp))
					.background(MaterialTheme.colorScheme.outlineVariant)
			)
		}
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp)
				.padding(bottom = 16.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Box(
						modifier = Modifier
							.size(6.dp)
							.clip(CircleShape)
							.background(if (routeInfo.isLan || routeInfo.isDirect) Color(0xFF2E7D32) else Color(0xFFE65100))
					)
					Text(
						text = if (routeInfo.isLan) "局域网直连" else if (routeInfo.isDirect) "极速直连" else "网络正常",
						fontSize = 13.sp,
						fontWeight = FontWeight.Bold,
						color = MaterialTheme.colorScheme.onSurface,
					)
				}
				Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
					TextButton(
						onClick = {
							onTriggerPunch()
							onDismiss()
						},
						contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
					) {
						Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
						Spacer(Modifier.width(2.dp))
						Text("刷新", fontSize = 12.sp)
					}
					TextButton(
						onClick = {
							onOpenSettings()
							onDismiss()
						},
						contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
					) {
						Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
						Spacer(Modifier.width(2.dp))
						Text("设置", fontSize = 12.sp)
					}
				}
			}

			Spacer(modifier = Modifier.height(4.dp))

			Text(
				text = when {
					routeInfo.isLanDirect -> "局域网高速传输，物理零延迟跑满带宽。"
					routeInfo.isDirect && !routeInfo.isRelay -> "端到端极速直连，不经过第三方中继。"
					routeInfo.isRelay -> "当前远程中继传输，可点击「刷新」尝试直连。"
					else -> "网络链路检测正常。"
				},
				fontSize = 11.sp,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/**
 * 底部操作抽屉 (ActionBottomSheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionBottomSheet(
	entry: DavEntry,
	urlOf: (String) -> String?,
	customIconKey: String? = null,
	onDismiss: () -> Unit,
	onDownload: () -> Unit,
	onMove: () -> Unit,
	onRename: () -> Unit,
	onDelete: () -> Unit,
	onChangeIcon: (() -> Unit)? = null,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			// 文件头部信息
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
			) {
				FileThumbnail(entry = entry, urlOf = urlOf, customIconKey = customIconKey, size = 44.dp)
				Column(modifier = Modifier.weight(1f)) {
					Text(entry.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
					Spacer(modifier = Modifier.height(2.dp))
					Text(formatMeta(entry), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}

			Spacer(modifier = Modifier.height(20.dp))

			// 操作宫格
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceAround,
			) {
				if (!entry.isDir) {
					ActionSheetBtn(
						icon = Icons.Rounded.CloudDownload,
						label = if (entry.isImage || entry.isVideo) "存入相册" else "下载本地",
						onClick = onDownload,
					)
				} else if (onChangeIcon != null) {
					ActionSheetBtn(
						icon = Icons.Rounded.Palette,
						label = "更换图标",
						onClick = onChangeIcon,
						tint = MaterialTheme.colorScheme.primary,
					)
				}
				ActionSheetBtn(icon = Icons.Rounded.DriveFileMove, label = "移动文件", onClick = onMove)
				ActionSheetBtn(icon = Icons.Rounded.DriveFileRenameOutline, label = "重命名", onClick = onRename)
				ActionSheetBtn(icon = Icons.Rounded.Delete, label = "彻底删除", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}
}

/**
 * 文件夹图标选择器底栏 (FolderIconPickerSheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderIconPickerSheet(
	folderName: String,
	currentIconKey: String?,
	onSelect: (NasFolderIcon) -> Unit,
	onUploadCustomIcon: () -> Unit = {},
	onDismiss: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 12.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Column {
					Text("选择文件夹图标", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
					Spacer(modifier = Modifier.height(2.dp))
					Text(folderName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
				IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
					Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
				}
			}

			Spacer(modifier = Modifier.height(14.dp))

			// 上传自定义图标入口卡片
			Surface(
				shape = RoundedCornerShape(14.dp),
				color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
				border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onUploadCustomIcon() },
			) {
				Row(
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Box(
						modifier = Modifier
							.size(40.dp)
							.clip(CircleShape)
							.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							Icons.Rounded.AddPhotoAlternate,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(22.dp)
						)
					}
					Column(modifier = Modifier.weight(1f)) {
						Text(
							"从手机相册上传自定义图标",
							fontSize = 14.sp,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onSurface
						)
						Spacer(modifier = Modifier.height(2.dp))
						Text(
							"选择一张图片作为此文件夹在 NAS 上的专属封面",
							fontSize = 11.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(14.dp))

			val icons = NasFolderIcon.entries
			LazyVerticalGrid(
				columns = GridCells.Fixed(4),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 8.dp),
			) {
				items(icons) { iconItem ->
					val isSelected = (currentIconKey == iconItem.key) || (currentIconKey == null && iconItem == NasFolderIcon.Default)
					Column(
						horizontalAlignment = Alignment.CenterHorizontally,
						modifier = Modifier
							.clip(RoundedCornerShape(12.dp))
							.clickable { onSelect(iconItem) }
							.padding(vertical = 6.dp),
					) {
						Box(
							modifier = Modifier
								.size(54.dp)
								.clip(RoundedCornerShape(14.dp))
								.background(iconItem.bgColor)
								.then(
									if (isSelected) {
										Modifier.border(2.dp, iconItem.tintColor, RoundedCornerShape(14.dp))
									} else {
										Modifier
									}
								),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								imageVector = iconItem.icon,
								contentDescription = iconItem.label,
								tint = iconItem.tintColor,
								modifier = Modifier.size(28.dp),
							)
						}
						Spacer(modifier = Modifier.height(6.dp))
						Text(
							text = iconItem.label,
							fontSize = 11.sp,
							color = if (isSelected) iconItem.tintColor else MaterialTheme.colorScheme.onSurface,
							fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
							maxLines = 1,
						)
					}
				}
			}

			Spacer(modifier = Modifier.height(24.dp))
		}
	}
}

@Composable
private fun ActionSheetBtn(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	tint: Color = MaterialTheme.colorScheme.onSurface,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier.clickable { onClick() },
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceVariant,
			modifier = Modifier.size(48.dp),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
			}
		}
		Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
	}
}

/**
 * 移动目录弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFolderSheet(
	folderPaths: List<String>,
	currentPath: String,
	onSelectTarget: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Text("选择移动目标目录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
			Spacer(modifier = Modifier.height(14.dp))

			LazyColumn(
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				items(folderPaths) { fPath ->
					val isCurrent = fPath == currentPath
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant,
						modifier = Modifier
							.fillMaxWidth()
							.clickable(enabled = !isCurrent) { onSelectTarget(fPath) },
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(10.dp),
						) {
							Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFFFA000))
							Text(
								text = if (fPath.isEmpty()) "根目录 (/)" else fPath,
								fontSize = 13.sp,
								fontWeight = FontWeight.SemiBold,
								color = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
								modifier = Modifier.weight(1f),
							)
							if (isCurrent) {
								Text("当前所在", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
							}
						}
					}
				}
			}
			Spacer(modifier = Modifier.height(24.dp))
		}
	}
}

/**
 * 新建文件夹与专属相册对话框
 */
enum class CreateDialogType {
	Folder,
	Album,
	MediaFolder,
}

@Composable
private fun CreateFolderDialog(
	type: CreateDialogType,
	onConfirm: (name: String) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by remember { mutableStateOf("") }
	val title = when (type) {
		CreateDialogType.Folder -> "新建文件夹"
		CreateDialogType.Album -> "新建专属相册"
		CreateDialogType.MediaFolder -> "新建影集/分类"
	}
	val placeholder = when (type) {
		CreateDialogType.Folder -> "输入文件夹名称"
		CreateDialogType.Album -> "如: 2026云南自驾游"
		CreateDialogType.MediaFolder -> "如: 科幻大片 / 纪录片"
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title, fontWeight = FontWeight.Bold) },
		text = {
			OutlinedTextField(
				value = name,
				onValueChange = { name = it },
				placeholder = { Text(placeholder) },
				singleLine = true,
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth(),
			)
		},
		confirmButton = {
			Button(
				onClick = {
					val target = name.trim()
					if (target.isNotBlank()) onConfirm(target)
				},
				colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
				shape = RoundedCornerShape(10.dp),
			) { Text("确定创建") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("取消") }
		},
	)
}

/** 按实际链路状态展示连接信息，不使用固定延迟或速度承诺。 */
@Composable
private fun NetworkIntelligenceConsole(
	routeInfo: RouteInfo,
	isPunching: Boolean = false,
	onTriggerPunch: () -> Unit,
	onOpenSettings: () -> Unit,
	logs: String,
) {
	Column(
		Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 88.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
			Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
				Text("NAS 连接", style = MaterialTheme.typography.titleMedium)
				Text(
					when {
						routeInfo.isLan -> "当前使用局域网连接"
						routeInfo.isDirect -> "当前使用直连"
						routeInfo.isRelay -> "当前使用中继连接"
						else -> "当前正在连接…"
					},
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Button(onClick = onTriggerPunch, enabled = !isPunching, modifier = Modifier.fillMaxWidth()) {
					Text(if (isPunching) "正在检测连接…" else "重新检测连接")
				}
				OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("检查连接设置") }
			}
		}
	}
}

@Composable
private fun CommercialNavigationBar(
	currentTab: MainTab,
	onTabSelect: (MainTab) -> Unit,
) {
	val homeWeight by animateFloatAsState(if (currentTab == MainTab.Home) 1.6f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "w_home")
	val photosWeight by animateFloatAsState(if (currentTab == MainTab.Photos) 1.6f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "w_photos")
	val filesWeight by animateFloatAsState(if (currentTab == MainTab.Files) 1.6f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "w_files")
	val settingsWeight by animateFloatAsState(if (currentTab == MainTab.Settings) 1.6f else 1.0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "w_settings")

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.navigationBarsPadding()
			.padding(horizontal = 20.dp, vertical = 8.dp),
		contentAlignment = Alignment.Center,
	) {
		Surface(
			shape = RoundedCornerShape(32.dp),
			color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
			shadowElevation = 10.dp,
			tonalElevation = 0.dp,
			border = BorderStroke(
				1.dp,
				Brush.verticalGradient(
					listOf(
						Color.White.copy(alpha = 0.65f),
						MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
					)
				)
			),
			modifier = Modifier
				.fillMaxWidth()
				.height(64.dp)
				.graphicsLayer {
					clip = true
					shape = RoundedCornerShape(32.dp)
				},
		) {
			Row(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 8.dp, vertical = 6.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceEvenly,
			) {
				CompactTabItem(
					title = "首页",
					activeIcon = Icons.Rounded.Home,
					inactiveIcon = Icons.Rounded.Home,
					selected = currentTab == MainTab.Home,
					onClick = { onTabSelect(MainTab.Home) },
					modifier = Modifier.weight(homeWeight),
				)
				CompactTabItem(
					title = "相册",
					activeIcon = Icons.Rounded.PhotoLibrary,
					inactiveIcon = Icons.Rounded.PhotoLibrary,
					selected = currentTab == MainTab.Photos,
					onClick = { onTabSelect(MainTab.Photos) },
					modifier = Modifier.weight(photosWeight),
				)
				CompactTabItem(
					title = "文件",
					activeIcon = Icons.Rounded.Folder,
					inactiveIcon = Icons.Rounded.Folder,
					selected = currentTab == MainTab.Files,
					onClick = { onTabSelect(MainTab.Files) },
					modifier = Modifier.weight(filesWeight),
				)
				CompactTabItem(
					title = "配置",
					activeIcon = Icons.Rounded.Settings,
					inactiveIcon = Icons.Rounded.Settings,
					selected = currentTab == MainTab.Settings,
					onClick = { onTabSelect(MainTab.Settings) },
					modifier = Modifier.weight(settingsWeight),
				)
			}
		}
	}
}

@Composable
private fun CompactTabItem(
	title: String,
	activeIcon: ImageVector,
	inactiveIcon: ImageVector,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val haptic = LocalHapticFeedback.current
	val activeColor = MaterialTheme.colorScheme.primary
	val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
	val targetBg = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else Color.Transparent
	val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "tab_bg")
	val animatedColor by animateColorAsState(targetValue = if (selected) activeColor else inactiveColor, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "tab_color")

	Box(
		modifier = modifier.fillMaxHeight(),
		contentAlignment = Alignment.Center,
	) {
		Surface(
			shape = RoundedCornerShape(22.dp),
			color = animatedBg,
			onClick = {
				haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
				onClick()
			},
			modifier = Modifier
				.fillMaxHeight(0.85f)
				.wrapContentWidth(),
		) {
			Row(
				modifier = Modifier.padding(horizontal = if (selected) 14.dp else 8.dp, vertical = 4.dp),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Box(
					modifier = Modifier
						.size(32.dp)
						.clip(CircleShape)
						.background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = if (selected) activeIcon else inactiveIcon,
						contentDescription = title,
						tint = animatedColor,
						modifier = Modifier.size(20.dp),
					)
				}
				AnimatedVisibility(
					visible = selected,
					enter = fadeIn() + expandHorizontally(),
					exit = fadeOut() + shrinkHorizontally(),
				) {
					Text(
						text = title,
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold,
						color = animatedColor,
						maxLines = 1,
						softWrap = false,
						modifier = Modifier.padding(start = 6.dp),
					)
				}
			}
		}
	}
}

internal fun formatMeta(entry: DavEntry): String {
	val dateStr = if (entry.effectiveDate > 0) formatQuarkDate(entry.effectiveDate) else ""
	if (entry.isDir) {
		return if (dateStr.isNotEmpty()) "$dateStr  文件夹" else "文件夹"
	}
	val sizeStr = formatSize(entry.size)
	return when {
		dateStr.isNotEmpty() && sizeStr != "--" -> "$dateStr  $sizeStr"
		dateStr.isNotEmpty() -> dateStr
		else -> sizeStr
	}
}

internal fun formatQuarkDate(millis: Long): String {
	if (millis <= 0) return ""
	val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
	return sdf.format(Date(millis))
}

internal fun formatSize(bytes: Long): String {
	if (bytes <= 0) return "--"
	val kb = bytes / 1024.0
	val mb = kb / 1024.0
	val gb = mb / 1024.0
	return when {
		gb >= 1.0 -> String.format(Locale.ROOT, "%.1f GB", gb)
		mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB", mb)
		kb >= 1.0 -> String.format(Locale.ROOT, "%.0f KB", kb)
		else -> "$bytes B"
	}
}

internal fun formatDate(millis: Long): String {
	if (millis <= 0) return ""
	val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
	return sdf.format(Date(millis))
}

@Composable
private fun DraggableUploadFab(
	currentTab: MainTab,
	onUploadFile: () -> Unit,
	onUploadPhoto: () -> Unit,
	onUploadVideo: () -> Unit,
) {
	if (currentTab == MainTab.Home || currentTab == MainTab.Me || currentTab == MainTab.Settings) return

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val density = LocalDensity.current
		val boxWidthPx = constraints.maxWidth.toFloat()
		val boxHeightPx = constraints.maxHeight.toFloat()

		val fabSizeDp = 52.dp
		val fabSizePx = with(density) { fabSizeDp.toPx() }
		val marginPx = with(density) { 16.dp.toPx() }
		val bottomMarginPx = with(density) { 92.dp.toPx() }

		// 默认吸附在可视区右下角：距离右边缘 16dp，距离底部（高于悬浮毛玻璃底栏）92dp
		var offsetX by remember(boxWidthPx) { mutableFloatStateOf((boxWidthPx - fabSizePx - marginPx).coerceAtLeast(0f)) }
		var offsetY by remember(boxHeightPx) { mutableFloatStateOf((boxHeightPx - fabSizePx - bottomMarginPx).coerceAtLeast(0f)) }
		var isDragging by remember { mutableStateOf(false) }

		val (bgColor, icon, label) = when (currentTab) {
			MainTab.Home -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.FileUpload, "快速上传")
			MainTab.Files -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.FileUpload, "上传文件")
			MainTab.Photos -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.FileUpload, "上传照片")
			MainTab.Media -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.FileUpload, "上传视频")
			MainTab.Settings, MainTab.Me -> Triple(Color.Transparent, Icons.Rounded.Add, "")
		}

		Surface(
			shape = CircleShape,
			color = bgColor,
			shadowElevation = if (isDragging) 12.dp else 6.dp,
			modifier = Modifier
				.offset {
					val curX = offsetX.coerceIn(marginPx, (boxWidthPx - fabSizePx - marginPx).coerceAtLeast(marginPx))
					val curY = offsetY.coerceIn(marginPx, (boxHeightPx - fabSizePx - bottomMarginPx).coerceAtLeast(marginPx))
					IntOffset(curX.roundToInt(), curY.roundToInt())
				}
				.size(fabSizeDp)
				.pointerInput(currentTab, boxWidthPx, boxHeightPx) {
					detectDragGestures(
						onDragStart = { isDragging = true },
						onDragEnd = {
							isDragging = false
							val centerX = offsetX + fabSizePx / 2
							offsetX = if (centerX < boxWidthPx / 2) marginPx else (boxWidthPx - fabSizePx - marginPx)
						},
						onDragCancel = { isDragging = false },
						onDrag = { change, dragAmount ->
							change.consume()
							offsetX = (offsetX + dragAmount.x).coerceIn(marginPx, boxWidthPx - fabSizePx - marginPx)
							offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, boxHeightPx - fabSizePx - bottomMarginPx)
						}
					)
				}
				.clickable {
					when (currentTab) {
						MainTab.Home -> onUploadPhoto()
						MainTab.Files -> onUploadFile()
						MainTab.Photos -> onUploadPhoto()
						MainTab.Media -> onUploadVideo()
						MainTab.Settings, MainTab.Me -> {}
					}
				},
		) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
					Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
			}
		}
	}
}

@Composable
private fun MemoriesBanner(
	memoryYearGroups: List<com.nasdrop.MemoryYearGroup>,
	onClickGroup: (com.nasdrop.MemoryYearGroup) -> Unit,
	urlOf: (String) -> String?,
	directUrlOf: ((String) -> String?)?,
	modifier: Modifier = Modifier,
) {
	val pagerState = rememberPagerState(pageCount = { memoryYearGroups.size })

	LaunchedEffect(pagerState, memoryYearGroups.size) {
		if (memoryYearGroups.size > 1) {
			while (true) {
				delay(4500)
				val next = (pagerState.currentPage + 1) % memoryYearGroups.size
				pagerState.animateScrollToPage(next)
			}
		}
	}

	val infiniteTransition = rememberInfiniteTransition(label = "ken_burns_memories")
	val imageScale by infiniteTransition.animateFloat(
		initialValue = 1.0f,
		targetValue = 1.07f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 5000, easing = EaseInOutSine),
			repeatMode = RepeatMode.Reverse
		),
		label = "scale"
	)

	Column(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		HorizontalPager(
			state = pagerState,
			modifier = Modifier
				.fillMaxWidth()
				.height(132.dp),
			pageSpacing = 10.dp,
		) { page ->
			val group = memoryYearGroups[page]
			val isCurrent = page == pagerState.currentPage

			Surface(
				modifier = Modifier
					.fillMaxSize()
					.clip(RoundedCornerShape(18.dp))
					.clickable { onClickGroup(group) },
				shape = RoundedCornerShape(18.dp),
				shadowElevation = 3.dp,
				color = MaterialTheme.colorScheme.surfaceVariant,
			) {
				Box(modifier = Modifier.fillMaxSize()) {
					// 封面图（当前卡片伴随呼吸微动）
					val coverTarget = urlOf(group.coverEntry.path)
					if (!coverTarget.isNullOrBlank()) {
						AsyncImage(
							model = coverTarget,
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.fillMaxSize()
								.graphicsLayer {
									if (isCurrent) {
										scaleX = imageScale
										scaleY = imageScale
									}
								},
						)
					} else {
						Box(
							modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Rounded.PhotoLibrary,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(36.dp)
							)
						}
					}

					// 半透明渐变遮罩
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(
								Brush.verticalGradient(
									colors = listOf(
										Color.Black.copy(alpha = 0.35f),
										Color.Transparent,
										Color.Black.copy(alpha = 0.85f),
									)
								)
							)
					)

					// 顶部动态标签
					Row(
						modifier = Modifier
							.align(Alignment.TopStart)
							.padding(10.dp)
							.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
							.padding(horizontal = 8.dp, vertical = 3.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Box(
							modifier = Modifier
								.size(6.dp)
								.clip(CircleShape)
								.background(Color(0xFF38BDF8))
						)
						Text(
							"✨ 那年今天 · 动态回顾",
							fontSize = 11.sp,
							fontWeight = FontWeight.SemiBold,
							color = Color.White
						)
					}

					if (memoryYearGroups.size > 1) {
						Surface(
							shape = RoundedCornerShape(8.dp),
							color = Color.Black.copy(alpha = 0.45f),
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(10.dp),
						) {
							Text(
								"${page + 1}/${memoryYearGroups.size}",
								fontSize = 10.5.sp,
								fontWeight = FontWeight.Medium,
								color = Color.White.copy(alpha = 0.9f),
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
							)
						}
					}

					// 底部核心信息
					Column(
						modifier = Modifier
							.align(Alignment.BottomStart)
							.padding(horizontal = 12.dp, vertical = 10.dp)
					) {
						Text(
							"${group.yearsAgo}年前的今天 · ${group.year}",
							fontSize = 15.5.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White,
							maxLines = 1,
						)
						Spacer(Modifier.height(2.dp))
						val countsStr = buildString {
							if (group.photoCount > 0) append("${group.photoCount}张照片")
							if (group.videoCount > 0) {
								if (isNotEmpty()) append(" · ")
								append("${group.videoCount}个视频")
							}
							if (group.albumGroups.size > 1) {
								if (isNotEmpty()) append(" · ")
								append("${group.albumGroups.size}个相册")
							}
						}
						Text(
							countsStr,
							fontSize = 11.5.sp,
							color = Color.White.copy(alpha = 0.9f),
							maxLines = 1,
						)
					}
				}
			}
		}

		if (memoryYearGroups.size > 1) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.Center,
				verticalAlignment = Alignment.CenterVertically,
			) {
				repeat(memoryYearGroups.size) { iteration ->
					val isSelected = pagerState.currentPage == iteration
					Box(
						modifier = Modifier
							.padding(horizontal = 3.dp)
							.height(4.dp)
							.width(if (isSelected) 14.dp else 5.dp)
							.clip(CircleShape)
							.background(
								if (isSelected) MaterialTheme.colorScheme.primary
								else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
							)
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryDetailModalSheet(
	yearGroup: com.nasdrop.MemoryYearGroup,
	onDismiss: () -> Unit,
	onSelectAlbum: (String) -> Unit,
	onOpenImage: (DavEntry) -> Unit,
	onOpenVideo: (DavEntry) -> Unit = {},
	urlOf: (String) -> String?,
	directUrlOf: ((String) -> String?)?,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.fillMaxHeight(0.85f)
		) {
			// 顶部栏
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						"${yearGroup.yearsAgo}年前的今天 · ${yearGroup.year}",
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
					)
					Spacer(Modifier.height(2.dp))
					Text(
						buildString {
							if (yearGroup.photoCount > 0) append("${yearGroup.photoCount}张照片")
							if (yearGroup.videoCount > 0) {
								if (isNotEmpty()) append(" · ")
								append("${yearGroup.videoCount}个视频")
							}
						},
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
				IconButton(onClick = onDismiss) {
					Icon(Icons.Rounded.Close, contentDescription = "关闭")
				}
			}

			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				contentPadding = PaddingValues(bottom = 32.dp),
			) {
				for (album in yearGroup.albumGroups) {
					// 相册分组标题栏
					item(key = "album_header_${album.albumPath}") {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 16.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Icon(
								Icons.Rounded.PhotoLibrary,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp)
							)
							Spacer(Modifier.width(8.dp))
							Text(
								album.albumName,
								style = MaterialTheme.typography.titleSmall,
								fontWeight = FontWeight.Bold,
							)
							Spacer(Modifier.width(6.dp))
							Text(
								"${album.items.size}项",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
							Spacer(Modifier.weight(1f))
							if (album.albumPath.isNotBlank()) {
								Surface(
									modifier = Modifier
										.height(26.dp)
										.clip(RoundedCornerShape(13.dp))
										.clickable { onSelectAlbum(album.albumPath) },
									color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
								) {
									Row(
										modifier = Modifier.padding(horizontal = 9.dp),
										verticalAlignment = Alignment.CenterVertically,
									) {
										Text(
											"相册 ➔",
											fontSize = 11.sp,
											fontWeight = FontWeight.SemiBold,
											color = MaterialTheme.colorScheme.primary,
										)
									}
								}
							}
						}
					}

					// 该相册内的照片/视频 3 列宫格展示
					val rows = album.items.chunked(3)
					items(rows.size, key = { "album_row_${album.albumPath}_$it" }) { rowIdx ->
						val rowItems = rows[rowIdx]
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp, vertical = 2.dp),
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							for (item in rowItems) {
								Box(
									modifier = Modifier
										.weight(1f)
										.aspectRatio(1f)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.surfaceVariant)
										.clickable {
											if (item.isVideo) {
												onOpenVideo(item)
											} else {
												onOpenImage(item)
											}
										},
								) {
									val thumbUrl = urlOf(item.path)
									if (!thumbUrl.isNullOrBlank()) {
										AsyncImage(
											model = thumbUrl,
											contentDescription = item.name,
											contentScale = ContentScale.Crop,
											modifier = Modifier.fillMaxSize(),
										)
									} else {
										Box(
											modifier = Modifier.fillMaxSize(),
											contentAlignment = Alignment.Center,
										) {
											FileIcon(
												category = if (item.isVideo) FileCategory.Video else FileCategory.Image,
												isDir = false,
												size = 28.dp,
											)
										}
									}

									// 如果是视频，左下角显示微型播放角标
									if (item.isVideo) {
										Box(
											modifier = Modifier
												.align(Alignment.BottomStart)
												.padding(4.dp)
												.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
												.padding(horizontal = 4.dp, vertical = 2.dp),
											contentAlignment = Alignment.Center,
										) {
											Icon(
												Icons.Rounded.PlayArrow,
												contentDescription = null,
												tint = Color.White,
												modifier = Modifier.size(12.dp)
											)
										}
									}
								}
							}
							// 补齐占位
							repeat(3 - rowItems.size) {
								Spacer(Modifier.weight(1f))
							}
						}
					}

					item(key = "album_spacer_${album.albumPath}") {
						Spacer(Modifier.height(10.dp))
					}
				}
			}
		}
	}
}
