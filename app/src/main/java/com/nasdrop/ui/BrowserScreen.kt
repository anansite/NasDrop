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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
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
) {
	val context = LocalContext.current
	val routeInfo by vm.routeInfo.collectAsState()

	// 模态弹窗状态
	var activeEntryForAction by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForRename by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForMove by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForDelete by remember { mutableStateOf<DavEntry?>(null) }
	// showCreateDialog replaced by activeCreateType
	var showTransferSheet by remember { mutableStateOf(false) }
	var showNetSheet by remember { mutableStateOf(false) }

	var activeCreateType by remember { mutableStateOf<CreateDialogType?>(null) }
	var activeFolderForDelete by remember { mutableStateOf<Pair<DavEntry, Boolean>?>(null) }
	var showBatchDeleteConfirm by remember { mutableStateOf(false) }
	var showBatchMoveSheet by remember { mutableStateOf(false) }

	// 系统文件选择器（上传）
	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			UploadService.start(context, uris, vm.path)
			Toast.makeText(context, "已加入上传队列: ${uris.size} 个文件", Toast.LENGTH_SHORT).show()
		}
	}

	val photoPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedAlbumPath.isNotBlank()) vm.selectedAlbumPath else "Photos"
			UploadService.start(context, uris, targetDir)
			Toast.makeText(context, "已加入上传队列: ${uris.size} 张照片", Toast.LENGTH_SHORT).show()
		}
	}

	val videoPicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			val targetDir = if (vm.selectedMediaPath.isNotBlank()) vm.selectedMediaPath else "Movies"
			UploadService.start(context, uris, targetDir)
			Toast.makeText(context, "已加入上传队列: ${uris.size} 部视频", Toast.LENGTH_SHORT).show()
		}
	}

	Scaffold(
		containerColor = Color(0xFFF7F8FA),
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
				// 主流商业网盘标准四栏 Tab
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
						onRefresh = { vm.refresh() },
						onOpenTransferSheet = { showTransferSheet = true },
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
						onRefresh = { vm.refresh() },
						onOpenTransferSheet = { showTransferSheet = true },
					)
				}

				MainTab.Me -> {
					NetworkTopBar(
						routeInfo = routeInfo,
						hasUpdate = vm.hasUpdate,
						onOpenUpdateDialog = { vm.openUpdateDialog() },
						onOpenSettings = onOpenSettings,
					)
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

			// 主内容区域根据 Tab 呈现
			Box(modifier = Modifier.weight(1f)) {
				when (vm.currentTab) {
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
							urlOf = { vm.urlFor(it) },
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
							urlOf = { vm.urlFor(it) },
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

				if (vm.loading) {
					CircularProgressIndicator(
						modifier = Modifier.align(Alignment.Center),
						color = Color(0xFF2A72F6),
					)
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

	// 1. 统一底部文件操作抽屉
	activeEntryForAction?.let { entry ->
		ActionBottomSheet(
			entry = entry,
			urlOf = { vm.urlFor(it) },
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
		)
	}

	// 2. 传输中心管理抽屉
	if (showTransferSheet) {
		TransferCenterSheet(
			tasks = vm.transferTasks,
			totalSpeedBps = vm.totalSpeedBps,
			onDismiss = { showTransferSheet = false },
			onPauseTask = { vm.pauseTask(it) },
			onResumeTask = { vm.resumeTask(it) },
			onCancelTask = { vm.cancelTask(it) },
			onPauseAll = { vm.pauseAll() },
			onResumeAll = { vm.resumeAll() },
		)
	}

	// 3. 智能网络管家抽屉
	if (showNetSheet) {
		NetworkManagerSheet(
			routeInfo = routeInfo,
			onDismiss = { showNetSheet = false },
			onTriggerPunch = { vm.punchHole() },
			onOpenSettings = onOpenSettings,
		)
	}

	// 4. 重命名对话框
	activeEntryForRename?.let { entry ->
		var newName by remember { mutableStateOf(entry.name) }
		AlertDialog(
			onDismissRequest = { activeEntryForRename = null },
			title = { Text("重命名", fontWeight = FontWeight.Bold) },
			text = {
				OutlinedTextField(
					value = newName,
					onValueChange = { newName = it },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
			},
			confirmButton = {
				Button(
					onClick = {
						val target = newName.trim()
						if (target.isNotBlank() && target != entry.name) vm.rename(entry, target)
						activeEntryForRename = null
					},
					colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
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
				vm.move(entry, destDir)
				activeEntryForMove = null
				Toast.makeText(context, "已成功移动到: /${destDir.ifEmpty { "根目录" }}", Toast.LENGTH_SHORT).show()
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
					colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4F)),
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
				val count = vm.selectedEntries.size
				vm.batchMove(destDir) {
					Toast.makeText(context, "已将 $count 项内容移动到: /${destDir.ifEmpty { "根目录" }}", Toast.LENGTH_SHORT).show()
				}
				showBatchMoveSheet = false
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
					colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4F)),
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
private fun HumanNetworkPill(routeInfo: RouteInfo, onClick: () -> Unit) {
	val (bgColor, textColor, dotColor, label) = when {
		routeInfo.isDirect && routeInfo.endpoint.isEmpty() -> {
			Quadruple(Color(0xFFE8F9F2), Color(0xFF00A870), Color(0xFF00B578), "🏡 在家直连 · 1ms")
		}
		routeInfo.isDirect -> {
			Quadruple(Color(0xFFEAF2FF), Color(0xFF2A72F6), Color(0xFF2A72F6), "🚀 远程专线 · 极速")
		}
		else -> {
			Quadruple(Color(0xFFFFF4E5), Color(0xFFE66A00), Color(0xFFFF7A00), "🐢 远程顺畅 · 自动加速中")
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
		color = Color.White,
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
						color = Color(0xFF1D2129),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Text(
						"可点击下方文件夹进入子目录，选定后点击确认存入",
						fontSize = 11.sp,
						color = Color(0xFF86909C),
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
				colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier.fillMaxWidth().height(46.dp),
			) {
				Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
				Spacer(modifier = Modifier.width(6.dp))
				Text("存入当前目录", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
			}
		}
	}
}

// ==================== 顶部导航栏与状态胶囊 ====================

@Composable
private fun PhotosTopBar(
	count: Int,
	selectedAlbumName: String,
	activeTransferCount: Int = 0,
	onUploadPhoto: () -> Unit,
	onUpAlbum: () -> Unit,
	onCreateAlbum: () -> Unit,
	onDeleteCurrentAlbum: (() -> Unit)? = null,
	onRefresh: () -> Unit,
	onOpenTransferSheet: () -> Unit,
) {
	// 固定 44dp 行高，平滑统一面包屑，杜绝跳动
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(44.dp)
			.background(Color.White)
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
			if (selectedAlbumName.isNotBlank()) {
				IconButton(
					onClick = onUpAlbum,
					modifier = Modifier.size(28.dp),
				) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回全部相册", tint = Color(0xFF2A72F6), modifier = Modifier.size(18.dp))
				}
				Text("全部相册", fontSize = 14.sp, color = Color(0xFF86909C), modifier = Modifier.clickable(onClick = onUpAlbum))
				Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFFC9CDD4), modifier = Modifier.size(14.dp))
				Text(selectedAlbumName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1)
			} else {
				Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = Color(0xFFFF3D71), modifier = Modifier.size(20.dp))
				Spacer(modifier = Modifier.width(2.dp))
				Text("云端相册", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
				Text("· $count 张", fontSize = 12.sp, color = Color(0xFF86909C))
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
			IconButton(onClick = onUploadPhoto, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.PhotoCamera, contentDescription = "上传照片", tint = Color(0xFFFF3D71), modifier = Modifier.size(20.dp))
			}
			if (selectedAlbumName.isNotBlank() && onDeleteCurrentAlbum != null) {
				IconButton(onClick = onDeleteCurrentAlbum, modifier = Modifier.size(32.dp)) {
					Icon(Icons.Rounded.Delete, contentDescription = "删除当前相册", tint = Color(0xFFFF4D4F), modifier = Modifier.size(19.dp))
				}
			}
			IconButton(onClick = onCreateAlbum, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建专属相册", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
			}
			IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
			}
			IconButton(onClick = onOpenTransferSheet, modifier = Modifier.size(32.dp)) {
				Box(contentAlignment = Alignment.Center) {
					Icon(Icons.Rounded.CloudDownload, contentDescription = "传输中心", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
					if (activeTransferCount > 0) {
						Box(
							modifier = Modifier
								.size(7.dp)
								.clip(CircleShape)
								.background(Color(0xFF2A72F6))
								.align(Alignment.TopEnd)
						)
					}
				}
			}
		}
	}
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
	onRefresh: () -> Unit,
	onOpenTransferSheet: () -> Unit,
) {
	// 固定 44dp 行高，平滑统一面包屑，杜绝跳动
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.height(44.dp)
			.background(Color.White)
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
			if (selectedMediaName.isNotBlank()) {
				IconButton(
					onClick = onUpMedia,
					modifier = Modifier.size(28.dp),
				) {
					Icon(Icons.Rounded.ArrowBack, contentDescription = "返回全部影视", tint = Color(0xFF2A72F6), modifier = Modifier.size(18.dp))
				}
				Text("全部影视", fontSize = 14.sp, color = Color(0xFF86909C), modifier = Modifier.clickable(onClick = onUpMedia))
				Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFFC9CDD4), modifier = Modifier.size(14.dp))
				Text(selectedMediaName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1)
			} else {
				Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = Color(0xFF722ED1), modifier = Modifier.size(20.dp))
				Spacer(modifier = Modifier.width(2.dp))
				Text("影音视界", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
				Text("· $count 部", fontSize = 12.sp, color = Color(0xFF86909C))
			}
		}

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
			IconButton(onClick = onUploadVideo, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.Movie, contentDescription = "上传视频", tint = Color(0xFF722ED1), modifier = Modifier.size(20.dp))
			}
			if (selectedMediaName.isNotBlank() && onDeleteCurrentMedia != null) {
				IconButton(onClick = onDeleteCurrentMedia, modifier = Modifier.size(32.dp)) {
					Icon(Icons.Rounded.Delete, contentDescription = "删除当前影集", tint = Color(0xFFFF4D4F), modifier = Modifier.size(19.dp))
				}
			}
			IconButton(onClick = onCreateMediaFolder, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建专属影集", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
			}
			IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
				Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
			}
			IconButton(onClick = onOpenTransferSheet, modifier = Modifier.size(32.dp)) {
				Box(contentAlignment = Alignment.Center) {
					Icon(Icons.Rounded.CloudDownload, contentDescription = "传输中心", tint = Color(0xFF4E5969), modifier = Modifier.size(19.dp))
					if (activeTransferCount > 0) {
						Box(
							modifier = Modifier
								.size(7.dp)
								.clip(CircleShape)
								.background(Color(0xFF2A72F6))
								.align(Alignment.TopEnd)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NetworkTopBar(
	routeInfo: RouteInfo,
	hasUpdate: Boolean,
	onOpenUpdateDialog: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.background(Color(0xFFF7F8FA))
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text("服务与设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))

		Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			HumanNetworkPill(routeInfo = routeInfo, onClick = {})

			if (hasUpdate) {
				IconButton(onClick = onOpenUpdateDialog) {
					Box {
						Icon(
							imageVector = Icons.Rounded.CloudDownload,
							contentDescription = "发现新版本",
							tint = Color(0xFF2A72F6),
						)
						Box(
							modifier = Modifier
								.size(8.dp)
								.clip(CircleShape)
								.background(Color(0xFFFF4D4F))
								.align(Alignment.TopEnd)
						)
					}
				}
			}

			IconButton(onClick = onOpenSettings) {
				Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = Color(0xFF4E5969))
			}
		}
	}
}

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
) {
	var isSearchActive by remember { mutableStateOf(false) }
	val parts = remember(vm.path) {
		if (vm.path.isBlank()) emptyList()
		else vm.path.trim('/').split('/')
	}

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(Color.White),
	) {
		// 第 1 行：夸克同款极简单层顶栏（返回/标题 + 搜索 + 新建 + 刷新 + 传输列表）
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(46.dp)
				.padding(horizontal = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (isSearchActive) {
				// 原地平滑展开搜索条
				Surface(
					shape = RoundedCornerShape(20.dp),
					color = Color(0xFFF7F8FA),
					modifier = Modifier
						.weight(1f)
						.height(36.dp),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF2A72F6), modifier = Modifier.size(18.dp))
						BasicTextField(
							value = vm.searchQuery,
						onValueChange = { vm.searchQuery = it },
							singleLine = true,
							modifier = Modifier.weight(1f),
							decorationBox = { innerTextField ->
								if (vm.searchQuery.isEmpty()) {
									Text("搜索文件名...", fontSize = 13.sp, color = Color(0xFF86909C))
								}
								innerTextField()
							},
						)
						if (vm.searchQuery.isNotEmpty()) {
							IconButton(
								onClick = { vm.searchQuery = "" },
								modifier = Modifier.size(24.dp),
							) {
								Icon(Icons.Rounded.Close, contentDescription = "清空", tint = Color(0xFF86909C), modifier = Modifier.size(16.dp))
							}
						}
					}
				}

				TextButton(
					onClick = {
						vm.searchQuery = ""
						isSearchActive = false
					},
					modifier = Modifier.padding(start = 4.dp),
				) {
					Text("取消", fontSize = 13.sp, color = Color(0xFF2A72F6), fontWeight = FontWeight.Bold)
				}
			} else {
				// 常态：左侧标题与返回
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					if (parts.isNotEmpty()) {
						IconButton(
							onClick = onUp,
							modifier = Modifier.size(30.dp),
						) {
							Icon(
								imageVector = Icons.Rounded.ArrowBack,
								contentDescription = "返回上一级",
								tint = Color(0xFF1D2129),
								modifier = Modifier.size(20.dp),
							)
						}
						Text(
							text = parts.last(),
							fontSize = 17.sp,
							fontWeight = FontWeight.Bold,
							color = Color(0xFF1D2129),
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					} else {
						Icon(
							imageVector = Icons.Rounded.FolderOpen,
							contentDescription = null,
							tint = Color(0xFF2A72F6),
							modifier = Modifier.size(22.dp),
						)
						Text(
							text = "我的文件",
							fontSize = 18.sp,
							fontWeight = FontWeight.Bold,
							color = Color(0xFF1D2129),
						)
					}
				}

				// 右侧极简操作（上传 + 搜索 + 新建 + 刷新 + 传输中心）
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(2.dp),
				) {
					IconButton(
						onClick = onUpload,
						modifier = Modifier.size(32.dp),
					) {
						Icon(Icons.Rounded.CloudUpload, contentDescription = "上传文件", tint = Color(0xFF2A72F6), modifier = Modifier.size(21.dp))
					}
					IconButton(
						onClick = { isSearchActive = true },
						modifier = Modifier.size(32.dp),
					) {
						Icon(Icons.Rounded.Search, contentDescription = "搜索", tint = Color(0xFF4E5969), modifier = Modifier.size(20.dp))
					}
					IconButton(
						onClick = onCreateFolder,
						modifier = Modifier.size(32.dp),
					) {
						Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建文件夹", tint = Color(0xFF4E5969), modifier = Modifier.size(20.dp))
					}
					IconButton(
						onClick = { vm.refresh() },
						modifier = Modifier.size(32.dp),
					) {
						Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFF4E5969), modifier = Modifier.size(20.dp))
					}
					IconButton(
						onClick = onOpenTransferSheet,
						modifier = Modifier.size(32.dp),
					) {
						Box(contentAlignment = Alignment.Center) {
							Icon(Icons.Rounded.CloudDownload, contentDescription = "传输列表", tint = Color(0xFF4E5969), modifier = Modifier.size(20.dp))
							if (vm.activeTransferCount > 0) {
								Box(
									modifier = Modifier
										.size(7.dp)
										.clip(CircleShape)
										.background(Color(0xFF2A72F6))
										.align(Alignment.TopEnd)
								)
							}
						}
					}
				}
			}
		}

		// 第 2 行：夸克同款精致面包屑路径（固定 26dp 高度，平滑杜绝高度跳动）
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(26.dp)
				.padding(horizontal = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			LazyRow(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				item {
					val isCurrent = parts.isEmpty()
					Text(
						text = "NAS 私有云",
						fontSize = 12.sp,
						color = if (isCurrent) Color(0xFF2A72F6) else Color(0xFF86909C),
						modifier = Modifier
							.clip(RoundedCornerShape(4.dp))
							.clickable { onNavigate("") }
							.padding(horizontal = 2.dp, vertical = 2.dp),
					)
				}
				items(parts.indices.toList()) { i ->
					Text("/", fontSize = 11.sp, color = Color(0xFFC9CDD4))
					val isLast = i == parts.size - 1
					val p = parts.take(i + 1).joinToString("/")
					Text(
						text = parts[i],
						fontSize = 12.sp,
						color = if (isLast) Color(0xFF1D2129) else Color(0xFF86909C),
						fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
						modifier = Modifier
							.clip(RoundedCornerShape(4.dp))
							.clickable { onNavigate(p) }
							.padding(horizontal = 2.dp, vertical = 2.dp),
					)
				}
			}
		}

		// 第 3 行：夸克同款排序与视图切换条（左侧：按修改时间 ↓；右侧：纯线框四宫格图标）
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(32.dp)
				.padding(horizontal = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Row(
				modifier = Modifier
					.clip(RoundedCornerShape(6.dp))
					.clickable { vm.cycleSortOrder() }
					.padding(vertical = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					text = vm.sortOrder.label,
					fontSize = 12.sp,
					fontWeight = FontWeight.Medium,
					color = Color(0xFF4E5969),
				)
			}

			IconButton(
				onClick = onToggleViewMode,
				modifier = Modifier.size(28.dp),
			) {
				Icon(
					imageVector = if (vm.viewMode == ViewMode.Cards) Icons.Rounded.GridView else Icons.Rounded.ViewHeadline,
					contentDescription = "切换视图",
					tint = Color(0xFF4E5969),
					modifier = Modifier.size(19.dp),
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
				color = if (active) Color(0xFF2A72F6) else Color.White,
				shadowElevation = if (active) 3.dp else 1.dp,
				onClick = { onSelect(cat) },
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						tint = if (active) Color.White else Color(0xFF4E5969),
						modifier = Modifier.size(14.dp),
					)
					Text(
						text = title,
						fontSize = 12.sp,
						fontWeight = FontWeight.SemiBold,
						color = if (active) Color.White else Color(0xFF4E5969),
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
		color = Color.White,
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
				color = Color(0xFF1D2129),
			)
			Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Text(
					text = "全选",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = Color(0xFF2A72F6),
					modifier = Modifier.clickable { onSelectAll() },
				)
				Text(
					text = "完成",
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
					color = Color(0xFF2A72F6),
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
		color = Color(0xFF1D2129),
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
			BatchActionItem(icon = Icons.Rounded.Delete, label = "彻底删除", onClick = onDelete, tint = Color(0xFFFF4D4F))
			BatchActionItem(icon = Icons.Rounded.ArrowBack, label = "取消", onClick = onCancel)
		}
	}
}

@Composable
private fun BatchActionItem(
	icon: ImageVector,
	label: String,
	onClick: () -> Unit,
	tint: Color = Color.White,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.clickable { onClick() },
	) {
		Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
		Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
	}
}

// ==================== 文件浏览内容区 ====================

@OptIn(ExperimentalFoundationApi::class)
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
					color = Color(0xFF86909C),
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
					.background(Color.White),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.combinedClickable(
								onClick = { onOpenEntry(entry) },
								onLongClick = { onLongClickEntry(entry) },
							)
							.padding(horizontal = 14.dp, vertical = 7.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
					) {
						// 左侧夸克同款文件徽标（微圆角，专属卡片或视频封面）
						FileThumbnail(entry = entry, urlOf = { vm.urlFor(it) }, size = 42.dp)

						// 中间主标题与单行元数据（保留完整文件名，如 LibreTV播放器.mp4）
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = entry.name,
								fontSize = 14.sp,
								fontWeight = FontWeight.Medium,
								color = Color(0xFF1D2129),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 11.sp,
								color = Color(0xFF86909C),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						// 右侧操作：夸克同款空心圆圈勾选 + 更多菜单点
						if (isMulti) {
							SelectCheckbox(isSelected = isSelected)
						} else {
							Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
								// 夸克同款轻量空心圆圈（点击直接进入多选并选中）
								Box(
									modifier = Modifier
										.size(20.dp)
										.clip(CircleShape)
										.border(1.dp, Color(0xFFC9CDD4), CircleShape)
										.clickable {
											vm.enterMultiSelect(entry)
										},
								)
								IconButton(
									onClick = { onOpenAction(entry) },
									modifier = Modifier.size(24.dp),
								) {
									Icon(
										Icons.Rounded.MoreVert,
										contentDescription = "更多",
										tint = Color(0xFFC9CDD4),
										modifier = Modifier.size(16.dp),
									)
								}
							}
						}
					}
					HorizontalDivider(color = Color(0xFFF7F8FA), thickness = 0.5.dp)
				}

				item {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 18.dp),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = "${items.size}项  已同步至私有云NAS",
							fontSize = 11.sp,
							color = Color(0xFFC9CDD4),
						)
					}
				}
			}
		}

		ViewMode.Compact -> {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.background(Color.White),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
					Row(
						modifier = Modifier
							.fillMaxWidth()
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

						FileThumbnail(entry = entry, urlOf = { vm.urlFor(it) }, size = 32.dp)

						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = entry.displayName,
								fontSize = 13.sp,
								fontWeight = FontWeight.SemiBold,
								color = Color(0xFF1D2129),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 11.sp,
								color = Color(0xFF86909C),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
						}

						if (!isMulti) {
							IconButton(onClick = { onOpenAction(entry) }, modifier = Modifier.size(24.dp)) {
								Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = Color(0xFF86909C), modifier = Modifier.size(16.dp))
							}
						}
					}
					HorizontalDivider(color = Color(0xFFF2F3F5), thickness = 0.5.dp)
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
					val imgUrl = if (entry.isImage) vm.urlFor(entry.path) else null
					Card(
						shape = RoundedCornerShape(14.dp),
						colors = CardDefaults.cardColors(containerColor = Color.White),
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
											.size(24.dp)
											.align(Alignment.TopEnd),
									) {
										Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = Color(0xFF86909C), modifier = Modifier.size(16.dp))
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
										.background(Color(0xFFE5E6EB)),
								)
							} else {
								FileIcon(category = entry.category, isDir = entry.isDir, isAlbum = entry.isAlbum, size = 52.dp)
							}
							Spacer(modifier = Modifier.height(8.dp))

							Text(
								text = entry.displayName,
								fontSize = 13.sp,
								fontWeight = FontWeight.Bold,
								color = Color(0xFF1D2129),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(modifier = Modifier.height(2.dp))
							Text(
								text = formatMeta(entry),
								fontSize = 10.sp,
								color = Color(0xFF86909C),
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
		color = if (isSelected) Color(0xFF2A72F6) else Color.Transparent,
		border = BorderStroke(2.dp, if (isSelected) Color(0xFF2A72F6) else Color(0xFFC9CDD4)),
		modifier = modifier.size(20.dp),
	) {
		if (isSelected) {
			Box(contentAlignment = Alignment.Center) {
				Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
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
		// 1. 顶部子相册分类横滑条（让相册中创建的子相册/文件夹一目了然）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// "全部照片" 胶囊
			item {
				val isAll = selectedAlbumPath.isBlank()
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = if (isAll) Color(0xFFFF3D71) else Color.White,
					shadowElevation = if (isAll) 3.dp else 1.dp,
					onClick = { onSelectAlbum("") },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = if (isAll) Color.White else Color(0xFFFF3D71), modifier = Modifier.size(15.dp))
						Text("全部照片", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAll) Color.White else Color(0xFF1D2129))
					}
				}
			}

			// 用户创建的各个子相册（支持长按直接删除）
			items(allAlbums) { album ->
				val isSelected = selectedAlbumPath == album.path
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = if (isSelected) Color(0xFFFF3D71) else Color(0xFFF7F8FA),
					shadowElevation = if (isSelected) 2.dp else 0.dp,
					modifier = Modifier.combinedClickable(
						onClick = { onSelectAlbum(album.path) },
						onLongClick = { onDeleteAlbum(album) },
					),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFFF9500), modifier = Modifier.size(15.dp))
						Text(album.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color(0xFF1D2129))
					}
				}
			}

			// 尾部新建专属相册快捷胶囊
			item {
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = Color(0xFFFFF0F5),
					onClick = onCreateAlbum,
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFFFF3D71), modifier = Modifier.size(15.dp))
						Text("新建相册", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3D71))
					}
				}
			}
		}

		// 2. 照片内容展示
		if (groups.isEmpty()) {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FileIcon(category = FileCategory.Image, isDir = false, size = 56.dp)
					Text("当前相册暂无照片", fontSize = 14.sp, color = Color(0xFF86909C))
					Text("可点击右下角「+ 上传照片」直接存入此相册", fontSize = 12.sp, color = Color(0xFFC9CDD4))
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
							Text(group.dateTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
							Text("${group.count}张", fontSize = 12.sp, color = Color(0xFF86909C))
						}
					}

					items(group.items, key = { it.path }) { item ->
						val isSelected = selectedPaths.contains(item.path)
						Box(
							modifier = Modifier
								.aspectRatio(1f)
								.clip(RoundedCornerShape(4.dp))
								.background(Color(0xFFE5E6EB))
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
							AsyncImage(
								model = urlOf(item.path),
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
		// 1. 顶部影集/影视分类横滑栏（让影音中创建的分类一目了然）
		LazyRow(
			contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// "全部影视" 胶囊
			item {
				val isAll = selectedMediaPath.isBlank()
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = if (isAll) Color(0xFF722ED1) else Color.White,
					shadowElevation = if (isAll) 3.dp else 1.dp,
					onClick = { onSelectMediaFolder("") },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(Icons.Rounded.VideoLibrary, contentDescription = null, tint = if (isAll) Color.White else Color(0xFF722ED1), modifier = Modifier.size(15.dp))
						Text("全部影视", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isAll) Color.White else Color(0xFF1D2129))
					}
				}
			}

			// 用户创建的各个影集文件夹（支持长按直接删除）
			items(allMediaFolders) { folder ->
				val isSelected = selectedMediaPath == folder.path
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = if (isSelected) Color(0xFF722ED1) else Color(0xFFF7F8FA),
					shadowElevation = if (isSelected) 2.dp else 0.dp,
					modifier = Modifier.combinedClickable(
						onClick = { onSelectMediaFolder(folder.path) },
						onLongClick = { onDeleteMediaFolder(folder) },
					),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(6.dp),
					) {
						Icon(Icons.Rounded.Folder, contentDescription = null, tint = if (isSelected) Color.White else Color(0xFFFF9500), modifier = Modifier.size(15.dp))
						Text(folder.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color(0xFF1D2129))
					}
				}
			}

			// 尾部新建影集快捷胶囊
			item {
				Surface(
					shape = RoundedCornerShape(16.dp),
					color = Color(0xFFF9F0FF),
					onClick = onCreateMediaFolder,
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp),
					) {
						Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF722ED1), modifier = Modifier.size(15.dp))
						Text("新建影集", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF722ED1))
					}
				}
			}
		}

		// 2. 视频内容展示：双列紧凑海报网格（一屏轻松容纳 6~8 部视频，高效且具备院线级视觉质感）
		if (videos.isEmpty()) {
			Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
					FileIcon(category = FileCategory.Video, isDir = false, size = 56.dp)
					Text("当前分类暂无视频", fontSize = 14.sp, color = Color(0xFF86909C))
					Text("可点击右下角「+ 上传视频」直接存入此影集", fontSize = 12.sp, color = Color(0xFFC9CDD4))
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
						colors = CardDefaults.cardColors(containerColor = Color.White),
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
							// 16:9 高清缩略图海报视窗
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.aspectRatio(16f / 9f)
									.background(Color(0xFF1D2129)),
								contentAlignment = Alignment.Center,
							) {
								val vidUrl = urlOf(video.path)
								if (!vidUrl.isNullOrBlank()) {
									AsyncImage(
										model = vidUrl,
										contentDescription = video.name,
										contentScale = ContentScale.Crop,
										modifier = Modifier.fillMaxSize(),
									)
									Box(
										modifier = Modifier
											.fillMaxSize()
											.background(Color.Black.copy(alpha = 0.2f))
									)
								}

								// 微质感播放指示器
								Surface(
									shape = CircleShape,
									color = Color(0xFF722ED1).copy(alpha = 0.85f),
									modifier = Modifier.size(32.dp),
								) {
									Box(contentAlignment = Alignment.Center) {
										Icon(
											Icons.Rounded.PlayArrow,
											contentDescription = "播放",
											tint = Color.White,
											modifier = Modifier.size(20.dp),
										)
									}
								}

								// 右下角格式/清晰度微徽标
								Surface(
									shape = RoundedCornerShape(4.dp),
									color = Color.Black.copy(alpha = 0.65f),
									modifier = Modifier
										.align(Alignment.BottomEnd)
										.padding(6.dp),
								) {
									Text(
										text = video.ext.uppercase(Locale.ROOT).ifEmpty { "HD" },
										fontSize = 9.sp,
										fontWeight = FontWeight.Bold,
										color = Color.White,
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
										color = Color(0xFF1D2129),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
									Spacer(modifier = Modifier.height(3.dp))
									Text(
										text = formatMeta(video),
										fontSize = 10.sp,
										color = Color(0xFF86909C),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
									)
								}
								if (!isMulti) {
									IconButton(
										onClick = { onOpenAction(video) },
										modifier = Modifier.size(24.dp)
									) {
										Icon(
											Icons.Rounded.MoreVert,
											contentDescription = "管理",
											tint = Color(0xFF86909C),
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

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun TransferCenterSheet(
	tasks: List<TransferTask>,
	totalSpeedBps: Long = 0L,
	onDismiss: () -> Unit,
	onPauseTask: (String) -> Unit,
	onResumeTask: (String) -> Unit,
	onCancelTask: (String) -> Unit,
	onPauseAll: () -> Unit,
	onResumeAll: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = Color.White,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.SpaceBetween,
			) {
				Text("传输管理中心", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Text("全部暂停", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF86909C), modifier = Modifier.clickable { onPauseAll() })
					Text("全部开始", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6), modifier = Modifier.clickable { onResumeAll() })
				}
			}

			if (totalSpeedBps > 0) {
				Spacer(modifier = Modifier.height(10.dp))
				Surface(
					shape = RoundedCornerShape(12.dp),
					color = Color(0xFFEAF2FF),
					modifier = Modifier.fillMaxWidth(),
				) {
					Row(
						modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						Text("⚡ 3通道并行传输", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6))
						val mb = totalSpeedBps / (1024.0 * 1024.0)
						val speedStr = if (mb >= 1.0) String.format(Locale.ROOT, "%.1f MB/s", mb) else String.format(Locale.ROOT, "%.0f KB/s", totalSpeedBps / 1024.0)
						Text("实时总速率 $speedStr", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B578))
					}
				}
			}

			Spacer(modifier = Modifier.height(16.dp))

			if (tasks.isEmpty()) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 40.dp),
					contentAlignment = Alignment.Center,
				) {
					Text("当前无正在传输的任务", fontSize = 13.sp, color = Color(0xFF86909C))
				}
			} else {
				LazyColumn(
					verticalArrangement = Arrangement.spacedBy(10.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					items(tasks, key = { it.id }) { task ->
						val statusText = when (task.status) {
							TransferStatus.RUNNING -> {
								if (task.isUpload) {
									if (task.totalBytes > 0 && task.sentBytes >= task.totalBytes) "正在云端校验存盘..." else "正在上传"
								} else {
									"正在下载"
								}
							}
							TransferStatus.QUEUED -> "排队等待中"
							TransferStatus.PAUSED -> "已暂停"
							TransferStatus.COMPLETED -> if (task.isUpload) "上传完成" else "下载完成"
							TransferStatus.FAILED -> (if (task.isUpload) "上传失败" else "下载失败") + if (!task.error.isNullOrBlank()) ": ${task.error}" else ""
						}
						Surface(
							shape = RoundedCornerShape(12.dp),
							color = Color(0xFFF7F8FA),
							modifier = Modifier.fillMaxWidth(),
						) {
							Column(modifier = Modifier.padding(12.dp)) {
								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Row(
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.spacedBy(6.dp),
										modifier = Modifier.weight(1f),
									) {
										Surface(
											shape = RoundedCornerShape(4.dp),
											color = if (task.isUpload) Color(0xFFE8F3FF) else Color(0xFFE8FFEA),
										) {
											Text(
												text = if (task.isUpload) "上传" else "下载",
												fontSize = 10.sp,
												fontWeight = FontWeight.Bold,
												color = if (task.isUpload) Color(0xFF165DFF) else Color(0xFF00B578),
												modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
											)
										}
										Text(
											text = task.name,
											fontSize = 13.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFF1D2129),
											maxLines = 1,
											overflow = TextOverflow.Ellipsis,
										)
									}
									Text(task.speedStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6))
								}

								Spacer(modifier = Modifier.height(6.dp))
								LinearProgressIndicator(
									progress = { task.progress },
									modifier = Modifier
										.fillMaxWidth()
										.height(5.dp)
										.clip(RoundedCornerShape(3.dp)),
									color = if (task.isUpload) Color(0xFF165DFF) else Color(0xFF2A72F6),
									trackColor = Color(0xFFE5E6EB),
								)
								Spacer(modifier = Modifier.height(6.dp))

								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text(
										text = "${formatSize(task.sentBytes)} / ${if (task.totalBytes > 0) formatSize(task.totalBytes) else "--"} · $statusText",
										fontSize = 11.sp,
										color = Color(0xFF86909C),
										maxLines = 1,
										overflow = TextOverflow.Ellipsis,
										modifier = Modifier.weight(1f, fill = false),
									)

									Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
										if (task.status == TransferStatus.RUNNING && !task.isUpload) {
											Text("暂停", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6), modifier = Modifier.clickable { onPauseTask(task.id) })
										} else if (task.status == TransferStatus.PAUSED && !task.isUpload) {
											Text("继续", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B578), modifier = Modifier.clickable { onResumeTask(task.id) })
										}
										Text(
											text = if (task.status == TransferStatus.COMPLETED) "清除" else "取消",
											fontSize = 11.sp,
											fontWeight = FontWeight.Bold,
											color = Color(0xFFFF4D4F),
											modifier = Modifier.clickable { onCancelTask(task.id) },
										)
									}
								}
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
 * 智能网络管家抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkManagerSheet(
	routeInfo: RouteInfo,
	onDismiss: () -> Unit,
	onTriggerPunch: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = Color.White,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Text("网络连接与智能自愈", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
			Spacer(modifier = Modifier.height(14.dp))

			// 网络质量大卡片
			Surface(
				shape = RoundedCornerShape(16.dp),
				color = Color(0xFFF7F8FA),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(14.dp)) {
					HumanNetworkPill(routeInfo = routeInfo, onClick = {})
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = if (routeInfo.isDirect) "当前处于高速直连通道，流量端到端加密直达，速度拉满。"
						else "当前网络正通过安全中继转发。后台已启动 15 秒静默探测自愈，具备条件时将自动无缝升级为极速专线。",
						fontSize = 12.sp,
						color = Color(0xFF4E5969),
						lineHeight = 18.sp,
					)
				}
			}

			Spacer(modifier = Modifier.height(14.dp))

			Button(
				onClick = onTriggerPunch,
				colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = Color.White)
				Spacer(modifier = Modifier.width(6.dp))
				Text("⚡ 立即发起加速探测", fontWeight = FontWeight.Bold, color = Color.White)
			}

			Spacer(modifier = Modifier.height(10.dp))
			Button(
				onClick = onOpenSettings,
				colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2F3F5)),
				shape = RoundedCornerShape(14.dp),
				modifier = Modifier
					.fillMaxWidth()
					.height(48.dp),
			) {
				Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color(0xFF4E5969))
				Spacer(modifier = Modifier.width(6.dp))
				Text("配置服务参数", fontWeight = FontWeight.Bold, color = Color(0xFF4E5969))
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
	onDismiss: () -> Unit,
	onDownload: () -> Unit,
	onMove: () -> Unit,
	onRename: () -> Unit,
	onDelete: () -> Unit,
) {
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		containerColor = Color.White,
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
				FileThumbnail(entry = entry, urlOf = urlOf, size = 44.dp)
				Column(modifier = Modifier.weight(1f)) {
					Text(entry.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1, overflow = TextOverflow.Ellipsis)
					Spacer(modifier = Modifier.height(2.dp))
					Text(formatMeta(entry), fontSize = 12.sp, color = Color(0xFF86909C), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
				}
				ActionSheetBtn(icon = Icons.Rounded.DriveFileMove, label = "移动文件", onClick = onMove)
				ActionSheetBtn(icon = Icons.Rounded.DriveFileRenameOutline, label = "重命名", onClick = onRename)
				ActionSheetBtn(icon = Icons.Rounded.Delete, label = "彻底删除", onClick = onDelete, tint = Color(0xFFFF4D4F))
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
	tint: Color = Color(0xFF1D2129),
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
		modifier = Modifier.clickable { onClick() },
	) {
		Surface(
			shape = CircleShape,
			color = Color(0xFFF7F8FA),
			modifier = Modifier.size(48.dp),
		) {
			Box(contentAlignment = Alignment.Center) {
				Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
			}
		}
		Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4E5969))
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
		containerColor = Color.White,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Text("选择移动目标目录", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
			Spacer(modifier = Modifier.height(14.dp))

			LazyColumn(
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				items(folderPaths) { fPath ->
					val isCurrent = fPath == currentPath
					Surface(
						shape = RoundedCornerShape(10.dp),
						color = if (isCurrent) Color(0xFFF2F3F5) else Color(0xFFF7F8FA),
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
								color = if (isCurrent) Color(0xFF86909C) else Color(0xFF1D2129),
								modifier = Modifier.weight(1f),
							)
							if (isCurrent) {
								Text("当前所在", fontSize = 11.sp, color = Color(0xFF86909C))
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
				colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
				shape = RoundedCornerShape(10.dp),
			) { Text("确定创建") }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("取消") }
		},
	)
}

/**
 * 控制台与链路详情
 */
@Composable
private fun NetworkIntelligenceConsole(
	routeInfo: RouteInfo,
	isPunching: Boolean = false,
	onTriggerPunch: () -> Unit,
	onOpenSettings: () -> Unit,
	logs: String,
) {
	LazyColumn(
		contentPadding = PaddingValues(16.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		// 1. 全息网络连接拓扑动效卡片
		item {
			Card(
				shape = RoundedCornerShape(20.dp),
				colors = CardDefaults.cardColors(containerColor = Color(0xFF101E38)),
				elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(18.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(
							text = "实时网络拓扑",
							fontSize = 14.sp,
							fontWeight = FontWeight.Bold,
							color = Color.White,
						)
						Surface(
							shape = RoundedCornerShape(8.dp),
							color = if (routeInfo.isLan) Color(0x3300E599) else if (routeInfo.isDirect) Color(0x332A72F6) else Color(0x33FF7A00),
							border = androidx.compose.foundation.BorderStroke(1.dp, if (routeInfo.isLan) Color(0x6600E599) else if (routeInfo.isDirect) Color(0x662A72F6) else Color(0x66FF7A00)),
						) {
							Text(
								text = if (routeInfo.isLan) "⚡ 局域网" else if (routeInfo.isDirect) "🚀 专线直连" else "🐢 中继自愈",
								fontSize = 11.sp,
								fontWeight = FontWeight.Bold,
								color = if (routeInfo.isLan) Color(0xFF00E599) else if (routeInfo.isDirect) Color(0xFF69A2FF) else Color(0xFFFF9A3E),
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
							)
						}
					}

					Spacer(modifier = Modifier.height(18.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						// 客户端节点
						Column(
							horizontalAlignment = Alignment.CenterHorizontally,
							modifier = Modifier.width(76.dp),
						) {
							Box(
								modifier = Modifier
									.size(48.dp)
									.clip(RoundedCornerShape(14.dp))
									.background(Color(0xFF2A72F6)),
								contentAlignment = Alignment.Center,
							) {
								Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
							}
							Spacer(modifier = Modifier.height(6.dp))
							Text("我的手机", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
							Text("客户端", fontSize = 10.sp, color = Color(0xFFA0AEC0))
						}

						// 动态光束
						Column(
							modifier = Modifier.weight(1f),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Text(
								text = if (routeInfo.isLan) "< 2ms 局域网" else if (routeInfo.isDirect) "24ms 直连" else "86ms 中继",
								fontSize = 10.sp,
								fontWeight = FontWeight.Bold,
								color = if (routeInfo.isLan) Color(0xFF00E599) else Color(0xFF69A2FF),
							)
							Spacer(modifier = Modifier.height(4.dp))
							Box(
								modifier = Modifier
									.fillMaxWidth()
									.height(4.dp)
									.clip(RoundedCornerShape(2.dp))
									.background(
										Brush.horizontalGradient(
											colors = listOf(
												Color(0xFF2A72F6),
												if (routeInfo.isLan) Color(0xFF00E599) else Color(0xFF00B5A6),
												Color(0xFF722ED1),
											)
										)
									)
							)
						}

						// NAS 节点
						Column(
							horizontalAlignment = Alignment.CenterHorizontally,
							modifier = Modifier.width(76.dp),
						) {
							Box(
								modifier = Modifier
									.size(48.dp)
									.clip(RoundedCornerShape(14.dp))
									.background(Color(0xFF00B5A6)),
								contentAlignment = Alignment.Center,
							) {
								Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
							}
							Spacer(modifier = Modifier.height(6.dp))
							Text("私有 NAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
							Text("数据中心", fontSize = 10.sp, color = Color(0xFFA0AEC0))
						}
					}
				}
			}
		}

		// 2. 智能专线自愈与加速控制卡片
		item {
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text("智能专线加速", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
						Text("● 自动探测中", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B578))
					}

					Spacer(modifier = Modifier.height(6.dp))
					Text(
						text = "周期探测网络环境，切换移动数据时自动平滑直连。",
						fontSize = 11.sp,
						color = Color(0xFF86909C),
						lineHeight = 16.sp,
					)

					Spacer(modifier = Modifier.height(12.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Button(
							onClick = onTriggerPunch,
							enabled = !isPunching,
							colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
							shape = RoundedCornerShape(12.dp),
							contentPadding = PaddingValues(horizontal = 8.dp),
							modifier = Modifier.weight(1f).height(40.dp),
						) {
							if (isPunching) {
								CircularProgressIndicator(
									modifier = Modifier.size(16.dp),
									color = Color.White,
									strokeWidth = 2.dp,
								)
								Spacer(modifier = Modifier.width(6.dp))
								Text("正在打洞直连...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
							} else {
								Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
								Spacer(modifier = Modifier.width(4.dp))
								Text("立即加速", fontWeight = FontWeight.Bold, fontSize = 13.sp)
							}
						}

						OutlinedButton(
							onClick = onOpenSettings,
							shape = RoundedCornerShape(12.dp),
							contentPadding = PaddingValues(horizontal = 8.dp),
							modifier = Modifier.weight(1f).height(40.dp),
						) {
							Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("服务器设置", fontWeight = FontWeight.Bold, fontSize = 13.sp)
						}
					}
				}
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
		color = Color.White,
		shadowElevation = 8.dp,
		modifier = Modifier
			.fillMaxWidth()
			.height(50.dp),
	) {
		Column {
			HorizontalDivider(color = Color(0xFFF2F3F5), thickness = 0.5.dp)
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
				CompactTabItem(
					title = "服务设置",
					icon = Icons.Rounded.Speed,
					selected = currentTab == MainTab.Me,
					onClick = { onTabSelect(MainTab.Me) },
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
	val color = if (selected) Color(0xFF2A72F6) else Color(0xFF86909C)
	Column(
		modifier = modifier
			.fillMaxHeight()
			.clickable(onClick = onClick)
			.padding(top = 7.dp, bottom = 3.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Bottom,
	) {
		Icon(
			imageVector = icon,
			contentDescription = title,
			tint = color,
			modifier = Modifier.size(20.dp),
		)
		Spacer(modifier = Modifier.height(1.dp))
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
			MainTab.Files -> Triple(Color(0xFF2A72F6), Icons.Rounded.CloudUpload, "上传文件")
			MainTab.Photos -> Triple(Color(0xFFFF3D71), Icons.Rounded.PhotoCamera, "上传照片")
			MainTab.Media -> Triple(Color(0xFF722ED1), Icons.Rounded.Movie, "上传视频")
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
				Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
			}
		}
	}
}
