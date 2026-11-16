package com.nasdrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nasdrop.ConnectionMode
import com.nasdrop.DavEntry
import com.nasdrop.FileCategory
import com.nasdrop.RouteInfo
import com.nasdrop.TimelineGroup
import com.nasdrop.UploadService
import com.nasdrop.Uploads
import com.nasdrop.ViewMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BrowserMode { Browse, Pick }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
	val snackbar = remember { SnackbarHostState() }
	val tasks by Uploads.tasks.collectAsStateWithLifecycle()
	val routeInfo by vm.routeInfo.collectAsStateWithLifecycle()

	var overflow by remember { mutableStateOf(false) }
	var renaming by remember { mutableStateOf<DavEntry?>(null) }
	var deleting by remember { mutableStateOf<DavEntry?>(null) }
	var creating by remember { mutableStateOf(false) }
	var showNetworkDialog by remember { mutableStateOf(false) }

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
								text = when {
									mode == BrowserMode.Pick && vm.isRoot -> "选择保存位置"
									vm.isRoot -> "NasDrop 网盘"
									else -> vm.path.substringAfterLast('/')
								},
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
							)
						}
						Spacer(Modifier.height(3.dp))
						// 现代化网络状态指示胶囊，点击弹出切换菜单
						NetworkStatusPill(
							routeInfo = routeInfo,
							linkStatus = vm.linkStatus,
							onClick = { showNetworkDialog = true },
						)
					}
				},
				navigationIcon = {
					if (!vm.isRoot) {
						IconButton(onClick = { vm.up() }) {
							Icon(Icons.Rounded.ArrowBack, contentDescription = "返回上级")
						}
					}
				},
				actions = {
					// 视图切换：列表模式 vs 时间线模式
					IconButton(onClick = { vm.toggleViewMode() }) {
						Icon(
							if (vm.viewMode == ViewMode.List) Icons.Rounded.ViewTimeline else Icons.Rounded.ViewList,
							contentDescription = "切换视图",
							tint = MaterialTheme.colorScheme.primary,
						)
					}
					IconButton(onClick = { vm.refresh() }) {
						Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
					}
					Box {
						IconButton(onClick = { overflow = true }) {
							Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
						}
						DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
							DropdownMenuItem(
								text = { Text("网络与连接方式") },
								leadingIcon = { Icon(Icons.Rounded.FlashOn, null) },
								onClick = { overflow = false; showNetworkDialog = true },
							)
							DropdownMenuItem(
								text = { Text("新建文件夹") },
								leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
								onClick = { overflow = false; creating = true },
							)
							DropdownMenuItem(
								text = { Text("服务器设置") },
								leadingIcon = { Icon(Icons.Rounded.Settings, null) },
								onClick = { overflow = false; onOpenSettings() },
							)
						}
					}
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface,
				),
			)
		},
		snackbarHost = { SnackbarHost(snackbar) },
		floatingActionButton = {
			if (mode == BrowserMode.Browse) {
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
			}
		},
	) { padding ->
		Column(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
			if (vm.needsLogin) LoginCard(onLogin)
			if (!vm.isRoot) CrumbBar(vm.path) { vm.open(it) }

			// 阿里云盘风格：分类标签横向滑动栏
			CategoryFilterRow(
				selected = vm.selectedCategory,
				onSelect = { vm.selectCategory(it) },
			)

			AnimatedVisibility(visible = tasks.any { !it.done }) {
				UploadStrip(
					done = tasks.count { it.done },
					total = tasks.size,
					progress = tasks.filter { !it.done }.map { it.progress }.maxOrNull() ?: 0f,
				)
			}

			// 纤细优雅的顶层刷新加载指示线
			if (vm.loading) {
				LinearProgressIndicator(
					modifier = Modifier.fillMaxWidth().height(2.dp),
					color = MaterialTheme.colorScheme.primary,
					trackColor = Color.Transparent,
				)
			} else {
				HorizontalDivider(
					color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
					thickness = 0.5.dp,
				)
			}

			val displayList = vm.filteredEntries
			when {
				vm.loading && vm.entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
					CircularProgressIndicator(strokeWidth = 3.dp)
				}

				vm.error != null && vm.entries.isEmpty() -> EmptyState(
					title = "打不开这个目录",
					detail = vm.error.orEmpty(),
					action = "重试",
					onAction = { vm.refresh() },
				)

				displayList.isEmpty() -> EmptyState(
					title = if (vm.selectedCategory != FileCategory.All) "没有该分类的文件" else "这里是空的",
					detail = if (mode == BrowserMode.Pick) "可以就这样存入根目录" else "点右下角上传，或从其他应用分享过来",
					action = if (vm.selectedCategory != FileCategory.All) "查看全部" else null,
					onAction = { vm.selectCategory(FileCategory.All) },
				)

				vm.viewMode == ViewMode.Timeline -> TimelineView(
					groups = vm.timelineGroups,
					thumbUrlFor = { vm.urlFor(it) },
					onClick = { entry ->
						when {
							entry.isDir -> vm.open(entry.path)
							entry.isImage -> onOpenImage(entry)
							entry.isVideo -> onOpenVideo(entry)
						}
					},
					onMenu = { deleting = it },
				)

				else -> LazyColumn(
					modifier = Modifier.fillMaxSize(),
					contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
				) {
					items(displayList, key = { it.path }) { entry ->
						EntryRow(
							entry = entry,
							thumbUrl = if (entry.isImage) vm.urlFor(entry.path) else null,
							onClick = {
								when {
									entry.isDir -> vm.open(entry.path)
									entry.isImage -> onOpenImage(entry)
									entry.isVideo -> onOpenVideo(entry)
								}
							},
							onLongClick = { if (mode == BrowserMode.Browse && !entry.isDir) renaming = entry },
							onMenu = { deleting = entry },
						)
					}
				}
			}
		}
	}

	// 网络切换与直连详情弹窗
	if (showNetworkDialog) {
		NetworkSwitchDialog(
			routeInfo = routeInfo,
			currentMode = vm.settings?.connectionMode ?: ConnectionMode.Auto,
			onSelectMode = { mode ->
				vm.setConnectionMode(mode)
				showNetworkDialog = false
			},
			onDismiss = { showNetworkDialog = false },
		)
	}

	renaming?.let { entry ->
		TextInputDialog(
			title = "重命名",
			initial = entry.name,
			confirmText = "保存",
			onDismiss = { renaming = null },
			onConfirm = { name ->
				renaming = null
				if (name.isNotBlank() && name != entry.name) vm.rename(entry, name.trim())
			},
		)
	}

	if (creating) {
		TextInputDialog(
			title = "新建文件夹",
			initial = "",
			confirmText = "创建",
			onDismiss = { creating = false },
			onConfirm = { name ->
				creating = false
				if (name.isNotBlank()) vm.mkdir(name.trim())
			},
		)
	}

	deleting?.let { entry ->
		AlertDialog(
			onDismissRequest = { deleting = null },
			title = { Text("删除确认", fontWeight = FontWeight.Bold) },
			text = { Text("确定删除「${entry.name}」${if (entry.isDir) "及其包含的全部内容" else ""}？此操作不可撤销。") },
			confirmButton = {
				TextButton(onClick = {
					deleting = null
					vm.delete(entry)
				}) { Text("彻底删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
			},
			dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
		)
	}
}

/** 现代网盘网络状态药丸徽章（直连高亮绿 / Tailscale科技蓝） */
@Composable
private fun NetworkStatusPill(
	routeInfo: RouteInfo,
	linkStatus: String,
	onClick: () -> Unit,
) {
	val isDirect = routeInfo.isDirect
	val bg = if (isDirect) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
	val fg = if (isDirect) Color(0xFF2E7D32) else Color(0xFF1565C0)
	val icon = if (isDirect) Icons.Rounded.FlashOn else Icons.Rounded.Security

	val text = when {
		routeInfo.isProbing -> "⚡ 正在探测直连…"
		isDirect -> if (routeInfo.latencyMs >= 0) "⚡ 直连 · ${routeInfo.latencyMs}ms" else "⚡ 直连模式"
		linkStatus.isNotEmpty() -> linkStatus
		else -> "🛡️ 内嵌 Tailscale"
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

/** 阿里云盘风格：横向滑动分类过滤 Chips */
@Composable
private fun CategoryFilterRow(
	selected: FileCategory,
	onSelect: (FileCategory) -> Unit,
) {
	LazyRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		items(FileCategory.values()) { cat ->
			val isChosen = cat == selected
			val bg = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
			val fg = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

			Surface(
				color = bg,
				shape = RoundedCornerShape(20.dp),
				modifier = Modifier
					.clip(RoundedCornerShape(20.dp))
					.clickable { onSelect(cat) },
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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

/** 时间线视图（按日期分组、带时间轴指示器） */
@Composable
private fun TimelineView(
	groups: List<TimelineGroup>,
	thumbUrlFor: (String) -> String?,
	onClick: (DavEntry) -> Unit,
	onMenu: (DavEntry) -> Unit,
) {
	LazyColumn(
		modifier = Modifier.fillMaxSize(),
		contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
	) {
		groups.forEach { group ->
			item(key = "header_${group.title}") {
				TimelineDateHeader(title = group.title, count = group.items.size)
			}
			items(group.items, key = { it.path }) { item ->
				TimelineItemRow(
					entry = item,
					thumbUrl = if (item.isImage) thumbUrlFor(item.path) else null,
					onClick = { onClick(item) },
					onMenu = { onMenu(item) },
				)
			}
		}
	}
}

@Composable
private fun TimelineDateHeader(title: String, count: Int) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 20.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(8.dp)
				.clip(CircleShape)
				.background(MaterialTheme.colorScheme.primary),
		)
		Spacer(Modifier.width(10.dp))
		Text(
			text = title,
			style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
			color = MaterialTheme.colorScheme.onSurface,
		)
		Spacer(Modifier.width(8.dp))
		Text(
			text = "${count}个文件",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.outline,
		)
	}
}

@Composable
private fun TimelineItemRow(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onMenu: () -> Unit,
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 3.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		// 竖直时间轴线
		Box(
			modifier = Modifier
				.padding(horizontal = 7.dp)
				.width(2.dp)
				.height(56.dp)
				.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
		)
		Spacer(Modifier.width(8.dp))

		EntryCard(
			entry = entry,
			thumbUrl = thumbUrl,
			onClick = onClick,
			onLongClick = {},
			onMenu = onMenu,
			showTime = true,
			modifier = Modifier.weight(1f),
		)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onMenu: () -> Unit,
) {
	EntryCard(
		entry = entry,
		thumbUrl = thumbUrl,
		onClick = onClick,
		onLongClick = onLongClick,
		onMenu = onMenu,
		showTime = false,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 14.dp, vertical = 4.dp),
	)
}

/** 阿里云盘质感卡片：彩色拟物化图标、大圆角、细腻副标题 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
	entry: DavEntry,
	thumbUrl: String?,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onMenu: () -> Unit,
	showTime: Boolean,
	modifier: Modifier = Modifier,
) {
	val (bgGradient, iconColor, icon) = fileVisuals(entry)

	Surface(
		color = MaterialTheme.colorScheme.surface,
		tonalElevation = 1.dp,
		shape = RoundedCornerShape(14.dp),
		border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
		modifier = modifier
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
					text = if (entry.isDir) "文件夹" else subtitleOf(entry, showTime),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
				)
			}

			if (entry.isDir) {
				Icon(
					Icons.Rounded.ChevronRight,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
					modifier = Modifier.size(20.dp),
				)
			} else {
				IconButton(onClick = onMenu, modifier = Modifier.size(34.dp)) {
					Icon(
						Icons.Rounded.MoreVert,
						contentDescription = "更多",
						tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
						modifier = Modifier.size(20.dp),
					)
				}
			}
		}
	}
}

/** 阿里云盘风格彩色文件类型视觉定义 */
private fun fileVisuals(entry: DavEntry): Triple<Brush, Color, ImageVector> = when {
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

/** 网络模式切换弹窗 */
@Composable
private fun NetworkSwitchDialog(
	routeInfo: RouteInfo,
	currentMode: ConnectionMode,
	onSelectMode: (ConnectionMode) -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text("网络连接模式", fontWeight = FontWeight.Bold)
		},
		text = {
			Column {
				Text(
					text = "当前状态: ${if (routeInfo.isDirect) "⚡ 直连模式" else "🛡️ 内嵌 Tailscale 组网"}" +
						if (routeInfo.latencyMs >= 0) " (${routeInfo.latencyMs}ms)" else "",
					style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
					color = if (routeInfo.isDirect) Color(0xFF2E7D32) else Color(0xFF1565C0),
				)
				Spacer(Modifier.height(14.dp))

				ConnectionModeOption(
					title = "智能直连 (推荐)",
					desc = "优先探测直连（超低延迟秒开），在外网或直连不通时自动回退至内嵌 Tailscale 组网",
					selected = currentMode == ConnectionMode.Auto,
					onClick = { onSelectMode(ConnectionMode.Auto) },
				)
				Spacer(Modifier.height(8.dp))

				ConnectionModeOption(
					title = "强制直连",
					desc = "完全不通过 Tailscale 隧道，直接访问 WebDAV 目标地址（适合同局域网 WiFi 或公网服务）",
					selected = currentMode == ConnectionMode.Direct,
					onClick = { onSelectMode(ConnectionMode.Direct) },
				)
				Spacer(Modifier.height(8.dp))

				ConnectionModeOption(
					title = "内嵌 Tailscale 组网",
					desc = "强制使用内置 Tailscale 用户态隧道访问，适合远程内网穿透",
					selected = currentMode == ConnectionMode.Tailscale,
					onClick = { onSelectMode(ConnectionMode.Tailscale) },
				)
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) { Text("关闭") }
		},
	)
}

@Composable
private fun ConnectionModeOption(
	title: String,
	desc: String,
	selected: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.clickable { onClick() },
	) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(
				if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
				contentDescription = null,
				tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
				modifier = Modifier.size(20.dp),
			)
			Spacer(Modifier.width(10.dp))
			Column {
				Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
				Spacer(Modifier.height(2.dp))
				Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
	}
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

private fun subtitleOf(entry: DavEntry, showTime: Boolean = false): String {
	val size = when {
		entry.size < 0 -> ""
		entry.size < 1024 -> "${entry.size} B"
		entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
		entry.size < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", entry.size / 1048576.0)
		else -> String.format(Locale.US, "%.2f GB", entry.size / 1073741824.0)
	}
	val datePattern = if (showTime) "HH:mm" else "yyyy-MM-dd"
	val date = if (entry.modified > 0) {
		SimpleDateFormat(datePattern, Locale.getDefault()).format(Date(entry.modified))
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
