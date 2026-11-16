package com.nasdrop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
import com.nasdrop.MainTab
import com.nasdrop.RouteInfo
import com.nasdrop.TimelineGroup
import com.nasdrop.UploadService
import com.nasdrop.Uploads
import com.nasdrop.ViewMode
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BrowserMode { Browse, Pick }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
	vm: BrowserViewModel,
	mode: BrowserMode,
	lastDir: String = "",
	onOpenImage: (DavEntry) -> Unit = {},
	onOpenVideo: (DavEntry) -> Unit = {},
	onOpenSettings: () -> Unit = {},
	onLogin: () -> Unit = {},
	onConfirm: (String) -> Unit = {},
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val snackbar = remember { SnackbarHostState() }
	val tasks by Uploads.tasks.collectAsStateWithLifecycle()
	val routeInfo by vm.routeInfo.collectAsStateWithLifecycle()

	// 统一文件操作抽屉状态
	var activeEntryForAction by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForMove by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForRename by remember { mutableStateOf<DavEntry?>(null) }
	var activeEntryForDelete by remember { mutableStateOf<DavEntry?>(null) }

	var showCreateDialog by remember { mutableStateOf(false) }
	var showHolePunchDialog by remember { mutableStateOf(false) }

	val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		if (uris.isNotEmpty()) UploadService.start(context, uris, vm.path)
	}

	LaunchedEffect(vm.error) {
		vm.error?.let { snackbar.showSnackbar(it) }
	}

	LaunchedEffect(tasks) {
		if (tasks.isNotEmpty() && tasks.all { it.done }) {
			vm.refresh()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Column {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text(
								text = when (vm.currentTab) {
									MainTab.Files -> if (vm.isRoot) "我的网盘" else vm.path.substringAfterLast('/')
									MainTab.Photos -> "时光相册"
									MainTab.Media -> "影音专区"
									MainTab.Me -> "传输与状态"
								},
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
							)
						}
						Spacer(Modifier.height(2.dp))
						// 醒目的 P2P 直连 / DERP 中继状态徽章，点击可触发 NAT 打洞
						NetworkPill(
							routeInfo = routeInfo,
							onClick = { showHolePunchDialog = true },
						)
					}
				},
				navigationIcon = {
					if (vm.currentTab == MainTab.Files && !vm.isRoot) {
						IconButton(onClick = { vm.up() }) {
							Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
						}
					}
				},
				actions = {
					if (vm.currentTab == MainTab.Files) {
						// 循环切换：卡片 -> 紧凑 -> 宫格
						IconButton(onClick = { vm.cycleViewMode() }) {
							Icon(
								when (vm.viewMode) {
									ViewMode.Cards -> Icons.Rounded.ViewAgenda
									ViewMode.Compact -> Icons.Rounded.ViewHeadline
									ViewMode.Grid -> Icons.Rounded.GridView
								},
								contentDescription = "切换视图",
								tint = MaterialTheme.colorScheme.primary,
							)
						}
					}
					// 新建文件夹/相册按钮
					IconButton(onClick = { showCreateDialog = true }) {
						Icon(Icons.Rounded.CreateNewFolder, contentDescription = "新建", tint = MaterialTheme.colorScheme.primary)
					}
					IconButton(onClick = { vm.refresh() }) {
						Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
			)
		},
		snackbarHost = { SnackbarHost(snackbar) },
		floatingActionButton = {
			if (mode == BrowserMode.Browse && vm.currentTab != MainTab.Me) {
				ExtendedFloatingActionButton(
					text = { Text("上传", fontWeight = FontWeight.SemiBold) },
					icon = { Icon(Icons.Rounded.CloudUpload, null) },
					containerColor = MaterialTheme.colorScheme.primary,
					contentColor = MaterialTheme.colorScheme.onPrimary,
					onClick = { picker.launch(arrayOf("*/*")) },
				)
			}
		},
		bottomBar = {
			if (mode == BrowserMode.Pick) {
				PickBar(
					path = vm.path,
					lastDir = lastDir,
					onConfirm = {
						vm.rememberDir(vm.path)
						onConfirm(vm.path)
					},
				)
			} else {
				// 类似夸克/主流网盘四大 Tab 导航
				NavigationBar(
					containerColor = MaterialTheme.colorScheme.surface,
					tonalElevation = 8.dp,
				) {
					NavigationBarItem(
						selected = vm.currentTab == MainTab.Files,
						onClick = { vm.currentTab = MainTab.Files },
						icon = { Icon(Icons.Rounded.Folder, null) },
						label = { Text("文件") },
						colors = NavigationBarItemDefaults.colors(
							selectedIconColor = MaterialTheme.colorScheme.primary,
							selectedTextColor = MaterialTheme.colorScheme.primary,
							indicatorColor = MaterialTheme.colorScheme.primaryContainer,
						),
					)
					NavigationBarItem(
						selected = vm.currentTab == MainTab.Photos,
						onClick = { vm.currentTab = MainTab.Photos },
						icon = { Icon(Icons.Rounded.PhotoLibrary, null) },
						label = { Text("相册") },
						colors = NavigationBarItemDefaults.colors(
							selectedIconColor = MaterialTheme.colorScheme.primary,
							selectedTextColor = MaterialTheme.colorScheme.primary,
							indicatorColor = MaterialTheme.colorScheme.primaryContainer,
						),
					)
					NavigationBarItem(
						selected = vm.currentTab == MainTab.Media,
						onClick = { vm.currentTab = MainTab.Media },
						icon = { Icon(Icons.Rounded.VideoLibrary, null) },
						label = { Text("影音") },
						colors = NavigationBarItemDefaults.colors(
							selectedIconColor = MaterialTheme.colorScheme.primary,
							selectedTextColor = MaterialTheme.colorScheme.primary,
							indicatorColor = MaterialTheme.colorScheme.primaryContainer,
						),
					)
					NavigationBarItem(
						selected = vm.currentTab == MainTab.Me,
						onClick = { vm.currentTab = MainTab.Me },
						icon = { Icon(Icons.Rounded.Person, null) },
						label = { Text("我的") },
						colors = NavigationBarItemDefaults.colors(
							selectedIconColor = MaterialTheme.colorScheme.primary,
							selectedTextColor = MaterialTheme.colorScheme.primary,
							indicatorColor = MaterialTheme.colorScheme.primaryContainer,
						),
					)
				}
			}
		},
	) { padding ->
		Column(
			Modifier
				.padding(padding)
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background),
		) {
			if (vm.needsLogin) LoginCard(onLogin)

			AnimatedVisibility(visible = tasks.any { !it.done }) {
				UploadStrip(
					done = tasks.count { it.done },
					total = tasks.size,
					progress = tasks.filter { !it.done }.map { it.progress }.maxOrNull() ?: 0f,
				)
			}

			if (vm.loading) {
				LinearProgressIndicator(
					modifier = Modifier.fillMaxWidth().height(2.dp),
					color = MaterialTheme.colorScheme.primary,
					trackColor = Color.Transparent,
				)
			}

			when (vm.currentTab) {
				MainTab.Files -> FilesTabContent(
					vm = vm,
					onOpenImage = onOpenImage,
					onOpenVideo = onOpenVideo,
					onActionSheet = { activeEntryForAction = it },
				)
				MainTab.Photos -> PhotosTabContent(
					groups = vm.photoTimelineGroups,
					thumbUrlFor = { vm.urlFor(it) },
					onOpenImage = onOpenImage,
					onCreateAlbum = { showCreateDialog = true },
				)
				MainTab.Media -> MediaTabContent(
					videos = vm.allVideos,
					onOpenVideo = onOpenVideo,
				)
				MainTab.Me -> MeTabContent(
					routeInfo = routeInfo,
					tasks = tasks,
					onPunchHole = {
						val res = vm.punchHole()
						Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
					},
					onOpenSettings = onOpenSettings,
				)
			}
		}
	}

	// 统一文件操作抽屉 Action BottomSheet
	activeEntryForAction?.let { entry ->
		ActionBottomSheet(
			entry = entry,
			onDownload = {
				activeEntryForAction = null
				vm.download(context, entry) { ok, msg ->
					Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
				}
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
			onDismiss = { activeEntryForAction = null },
		)
	}

	// 移动文件目标文件夹选择抽屉
	activeEntryForMove?.let { entry ->
		MoveFolderSheet(
			entry = entry,
			folderPaths = vm.allFolderPaths,
			onConfirm = { targetDir ->
				activeEntryForMove = null
				vm.move(entry, targetDir)
				Toast.makeText(context, "已移动到 /${targetDir.ifEmpty { "根目录" }}", Toast.LENGTH_SHORT).show()
			},
			onDismiss = { activeEntryForMove = null },
		)
	}

	// 重命名对话框
	activeEntryForRename?.let { entry ->
		TextInputDialog(
			title = "重命名",
			initial = entry.name,
			confirmText = "保存",
			onDismiss = { activeEntryForRename = null },
			onConfirm = { name ->
				activeEntryForRename = null
				if (name.isNotBlank() && name != entry.name) vm.rename(entry, name.trim())
			},
		)
	}

	// 新建文件夹/相册对话框
	if (showCreateDialog) {
		CreateFolderDialog(
			onConfirm = { name, isAlbum ->
				showCreateDialog = false
				if (name.isNotBlank()) vm.mkdir(name.trim(), isAlbum)
			},
			onDismiss = { showCreateDialog = false },
		)
	}

	// 删除确认对话框
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

	// P2P 打洞控制台弹窗
	if (showHolePunchDialog) {
		HolePunchDialog(
			routeInfo = routeInfo,
			onPunch = {
				scope.launch {
					val res = vm.punchHole()
					Toast.makeText(context, res, Toast.LENGTH_LONG).show()
				}
			},
			onDismiss = { showHolePunchDialog = false },
		)
	}
}

/** 顶部【文件】页面主体内容 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilesTabContent(
	vm: BrowserViewModel,
	onOpenImage: (DavEntry) -> Unit,
	onOpenVideo: (DavEntry) -> Unit,
	onActionSheet: (DavEntry) -> Unit,
) {
	Column(Modifier.fillMaxSize()) {
		// 夸克网盘风格搜索胶囊
		SearchCapsuleBar(
			query = vm.searchQuery,
			onQueryChange = { vm.searchQuery = it },
		)

		if (!vm.isRoot) CrumbBar(vm.path) { vm.open(it) }

		// 解决点照片没反应：选图片精准呈现照片，排除无关普通文件夹
		CategoryFilterRow(
			selected = vm.selectedCategory,
			onSelect = { vm.selectCategory(it) },
		)

		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

		val list = vm.filteredEntries
		when {
			vm.loading && list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
				CircularProgressIndicator(strokeWidth = 3.dp)
			}
			list.isEmpty() -> EmptyState(
				title = if (vm.selectedCategory != FileCategory.All) "没有该分类的文件" else "当前目录为空",
				detail = "可点右下角上传，或从其他 App 分享保存",
				action = if (vm.selectedCategory != FileCategory.All) "查看全部" else null,
				onAction = { vm.selectCategory(FileCategory.All) },
			)
			vm.viewMode == ViewMode.Grid -> LazyVerticalGrid(
				columns = GridCells.Fixed(2),
				contentPadding = PaddingValues(12.dp),
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(list, key = { it.path }) { entry ->
					GridEntryCard(
						entry = entry,
						thumbUrl = if (entry.isImage) vm.urlFor(entry.path) else null,
						onClick = {
							when {
								entry.isDir -> vm.open(entry.path)
								entry.isImage -> onOpenImage(entry)
								entry.isVideo -> onOpenVideo(entry)
							}
						},
						onMenu = { onActionSheet(entry) },
					)
				}
			}
			vm.viewMode == ViewMode.Compact -> LazyColumn(
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(list, key = { it.path }) { entry ->
					CompactEntryRow(
						entry = entry,
						thumbUrl = if (entry.isImage) vm.urlFor(entry.path) else null,
						onClick = {
							when {
								entry.isDir -> vm.open(entry.path)
								entry.isImage -> onOpenImage(entry)
								entry.isVideo -> onOpenVideo(entry)
							}
						},
						onMenu = { onActionSheet(entry) },
					)
				}
			}
			else -> LazyColumn(
				contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
				verticalArrangement = Arrangement.spacedBy(6.dp),
				modifier = Modifier.fillMaxSize(),
			) {
				items(list, key = { it.path }) { entry ->
					EntryCard(
						entry = entry,
						thumbUrl = if (entry.isImage) vm.urlFor(entry.path) else null,
						onClick = {
							when {
								entry.isDir -> vm.open(entry.path)
								entry.isImage -> onOpenImage(entry)
								entry.isVideo -> onOpenVideo(entry)
							}
						},
						onLongClick = { onActionSheet(entry) },
						onMenu = { onActionSheet(entry) },
					)
				}
			}
		}
	}
}

/** 统一文件操作抽屉 Action BottomSheet */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionBottomSheet(
	entry: DavEntry,
	onDownload: () -> Unit,
	onMove: () -> Unit,
	onRename: () -> Unit,
	onDelete: () -> Unit,
	onDismiss: () -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	val (bgGradient, iconColor, icon) = fileVisuals(entry)

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			// 顶部文件卡片
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(bottom = 16.dp),
			) {
				Box(
					modifier = Modifier
						.size(46.dp)
						.clip(RoundedCornerShape(12.dp))
						.background(bgGradient),
					contentAlignment = Alignment.Center,
				) {
					Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
				}
				Spacer(Modifier.width(14.dp))
				Column(Modifier.weight(1f)) {
					Text(
						text = entry.name,
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
					Spacer(Modifier.height(2.dp))
					Text(
						text = if (entry.isDir) "文件夹" else subtitleOf(entry),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}
			}

			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

			// 核心金刚位操作按钮行（4个操作）
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(vertical = 18.dp),
				horizontalArrangement = Arrangement.SpaceAround,
			) {
				ActionButtonItem(
					icon = Icons.Rounded.Download,
					label = if (entry.isImage || entry.isVideo) "存入相册" else "下载文件",
					onClick = onDownload,
				)
				ActionButtonItem(
					icon = Icons.Rounded.DriveFileMove,
					label = "移动文件",
					onClick = onMove,
				)
				ActionButtonItem(
					icon = Icons.Rounded.DriveFileRenameOutline,
					label = "重命名",
					onClick = onRename,
				)
				ActionButtonItem(
					icon = Icons.Rounded.Delete,
					label = "删除文件",
					tint = MaterialTheme.colorScheme.error,
					onClick = onDelete,
				)
			}

			// 详细属性展示
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
			) {
				Column(Modifier.padding(12.dp)) {
					Text("📁 存储路径: /${entry.path}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
					Spacer(Modifier.height(4.dp))
					Text("🏷️ 文件类型: ${if (entry.isDir) "文件夹" else entry.ext.uppercase(Locale.ROOT)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			}
		}
	}
}

@Composable
private fun ActionButtonItem(
	icon: ImageVector,
	label: String,
	tint: Color = MaterialTheme.colorScheme.onSurface,
	onClick: () -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		modifier = Modifier
			.clip(RoundedCornerShape(12.dp))
			.clickable { onClick() }
			.padding(8.dp),
	) {
		Box(
			modifier = Modifier
				.size(48.dp)
				.clip(RoundedCornerShape(14.dp))
				.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
			contentAlignment = Alignment.Center,
		) {
			Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
		}
		Spacer(Modifier.height(6.dp))
		Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = tint)
	}
}

/** 移动文件选择目标文件夹抽屉 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFolderSheet(
	entry: DavEntry,
	folderPaths: List<String>,
	onConfirm: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 10.dp),
		) {
			Text("移动「${entry.name}」到…", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
			Spacer(Modifier.height(10.dp))
			LazyColumn(
				modifier = Modifier.height(260.dp),
			) {
				items(folderPaths) { fPath ->
					val displayName = if (fPath.isEmpty()) "根目录 (/)" else "/$fPath"
					Surface(
						color = MaterialTheme.colorScheme.surface,
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 4.dp)
							.clip(RoundedCornerShape(10.dp))
							.clickable { onConfirm(fPath) },
					) {
						Row(
							modifier = Modifier.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
						) {
							Icon(Icons.Rounded.Folder, null, tint = Color(0xFFF57F17))
							Spacer(Modifier.width(10.dp))
							Text(displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
						}
					}
				}
			}
		}
	}
}

/** 新建文件夹/新建相册弹窗 */
@Composable
private fun CreateFolderDialog(
	onConfirm: (String, Boolean) -> Unit,
	onDismiss: () -> Unit,
) {
	var name by remember { mutableStateOf("") }
	var isAlbum by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("新建文件夹 / 相册", fontWeight = FontWeight.Bold) },
		text = {
			Column {
				OutlinedTextField(
					value = name,
					onValueChange = { name = it },
					singleLine = true,
					label = { Text("名称") },
					placeholder = { Text(if (isAlbum) "例如: 2026家庭相册" else "例如: 学习资料") },
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(Modifier.height(14.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(10.dp),
				) {
					Surface(
						color = if (!isAlbum) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
						shape = RoundedCornerShape(12.dp),
						border = if (!isAlbum) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
						modifier = Modifier
							.weight(1f)
							.clip(RoundedCornerShape(12.dp))
							.clickable { isAlbum = false },
					) {
						Column(
							modifier = Modifier.padding(12.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Icon(Icons.Rounded.Folder, null, tint = Color(0xFFF57F17), modifier = Modifier.size(28.dp))
							Spacer(Modifier.height(4.dp))
							Text("普通文件夹", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
						}
					}
					Surface(
						color = if (isAlbum) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
						shape = RoundedCornerShape(12.dp),
						border = if (isAlbum) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
						modifier = Modifier
							.weight(1f)
							.clip(RoundedCornerShape(12.dp))
							.clickable { isAlbum = true },
					) {
						Column(
							modifier = Modifier.padding(12.dp),
							horizontalAlignment = Alignment.CenterHorizontally,
						) {
							Icon(Icons.Rounded.PhotoCamera, null, tint = Color(0xFFD81E06), modifier = Modifier.size(28.dp))
							Spacer(Modifier.height(4.dp))
							Text("专属相册", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
						}
					}
				}
			}
		},
		confirmButton = {
			TextButton(onClick = { onConfirm(name, isAlbum) }) {
				Text("立即创建", fontWeight = FontWeight.Bold)
			}
		},
		dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
	)
}

/** 紧凑单行视图列表项 */
@Composable
private fun CompactEntryRow(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onMenu: () -> Unit,
) {
	val (_, iconColor, icon) = fileVisuals(entry)
	Surface(
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onClick() }
			.padding(vertical = 2.dp),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
			Spacer(Modifier.width(10.dp))
			Text(
				text = entry.name,
				style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			Text(
				text = if (entry.isDir) "文件夹" else subtitleOf(entry),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.outline,
			)
			IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
				Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
			}
		}
	}
}

/** 3 列相册瀑布流时光轴 */
@Composable
private fun PhotosTabContent(
	groups: List<TimelineGroup>,
	thumbUrlFor: (String) -> String?,
	onOpenImage: (DavEntry) -> Unit,
	onCreateAlbum: () -> Unit,
) {
	if (groups.isEmpty()) {
		EmptyState(
			title = "暂未发现照片",
			detail = "网盘内的照片会自动按时间轴汇总在这里，支持全屏预览与缩放",
			action = "新建相册",
			onAction = onCreateAlbum,
		)
		return
	}

	LazyVerticalGrid(
		columns = GridCells.Fixed(3),
		contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 96.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		groups.forEach { group ->
			item(span = { GridItemSpan(3) }) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = group.title,
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
						color = MaterialTheme.colorScheme.onBackground,
					)
					Spacer(Modifier.width(8.dp))
					Text(
						text = "${group.items.size}张",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.outline,
					)
				}
			}
			items(group.items, key = { it.path }) { photo ->
				Box(
					modifier = Modifier
						.aspectRatio(1f)
						.clip(RoundedCornerShape(8.dp))
						.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
						.clickable { onOpenImage(photo) },
					contentAlignment = Alignment.Center,
				) {
					AsyncImage(
						model = thumbUrlFor(photo.path),
						contentDescription = photo.name,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				}
			}
		}
	}
}

/** 影音专区 */
@Composable
private fun MediaTabContent(
	videos: List<DavEntry>,
	onOpenVideo: (DavEntry) -> Unit,
) {
	if (videos.isEmpty()) {
		EmptyState(
			title = "暂无视频",
			detail = "网盘内的视频文件会自动汇集在这里，支持 ExoPlayer 极速流式缓冲播放",
			action = null,
			onAction = {},
		)
		return
	}

	LazyColumn(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp),
		modifier = Modifier.fillMaxSize(),
	) {
		items(videos, key = { it.path }) { video ->
			Card(
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
				elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
				shape = RoundedCornerShape(16.dp),
				modifier = Modifier
					.fillMaxWidth()
					.clickable { onOpenVideo(video) },
			) {
				Column {
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.aspectRatio(16f / 9f)
							.background(
								Brush.verticalGradient(
									listOf(Color(0xFF2C3E50), Color(0xFF1A1A24)),
								),
							),
						contentAlignment = Alignment.Center,
					) {
						Icon(
							Icons.Rounded.PlayCircle,
							contentDescription = "播放",
							tint = Color.White.copy(alpha = 0.85f),
							modifier = Modifier.size(56.dp),
						)
						Surface(
							color = Color.Black.copy(alpha = 0.6f),
							shape = RoundedCornerShape(6.dp),
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(10.dp),
						) {
							Text(
								text = video.ext.uppercase(Locale.ROOT),
								color = Color.White,
								style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
							)
						}
					}
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(14.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Column(Modifier.weight(1f)) {
							Text(
								text = video.name,
								style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
							)
							Spacer(Modifier.height(3.dp))
							Text(
								text = subtitleOf(video),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
							)
						}
						Button(
							onClick = { onOpenVideo(video) },
							colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
							shape = RoundedCornerShape(12.dp),
						) {
							Text("播放", fontWeight = FontWeight.Bold)
						}
					}
				}
			}
		}
	}
}

/** 【我的与传输】页面 */
@Composable
private fun MeTabContent(
	routeInfo: RouteInfo,
	tasks: List<com.nasdrop.UploadTask>,
	onPunchHole: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
	) {
		Card(
			colors = CardDefaults.cardColors(
				containerColor = if (routeInfo.isDirect) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
			),
			shape = RoundedCornerShape(18.dp),
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(Modifier.padding(18.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(
						if (routeInfo.isDirect) Icons.Rounded.FlashOn else Icons.Rounded.Sync,
						contentDescription = null,
						tint = if (routeInfo.isDirect) Color(0xFF2E7D32) else Color(0xFFE65100),
						modifier = Modifier.size(24.dp),
					)
					Spacer(Modifier.width(8.dp))
					Text(
						text = if (routeInfo.isDirect) "⚡ 当前为 P2P 直连打洞" else "🔄 当前为 DERP 中继代理",
						style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
						color = if (routeInfo.isDirect) Color(0xFF1B5E20) else Color(0xFFBF360C),
					)
				}
				Spacer(Modifier.height(8.dp))
				Text(
					text = if (routeInfo.isDirect) {
						"端到端打洞成功！流量经由 WireGuard P2P 直连传输，延迟最低且速度拉满。"
					} else {
						"当前流量经中继节点转发。点击下方按钮可主动发送探测包尝试 NAT 打洞直连。"
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				if (routeInfo.endpoint.isNotEmpty()) {
					Spacer(Modifier.height(4.dp))
					Text(
						text = "端点/中继: ${routeInfo.endpoint}",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.outline,
					)
				}
				Spacer(Modifier.height(14.dp))
				Button(
					onClick = onPunchHole,
					colors = ButtonDefaults.buttonColors(
						containerColor = if (routeInfo.isDirect) Color(0xFF2E7D32) else Color(0xFFE65100),
					),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("⚡ 立即尝试 P2P 打洞直连", fontWeight = FontWeight.Bold)
				}
			}
		}

		Spacer(Modifier.height(16.dp))

		Card(
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
			shape = RoundedCornerShape(16.dp),
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onOpenSettings() },
		) {
			Row(
				modifier = Modifier.padding(16.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary)
				Spacer(Modifier.width(12.dp))
				Column(Modifier.weight(1f)) {
					Text("WebDAV 与服务器设置", fontWeight = FontWeight.SemiBold)
					Text("修改地址、账号、密码及证书信任", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
				}
				Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
			}
		}

		Spacer(Modifier.height(16.dp))

		Text("传输任务 (${tasks.size})", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
		Spacer(Modifier.height(8.dp))
		if (tasks.isEmpty()) {
			Text("暂无上传任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
		} else {
			LazyColumn {
				items(tasks) { task ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 6.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Text(task.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
						Spacer(Modifier.width(8.dp))
						Text(if (task.done) "已完成" else "${(task.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
					}
				}
			}
		}
	}
}

/** P2P 打洞控制台弹窗 */
@Composable
private fun HolePunchDialog(
	routeInfo: RouteInfo,
	onPunch: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(Icons.Rounded.FlashOn, null, tint = MaterialTheme.colorScheme.primary)
				Spacer(Modifier.width(6.dp))
				Text("Tailscale 链路与打洞", fontWeight = FontWeight.Bold)
			}
		},
		text = {
			Column {
				Text(
					text = "当前链路: ${if (routeInfo.isDirect) "⚡ P2P 直连打洞" else "🔄 DERP 中继代理模式"}",
					fontWeight = FontWeight.Bold,
					color = if (routeInfo.isDirect) Color(0xFF2E7D32) else Color(0xFFE65100),
				)
				if (routeInfo.endpoint.isNotEmpty()) {
					Spacer(Modifier.height(4.dp))
					Text("对端端点: ${routeInfo.endpoint}", style = MaterialTheme.typography.bodySmall)
				}
				Spacer(Modifier.height(10.dp))
				Text(
					text = "原理说明：Tailscale 默认通过 NAT STUN 探测建立两端直接 UDP 直连（P2P 打洞）。若网络环境复杂则回退至中继。点击下方按钮可主动重发探测包促成打洞直连。",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
				Spacer(Modifier.height(16.dp))
				Button(
					onClick = onPunch,
					colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
					shape = RoundedCornerShape(12.dp),
					modifier = Modifier.fillMaxWidth(),
				) {
					Text("⚡ 发送探测包尝试 P2P 打洞", fontWeight = FontWeight.Bold)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) { Text("关闭") }
		},
	)
}

/** 网络状态药丸徽章 */
@Composable
private fun NetworkPill(
	routeInfo: RouteInfo,
	onClick: () -> Unit,
) {
	val isDirect = routeInfo.isDirect
	val bg = if (isDirect) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
	val fg = if (isDirect) Color(0xFF2E7D32) else Color(0xFFE65100)
	val icon = if (isDirect) Icons.Rounded.FlashOn else Icons.Rounded.Sync

	val text = when {
		isDirect -> "⚡ P2P直连" + if (routeInfo.endpoint.isNotEmpty()) " · ${routeInfo.endpoint.substringBefore(':')}" else ""
		routeInfo.isRelay -> "🔄 DERP中继 (点此打洞)"
		routeInfo.label.isNotEmpty() -> routeInfo.label
		else -> "🛡️ Tailscale 准备中"
	}

	Surface(
		color = bg,
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.clip(RoundedCornerShape(12.dp))
			.clickable { onClick() },
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
		) {
			Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
			Spacer(Modifier.width(4.dp))
			Text(
				text = text,
				style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
				color = fg,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.width(2.dp))
			Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
		}
	}
}

/** 夸克网盘风格搜索胶囊输入框 */
@Composable
private fun SearchCapsuleBar(
	query: String,
	onQueryChange: (String) -> Unit,
) {
	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
		shape = RoundedCornerShape(24.dp),
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 6.dp),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
			Spacer(Modifier.width(8.dp))
			Box(Modifier.weight(1f)) {
				if (query.isEmpty()) {
					Text("搜索网盘文件、图片或视频…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
				}
				androidx.compose.foundation.text.BasicTextField(
					value = query,
					onValueChange = onQueryChange,
					textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
					singleLine = true,
					modifier = Modifier.fillMaxWidth(),
				)
			}
		}
	}
}

/** 横向分类滑动胶囊 */
@Composable
private fun CategoryFilterRow(
	selected: FileCategory,
	onSelect: (FileCategory) -> Unit,
) {
	LazyRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		items(FileCategory.values()) { cat ->
			val isChosen = cat == selected
			val bg = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
			val fg = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
			val border = if (isChosen) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)

			Surface(
				color = bg,
				shape = RoundedCornerShape(20.dp),
				border = border,
				modifier = Modifier
					.clip(RoundedCornerShape(20.dp))
					.clickable { onSelect(cat) },
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
				) {
					Text(cat.icon, fontSize = 12.sp)
					Spacer(Modifier.width(4.dp))
					Text(
						text = cat.label,
						style = MaterialTheme.typography.labelMedium.copy(
							fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
						),
						color = fg,
					)
				}
			}
		}
	}
}

/** 详细卡片视图项 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onMenu: () -> Unit,
) {
	val (bgGradient, iconColor, icon) = fileVisuals(entry)

	Surface(
		color = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(14.dp),
		border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		Row(
			modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Box(
				modifier = Modifier
					.size(46.dp)
					.clip(RoundedCornerShape(12.dp))
					.background(bgGradient),
				contentAlignment = Alignment.Center,
			) {
				if (thumbUrl != null) {
					AsyncImage(
						model = thumbUrl,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				} else {
					Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
				}
			}

			Spacer(Modifier.width(14.dp))

			Column(Modifier.weight(1f)) {
				Text(
					text = entry.name,
					style = MaterialTheme.typography.bodyLarge.copy(
						fontWeight = if (entry.isDir) FontWeight.SemiBold else FontWeight.Normal,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Spacer(Modifier.height(2.dp))
				Text(
					text = if (entry.isDir) (if (entry.isAlbum) "专属相册" else "文件夹") else subtitleOf(entry),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
				)
			}

			if (entry.isDir) {
				Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
			} else {
				IconButton(onClick = onMenu, modifier = Modifier.size(34.dp)) {
					Icon(Icons.Rounded.MoreVert, contentDescription = "更多", tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
				}
			}
		}
	}
}

/** 2列宫格大卡片 */
@Composable
private fun GridEntryCard(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onMenu: () -> Unit,
) {
	val (bgGradient, iconColor, icon) = fileVisuals(entry)

	Surface(
		color = MaterialTheme.colorScheme.surface,
		shape = RoundedCornerShape(14.dp),
		border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.clickable { onClick() },
	) {
		Column(
			modifier = Modifier.padding(12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Box(
				modifier = Modifier
					.size(56.dp)
					.clip(RoundedCornerShape(14.dp))
					.background(bgGradient),
				contentAlignment = Alignment.Center,
			) {
				if (thumbUrl != null) {
					AsyncImage(
						model = thumbUrl,
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				} else {
					Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
				}
			}
			Spacer(Modifier.height(8.dp))
			Text(
				text = entry.name,
				style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Spacer(Modifier.height(2.dp))
			Text(
				text = if (entry.isDir) (if (entry.isAlbum) "专属相册" else "文件夹") else subtitleOf(entry),
				style = MaterialTheme.typography.labelSmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/** 拟物彩色文件类型视觉定义 */
private fun fileVisuals(entry: DavEntry): Triple<Brush, Color, ImageVector> = when {
	entry.isAlbum -> Triple(
		Brush.linearGradient(listOf(Color(0xFFFFE8EC), Color(0xFFFFB3C2))),
		Color(0xFFD81E06),
		Icons.Rounded.PhotoCamera,
	)
	entry.isDir -> Triple(
		Brush.linearGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFE082))),
		Color(0xFFF57F17),
		Icons.Rounded.Folder,
	)
	entry.isImage -> Triple(
		Brush.linearGradient(listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2))),
		Color(0xFF00838F),
		Icons.Rounded.Image,
	)
	entry.isVideo -> Triple(
		Brush.linearGradient(listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2))),
		Color(0xFFC62828),
		Icons.Rounded.Movie,
	)
	entry.isDocument -> Triple(
		Brush.linearGradient(listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB))),
		Color(0xFF1565C0),
		Icons.Rounded.Description,
	)
	entry.isAudio -> Triple(
		Brush.linearGradient(listOf(Color(0xFFF3E5F5), Color(0xFFE1BEE7))),
		Color(0xFF6A1B9A),
		Icons.Rounded.AudioFile,
	)
	entry.isArchive -> Triple(
		Brush.linearGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9))),
		Color(0xFF2E7D32),
		Icons.Rounded.FolderZip,
	)
	else -> Triple(
		Brush.linearGradient(listOf(Color(0xFFF5F5F5), Color(0xFFEEEEEE))),
		Color(0xFF616161),
		Icons.Rounded.InsertDriveFile,
	)
}

@Composable
private fun LoginCard(onLogin: () -> Unit) {
	Surface(
		color = MaterialTheme.colorScheme.errorContainer,
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
	) {
		Row(
			Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f)) {
				Text("内嵌 Tailscale 还没授权", style = MaterialTheme.typography.titleSmall)
				Text(
					"点右侧登录一次，之后就永久免登录",
					style = MaterialTheme.typography.bodySmall,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Button(onClick = onLogin) { Text("去登录") }
		}
	}
}

@Composable
private fun UploadStrip(done: Int, total: Int, progress: Float) {
	Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
			Text(
				"正在上传 $done/$total",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSecondaryContainer,
			)
			Spacer(Modifier.height(6.dp))
			LinearProgressIndicator(
				progress = { progress },
				modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
				color = MaterialTheme.colorScheme.primary,
				trackColor = MaterialTheme.colorScheme.surfaceVariant,
			)
		}
	}
}

@Composable
private fun PickBar(path: String, lastDir: String, onConfirm: () -> Unit) {
	Surface(tonalElevation = 3.dp) {
		Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
			Text(
				"当前 /${path}",
				style = MaterialTheme.typography.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (lastDir.isNotEmpty() && lastDir != path) {
				Text(
					"上次存入 /$lastDir",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}
			Spacer(Modifier.height(10.dp))
			Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(50.dp)) {
				Text("存入此处")
			}
		}
	}
}

@Composable
private fun EmptyState(title: String, detail: String, action: String?, onAction: () -> Unit) {
	Box(Modifier.fillMaxSize(), Alignment.Center) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			modifier = Modifier.padding(32.dp),
		) {
			Icon(
				Icons.Rounded.FolderOpen,
				null,
				tint = MaterialTheme.colorScheme.outlineVariant,
				modifier = Modifier.size(56.dp),
			)
			Spacer(Modifier.height(14.dp))
			Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
			Spacer(Modifier.height(6.dp))
			Text(
				detail,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 8.dp),
			)
			if (action != null) {
				Spacer(Modifier.height(16.dp))
				Button(onClick = onAction) { Text(action) }
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrumbBar(path: String, onNavigate: (String) -> Unit) {
	val parts = path.split('/').filter { it.isNotEmpty() }
	LazyRow(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		item {
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
				shape = RoundedCornerShape(8.dp),
				modifier = Modifier
					.clip(RoundedCornerShape(8.dp))
					.clickable { onNavigate("") },
			) {
				Text(
					text = "根目录",
					style = MaterialTheme.typography.labelSmall,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
				)
			}
		}
		itemsIndexed(parts) { i, part ->
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("/", color = MaterialTheme.colorScheme.outlineVariant, fontSize = 12.sp)
				Spacer(Modifier.width(6.dp))
				Surface(
					color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
					shape = RoundedCornerShape(8.dp),
					modifier = Modifier
						.clip(RoundedCornerShape(8.dp))
						.clickable { onNavigate(parts.subList(0, i + 1).joinToString("/")) },
				) {
					Text(
						text = part,
						style = MaterialTheme.typography.labelSmall,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
					)
				}
			}
		}
	}
}

private fun subtitleOf(entry: DavEntry): String {
	val size = when {
		entry.size < 0 -> ""
		entry.size < 1024 -> "${entry.size} B"
		entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
		entry.size < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", entry.size / 1048576.0)
		else -> String.format(Locale.US, "%.2f GB", entry.size / 1073741824.0)
	}
	val date = if (entry.modified > 0) {
		SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.modified))
	} else ""
	return listOf(size, date).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun TextInputDialog(
	title: String,
	initial: String,
	confirmText: String,
	onDismiss: () -> Unit,
	onConfirm: (String) -> Unit,
) {
	var value by remember { mutableStateOf(initial) }
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title, fontWeight = FontWeight.Bold) },
		text = {
			OutlinedTextField(
				value = value,
				onValueChange = { value = it },
				singleLine = true,
				label = { Text("名称") },
				modifier = Modifier.fillMaxWidth(),
			)
		},
		confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmText, fontWeight = FontWeight.Bold) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
	)
}
