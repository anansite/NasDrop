@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nasdrop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material.icons.rounded.Sync
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
import com.nasdrop.NasFolderIcon
import androidx.compose.material.icons.rounded.Palette
import com.nasdrop.MainTab
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import com.nasdrop.ViewMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BrowserMode {
	Browse,
	Pick,
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

	// 系统文件选择器（上传）
	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		startUpload(uris, vm.path)
	}

	val photoPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedAlbumPath.isNotBlank()) vm.selectedAlbumPath else "Photos"
			startUpload(uris, targetDir)
		}
	}

	val videoPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedMediaPath.isNotBlank()) vm.selectedMediaPath else "movies"
			startUpload(uris, targetDir)
		}
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.background,
		bottomBar = {
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
				// 多选批量操作浮动托盘
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
				// 保留文件、相册、影音三个主入口。
				CommercialNavigationBar(
					currentTab = vm.currentTab,
					onTabSelect = { tab ->
						vm.currentTab = tab
						if (tab == MainTab.Photos || tab == MainTab.Media) {
							vm.ensureMediaScanned()
						}
					},
				)
			}
		},
		floatingActionButton = {},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
		) {
			// 各 Tab 专属顶栏：文件 Tab 仅需 2 行极简流线型；相册/影音支持专属新建与上传；服务与设置专职运维
			when (vm.currentTab) {
				MainTab.Files -> {
					FilesTopBar(
						vm = vm,
						onUpload = { filePicker.launch(arrayOf("*/*")) },
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
						selectedAlbumName = vm.selectedAlbumPath.substringAfterLast('/'),
						activeTransferCount = vm.activeTransferCount,
						onUploadPhoto = { photoPicker.launch(arrayOf("image/*")) },
						onUpAlbum = { vm.upAlbum() },
						onCreateAlbum = { activeCreateType = CreateDialogType.Album },
						onDeleteCurrentAlbum = {
							val cur = vm.allAlbumFolders.find { it.path == vm.selectedAlbumPath }
							if (cur != null) activeFolderForDelete = Pair(cur, true)
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
						selectedMediaName = vm.selectedMediaPath.substringAfterLast('/'),
						activeTransferCount = vm.activeTransferCount,
						onUploadVideo = { videoPicker.launch(arrayOf("video/*")) },
						onUpMedia = { vm.upMedia() },
						onCreateMediaFolder = { activeCreateType = CreateDialogType.MediaFolder },
						onDeleteCurrentMedia = {
							val cur = vm.allMediaFolders.find { it.path == vm.selectedMediaPath }
							if (cur != null) activeFolderForDelete = Pair(cur, false)
						},
						onOpenTransferSheet = { showTransferSheet = true },
						routeInfo = routeInfo,
						onOpenSettings = onOpenSettings,
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

			// 多选状态信息条
			if (vm.isMultiSelectMode) {
				MultiSelectHeaderStrip(
					selectedCount = vm.selectedEntries.size,
					onSelectAll = { vm.selectAll(vm.currentTabEntries) },
					onComplete = { vm.exitMultiSelect() },
				)
			}

			// 主内容区域根据 Tab 呈现，并接入标准下拉刷新
			Box(modifier = Modifier.weight(1f)) {
				val mediaTab = vm.currentTab == MainTab.Photos || vm.currentTab == MainTab.Media
				val contentError = if (mediaTab) vm.mediaError else vm.error
				val initialLoading = (if (mediaTab) vm.mediaScanning else vm.loading) && vm.currentTabEntries.isEmpty()
				PullToRefreshBox(
					isRefreshing = vm.isRefreshing,
					onRefresh = { vm.pullRefresh() },
					modifier = Modifier.fillMaxSize(),
				) {
					when {
						vm.needsLogin -> BrowserStatus(
							title = "需要授权内网连接",
							detail = contentError ?: "请先完成 Tailscale 授权，再访问 NAS。",
							onPrimary = onLogin, primaryLabel = "前往登录", onSettings = onOpenSettings,
						)
						contentError != null -> BrowserStatus(
							title = if (mediaTab) "媒体加载失败" else "文件加载失败",
							detail = contentError,
							onPrimary = { if (mediaTab) vm.ensureMediaScanned(force = true) else vm.refresh() },
							primaryLabel = "重试", onSettings = onOpenSettings,
						)
						initialLoading -> BrowserStatus(
							title = if (mediaTab) "正在扫描媒体…" else "正在加载文件…", loading = true,
						)
						else -> when (vm.currentTab) {
						MainTab.Files -> {
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

						MainTab.Photos -> {
							PhotoTimelineContent(
								groups = vm.photoTimelineGroups,
								allAlbums = vm.allAlbumFolders,
								selectedAlbumPath = vm.selectedAlbumPath,
								isMulti = vm.isMultiSelectMode,
								selectedPaths = vm.selectedPaths,
								onToggleSelect = { vm.toggleSelect(it) },
								onEnterMultiSelect = { vm.enterMultiSelect(it) },
								onOpenAction = { activeEntryForAction = it },
								onSelectAlbum = { vm.openAlbum(it) },
								onDeleteAlbum = { folder -> activeFolderForDelete = Pair(folder, true) },
								onCreateAlbum = { activeCreateType = CreateDialogType.Album },
								urlOf = { vm.thumbnailUrlFor(it) },
								onOpenImage = onOpenImage,
							)
						}

						MainTab.Media -> {
							MediaStreamContent(
								videos = vm.currentMediaVideos,
								allMediaFolders = vm.allMediaFolders,
								selectedMediaPath = vm.selectedMediaPath,
								isMulti = vm.isMultiSelectMode,
								selectedPaths = vm.selectedPaths,
								onToggleSelect = { vm.toggleSelect(it) },
								onEnterMultiSelect = { vm.enterMultiSelect(it) },
								onOpenAction = { activeEntryForAction = it },
								onSelectMediaFolder = { vm.openMedia(it) },
								onDeleteMediaFolder = { folder -> activeFolderForDelete = Pair(folder, false) },
								onCreateMediaFolder = { activeCreateType = CreateDialogType.MediaFolder },
								urlOf = { vm.thumbnailUrlFor(it) },
								onOpenVideo = onOpenVideo,
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
				}

				// 可自由拖拽吸附的悬浮上传球（按住任意拖动，松手平滑贴边，绝不挡列表底部）
				if (!vm.isMultiSelectMode && mode == BrowserMode.Browse) {
					DraggableUploadFab(
						currentTab = vm.currentTab,
						onUploadFile = { filePicker.launch(arrayOf("*/*")) },
						onUploadPhoto = { photoPicker.launch(arrayOf("image/*")) },
						onUploadVideo = { videoPicker.launch(arrayOf("video/*")) },
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
						if (isAlbum) vm.deleteAlbum(folder) else vm.deleteMediaFolder(folder)
						activeFolderForDelete = null
						Toast.makeText(context, "已删除: ${folder.name}", Toast.LENGTH_SHORT).show()
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
		else -> {
			Quadruple(
				Color(0xFFE3F2FD),
				Color(0xFF1565C0),
				Color(0xFF1E88E5),
				"连接中",
			)
		}
	}

	Surface(
		shape = RoundedCornerShape(8.dp),
		color = bgColor,
		onClick = onClick,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp),
		) {
			Box(
				modifier = Modifier
					.size(5.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Text(
				text = label,
				fontSize = 11.sp,
				fontWeight = FontWeight.SemiBold,
				color = textColor,
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
				"正在连接…",
			)
		}
		routeInfo.isLan -> {
			Quadruple(
				Color(0xFFE8F5E9),
				Color(0xFF2E7D32),
				Color(0xFF43A047),
				"局域网直连",
			)
		}
		routeInfo.isDirect -> {
			val text = if (routeInfo.latencyMs > 0) "P2P直连 (${routeInfo.latencyMs}ms)" else "P2P直连"
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
				"中继代理",
			)
		}
		else -> {
			Quadruple(
				Color(0xFFE3F2FD),
				Color(0xFF1565C0),
				Color(0xFF1E88E5),
				"正在连接…",
			)
		}
	}

	Surface(
		shape = RoundedCornerShape(20.dp),
		color = bgColor,
		shadowElevation = 1.dp,
		onClick = onClick,
	) {
		Row(
			modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Box(
				modifier = Modifier
					.size(7.dp)
					.clip(CircleShape)
					.background(dotColor),
			)
			Text(
				text = label,
				fontSize = 11.sp,
				fontWeight = FontWeight.Bold,
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

/** 相册与影音共用稳定顶栏；标题占剩余宽度，次级操作收进菜单。 */
@Composable
private fun CollectionTopBar(
	title: String,
	subtitle: String,
	isInside: Boolean,
	activeTransferCount: Int,
	onUp: () -> Unit,
	onUpload: () -> Unit,
	onCreate: () -> Unit,
	onDelete: (() -> Unit)?,
	onTransfer: () -> Unit,
	onSettings: () -> Unit,
	routeInfo: RouteInfo? = null,
	onShowNetworkSheet: () -> Unit = {},
) {
	var menuOpen by remember { mutableStateOf(false) }
	Surface(color = MaterialTheme.colorScheme.surface) {
		Row(
			Modifier
				.fillMaxWidth()
				.heightIn(min = 54.dp)
				.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (isInside) {
				IconButton(onClick = onUp, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回全部")
				}
			}
			Column(
				modifier = Modifier
					.weight(1f)
					.padding(start = if (isInside) 2.dp else 8.dp),
				verticalArrangement = Arrangement.Center,
			) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
					if (routeInfo != null) {
						CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
					}
				}
				if (subtitle.isNotBlank()) {
					Spacer(Modifier.height(1.dp))
					Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}
			TransferButton(activeTransferCount, onTransfer)
			Box {
				IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
				}
				DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
					DropdownMenuItem(text = { Text("上传文件") }, onClick = { menuOpen = false; onUpload() })
					DropdownMenuItem(text = { Text("新建文件夹") }, onClick = { menuOpen = false; onCreate() })
					if (isInside && onDelete != null) {
						DropdownMenuItem(text = { Text("删除当前文件夹", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
					}
					DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onSettings() })
				}
			}
		}
	}
}

/** 传输入口保留独立触控区，数量提示不参与标题宽度分配。 */
@Composable
private fun TransferButton(count: Int, onClick: () -> Unit) {
	IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
		Box {
			Icon(Icons.Rounded.CloudDownload, contentDescription = if (count > 0) "传输中心，$count 个任务进行中" else "传输中心")
			if (count > 0) {
				Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd))
			}
		}
	}
}

@Composable
private fun PhotosTopBar(
	count: Int,
	selectedAlbumName: String,
	activeTransferCount: Int = 0,
	onUploadPhoto: () -> Unit,
	onUpAlbum: () -> Unit,
	onCreateAlbum: () -> Unit,
	onDeleteCurrentAlbum: (() -> Unit)? = null,
	onOpenTransferSheet: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit = {},
) {
	CollectionTopBar(
		title = selectedAlbumName.ifBlank { "云端相册" },
		subtitle = if (selectedAlbumName.isBlank()) "$count 张照片" else "相册 · $count 张照片",
		isInside = selectedAlbumName.isNotBlank(),
		activeTransferCount = activeTransferCount,
		onUp = onUpAlbum, onUpload = onUploadPhoto, onCreate = onCreateAlbum,
		onDelete = onDeleteCurrentAlbum, onTransfer = onOpenTransferSheet, onSettings = onOpenSettings,
		routeInfo = routeInfo, onShowNetworkSheet = onShowNetworkSheet,
	)
}

@Composable
private fun MediaTopBar(
	count: Int,
	selectedMediaName: String,
	activeTransferCount: Int = 0,
	onUploadVideo: () -> Unit,
	onUpMedia: () -> Unit,
	onCreateMediaFolder: () -> Unit,
	onDeleteCurrentMedia: (() -> Unit)? = null,
	onOpenTransferSheet: () -> Unit,
	routeInfo: RouteInfo,
	onOpenSettings: () -> Unit,
	onShowNetworkSheet: () -> Unit = {},
) {
	CollectionTopBar(
		title = selectedMediaName.ifBlank { "影音视界" },
		subtitle = if (selectedMediaName.isBlank()) "$count 个视频" else "影集 · $count 个视频",
		isInside = selectedMediaName.isNotBlank(),
		activeTransferCount = activeTransferCount,
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
				IconButton(onClick = onOpenUpdateDialog) {
					Box {
						Icon(
							imageVector = Icons.Rounded.CloudDownload,
							contentDescription = "发现新版本",
							tint = MaterialTheme.colorScheme.primary,
						)
						Box(
							modifier = Modifier
								.size(8.dp)
								.clip(CircleShape)
								.background(MaterialTheme.colorScheme.error)
								.align(Alignment.TopEnd)
						)
					}
				}
			}

			IconButton(onClick = onOpenSettings) {
				Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = MaterialTheme.colorScheme.onSurface)
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
	var isSearchActive by remember { mutableStateOf(false) }
	var menuOpen by remember { mutableStateOf(false) }
	val parts = remember(vm.path) { vm.path.trim('/').split('/').filter { it.isNotBlank() } }
	Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
		Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
			if (isSearchActive) {
				OutlinedTextField(
					value = vm.searchQuery,
					onValueChange = { vm.searchQuery = it },
					singleLine = true,
					placeholder = { Text("搜索文件名") },
					modifier = Modifier.weight(1f),
				)
				IconButton(onClick = { vm.searchQuery = ""; isSearchActive = false }, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.Close, contentDescription = "关闭搜索")
				}
			} else {
				if (parts.isNotEmpty()) {
					IconButton(onClick = onUp, modifier = Modifier.size(40.dp)) {
						Icon(Icons.Rounded.ArrowBack, contentDescription = "返回上一级")
					}
				}
				Column(
					modifier = Modifier.weight(1f).padding(start = if (parts.isEmpty()) 8.dp else 2.dp),
					verticalArrangement = Arrangement.Center,
				) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Text(
							parts.lastOrNull() ?: "我的文件",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.Bold,
							maxLines = 1, overflow = TextOverflow.Ellipsis,
						)
						CompactNetworkBadge(routeInfo = routeInfo, onClick = onShowNetworkSheet)
					}
					Text(
						"${vm.entries.size} 项文件与目录",
						style = MaterialTheme.typography.bodySmall,
						fontSize = 11.sp,
						fontWeight = FontWeight.Medium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1, overflow = TextOverflow.Ellipsis,
					)
				}
				IconButton(onClick = { isSearchActive = true }, modifier = Modifier.size(40.dp)) {
					Icon(Icons.Rounded.Search, contentDescription = "搜索")
				}
				TransferButton(vm.activeTransferCount, onOpenTransferSheet)
				Box {
					IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
						Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
					}
					DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
						DropdownMenuItem(text = { Text("上传文件") }, onClick = { menuOpen = false; onUpload() })
						DropdownMenuItem(text = { Text("新建文件夹") }, onClick = { menuOpen = false; onCreateFolder() })
						DropdownMenuItem(text = { Text("多选文件") }, onClick = { menuOpen = false; onToggleMultiSelect() })
						DropdownMenuItem(text = { Text("刷新") }, onClick = { menuOpen = false; vm.refresh() })
						DropdownMenuItem(text = { Text("设置") }, onClick = { menuOpen = false; onOpenSettings() })
					}
				}
			}
		}

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
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		items(categories) { (cat, title, icon) ->
			val active = cat == selected
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
				shadowElevation = if (active) 3.dp else 1.dp,
				onClick = { onSelect(cat) },
			) {
				Row(
					modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
						modifier = Modifier.size(14.dp),
					)
					Text(
						text = title,
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
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
		shape = RoundedCornerShape(12.dp),
		color = MaterialTheme.colorScheme.surface,
		shadowElevation = 1.dp,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				text = "已选中 $selectedCount 项",
				fontSize = 13.sp,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.onSurface,
			)
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
	Surface(
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		color = MaterialTheme.colorScheme.surface,
		shadowElevation = 16.dp,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp, vertical = 14.dp),
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
			modifier = Modifier.fillMaxSize(),
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
			// 夸克式极简纯净无框列表：纯白底、无卡片框、精致微留白、一屏容纳更多文件
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.surface),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
							.combinedClickable(
								onClick = { onOpenEntry(entry) },
								onLongClick = { onLongClickEntry(entry) },
							)
							.padding(horizontal = 14.dp, vertical = 7.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						// 左侧夸克同款文件徽标（微圆角，专属卡片或视频封面）
						FileThumbnail(entry = entry, urlOf = { vm.thumbnailUrlFor(it) }, size = 42.dp, customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null)

						// 中间主标题与单行元数据（保留完整文件名，如 LibreTV播放器.mp4）
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
								fontSize = 11.sp,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						// 右侧操作：仅在多选模式下显示选择框；日常长按任意条目即可进入多选
						if (isMulti) {
							SelectCheckbox(isSelected = isSelected)
						} else {
							IconButton(
								onClick = { onOpenAction(entry) },
								modifier = Modifier.size(48.dp),
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
					HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
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
				modifier = Modifier
					.fillMaxSize()
					.background(MaterialTheme.colorScheme.surface),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
							.combinedClickable(
								onClick = { onOpenEntry(entry) },
								onLongClick = { onLongClickEntry(entry) },
							)
							.padding(horizontal = 14.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						if (isMulti) {
							SelectCheckbox(isSelected = isSelected)
						}

						FileThumbnail(entry = entry, urlOf = { vm.thumbnailUrlFor(it) }, size = 32.dp, customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null)

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
					HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
				}
			}
		}

		ViewMode.Grid -> {
			LazyVerticalGrid(
				columns = GridCells.Fixed(2),
				contentPadding = PaddingValues(14.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					val imgUrl = if (entry.isImage) vm.thumbnailUrlFor(entry.path) else null
					Card(
						shape = RoundedCornerShape(14.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
						modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = { onOpenEntry(entry) },
								onLongClick = { onLongClickEntry(entry) },
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
							if (entry.isImage && !imgUrl.isNullOrBlank()) {
								AsyncImage(
									model = imgUrl,
									contentDescription = entry.name,
									contentScale = ContentScale.Crop,
									modifier = Modifier
										.fillMaxWidth()
										.aspectRatio(1.2f)
										.clip(RoundedCornerShape(8.dp))
										.background(MaterialTheme.colorScheme.outlineVariant),
								)
							} else {
								FileIconByEntry(entry = entry, size = 52.dp, customIconKey = if (entry.isDir) vm.folderIcons[entry.path.trim('/')] else null)
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

// ==================== 相册与影音 Tab ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTimelineContent(
	groups: List<com.nasdrop.TimelineGroup>,
	allAlbums: List<DavEntry>,
	selectedAlbumPath: String,
	isMulti: Boolean = false,
	selectedPaths: Set<String> = emptySet(),
	onToggleSelect: (DavEntry) -> Unit = {},
	onEnterMultiSelect: (DavEntry) -> Unit = {},
	onOpenAction: (DavEntry) -> Unit = {},
	onSelectAlbum: (String) -> Unit,
	onDeleteAlbum: (DavEntry) -> Unit,
	onCreateAlbum: () -> Unit,
	urlOf: (String) -> String?,
	onOpenImage: (DavEntry) -> Unit,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		// 1. 顶部子相册分类横滑条（紧凑轻盈胶囊）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// "全部照片" 胶囊
			item {
				val isAll = selectedAlbumPath.isBlank()
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = if (isAll) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
					shadowElevation = if (isAll) 2.dp else 1.dp,
					onClick = { onSelectAlbum("") },
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = if (isAll) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
						Text("全部照片", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAll) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface)
					}
				}
			}

			// 用户创建的各个子相册（支持长按直接删除）
			items(allAlbums) { album ->
				val isSelected = selectedAlbumPath == album.path
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
					shadowElevation = if (isSelected) 2.dp else 0.dp,
					modifier = Modifier.combinedClickable(
						onClick = { onSelectAlbum(album.path) },
						onLongClick = { onDeleteAlbum(album) },
					),
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onSecondary else Color(0xFFFF9500), modifier = Modifier.size(14.dp))
						Text(album.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
					}
				}
			}

			// 尾部新建专属相册快捷胶囊
			item {
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = MaterialTheme.colorScheme.secondaryContainer,
					onClick = onCreateAlbum,
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
						Text("新建相册", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
					}
				}
			}
		}

		// 2. 照片内容展示
		if (groups.isEmpty()) {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FileIcon(category = FileCategory.Image, isDir = false, size = 56.dp)
					Text("当前相册暂无照片", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text("点击上传按钮，添加照片到当前相册", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		} else {
			LazyVerticalGrid(
				columns = GridCells.Fixed(3),
				contentPadding = PaddingValues(4.dp),
				horizontalArrangement = Arrangement.spacedBy(3.dp),
				verticalArrangement = Arrangement.spacedBy(3.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				groups.forEach { group ->
					item(span = { GridItemSpan(3) }) {
						Row(
							modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(8.dp),
						) {
							Text(group.dateTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
							Text("${group.count}张", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
					}

					items(group.items, key = { it.path }) { item ->
						val isSelected = selectedPaths.contains(item.path)
						Box(
							modifier = Modifier
								.aspectRatio(1f)
								.clip(RoundedCornerShape(4.dp))
								.background(MaterialTheme.colorScheme.outlineVariant)
								.combinedClickable(
									onClick = {
										if (isMulti) {
											onToggleSelect(item)
										} else {
											onOpenImage(item)
										}
									},
									onLongClick = {
										if (!isMulti) {
											onEnterMultiSelect(item)
										}
									}
								),
						) {
							val thumbTarget = urlOf(item.path)
							AsyncImage(
								model = ImageRequest.Builder(LocalContext.current)
									.data(thumbTarget)
									.size(240, 240)
									.precision(coil.size.Precision.INEXACT)
									.allowRgb565(true)
									.crossfade(false)
									.build(),
								contentDescription = item.name,
								contentScale = ContentScale.Crop,
								modifier = Modifier.fillMaxSize(),
							)

							// 仅在多选模式下显示右上角勾选框（日常沉浸全图，长按才浮现选框）
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
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaStreamContent(
	videos: List<DavEntry>,
	allMediaFolders: List<DavEntry>,
	selectedMediaPath: String,
	isMulti: Boolean = false,
	selectedPaths: Set<String> = emptySet(),
	onToggleSelect: (DavEntry) -> Unit = {},
	onEnterMultiSelect: (DavEntry) -> Unit = {},
	onOpenAction: (DavEntry) -> Unit = {},
	onSelectMediaFolder: (String) -> Unit,
	onDeleteMediaFolder: (DavEntry) -> Unit,
	onCreateMediaFolder: () -> Unit,
	urlOf: (String) -> String?,
	onOpenVideo: (DavEntry) -> Unit,
) {
	Column(modifier = Modifier.fillMaxSize()) {
		// 1. 顶部影集/影视分类横滑栏（紧凑轻盈胶囊）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// "全部影视" 胶囊
			item {
				val isAll = selectedMediaPath.isBlank()
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = if (isAll) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
					shadowElevation = if (isAll) 2.dp else 1.dp,
					onClick = { onSelectMediaFolder("") },
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = if (isAll) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
						Text("全部影视", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAll) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface)
					}
				}
			}

			// 用户创建的各个影集文件夹（支持长按直接删除）
			items(allMediaFolders) { folder ->
				val isSelected = selectedMediaPath == folder.path
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
					shadowElevation = if (isSelected) 2.dp else 0.dp,
					modifier = Modifier.combinedClickable(
						onClick = { onSelectMediaFolder(folder.path) },
						onLongClick = { onDeleteMediaFolder(folder) },
					),
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(5.dp),
					) {
						Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onSecondary else Color(0xFFFF9500), modifier = Modifier.size(14.dp))
						Text(folder.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
					}
				}
			}

			// 尾部新建影集快捷胶囊
			item {
				Surface(
					shape = RoundedCornerShape(17.dp),
					color = MaterialTheme.colorScheme.secondaryContainer,
					onClick = onCreateMediaFolder,
				) {
					Row(
						modifier = Modifier.height(34.dp).padding(horizontal = 10.dp, vertical = 4.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
						Text("新建影集", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
					}
				}
			}
		}

		// 2. 视频内容展示：双列紧凑海报网格（一屏轻松容纳 6~8 部视频，高效且具备院线级视觉质感）
		if (videos.isEmpty()) {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FileIcon(category = FileCategory.Video, isDir = false, size = 56.dp)
					Text("当前分类暂无视频", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Text("点击上传按钮，添加视频到当前影集", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		} else {
			LazyVerticalGrid(
				columns = GridCells.Fixed(2),
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(videos, key = { it.path }) { video ->
					val isSelected = selectedPaths.contains(video.path)
					Card(
						shape = RoundedCornerShape(14.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
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
									}
								}
							),
					) {
						Column {
							// 16:9 院线质感海报视窗：优先展示服务端缩略图（OpenList type=thumb，真图片），
							// 无缩略图/加载失败时回退渐变 + 播放图标（零黑块、零等待）。
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
											.crossfade(true)
											.size(640, 360)
											.build(),
										contentDescription = video.name,
										contentScale = ContentScale.Crop,
										onError = { thumbFailed = true },
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

							// 紧凑双行信息 + 更多操作（非多选时提供单项管理）
							Row(
								modifier = Modifier
									.fillMaxWidth()
									.padding(horizontal = 8.dp, vertical = 8.dp),
								verticalAlignment = Alignment.CenterVertically,
							) {
								Column(modifier = Modifier.weight(1f)) {
									Text(
										text = video.displayName,
										fontSize = 12.sp,
										fontWeight = FontWeight.Bold,
										color = MaterialTheme.colorScheme.onSurface,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Spacer(modifier = Modifier.height(3.dp))
									Text(
										text = formatMeta(video),
										fontSize = 10.sp,
										color = MaterialTheme.colorScheme.onSurfaceVariant,
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								if (!isMulti) {
									IconButton(
										onClick = { onOpenAction(video) },
										modifier = Modifier.size(48.dp)
									) {
										Icon(
											Icons.Rounded.MoreVert,
											contentDescription = "管理",
											tint = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.size(16.dp)
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
	val finishedStates = setOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)
	val visibleTasks = tasks.filter {
		when (filter) {
			1 -> it.isUpload
			2 -> !it.isUpload
			3 -> it.status == TransferStatus.COMPLETED
			else -> true
		}
	}.sortedBy { it.status in finishedStates }
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
			Text(
				"${tasks.count { it.status !in finishedStates }} 个进行中 · ${tasks.count { it.status == TransferStatus.COMPLETED }} 个完成 · ${tasks.count { it.status == TransferStatus.FAILED }} 个失败 · ${tasks.count { it.status == TransferStatus.CANCELLED }} 个取消",
				style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			if (totalSpeedBps > 0) {
				Text("当前总速率 ${formatSize(totalSpeedBps)}/s", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
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
				LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
					items(visibleTasks, key = { it.id }) { task ->
						val terminal = task.status in finishedStates
						val statusText = when (task.status) {
							TransferStatus.RUNNING -> if (task.isUpload) "正在上传" else "正在下载"
							TransferStatus.CONFIRMING -> "正在写入存储…"
							TransferStatus.QUEUED -> "排队等待中"
							TransferStatus.PAUSED -> "已暂停"
							TransferStatus.COMPLETED -> if (task.isUpload) "上传完成" else "下载完成"
							TransferStatus.FAILED -> "传输失败"
							TransferStatus.CANCELLED -> "已取消"
						}
						Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
							Column(Modifier.fillMaxWidth().padding(16.dp)) {
								Row(verticalAlignment = Alignment.CenterVertically) {
									Icon(if (task.isUpload) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDownload, contentDescription = if (task.isUpload) "上传" else "下载", tint = MaterialTheme.colorScheme.primary)
									Text(task.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 10.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
								}
								Text(statusText, style = MaterialTheme.typography.bodyMedium, color = if (task.status == TransferStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 10.dp))
								if (task.status == TransferStatus.FAILED && !task.error.isNullOrBlank()) {
									Text(task.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
								}
								if (!terminal) {
									if (task.status == TransferStatus.CONFIRMING) {
										LinearProgressIndicator(progress = { 0.99f }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
									} else if (task.status == TransferStatus.RUNNING && task.totalBytes <= 0) {
										LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
									} else {
										LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
									}
								}
								Text(
									"${formatSize(task.sentBytes)} / ${if (task.totalBytes > 0) formatSize(task.totalBytes) else "大小未知"}${if (task.status == TransferStatus.RUNNING && task.speedStr.isNotBlank()) " · ${task.speedStr}" else ""}",
									style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
								)
								LazyRow {
									if (!task.isUpload && task.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED)) {
										item { TextButton(onClick = { onPauseTask(task.id) }) { Text("暂停") } }
									}
									if (!task.isUpload && task.status == TransferStatus.PAUSED) {
										item { TextButton(onClick = { onResumeTask(task.id) }) { Text("继续") } }
									}
									if (task.status in setOf(TransferStatus.FAILED, TransferStatus.CANCELLED)) {
										item { TextButton(onClick = { onRetryTask(task.id) }) { Text("重试") } }
									}
									if (task.status == TransferStatus.COMPLETED) {
										item { TextButton(onClick = { onOpenResult(task.id) }) { Text(if (task.isUpload) "查看位置" else "打开文件") } }
									}
									item {
										TextButton(onClick = { onCancelTask(task.id) }) {
											Text(if (terminal) "清除" else "取消", color = if (terminal) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
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

/**
 * 智能网络管家抽屉
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
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Text("网络连接与模式控制", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
			Spacer(modifier = Modifier.height(14.dp))

			// 网络质量大卡片
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = MaterialTheme.colorScheme.surfaceVariant,
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(14.dp)) {
					HumanNetworkPill(routeInfo = routeInfo, onClick = {})
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = if (routeInfo.isDirect) "当前通过直连高速访问 NAS。"
						else "当前通过 Tailscale 中继转发，速度受 DERP 服务器带宽限制。若在同网络或已配置系统组网，可强制锁定直连。",
						fontSize = 12.sp,
						color = MaterialTheme.colorScheme.onSurface,
						lineHeight = 18.sp,
					)
					Spacer(modifier = Modifier.height(4.dp))
					Text(
						text = "当前工作模式：${currentMode.title}（${currentMode.desc}）",
						fontSize = 11.sp,
						color = MaterialTheme.colorScheme.primary,
						fontWeight = FontWeight.Medium,
					)
				}
			}

			Spacer(modifier = Modifier.height(14.dp))

			// 快捷强制直连 / 恢复自动
			if (currentMode != com.nasdrop.ConnectionMode.Direct) {
				Button(
					onClick = { onSwitchMode(com.nasdrop.ConnectionMode.Direct) },
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.fillMaxWidth()
						.height(48.dp),
				) {
					Icon(Icons.Rounded.ElectricBolt, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
					Spacer(modifier = Modifier.width(6.dp))
					Text("⚡ 强制锁定【仅直连模式】", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiary)
				}
				Spacer(modifier = Modifier.height(10.dp))
			} else {
				OutlinedButton(
					onClick = { onSwitchMode(com.nasdrop.ConnectionMode.Auto) },
					shape = RoundedCornerShape(14.dp),
					modifier = Modifier
						.fillMaxWidth()
						.height(48.dp),
				) {
					Icon(Icons.Rounded.Autorenew, contentDescription = null)
					Spacer(modifier = Modifier.width(6.dp))
					Text("切回【智能自动模式】", fontWeight = FontWeight.Bold)
				}
				Spacer(modifier = Modifier.height(10.dp))
			}

			Button(
				onClick = onTriggerPunch,
				colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
				Spacer(modifier = Modifier.width(6.dp))
				Text("重新探测直连与打洞", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
			}

			Spacer(modifier = Modifier.height(10.dp))
			Button(
				onClick = onOpenSettings,
				colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
				Spacer(modifier = Modifier.width(6.dp))
				Text("配置服务参数", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
			}

			Spacer(modifier = Modifier.height(24.dp))
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

			Spacer(modifier = Modifier.height(16.dp))

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
		Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
	Surface(
		color = MaterialTheme.colorScheme.surface,
		shadowElevation = 8.dp,
		modifier = Modifier
			.fillMaxWidth()
			.height(50.dp),
	) {
		Column {
			HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
			Row(
				modifier = Modifier.fillMaxSize(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				CompactTabItem(
					title = "文件",
					icon = Icons.Rounded.FolderOpen,
					selected = currentTab == MainTab.Files,
					onClick = { onTabSelect(MainTab.Files) },
					modifier = Modifier.weight(1f),
				)
				CompactTabItem(
					title = "相册",
					icon = Icons.Rounded.PhotoLibrary,
					selected = currentTab == MainTab.Photos,
					onClick = { onTabSelect(MainTab.Photos) },
					modifier = Modifier.weight(1f),
				)
				CompactTabItem(
					title = "影音",
					icon = Icons.Rounded.VideoLibrary,
					selected = currentTab == MainTab.Media,
					onClick = { onTabSelect(MainTab.Media) },
					modifier = Modifier.weight(1f),
				)
			}
		}
	}
}

@Composable
private fun CompactTabItem(
	title: String,
	icon: ImageVector,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
	Column(
		modifier = modifier
			.fillMaxHeight()
			.clickable(onClick = onClick)
			.padding(vertical = 5.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		// 图标与文字垂直居中且紧贴，去掉多余留白
		verticalArrangement = Arrangement.Center,
	) {
		Icon(
			imageVector = icon,
			contentDescription = title,
			tint = color,
			modifier = Modifier.size(19.dp),
		)
		Text(
			text = title,
			fontSize = 10.sp,
			fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
			color = color,
		)
	}
}

internal fun formatMeta(entry: DavEntry): String {
	val dateStr = if (entry.modified > 0) formatQuarkDate(entry.modified) else ""
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
	if (currentTab == MainTab.Me) return

	BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
		val density = LocalDensity.current
		val boxWidthPx = constraints.maxWidth.toFloat()
		val boxHeightPx = constraints.maxHeight.toFloat()

		val fabSizeDp = 52.dp
		val fabSizePx = with(density) { fabSizeDp.toPx() }
		val marginPx = with(density) { 16.dp.toPx() }

		// 默认吸附在可视区右下角：距离右边缘 16dp，距离底部 16dp
		var offsetX by remember(boxWidthPx) { mutableFloatStateOf((boxWidthPx - fabSizePx - marginPx).coerceAtLeast(0f)) }
		var offsetY by remember(boxHeightPx) { mutableFloatStateOf((boxHeightPx - fabSizePx - marginPx).coerceAtLeast(0f)) }
		var isDragging by remember { mutableStateOf(false) }

		val (bgColor, icon, label) = when (currentTab) {
			MainTab.Files -> Triple(MaterialTheme.colorScheme.primary, Icons.Rounded.CloudUpload, "上传文件")
			MainTab.Photos -> Triple(MaterialTheme.colorScheme.secondary, Icons.Rounded.PhotoCamera, "上传照片")
			MainTab.Media -> Triple(MaterialTheme.colorScheme.secondary, Icons.Rounded.Movie, "上传视频")
			MainTab.Me -> Triple(Color.Transparent, Icons.Rounded.Add, "")
		}

		Surface(
			shape = CircleShape,
			color = bgColor,
			shadowElevation = if (isDragging) 12.dp else 6.dp,
			modifier = Modifier
				.offset {
					val curX = offsetX.coerceIn(marginPx, (boxWidthPx - fabSizePx - marginPx).coerceAtLeast(marginPx))
					val curY = offsetY.coerceIn(marginPx, (boxHeightPx - fabSizePx - marginPx).coerceAtLeast(marginPx))
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
							offsetY = (offsetY + dragAmount.y).coerceIn(marginPx, boxHeightPx - fabSizePx - marginPx)
						}
					)
				}
				.clickable {
					when (currentTab) {
						MainTab.Files -> onUploadFile()
						MainTab.Photos -> onUploadPhoto()
						MainTab.Media -> onUploadVideo()
						MainTab.Me -> {}
					}
				},
		) {
			Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
					Icon(imageVector = icon, contentDescription = label, tint = if (currentTab == MainTab.Files) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary, modifier = Modifier.size(24.dp))
			}
		}
	}
}
