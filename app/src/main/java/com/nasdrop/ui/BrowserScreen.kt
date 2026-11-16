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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import com.nasdrop.TransferStatus
import com.nasdrop.TransferTask
import com.nasdrop.UploadService
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
	var showCreateDialog by remember { mutableStateOf(false) }
	var showTransferSheet by remember { mutableStateOf(false) }
	var showNetSheet by remember { mutableStateOf(false) }

	// 系统文件选择器（上传）
	val filePicker = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenMultipleDocuments(),
	) { uris ->
		if (uris.isNotEmpty()) {
			UploadService.start(context, uris, vm.path)
			Toast.makeText(context, "已加入上传队列: ${uris.size} 个文件", Toast.LENGTH_SHORT).show()
		}
	}

	Scaffold(
		containerColor = Color(0xFFF7F8FA),
		bottomBar = {
			if (mode == BrowserMode.Pick) {
				PickBar(
					path = vm.path,
					lastDir = lastDir,
					onCreateFolder = { showCreateDialog = true },
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
						val first = vm.selectedEntries.firstOrNull()
						if (first != null) activeEntryForMove = first
					},
					onDelete = {
						val first = vm.selectedEntries.firstOrNull()
						if (first != null) activeEntryForDelete = first
					},
					onCancel = { vm.exitMultiSelect() },
				)
			} else {
				// 主流商业网盘标准四栏 Tab
				CommercialNavigationBar(
					currentTab = vm.currentTab,
					onTabSelect = { tab ->
						vm.currentTab = tab
						if (tab == MainTab.Me) showNetSheet = true
					},
				)
			}
		},
		floatingActionButton = {
			if (vm.currentTab == MainTab.Files && !vm.isMultiSelectMode && mode == BrowserMode.Browse) {
				ExtendedFloatingActionButton(
					onClick = { filePicker.launch(arrayOf("*/*")) },
					icon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = Color.White) },
					text = { Text("上传", fontWeight = FontWeight.Bold, color = Color.White) },
					containerColor = Color(0xFF2A72F6),
					elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
					shape = RoundedCornerShape(20.dp),
				)
			}
		},
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding),
		) {
			// 沉浸通透顶栏：网络药丸、操作按钮、搜索条、动态传输胶囊
			CommercialTopBar(
				vm = vm,
				routeInfo = routeInfo,
				onOpenNetSheet = { showNetSheet = true },
				onOpenTransferSheet = { showTransferSheet = true },
				onToggleViewMode = { vm.cycleViewMode() },
				onToggleMultiSelect = {
					if (vm.isMultiSelectMode) vm.exitMultiSelect()
					else vm.enterMultiSelect()
				},
				onCreateFolder = { showCreateDialog = true },
				onRefresh = { vm.refresh() },
				onOpenSettings = onOpenSettings,
				onPickFile = { filePicker.launch(arrayOf("*/*")) },
			)

			// 面包屑路径导航栏（支持点击任意层级直达与返回上一级）
			if (vm.currentTab == MainTab.Files && !vm.isMultiSelectMode) {
				BreadcrumbBar(
					currentPath = vm.path,
					onNavigate = { vm.open(it) },
					onUp = { vm.up() },
				)
			}

			// 分类胶囊栏（仅在文件 Tab 展示，纯矢量质感，杜绝 Emoji）
			if (vm.currentTab == MainTab.Files && !vm.isMultiSelectMode) {
				CategoryFilterRow(
					selected = vm.selectedCategory,
					onSelect = { vm.selectCategory(it) },
				)
			}

			// 多选状态信息条
			if (vm.isMultiSelectMode) {
				MultiSelectHeaderStrip(
					selectedCount = vm.selectedEntries.size,
					onSelectAll = { vm.selectAll(vm.filteredEntries) },
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
							urlOf = { vm.urlFor(it) },
							onOpenImage = onOpenImage,
							onCreateAlbum = { showCreateDialog = true },
						)
					}

					MainTab.Media -> {
						MediaStreamContent(
							videos = vm.allVideos,
							urlOf = { vm.urlFor(it) },
							onOpenVideo = onOpenVideo,
						)
					}

					MainTab.Me -> {
						NetworkIntelligenceConsole(
							routeInfo = routeInfo,
							onTriggerPunch = { vm.punchHole() },
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

	// 6. 新建文件夹/专属相册对话框
	if (showCreateDialog) {
		CreateFolderDialog(
			onConfirm = { name, isAlbum ->
				showCreateDialog = false
				if (name.isNotBlank()) vm.mkdir(name.trim(), isAlbum)
			},
			onDismiss = { showCreateDialog = false },
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
					activeEntryForDelete = null
					vm.delete(entry)
				}) { Text("彻底删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
			},
			dismissButton = { TextButton(onClick = { activeEntryForDelete = null }) { Text("取消") } },
		)
	}
}

// ==================== 顶部导航栏与状态胶囊 ====================

@Composable
private fun CommercialTopBar(
	vm: BrowserViewModel,
	routeInfo: RouteInfo,
	onOpenNetSheet: () -> Unit,
	onOpenTransferSheet: () -> Unit,
	onToggleViewMode: () -> Unit,
	onToggleMultiSelect: () -> Unit,
	onCreateFolder: () -> Unit,
	onRefresh: () -> Unit,
	onOpenSettings: () -> Unit,
	onPickFile: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(Color(0xFFF7F8FA))
			.padding(horizontal = 14.dp, vertical = 6.dp),
	) {
		// 第一行：网络药丸 + 顶栏功能按钮
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			// 通俗易懂的人性化网络状态药丸
			HumanNetworkPill(routeInfo = routeInfo, onClick = onOpenNetSheet)

			// 右侧操作按钮组
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				// 多选模式按钮
				IconButton(onClick = onToggleMultiSelect) {
					Icon(
						imageVector = Icons.Rounded.Checklist,
						contentDescription = "多选",
						tint = if (vm.isMultiSelectMode) Color(0xFF2A72F6) else Color(0xFF4E5969),
					)
				}

				// 视图切换按钮
				IconButton(onClick = onToggleViewMode) {
					Icon(
						imageVector = when (vm.viewMode) {
							ViewMode.Cards -> Icons.Rounded.ViewAgenda
							ViewMode.Compact -> Icons.Rounded.ViewHeadline
							ViewMode.Grid -> Icons.Rounded.GridView
						},
						contentDescription = "切换视图",
						tint = Color(0xFF4E5969),
					)
				}

				// 新建文件夹/相册按钮
				IconButton(onClick = onCreateFolder) {
					Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建", tint = Color(0xFF4E5969))
				}

				// 刷新
				IconButton(onClick = onRefresh) {
					Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = Color(0xFF4E5969))
				}

				// 仅在检测到有新版本发布时才在首页顶栏展示更新按钮
				if (vm.hasUpdate) {
					IconButton(onClick = { vm.openUpdateDialog() }) {
						Box {
							Icon(
								imageVector = Icons.Rounded.RocketLaunch,
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
			}
		}

		Spacer(modifier = Modifier.height(8.dp))

		// 第二行：极简搜索框 + 动态传输中心胶囊
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			// 搜索输入框
			Surface(
				shape = RoundedCornerShape(20.dp),
				color = Color.White,
				shadowElevation = 1.dp,
				modifier = Modifier.weight(1f),
			) {
				Row(
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF86909C), modifier = Modifier.size(16.dp))
					BasicTextField(
						value = vm.searchQuery,
						onValueChange = { vm.searchQuery = it },
						singleLine = true,
						textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1D2129), fontSize = 13.sp),
						modifier = Modifier.weight(1f),
						decorationBox = { innerTextField ->
							if (vm.searchQuery.isEmpty()) {
								Text("搜索私有云盘文件...", fontSize = 13.sp, color = Color(0xFF86909C))
							}
							innerTextField()
						},
					)
					if (vm.searchQuery.isNotEmpty()) {
						IconButton(
							onClick = { vm.searchQuery = "" },
							modifier = Modifier.size(20.dp),
						) {
							Icon(Icons.Rounded.Close, contentDescription = "清除", tint = Color(0xFF86909C), modifier = Modifier.size(14.dp))
						}
					}
				}
			}

			// 动态传输中心药丸（显示实时任务数与总速度）
			Surface(
				shape = RoundedCornerShape(20.dp),
				color = Color.White,
				border = BorderStroke(1.dp, if (vm.activeTransferCount > 0) Color(0xFF2A72F6) else Color(0xFFE5E6EB)),
				shadowElevation = 1.dp,
				onClick = onOpenTransferSheet,
			) {
				Row(
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(6.dp),
				) {
					Icon(
						imageVector = if (vm.activeTransferCount > 0) Icons.Rounded.CloudDownload else Icons.Rounded.CloudDone,
						contentDescription = "传输",
						tint = if (vm.activeTransferCount > 0) Color(0xFF2A72F6) else Color(0xFF86909C),
						modifier = Modifier.size(16.dp),
					)
					Text(
						text = if (vm.activeTransferCount > 0) "${vm.activeTransferCount} 项 · ${vm.totalSpeedStr}" else "传输",
						fontSize = 12.sp,
						fontWeight = FontWeight.Bold,
						color = if (vm.activeTransferCount > 0) Color(0xFF2A72F6) else Color(0xFF4E5969),
					)
				}
			}
		}
	}
}

/**
 * 通俗化网络状态胶囊：
 * - 🏡 在家直连 (LAN)
 * - 🚀 远程专线 (VPN 极速 P2P)
 * - 🐢 远程顺畅 (VPN 顺畅 Relay)
 */
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

/**
 * 现代网盘面包屑导航：根目录 > 文件夹 > 子文件夹，支持点击任意层级直达及返回上一级
 */
@Composable
private fun BreadcrumbBar(
	currentPath: String,
	onNavigate: (String) -> Unit,
	onUp: () -> Unit,
) {
	val parts = remember(currentPath) {
		if (currentPath.isBlank()) emptyList()
		else currentPath.trim('/').split('/')
	}

	Surface(
		color = Color.White,
		shadowElevation = 0.5.dp,
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			if (parts.isNotEmpty()) {
				// 返回上一级按钮
				IconButton(
					onClick = onUp,
					modifier = Modifier.size(28.dp),
				) {
					Icon(
						imageVector = Icons.Rounded.ArrowBack,
						contentDescription = "返回上一级",
						tint = Color(0xFF2A72F6),
						modifier = Modifier.size(18.dp),
					)
				}
				Spacer(modifier = Modifier.width(4.dp))
			}

			LazyRow(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
				modifier = Modifier.weight(1f),
			) {
				// 根目录节点
				item {
					val isCurrent = parts.isEmpty()
					Surface(
						shape = RoundedCornerShape(8.dp),
						color = if (isCurrent) Color(0xFFEAF2FF) else Color.Transparent,
						onClick = { onNavigate("") },
					) {
						Row(
							modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(4.dp),
						) {
							Icon(
								imageVector = Icons.Rounded.Home,
								contentDescription = null,
								tint = if (isCurrent) Color(0xFF2A72F6) else Color(0xFF4E5969),
								modifier = Modifier.size(14.dp),
							)
							Text(
								text = "根目录",
								fontSize = 12.sp,
								fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
								color = if (isCurrent) Color(0xFF2A72F6) else Color(0xFF4E5969),
							)
						}
					}
				}

				// 各级子目录节点
				parts.forEachIndexed { index, part ->
					item {
						Icon(
							imageVector = Icons.Rounded.ChevronRight,
							contentDescription = null,
							tint = Color(0xFFC9CDD4),
							modifier = Modifier.size(14.dp),
						)
					}
					item {
						val targetPath = parts.take(index + 1).joinToString("/")
						val isCurrent = index == parts.lastIndex
						Surface(
							shape = RoundedCornerShape(8.dp),
							color = if (isCurrent) Color(0xFFEAF2FF) else Color.Transparent,
							onClick = { if (!isCurrent) onNavigate(targetPath) },
						) {
							Text(
								text = part,
								fontSize = 12.sp,
								fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
								color = if (isCurrent) Color(0xFF2A72F6) else Color(0xFF4E5969),
								maxLines = 1,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
							)
						}
					}
				}
			}
		}
	}
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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
			LazyColumn(
				contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(items, key = { it.path }) { entry ->
					val isSelected = vm.selectedEntries.contains(entry)
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
						Row(
							modifier = Modifier.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp),
						) {
							if (isMulti) {
								SelectCheckbox(isSelected = isSelected)
							}

							FileThumbnail(entry = entry, urlOf = { vm.urlFor(it) }, size = 42.dp)

							Column(modifier = Modifier.weight(1f)) {
								Text(
									text = entry.name,
									fontSize = 14.sp,
									fontWeight = FontWeight.Bold,
									color = Color(0xFF1D2129),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
								Spacer(modifier = Modifier.height(3.dp))
								Text(
									text = formatMeta(entry),
									fontSize = 12.sp,
									color = Color(0xFF86909C),
									maxLines = 1,
									overflow = TextOverflow.Ellipsis,
								)
							}

							if (!isMulti) {
								IconButton(onClick = { onOpenAction(entry) }, modifier = Modifier.size(28.dp)) {
									Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = Color(0xFF86909C))
								}
							}
						}
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
								text = entry.name,
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
								text = entry.name,
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

@Composable
private fun PhotoTimelineContent(
	groups: List<com.nasdrop.TimelineGroup>,
	urlOf: (String) -> String?,
	onOpenImage: (DavEntry) -> Unit,
	onCreateAlbum: () -> Unit,
) {
	if (groups.isEmpty()) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
				FileIcon(category = FileCategory.Image, isDir = false, size = 56.dp)
				Text("暂未发现相册图片", fontSize = 14.sp, color = Color(0xFF86909C))
			}
		}
		return
	}

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
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					Text(group.dateTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
					Text("${group.count}张", fontSize = 12.sp, color = Color(0xFF86909C))
				}
			}

			items(group.items, key = { it.path }) { item ->
				AsyncImage(
					model = urlOf(item.path),
					contentDescription = item.name,
					contentScale = ContentScale.Crop,
					modifier = Modifier
						.aspectRatio(1f)
						.clip(RoundedCornerShape(4.dp))
						.background(Color(0xFFE5E6EB))
						.clickable { onOpenImage(item) },
				)
			}
		}
	}
}

@Composable
private fun MediaStreamContent(
	videos: List<DavEntry>,
	urlOf: (String) -> String?,
	onOpenVideo: (DavEntry) -> Unit,
) {
	if (videos.isEmpty()) {
		Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
				FileIcon(category = FileCategory.Video, isDir = false, size = 56.dp)
				Text("暂未发现高清影音视频", fontSize = 14.sp, color = Color(0xFF86909C))
			}
		}
		return
	}

	LazyColumn(
		contentPadding = PaddingValues(14.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		items(videos, key = { it.path }) { video ->
			Card(
				shape = RoundedCornerShape(16.dp),
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onOpenVideo(video) },
			) {
				Column {
					// 16:9 视频海报视窗
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(16f / 9f)
							.background(Color(0xFF1D2129)),
						contentAlignment = Alignment.Center,
					) {
						Surface(
							shape = CircleShape,
							color = Color(0xFF2A72F6).copy(alpha = 0.85f),
							modifier = Modifier.size(48.dp),
						) {
							Box(contentAlignment = Alignment.Center) {
								Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(30.dp))
							}
						}

						// 右下角 4K/HDR 标签
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = Color.Black.copy(alpha = 0.65f),
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(8.dp),
						) {
							Text("4K / 60FPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
						}
					}

					// 标题与元数据
					Column(modifier = Modifier.padding(12.dp)) {
						Text(video.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1, overflow = TextOverflow.Ellipsis)
						Spacer(modifier = Modifier.height(4.dp))
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							Text(formatSize(video.size), fontSize = 11.sp, color = Color(0xFF86909C))
							Text("·", fontSize = 11.sp, color = Color(0xFF86909C))
							Text(formatDate(video.modified), fontSize = 11.sp, color = Color(0xFF86909C))
						}
					}
				}
			}
		}
	}
}

// ==================== 抽屉弹窗实现 ====================

/**
 * 传输中心管理抽屉
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferCenterSheet(
	tasks: List<TransferTask>,
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
									Text(task.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
									Text(task.speedStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6))
								}

								Spacer(modifier = Modifier.height(6.dp))
								LinearProgressIndicator(
									progress = { task.progress },
									modifier = Modifier
										.fillMaxWidth()
										.height(5.dp)
										.clip(RoundedCornerShape(3.dp)),
									color = Color(0xFF2A72F6),
									trackColor = Color(0xFFE5E6EB),
								)
								Spacer(modifier = Modifier.height(6.dp))

								Row(
									modifier = Modifier.fillMaxWidth(),
									verticalAlignment = Alignment.CenterVertically,
									horizontalArrangement = Arrangement.SpaceBetween,
								) {
									Text(
										text = "${formatSize(task.sentBytes)} / ${formatSize(task.totalBytes)} · ${task.status.name}",
										fontSize = 11.sp,
										color = Color(0xFF86909C),
									)

									Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
										if (task.status == TransferStatus.RUNNING) {
											Text("暂停", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A72F6), modifier = Modifier.clickable { onPauseTask(task.id) })
										} else if (task.status == TransferStatus.PAUSED) {
											Text("继续", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B578), modifier = Modifier.clickable { onResumeTask(task.id) })
										}
										Text("取消", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF4D4F), modifier = Modifier.clickable { onCancelTask(task.id) })
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
					Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
@Composable
private fun CreateFolderDialog(
	onConfirm: (name: String, isAlbum: Boolean) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by remember { mutableStateOf("") }
	var isAlbum by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(if (isAlbum) "新建专属相册" else "新建文件夹", fontWeight = FontWeight.Bold) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it },
					placeholder = { Text(if (isAlbum) "如: 2026云南自驾游" else "文件夹名称") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)

				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.SpaceBetween,
				) {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = Color(0xFFFF3D71), modifier = Modifier.size(18.dp))
						Text("设为专属相册", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
					}
					Switch(
						checked = isAlbum,
						onCheckedChange = { isAlbum = it },
						colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF3D71)),
					)
				}
			}
		},
		confirmButton = {
			Button(
				onClick = { onConfirm(name, isAlbum) },
				colors = ButtonDefaults.buttonColors(containerColor = if (isAlbum) Color(0xFFFF3D71) else Color(0xFF2A72F6)),
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
							text = "实时全息连接拓扑",
							fontSize = 14.sp,
							fontWeight = FontWeight.ExtraBold,
							color = Color.White,
						)
						Surface(
							shape = RoundedCornerShape(10.dp),
							color = if (routeInfo.isLan) Color(0x3300E599) else if (routeInfo.isDirect) Color(0x332A72F6) else Color(0x33FF7A00),
							border = androidx.compose.foundation.BorderStroke(1.dp, if (routeInfo.isLan) Color(0x6600E599) else if (routeInfo.isDirect) Color(0x662A72F6) else Color(0x66FF7A00)),
						) {
							Text(
								text = if (routeInfo.isLan) "⚡ 局域网高速直连" else if (routeInfo.isDirect) "🚀 P2P 专线极速直连" else "🐢 中继顺畅传输 (15s自愈)",
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
								Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
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
								text = if (routeInfo.isLan) "< 2ms · 无限带宽" else if (routeInfo.isDirect) "24ms · 端到端加密" else "86ms · 中继加速中",
								fontSize = 10.sp,
								fontWeight = FontWeight.ExtraBold,
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
								Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
							}
							Spacer(modifier = Modifier.height(6.dp))
							Text("私有 NAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
							Text("数据中心", fontSize = 10.sp, color = Color(0xFFA0AEC0))
						}
					}
				}
			}
		}

		// 2. 存储容量与健康度多色段仪表卡片
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
						Column {
							Text("NAS 存储空间总览", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D2129))
							Text("已使用 3.82 TB / 7.28 TB (52%)", fontSize = 11.sp, color = Color(0xFF86909C))
						}
						Surface(
							shape = RoundedCornerShape(8.dp),
							color = Color(0xFFE6F8F1),
						) {
							Text(
								"健康度 100%",
								fontSize = 11.sp,
								fontWeight = FontWeight.Bold,
								color = Color(0xFF00B578),
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
							)
						}
					}

					Spacer(modifier = Modifier.height(12.dp))

					// 多色段容量进度条
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.height(8.dp)
							.clip(RoundedCornerShape(4.dp))
							.background(Color(0xFFF0F2F5)),
					) {
						Box(modifier = Modifier.fillMaxHeight().weight(0.45f).background(Color(0xFF722ED1)))
						Box(modifier = Modifier.fillMaxHeight().weight(0.25f).background(Color(0xFFFF4D4F)))
						Box(modifier = Modifier.fillMaxHeight().weight(0.12f).background(Color(0xFF2A72F6)))
						Box(modifier = Modifier.fillMaxHeight().weight(0.18f).background(Color(0xFFE5E6EB)))
					}

					Spacer(modifier = Modifier.height(10.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
					) {
						LegendDot("影视 3.2T", Color(0xFF722ED1))
						LegendDot("相册 1.8T", Color(0xFFFF4D4F))
						LegendDot("文档 0.8T", Color(0xFF2A72F6))
						LegendDot("剩余 3.4T", Color(0xFFC9CDD4))
					}
				}
			}
		}

		// 3. 智能穿透加速与自愈引擎
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
						Text("智能自愈与加速控制", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
						Text("● 15s 自动打洞中", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00B578))
					}

					Spacer(modifier = Modifier.height(6.dp))
					Text(
						text = "系统在后台自动周期探测 NAT 环境。在离开 Wi-Fi 切换到 5G 蜂窝网络时，将自动平滑握手升级为极速专线直连。",
						fontSize = 11.sp,
						color = Color(0xFF86909C),
						lineHeight = 16.sp,
					)

					Spacer(modifier = Modifier.height(14.dp))

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.spacedBy(10.dp),
					) {
						Button(
							onClick = onTriggerPunch,
							colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A72F6)),
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.weight(1f).height(44.dp),
						) {
							Icon(Icons.Rounded.FlashOn, contentDescription = null, tint = Color.White)
							Spacer(modifier = Modifier.width(4.dp))
							Text("⚡ 立即发起加速探测", fontWeight = FontWeight.Bold, fontSize = 13.sp)
						}

						OutlinedButton(
							onClick = onOpenSettings,
							shape = RoundedCornerShape(12.dp),
							modifier = Modifier.height(44.dp),
						) {
							Text("设置", fontWeight = FontWeight.Bold, fontSize = 13.sp)
						}
					}
				}
			}
		}

		// 4. 底层链路诊断日志卡片
		item {
			Card(
				shape = RoundedCornerShape(18.dp),
				colors = CardDefaults.cardColors(containerColor = Color.White),
				elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(modifier = Modifier.padding(16.dp)) {
					Text("底层链路运行日志", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D2129))
					Spacer(modifier = Modifier.height(8.dp))
					Text(
						text = logs.ifBlank { "暂无异常日志，网络专线与直连链路运行良好。" },
						fontSize = 11.sp,
						color = Color(0xFF86909C),
						lineHeight = 16.sp,
					)
				}
			}
		}
	}
}

@Composable
private fun LegendDot(text: String, color: Color) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
		Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
		Text(text = text, fontSize = 10.sp, color = Color(0xFF4E5969))
	}
}

/**
 * 底部商业级导航栏
 */
@Composable
private fun CommercialNavigationBar(
	currentTab: MainTab,
	onTabSelect: (MainTab) -> Unit,
) {
	NavigationBar(
		containerColor = Color.White,
		tonalElevation = 6.dp,
	) {
		NavigationBarItem(
			selected = currentTab == MainTab.Files,
			onClick = { onTabSelect(MainTab.Files) },
			icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = "文件") },
			label = { Text("文件", fontWeight = FontWeight.Bold) },
			colors = NavigationBarItemDefaults.colors(
				selectedIconColor = Color(0xFF2A72F6),
				selectedTextColor = Color(0xFF2A72F6),
				indicatorColor = Color(0xFFEAF2FF),
			),
		)

		NavigationBarItem(
			selected = currentTab == MainTab.Photos,
			onClick = { onTabSelect(MainTab.Photos) },
			icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = "相册") },
			label = { Text("相册", fontWeight = FontWeight.Bold) },
			colors = NavigationBarItemDefaults.colors(
				selectedIconColor = Color(0xFF2A72F6),
				selectedTextColor = Color(0xFF2A72F6),
				indicatorColor = Color(0xFFEAF2FF),
			),
		)

		NavigationBarItem(
			selected = currentTab == MainTab.Media,
			onClick = { onTabSelect(MainTab.Media) },
			icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = "影音") },
			label = { Text("影音", fontWeight = FontWeight.Bold) },
			colors = NavigationBarItemDefaults.colors(
				selectedIconColor = Color(0xFF2A72F6),
				selectedTextColor = Color(0xFF2A72F6),
				indicatorColor = Color(0xFFEAF2FF),
			),
		)

		NavigationBarItem(
			selected = currentTab == MainTab.Me,
			onClick = { onTabSelect(MainTab.Me) },
			icon = { Icon(Icons.Rounded.Speed, contentDescription = "网络管家") },
			label = { Text("网络管家", fontWeight = FontWeight.Bold) },
			colors = NavigationBarItemDefaults.colors(
				selectedIconColor = Color(0xFF2A72F6),
				selectedTextColor = Color(0xFF2A72F6),
				indicatorColor = Color(0xFFEAF2FF),
			),
		)
	}
}

internal fun formatMeta(entry: DavEntry): String {
	val dateStr = if (entry.modified > 0) formatDate(entry.modified) else ""
	if (entry.isDir) {
		return if (dateStr.isNotEmpty()) "文件夹 · $dateStr" else "文件夹"
	}
	val ext = entry.name.substringAfterLast('.', "").uppercase(Locale.ROOT)
	val sizeStr = formatSize(entry.size)
	val parts = mutableListOf<String>()
	if (sizeStr != "--") parts.add(sizeStr)
	if (ext.isNotEmpty() && ext.length <= 5) parts.add(ext)
	if (dateStr.isNotEmpty()) parts.add(dateStr)
	return parts.joinToString(" · ")
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
