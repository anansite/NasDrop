package com.nasdrop

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nasdrop.ui.BrowserMode
import com.nasdrop.ui.BrowserScreen
import com.nasdrop.ui.ImageViewer
import com.nasdrop.ui.NasDropTheme
import com.nasdrop.ui.SettingsScreen
import com.nasdrop.ui.UpdateDialog
import com.nasdrop.ui.VideoPlayer

/** 极简路由。只有四个页面，不值得为它引入 navigation-compose。 */
private sealed interface Route {
	data object Browser : Route
	data object Settings : Route
	data class Images(val list: List<DavEntry>, val index: Int) : Route
	data class Video(val entry: DavEntry) : Route
}

class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent { App() }
	}

	@Composable
	private fun App() {
		NasDropTheme {
			val context = LocalContext.current
			val app = applicationContext as NasDropApp
			val vm: BrowserViewModel = viewModel(
				factory = viewModelFactory { initializer { BrowserViewModel(app) } },
			)
			var route by remember { mutableStateOf<Route>(Route.Browser) }

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

			when (val r = route) {
				Route.Browser -> {
					BackHandler(enabled = vm.isMultiSelectMode || vm.currentTab != MainTab.Files || !vm.isRoot) {
						if (vm.isMultiSelectMode) {
							vm.exitMultiSelect()
						} else if (vm.currentTab != MainTab.Files) {
							vm.currentTab = MainTab.Files
						} else {
							vm.up()
						}
					}
					BrowserScreen(
						vm = vm,
						mode = BrowserMode.Browse,
						onOpenImage = { entry ->
							val (album, index) = vm.imageAlbum(entry)
							route = Route.Images(album, index)
						},
						onOpenVideo = { entry -> route = Route.Video(entry) },
						onOpenSettings = { route = Route.Settings },
						onLogin = login,
					)
				}

				Route.Settings -> {
					BackHandler { route = Route.Browser }
					SettingsScreen(vm = vm, onBack = { route = Route.Browser })
				}

				is Route.Images -> {
					BackHandler { route = Route.Browser }
					ImageViewer(
						images = r.list,
						startIndex = r.index,
						urlOf = { vm.urlFor(it) },
						onBack = { route = Route.Browser },
						onSaveToGallery = { entry, cb -> vm.saveImageToGallery(entry, cb) },
						onShare = { entry, cb -> vm.prepareShareImage(entry, cb) },
						onDelete = { entry -> vm.delete(entry) },
					)
				}

				is Route.Video -> {
					BackHandler { route = Route.Browser }
					VideoPlayer(
						entry = r.entry,
						url = vm.urlFor(r.entry.path),
						onBack = { route = Route.Browser },
					)
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
						onStartUpdate = { vm.startDownloadUpdate(context) },
						onDismiss = { vm.dismissUpdateDialog() },
					)
				}
			}
		}
	}
}
