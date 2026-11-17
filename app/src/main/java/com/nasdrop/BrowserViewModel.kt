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

	var isRefreshing by mutableStateOf(false)
		private set

	var folderIcons by mutableStateOf<Map<String, String>>(emptyMap())
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

	private val workerDispatcher = Dispatchers.IO.limitedParallelism(4)
	private val scanDispatcher = Dispatchers.IO.limitedParallelism(1)
	private val downloadSemaphore = Semaphore(3)
	private var hasScannedMedia = false

	val totalSpeedBps: Long
		get() = downloadTasks.filter { it.status == TransferStatus.RUNNING }.sumOf { it.speedBytesPerSec } +
				uploadTasks.filter { it.status == TransferStatus.RUNNING }.sumOf { it.speedBytesPerSec }

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

	@Volatile private var client: WebDavClient? = null
	private val dirCache = ConcurrentHashMap<String, List<DavEntry>>()
	private var transferJob: Job? = null

	/**
	 * 缩略图版本号：异步补齐缩略图直链后自增，触发使用 thumbnailUrlFor 的 Composable 重组。
	 * thumbUrls 本身只是 WebDavClient 内的普通 Map，Compose 无法观察其变化——
	 * 没有这个版本号，就会出现"缩略图明明加载好了列表却不刷新"的假死现象。
	 */
	private var thumbRevision by mutableStateOf(0)
		private set

	/**
	 * 取 client 并挂上缩略图就绪回调（每个实例只挂一次）。
	 * 回调在后台线程触发；mutableStateOf 的写线程安全，Compose 会自动安排重组。
	 */
	private fun clientOf(s: Settings): WebDavClient {
		val c = client ?: app.repo.client(s).also { client = it }
		if (c.onThumbnailsLoaded == null) {
			c.onThumbnailsLoaded = { thumbRevision++ }
		}
		return c
	}

	/**
	 * 后台异步补齐某目录的缩略图直链。列目录先返回、UI 先渲染，缩略图随后流入，
	 * 两条链路解耦——慢速隧道里不再互相拖累。
	 */
	private fun prefetchThumbsAsync(path: String, items: List<DavEntry>) {
		viewModelScope.launch(workerDispatcher) {
			val s = app.prefs.current()
			clientOf(s).prefetchThumbnails(path, items)
		}
	}

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
		loadUserPreferences()
		viewModelScope.launch(workerDispatcher) { open("") }
		startUnifiedLinkMonitor()
		cleanupOldCache()
		startTransferWorker()

		viewModelScope.launch(workerDispatcher) {
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

		viewModelScope.launch(workerDispatcher) {
			Uploads.uploadCompletedEvent.collect {
				// 只刷新当前目录：新文件立即可见。
				// 绝不再 scanGlobalMedia()——它会串行列几十个目录，在慢速隧道里要几十秒，
				// 表现为"上传完一直在云端校验"（其实是客户端在全量重扫，不是服务端慢）。
				dirCache.remove(path)
				refreshSilent()
				// 媒体池标记脏：下次真正切到相册/影音页时才增量重扫，把开销挪出上传链路。
				if (hasScannedMedia) mediaDirty = true
			}
		}
		viewModelScope.launch(workerDispatcher) {
			checkForUpdates(silent = true)
		}
	}

	/** 媒体池是否需要因上传而重扫（惰性，切页面时才消费） */
	@Volatile private var mediaDirty = false

	/** 惰性按需媒体扫描：用户切换到相册或影音页面时才触发，首屏启动完全零负载 */
	fun ensureMediaScanned(force: Boolean = false) {
		if (hasScannedMedia && !force && !mediaDirty) return
		val needFull = !hasScannedMedia || force || mediaDirty
		hasScannedMedia = true
		mediaDirty = false
		if (needFull) {
			viewModelScope.launch(scanDispatcher) {
				scanGlobalMedia()
			}
		}
	}

	/**
	 * 从后台回到前台时调用：若 tsnet 隧道已断，用持久化状态原地重连并即时刷新。
	 * 报告 true 表示确实重连过（供 UI 决定是否提示）。
	 */
	fun reconnectIfNeeded(onReconnected: (Boolean) -> Unit = {}) {
		viewModelScope.launch(workerDispatcher) {
			val s = app.prefs.current()
			val reconnected = app.repo.reconnectTunnelIfNeeded(s)
			if (reconnected) {
				// 端口变了，VM 手里的旧 client 指向死端口，必须丢弃
				client = null
				dirCache.clear()
				refreshSilent()
			}
			withContext(Dispatchers.Main) { onReconnected(reconnected) }
		}
	}

	/** 统一整合链路监听、自愈与激进直连打洞 */
	private fun startUnifiedLinkMonitor() {
		viewModelScope.launch(workerDispatcher) {
			var lastDirect = false
			// 从现在开始计时，避免冷启动时立刻触发一次自愈检查（隧道尚在 starting）
			var lastReconnectCheck = System.currentTimeMillis()
			while (isActive) {
				pollPeerStatus()
				val info = app.repo.routeInfo.value
				val isNowDirect = info.isDirect
				val isReady = info.isDirect || info.isRelay

				if (isReady) {
					if (entries.isEmpty() || (!lastDirect && isNowDirect)) {
						open(path)
					}
				} else if (TsBridge.available) {
					// 后台隧道掉线自愈：每 ~12s 检查一次，失败不打扰用户，
					// 连上后自动重列目录，用户回到前台看到的就是最新数据。
					val now = System.currentTimeMillis()
					if (now - lastReconnectCheck > 12_000L) {
						lastReconnectCheck = now
						val s = app.prefs.current()
						if (app.repo.reconnectTunnelIfNeeded(s)) {
							client = null
							dirCache.clear()
							refreshSilent()
						}
					}
				}
				lastDirect = isNowDirect

				// 如果当前仍是低速 DERP 中继且没有在打洞，后台自动主动触发打洞探测尽快切入 P2P 直连
				if (info.isRelay && !isPunching) {
					withContext(Dispatchers.IO) {
						app.repo.triggerHolePunch()
					}
				}

				delay(4000)
			}
		}
	}

	fun urlFor(entryPath: String): String? = client?.urlFor(entryPath)?.toString()

	/**
	 * 查看器原图/视频专用：OpenList 原生 /p/?sign= 直链（无 Basic Auth、标准 GET，
	 * 缓存与 Range 命中率更高）。读取 thumbRevision 建立快照依赖，sign 预取到后自动刷新。
	 * 非 OpenList 或尚未预取到 sign 时回退 WebDAV urlFor，绝不返回 null。
	 */
	fun directUrlFor(entryPath: String): String? {
		@Suppress("UNUSED_VARIABLE") val revision = thumbRevision
		return client?.directUrlFor(entryPath)?.toString() ?: urlFor(entryPath)
	}

	/** 列表缩略图专用：命中 OpenList 服务端缩略图时返回真图片直链，否则回退原图地址。
	 *  读取 thumbRevision 建立 Compose 快照依赖，异步缩略图就绪后自动重组刷新。 */
	fun thumbnailUrlFor(entryPath: String): String? {
		@Suppress("UNUSED_VARIABLE") val revision = thumbRevision
		return client?.thumbnailUrl(entryPath)?.toString()
	}

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun selectCategory(cat: FileCategory) {
		selectedCategory = cat
	}

	private fun loadUserPreferences() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				val savedView = app.prefs.getViewMode()
				if (savedView != null) {
					val vm = ViewMode.valueOf(savedView)
					withContext(Dispatchers.Main) { viewMode = vm }
				}
			}
			runCatching {
				val savedSort = app.prefs.getSortOrder()
				if (savedSort != null) {
					val so = SortOrder.valueOf(savedSort)
					withContext(Dispatchers.Main) { sortOrder = so }
				}
			}
			runCatching {
				val icons = NasDb.getInstance(app).getAllFolderIcons()
				withContext(Dispatchers.Main) {
					folderIcons = icons
				}
			}
		}
	}

	fun toggleViewMode() {
		viewMode = if (viewMode == ViewMode.Cards) ViewMode.Grid else ViewMode.Cards
		viewModelScope.launch { app.prefs.setViewMode(viewMode.name) }
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
		viewModelScope.launch { app.prefs.setSortOrder(sortOrder.name) }
	}

	/**
	 * 将远端原图保存到本地系统相册 (Pictures/NasDrop) 并通知系统刷新
	 */
	fun saveImageToGallery(entry: DavEntry, onResult: (Boolean, String) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val cli = client ?: return@launch withContext(Dispatchers.Main) {
					onResult(false, "未连接服务器")
				}
				val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
					android.os.Environment.DIRECTORY_PICTURES
				).resolve("NasDrop")
				if (!picturesDir.exists()) picturesDir.mkdirs()

				val destFile = picturesDir.resolve(entry.name)
				destFile.outputStream().use { fos ->
					cli.download(entry.path, fos) { _, _ -> }
				}
				if (destFile.exists() && destFile.length() > 0) {
					android.media.MediaScannerConnection.scanFile(
						app,
						arrayOf(destFile.absolutePath),
						null,
					) { _, _ -> }
					withContext(Dispatchers.Main) {
						onResult(true, "已保存至相册 Pictures/NasDrop")
					}
				} else {
					withContext(Dispatchers.Main) { onResult(false, "下载失败") }
				}
			} catch (e: Exception) {
				withContext(Dispatchers.Main) { onResult(false, "保存失败：${e.message}") }
			}
		}
	}

	/**
	 * 将图片下载至共享临时缓存，准备进行系统微信/QQ等第三方分享
	 */
	fun prepareShareImage(entry: DavEntry, onResult: (java.io.File?) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val cli = client ?: return@launch withContext(Dispatchers.Main) { onResult(null) }
				val shareDir = app.cacheDir.resolve("share")
				if (!shareDir.exists()) shareDir.mkdirs()
				val shareFile = shareDir.resolve(entry.name)
				if (!shareFile.exists() || shareFile.length() != entry.size) {
					shareFile.outputStream().use { fos ->
						cli.download(entry.path, fos) { _, _ -> }
					}
				}
				withContext(Dispatchers.Main) { onResult(shareFile) }
			} catch (_: Exception) {
				withContext(Dispatchers.Main) { onResult(null) }
			}
		}
	}

	/**
	 * 慢速自适应智能自愈：若当前处于 DERP 中继慢速代理，后台每 15 秒静默发起探测
	 */
	
	/** 启动时彻底清空旧 APK 安装包与临时碎片，杜绝缓存积压无节制增大 */
	private fun cleanupOldCache() {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				val updateDir = File(app.cacheDir, "updates")
				if (updateDir.exists()) {
					updateDir.deleteRecursively()
				}
			}
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

	fun batchDelete(onDone: (Int, Int) -> Unit = { _, _ -> }) {
		val targets = selectedEntries.toList()
		if (targets.isEmpty()) return
		val targetPaths = targets.map { it.path }.toSet()

		// 乐观即时移除，秒级清爽
		entries = entries.filterNot { it.path in targetPaths }
		allPhotos = allPhotos.filterNot { it.path in targetPaths }
		allVideos = allVideos.filterNot { it.path in targetPaths }
		exitMultiSelect()

		viewModelScope.launch {
			var okCount = 0
			var failCount = 0
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = client ?: app.repo.client(s).also { client = it }
				for (item in targets) {
					try {
						c.delete(item.path, item.isDir)
						okCount++
					} catch (_: Throwable) {
						failCount++
					}
				}
			}
			dirCache.clear()
			onDone(okCount, failCount)
			refresh()
		}
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
		transferJob = viewModelScope.launch(workerDispatcher) {
			while (isActive) {
				val queued = downloadTasks.filter { it.status == TransferStatus.QUEUED }
				for (task in queued) {
					if (!isActive) break
					updateTask(task.id) { it.status = TransferStatus.RUNNING }
					launch(workerDispatcher) {
						downloadSemaphore.withPermit {
							runDownloadTask(task)
						}
					}
				}
				delay(600)
			}
		}
	}

	private suspend fun runDownloadTask(task: TransferTask) {
		try {
			val c = client ?: app.repo.client(app.prefs.current()).also { client = it }
			val isMedia = task.name.endsWith(".jpg", true) ||
				task.name.endsWith(".png", true) ||
				task.name.endsWith(".mp4", true) ||
				task.name.endsWith(".mov", true)

			val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
			val subDir = File(dir, "NasDrop").apply { mkdirs() }
			val partFile = File(subDir, "${task.name}.part")
			val destFile = File(subDir, task.name)

			// 断点续传：若存在未完成的 .part 文件，从当前已有字节接续请求
			val startByte = if (partFile.exists()) partFile.length() else 0L
			var lastBytes = startByte
			var lastTime = System.currentTimeMillis()

			FileOutputStream(partFile, true).use { fos ->
				c.download(task.path, fos, startByte = startByte) { sent, total ->
					val now = System.currentTimeMillis()
					val dt = now - lastTime
					var speed = 0L
					if (dt >= 500) {
						speed = ((sent - lastBytes) * 1000) / dt
						lastBytes = sent
						lastTime = now
					}
					updateTask(task.id) {
						it.sentBytes = sent
						if (total > 0) it.totalBytes = total
						if (speed > 0) it.speedBytesPerSec = speed
					}
				}
			}

			// 下载完成，原子重命名为目标文件
			if (partFile.exists()) {
				if (destFile.exists()) destFile.delete()
				partFile.renameTo(destFile)
			}

			if (isMedia) {
				runCatching {
					android.media.MediaScannerConnection.scanFile(
						app,
						arrayOf(destFile.absolutePath),
						null,
						null,
					)
				}
			}

			updateTask(task.id) {
				it.status = TransferStatus.COMPLETED
				it.sentBytes = it.totalBytes
				it.speedBytesPerSec = 0
			}
		} catch (t: Throwable) {
			updateTask(task.id) {
				it.status = TransferStatus.FAILED
				it.error = t.message
				it.speedBytesPerSec = 0
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
			// 只有在当前没有任何数据展示时，才展示居中 loading 菊花
			if (entries.isEmpty()) {
				loading = true
			}
		}

		viewModelScope.launch {
			try {
				val s = app.prefs.current()
				val fresh = withContext(Dispatchers.IO) {
					clientOf(s).list(target)
				}
				dirCache[target] = fresh
				error = null
				if (path == target || cached == null) {
					entries = fresh
					path = target
				}
				collectMediaFrom(fresh)
				updateFolderPaths(fresh)
				// 列目录已呈现，缩略图放到后台慢慢补，绝不阻塞列表
				prefetchThumbsAsync(target, fresh)
			} catch (t: Throwable) {
				if (cached == null) {
					error = t.message ?: t.javaClass.simpleName
				}
			} finally {
				loading = false
			}
		}
	}

	/**
	 * 下拉刷新专用：带指示器状态，真正挂起等待网络拉取完成，
	 * 并保持合理的回弹生命周期，彻底杜绝下拉箭头悬挂卡死问题。
	 */
	fun pullRefresh() {
		if (isRefreshing) return
		viewModelScope.launch {
			isRefreshing = true
			val start = System.currentTimeMillis()
			try {
				doRefresh()
				val elapsed = System.currentTimeMillis() - start
				if (elapsed < 500) {
					delay(500 - elapsed)
				}
			} finally {
				isRefreshing = false
			}
		}
	}

	/**
	 * 静默刷新：不弹全屏 Loading 菊花，不让屏幕白屏，平滑无感替换列表。
	 */
	fun refreshSilent() {
		viewModelScope.launch {
			doRefresh()
		}
	}

	fun refresh() {
		refreshSilent()
	}

	private suspend fun doRefresh() {
		withContext(Dispatchers.IO) {
			try {
				val s = app.prefs.current()
				val c = clientOf(s)
				val target = path
				val fresh = c.list(target)
				dirCache[target] = fresh
				withContext(Dispatchers.Main) {
					entries = fresh
					error = null
				}
				collectMediaFrom(fresh)
				updateFolderPaths(fresh)
				prefetchThumbsAsync(target, fresh)

				if (selectedAlbumPath.isNotBlank()) {
					val albumFresh = c.list(selectedAlbumPath)
					dirCache[selectedAlbumPath] = albumFresh
					collectMediaFrom(albumFresh)
					prefetchThumbsAsync(selectedAlbumPath, albumFresh)
				}
				if (selectedMediaPath.isNotBlank()) {
					val mediaFresh = c.list(selectedMediaPath)
					dirCache[selectedMediaPath] = mediaFresh
					collectMediaFrom(mediaFresh)
					prefetchThumbsAsync(selectedMediaPath, mediaFresh)
				}
			} catch (_: Throwable) {
			}
		}
	}

	/** 更新文件夹自定义图标并持久化至 SQLite 数据库 */
	fun updateFolderIcon(entry: DavEntry, icon: NasFolderIcon) {
		val cleanPath = entry.path.trim('/')
		viewModelScope.launch(Dispatchers.IO) {
			NasDb.getInstance(app).setFolderIcon(cleanPath, icon.key)
			withContext(Dispatchers.Main) {
				folderIcons = if (icon == NasFolderIcon.Default) {
					folderIcons - cleanPath
				} else {
					folderIcons + (cleanPath to icon.key)
				}
			}
		}
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
		val targetDir = if (selectedMediaPath.isNotBlank()) selectedMediaPath else "movies"
		mutate { client ->
			if (targetDir == "movies") {
				runCatching { client.mkcol("movies") }
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

	fun delete(entry: DavEntry, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
		// 乐观 UI 立即清除，告别删除迟钝/不生效的错觉
		entries = entries.filterNot { it.path == entry.path }
		allPhotos = allPhotos.filterNot { it.path == entry.path }
		allVideos = allVideos.filterNot { it.path == entry.path }
		selectedEntries = selectedEntries.filterNot { it.path == entry.path }.toSet()

		viewModelScope.launch {
			try {
				val s = app.prefs.current()
				withContext(Dispatchers.IO) {
					val c = client ?: app.repo.client(s).also { client = it }
					c.delete(entry.path, entry.isDir)
				}
				dirCache.clear()
				onResult(true, null)
				refresh()
			} catch (t: Throwable) {
				error = t.message ?: t.javaClass.simpleName
				onResult(false, t.message ?: "删除失败")
				refresh()
			}
		}
	}

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
			client = null  // repo 已停隧道并清缓存，VM 里的旧 client 指向死端口，必须丢弃重建
			open(path)
			scanGlobalMedia()
		}
	}

	private fun mutate(action: suspend (WebDavClient) -> Unit) {
		viewModelScope.launch {
			try {
				val s = app.prefs.current()
				withContext(Dispatchers.IO) {
					val c = client ?: app.repo.client(s).also { client = it }
					action(c)
				}
				dirCache.clear()
				open(path)
				scanGlobalMedia()
			} catch (t: Throwable) {
				error = t.message ?: t.javaClass.simpleName
			}
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
				val c = clientOf(s)
				val rootEntries = scanSemaphore.withPermit { c.list("") }
				collectMediaFrom(rootEntries)
				updateFolderPaths(rootEntries)
				prefetchThumbsAsync("", rootEntries)

				val albums = mutableListOf<DavEntry>()
				val mediaAlbums = mutableListOf<DavEntry>()

				for (dir in rootEntries.filter { it.isDir }) {
					if (loading) delay(200)
					runCatching {
						val sub = scanSemaphore.withPermit { c.list(dir.path) }
						dirCache[dir.path] = sub
						collectMediaFrom(sub)
						updateFolderPaths(sub)
						prefetchThumbsAsync(dir.path, sub)

						if (dir.name.equals("Photos", ignoreCase = true) || dir.name == "相册") {
							albums.addAll(sub.filter { it.isDir })
							for (subDir in sub.filter { it.isDir }) {
								if (loading) delay(200)
								runCatching {
									val subSub = scanSemaphore.withPermit { c.list(subDir.path) }
									dirCache[subDir.path] = subSub
									collectMediaFrom(subSub)
									updateFolderPaths(subSub)
									prefetchThumbsAsync(subDir.path, subSub)
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
									prefetchThumbsAsync(subDir.path, subSub)
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
