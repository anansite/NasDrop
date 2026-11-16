package com.nasdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class ViewMode {
	List,
	Timeline,
}

data class TimelineGroup(
	val title: String,
	val items: List<DavEntry>,
)

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

	/** 形如「内嵌 Tailscale · 100.87.88.9」/「直连模式 · 12ms」 */
	var linkStatus by mutableStateOf("")
		private set

	/** 需要登录时的一次性授权链接 */
	var authUrl by mutableStateOf("")
		private set

	/** 分类标签筛选：全部 / 📷 图片 / 🎬 视频 / 📄 文档 / 📦 压缩包 / 🎵 音频 */
	var selectedCategory by mutableStateOf(FileCategory.All)
		private set

	/** 视图模式：普通列表 vs 时间线视图 */
	var viewMode by mutableStateOf(ViewMode.List)
		private set

	/** 路由信息：当前是否直连、延迟等 */
	val routeInfo: StateFlow<RouteInfo> = app.repo.routeInfo

	private var client: WebDavClient? = null

	/** 目录内存缓存：实现 0ms 瞬间秒开（Stale-While-Revalidate），彻底消除跳转延迟 */
	private val dirCache = ConcurrentHashMap<String, List<DavEntry>>()

	val isRoot: Boolean get() = path.isEmpty()
	val needsLogin: Boolean get() = linkStatus.contains("等待登录")

	/** 经过分类筛选后的条目列表（保留文件夹，文件按分类筛选） */
	val filteredEntries: List<DavEntry>
		get() {
			if (selectedCategory == FileCategory.All) return entries
			return entries.filter { it.isDir || it.category == selectedCategory }
		}

	/** 时间线分组（按文件的最后修改日期降序分组） */
	val timelineGroups: List<TimelineGroup>
		get() = groupEntriesForTimeline(filteredEntries)

	init {
		viewModelScope.launch { open("") }
		viewModelScope.launch { pollLink() }
	}

	fun urlFor(entryPath: String): String? = client?.urlFor(entryPath)?.toString()

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun selectCategory(cat: FileCategory) {
		selectedCategory = cat
	}

	fun toggleViewMode() {
		viewMode = if (viewMode == ViewMode.List) ViewMode.Timeline else ViewMode.List
	}

	fun setConnectionMode(mode: ConnectionMode) {
		viewModelScope.launch {
			app.prefs.setConnectionMode(mode)
			reloadSettings()
		}
	}

	/**
	 * 打开指定目录。
	 * 采用 Stale-While-Revalidate 机制：
	 * 1. 如果有缓存，0ms 立即渲染，页面瞬间进入，无任何等待感知；
	 * 2. 同时在后台并发拉取最新目录数据，拉取完成平滑更新，保证数据最终一致。
	 */
	fun open(target: String) {
		// 1. 命中缓存：立即呈现
		val cached = dirCache[target]
		if (cached != null) {
			entries = cached
			path = target
			loading = false
		} else {
			// 未命中缓存（首次进入该目录），展示轻量加载状态
			loading = true
		}
		error = null

		viewModelScope.launch {
			try {
				val s = settings ?: app.prefs.current().also { settings = it }
				val c = withContext(Dispatchers.IO) { app.repo.client(s) }.also { client = it }
				val fresh = withContext(Dispatchers.IO) { c.list(target) }
				dirCache[target] = fresh
				// 如果当前页面依然停留在 target 路径，则更新展示
				if (path == target || cached == null) {
					entries = fresh
					path = target
				}
			} catch (t: Throwable) {
				if (cached == null) {
					error = t.message ?: t.javaClass.simpleName
				}
			} finally {
				loading = false
			}
		}
	}

	fun refresh() {
		dirCache.remove(path)
		open(path)
	}

	fun up() {
		if (path.isNotEmpty()) {
			val parent = path.substringBeforeLast('/', "")
			open(parent)
		}
	}

	fun mkdir(name: String) = mutate { it.mkcol(join(path, name)) }

	fun delete(entry: DavEntry) = mutate { it.delete(entry.path, entry.isDir) }

	fun rename(entry: DavEntry, newName: String) = mutate {
		it.move(entry.path, join(entry.path.substringBeforeLast('/', ""), newName))
	}

	fun rememberDir(dir: String) {
		viewModelScope.launch { app.prefs.setLastDir(dir) }
	}

	fun reloadSettings() {
		viewModelScope.launch {
			dirCache.clear()
			app.repo.invalidate()
			settings = app.prefs.current()
			open(path)
		}
	}

	fun imageAlbum(entry: DavEntry): Pair<List<DavEntry>, Int> {
		val imgs = entries.filter { it.isImage }
		val i = imgs.indexOfFirst { it.path == entry.path }
		return imgs to i.coerceAtLeast(0)
	}

	private suspend fun pollLink() {
		while (true) {
			val s = settings ?: app.prefs.current().also { settings = it }
			val currentRoute = app.repo.routeInfo.value
			if (currentRoute.isDirect) {
				linkStatus = currentRoute.label
				authUrl = ""
			} else {
				val embedded = withContext(Dispatchers.IO) { TsBridge.available }
				if (s.useTailscale && embedded) {
					val st = withContext(Dispatchers.IO) { TsBridge.status() }
					linkStatus = label(st)
					authUrl = if (st == "needs-login") withContext(Dispatchers.IO) { TsBridge.authUrl() } else ""
				} else {
					linkStatus = "直连模式"
					authUrl = ""
				}
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
				dirCache.remove(path)
				val fresh = withContext(Dispatchers.IO) { c.list(path) }
				dirCache[path] = fresh
				entries = fresh
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

		private fun groupEntriesForTimeline(list: List<DavEntry>): List<TimelineGroup> {
			val files = list.filter { !it.isDir }
			if (files.isEmpty()) return emptyList()

			val now = Calendar.getInstance()
			val todayYear = now.get(Calendar.YEAR)
			val todayDayOfYear = now.get(Calendar.DAY_OF_YEAR)

			val groups = LinkedHashMap<String, MutableList<DavEntry>>()
			val sdfDate = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
			val sdfMonth = SimpleDateFormat("yyyy年MM月", Locale.getDefault())

			val sorted = files.sortedByDescending { it.modified }
			for (item in sorted) {
				val title = if (item.modified <= 0) {
					"未知时间"
				} else {
					val c = Calendar.getInstance().apply { timeInMillis = item.modified }
					val itemYear = c.get(Calendar.YEAR)
					val itemDayOfYear = c.get(Calendar.DAY_OF_YEAR)
					when {
						itemYear == todayYear && itemDayOfYear == todayDayOfYear -> "今天"
						itemYear == todayYear && itemDayOfYear == todayDayOfYear - 1 -> "昨天"
						itemYear == todayYear -> sdfDate.format(Date(item.modified))
						else -> sdfMonth.format(Date(item.modified))
					}
				}
				groups.getOrPut(title) { ArrayList() }.add(item)
			}

			return groups.map { TimelineGroup(it.key, it.value) }
		}
	}
}
