package com.nasdrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nasdrop.BrowserViewModel
import com.nasdrop.DavEntry
import com.nasdrop.UploadService
import com.nasdrop.Uploads
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

	var overflow by remember { mutableStateOf(false) }
	var renaming by remember { mutableStateOf<DavEntry?>(null) }
	var deleting by remember { mutableStateOf<DavEntry?>(null) }
	var creating by remember { mutableStateOf(false) }

	val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		if (uris.isNotEmpty()) UploadService.start(context, uris, vm.path)
	}

	LaunchedEffect(vm.error) {
		vm.error?.let { snackbar.showSnackbar(it) }
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = {
					Column {
						Text(
							when {
								mode == BrowserMode.Pick && vm.isRoot -> "选择保存位置"
								vm.isRoot -> "NasDrop"
								else -> vm.path.substringAfterLast('/')
							},
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							style = MaterialTheme.typography.titleLarge,
						)
						LinkCaption(vm.linkStatus, vm.needsLogin)
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
					IconButton(onClick = { vm.refresh() }) {
						Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
					}
					Box {
						IconButton(onClick = { overflow = true }) {
							Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
						}
						DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
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
					text = { Text("上传") },
					icon = { Icon(Icons.Rounded.CloudUpload, null) },
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
		Column(Modifier.padding(padding).fillMaxSize()) {
			if (vm.needsLogin) LoginCard(onLogin)
			if (!vm.isRoot) CrumbBar(vm.path) { vm.open(it) }

			AnimatedVisibility(visible = tasks.any { !it.done }) {
				UploadStrip(
					done = tasks.count { it.done },
					total = tasks.size,
					progress = tasks.filter { !it.done }.map { it.progress }.maxOrNull() ?: 0f,
				)
			}

			HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

			when {
				vm.loading && vm.entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
					CircularProgressIndicator()
				}

				vm.error != null && vm.entries.isEmpty() -> EmptyState(
					title = "打不开这个目录",
					detail = vm.error.orEmpty(),
					action = "重试",
					onAction = { vm.refresh() },
				)

				vm.entries.isEmpty() -> EmptyState(
					title = "这里是空的",
					detail = if (mode == BrowserMode.Pick) "可以就这样存入根目录" else "点右下角上传，或从别的 App 分享过来",
					action = null,
					onAction = {},
				)

				else -> LazyColumn(Modifier.fillMaxSize()) {
					items(vm.entries, key = { it.path }) { entry ->
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
					item { Spacer(Modifier.height(96.dp)) }
				}
			}
		}
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
			title = { Text("删除") },
			text = { Text("确定删除「${entry.name}」${if (entry.isDir) "及其全部内容" else ""}？此操作不可撤销。") },
			confirmButton = {
				TextButton(onClick = {
					deleting = null
					vm.delete(entry)
				}) { Text("删除", color = MaterialTheme.colorScheme.error) }
			},
			dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
		)
	}
}

@Composable
private fun LinkCaption(status: String, needsLogin: Boolean) {
	val color = when {
		needsLogin -> MaterialTheme.colorScheme.error
		status.contains("失败") || status.contains("直连") -> MaterialTheme.colorScheme.outline
		status.contains("待启动") || status.contains("连接中") -> MaterialTheme.colorScheme.outline
		else -> MaterialTheme.colorScheme.primary
	}
	Text(
		if (status.isEmpty()) " " else status,
		style = MaterialTheme.typography.bodySmall,
		color = color,
		maxLines = 1,
		overflow = TextOverflow.Ellipsis,
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
			Text(title, style = MaterialTheme.typography.titleMedium)
			Spacer(Modifier.height(6.dp))
			Text(
				detail,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 8.dp),
			)
			if (action != null) {
				Spacer(Modifier.height(16.dp))
				TextButton(onClick = onAction) { Text(action) }
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrumbBar(path: String, onNavigate: (String) -> Unit) {
	val parts = path.split('/').filter { it.isNotEmpty() }
	LazyRow(
		modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
	) {
		item {
			AssistChip(onClick = { onNavigate("") }, label = { Text("根目录") })
		}
		itemsIndexed(parts) { i, part ->
			AssistChip(
				onClick = { onNavigate(parts.subList(0, i + 1).joinToString("/")) },
				label = { Text(part) },
			)
		}
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
	val scheme = MaterialTheme.colorScheme
	val (container, tint, icon) = when {
		entry.isDir -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Rounded.Folder)
		entry.isImage -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Rounded.Image)
		entry.isVideo -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.Movie)
		else -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, Icons.Rounded.InsertDriveFile)
	}

	Row(
		modifier = Modifier
			.fillMaxWidth()
			.combinedClickable(onClick = onClick, onLongClick = onLongClick)
			.padding(horizontal = 16.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(container),
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
				Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
			}
		}

		Spacer(Modifier.width(14.dp))

		Column(Modifier.weight(1f)) {
			Text(
				entry.name,
				style = MaterialTheme.typography.titleMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				if (entry.isDir) "文件夹" else subtitleOf(entry),
				style = MaterialTheme.typography.bodySmall,
				color = scheme.onSurfaceVariant,
				maxLines = 1,
			)
		}

		if (entry.isDir) {
			Icon(
				Icons.Rounded.ChevronRight,
				contentDescription = null,
				tint = scheme.outline,
				modifier = Modifier.size(20.dp),
			)
		} else {
			IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
				Icon(
					Icons.Rounded.MoreVert,
					contentDescription = "更多",
					tint = scheme.outline,
					modifier = Modifier.size(20.dp),
				)
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
		title = { Text(title) },
		text = {
			OutlinedTextField(
				value = value,
				onValueChange = { value = it },
				singleLine = true,
				label = { Text("名称") },
			)
		},
		confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirmText) } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
	)
}
