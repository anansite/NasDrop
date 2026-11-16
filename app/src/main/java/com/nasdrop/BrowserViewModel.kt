package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class MainTab {
	Files,
	Photos,
	Media,
	Me,
}

enum class ViewMode {
	Cards,   // 详细卡片视图
	Compact, // 极简紧凑列表
	Grid,    // 2列宫格大图
}

data class TimelineGroup(
	val title: String,
	val items: List<DavEntry>,
)

class BrowserViewModel(private val app: NasDropApp) : ViewModel() {

	var currentTab by mutableStateOf(MainTab.Files)

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

	var linkStatus by mutableStateOf("")
		private set

	var authUrl by mutableStateOf("")
		private set

	/** 文件筛选分类：全部 / 图片 / 视频 / 文档 / 压缩包 / 音频 */
	var selectedCategory by mutableStateOf(FileCategory.All)
		private set

	/** 视图模式：详细卡片 vs 紧凑列表 vs 宫格大图 */
	var viewMode by mutableStateOf(ViewMode.Cards)
		private set

	/** 全局搜索关键词 */
	var searchQuery by mutableStateOf("")

	/** 全局聚合照片池（跨目录汇总所有照片，类似主流网盘相册） */
	var allPhotos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 全局聚合视频池（跨目录汇总所有视频，类似主流网盘影音） */
	var allVideos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 当前已发现的所有文件夹相对路径集合（供文件移动对话框选择） */
	var allFolderPaths by mutableStateOf<List<String>>(listOf(""))
		private set

	val routeInfo: StateFlow<RouteInfo> = app.repo.routeInfo

	private var client: WebDavClient? = null

	/** 目录内存缓存：0ms 瞬间秒开（Stale-While-Revalidate） */
	private val dirCache = ConcurrentHashMap<String, List<DavEntry>>()

	val isRoot: Boolean get() = path.isEmpty()
	val needsLogin: Boolean get() = linkStatus.contains("等待登录")

	/** 经过分类筛选与搜索后的条目列表 */
	val filteredEntries: List<DavEntry>
		get() {
			val baseList = when (selectedCategory) {
				FileCategory.All -> entries
				FileCategory.Image -> {
					val local = entries.filter { !it.isDir && it.isImage }
					if (local.isNotEmpty()) local else allPhotos
				}
				FileCategory.Video -> {
					val local = entries.filter { !it.isDir && it.isVideo }
					if (local.isNotEmpty()) local else allVideos
				}
				FileCategory.Document -> entries.filter { !it.isDir && it.isDocument }
				FileCategory.Audio -> entries.filter { !it.isDir && it.isAudio }
				FileCategory.Archive -> entries.filter { !it.isDir && it.isArchive }
			}

			if (searchQuery.isBlank()) return baseList
			return baseList.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
		}

	/** 相册时间线分组数据（按拍摄/修改日期降序） */
	val photoTimelineGroups: List<TimelineGroup>
		get() = groupEntriesForTimeline(allPhotos)

	init {
		viewModelScope.launch { open("") }
		viewModelScope.launch { pollLink() }
		viewModelScope.launch { scanGlobalMedia() }
	}

	fun urlFor(entryPath: String): String? = client?.urlFor(entryPath)?.toString()

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun selectCategory(cat: FileCategory) {
		selectedCategory = cat
	}

	fun cycleViewMode() {
		viewMode = when (viewMode) {
			ViewMode.Cards -> ViewMode.Compact
			ViewMode.Compact -> ViewMode.Grid
			ViewMode.Grid -> ViewMode.Cards
		}
	}

	/** 触发 NAT 打洞直连 */
	fun punchHole(): String {
		val res = app.repo.triggerHolePunch()
		pollPeerStatus()
		return res
	}

	fun open(target: String) {
		val cached = dirCache[target]
		if (cached != null) {
			entries = cached
			path = target
			loading = false
		} else {
			loading = true
		}
		error = null

		viewModelScope.launch {
			try {
				val s = settings ?: app.prefs.current().also { settings = it }
				val c = withContext(Dispatchers.IO) { app.repo.client(s) }.also { client = it }
				val fresh = withContext(Dispatchers.IO) { c.list(target) }
				dirCache[target] = fresh
				if (path == target || cached == null) {
					entries = fresh
					path = target
				}
				collectMediaFrom(fresh)
				updateFolderPaths(fresh)
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
		viewModelScope.launch { scanGlobalMedia() }
	}

	fun up() {
		if (path.isNotEmpty()) {
			val parent = path.substringBeforeLast('/', "")
			open(parent)
		}
	}

	/** 新建普通子文件夹或专属相册 */
	fun mkdir(name: String, isAlbum: Boolean = false) {
		val finalName = if (isAlbum && !name.contains("相册")) "$name 相册" else name
		mutate { it.mkcol(join(path, finalName)) }
	}

	fun delete(entry: DavEntry) = mutate { it.delete(entry.path, entry.isDir) }

	fun rename(entry: DavEntry, newName: String) = mutate {
		it.move(entry.path, join(entry.path.substringBeforeLast('/', ""), newName))
	}

	/** 移动文件到目标文件夹 */
	fun move(entry: DavEntry, targetDir: String) = mutate {
		val dest = join(targetDir, entry.name)
		it.move(entry.path, dest)
	}

	/**
	 * 将网盘文件下载并保存到本地（相册/Download目录）
	 */
	fun download(context: Context, entry: DavEntry, onResult: (Boolean, String) -> Unit) {
		viewModelScope.launch {
			withContext(Dispatchers.IO) {
				try {
					val c = client ?: app.repo.client(app.prefs.current()).also { client = it }
					val resolver = context.contentResolver

					if (entry.isImage || entry.isVideo) {
						// 存入系统相册
						val collection = if (entry.isImage) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
							} else {
								MediaStore.Images.Media.EXTERNAL_CONTENT_URI
							}
						} else {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
							} else {
								MediaStore.Video.Media.EXTERNAL_CONTENT_URI
							}
						}

						val values = ContentValues().apply {
							put(MediaStore.MediaColumns.DISPLAY_NAME, entry.name)
							put(MediaStore.MediaColumns.MIME_TYPE, if (entry.isImage) "image/*" else "video/*")
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								put(MediaStore.MediaColumns.RELATIVE_PATH, if (entry.isImage) "Pictures/NasDrop" else "Movies/NasDrop")
							}
						}

						val uri = resolver.insert(collection, values)
							?: throw IllegalStateException("无法创建媒体存储记录")
						resolver.openOutputStream(uri)?.use { os ->
							c.download(entry.path, os) { _, _ -> }
						}
						withContext(Dispatchers.Main) { onResult(true, "已成功保存到手机相册") }
					} else {
						// 存入外部下载目录
						val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
						val subDir = File(dir, "NasDrop").apply { mkdirs() }
						val destFile = File(subDir, entry.name)
						FileOutputStream(destFile).use { fos ->
							c.download(entry.path, fos) { _, _ -> }
						}
						withContext(Dispatchers.Main) { onResult(true, "已下载到: Download/NasDrop/${entry.name}") }
					}
				} catch (t: Throwable) {
					withContext(Dispatchers.Main) { onResult(false, "下载失败: ${t.message}") }
				}
			}
		}
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
			scanGlobalMedia()
		}
	}

	fun imageAlbum(entry: DavEntry): Pair<List<DavEntry>, Int> {
		val pool = if (allPhotos.any { it.path == entry.path }) allPhotos else entries.filter { it.isImage }
		val i = pool.indexOfFirst { it.path == entry.path }
		return pool to i.coerceAtLeast(0)
	}

	private suspend fun pollLink() {
		while (true) {
			pollPeerStatus()
			delay(2500)
		}
	}

	private fun pollPeerStatus() {
		val embedded = TsBridge.available
		if (embedded) {
			app.repo.updateRouteInfo()
			val st = TsBridge.status()
			linkStatus = label(st)
			authUrl = if (st == "needs-login") TsBridge.authUrl() else ""
		} else {
			linkStatus = "直连模式"
			authUrl = ""
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

	private fun collectMediaFrom(items: List<DavEntry>) {
		val imgs = (allPhotos + items.filter { it.isImage }).distinctBy { it.path }
		val vids = (allVideos + items.filter { it.isVideo }).distinctBy { it.path }
		allPhotos = imgs
		allVideos = vids
	}

	private fun updateFolderPaths(items: List<DavEntry>) {
		val dirs = items.filter { it.isDir }.map { it.path }
		allFolderPaths = (allFolderPaths + dirs).distinct().sorted()
	}

	private suspend fun scanGlobalMedia() {
		withContext(Dispatchers.IO) {
			try {
				val s = settings ?: app.prefs.current().also { settings = it }
				val c = app.repo.client(s)
				val rootEntries = c.list("")
				collectMediaFrom(rootEntries)
				updateFolderPaths(rootEntries)
				for (dir in rootEntries.filter { it.isDir }) {
					runCatching {
						val sub = c.list(dir.path)
						dirCache[dir.path] = sub
						collectMediaFrom(sub)
						updateFolderPaths(sub)
					}
				}
			} catch (_: Exception) {}
		}
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
				scanGlobalMedia()
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

		fun groupEntriesForTimeline(list: List<DavEntry>): List<TimelineGroup> {
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
