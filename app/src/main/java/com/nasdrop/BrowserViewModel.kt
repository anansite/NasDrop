package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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

/** 文件浏览与传输展示状态；目录和媒体池合并待同步上传项，网络请求不阻塞状态接收。 */
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

	var connectionMode by mutableStateOf(ConnectionMode.Auto)
		private set

	var authUrl by mutableStateOf("")
		private set

	/** 文件筛选分类：全部 / 图片 / 视频 / 文档 / 压缩包 / 音频 */
	var selectedCategory by mutableStateOf(FileCategory.All)
		private set

	/** 视图模式：夸克式极简列表 vs 宫格大图 */
	var viewMode by mutableStateOf(ViewMode.Cards)
		private set

	/** 持久化列表与网格滚动位置，跨 Route（看图/视频/设置）生命周期不丢失 */
	val photoGridState = LazyGridState()
	val fileListState = LazyListState()
	val fileCompactState = LazyListState()
	val fileGridState = LazyGridState()
	val mediaGridState = LazyGridState()

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
			return allPhotos.filter { isPathWithin(it.path, selectedAlbumPath) }
		}

	/** 当前所选子影集（或全部）视频集合 */
	val currentMediaVideos: List<DavEntry>
		get() {
			if (selectedMediaPath.isBlank()) return allVideos
			return allVideos.filter { isPathWithin(it.path, selectedMediaPath) }
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
	private var hasScannedMedia = false
	private var mediaScanJob: Job? = null
	private var mediaScanVersion = 0
	var mediaScanning by mutableStateOf(false)
		private set
	var mediaError by mutableStateOf<String?>(null)
		private set

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
		get() = transferTasks.count { it.status in setOf(TransferStatus.RUNNING, TransferStatus.QUEUED, TransferStatus.CONFIRMING) }

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
	private val downloads = DownloadQueue(viewModelScope, File(app.filesDir, "downloads/parts"),
		{ clientOf(app.prefs.current()) }, { task, part -> DownloadStorage.publish(app, task, part) })
	private var uploadListing = UploadListing()
	private var observedConfiguration: Settings? = null
	private val uploadRefreshDirs = linkedSetOf<String>()
	private var uploadRefreshJob: Job? = null
	private var directoryLoadJob: Job? = null
	private val directoryRequests = mutableMapOf<String, Long>()

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
		val c = app.repo.client(s).also { client = it }
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
		open("")
		startUnifiedLinkMonitor()
		cleanupOldCache()
		viewModelScope.launch {
			app.prefs.settings.collect { s ->
				connectionMode = s.connectionMode
			}
		}
		viewModelScope.launch {
			downloads.tasks.collect { tasks -> downloadTasks = tasks; syncTransferTasks() }
		}

		// 状态接收只更新内存/UI，绝不等待列目录；页面重建可从快照恢复已发送文件。
		viewModelScope.launch {
			val lastSentMap = mutableMapOf<String, Long>()
			val lastTimeMap = mutableMapOf<String, Long>()
			val speedMap = mutableMapOf<String, Long>()
			val completedIds = mutableSetOf<String>()
			combine(Uploads.tasks, app.prefs.settings) { list, settings -> list to settings.copy(lastDir = "") }
				.collect { (list, configuration) ->
				val previous = observedConfiguration
				if (previous != configuration) {
					observedConfiguration = configuration
					if (previous != null) {
						completedIds.clear()
						speedMap.clear()
						resetForConfigurationChange(previous.serverIdentity != configuration.serverIdentity)
					}
				}
				val now = System.currentTimeMillis()
				uploadTasks = list.filterNot { it.dismissed }.map { up ->
					val prevSent = lastSentMap[up.id] ?: 0L
					val prevTime = lastTimeMap[up.id] ?: now
					val dt = now - prevTime
					val speed = if (up.done || up.bodySent || up.error != null) {
						speedMap.remove(up.id)
						0L
					} else if (dt >= 300L) {
						val dSent = (up.sent - prevSent).coerceAtLeast(0)
						val instant = (dSent * 1000 / dt)
						val old = speedMap[up.id] ?: instant
						// 平滑移动平均，防止微小间隔抖动归零
						val smoothed = ((old * 0.6) + (instant * 0.4)).toLong()
						speedMap[up.id] = smoothed
						lastSentMap[up.id] = up.sent
						lastTimeMap[up.id] = now
						smoothed
					} else {
						speedMap[up.id] ?: 0L
					}

					val status = when {
						up.cancelled -> TransferStatus.CANCELLED
						up.done && up.error == null -> TransferStatus.COMPLETED
						up.error != null -> TransferStatus.FAILED
						up.bodySent -> TransferStatus.CONFIRMING
						up.started -> TransferStatus.RUNNING
						else -> TransferStatus.QUEUED
					}
					TransferTask(
						id = up.id,
						name = up.name,
						path = up.uri,
						isUpload = true,
						totalBytes = up.total,
						sentBytes = up.sent,
						speedBytesPerSec = speed,
						status = status,
						error = up.error,
						directory = up.dir,
					)
				}
				syncTransferTasks()
				val owned = list.filter { it.ownerKey.isBlank() || it.ownerKey == configuration.serverIdentity }
				val changedDirs = uploadListing.update(owned)
				changedDirs.forEach { renderUploadedDirectory(it) }
				val finished = owned.filter { it.done && completedIds.add(it.id) }
				if (finished.isNotEmpty()) {
					queueUploadRefresh(finished.filter { !it.listed && it.listingActive }.map { it.dir })
				}
			}
		}

		viewModelScope.launch(workerDispatcher) {
			checkForUpdates(silent = true)
		}
	}

	/** 媒体池是否需要因上传而重扫（惰性，切页面时才消费） */
	@Volatile private var mediaDirty = false

	/**
	 * 一次性操作提示（移动/重命名/新建/删除的结果）。
	 * 旧实现只把错误写进 [error]，而 error 在界面上根本没有渲染——
	 * 于是"移动文件失败"在用户看来就是"点了完全没反应"。这个字段由 UI 用 Toast 消费。
	 */
	var uiMessage by mutableStateOf<String?>(null)
		private set
	var checkingUploadDestination by mutableStateOf(false)
		private set
	var pendingUploadOverwriteNames by mutableStateOf<List<String>>(emptyList())
		private set
	var shareUploadBatchId by mutableStateOf<String?>(null)
		private set
	var shareUploadDirectory by mutableStateOf("")
		private set
	private data class PendingUpload(val uris: List<Uri>, val directory: String, val onStarted: (String) -> Unit)
	private var pendingUpload: PendingUpload? = null
	private var uploadPreparation: Job? = null
	private var preparationVersion = 0

	/** App 内上传与分享上传共用同名检查，明确确认后才允许覆盖远端文件。 */
	fun startUploads(uris: List<Uri>, dir: String, onStarted: () -> Unit) {
		prepareUploads(PendingUpload(uris, dir.trim('/')) { onStarted() })
	}

	fun startShareUploads(uris: List<Uri>, dir: String) {
		prepareUploads(PendingUpload(uris, dir.trim('/')) { id ->
			shareUploadDirectory = dir.trim('/')
			shareUploadBatchId = id
		})
	}

	private fun prepareUploads(request: PendingUpload) {
		if (request.uris.isEmpty()) return
		if (checkingUploadDestination || pendingUpload != null) { toast("请先完成当前上传确认"); return }
		val version = ++preparationVersion
		checkingUploadDestination = true
		uploadPreparation = viewModelScope.launch {
			try {
				val conflicts = withContext(Dispatchers.IO) {
					request.uris.forEach { uri ->
						app.contentResolver.openAssetFileDescriptor(uri, "r")?.close()
							?: error("无法读取所选文件，请重新选择")
					}
					val names = request.uris.map { Query.nameOf(app, it) }
					val settings = app.prefs.current()
					val existing = try {
						clientOf(settings).list(request.directory).map { it.name }.toSet()
					} catch (e: DavException) {
						if (e.code == 404) emptySet() else throw e
					}
					findUploadConflicts(names, existing, Uploads.tasks.value, request.directory, settings.serverIdentity)
				}
				if (conflicts.isEmpty()) launchUploads(request)
				else { pendingUpload = request; pendingUploadOverwriteNames = conflicts }
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Log.w("BrowserViewModel", "上传准备失败 dir=${request.directory} count=${request.uris.size}", e)
				toast(if (e is SecurityException) "读取权限已失效，请重新选择文件" else e.message ?: "无法检查上传位置，请重试")
			} finally {
				if (preparationVersion == version) checkingUploadDestination = false
			}
		}
	}

	/** 取消前置检查后，即使旧目录请求晚到，也不能再启动上传。 */
	fun cancelUploadPreparation() {
		preparationVersion++
		uploadPreparation?.cancel()
		checkingUploadDestination = false
	}

	fun confirmUploadOverwrite() {
		val request = pendingUpload ?: return
		pendingUpload = null
		pendingUploadOverwriteNames = emptyList()
		launchUploads(request)
	}

	fun dismissUploadOverwrite() { pendingUpload = null; pendingUploadOverwriteNames = emptyList() }

	private fun launchUploads(request: PendingUpload) {
		runCatching { UploadService.start(app, request.uris, request.directory) }
			.onSuccess { id -> request.onStarted(id) }
			.onFailure { error ->
				Log.w("BrowserViewModel", "无法启动上传服务 dir=${request.directory}", error)
				toast("无法开始上传：${error.message}")
			}
	}

	fun consumeMessage(): String? {
		val m = uiMessage
		uiMessage = null
		return m
	}

	private fun toast(msg: String) {
		viewModelScope.launch(Dispatchers.Main) { uiMessage = msg }
	}

	/** 将临时上传覆盖合入原始目录；失败不修改原缓存，覆盖上传失败能恢复旧文件。 */
	private fun mergeUploadedEntries(dir: String, remote: List<DavEntry>): List<DavEntry> {
		val pending = uploadListing.pending(dir).map { task ->
			DavEntry(task.name, join(dir, task.name), false, task.total.coerceAtLeast(0), task.createdAt)
		}
		val paths = pending.map { it.path }.toSet()
		return remote.filterNot { it.path in paths } + pending
	}

	/** 主线程更新文件列表和媒体池，既不列目录也不等待缩略图。 */
	private fun renderUploadedDirectory(dir: String) {
		val visible = mergeUploadedEntries(dir, dirCache[dir].orEmpty())
		if (path == dir) entries = visible
		allPhotos = allPhotos.filterNot { parentOf(it.path) == dir } + visible.filter { !it.isDir && it.isImage }
		allVideos = allVideos.filterNot { parentOf(it.path) == dir } + visible.filter { !it.isDir && it.isVideo }
	}

	/** 保存服务器原始列表，再合并仍未被目录确认的上传项；所有 UI 状态在主线程更新。 */
	private fun applyDirectoryListing(dir: String, fresh: List<DavEntry>) {
		dirCache[dir] = fresh
		Uploads.markListed(uploadListing.reconcile(dir, fresh.associate { it.path to it.size }))
		renderUploadedDirectory(dir)
		updateFolderPaths(fresh)
		prefetchThumbsAsync(dir, fresh)
	}

	/** 同目录只应用最后发起的请求，防止旧响应晚到后撤销已确认的新文件。 */
	private suspend fun loadDirectory(dir: String): List<DavEntry> {
		val configuration = app.prefs.current().copy(lastDir = "")
		val request = withContext(Dispatchers.Main) {
			((directoryRequests[dir] ?: 0L) + 1L).also { directoryRequests[dir] = it }
		}
		val (fresh, effectivePath) = withContext(Dispatchers.IO) {
			val c = clientOf(configuration)
			c.list(dir) to c.effectiveBase.encodedPath
		}
		withContext(Dispatchers.Main) {
			if (directoryRequests[dir] == request && (observedConfiguration == null || observedConfiguration == configuration)) applyDirectoryListing(dir, fresh)
		}
		val currentUrl = configuration.baseUrl.toHttpUrlOrNull()
		if (dir.isEmpty() && currentUrl != null && currentUrl.encodedPath != effectivePath) {
			val corrected = currentUrl.newBuilder().encodedPath(effectivePath).build().toString()
			viewModelScope.launch {
				val cur = app.prefs.current()
				if (cur.baseUrl != corrected) {
					app.prefs.save(cur.copy(baseUrl = corrected))
				}
			}
		}
		return fresh
	}

	/** 短时间内完成的同目录文件合并刷新；慢响应不能阻塞后续上传状态。 */
	private fun queueUploadRefresh(dirs: Collection<String>) {
		uploadRefreshDirs.addAll(dirs)
		if (uploadRefreshJob?.isActive == true) return
		uploadRefreshJob = viewModelScope.launch {
			while (uploadRefreshDirs.isNotEmpty()) {
				delay(350)
				val targets = uploadRefreshDirs.toList()
				uploadRefreshDirs.clear()
				for (dir in targets) {
					// OpenList 可能短暂返回旧目录；最多补查三次，不延迟上传完成或阻塞 UI。
					for (attempt in 0..3) {
						if (attempt > 0) delay(1000L shl (attempt - 1))
						try {
							val fresh = loadDirectory(dir)
							Log.i("BrowserViewModel", "上传目录已刷新 dir=$dir count=${fresh.size} attempt=$attempt")
						} catch (e: Exception) {
							if (e is CancellationException) throw e
							Log.w("BrowserViewModel", "上传目录刷新失败，保留待同步文件 dir=$dir attempt=$attempt", e)
						}
						if (uploadListing.pending(dir).none { it.done }) break
					}
				}
			}
		}
	}

	/** 惰性按需媒体扫描：用户切换到相册或影音页面时才触发，首屏启动完全零负载 */
	fun ensureMediaScanned(force: Boolean = false) {
		if (mediaScanning && !force) return
		if (hasScannedMedia && !force && !mediaDirty) return
		mediaScanJob?.cancel()
		val version = ++mediaScanVersion
		mediaDirty = false
		mediaScanning = true
		mediaError = null
		mediaScanJob = viewModelScope.launch {
			try {
				scanGlobalMedia()
				hasScannedMedia = mediaError == null
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				hasScannedMedia = false
				mediaError = e.message ?: "媒体加载失败，请重试"
				Log.w("BrowserViewModel", "媒体列表加载失败", e)
			} finally {
				if (mediaScanVersion == version) mediaScanning = false
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
				val tsRunning = TsBridge.status().startsWith("running")
				val isReady = info.isDirect || info.isRelay || tsRunning

				// 异常自愈防护：若持久化配置锁在 Direct 模式但局域网不可达，自动回退到 Auto 模式并自愈
				if (connectionMode == ConnectionMode.Direct && !app.repo.isDirectConfirmed && !info.isDirect && !info.isLan) {
					setConnectionMode(ConnectionMode.Auto) {
						uiMessage = "检测到局域网不可达，已自动切换为智能自动模式"
					}
				}

				if (isReady) {
					if ((!loading && error != null) || (!lastDirect && isNowDirect)) {
						withContext(Dispatchers.Main) { open(path) }
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
	 *  读取 thumbRevision / LocalThumbs.revision 建立 Compose 快照依赖，异步缩略图就绪后自动重组刷新。
	 *
	 *  优先级：**刚上传文件的本地小图** > 服务端真缩略图 > 原图。
	 *  本地图放最前是因为刚 PUT 完的文件服务端还没生成缩略图，而此时拿原图当缩略图
	 *  等于在列表里下好几 MB——用户看到的就是"传完了，可是图片一直是灰的"。
	 */
	fun thumbnailUrlFor(entryPath: String): String? {
		@Suppress("UNUSED_VARIABLE") val localRev = LocalThumbs.revision
		val key = entryPath.trim('/')
		LocalThumbs.get(key)?.let { return android.net.Uri.fromFile(it).toString() }
		@Suppress("UNUSED_VARIABLE") val revision = thumbRevision
		return client?.thumbnailUrl(key)?.toString()
	}

	/**
	 * 看图器垫底层专用：本地小图 > **真正的服务端缩略图**，没有就返回 null。
	 * 不能复用 thumbnailUrlFor —— 那对图片会回退成原图 WebDAV 地址，
	 * 而原图层走的是 /p/?sign= 直链，两个 URL 不互命中缓存，等于把原图下两遍。
	 */
	fun serverThumbUrlFor(entryPath: String): String? {
		@Suppress("UNUSED_VARIABLE") val localRev = LocalThumbs.revision
		val key = entryPath.trim('/')
		LocalThumbs.get(key)?.let { return android.net.Uri.fromFile(it).toString() }
		@Suppress("UNUSED_VARIABLE") val revision = thumbRevision
		return client?.serverThumbUrl(key)?.toString()
	}

	fun markThumbnailFailed(entryPath: String) {
		client?.markThumbnailFailed(entryPath)
		thumbRevision++
	}

	/** 看图器退出时，平滑对齐当前浏览的照片，防止滚动位置跳回顶部 */
	fun syncViewerPosition(entry: DavEntry) {
		viewModelScope.launch {
			if (currentTab == MainTab.Photos) {
				var index = 0
				for (g in photoTimelineGroups) {
					index++ // 标题项占 1 个 span
					val itemIdx = g.items.indexOfFirst { it.path == entry.path }
					if (itemIdx != -1) {
						index += itemIdx
						val visible = photoGridState.layoutInfo.visibleItemsInfo
						if (visible.none { it.index == index }) {
							photoGridState.scrollToItem((index - 1).coerceAtLeast(0))
						}
						break
					} else {
						index += g.items.size
					}
				}
			} else if (currentTab == MainTab.Files) {
				val idx = filteredEntries.indexOfFirst { it.path == entry.path }
				if (idx != -1) {
					when (viewMode) {
						ViewMode.Cards -> {
							val visible = fileListState.layoutInfo.visibleItemsInfo
							if (visible.none { it.index == idx }) {
								fileListState.scrollToItem((idx - 1).coerceAtLeast(0))
							}
						}
						ViewMode.Compact -> {
							val visible = fileCompactState.layoutInfo.visibleItemsInfo
							if (visible.none { it.index == idx }) {
								fileCompactState.scrollToItem((idx - 1).coerceAtLeast(0))
							}
						}
						ViewMode.Grid -> {
							val visible = fileGridState.layoutInfo.visibleItemsInfo
							if (visible.none { it.index == idx }) {
								fileGridState.scrollToItem((idx - 1).coerceAtLeast(0))
							}
						}
					}
				}
			}
		}
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
			// 上传时生成的本地缩略图只服务"刚上传"这个窗口，7 天后清掉
			LocalThumbs.cleanup(app)
		}
	}


	fun imageAlbum(entry: DavEntry): Pair<List<DavEntry>, Int> {
		val list = if (currentTab == MainTab.Photos) {
			currentAlbumPhotos.sortedByDescending { it.modified }
		} else {
			filteredEntries.filter { !it.isDir && it.isImage }
		}
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

	/** 切换并应用连接模式（如一键锁定直连或切回智能模式） */
	fun setConnectionMode(mode: ConnectionMode, onComplete: (String) -> Unit = {}) {
		connectionMode = mode
		viewModelScope.launch {
			withContext(Dispatchers.IO) {
				app.prefs.setConnectionMode(mode)
				app.repo.invalidate()
			}
			refresh()
			val msg = when (mode) {
				ConnectionMode.Direct -> "已切换为【强制直连模式】"
				ConnectionMode.Auto -> "已切换为【智能自动模式】"
				ConnectionMode.Tailscale -> "已切换为【强制组网模式】"
			}
			onComplete(msg)
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

	fun updateSelection(newSet: Set<DavEntry>) {
		selectedEntries = newSet
		if (newSet.isNotEmpty() && !isMultiSelectMode) {
			isMultiSelectMode = true
		}
	}

	fun updateSelectionByPaths(paths: Set<String>) {
		val allKnown = (currentAlbumPhotos + filteredEntries + currentMediaVideos).distinctBy { it.path }
		val newSet = allKnown.filter { paths.contains(it.path) }.toSet()
		selectedEntries = newSet
		if (newSet.isNotEmpty() && !isMultiSelectMode) {
			isMultiSelectMode = true
		}
	}

	fun toggleSelectByPath(path: String, select: Boolean) {
		val found = currentAlbumPhotos.firstOrNull { it.path == path }
			?: filteredEntries.firstOrNull { it.path == path }
			?: currentMediaVideos.firstOrNull { it.path == path }
			?: return
		val newSet = selectedEntries.toMutableSet()
		if (select) {
			newSet.add(found)
		} else {
			newSet.remove(found)
		}
		selectedEntries = newSet
	}

	fun toggleSelectGroup(group: TimelineGroup, select: Boolean) {
		val newSet = selectedEntries.toMutableSet()
		if (select) {
			newSet.addAll(group.items)
		} else {
			newSet.removeAll(group.items.toSet())
		}
		selectedEntries = newSet
	}

	/** 批量删除仅撤销已成功路径的上传覆盖，失败项保留并由刷新恢复。 */
	fun batchDelete(onDone: (Int, Int) -> Unit = { _, _ -> }) {
		val targets = selectedEntries.toList()
		if (targets.isEmpty()) return
		val targetPaths = targets.map { it.path }.toSet()
		fun hit(p: String) = targetPaths.any { p == it || p.startsWith("$it/") }

		// 乐观即时移除，秒级清爽
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
		exitMultiSelect()

		viewModelScope.launch {
			var okCount = 0
			var failCount = 0
			val deleted = mutableListOf<String>()
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = clientOf(s)
				for (item in targets) {
					try {
						c.delete(item.path, item.isDir)
						okCount++
						deleted += item.path
					} catch (e: Exception) {
						if (e is CancellationException) throw e
						Log.w("BrowserViewModel", "批量删除失败 path=${item.path}", e)
						failCount++
					}
				}
			}
			dirCache.clear()
			LocalThumbs.forget(*deleted.toTypedArray())
			pruneMediaPools(deleted)
			onDone(okCount, failCount)
			if (failCount > 0) uiMessage = "删除完成：成功 $okCount 项，失败 $failCount 项"
			refresh()
		}
	}

	/** 批量移动仅撤销成功项的旧路径，失败项仍可见。 */
	fun batchMove(targetDir: String, onDone: (Int, Int) -> Unit = { _, _ -> }) {
		val targets = selectedEntries.toList()
		if (targets.isEmpty()) return
		val paths = targets.map { it.path }
		val pathSet = paths.toSet()
		fun hit(p: String) = pathSet.any { p == it || p.startsWith("$it/") }
		// 乐观：先从列表移除，避免"批量移动完还挂在原地"
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
		exitMultiSelect()
		viewModelScope.launch {
			var okCount = 0
			var failCount = 0
			val moved = mutableListOf<String>()
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = clientOf(s)
				for (item in targets) {
					try {
						c.move(item.path, join(targetDir, item.name))
						okCount++
						moved += item.path
					} catch (e: Exception) {
						if (e is CancellationException) throw e
						Log.w("BrowserViewModel", "批量移动失败 path=${item.path} targetDir=$targetDir", e)
						failCount++
					}
				}
			}
			dirCache.clear()
			LocalThumbs.forget(*moved.toTypedArray())
			pruneMediaPools(moved)
			mediaDirty = true
			refreshSilent()
			if (failCount > 0) uiMessage = "移动完成：成功 $okCount 项，失败 $failCount 项"
			onDone(okCount, failCount)
		}
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
		downloads.enqueue(entry.name, entry.path, entry.size)
	}

	/** 所有下载控制统一交给持有实际 Call 和写者的队列。 */
	fun pauseTask(taskId: String) {
		viewModelScope.launch { downloads.pause(taskId) }
	}

	fun resumeTask(taskId: String) {
		viewModelScope.launch { downloads.resume(taskId) }
	}

	/** 运行任务实际取消；已结束任务仅清除结果，不删除用户已保存的文件。 */
	fun cancelTask(taskId: String) {
		val upload = Uploads.tasks.value.firstOrNull { it.id == taskId }
		if (upload != null) {
			if (upload.done) Uploads.remove(taskId) else Uploads.cancel(taskId)
		} else {
			val task = downloadTasks.firstOrNull { it.id == taskId } ?: return
			if (task.finished) downloads.remove(taskId) else viewModelScope.launch { downloads.cancel(taskId) }
		}
	}

	fun pauseAll() {
		viewModelScope.launch { downloadTasks.toList().forEach { downloads.pause(it.id) } }
	}

	fun resumeAll() {
		viewModelScope.launch { downloadTasks.toList().forEach { downloads.resume(it.id) } }
	}

	/** 上传重试重新检查读取权限及同名覆盖；下载沿原任务继续安全重试。 */
	fun retryTask(taskId: String) {
		val upload = Uploads.tasks.value.firstOrNull { it.id == taskId }
		if (upload != null) {
			if (upload.ownerKey.isNotBlank() && upload.ownerKey != observedConfiguration?.serverIdentity) {
				toast("请先切换回该任务的服务器后重试"); return
			}
			if (!upload.done || (upload.error == null && !upload.cancelled)) return
			startUploads(listOf(Uri.parse(upload.uri)), upload.dir) {}
		} else viewModelScope.launch {
			runCatching { downloads.retry(taskId) }.onFailure { toast(it.message ?: "无法重试下载") }
		}
	}

	fun clearFinishedTransfers() { Uploads.clearFinished(); downloads.clearFinished() }

	/** 上传结果定位到远端目录；下载结果通过临时只读 URI 授权交给系统查看器。 */
	fun openTransferResult(context: Context, taskId: String) {
		val task = transferTasks.firstOrNull { it.id == taskId && it.status == TransferStatus.COMPLETED } ?: return
		if (task.isUpload) {
			val upload = Uploads.tasks.value.firstOrNull { it.id == task.id }
			if (upload != null && upload.ownerKey.isNotBlank() && upload.ownerKey != observedConfiguration?.serverIdentity) {
				toast("该记录属于其他服务器，请切换回对应服务器后查看"); return
			}
			currentTab = MainTab.Files
			searchQuery = ""
			selectCategory(FileCategory.All)
			open(task.directory.orEmpty())
			return
		}
		val uri = task.resultUri?.let { Uri.parse(it) } ?: return
		runCatching {
			val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream")
				.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
			context.startActivity(Intent.createChooser(view, "打开下载文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		}.onFailure { toast("无法打开文件，请安装支持此格式的应用") }
	}

	override fun onCleared() { downloads.close(); super.onCleared() }

	// ==================== 浏览与目录 ====================
	/** 切目录立即使用缓存与待同步上传项；取消旧加载，避免迟到响应把用户带回旧目录。 */
	fun open(target: String) {
		directoryLoadJob?.cancel()
		val cached = dirCache[target]
		val oldPath = path
		path = target
		entries = mergeUploadedEntries(target, cached.orEmpty())
		loading = cached == null && entries.isEmpty()
		error = null

		if (oldPath != target) {
			viewModelScope.launch {
				runCatching {
					fileListState.scrollToItem(0)
					fileCompactState.scrollToItem(0)
					fileGridState.scrollToItem(0)
				}
			}
		}

		directoryLoadJob = viewModelScope.launch {
			try {
				loadDirectory(target)
				if (path == target) error = null
			} catch (t: Exception) {
				if (t is CancellationException) throw t
				Log.w("BrowserViewModel", "目录加载失败 dir=$target", t)
				if (path == target && cached == null) {
					error = t.message ?: t.javaClass.simpleName
				}
			} finally {
				if (path == target) loading = false
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
					if (currentTab == MainTab.Photos || currentTab == MainTab.Media) {
						val selected = if (currentTab == MainTab.Photos) selectedAlbumPath else selectedMediaPath
						if (selected.isNotBlank()) {
							try { loadDirectory(selected); mediaError = null }
							catch (e: Exception) {
								if (e is CancellationException) throw e
								mediaError = e.message ?: "媒体刷新失败"
								Log.w("BrowserViewModel", "媒体刷新失败 dir=$selected", e)
							}
						} else {
							ensureMediaScanned(force = true)
							mediaScanJob?.join()
						}
					} else doRefresh()
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

	/** 刷新进入时记录目标；返回后按目录应用，不覆盖期间切换到的其他目录。 */
	private suspend fun doRefresh() {
		val targets = listOf(path, selectedAlbumPath, selectedMediaPath)
			.filter { it.isNotBlank() || it == path }.distinct()
		for (target in targets) {
			try {
				loadDirectory(target)
				if (path == target) error = null
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Log.w("BrowserViewModel", "刷新失败 dir=$target", e)
				if (path == target) error = e.message ?: "刷新失败"
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
		val newPath = join(path, finalName)
		val newEntry = DavEntry(name = finalName, path = newPath, isDir = true, size = 0L, modified = System.currentTimeMillis())
		entries = (entries + newEntry).sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
		if (newPath !in allFolderPaths) {
			allFolderPaths = (allFolderPaths + newPath).sorted()
		}
		if (path.equals("Photos", true) || path == "相册" || isAlbum) {
			if (allAlbumFolders.none { it.path == newPath }) {
				allAlbumFolders = (allAlbumFolders + newEntry).sortedBy { it.name }
			}
		}
		if (path.equals("movies", true) || path == "影视" || path == "影音") {
			if (allMediaFolders.none { it.path == newPath }) {
				allMediaFolders = (allMediaFolders + newEntry).sortedBy { it.name }
			}
		}
		mutate(onFailure = {
			entries = entries.filterNot { it.path == newPath }
			allAlbumFolders = allAlbumFolders.filterNot { it.path == newPath }
			allMediaFolders = allMediaFolders.filterNot { it.path == newPath }
			allFolderPaths = allFolderPaths.filterNot { it == newPath }
		}) { it.mkcol(newPath) }
	}

	fun openAlbum(albumPath: String) {
		if (selectedAlbumPath != albumPath) {
			selectedAlbumPath = albumPath
			viewModelScope.launch { runCatching { photoGridState.scrollToItem(0) } }
		}
	}

	fun upAlbum() {
		if (selectedAlbumPath.isBlank()) return
		val parent = selectedAlbumPath.substringBeforeLast('/', "")
		selectedAlbumPath = if (parent.equals("Photos", true) || parent == "相册") "" else parent
		viewModelScope.launch { runCatching { photoGridState.scrollToItem(0) } }
	}

	fun openMedia(mediaPath: String) {
		if (selectedMediaPath != mediaPath) {
			selectedMediaPath = mediaPath
			viewModelScope.launch { runCatching { mediaGridState.scrollToItem(0) } }
		}
	}

	fun upMedia() {
		if (selectedMediaPath.isBlank()) return
		val parent = selectedMediaPath.substringBeforeLast('/', "")
		selectedMediaPath = if (parent.equals("movies", true) || parent == "影视" || parent == "影音") "" else parent
		viewModelScope.launch { runCatching { mediaGridState.scrollToItem(0) } }
	}

	/** 在相册专属目录下新建相册文件夹：立即乐观呈现于相册胶囊栏与选择列表，创建失败平滑回滚 */
	fun createAlbum(name: String) {
		val targetDir = if (selectedAlbumPath.isNotBlank()) selectedAlbumPath else "Photos"
		val newPath = join(targetDir, name)
		val newEntry = DavEntry(name = name, path = newPath, isDir = true, size = 0L, modified = System.currentTimeMillis())
		if (allAlbumFolders.none { it.path == newPath }) {
			allAlbumFolders = (allAlbumFolders + newEntry).sortedBy { it.name }
		}
		if (newPath !in allFolderPaths) {
			allFolderPaths = (allFolderPaths + newPath).sorted()
		}
		if (path == targetDir && entries.none { it.path == newPath }) {
			entries = (entries + newEntry).sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
		}
		mutate(onFailure = {
			allAlbumFolders = allAlbumFolders.filterNot { it.path == newPath }
			allFolderPaths = allFolderPaths.filterNot { it == newPath }
			entries = entries.filterNot { it.path == newPath }
		}) { client ->
			if (targetDir == "Photos") {
				runCatching { client.mkcol("Photos") }
			}
			client.mkcol(newPath)
		}
	}

	/** 在影音专属目录下新建影集/分类文件夹：立即乐观呈现于影音胶囊栏与选择列表，创建失败平滑回滚 */
	fun createMediaFolder(name: String) {
		val targetDir = if (selectedMediaPath.isNotBlank()) selectedMediaPath else "movies"
		val newPath = join(targetDir, name)
		val newEntry = DavEntry(name = name, path = newPath, isDir = true, size = 0L, modified = System.currentTimeMillis())
		if (allMediaFolders.none { it.path == newPath }) {
			allMediaFolders = (allMediaFolders + newEntry).sortedBy { it.name }
		}
		if (newPath !in allFolderPaths) {
			allFolderPaths = (allFolderPaths + newPath).sorted()
		}
		if (path == targetDir && entries.none { it.path == newPath }) {
			entries = (entries + newEntry).sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
		}
		mutate(onFailure = {
			allMediaFolders = allMediaFolders.filterNot { it.path == newPath }
			allFolderPaths = allFolderPaths.filterNot { it == newPath }
			entries = entries.filterNot { it.path == newPath }
		}) { client ->
			if (targetDir == "movies") {
				runCatching { client.mkcol("movies") }
			}
			client.mkcol(newPath)
		}
	}

	/** 相册删除复用文件删除流程，成功后退出对应的子相册。 */
	fun deleteAlbum(folder: DavEntry) {
		delete(folder) { success, _ ->
			if (success && (selectedAlbumPath == folder.path || selectedAlbumPath.startsWith("${folder.path}/"))) {
				selectedAlbumPath = ""
			}
		}
	}

	/** 影音分类删除复用文件删除流程，成功后退出对应的子目录。 */
	fun deleteMediaFolder(folder: DavEntry) {
		delete(folder) { success, _ ->
			if (success && (selectedMediaPath == folder.path || selectedMediaPath.startsWith("${folder.path}/"))) {
				selectedMediaPath = ""
			}
		}
	}

	/** 删除成功后同步撤销跨页面的上传覆盖；删除失败不使原文件的上传记录失效。 */
	fun delete(entry: DavEntry, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
		val p = entry.path
		fun hit(target: String) = target == p || target.startsWith("$p/")
		// 乐观 UI 立即清除，包括文件列表、相册/影音胶囊栏和全部媒体池，告别删除迟钝/需要手动刷新的问题
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		selectedEntries = selectedEntries.filterNot { hit(it.path) }.toSet()
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""

		viewModelScope.launch {
			val err = runRemote { it.delete(entry.path, entry.isDir) }
			dirCache.clear()
			if (err == null) {
				LocalThumbs.forget(entry.path)
				pruneMediaPools(listOf(entry.path))
				onResult(true, null)
			} else {
				uiMessage = "删除失败：$err"
				onResult(false, err)
			}
			refresh()
		}
	}

	fun rename(entry: DavEntry, newName: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
		val dest = join(entry.path.substringBeforeLast('/', ""), newName)
		val oldPath = entry.path
		fun hit(target: String) = target == oldPath || target.startsWith("$oldPath/")
		fun replacePath(p: String) = if (p == oldPath) dest else dest + p.removePrefix(oldPath)

		val renamedEntry = entry.copy(name = newName, path = dest)
		entries = entries.map { if (it.path == oldPath) renamedEntry else it }
		allPhotos = allPhotos.map { if (hit(it.path)) it.copy(path = replacePath(it.path)) else it }
		allVideos = allVideos.map { if (hit(it.path)) it.copy(path = replacePath(it.path)) else it }
		allAlbumFolders = allAlbumFolders.map { if (it.path == oldPath) renamedEntry else if (hit(it.path)) it.copy(path = replacePath(it.path)) else it }
		allMediaFolders = allMediaFolders.map { if (it.path == oldPath) renamedEntry else if (hit(it.path)) it.copy(path = replacePath(it.path)) else it }
		allFolderPaths = allFolderPaths.map { if (hit(it)) replacePath(it) else it }.distinct().sorted()
		if (hit(selectedAlbumPath)) selectedAlbumPath = replacePath(selectedAlbumPath)
		if (hit(selectedMediaPath)) selectedMediaPath = replacePath(selectedMediaPath)

		viewModelScope.launch {
			val err = runRemote { it.move(entry.path, dest) }
			dirCache.clear()
			refreshSilent()
			if (err == null) {
				LocalThumbs.forget(entry.path)
				mediaDirty = true
				onResult(true, "已重命名为 $newName")
			} else {
				uiMessage = "重命名失败：$err"
				onResult(false, err)
			}
		}
	}

	/**
	 * 移动单个文件/文件夹。
	 *
	 * 结果通过 [onResult] 异步回传——旧版调用方是"先弹 Toast 说成功了，再去请求"，
	 * 而这个请求其实会因为 Destination 头的问题被服务端 502 掉，于是变成
	 * "提示已移动成功，但文件还在原地"。
	 */
	fun move(entry: DavEntry, targetDir: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
		val dest = join(targetDir, entry.name)
		// 乐观：当前列表里立刻消失，避免"点完没有任何反应"的错觉
		entries = entries.filterNot { it.path == entry.path }
		viewModelScope.launch {
			val err = runRemote { it.move(entry.path, dest) }
			dirCache.clear()
			if (err == null) {
				LocalThumbs.forget(entry.path)
				pruneMediaPools(listOf(entry.path))
				mediaDirty = true // 目标目录的相册/影音归属变了，下次进页面时校正
				refreshSilent()
				onResult(true, "已移动到 /${targetDir.ifEmpty { "根目录" }}")
			} else {
				refreshSilent() // 回滚乐观删除
				uiMessage = "移动失败：$err"
				onResult(false, err)
			}
		}
	}

	fun rememberDir(dir: String) {
		viewModelScope.launch { app.prefs.setLastDir(dir) }
	}

	fun reloadSettings() {
		viewModelScope.launch {
			cancelUploadPreparation()
			dismissUploadOverwrite()
			mediaScanJob?.cancel()
			dirCache.clear()
			withContext(Dispatchers.IO) { app.repo.invalidate() }
			client = null  // repo 已停隧道并清缓存，VM 里的旧 client 指向死端口，必须丢弃重建
			allPhotos = emptyList()
			allVideos = emptyList()
			allAlbumFolders = emptyList()
			allMediaFolders = emptyList()
			hasScannedMedia = false
			open(path)
			if (currentTab == MainTab.Photos || currentTab == MainTab.Media) ensureMediaScanned(force = true)
		}
	}

	/** 配置流同时通知主页面与分享页，旧客户端和晚到目录响应不能跨服务器复用。 */
	private fun resetForConfigurationChange(serverChanged: Boolean) {
		cancelUploadPreparation()
		dismissUploadOverwrite()
		directoryLoadJob?.cancel()
		mediaScanJob?.cancel()
		uploadRefreshJob?.cancel()
		uploadRefreshDirs.clear()
		directoryRequests.keys.toList().forEach { directoryRequests[it] = (directoryRequests[it] ?: 0) + 1 }
		client = null
		dirCache.clear()
		uploadListing = UploadListing()
		entries = emptyList()
		allPhotos = emptyList(); allVideos = emptyList()
		allAlbumFolders = emptyList(); allMediaFolders = emptyList()
		hasScannedMedia = false
		mediaError = null
		if (serverChanged) { path = ""; selectedAlbumPath = ""; selectedMediaPath = "" }
		open(path)
		if (currentTab == MainTab.Photos || currentTab == MainTab.Media) ensureMediaScanned(force = true)
	}

	/** 执行一次远端写操作，返回 null 表示成功，否则是错误文案。统一在这里兜住异常。 */
	private suspend fun runRemote(block: suspend (WebDavClient) -> Unit): String? {
		return try {
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = clientOf(s)
				block(c)
			}
			null
		} catch (t: Exception) {
			if (t is CancellationException) throw t
			Log.w("BrowserViewModel", "远端操作失败", t)
			t.message ?: t.javaClass.simpleName
		}
	}

	/** 从相册/影音池里摘掉一批路径（含其子项），用于删除/移动后的即时校正 */
	private fun pruneMediaPools(paths: Collection<String>) {
		if (paths.isEmpty()) return
		uploadListing.forget(paths)
		Uploads.forgetPaths(paths)
		fun hit(p: String) = paths.any { p == it || p.startsWith("$it/") }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
	}

	/**
	 * 远端变更统一入口（新建/删除相册等）。
	 *
	 * 与旧版的区别：
	 *  1) 失败不再静默——旧版把异常塞进 error，而 error 在界面上没有任何渲染，
	 *     所以"移动/新建失败"在用户看来就是"点了完全没反应"；
	 *  2) 成功后只刷新当前目录，不再 scanGlobalMedia() 全量重扫（根目录 + 每个子目录
	 *     串行列一遍，隧道下几十秒，期间列表还可能被更旧的数据覆盖）。
	 */
	private fun mutate(onFailure: () -> Unit = {}, action: suspend (WebDavClient) -> Unit) {
		viewModelScope.launch {
			val err = runRemote(action)
			dirCache.clear()
			refreshSilent()
			if (err != null) {
				onFailure()
				uiMessage = "操作失败：$err"
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

	private fun updateFolderPaths(items: List<DavEntry>) {
		val dirs = items.filter { it.isDir }.map { it.path }
		allFolderPaths = (allFolderPaths + dirs).distinct().sorted()
	}

	/** 媒体扫描仅在用户进入对应页面时执行，逐目录呈现；失败保留现有内容并明确提示。 */
	private suspend fun scanGlobalMedia() {
		val rootEntries = loadDirectory("")
		val albums = mutableListOf<DavEntry>()
		val mediaAlbums = mutableListOf<DavEntry>()
		for (dir in rootEntries.filter { it.isDir }) {
			val sub = scanMediaDirectory(dir.path) ?: continue
			if (dir.name.equals("Photos", true) || dir.name == "相册") {
				albums.addAll(sub.filter { it.isDir })
				sub.filter { it.isDir }.forEach { scanMediaDirectory(it.path) }
			} else if (dir.name.equals("movies", true) || dir.name == "影视" || dir.name == "影音") {
				mediaAlbums.addAll(sub.filter { it.isDir })
				sub.filter { it.isDir }.forEach { scanMediaDirectory(it.path) }
			} else {
				if (sub.any { it.isImage }) albums.add(dir)
				if (sub.any { it.isVideo }) mediaAlbums.add(dir)
			}
		}
		allAlbumFolders = (albums + if (mediaError != null) allAlbumFolders else emptyList()).distinctBy { it.path }
		allMediaFolders = (mediaAlbums + if (mediaError != null) allMediaFolders else emptyList()).distinctBy { it.path }
	}

	private suspend fun scanMediaDirectory(directory: String): List<DavEntry>? = try {
		loadDirectory(directory)
	} catch (e: Exception) {
		if (e is CancellationException) throw e
		mediaError = "部分目录加载失败，可重试补全"
		Log.w("BrowserViewModel", "媒体子目录加载失败 dir=$directory", e)
		null
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
