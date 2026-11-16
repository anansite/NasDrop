package com.nasdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserViewModel(private val app: NasDropApp) : ViewModel() {

	var settings by mutableStateOf<Settings?>(null)
		private set
	var entries by mutableStateOf<List<DavEntry>>(emptyList())
		private set
	var path by mutableStateOf("")
		private set
	var loading by mutableStateOf(false)
		private set
	var error by mutableStateOf<String?>(null)
		private set

	/** 形如「内嵌 Tailscale · 100.87.88.9」/「内嵌 Tailscale · 等待登录」 */
	var linkStatus by mutableStateOf("")
		private set

	/** 需要登录时的一次性授权链接，由轮询填好，点按钮时直接取缓存 */
	var authUrl by mutableStateOf("")
		private set

	private var client: WebDavClient? = null

	val isRoot: Boolean get() = path.isEmpty()
	val needsLogin: Boolean get() = linkStatus.contains("等待登录")

	init {
		viewModelScope.launch { open("") }
		// 节点状态是异步变化的（首次还要等用户在浏览器里点授权），轻量轮询让它自己长出来。
		// 所有 TsBridge 调用都放在 IO 线程：Go 侧的 Status() 最长会等 3 秒。
		viewModelScope.launch { pollLink() }
	}

	fun urlFor(entryPath: String): String? = client?.urlFor(entryPath)?.toString()

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun open(target: String) {
		viewModelScope.launch {
			loading = true
			error = null
			try {
				val s = settings ?: app.prefs.current().also { settings = it }
				val c = withContext(Dispatchers.IO) { app.repo.client(s) }.also { client = it }
				entries = withContext(Dispatchers.IO) { c.list(target) }
				path = target
			} catch (t: Throwable) {
				error = t.message ?: t.javaClass.simpleName
			} finally {
				loading = false
			}
		}
	}

	fun refresh() = open(path)

	fun up() {
		if (path.isNotEmpty()) open(path.substringBeforeLast('/', ""))
	}

	fun mkdir(name: String) = mutate { it.mkcol(join(path, name)) }

	fun delete(entry: DavEntry) = mutate { it.delete(entry.path, entry.isDir) }

	fun rename(entry: DavEntry, newName: String) = mutate {
		it.move(entry.path, join(entry.path.substringBeforeLast('/', ""), newName))
	}

	/** 记住用户选择的上传目录，下次分享流程默认落在这里 */
	fun rememberDir(dir: String) {
		viewModelScope.launch { app.prefs.setLastDir(dir) }
	}

	fun reloadSettings() {
		viewModelScope.launch {
			app.repo.invalidate()
			settings = app.prefs.current()
			open(path)
		}
	}

	/** 当前目录里的图片，以及 entry 在其中的位置，给看图器做左右滑动 */
	fun imageAlbum(entry: DavEntry): Pair<List<DavEntry>, Int> {
		val imgs = entries.filter { it.isImage }
		val i = imgs.indexOfFirst { it.path == entry.path }
		return imgs to i.coerceAtLeast(0)
	}

	private suspend fun pollLink() {
		while (true) {
			val s = settings ?: app.prefs.current().also { settings = it }
			// 第一次读 TsBridge.available 会触发 Class.forName，进而加载 libgojni.so，
			// 放到 IO 线程做，别卡主线程。
			val embedded = withContext(Dispatchers.IO) { TsBridge.available }
			if (s.useTailscale && embedded) {
				val st = withContext(Dispatchers.IO) { TsBridge.status() }
				linkStatus = label(st)
				authUrl = if (st == "needs-login") withContext(Dispatchers.IO) { TsBridge.authUrl() } else ""
			} else {
				linkStatus = if (s.useTailscale) "直连模式（未打包 Tailscale 桥）" else "直连模式"
				authUrl = ""
			}
			delay(2500)
		}
	}

	private fun label(st: String): String = when {
		st.startsWith("running") ->
			"内嵌 Tailscale · " + st.removePrefix("running:").removePrefix("running").trim(':')
				.ifEmpty { "已连接" }
		st == "needs-login" -> "内嵌 Tailscale · 等待登录"
		st == "starting" -> "内嵌 Tailscale · 连接中"
		st.startsWith("error") -> "内嵌 Tailscale 失败"
		st == "idle" || st.isEmpty() -> "内嵌 Tailscale · 待启动"
		else -> "内嵌 Tailscale · $st"
	}

	private fun mutate(block: (WebDavClient) -> Unit) {
		viewModelScope.launch {
			loading = true
			error = null
			try {
				val c = client ?: app.repo.client(app.prefs.current()).also { client = it }
				withContext(Dispatchers.IO) { block(c) }
				entries = withContext(Dispatchers.IO) { c.list(path) }
			} catch (t: Throwable) {
				error = t.message ?: t.javaClass.simpleName
			} finally {
				loading = false
			}
		}
	}

	companion object {
		fun join(dir: String, name: String) =
			listOf(dir.trim('/'), name.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
	}
}
