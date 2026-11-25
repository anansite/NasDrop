package com.nasdrop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nasdrop.ui.BrowserMode
import com.nasdrop.ui.BrowserScreen
import com.nasdrop.ui.NasDropTheme
import com.nasdrop.ui.UploadProgressScreen
import com.nasdrop.ui.SettingsScreen

/**
 * 相册/微信/文件管理器里「分享 → 存入 NasDrop」的落地页。
 *
 * 两个要点：
 *  1. 先让用户选目标目录，默认停在上次选的那个（记在 Prefs.lastDir），支持自由进入子文件夹、回退与新建目录；
 *  2. 开始上传后不要马上 finish —— ACTION_SEND 的 content:// 读取授权只在
 *     这个任务存活期间有效，提前关掉会让大文件传到一半失败。
 */
class ShareActivity : ComponentActivity() {

	private var uris: List<Uri> = emptyList()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		uris = collectUris(intent)
		if (uris.isEmpty()) {
			finish()
			return
		}
		// 部分来源支持持久化授权，能拿到就拿到，拿不到靠下面的进度页保活
		uris.forEach {
			runCatching {
				contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
		}
		setContent { ShareFlow() }
	}

	@Composable
	private fun ShareFlow() {
		NasDropTheme {
			val app = applicationContext as NasDropApp
			val vm: BrowserViewModel = viewModel(
				factory = viewModelFactory { initializer { BrowserViewModel(app) } },
			)
			var lastDir by remember { mutableStateOf("") }
			var batchId by rememberSaveable { mutableStateOf<String?>(null) }
			var uploadDir by rememberSaveable { mutableStateOf("") }
			var uploadSession by rememberSaveable { mutableStateOf(Uploads.sessionId) }
			var showSettings by rememberSaveable { mutableStateOf(false) }
			val interrupted = uploadSession != Uploads.sessionId

			LaunchedEffect(Unit) {
				vm.currentTab = MainTab.Files
				val s = app.prefs.current()
				lastDir = s.lastDir
				vm.open("")
			}
			LaunchedEffect(vm.shareUploadBatchId) {
				vm.shareUploadBatchId?.let { id ->
					batchId = id
					uploadDir = vm.shareUploadDirectory
					uploadSession = Uploads.sessionId
				}
			}

			// 拦截返回键：如果已进入子目录，按返回键优先返回上级目录；在根目录时再退出
			BackHandler {
				if (showSettings) {
					showSettings = false
				} else if (batchId != null) {
					val batch = Uploads.tasks.value.filter { it.batchId == batchId }
					// 服务尚未发布任务也不能退出，否则分享来源的临时读取授权会被提前释放。
					if (!interrupted && (batch.isEmpty() || batch.any { !it.done })) {
						android.widget.Toast.makeText(this@ShareActivity, "正在上传，请保留此窗口直到完成", android.widget.Toast.LENGTH_SHORT).show()
					} else finish()
				} else if (vm.path.isNotEmpty()) {
					vm.up()
				} else {
					finish()
				}
			}

			if (showSettings) {
				SettingsScreen(vm = vm, onBack = { showSettings = false })
			} else if (batchId != null) {
				UploadProgressScreen(
					count = uris.size,
					batchId = batchId!!,
					interrupted = interrupted,
					onDone = { finish() },
					onBrowse = {
						startActivity(Intent(this@ShareActivity, MainActivity::class.java).apply {
							putExtra(MainActivity.EXTRA_UPLOAD_DIR, uploadDir)
							addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
						})
						finish()
					},
				)
			} else {
				BrowserScreen(
					vm = vm,
					mode = BrowserMode.Pick,
					lastDir = lastDir,
					onOpenSettings = { showSettings = true },
					onLogin = {
						val url = vm.loginUrl()
						if (url.isBlank()) android.widget.Toast.makeText(this@ShareActivity, "节点尚未准备好，请稍后重试", android.widget.Toast.LENGTH_SHORT).show()
						else runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
							.onFailure { android.widget.Toast.makeText(this@ShareActivity, "无法打开授权页面", android.widget.Toast.LENGTH_LONG).show() }
					},
					onConfirm = { dir ->
						vm.startShareUploads(uris, dir)
					},
				)
			}
		}
	}

	@Suppress("DEPRECATION")
	private fun collectUris(intent: Intent?): List<Uri> {
		if (intent == null) return emptyList()
		val out = LinkedHashSet<Uri>()

		intent.clipData?.let { clip ->
			for (i in 0 until clip.itemCount) {
				clip.getItemAt(i)?.uri?.let { out += it }
			}
		}
		runCatching { intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) }
			.getOrNull()?.let { out += it }
		runCatching { intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) }
			.getOrNull()?.let { out += it }

		return out.toList()
	}
}
