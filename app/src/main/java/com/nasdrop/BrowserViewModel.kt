package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MainTab {
	Files,
	Photos,
	Media,
	Me,
}

enum class ViewMode {
	Cards,   // 夸克式极简纯净列表
	Compact, // 紧凑列表
	Grid,    // 2列宫格大图
}

enum class SortOrder(val label: String) {
	ModifiedDesc("按修改时间 ↓"),
	ModifiedAsc("按修改时间 ↑"),
	NameAsc("按名称 A-Z"),
	SizeDesc("按文件大小 ↓"),
}

data class TimelineGroup(
	val dateTitle: String,
	val count: Int,
	val items: List<DavEntry>,
)

enum class TransferStatus {
	QUEUED,
	RUNNING,
	PAUSED,
	COMPLETED,
	FAILED,
}

data class TransferTask(
	val id: String = UUID.randomUUID().toString(),
	val name: String,
	val path: String,
	val isUpload: Boolean = false,
	var totalBytes: Long = 0L,
	var sentBytes: Long = 0L,
	var speedBytesPerSec: Long = 0L,
	var status: TransferStatus = TransferStatus.QUEUED,
	var error: String? = null,
	var lastUpdated: Long = System.currentTimeMillis(),
) {
	val progress: Float
		get() = if (totalBytes > 0) (sentBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

	val speedStr: String
		get() {
			if (speedBytesPerSec <= 0) return "--"
			val mb = speedBytesPerSec / (1024.0 * 1024.0)
			return if (mb >= 1.0) String.format(Locale.ROOT, "%.1f MB/s", mb)
			else String.format(Locale.ROOT, "%.0f KB/s", speedBytesPerSec / 1024.0)
		}
}

class BrowserViewModel(private val app: NasDropApp) : ViewModel() {
	var currentTab by mutableStateOf(MainTab.Files)

	var path by mutableStateOf("")
		private set

	var entries by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	var loading by mutableStateOf(false)
		private set

	var error by mutableStateOf<String?>(null)
		private set

	var linkStatus by mutableStateOf("探测中...")
		private set

	var authUrl by mutableStateOf("")
		private set

	/** 文件筛选分类：全部 / 图片 / 视频 / 文档 / 压缩包 / 音频 */
	var selectedCategory by mutableStateOf(FileCategory.All)
		private set

	/** 视图模式：夸克式极简列表 vs 宫格大图 */
	var viewMode by mutableStateOf(ViewMode.Cards)
		private set

	/** 夸克同款排序方式：按修改时间 / 按名称 / 按大小 */
	var sortOrder by mutableStateOf(SortOrder.ModifiedDesc)
		private set

	/** 全局搜索关键词 */
	var searchQuery by mutableStateOf("")

	/** 全局聚合照片池（跨目录汇总所有照片，类似主流网盘相册） */
	var allPhotos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 全局聚合视频池（跨目录汇总所有视频，类似主流网盘影音） */
	var allVideos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	// ========== 相册与影音子分类浏览系统 ==========
	/** 当前选中的子相册路径："" 表示查看全部时光流，若为具体路径如 "Photos/云南自驾" 则浏览该子相册 */
	var selectedAlbumPath by mutableStateOf("")

	/** 当前选中的子影集路径："" 表示查看全部视频流，若为具体路径如 "Movies/漫威" 则浏览该子影集 */
	var selectedMediaPath by mutableStateOf("")

	/** 所有已发现的相册文件夹列表（含封面与子相册） */
	var allAlbumFolders by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 所有已发现的影音分类文件夹列表 */
	var allMediaFolders by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 当前所选子相册（或全部）照片集合 */
	val currentAlbumPhotos: List<DavEntry>
		get() {
			if (selectedAlbumPath.isBlank()) return allPhotos
			return allPhotos.filter { it.path.startsWith(selectedAlbumPath) }
		}

	/** 当前所选子影集（或全部）视频集合 */
	val currentMediaVideos: List<DavEntry>
		get() {
			if (selectedMediaPath.isBlank()) return allVideos
			return allVideos.filter { it.path.startsWith(selectedMediaPath) }
		}

	/** 当前已发现的所有文件夹相对路径集合（供文件移动对话框选择） */
	var allFolderPaths by mutableStateOf<List<String>>(listOf(""))
		private set

	val routeInfo: StateFlow<RouteInfo> = app.repo.routeInfo

	val currentVersionName: String
		get() = runCatching {
			val pInfo = app.packageManager.getPackageInfo(app.packageName, 0)
			pInfo.versionName ?: "0.1.0"
		}.getOrDefault("0.1.0")

	// ========== 版本推送与一键更新状态 ==========
	var hasUpdate by mutableStateOf(false)
		private set

	var latestUpdateInfo by mutableStateOf<UpdateInfo?>(null)
		private set

	var isDownloadingUpdate by mutableStateOf(false)
		private set

	var updateProgress by mutableStateOf(0f)
		private set

	var updateSpeedText by mutableStateOf("")
		private set

	var showUpdateDialog by mutableStateOf(false)

	// ========== 批量多选状态 ==========
	var isPunching by mutableStateOf(false)
		private set

	var isMultiSelectMode by mutableStateOf(false)
		private set

	var selectedEntries by mutableStateOf<Set<DavEntry>>(emptySet())
		private set

	val selectedPaths: Set<String>
		get() = selectedEntries.map { it.path }.toSet()

	// ========== 传输管理中心状态 ==========
	private var downloadTasks by mutableStateOf<List<TransferTask>>(emptyList())
	private var uploadTasks by mutableStateOf<List<TransferTask>>(emptyList())

	var transferTasks by mutableStateOf<List<TransferTask>>(emptyList())
		private set

	private fun syncTransferTasks() {
		viewModelScope.launch(Dispatchers.Main) {
			transferTasks = uploadTasks + downloadTasks
		}
	}

	val activeTransferCount: Int
		get() = transferTasks.count { it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED }

	val totalSpeedStr: String
		get() {
			val total = transferTasks.filter { it.status == TransferStatus.RUNNING }.sumOf { it.speedBytesPerSec }
			if (total <= 0) return "--"
			val mb = total / (1024.0 * 1024.0)
			return if (mb >= 1.0) String.format(Locale.ROOT, "%.1f MB/s", mb)
			else String.format(Locale.ROOT, "%.0f KB/s", total / 1024.0)
		}

	private var client: WebDavClient? = null
	private val dirCache = ConcurrentHashMap<String, List<DavEntry>>()
	private var transferJob: Job? = null

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

			val raw = if (searchQuery.isBlank()) baseList else baseList.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }

			// 夸克排序逻辑：文件夹恒定排在最上方（置顶），内部按用户所选排序
			return when (sortOrder) {
				SortOrder.ModifiedDesc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenByDescending { it.modified }
				)
				SortOrder.ModifiedAsc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenBy { it.modified }
				)
				SortOrder.NameAsc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenBy { it.name.lowercase(Locale.ROOT) }
				)
				SortOrder.SizeDesc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenComparator { a, b ->
							if (a.isDir && b.isDir) a.name.compareTo(b.name, ignoreCase = true)
							else b.size.compareTo(a.size)
						}
				)
			}
		}

	/** 相册时间线分组数据（按拍摄/修改日期降序） */
	val currentTabEntries: List<DavEntry>
		get() = when (currentTab) {
			MainTab.Files -> filteredEntries
			MainTab.Photos -> currentAlbumPhotos
			MainTab.Media -> currentMediaVideos
			MainTab.Me -> emptyList()
		}

	val photoTimelineGroups: List<TimelineGroup>
		get() = groupEntriesForTimeline(currentAlbumPhotos)

	init {
		viewModelScope.launch { open("") }
		viewModelScope.launch { pollLink() }
		viewModelScope.launch { startAutoHealing() }
		cleanupOldCache()
		startTransferWorker()

		// 响应式自愈监听：当网络链路从离线到就绪、或者从中继成功打洞为直连时，
		// 若当前列表仍为空或此前超时报错，立刻自动触发刷新拉取，绝不给用户看空白列表！
		viewModelScope.launch {
			var lastDirect = false
			routeInfo.collect { info ->
				val isNowDirect = info.isDirect
				val isReady = info.isDirect || info.isRelay
				if (isReady) {
					if (entries.isEmpty() || (!lastDirect && isNowDirect)) {
						open(path)
					}
				}
				lastDirect = isNowDirect
			}
		}

		// 低优先级延迟全盘媒体扫描（在首屏数据加载 3 秒后启动，严禁在冷启动首秒挤占中继通道）
		viewModelScope.launch {
			kotlinx.coroutines.delay(3000)
			scanGlobalMedia()
		}
		viewModelScope.launch {
			val lastSentMap = mutableMapOf<String, Long>()
			val lastTimeMap = mutableMapOf<String, Long>()
			Uploads.tasks.collect { list ->
				val now = System.currentTimeMillis()
				uploadTasks = list.map { up ->
					val prevSent = lastSentMap[up.uri] ?: 0L
					val prevTime = lastTimeMap[up.uri] ?: now
					val dt = (now - prevTime).coerceAtLeast(1)
					val dSent = (up.sent - prevSent).coerceAtLeast(0)
					val speed = if (up.done || up.error != null) 0L else (dSent * 1000 / dt)
					lastSentMap[up.uri] = up.sent
					lastTimeMap[up.uri] = now

					val status = when {
						up.done && up.error == null -> TransferStatus.COMPLETED
						up.error != null -> TransferStatus.FAILED
						up.sent > 0 -> TransferStatus.RUNNING
						else -> TransferStatus.QUEUED
					}
					TransferTask(
						id = "upload_${up.uri.hashCode()}",
						name = up.name,
						path = up.uri,
						isUpload = true,
						totalBytes = up.total,
						sentBytes = up.sent,
						speedBytesPerSec = speed,
						status = status,
						error = up.error,
					)
				}
				syncTransferTasks()
			}
		}
		viewModelScope.launch {
			Uploads.uploadCompletedEvent.collect {
				refresh()
			}
		}
		viewModelScope.launch {
			checkForUpdates(silent = true)
		}
	}

	fun urlFor(entryPath: String): String? = client?.urlFor(entryPath)?.toString()

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun selectCategory(cat: FileCategory) {
		selectedCategory = cat
	}

	fun toggleViewMode() {
		viewMode = if (viewMode == ViewMode.Cards) ViewMode.Grid else ViewMode.Cards
	}

	fun cycleViewMode() {
		toggleViewMode()
	}

	fun cycleSortOrder() {
		sortOrder = when (sortOrder) {
			SortOrder.ModifiedDesc -> SortOrder.ModifiedAsc
			SortOrder.ModifiedAsc -> SortOrder.NameAsc
			SortOrder.NameAsc -> SortOrder.SizeDesc
			SortOrder.SizeDesc -> SortOrder.ModifiedDesc
		}
	}

	/**
	 * 慢速自适应智能自愈：若当前处于 DERP 中继慢速代理，后台每 15 秒静默发起探测
	 */
	
	/** 自动清理 1 天前的旧 APK 安装包与临时碎片，杜绝缓存积压无节制增大 */
	private fun cleanupOldCache() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				val updateDir = File(app.cacheDir, "updates")
				if (updateDir.exists()) {
					val now = System.currentTimeMillis()
					updateDir.listFiles()?.forEach { f ->
						if (now - f.lastModified() > 24 * 3600 * 1000L) {
							f.delete()
						}
					}
				}
			}
		}
	}

	private suspend fun startAutoHealing() {
		while (true) {
			delay(15_000)
			try {
				val info = routeInfo.value
				if (info.isRelay) {
					app.repo.triggerHolePunch()
				}
			} catch (_: Exception) {}
		}
	}

	fun imageAlbum(entry: DavEntry): Pair<List<DavEntry>, Int> {
		val list = if (currentTab == MainTab.Photos) currentAlbumPhotos else entries.filter { !it.isDir && it.isImage }
		val idx = list.indexOfFirst { it.path == entry.path }.coerceAtLeast(0)
		return Pair(if (list.isEmpty()) listOf(entry) else list, idx)
	}

	/** 手动触发加速直连，返回打洞结果并在成功后自动刷新 */
	fun punchHole(onResult: (String) -> Unit = {}) {
		if (isPunching) return
		isPunching = true
		viewModelScope.launch {
			val msg = withContext(Dispatchers.IO) {
				app.repo.triggerHolePunch()
			}
			isPunching = false
			onResult(msg)
			// 打洞完成后若列表为空，或者成功打洞，立刻自动刷新当前列表
			if (entries.isEmpty() || msg.contains("直连") || msg.contains("打洞成功")) {
				refresh()
			}
		}
	}

	// ==================== 批量多选控制 ====================
	fun enterMultiSelect(initial: DavEntry? = null) {
		isMultiSelectMode = true
		selectedEntries = if (initial != null) setOf(initial) else emptySet()
	}

	fun exitMultiSelect() {
		isMultiSelectMode = false
		selectedEntries = emptySet()
	}

	fun toggleSelect(entry: DavEntry) {
		val newSet = selectedEntries.toMutableSet()
		if (newSet.contains(entry)) {
			newSet.remove(entry)
		} else {
			newSet.add(entry)
		}
		selectedEntries = newSet
		if (newSet.isEmpty() && isMultiSelectMode) {
			// 仍然保持多选模式，等待用户继续选择
		}
	}

	fun selectAll(currentItems: List<DavEntry>) {
		selectedEntries = currentItems.toSet()
	}

	fun batchDelete(onDone: () -> Unit = {}) {
		val targets = selectedEntries.toList()
		if (targets.isEmpty()) return
		mutate { c ->
			for (item in targets) {
				c.delete(item.path, item.isDir)
			}
		}
		exitMultiSelect()
		onDone()
	}

	fun batchMove(targetDir: String, onDone: () -> Unit = {}) {
		val targets = selectedEntries.toList()
		if (targets.isEmpty()) return
		mutate { c ->
			for (item in targets) {
				val dest = join(targetDir, item.name)
				c.move(item.path, dest)
			}
		}
		exitMultiSelect()
		onDone()
	}

	fun batchDownload(context: Context, onDone: () -> Unit = {}) {
		val targets = selectedEntries.toList().filter { !it.isDir }
		for (entry in targets) {
			enqueueDownload(context, entry)
		}
		exitMultiSelect()
		onDone()
	}

	// ==================== 并发传输队列管理 ====================
	fun enqueueDownload(context: Context, entry: DavEntry) {
		// 避免重复入队
		if (downloadTasks.any { it.path == entry.path && it.status != TransferStatus.FAILED }) return
		val task = TransferTask(
			name = entry.name,
			path = entry.path,
			isUpload = false,
			totalBytes = entry.size,
			status = TransferStatus.QUEUED,
		)
		downloadTasks = downloadTasks + task
		syncTransferTasks()
		// 每次发起传输时，若当前不是直连，顺便触发一次打洞自愈
		punchHole()
	}

	fun pauseTask(taskId: String) {
		downloadTasks = downloadTasks.map {
			if (it.id == taskId && (it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED)) {
				it.copy(status = TransferStatus.PAUSED, speedBytesPerSec = 0)
			} else it
		}
		syncTransferTasks()
	}

	fun resumeTask(taskId: String) {
		downloadTasks = downloadTasks.map {
			if (it.id == taskId && it.status == TransferStatus.PAUSED) {
				it.copy(status = TransferStatus.QUEUED)
			} else it
		}
		syncTransferTasks()
	}

	fun cancelTask(taskId: String) {
		if (taskId.startsWith("upload_")) {
			uploadTasks = uploadTasks.filterNot { it.id == taskId }
			if (uploadTasks.isEmpty()) {
				Uploads.clear()
			}
		} else {
			downloadTasks = downloadTasks.filterNot { it.id == taskId }
		}
		syncTransferTasks()
	}

	fun pauseAll() {
		downloadTasks = downloadTasks.map {
			if (it.status == TransferStatus.RUNNING || it.status == TransferStatus.QUEUED) {
				it.copy(status = TransferStatus.PAUSED, speedBytesPerSec = 0)
			} else it
		}
		syncTransferTasks()
	}

	fun resumeAll() {
		downloadTasks = downloadTasks.map {
			if (it.status == TransferStatus.PAUSED) {
				it.copy(status = TransferStatus.QUEUED)
			} else it
		}
		syncTransferTasks()
	}

	private fun startTransferWorker() {
		transferJob?.cancel()
		transferJob = viewModelScope.launch(Dispatchers.IO) {
			while (isActive) {
				val nextTask = downloadTasks.firstOrNull { it.status == TransferStatus.QUEUED }
				if (nextTask == null) {
					delay(1000)
					continue
				}

				// 将状态标记为 RUNNING
				updateTask(nextTask.id) { it.status = TransferStatus.RUNNING }

				try {
					val c = client ?: app.repo.client(app.prefs.current()).also { client = it }
					val resolver = app.contentResolver

					val isMedia = nextTask.name.endsWith(".jpg", true) ||
						nextTask.name.endsWith(".png", true) ||
						nextTask.name.endsWith(".mp4", true) ||
						nextTask.name.endsWith(".mov", true)

					var lastBytes = 0L
					var lastTime = System.currentTimeMillis()

					if (isMedia) {
						val isImg = nextTask.name.endsWith(".jpg", true) || nextTask.name.endsWith(".png", true)
						val collection = if (isImg) {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
							else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
						} else {
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
							else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
						}

						val values = ContentValues().apply {
							put(MediaStore.MediaColumns.DISPLAY_NAME, nextTask.name)
							put(MediaStore.MediaColumns.MIME_TYPE, if (isImg) "image/*" else "video/*")
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								put(MediaStore.MediaColumns.RELATIVE_PATH, if (isImg) "Pictures/NasDrop" else "Movies/NasDrop")
							}
						}

						val uri = resolver.insert(collection, values) ?: throw IllegalStateException("无法创建媒体存储")
						resolver.openOutputStream(uri)?.use { os ->
							c.download(nextTask.path, os) { sent, total ->
								val now = System.currentTimeMillis()
								val dt = now - lastTime
								var speed = 0L
								if (dt >= 500) {
									speed = ((sent - lastBytes) * 1000) / dt
									lastBytes = sent
									lastTime = now
								}
								updateTask(nextTask.id) {
									it.sentBytes = sent
									if (total > 0) it.totalBytes = total
									if (speed > 0) it.speedBytesPerSec = speed
								}
							}
						}
					} else {
						val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
						val subDir = File(dir, "NasDrop").apply { mkdirs() }
						val destFile = File(subDir, nextTask.name)
						FileOutputStream(destFile).use { fos ->
							c.download(nextTask.path, fos) { sent, total ->
								val now = System.currentTimeMillis()
								val dt = now - lastTime
								var speed = 0L
								if (dt >= 500) {
									speed = ((sent - lastBytes) * 1000) / dt
									lastBytes = sent
									lastTime = now
								}
								updateTask(nextTask.id) {
									it.sentBytes = sent
									if (total > 0) it.totalBytes = total
									if (speed > 0) it.speedBytesPerSec = speed
								}
							}
						}
					}

					updateTask(nextTask.id) {
						it.status = TransferStatus.COMPLETED
						it.sentBytes = it.totalBytes
						it.speedBytesPerSec = 0
					}
				} catch (t: Throwable) {
					updateTask(nextTask.id) {
						it.status = TransferStatus.FAILED
						it.error = t.message
						it.speedBytesPerSec = 0
					}
				}
				delay(200)
			}
		}
	}

	private fun updateTask(taskId: String, block: (TransferTask) -> Unit) {
		downloadTasks = downloadTasks.map {
			if (it.id == taskId) {
				val copy = it.copy()
				block(copy)
				copy
			} else it
		}
		syncTransferTasks()
	}

	// ==================== 浏览与目录 ====================
	fun open(target: String) {
		val cached = dirCache[target]
		if (cached != null) {
			entries = cached
			path = target
			loading = false
			error = null
		} else {
			loading = true
		}

		viewModelScope.launch {
			try {
				val s = app.prefs.current()
				val c = app.repo.client(s)
				client = c
				val fresh = withContext(Dispatchers.IO) { c.list(target) }
				dirCache[target] = fresh
				error = null
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
	}

	fun cd(entry: DavEntry) {
		if (entry.isDir) open(entry.path)
	}

	fun up(): Boolean {
		if (isRoot) return false
		open(parentOf(path))
		return true
	}

	/** 新建普通子文件夹 */
	fun mkdir(name: String, isAlbum: Boolean = false) {
		val finalName = if (isAlbum && !name.contains("相册")) "$name 相册" else name
		mutate { it.mkcol(join(path, finalName)) }
	}

	fun openAlbum(albumPath: String) {
		selectedAlbumPath = albumPath
	}

	fun upAlbum() {
		if (selectedAlbumPath.isBlank()) return
		val parent = selectedAlbumPath.substringBeforeLast('/', "")
		selectedAlbumPath = if (parent == "Photos" || parent == "相册") "" else parent
	}

	fun openMedia(mediaPath: String) {
		selectedMediaPath = mediaPath
	}

	fun upMedia() {
		if (selectedMediaPath.isBlank()) return
		val parent = selectedMediaPath.substringBeforeLast('/', "")
		selectedMediaPath = if (parent == "Movies" || parent == "影视" || parent == "影音") "" else parent
	}

	/** 在相册专属目录下新建相册文件夹 */
	fun createAlbum(name: String) {
		val targetDir = if (selectedAlbumPath.isNotBlank()) selectedAlbumPath else "Photos"
		mutate { client ->
			if (targetDir == "Photos") {
				runCatching { client.mkcol("Photos") }
			}
			client.mkcol(join(targetDir, name))
		}
	}

	/** 在影音专属目录下新建影集/分类文件夹 */
	fun createMediaFolder(name: String) {
		val targetDir = if (selectedMediaPath.isNotBlank()) selectedMediaPath else "Movies"
		mutate { client ->
			if (targetDir == "Movies") {
				runCatching { client.mkcol("Movies") }
			}
			client.mkcol(join(targetDir, name))
		}
	}

	fun deleteAlbum(folder: DavEntry) {
		mutate { client ->
			client.delete(folder.path, isDir = true)
			if (selectedAlbumPath == folder.path || selectedAlbumPath.startsWith(folder.path)) {
				selectedAlbumPath = ""
			}
		}
	}

	fun deleteMediaFolder(folder: DavEntry) {
		mutate { client ->
			client.delete(folder.path, isDir = true)
			if (selectedMediaPath == folder.path || selectedMediaPath.startsWith(folder.path)) {
				selectedMediaPath = ""
			}
		}
	}

	fun delete(entry: DavEntry) = mutate { it.delete(entry.path, entry.isDir) }

	fun rename(entry: DavEntry, newName: String) = mutate {
		it.move(entry.path, join(entry.path.substringBeforeLast('/', ""), newName))
	}

	fun move(entry: DavEntry, targetDir: String) = mutate {
		val dest = join(targetDir, entry.name)
		it.move(entry.path, dest)
	}

	fun rememberDir(dir: String) {
		viewModelScope.launch { app.prefs.setLastDir(dir) }
	}

	fun reloadSettings() {
		viewModelScope.launch {
			dirCache.clear()
			app.repo.invalidate()
			open(path)
			scanGlobalMedia()
		}
	}

	private fun mutate(action: suspend (WebDavClient) -> Unit) {
		viewModelScope.launch {
			try {
				val s = app.prefs.current()
				val c = client ?: app.repo.client(s).also { client = it }
				withContext(Dispatchers.IO) { action(c) }
				dirCache.clear()
				open(path)
				scanGlobalMedia()
			} catch (t: Throwable) {
				error = t.message ?: t.javaClass.simpleName
			}
		}
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
		st == "needs-login" -> "Tailscale 等待登录"
		st == "stopped" -> "Tailscale 已停止"
		st == "starting" -> "Tailscale 启动中…"
		st.startsWith("error") -> "Tailscale 异常: " + st.removePrefix("error:").trim()
		else -> st
	}

	private fun collectMediaFrom(items: List<DavEntry>) {
		val imgs = (allPhotos + items.filter { !it.isDir && it.isImage }).distinctBy { it.path }
		val vids = (allVideos + items.filter { !it.isDir && it.isVideo }).distinctBy { it.path }
		allPhotos = imgs
		allVideos = vids
	}

	private fun updateFolderPaths(items: List<DavEntry>) {
		val dirs = items.filter { it.isDir }.map { it.path }
		allFolderPaths = (allFolderPaths + dirs).distinct().sorted()
	}

	private val scanSemaphore = Semaphore(2)

	private suspend fun scanGlobalMedia() {
		withContext(Dispatchers.IO) {
			try {
				val s = app.prefs.current()
				val c = app.repo.client(s)
				val rootEntries = scanSemaphore.withPermit { c.list("") }
				collectMediaFrom(rootEntries)
				updateFolderPaths(rootEntries)

				val albums = mutableListOf<DavEntry>()
				val mediaAlbums = mutableListOf<DavEntry>()

				for (dir in rootEntries.filter { it.isDir }) {
					if (loading) delay(200)
					runCatching {
						val sub = scanSemaphore.withPermit { c.list(dir.path) }
						dirCache[dir.path] = sub
						collectMediaFrom(sub)
						updateFolderPaths(sub)

						if (dir.name.equals("Photos", ignoreCase = true) || dir.name == "相册") {
							albums.addAll(sub.filter { it.isDir })
							for (subDir in sub.filter { it.isDir }) {
								if (loading) delay(200)
								runCatching {
									val subSub = scanSemaphore.withPermit { c.list(subDir.path) }
									dirCache[subDir.path] = subSub
									collectMediaFrom(subSub)
									updateFolderPaths(subSub)
								}
							}
						} else if (dir.name.equals("Movies", ignoreCase = true) || dir.name == "影视" || dir.name == "影音") {
							mediaAlbums.addAll(sub.filter { it.isDir })
							for (subDir in sub.filter { it.isDir }) {
								if (loading) delay(200)
								runCatching {
									val subSub = scanSemaphore.withPermit { c.list(subDir.path) }
									dirCache[subDir.path] = subSub
									collectMediaFrom(subSub)
									updateFolderPaths(subSub)
								}
							}
						} else {
							if (sub.any { it.isImage }) albums.add(dir)
							if (sub.any { it.isVideo }) mediaAlbums.add(dir)
						}
					}
				}
				allAlbumFolders = albums.distinctBy { it.path }
				allMediaFolders = mediaAlbums.distinctBy { it.path }
			} catch (_: Exception) {}
		}
	}

	private fun groupEntriesForTimeline(items: List<DavEntry>): List<TimelineGroup> {
		if (items.isEmpty()) return emptyList()
		val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
		val todayStr = sdf.format(Date())

		val cal = Calendar.getInstance()
		cal.add(Calendar.DAY_OF_YEAR, -1)
		val yesterdayStr = sdf.format(cal.time)

		val groupsMap = items.sortedByDescending { it.modified }.groupBy {
			if (it.modified > 0) sdf.format(Date(it.modified)) else "其他"
		}

		return groupsMap.map { (dateKey, list) ->
			val title = when (dateKey) {
				todayStr -> "今天"
				yesterdayStr -> "昨天"
				"其他" -> "更早之前"
				else -> dateKey
			}
			TimelineGroup(dateTitle = title, count = list.size, items = list)
		}
	}

	private fun parentOf(p: String): String {
		val trimmed = p.trim('/')
		val idx = trimmed.lastIndexOf('/')
		return if (idx <= 0) "" else trimmed.substring(0, idx)
	}

	private fun join(a: String, b: String): String {
		val cleanA = a.trim('/')
		val cleanB = b.trim('/')
		return if (cleanA.isEmpty()) cleanB else "$cleanA/$cleanB"
	}

	// ==================== 版本更新检测与一键下载安装 ====================
	fun checkForUpdates(silent: Boolean = true, onChecked: ((Boolean) -> Unit)? = null) {
		viewModelScope.launch {
			val info = UpdateManager.checkUpdate(currentVersionName)
			if (info != null) {
				latestUpdateInfo = info
				hasUpdate = true
				if (!silent) {
					showUpdateDialog = true
				}
				onChecked?.invoke(true)
			} else {
				hasUpdate = false
				onChecked?.invoke(false)
			}
		}
	}

	fun openUpdateDialog() {
		if (latestUpdateInfo != null) {
			showUpdateDialog = true
		}
	}

	fun dismissUpdateDialog() {
		showUpdateDialog = false
	}

	fun startDownloadUpdate(context: Context) {
		val info = latestUpdateInfo ?: return
		if (isDownloadingUpdate) return
		isDownloadingUpdate = true
		updateProgress = 0f
		updateSpeedText = ""

		viewModelScope.launch {
			runCatching {
				val file = UpdateManager.downloadApk(context, info.apkUrl) { sent, total, speed ->
					if (total > 0) {
						updateProgress = (sent.toFloat() / total).coerceIn(0f, 1f)
					}
					val mb = speed / (1024.0 * 1024.0)
					updateSpeedText = if (mb >= 1.0) String.format(Locale.ROOT, "%.1f MB/s", mb) else String.format(Locale.ROOT, "%.0f KB/s", speed / 1024.0)
				}
				updateProgress = 1f
				isDownloadingUpdate = false
				UpdateManager.installApk(context, file)
			}.onFailure {
				isDownloadingUpdate = false
				Toast.makeText(context, "下载更新失败: ${it.message}", Toast.LENGTH_SHORT).show()
			}
		}
	}
}
