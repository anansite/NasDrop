package com.nasdrop

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nasdrop.ui.BrowserMode
import com.nasdrop.ui.BrowserScreen
import com.nasdrop.ui.ImageViewer
import com.nasdrop.ui.NasDropTheme
import com.nasdrop.ui.SettingsScreen
import com.nasdrop.ui.UpdateDialog
import com.nasdrop.ui.AudioPlayer
import com.nasdrop.ui.VideoPlayer

/** 极简路由。只有五个页面，不值得为它引入 navigation-compose。 */
private sealed interface Route {
	data object Browser : Route
	data object Settings : Route
	data class Images(val list: List<DavEntry>, val index: Int) : Route
	data class Video(val entry: DavEntry) : Route
	data class Audio(val entry: DavEntry, val playlist: List<DavEntry> = listOf(entry)) : Route
}

/** 主浏览入口；处理分享完成后的目录跳转，以及通知栏进入传输中心的请求。 */
class MainActivity : ComponentActivity() {
	private var uploadDirectory by mutableStateOf<String?>(null)
	private var transferRequest by mutableIntStateOf(0)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		handleUploadIntent(intent)
		enableEdgeToEdge()
		setContent { App() }
	}

	/** 分享结果打开目标目录，上传通知打开传输中心；复用已有 Activity 时也响应。 */
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleUploadIntent(intent)
	}

	/** 提取一次性上传导航参数，不改变服务器配置或上传任务本身。 */
	private fun handleUploadIntent(intent: Intent) {
		uploadDirectory = intent.getStringExtra(EXTRA_UPLOAD_DIR)
		if (intent.getBooleanExtra(EXTRA_SHOW_TRANSFERS, false)) transferRequest++
		intent.removeExtra(EXTRA_UPLOAD_DIR)
		intent.removeExtra(EXTRA_SHOW_TRANSFERS)
	}

	@Composable
	private fun App() {
		val app = applicationContext as NasDropApp
		val vm: BrowserViewModel = viewModel(
			factory = viewModelFactory { initializer { BrowserViewModel(app) } },
		)
		val settings by vm.settings.collectAsStateWithLifecycle(initialValue = Settings())
		NasDropTheme(palette = com.nasdrop.ui.ThemePalette.fromId(settings.themePalette)) {
			val context = LocalContext.current
			var route by remember { mutableStateOf<Route>(Route.Browser) }
			LaunchedEffect(uploadDirectory) {
				uploadDirectory?.let { dir ->
					vm.currentTab = MainTab.Files
					vm.open(dir)
					route = Route.Browser
					uploadDirectory = null
				}
			}
			LaunchedEffect(transferRequest) {
				if (transferRequest > 0) route = Route.Browser
			}

			// 上传进度通知需要这个权限；拒绝也不影响功能，只是看不到通知栏进度
			val askNotifications = rememberLauncherForActivityResult(
				ActivityResultContracts.RequestPermission(),
			) {}
			LaunchedEffect(Unit) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
				}
				// 第一次打开（还没填密码）直接进设置页，省一次找入口
				if (app.prefs.current().password.isEmpty()) route = Route.Settings
			}

			// 从后台回到前台：tsnet 隧道可能已随后台进程断开，用持久化状态原地重连。
			// 跳过首次 ON_RESUME：冷启动时 open("") 已经会启动隧道，无需重复重连造成端口抖动。
			val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
			var firstResume = true
			androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
				val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
					if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
						if (firstResume) {
							firstResume = false
						} else {
							vm.reconnectIfNeeded()
						}
					}
				}
				lifecycleOwner.lifecycle.addObserver(observer)
				onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
			}

			val login: () -> Unit = {
				val url = vm.loginUrl()
				if (url.isEmpty()) {
					Toast.makeText(context, "内嵌节点还没就绪，稍等几秒再点", Toast.LENGTH_SHORT).show()
				} else {
					runCatching {
						CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
					}.onFailure {
						Toast.makeText(context, "打不开浏览器：${it.message}", Toast.LENGTH_LONG).show()
					}
				}
			}

			androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
				// 保持 BrowserScreen 常驻组合树，点开图片/视频或设置再返回时完整保留各页滚动位置
				BackHandler(enabled = route == Route.Browser && (vm.activePhotoGroupId != null || vm.isMultiSelectMode || vm.currentTab != MainTab.Home || (!vm.isRoot && vm.currentTab == MainTab.Files))) {
					if (vm.activePhotoGroupId != null) {
						vm.activePhotoGroupId = null
					} else if (vm.isMultiSelectMode) {
						vm.exitMultiSelect()
					} else if (vm.currentTab == MainTab.Photos && vm.selectedAlbumPath.isNotBlank()) {
						vm.upAlbum()
					} else if (vm.currentTab == MainTab.Media && vm.selectedMediaPath.isNotBlank()) {
						vm.upMedia()
					} else if (vm.currentTab == MainTab.Files && !vm.isRoot) {
						vm.up()
					} else if (vm.currentTab != MainTab.Home) {
						vm.currentTab = MainTab.Home
					}
				}
				BrowserScreen(
					vm = vm,
					mode = BrowserMode.Browse,
					onOpenImage = { entry ->
						vm.viewerInitialEntry = entry
						val (album, index) = vm.imageAlbum(entry)
						route = Route.Images(album, index)
					},
					onOpenVideo = { entry -> route = Route.Video(entry) },
					onOpenAudio = { entry -> route = Route.Audio(entry, vm.audioPlaylistFor(entry)) },
					onOpenSettings = { vm.currentTab = MainTab.Settings },
					onLogin = login,
					transferRequest = transferRequest,
				)

				when (val r = route) {
					Route.Browser -> {}

					Route.Settings -> {
						BackHandler { route = Route.Browser }
						SettingsScreen(vm = vm, onBack = { route = Route.Browser })
					}

					is Route.Images -> {
						ImageViewer(
							images = r.list,
							startIndex = r.index,
							urlOf = { path -> vm.directUrlFor(path) },
							fallbackUrlOf = { path -> vm.urlFor(path) },
							thumbOf = { vm.thumbnailUrlFor(it) },
							audioUrlOf = { path -> vm.audioUrlFor(path) },
							onBack = { currentEntry ->
								if (currentEntry != null && currentEntry.path != vm.viewerInitialEntry?.path) {
									vm.syncViewerPosition(currentEntry)
								}
								vm.viewerInitialEntry = null
								route = Route.Browser
							},
							onSaveToGallery = { entry, cb -> vm.saveImageToGallery(entry, cb) },
							onShare = { entry, cb -> vm.prepareShareImage(entry, cb) },
							onDelete = { entry -> vm.delete(entry) },
							onSaveAudio = { entry, bytes, dur, cb -> vm.savePhotoVoiceNote(entry.path, bytes, dur, cb) },
							onDeleteAudio = { entry, cb -> vm.deletePhotoVoiceNote(entry.path, cb) },
							onSaveMemo = { entry, memo -> vm.savePhotoMemo(entry.path, memo) },
							onDeleteMemo = { entry -> vm.deletePhotoMemo(entry.path) },
						)
					}

					is Route.Video -> {
						VideoPlayer(
							entry = r.entry,
							url = vm.directUrlFor(r.entry.path),
							audioUrlOf = { path -> vm.audioUrlFor(path) },
							onBack = { route = Route.Browser },
							onSaveToGallery = { entry, cb -> vm.saveVideoToGallery(entry, cb) },
							onSaveAudio = { entry, bytes, dur, cb -> vm.savePhotoVoiceNote(entry.path, bytes, dur, cb) },
							onDeleteAudio = { entry, cb -> vm.deletePhotoVoiceNote(entry.path, cb) },
							onSaveMemo = { entry, memo -> vm.savePhotoMemo(entry.path, memo) },
							onDeleteMemo = { entry -> vm.deletePhotoMemo(entry.path) },
						)
					}
					is Route.Audio -> {
						AudioPlayer(
							entry = r.entry,
							playlist = r.playlist,
							urlOf = { path -> vm.directUrlFor(path) },
							onBack = { route = Route.Browser },
							onSaveToMusic = { entry, cb -> vm.saveAudioToMusic(entry, cb) },
							onOpenExternal = { entry -> vm.openWithExternalViewer(context, entry) },
						)
					}
				}
			}

			if (vm.showUpdateDialog) {
				vm.latestUpdateInfo?.let { info ->
					val context = androidx.compose.ui.platform.LocalContext.current
					UpdateDialog(
						info = info,
						isDownloading = vm.isDownloadingUpdate,
						downloadProgress = vm.updateProgress,
						speedText = vm.updateSpeedText,
						currentVersionName = vm.currentVersionName,
						onStartUpdate = { vm.startDownloadUpdate(context) },
						onDismiss = { vm.dismissUpdateDialog() },
					)
				}
			}
		}
	}

	companion object {
		const val EXTRA_UPLOAD_DIR = "upload_dir"
		const val EXTRA_SHOW_TRANSFERS = "show_transfers"
	}
}
