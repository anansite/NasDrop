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
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class MainTab {
	Home,
	Photos,
	Files,
	Media,
	Settings,
	Me,
}

enum class MediaFilterType(val label: String) {
	All("全部"),
	PhotosOnly("仅照片"),
	VideosOnly("仅视频"),
	WithAudioMemo("伴生记忆"),
}

enum class ViewMode {
	Cards,   // 夸克式极简纯净列表
	Compact, // 紧凑列表
	Grid,    // 2列宫格大图
}

enum class SortOrder(val label: String) {
	UploadDesc("按上传时间 ↓"),
	UploadAsc("按上传时间 ↑"),
	CreatedDesc("按生成时间 ↓"),
	CreatedAsc("按生成时间 ↑"),
	NameAsc("按名称 A-Z"),
	SizeDesc("按文件大小 ↓"),
}

data class TimelineMoment(
	val timeTitle: String,
	val timestamp: Long,
	val items: List<DavEntry>,
)

data class TimelineGroup(
	val dateTitle: String,
	val count: Int,
	val items: List<DavEntry>,
	val moments: List<TimelineMoment> = emptyList(),
)

data class MemoryAlbumGroup(
	val albumName: String,
	val albumPath: String,
	val items: List<DavEntry>,
)

data class MemoryYearGroup(
	val year: Int,
	val yearsAgo: Int,
	val totalCount: Int,
	val photoCount: Int,
	val videoCount: Int,
	val coverEntry: DavEntry,
	val albumGroups: List<MemoryAlbumGroup>,
)

/** 文件浏览与传输展示状态；目录和媒体池合并待同步上传项，网络请求不阻塞状态接收。 */
class BrowserViewModel(private val app: NasDropApp) : ViewModel() {
	var currentTab by mutableStateOf(MainTab.Home)

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

	/** 排序方式：默认按上传时间降序（新上传内容立即置顶），支持按上传时间 / 生成时间 / 名称 / 大小排序 */
	var sortOrder by mutableStateOf(SortOrder.UploadDesc)
		private set

	/** 全局搜索关键词 */
	var searchQuery by mutableStateOf("")

	/** 全局聚合照片池（跨目录汇总所有照片，类似主流网盘相册） */
	var allPhotos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 全局聚合视频池（跨目录汇总所有视频，类似主流网盘影音） */
	var allVideos by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 全局聚合文档池（跨目录汇总所有文档，支持根目录筛选 xls/pdf/doc 等） */
	var allDocuments by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	// ========== 相册与影音子分类浏览系统 ==========
	/** 当前选中的子相册路径："" 表示查看全部时光流，若为具体路径如 "Photos/云南自驾" 则浏览该子相册，"__ALL_FOLDERS__" 表示查看相册文件夹概览 */
	var selectedAlbumPath by mutableStateOf("")

	/** 当前选中的子影集路径："" 表示查看全部视频流，若为具体路径如 "movies/漫威" 则浏览该子影集，"__ALL_FOLDERS__" 表示查看影音文件夹概览 */
	var selectedMediaPath by mutableStateOf("")

	/** 相册自定义排序记录（持久化至远端 .nasdrop_meta.json） */
	var albumOrder by mutableStateOf<List<String>>(emptyList())

	/** 影音分类自定义排序记录（持久化至远端 .nasdrop_meta.json） */
	var mediaOrder by mutableStateOf<List<String>>(emptyList())

	/** 所有已发现的相册文件夹列表（含封面与子相册） */
	var allAlbumFolders by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 所有已发现的影音分类文件夹列表 */
	var allMediaFolders by mutableStateOf<List<DavEntry>>(emptyList())
		private set

	/** 那年今天分组集合（按年份倒序，包含每年下的相册分组） */
	var memoryYearGroups by mutableStateOf<List<MemoryYearGroup>>(emptyList())
		private set

	/** 相册照片串联组（直接持久化于服务端 SQLite 数据库） */
	var photoGroups by mutableStateOf<List<PhotoGroup>>(emptyList())
		private set

	/** 当前打开的串联组 ID（用于从组内放大照片时限定左右滑动范围在组内） */
	var activePhotoGroupId by mutableStateOf<String?>(null)

	/** 当用户从串联组内点击「+ 追加 -> 新上传照片」时记录目标组 ID，上传完成自动入组 */
	var pendingAppendGroupId by mutableStateOf<String?>(null)

	/** 媒体筛选模式：全部、仅照片、仅视频、带语音/备忘 */
	var mediaFilterType by mutableStateOf(MediaFilterType.All)

	/** 当前所选子相册（或全部）照片与视频集合：全面整合照片与视频，按时间倒序统一呈现在相册中 */
	val currentAlbumPhotos: List<DavEntry>
		get() {
			val allMedia = (allPhotos + allVideos).filterNot { it.name.startsWith(".") || it.path.contains(".thumbnails") }.distinctBy { it.path }
			val filtered = if (selectedAlbumPath.isBlank() || selectedAlbumPath == "__ALL_FOLDERS__") allMedia
			else allMedia.filter { isPathWithin(it.path, selectedAlbumPath) }
			val typeFiltered = when (mediaFilterType) {
				MediaFilterType.All -> filtered
				MediaFilterType.PhotosOnly -> filtered.filter { it.isImage }
				MediaFilterType.VideosOnly -> filtered.filter { it.isVideo }
				MediaFilterType.WithAudioMemo -> {
					@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
					filtered.filter { it.hasAudio || it.hasMemo }
				}
			}
			return typeFiltered.sortedByDescending { it.effectiveDate }
		}

	/** 整合相册与影音目录列表（滤除冗余的默认影集残留，全面归入统一相册） */
	val combinedAlbumFolders: List<DavEntry>
		get() = (allAlbumFolders + allMediaFolders).filterNot {
			it.name.startsWith(".") || it.path.contains(".thumbnails") ||
				it.name == "默认影集" || it.name == "默认影音" ||
				it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
				it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
		}.distinctBy { it.path }

	/** 当前所选子影集（或全部）视频集合 */
	val currentMediaVideos: List<DavEntry>
		get() {
			val distinct = allVideos.filterNot { it.name.startsWith(".") || it.path.contains(".thumbnails") }.distinctBy { it.path }
			if (selectedMediaPath.isBlank() || selectedMediaPath == "__ALL_FOLDERS__") return distinct
			return distinct.filter { isPathWithin(it.path, selectedMediaPath) }
		}

	/** 当前已发现的所有文件夹相对路径集合（供文件移动对话框选择） */
	var allFolderPaths by mutableStateOf<List<String>>(listOf(""))
		private set

	val routeInfo: StateFlow<RouteInfo> = app.repo.routeInfo
	val settings: kotlinx.coroutines.flow.Flow<Settings> = app.prefs.settings

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

	// ========== NAS 系统运行指标监控 ==========
	var systemInfo by mutableStateOf<NasSystemInfo?>(null)
		private set
	var isRefreshingSystem by mutableStateOf(false)
		private set

	fun refreshSystemInfo() {
		viewModelScope.launch(Dispatchers.IO) {
			if (isRefreshingSystem) return@launch
			isRefreshingSystem = true
			try {
				val info = client?.fetchSystemInfo()
				if (info != null) {
					withContext(Dispatchers.Main) {
						systemInfo = info
					}
				}
			} catch (_: Exception) {
			} finally {
				withContext(Dispatchers.Main) {
					isRefreshingSystem = false
				}
			}
		}
	}

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
	private val finishedStates = setOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)
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
	fun repairThumbnailAsync(entry: DavEntry) {
		// 严禁客户端后台静默拉取几十MB原图自愈，避免跑满带宽
	}

	private fun prefetchThumbsAsync(path: String, items: List<DavEntry>) {
		viewModelScope.launch(workerDispatcher) {
			val s = app.prefs.current()
			val c = clientOf(s)
			c.prefetchThumbnails(path, items)
		}
	}

	val isRoot: Boolean get() = path.isEmpty()
	val needsLogin: Boolean get() = (connectionMode != ConnectionMode.Direct && !app.repo.isDirectConfirmed) && linkStatus.contains("等待登录")

	/** 经过分类筛选与搜索后的条目列表（全部文件只显示当前文件夹内容，不做跨目录透视） */
	val filteredEntries: List<DavEntry>
		get() {
			val baseList = when (selectedCategory) {
				FileCategory.All -> entries
				FileCategory.Image -> entries.filter { !it.isDir && it.isImage }
				FileCategory.Video -> entries.filter { !it.isDir && it.isVideo }
				FileCategory.Document -> entries.filter { !it.isDir && it.isDocument }
				FileCategory.Audio -> entries.filter { !it.isDir && it.isAudio }
				FileCategory.Archive -> entries.filter { !it.isDir && it.isArchive }
			}

			val raw = if (searchQuery.isBlank()) baseList else baseList.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }

			// 文件夹恒定置顶，内部按所选排序维度排序
			return when (sortOrder) {
				SortOrder.UploadDesc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenByDescending { it.modified }
				)
				SortOrder.UploadAsc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenBy { it.modified }
				)
				SortOrder.CreatedDesc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenByDescending { it.effectiveDate }
				)
				SortOrder.CreatedAsc -> raw.sortedWith(
					compareBy<DavEntry> { !it.isDir }
						.thenBy { it.effectiveDate }
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
			MainTab.Home -> (allPhotos + allVideos).distinctBy { it.path }
			MainTab.Files -> filteredEntries
			MainTab.Photos -> currentAlbumPhotos
			MainTab.Media -> currentMediaVideos
			MainTab.Settings, MainTab.Me -> emptyList()
		}

	val photoTimelineGroups: List<TimelineGroup>
		get() = groupEntriesForTimeline(currentAlbumPhotos)

	val mediaTimelineGroups: List<TimelineGroup>
		get() = groupEntriesForTimeline(currentMediaVideos)

	fun loadMediaFromLocalDb() {
		val all = PhotoMetadataStore.getAllEntries()
		if (all.isEmpty()) return
		val photos = all.filter { it.isImage }
		val videos = all.filter { it.isVideo }
		val albumDirs = all.map { it.path.substringBeforeLast('/', "") }
			.filter { it.isNotBlank() }
			.distinct()
			.map { dirPath ->
				val dirName = dirPath.substringAfterLast('/')
				DavEntry(name = dirName, path = dirPath, isDir = true, modified = 0L)
			}
		allPhotos = (allPhotos + photos).distinctBy { it.path }
		allVideos = (allVideos + videos).distinctBy { it.path }
		if (albumDirs.isNotEmpty()) {
			val defaultAlbum = DavEntry(name = "默认相册", path = "Photos/默认相册", isDir = true, modified = 0L)
			val combined = (listOf(defaultAlbum) + allAlbumFolders + albumDirs).filterNot {
				it.name.startsWith(".") || it.path.contains(".thumbnails") ||
					it.name == "默认影集" || it.name == "默认影音" ||
					it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
					it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
			}.distinctBy { it.path }
			allAlbumFolders = sortAlbums(combined, albumOrder)
		}
	}

	init {
		PhotoMetadataStore.init(app)
		// 构造瞬间同步读取本地 SQLite，0ms 秒开无任何等待
		loadMediaFromLocalDb()
		loadUserPreferences()
		open("")
		viewModelScope.launch(Dispatchers.IO) {
			// 后台异步递归扫描远端相册目录中的伴生记忆隐藏文件，自动同步元数据入库
			val conf = app.prefs.current()
			val c = clientOf(conf)
			val queue = ArrayDeque<String>()
			queue.add("")
			queue.add("Photos")
			val visited = mutableSetOf<String>()
			while (queue.isNotEmpty() && visited.size < 30) {
				val cur = queue.removeFirst()
				if (!visited.add(cur)) continue
				runCatching {
					val list = c.list(cur)
					withContext(Dispatchers.Main) {
						applyDirectoryListing(cur, list)
					}
					for (e in list) {
						if (e.isDir && !e.name.startsWith(".") && !visited.contains(e.path.trim('/'))) {
							queue.add(e.path.trim('/'))
						}
					}
				}
			}
		}
		viewModelScope.launch {
			snapshotFlow { PhotoMetadataStore.revision }.collect {
				loadMediaFromLocalDb()
			}
		}
		startUnifiedLinkMonitor()
		cleanupOldCache()
		viewModelScope.launch {
			refreshSystemInfo()
		}
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
						lastUpdated = up.createdAt,
					)
				}
				syncTransferTasks()
				val owned = list.filter { it.ownerKey.isBlank() || it.ownerKey == configuration.serverIdentity }
				val changedDirs = uploadListing.update(owned)
				changedDirs.forEach { renderUploadedDirectory(it) }
				val finished = owned.filter { it.done && completedIds.add(it.id) }
				if (finished.isNotEmpty()) {
					queueUploadRefresh(finished.filter { !it.listed && it.listingActive }.map { it.dir })
					val targetGroupId = pendingAppendGroupId
					if (!targetGroupId.isNullOrBlank()) {
						val okPaths = finished.filter { it.error == null && !it.cancelled }.map { join(it.dir, it.name) }
						if (okPaths.isNotEmpty()) {
							appendToPhotoGroup(targetGroupId, okPaths)
						}
						if (owned.none { !it.done }) {
							pendingAppendGroupId = null
						}
					}
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
		// 若上一批次任务已全部完成，重新选上传时主动清理已结束任务，防止叠加旧批次进度百分比
		if (uploadTasks.isNotEmpty() && uploadTasks.all { it.status in finishedStates }) {
			Uploads.clearFinished()
			transferTasks = transferTasks.filterNot { it.isUpload && it.status in finishedStates }
		}
		prepareUploads(PendingUpload(uris, dir.trim('/')) { onStarted() })
	}

	fun startShareUploads(uris: List<Uri>, dir: String) {
		if (uploadTasks.isNotEmpty() && uploadTasks.all { it.status in finishedStates }) {
			Uploads.clearFinished()
			transferTasks = transferTasks.filterNot { it.isUpload && it.status in finishedStates }
		}
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
		val cleanDir = dir.trim('/')
		val visible = mergeUploadedEntries(dir, dirCache[dir].orEmpty())
		if (path.trim('/') == cleanDir) entries = visible
		allPhotos = (allPhotos.filterNot { parentOf(it.path) == cleanDir || it.path.contains(".thumbnails") } + visible.filter { !it.isDir && it.isImage && !it.path.contains(".thumbnails") }).distinctBy { it.path }
		allVideos = (allVideos.filterNot { parentOf(it.path) == cleanDir || it.path.contains(".thumbnails") } + visible.filter { !it.isDir && it.isVideo && !it.path.contains(".thumbnails") }).distinctBy { it.path }
		allDocuments = (allDocuments.filterNot { parentOf(it.path) == cleanDir } + visible.filter { !it.isDir && it.isDocument }).distinctBy { it.path }
		if (memoryYearGroups.isEmpty() && (allPhotos.isNotEmpty() || allVideos.isNotEmpty())) {
			loadMemories()
		}
	}

	/** 保存服务器原始列表，再合并仍未被目录确认的上传项；所有 UI 状态在主线程更新。 */
	private fun applyDirectoryListing(dir: String, fresh: List<DavEntry>) {
		val cleanDir = dir.trim('/')

		// 过滤所有点开头目录/隐藏文件，不让其干扰主目录浏览
		val filteredFresh = fresh.filterNot { it.name.startsWith(".") }

		// 差量对齐：比对远端真实文件，清理已被电脑端删除的孤儿元数据记录（后台 IO 自动执行）
		PhotoMetadataStore.reconcile(cleanDir, filteredFresh)

		// 注入并同步拍摄时间（takenAt）元数据与伴生资产（全部来自统一新存储与 SQLite）
		val toPersist = mutableListOf<PhotoMetadata>()
		val resolvedFresh = filteredFresh.map { entry ->
			if (entry.isImage || entry.isVideo) {
				val meta = PhotoMetadataStore.get(entry.path)
				val takenAt = if (meta != null && meta.takenAt > 0L && meta.size == entry.size && meta.modified == entry.modified) {
					meta.takenAt
				} else {
					val parsed = PhotoTimeParser.parseFromFilename(entry.name) ?: if (entry.created > 0L) entry.created else entry.modified
					val newMeta = PhotoMetadata(
						path = entry.path,
						name = entry.name,
						dir = cleanDir,
						size = entry.size,
						modified = entry.modified,
						takenAt = parsed,
						hasThumbnail = meta?.hasThumbnail == true,
						thumbId = meta?.thumbId ?: "",
						voiceId = meta?.voiceId ?: "",
						audioPath = meta?.audioPath,
						audioDurationMs = meta?.audioDurationMs ?: 0L,
						memo = meta?.memo,
						memoUpdatedAt = meta?.memoUpdatedAt ?: 0L,
						lat = meta?.lat ?: 0.0,
						lng = meta?.lng ?: 0.0,
						locationName = meta?.locationName ?: "",
						mediaType = if (entry.isVideo) 1 else 0,
					)
					toPersist.add(newMeta)
					PhotoMetadataStore.putMemoryOnly(newMeta)
					parsed
				}
				entry.copy(takenAt = takenAt)
			} else {
				val parsed = PhotoTimeParser.parseFromFilename(entry.name)
				if (parsed != null && parsed > 0L) {
					entry.copy(takenAt = parsed)
				} else {
					entry
				}
			}
		}

		if (toPersist.isNotEmpty()) {
			PhotoMetadataStore.putBatchAsync(toPersist)
		}

		dirCache[dir] = resolvedFresh
		Uploads.markListed(uploadListing.reconcile(dir, resolvedFresh.associate { it.path to it.size }))
		renderUploadedDirectory(dir)
		updateFolderPaths(resolvedFresh)
		prefetchThumbsAsync(dir, resolvedFresh)
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
		if (currentTab == MainTab.Photos) {
			refreshPhotoGroups()
		}
		if (mediaScanning && !force) return
		if (hasScannedMedia && !force && !mediaDirty) return
		mediaScanJob?.cancel()
		val version = ++mediaScanVersion
		mediaDirty = false
		val hasLocalMedia = allPhotos.isNotEmpty() || allVideos.isNotEmpty()
		if (!hasLocalMedia) {
			mediaScanning = true
		}
		mediaError = null
		mediaScanJob = viewModelScope.launch {
			try {
				scanGlobalMedia()
				loadMemories()
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

	/**
	 * 获取伴随录音的最佳播放地址（优先本地零延迟缓存，兜底 WebDAV 流式直链）
	 */
	fun audioUrlFor(companionAudioPath: String): String? {
		val clean = companionAudioPath.trim('/')
		if (clean.isEmpty()) return null
		val localFile = File(File(app.cacheDir, "audio_cache"), clean.replace('/', '_'))
		if (localFile.exists() && localFile.length() > 0) {
			return localFile.absolutePath
		}
		return directUrlFor(clean)
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

		// 自定义图标文件（存放在 .nasdrop_icons 目录）服务端 /api/thumbnail 不予支持，直接返回 WebDAV 直链
		if (key.startsWith(".nasdrop_icons") || key.contains(".nasdrop_icons")) {
			return directUrlFor(key) ?: urlFor(key)
		}

		// 1. 仅命中 NAS 根目录公共缩略图库 .nasdrop_thumbs/<thumb_id>.jpg（禁止服务端解码与旧式同级 .thumbnails 回退）
		val meta = PhotoMetadataStore.get(key)
		if (meta != null && meta.thumbId.isNotBlank()) {
			val dUrl = client?.directUrlFor(".nasdrop_thumbs/${meta.thumbId}.jpg")?.toString()
			if (dUrl != null) return dUrl
		}
		// 2. 若当前图片/视频还没有公共 thumb_id，由 App 端异步补齐并上传至 .nasdrop_thumbs
		scheduleAppThumbBackfill(key)
		return null
	}

	/**
	 * 看图器垫底层专用：本地小图 > 公共缩略图，没有就返回 null。
	 */
	fun serverThumbUrlFor(entryPath: String): String? {
		@Suppress("UNUSED_VARIABLE") val localRev = LocalThumbs.revision
		val key = entryPath.trim('/')
		LocalThumbs.get(key)?.let { return android.net.Uri.fromFile(it).toString() }
		@Suppress("UNUSED_VARIABLE") val revision = thumbRevision

		val meta = PhotoMetadataStore.get(key)
		if (meta != null && meta.thumbId.isNotBlank()) {
			val dUrl = client?.directUrlFor(".nasdrop_thumbs/${meta.thumbId}.jpg")?.toString()
			if (dUrl != null) return dUrl
		}
		return null
	}

	private val backfillingThumbs = java.util.Collections.synchronizedSet(HashSet<String>())
	private val backfillThumbSemaphore = Semaphore(2)

	/** App 端自主补齐缩略图并上传至 NAS 根目录 .nasdrop_thumbs/<thumb_id>.jpg（禁止服务端解码） */
	private fun scheduleAppThumbBackfill(entryPath: String) {
		val key = entryPath.trim('/')
		val ext = key.substringAfterLast('.', "").lowercase()
		val isImage = ext in DavEntry.IMAGES
		val isVideo = ext in DavEntry.VIDEOS
		if (!isImage && !isVideo) return
		if (!backfillingThumbs.add(key)) return

		viewModelScope.launch(Dispatchers.IO) {
			try {
				backfillThumbSemaphore.withPermit {
					val s = app.prefs.current()
					val c = clientOf(s)
					val thumbBytes: ByteArray? = if (isImage) {
						val url = c.directUrlFor(key) ?: c.urlFor(key)
						val req = okhttp3.Request.Builder().url(url).get().build()
						c.metaCall(req).execute().use { resp ->
							if (!resp.isSuccessful) return@use null
							val raw = resp.body?.bytes() ?: return@use null
							val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
							android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
							var sample = 1
							val maxOrig = maxOf(opts.outWidth, opts.outHeight)
							while (maxOrig / sample > 720) sample *= 2
							val decOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
							val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, decOpts) ?: return@use null
							val scale = (360f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)).coerceAtMost(1f)
							val scaled = if (scale < 1f) {
								android.graphics.Bitmap.createScaledBitmap(
									bmp,
									(bmp.width * scale).toInt().coerceAtLeast(1),
									(bmp.height * scale).toInt().coerceAtLeast(1),
									true,
								).also { if (it != bmp) bmp.recycle() }
							} else bmp
							val baos = java.io.ByteArrayOutputStream()
							scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
							scaled.recycle()
							baos.toByteArray()
						}
					} else {
						val url = c.directUrlFor(key) ?: c.urlFor(key)
						val tmpFile = File(app.cacheDir, "vthumb_${key.hashCode().toUInt().toString(16)}.tmp")
						val retriever = android.media.MediaMetadataRetriever()
						try {
							val req = okhttp3.Request.Builder().url(url).get().build()
							val ok = c.metaCall(req).execute().use { resp ->
								if (!resp.isSuccessful) return@use false
								val body = resp.body ?: return@use false
								tmpFile.outputStream().use { out -> body.byteStream().copyTo(out) }
								tmpFile.length() > 0L
							}
							val frame = if (ok) {
								retriever.setDataSource(tmpFile.absolutePath)
								retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
									?: retriever.getFrameAtTime(0L)
							} else null
							if (frame != null) {
								val scale = (360f / maxOf(frame.width, frame.height).coerceAtLeast(1)).coerceAtMost(1f)
								val scaled = if (scale < 1f) {
									android.graphics.Bitmap.createScaledBitmap(
										frame,
										(frame.width * scale).toInt().coerceAtLeast(1),
										(frame.height * scale).toInt().coerceAtLeast(1),
										true,
									).also { if (it != frame) frame.recycle() }
								} else frame
								val baos = java.io.ByteArrayOutputStream()
								scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, baos)
								scaled.recycle()
								baos.toByteArray()
							} else null
						} finally {
							runCatching { retriever.release() }
							runCatching { tmpFile.delete() }
						}
					}

					if (thumbBytes != null && thumbBytes.isNotEmpty()) {
						LocalThumbs.put(app, key, thumbBytes)
						val thumbId = "t_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "").take(8)}"
						runCatching { c.mkcol(".nasdrop_thumbs") }
						c.uploadBytes(thumbBytes, ".nasdrop_thumbs", "$thumbId.jpg")

						val existing = PhotoMetadataStore.get(key)
						val updatedMeta = if (existing != null) {
							existing.copy(hasThumbnail = true, thumbId = thumbId, updatedAt = System.currentTimeMillis())
						} else {
							val entry = (allPhotos + allVideos).firstOrNull { it.path.trim('/') == key }
							PhotoMetadata(
								path = key,
								name = key.substringAfterLast('/'),
								dir = parentOf(key),
								size = entry?.size ?: 0L,
								modified = entry?.modified ?: System.currentTimeMillis(),
								takenAt = entry?.takenAt ?: 0L,
								hasThumbnail = true,
								thumbId = thumbId,
							)
						}
						PhotoMetadataStore.put(updatedMeta)
						c.reportMetadata(listOf(updatedMeta))
						withContext(Dispatchers.Main) {
							thumbRevision++
						}
					}
				}
			} catch (e: Exception) {
				backfillingThumbs.remove(key)
				Log.w("BrowserViewModel", "App 端补齐缩略图失败 path=$key: ${e.message}")
			}
		}
	}

	fun markThumbnailFailed(entryPath: String) {
		val key = entryPath.trim('/')
		PhotoMetadataStore.get(key)?.let { meta ->
			if (meta.hasThumbnail || meta.thumbId.isNotBlank()) {
				PhotoMetadataStore.putMemoryOnly(meta.copy(hasThumbnail = false, thumbId = ""))
			}
		}
		client?.markThumbnailFailed(entryPath)
		scheduleAppThumbBackfill(key)
		thumbRevision++
	}

	/** 记录进入大图查看器时的初始图片，退出时若未翻页则保持原位置绝对不动 */
	var viewerInitialEntry: DavEntry? = null

	/** 看图器退出且翻页到新图片时，平滑对齐当前浏览的照片 */
	fun syncViewerPosition(entry: DavEntry) {
		viewModelScope.launch {
			kotlinx.coroutines.delay(60)
			if (currentTab == MainTab.Photos) {
				var targetIndex = 0
				for (g in photoTimelineGroups) {
					targetIndex++ // 标题项占 1 个 span
					val itemIdx = g.items.indexOfFirst { it.path == entry.path }
					if (itemIdx != -1) {
						targetIndex += itemIdx
						val scrollPos = (targetIndex - 3).coerceAtLeast(0)
						photoGridState.scrollToItem(scrollPos)
						break
					} else {
						targetIndex += g.items.size
					}
				}
			} else if (currentTab == MainTab.Files) {
				val idx = filteredEntries.indexOfFirst { it.path == entry.path }
				if (idx != -1) {
					when (viewMode) {
						ViewMode.Cards -> fileListState.scrollToItem((idx - 1).coerceAtLeast(0))
						ViewMode.Compact -> fileCompactState.scrollToItem((idx - 2).coerceAtLeast(0))
						ViewMode.Grid -> fileGridState.scrollToItem((idx - 3).coerceAtLeast(0))
					}
				}
			}
		}
	}

	fun loginUrl(): String = authUrl

	fun logs(): String = if (TsBridge.available) TsBridge.logs() else ""

	fun selectCategory(cat: FileCategory) {
		selectedCategory = cat
		if (cat == FileCategory.Image || cat == FileCategory.Video || cat == FileCategory.Document || cat == FileCategory.All) {
			ensureMediaScanned()
		}
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
					val so = when (savedSort) {
						"ModifiedDesc" -> SortOrder.UploadDesc
						"ModifiedAsc" -> SortOrder.UploadAsc
						else -> SortOrder.valueOf(savedSort)
					}
					withContext(Dispatchers.Main) { sortOrder = so }
				}
			}
			runCatching {
				val icons = NasDb.getInstance(app).getAllFolderIcons()
				withContext(Dispatchers.Main) {
					folderIcons = icons
				}
			}
			runCatching {
				val savedAlbumOrder = app.prefs.getAlbumOrder()
				val savedMediaOrder = app.prefs.getMediaOrder()
				withContext(Dispatchers.Main) {
					if (savedAlbumOrder.isNotEmpty()) {
						albumOrder = savedAlbumOrder
						if (allAlbumFolders.isNotEmpty()) {
							allAlbumFolders = sortAlbums(allAlbumFolders, savedAlbumOrder)
						}
					}
					if (savedMediaOrder.isNotEmpty()) {
						mediaOrder = savedMediaOrder
						if (allMediaFolders.isNotEmpty()) {
							allMediaFolders = sortAlbums(allMediaFolders, savedMediaOrder)
						}
					}
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
			SortOrder.UploadDesc -> SortOrder.UploadAsc
			SortOrder.UploadAsc -> SortOrder.CreatedDesc
			SortOrder.CreatedDesc -> SortOrder.CreatedAsc
			SortOrder.CreatedAsc -> SortOrder.NameAsc
			SortOrder.NameAsc -> SortOrder.SizeDesc
			SortOrder.SizeDesc -> SortOrder.UploadDesc
		}
		viewModelScope.launch { app.prefs.setSortOrder(sortOrder.name) }
	}

	fun updateSortOrder(order: SortOrder) {
		sortOrder = order
		viewModelScope.launch { app.prefs.setSortOrder(order.name) }
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
	 * 将远端原视频保存到本地系统相册 (Movies/NasDrop) 并通知系统刷新
	 */
	fun saveVideoToGallery(entry: DavEntry, onResult: (Boolean, String) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val cli = client ?: return@launch withContext(Dispatchers.Main) {
					onResult(false, "未连接服务器")
				}
				val moviesDir = android.os.Environment.getExternalStoragePublicDirectory(
					android.os.Environment.DIRECTORY_MOVIES
				).resolve("NasDrop")
				if (!moviesDir.exists()) moviesDir.mkdirs()

				val destFile = moviesDir.resolve(entry.name)
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
						onResult(true, "已保存至本地视频 Movies/NasDrop")
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
		val groupPhotos = activePhotoGroupId?.let { gid ->
			val group = photoGroups.firstOrNull { it.id == gid }
			if (group != null) {
				val photoMap = allPhotos.associateBy { it.path }
				group.items.mapNotNull { p -> photoMap[p] }.filter { it.isImage }
			} else null
		}?.takeIf { it.any { item -> item.path == entry.path } }

		val list = when {
			groupPhotos != null -> groupPhotos
			currentTab == MainTab.Photos -> {
				val timelineItems = photoTimelineGroups.flatMap { it.items }.filter { it.isImage }
				if (timelineItems.any { it.path == entry.path }) timelineItems else allPhotos.filter { it.isImage }
			}
			filteredEntries.any { it.path == entry.path } -> {
				filteredEntries.filter { !it.isDir && it.isImage }
			}
			allPhotos.any { it.path == entry.path } -> {
				allPhotos.filter { it.isImage }
			}
			else -> listOf(entry)
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
		val targets = selectedEntries.toList().filterNot { isProtectedSystemDir(it) }
		if (targets.isEmpty()) {
			if (selectedEntries.isNotEmpty()) {
				uiMessage = "系统相册根目录不允许删除"
				exitMultiSelect()
			}
			return
		}
		val targetPaths = targets.map { it.path }.toSet()
		fun hit(p: String) = targetPaths.any { p == it || p.startsWith("$it/") }

		// 乐观即时移除，秒级清爽
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allDocuments = allDocuments.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
		exitMultiSelect()

		viewModelScope.launch {
			val okCount = java.util.concurrent.atomic.AtomicInteger(0)
			val failCount = java.util.concurrent.atomic.AtomicInteger(0)
			val deleted = java.util.Collections.synchronizedList(mutableListOf<String>())
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = clientOf(s)
				val sem = Semaphore(6)
				coroutineScope {
					targets.map { item ->
						async {
							sem.withPermit {
								try {
									val meta = PhotoMetadataStore.get(item.path)
									c.delete(item.path, item.isDir)
									if (meta != null && meta.voiceId.isNotBlank()) {
										runCatching { c.delete(".nasdrop_voices/${meta.voiceId}.m4a", false) }
									}
									if (meta != null && meta.thumbId.isNotBlank()) {
										runCatching { c.delete(".nasdrop_thumbs/${meta.thumbId}.jpg", false) }
									}
									okCount.incrementAndGet()
									deleted += item.path
								} catch (e: Exception) {
									if (e is CancellationException) throw e
									Log.w("BrowserViewModel", "批量删除失败 path=${item.path}", e)
									failCount.incrementAndGet()
								}
							}
						}
					}.awaitAll()
				}
			}
			dirCache.clear()
			val deletedSnap = deleted.toList()
			LocalThumbs.forget(*deletedSnap.toTypedArray())
			val db = NasDb.getInstance(app)
			deletedSnap.forEach { p ->
				PhotoMetadataStore.delete(p)
				db.clearVideoProgress(p)
				db.clearIconIfMatching(p)
			}
			folderIcons = db.getAllFolderIcons()
			pruneMediaPools(deletedSnap)
			val ok = okCount.get()
			val fail = failCount.get()
			onDone(ok, fail)
			if (fail > 0) uiMessage = "删除完成：成功 $ok 项，失败 $fail 项"
			refreshSilent()
		}
	}

	/** 批量移动仅撤销成功项的旧路径，失败项仍可见。 */
	fun batchMove(targetDir: String, onDone: (Int, Int) -> Unit = { _, _ -> }) {
		val targets = selectedEntries.toList().filterNot { isProtectedSystemDir(it) }
		if (targets.isEmpty()) {
			if (selectedEntries.isNotEmpty()) {
				uiMessage = "系统相册根目录不允许移动"
				exitMultiSelect()
			}
			return
		}
		val paths = targets.map { it.path }
		val pathSet = paths.toSet()
		fun hit(p: String) = pathSet.any { p == it || p.startsWith("$it/") }
		// 乐观：先从列表移除，避免"批量移动完还挂在原地"
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allDocuments = allDocuments.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
		exitMultiSelect()
		viewModelScope.launch {
			val okCount = java.util.concurrent.atomic.AtomicInteger(0)
			val failCount = java.util.concurrent.atomic.AtomicInteger(0)
			val moved = java.util.Collections.synchronizedList(mutableListOf<String>())
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				val c = clientOf(s)
				val sem = Semaphore(6)
				coroutineScope {
					targets.map { item ->
						async {
							sem.withPermit {
								try {
									c.move(item.path, join(targetDir, item.name))
									okCount.incrementAndGet()
									moved += item.path
								} catch (e: Exception) {
									if (e is CancellationException) throw e
									Log.w("BrowserViewModel", "批量移动失败 path=${item.path} targetDir=$targetDir", e)
									failCount.incrementAndGet()
								}
							}
						}
					}.awaitAll()
				}
			}
			dirCache.clear()
			val movedSnap = moved.toList()
			val movedMetas = mutableListOf<PhotoMetadata>()
			movedSnap.forEach { p ->
				val dest = join(targetDir, p.substringAfterLast('/'))
				LocalThumbs.move(app, p, dest)
				PhotoMetadataStore.move(p, dest)
				PhotoMetadataStore.get(dest)?.let { movedMetas.add(it) }
			}
			if (movedMetas.isNotEmpty()) {
				withContext(Dispatchers.IO) {
					runCatching { clientOf(s).reportMetadata(movedMetas) }
				}
			}
			pruneMediaPools(movedSnap)
			mediaDirty = true
			runCatching { loadDirectory(targetDir) }
			refreshSilent()
			val ok = okCount.get()
			val fail = failCount.get()
			if (fail > 0) uiMessage = "移动完成：成功 $ok 项，失败 $fail 项"
			onDone(ok, fail)
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
						if (currentTab == MainTab.Photos) {
							refreshPhotoGroups()
						}
						if (selected.isNotBlank() && selected != "__ALL_FOLDERS__") {
							try {
								withContext(Dispatchers.IO) {
									clientOf(app.prefs.current()).syncServerDir(selected)
								}
								loadTimelinePage(dir = selected, reset = true)
								mediaError = null
							}
							catch (e: Exception) {
								if (e is CancellationException) throw e
								mediaError = e.message ?: "媒体刷新失败"
								Log.w("BrowserViewModel", "媒体刷新失败 dir=$selected", e)
							}
						} else {
							withContext(Dispatchers.IO) {
								clientOf(app.prefs.current()).syncServerDir("Photos")
							}
							ensureMediaScanned(force = true)
							withTimeoutOrNull(20_000L) {
								mediaScanJob?.join()
							}
						}
					} else {
						if (currentTab == MainTab.Home) {
							refreshSystemInfo()
						}
						doRefresh()
					}
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

	/** 更新文件夹自定义图标并持久化至 SQLite 数据库，并同步至远端配置 */
	fun updateFolderIcon(entry: DavEntry, icon: NasFolderIcon) {
		updateFolderCustomIcon(entry, icon.key)
	}

	fun updateFolderCustomIcon(entry: DavEntry, iconKey: String) {
		val cleanPath = entry.path.trim('/')
		viewModelScope.launch(Dispatchers.IO) {
			NasDb.getInstance(app).setFolderIcon(cleanPath, iconKey)
			withContext(Dispatchers.Main) {
				folderIcons = if (iconKey == "default" || iconKey.isEmpty()) {
					folderIcons - cleanPath
				} else {
					folderIcons + (cleanPath to iconKey)
				}
			}
			syncRemoteMetadata()
		}
	}

	fun uploadCustomFolderIcon(entry: DavEntry, uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val cli = client ?: return@launch withContext(Dispatchers.Main) { onResult(false, "未连接服务器") }
				val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
					?: return@launch withContext(Dispatchers.Main) { onResult(false, "读取图片失败") }
				val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
					?: return@launch withContext(Dispatchers.Main) { onResult(false, "解码图片失败") }
				val scaled = if (bmp.width > 256 || bmp.height > 256) {
					android.graphics.Bitmap.createScaledBitmap(bmp, 256, 256, true)
				} else bmp
				val baos = java.io.ByteArrayOutputStream()
				scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, baos)
				val pngBytes = baos.toByteArray()
				val hash = Math.abs(entry.path.hashCode()).toString(16)
				val fileName = "icon_${hash}.png"
				val remotePath = cli.uploadCustomIcon(fileName, pngBytes)
				if (remotePath != null) {
					updateFolderCustomIcon(entry, remotePath)
					withContext(Dispatchers.Main) { onResult(true, "图标上传成功") }
				} else {
					withContext(Dispatchers.Main) { onResult(false, "上传图标到远端失败") }
				}
			} catch (e: Exception) {
				withContext(Dispatchers.Main) { onResult(false, "处理失败: ${e.message}") }
			}
		}
	}

	fun reorderAlbums(fromPath: String, toPath: String) {
		if (fromPath == toPath) return
		val mutable = allAlbumFolders.toMutableList()
		val fromIdx = mutable.indexOfFirst { it.path == fromPath }
		val toIdx = mutable.indexOfFirst { it.path == toPath }
		if (fromIdx == -1 || toIdx == -1) return
		val item = mutable.removeAt(fromIdx)
		mutable.add(toIdx, item)
		allAlbumFolders = mutable
		albumOrder = mutable.map { it.path }
		viewModelScope.launch(Dispatchers.IO) { app.prefs.setAlbumOrder(albumOrder) }
		syncRemoteMetadata()
	}

	fun reorderAlbums(fromIndex: Int, toIndex: Int) {
		if (fromIndex !in allAlbumFolders.indices || toIndex !in allAlbumFolders.indices || fromIndex == toIndex) return
		val fromPath = allAlbumFolders.getOrNull(fromIndex)?.path ?: return
		val toPath = allAlbumFolders.getOrNull(toIndex)?.path ?: return
		reorderAlbums(fromPath, toPath)
	}

	fun reorderMediaFolders(fromPath: String, toPath: String) {
		if (fromPath == toPath) return
		val mutable = allMediaFolders.toMutableList()
		val fromIdx = mutable.indexOfFirst { it.path == fromPath }
		val toIdx = mutable.indexOfFirst { it.path == toPath }
		if (fromIdx == -1 || toIdx == -1) return
		val item = mutable.removeAt(fromIdx)
		mutable.add(toIdx, item)
		allMediaFolders = mutable
		mediaOrder = mutable.map { it.path }
		viewModelScope.launch(Dispatchers.IO) { app.prefs.setMediaOrder(mediaOrder) }
		syncRemoteMetadata()
	}

	fun reorderMediaFolders(fromIndex: Int, toIndex: Int) {
		if (fromIndex !in allMediaFolders.indices || toIndex !in allMediaFolders.indices || fromIndex == toIndex) return
		val fromPath = allMediaFolders.getOrNull(fromIndex)?.path ?: return
		val toPath = allMediaFolders.getOrNull(toIndex)?.path ?: return
		reorderMediaFolders(fromPath, toPath)
	}

	fun loadMemories() {
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val cal = Calendar.getInstance()
				val currentYear = cal.get(Calendar.YEAR)
				val currentMonth = cal.get(Calendar.MONTH) + 1
				val currentDay = cal.get(Calendar.DAY_OF_MONTH)
				val monthDayStr = String.format(Locale.US, "%02d-%02d", currentMonth, currentDay)

				// 1. 尝试从服务端商用级 /api/memories 接口秒级拉取（零开销）
				val serverItems = runCatching {
					client?.fetchServerMemories(monthDayStr, limit = 500)
				}.getOrNull()

				val rawItems = if (!serverItems.isNullOrEmpty()) {
					serverItems
				} else {
					// 2. 本地兜底：从内存中的全部照片与视频池中过滤历史同月同日内容
					val pool = (allPhotos + allVideos).filterNot {
						it.name.startsWith(".") || it.path.contains(".thumbnails")
					}.distinctBy { it.path }

					val itemCal = Calendar.getInstance()
					pool.filter { entry ->
						val dateMs = entry.effectiveDate
						if (dateMs <= 0L) false
						else {
							itemCal.timeInMillis = dateMs
							val y = itemCal.get(Calendar.YEAR)
							val m = itemCal.get(Calendar.MONTH) + 1
							val d = itemCal.get(Calendar.DAY_OF_MONTH)
							// 严格匹配历史同月同日（闰年 2月29日平年归入 2月28日）
							val isSameDay = (m == currentMonth && d == currentDay) ||
								(currentMonth == 2 && currentDay == 28 && m == 2 && d == 29)
							isSameDay && y < currentYear
						}
					}
				}

				if (rawItems.isEmpty()) {
					withContext(Dispatchers.Main) {
						memoryYearGroups = emptyList()
					}
					return@launch
				}

				val itemCal = Calendar.getInstance()
				val byYear = rawItems.groupBy { entry ->
					itemCal.timeInMillis = entry.effectiveDate
					itemCal.get(Calendar.YEAR)
				}.filterKeys { it < currentYear }

				val yearGroups = byYear.entries.sortedByDescending { it.key }.map { (year, yearItems) ->
					val yearsAgo = currentYear - year
					val photos = yearItems.filter { it.isImage }
					val videos = yearItems.filter { it.isVideo }

					// 按相册目录结构归类
					val byAlbum = yearItems.groupBy { entry -> parentOf(entry.path) }
					val albumGroups = byAlbum.entries.map { (albumPath, items) ->
						val albumName = when {
							albumPath.isEmpty() -> "根目录"
							albumPath == "Photos/默认相册" || albumPath.endsWith("/默认相册") -> "默认相册"
							else -> albumPath.substringAfterLast('/')
						}
						MemoryAlbumGroup(
							albumName = albumName,
							albumPath = albumPath,
							items = items.sortedByDescending { it.effectiveDate }
						)
					}.sortedWith(
						compareBy<MemoryAlbumGroup> { if (it.albumName == "默认相册") 1 else 0 }
							.thenByDescending { it.items.size }
					)

					val cover = photos.maxByOrNull { it.effectiveDate } ?: yearItems.first()

					MemoryYearGroup(
						year = year,
						yearsAgo = yearsAgo,
						totalCount = yearItems.size,
						photoCount = photos.size,
						videoCount = videos.size,
						coverEntry = cover,
						albumGroups = albumGroups,
					)
				}

				withContext(Dispatchers.Main) {
					memoryYearGroups = yearGroups
				}
			} catch (e: Exception) {
				Log.w("BrowserViewModel", "加载那年今天异常", e)
			}
		}
	}

	fun syncRemoteMetadata() {
		viewModelScope.launch(Dispatchers.IO) {
			val cli = client ?: return@launch
			val meta = RemoteMetadata(
				folderIcons = folderIcons,
				albumOrder = albumOrder,
				mediaOrder = mediaOrder,
			)
			cli.saveRemoteMetadata(meta)
		}
	}

	// ==================== 相册集锦（直连服务端数据库） ====================

	/** 若服务端同步回来的集锦中包含本地 allPhotos 尚未收录的新照片，按其父目录定向补拉一次 */
	private fun backfillMissingGroupDirs(groups: List<PhotoGroup>) {
		val known = (allPhotos + allVideos).mapTo(HashSet()) { it.path.trim('/') }
		val missingDirs = groups.asSequence()
			.flatMap { it.items.asSequence() }
			.map { it.trim('/') }
			.filter { it.isNotEmpty() && !known.contains(it) }
			.map { parentOf(it) }
			.distinct()
			.take(6)
			.toList()
		if (missingDirs.isEmpty()) return
		viewModelScope.launch {
			missingDirs.forEach { dir ->
				runCatching { loadDirectory(dir) }
			}
		}
	}

	fun refreshPhotoGroups(albumDir: String = "") {
		viewModelScope.launch(Dispatchers.IO) {
			val list = runCatching { clientOf(app.prefs.current()).fetchPhotoGroups(albumDir) }.getOrNull() ?: return@launch
			withContext(Dispatchers.Main) {
				photoGroups = if (albumDir.isBlank()) list else {
					val clean = albumDir.trim('/')
					photoGroups.filterNot { it.albumDir == clean } + list
				}
				backfillMissingGroupDirs(list)
			}
		}
	}

	fun createPhotoGroup(
		albumDir: String,
		title: String,
		memo: String,
		selected: List<DavEntry>,
		onDone: (PhotoGroup?) -> Unit = {},
	) {
		val orderedPaths = selected.sortedBy { it.effectiveDate }.map { it.path.trim('/') }.distinct()
		if (orderedPaths.isEmpty()) {
			onDone(null)
			return
		}
		val distinctParents = orderedPaths.map { parentOf(it) }.distinct()
		if (distinctParents.size > 1) {
			uiMessage = "仅支持同一相册内的照片/视频创建集锦，不支持跨相册"
			onDone(null)
			return
		}
		val cleanAlbum = distinctParents.firstOrNull()?.ifBlank { albumDir.trim('/') } ?: albumDir.trim('/')
		viewModelScope.launch {
			val created = withContext(Dispatchers.IO) {
				runCatching {
					clientOf(app.prefs.current()).createPhotoGroup(
						albumDir = cleanAlbum,
						title = title.ifBlank { "照片集锦" },
						memo = memo.trim(),
						items = orderedPaths,
					)
				}.getOrNull()
			}
			if (created != null) {
				photoGroups = listOf(created) + photoGroups.filterNot { it.id == created.id }
				exitMultiSelect()
				uiMessage = "已创建集锦（${created.items.size} 张）"
			} else {
				uiMessage = "创建集锦失败，请检查网络或服务端状态"
			}
			onDone(created)
		}
	}

	fun updatePhotoGroup(
		id: String,
		title: String? = null,
		memo: String? = null,
		items: List<String>? = null,
		replaceItems: Boolean = false,
		onDone: (PhotoGroup?) -> Unit = {},
	) {
		val prevGroup = photoGroups.firstOrNull { it.id == id }
		// 乐观更新本地状态，保证拖拽调序/改备注零延迟
		photoGroups = photoGroups.map { g ->
			if (g.id == id) {
				g.copy(
					title = title ?: g.title,
					memo = memo ?: g.memo,
					items = if (replaceItems && items != null) items else g.items,
					updatedAt = System.currentTimeMillis(),
				)
			} else g
		}
		viewModelScope.launch {
			val updated = withContext(Dispatchers.IO) {
				runCatching {
					clientOf(app.prefs.current()).updatePhotoGroup(id, title, memo, items, replaceItems)
				}.getOrNull()
			}
			if (updated != null) {
				photoGroups = photoGroups.map { if (it.id == updated.id) updated else it }
			} else {
				if (prevGroup != null) {
					photoGroups = photoGroups.map { if (it.id == id) prevGroup else it }
				}
				uiMessage = "保存集锦修改失败，已恢复原状态"
			}
			onDone(updated)
		}
	}

	fun appendToPhotoGroup(groupId: String, paths: List<String>, onDone: (Boolean) -> Unit = {}) {
		val clean = paths.map { it.trim('/') }.filter { it.isNotEmpty() }
		if (clean.isEmpty()) {
			onDone(false)
			return
		}
		val prevGroup = photoGroups.firstOrNull { it.id == groupId }
		photoGroups = photoGroups.map { g ->
			if (g.id == groupId) g.copy(items = (g.items + clean).distinct(), updatedAt = System.currentTimeMillis())
			else g
		}
		viewModelScope.launch {
			val updated = withContext(Dispatchers.IO) {
				runCatching { clientOf(app.prefs.current()).appendPhotoGroupItems(groupId, clean) }.getOrNull()
			}
			if (updated != null) {
				photoGroups = photoGroups.map { if (it.id == updated.id) updated else it }
				uiMessage = "已追加 ${clean.size} 张照片到「${updated.title}」"
				onDone(true)
			} else {
				if (prevGroup != null) {
					photoGroups = photoGroups.map { if (it.id == groupId) prevGroup else it }
				}
				uiMessage = "追加到集锦失败，请稍后重试"
				onDone(false)
			}
		}
	}

	fun removeFromPhotoGroup(groupId: String, photoPath: String) {
		val clean = photoPath.trim('/')
		val prevGroup = photoGroups.firstOrNull { it.id == groupId }
		photoGroups = photoGroups.map { g ->
			if (g.id == groupId) g.copy(items = g.items.filterNot { it == clean }, updatedAt = System.currentTimeMillis())
			else g
		}
		viewModelScope.launch {
			val updated = withContext(Dispatchers.IO) {
				runCatching { clientOf(app.prefs.current()).removePhotoGroupItem(groupId, clean) }.getOrNull()
			}
			if (updated != null) {
				photoGroups = photoGroups.map { if (it.id == updated.id) updated else it }
				uiMessage = "已移出集锦（原照片已保留）"
			} else {
				if (prevGroup != null) {
					photoGroups = photoGroups.map { if (it.id == groupId) prevGroup else it }
				}
				uiMessage = "移出集锦失败，已恢复原状态"
			}
		}
	}

	fun deletePhotoGroup(groupId: String, onDone: (Boolean) -> Unit = {}) {
		val prev = photoGroups
		photoGroups = photoGroups.filterNot { it.id == groupId }
		viewModelScope.launch {
			val ok = withContext(Dispatchers.IO) {
				runCatching { clientOf(app.prefs.current()).deletePhotoGroup(groupId) }.getOrDefault(false)
			}
			if (ok) {
				uiMessage = "已解散集锦（原照片已保留）"
			} else {
				photoGroups = prev
				uiMessage = "解散集锦失败"
			}
			onDone(ok)
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

	private val albumPageOffsets = mutableMapOf<String, Int>()
	private val albumHasMoreMap = mutableMapOf<String, Boolean>()
	var timelineLoadingMore by mutableStateOf(false)
		private set

	fun openAlbum(albumPath: String) {
		if (selectedAlbumPath != albumPath) {
			selectedAlbumPath = albumPath
			viewModelScope.launch { runCatching { photoGridState.scrollToItem(0) } }
			if (albumPath.isNotBlank() && albumPath != "__ALL_FOLDERS__" && !albumPageOffsets.containsKey(albumPath)) {
				viewModelScope.launch { loadTimelinePage(dir = albumPath, reset = true) }
			}
		}
	}

	fun loadMoreTimelineIfNeeded() {
		if (currentTab != MainTab.Photos) return
		val dirKey = if (selectedAlbumPath == "__ALL_FOLDERS__") "" else selectedAlbumPath.trim('/')
		if (timelineLoadingMore) return
		if (albumHasMoreMap[dirKey] != true) return
		viewModelScope.launch {
			loadTimelinePage(dir = dirKey, reset = false)
		}
	}

	private suspend fun loadTimelinePage(dir: String, reset: Boolean) {
		val cleanDir = dir.trim('/')
		val offset = if (reset) 0 else (albumPageOffsets[cleanDir] ?: 0)
		if (!reset && timelineLoadingMore) return
		timelineLoadingMore = true
		try {
			val cfg = app.prefs.current()
			val page = withContext(Dispatchers.IO) {
				clientOf(cfg).fetchServerTimelinePage(limit = 120, offset = offset, dir = cleanDir)
			} ?: return
			val newPhotos = page.items.filter { it.isImage }
			val newVideos = page.items.filter { it.isVideo }
			withContext(Dispatchers.Main) {
				if (reset && cleanDir.isEmpty()) {
					allPhotos = newPhotos.distinctBy { it.path }
					allVideos = newVideos.distinctBy { it.path }
				} else {
					allPhotos = (allPhotos + newPhotos).distinctBy { it.path }
					allVideos = (allVideos + newVideos).distinctBy { it.path }
				}
				albumPageOffsets[cleanDir] = offset + page.items.size
				albumHasMoreMap[cleanDir] = page.hasMore
			}
		} finally {
			timelineLoadingMore = false
		}
	}

	fun upAlbum() {
		if (selectedAlbumPath.isBlank()) return
		selectedAlbumPath = ""
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
		selectedMediaPath = ""
		viewModelScope.launch { runCatching { mediaGridState.scrollToItem(0) } }
	}

	/** 在相册专属目录下新建相册文件夹：恒定创建在根相册 Photos/ 下，不发生多层嵌套错误 */
	fun createAlbum(name: String) {
		val targetDir = "Photos"
		val newPath = join(targetDir, name)
		val newEntry = DavEntry(name = name, path = newPath, isDir = true, size = 0L, modified = System.currentTimeMillis())
		if (allAlbumFolders.none { it.path == newPath }) {
			allAlbumFolders = sortAlbums((allAlbumFolders + newEntry), albumOrder)
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
			runCatching { client.mkcol("Photos") }
			client.mkcol(newPath)
		}
	}

	/** 在影音专属目录下新建影集/分类文件夹：恒定创建在根影音 movies/ 下，不发生多层嵌套错误 */
	fun createMediaFolder(name: String) {
		val targetDir = "movies"
		val newPath = join(targetDir, name)
		val newEntry = DavEntry(name = name, path = newPath, isDir = true, size = 0L, modified = System.currentTimeMillis())
		if (allMediaFolders.none { it.path == newPath }) {
			allMediaFolders = sortAlbums((allMediaFolders + newEntry), mediaOrder)
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
			runCatching { client.mkcol("movies") }
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

	fun isProtectedSystemDir(entry: DavEntry): Boolean {
		val p = entry.path.trim('/')
		return entry.isDir && (
			p.equals("Photos", ignoreCase = true) ||
				p.equals("Photos/默认相册", ignoreCase = true) ||
				entry.name == "默认相册" ||
				entry.name == "默认影集" ||
				entry.name == "默认影音" ||
				p == "movies/默认影集" ||
				p == "movies/默认影音"
			)
	}

	/** 删除成功后同步撤销跨页面的上传覆盖；删除失败不使原文件的上传记录失效。 */
	fun delete(entry: DavEntry, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
		if (isProtectedSystemDir(entry)) {
			uiMessage = "系统相册根目录不允许删除"
			onResult(false, "系统相册根目录不允许删除")
			return
		}
		val p = entry.path
		fun hit(target: String) = target == p || target.startsWith("$p/")
		// 乐观 UI 立即清除，包括文件列表、相册/影音胶囊栏和全部媒体池，告别删除迟钝/需要手动刷新的问题
		entries = entries.filterNot { hit(it.path) }
		allPhotos = allPhotos.filterNot { hit(it.path) }
		allVideos = allVideos.filterNot { hit(it.path) }
		allDocuments = allDocuments.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		selectedEntries = selectedEntries.filterNot { hit(it.path) }.toSet()
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""

		viewModelScope.launch {
			val meta = PhotoMetadataStore.get(entry.path)
			val err = runRemote { client ->
				client.delete(entry.path, entry.isDir)
				if (meta != null && meta.voiceId.isNotBlank()) {
					runCatching { client.delete(".nasdrop_voices/${meta.voiceId}.m4a", false) }
				}
				if (meta != null && meta.thumbId.isNotBlank()) {
					runCatching { client.delete(".nasdrop_thumbs/${meta.thumbId}.jpg", false) }
				}
			}
			dirCache.clear()
			if (err == null) {
				LocalThumbs.forget(entry.path)
				val db = NasDb.getInstance(app)
				if (entry.isDir) {
					PhotoMetadataStore.deleteDir(entry.path)
					db.clearIconsForDir(entry.path)
				} else {
					PhotoMetadataStore.delete(entry.path)
					db.clearVideoProgress(entry.path)
					db.clearIconIfMatching(entry.path)
				}
				folderIcons = db.getAllFolderIcons()
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
		if (isProtectedSystemDir(entry)) {
			uiMessage = "系统相册根目录不允许修改"
			onResult(false, "系统相册根目录不允许修改")
			return
		}
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
		photoGroups = photoGroups.map { g ->
			g.copy(items = g.items.map { p -> if (hit(p)) replacePath(p) else p })
		}

		viewModelScope.launch {
			val err = runRemote { it.move(entry.path, dest) }
			dirCache.clear()
			refreshSilent()
			if (err == null) {
				if (entry.isDir) {
					LocalThumbs.forget(entry.path)
					PhotoMetadataStore.moveDir(oldPath, dest)
				} else {
					LocalThumbs.move(app, oldPath, dest)
					PhotoMetadataStore.move(oldPath, dest, newName)
					PhotoMetadataStore.get(dest)?.let { m ->
						launch(Dispatchers.IO) {
							runCatching { clientOf(app.prefs.current()).reportMetadata(listOf(m)) }
						}
					}
				}
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
		if (isProtectedSystemDir(entry)) {
			uiMessage = "系统相册根目录不允许移动"
			onResult(false, "系统相册根目录不允许移动")
			return
		}
		val dest = join(targetDir, entry.name)
		// 乐观：当前列表里立刻消失，避免"点完没有任何反应"的错觉
		entries = entries.filterNot { it.path == entry.path }
		viewModelScope.launch {
			val err = runRemote { it.move(entry.path, dest) }
			dirCache.clear()
			if (err == null) {
				if (entry.isDir) {
					LocalThumbs.forget(entry.path)
					PhotoMetadataStore.moveDir(entry.path, dest)
				} else {
					LocalThumbs.move(app, entry.path, dest)
					PhotoMetadataStore.move(entry.path, dest)
					PhotoMetadataStore.get(dest)?.let { m ->
						withContext(Dispatchers.IO) {
							runCatching { client?.reportMetadata(listOf(m)) }
						}
					}
				}
				pruneMediaPools(listOf(entry.path))
				mediaDirty = true // 目标目录的相册/影音归属变了，下次进页面时校正
				runCatching { loadDirectory(targetDir) }
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
		allDocuments = allDocuments.filterNot { hit(it.path) }
		allAlbumFolders = allAlbumFolders.filterNot { hit(it.path) }
		allMediaFolders = allMediaFolders.filterNot { hit(it.path) }
		allFolderPaths = allFolderPaths.filterNot { hit(it) }
		if (hit(selectedAlbumPath)) selectedAlbumPath = ""
		if (hit(selectedMediaPath)) selectedMediaPath = ""
		photoGroups = photoGroups.mapNotNull { g ->
			val remaining = g.items.filterNot { hit(it) }
			if (remaining.isEmpty()) null else g.copy(items = remaining)
		}
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
		if (connectionMode == ConnectionMode.Direct || app.repo.isDirectConfirmed) {
			linkStatus = if (app.repo.isDirectConfirmed) "系统直连" else "直连模式"
			authUrl = ""
			return
		}
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
		val dirs = items.filter { it.isDir && !it.name.startsWith(".") && !it.path.contains(".thumbnails") }.map { it.path }
		allFolderPaths = (allFolderPaths + dirs).distinct().sorted()
	}

	/** 媒体扫描仅在用户进入对应页面时执行，逐目录呈现；失败保留现有内容并明确提示。 */
	private suspend fun scanGlobalMedia() {
		// 1. 同步远端元数据（图标与排序）与服务端集锦（必须在 Dispatchers.IO 中执行网络请求）
		val cfg = app.prefs.current()
		val (remoteMeta, remoteGroups) = withContext(Dispatchers.IO) {
			val c = clientOf(cfg)
			val meta = runCatching { c.loadRemoteMetadata() }.getOrNull()
			val groups = runCatching { c.fetchPhotoGroups() }.getOrNull()
			meta to groups
		}
		if (remoteMeta != null) {
			val db = NasDb.getInstance(app)
			remoteMeta.folderIcons.forEach { (p, k) -> db.setFolderIcon(p, k) }
			withContext(Dispatchers.Main) {
				folderIcons = folderIcons + remoteMeta.folderIcons
				if (remoteMeta.albumOrder.isNotEmpty()) {
					albumOrder = remoteMeta.albumOrder
					viewModelScope.launch(Dispatchers.IO) { app.prefs.setAlbumOrder(remoteMeta.albumOrder) }
				}
				if (remoteMeta.mediaOrder.isNotEmpty()) {
					mediaOrder = remoteMeta.mediaOrder
					viewModelScope.launch(Dispatchers.IO) { app.prefs.setMediaOrder(remoteMeta.mediaOrder) }
				}
			}
		}

		// 同步服务端数据库中的照片集锦
		if (remoteGroups != null) {
			withContext(Dispatchers.Main) {
				photoGroups = remoteGroups
			}
		}

		// 2. 优先走服务端 SQLite 分页拉取第一页（120条，毫秒级首屏，彻底解决单文件夹数千张图卡顿）
		val page = withContext(Dispatchers.IO) {
			clientOf(cfg).fetchServerTimelinePage(limit = 120, offset = 0, dir = "")
		}
		if (page != null) {
			withContext(Dispatchers.Main) {
				allPhotos = page.items.filter { it.isImage }.distinctBy { it.path }
				allVideos = page.items.filter { it.isVideo }.distinctBy { it.path }
				albumPageOffsets.clear()
				albumHasMoreMap.clear()
				albumPageOffsets[""] = page.items.size
				albumHasMoreMap[""] = page.hasMore
			}
		}

		// 3. 轻量列举相册目录结构（不再递归列举数千张子文件，除非服务端未启用数据库）
		val rootEntries = loadDirectory("")
		val albums = mutableListOf<DavEntry>()
		val mediaAlbums = mutableListOf<DavEntry>()
		val allDocs = mutableListOf<DavEntry>()
		allDocs.addAll(rootEntries.filter { !it.isDir && it.isDocument })

		val defaultAlbum = DavEntry(name = "默认相册", path = "Photos/默认相册", isDir = true, modified = System.currentTimeMillis())
		albums.add(defaultAlbum)

		for (dir in rootEntries.filter { it.isDir && !it.name.startsWith(".") }) {
			if (page != null && (dir.name.equals("Photos", true) || dir.name == "相册")) {
				val subDirs = runCatching { clientOf(cfg).list(dir.path) }.getOrDefault(emptyList())
					.filter { it.isDir && !it.name.startsWith(".") }
				albums.addAll(subDirs)
				continue
			}
			val sub = scanMediaDirectory(dir.path) ?: continue
			allDocs.addAll(sub.filter { !it.isDir && it.isDocument })
			if (dir.name.equals("Photos", true) || dir.name == "相册") {
				scanRecursiveMedia(dir.path, maxDepth = 4) { subDir ->
					albums.add(subDir)
				}
			} else if (dir.name.equals("movies", true) || dir.name.equals("Videos", true) || dir.name == "影视" || dir.name == "影音" || dir.name == "视频" || dir.name == "影集") {
				scanRecursiveMedia(dir.path, maxDepth = 4) { subDir ->
					albums.add(subDir)
					mediaAlbums.add(subDir)
				}
			} else {
				if (sub.any { it.isImage }) albums.add(dir)
				if (sub.any { it.isVideo }) {
					albums.add(dir)
					mediaAlbums.add(dir)
				}
			}
		}
		val distinctAlbums = albums.filterNot {
			it.name.startsWith(".") || it.path.contains(".thumbnails") ||
				it.name == "默认影集" || it.name == "默认影音" ||
				it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
				it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
		}.distinctBy { it.path }
		val distinctMedia = mediaAlbums.filterNot {
			it.name.startsWith(".") || it.path.contains(".thumbnails") ||
				it.name == "默认影集" || it.name == "默认影音" ||
				it.path == "movies/默认影集" || it.path == "movies/默认影音" ||
				it.path.endsWith("/默认影集") || it.path.endsWith("/默认影音")
		}.distinctBy { it.path }
		allAlbumFolders = sortAlbums(distinctAlbums, albumOrder)
		allMediaFolders = sortAlbums(distinctMedia, mediaOrder)
		withContext(Dispatchers.Main) {
			allDocuments = (allDocuments + allDocs).distinctBy { it.path }
		}
	}

	private suspend fun scanRecursiveMedia(baseDir: String, maxDepth: Int = 4, onFoundDir: (DavEntry) -> Unit) {
		val queue = ArrayDeque<Pair<String, Int>>()
		queue.add(baseDir to 0)
		while (queue.isNotEmpty()) {
			val (cur, depth) = queue.removeFirst()
			val list = scanMediaDirectory(cur) ?: continue
			val dirs = list.filter { it.isDir && !it.name.startsWith(".") && !it.path.contains(".thumbnails") }
			for (d in dirs) {
				onFoundDir(d)
				if (depth + 1 < maxDepth) {
					queue.add(d.path to depth + 1)
				}
			}
		}
	}

	private fun sortAlbums(items: List<DavEntry>, order: List<String>): List<DavEntry> {
		if (items.isEmpty()) return emptyList()
		val defaultPath = "Photos/默认相册"
		val orderMap = order.withIndex().associate { it.value to it.index }
		val collator = java.text.Collator.getInstance(Locale.CHINA).apply {
			strength = java.text.Collator.PRIMARY
		}

		return items.sortedWith { a, b ->
			val isADefault = a.name == "默认相册" || a.path == defaultPath
			val isBDefault = b.name == "默认相册" || b.path == defaultPath

			val orderA = orderMap[a.path]
			val orderB = orderMap[b.path]

			if (orderA != null && orderB != null) {
				orderA.compareTo(orderB)
			} else if (orderA != null) {
				-1
			} else if (orderB != null) {
				1
			} else if (isADefault != isBDefault) {
				if (isADefault) -1 else 1
			} else {
				val nameComp = collator.compare(a.name, b.name)
				if (nameComp != 0) nameComp else a.path.compareTo(b.path)
			}
		}
	}

	private suspend fun scanMediaDirectory(directory: String): List<DavEntry>? = try {
		loadDirectory(directory)
	} catch (e: Exception) {
		if (e is CancellationException) throw e
		Log.w("BrowserViewModel", "媒体子目录加载跳过 dir=$directory", e)
		null
	}

	private fun groupEntriesForTimeline(items: List<DavEntry>): List<TimelineGroup> {
		if (items.isEmpty()) return emptyList()
		val sdfDay = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
		val sdfTime = SimpleDateFormat("HH:mm", Locale.CHINA)
		val todayStr = sdfDay.format(Date())

		val cal = Calendar.getInstance()
		cal.add(Calendar.DAY_OF_YEAR, -1)
		val yesterdayStr = sdfDay.format(cal.time)

		val groupsMap = items.sortedByDescending { it.effectiveDate }.groupBy {
			if (it.effectiveDate > 0) sdfDay.format(Date(it.effectiveDate)) else "其他"
		}

		return groupsMap.map { (dateKey, list) ->
			val title = when (dateKey) {
				todayStr -> "今天"
				yesterdayStr -> "昨天"
				"其他" -> "更早之前"
				else -> dateKey
			}
			// 按时刻细分 Moments，支持一天内的多个不同时间段细化呈现
			val momentsMap = list.groupBy {
				if (it.effectiveDate > 0) sdfTime.format(Date(it.effectiveDate)) else "--:--"
			}
			val moments = momentsMap.map { (timeStr, mItems) ->
				TimelineMoment(
					timeTitle = timeStr,
					timestamp = mItems.firstOrNull()?.effectiveDate ?: 0L,
					items = mItems
				)
			}
			TimelineGroup(dateTitle = title, count = list.size, items = list, moments = moments)
		}
	}

	/**
	 * 保存照片伴随语音录音：
	 * 1. 存放在照片同级目录下，且以"."开头命名（默认不可见隐藏文件）；
	 * 2. 数据库仅记录路径指针与时长毫秒，绝不存音频原文（零 BLOB）；
	 * 3. 即时刷新 PhotoMetadataStore 缓存并通知 UI。
	 */
	fun savePhotoVoiceNote(
		photoPath: String,
		audioBytes: ByteArray,
		durationMs: Long,
		onResult: (Boolean, String?) -> Unit = { _, _ -> },
	) {
		val voiceId = "v_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().replace("-", "").take(8)}"
		val voiceDir = ".nasdrop_voices"
		val voiceFileName = "$voiceId.m4a"
		val unifiedPath = "$voiceDir/$voiceFileName"
		val oldAudioPath = PhotoMetadataStore.get(photoPath)?.audioPath

		viewModelScope.launch {
			val s = app.prefs.current()
			val err = withContext(Dispatchers.IO) {
				try {
					val localCacheDir = File(app.cacheDir, "audio_cache")
					if (!localCacheDir.exists()) localCacheDir.mkdirs()
					val localFile = File(localCacheDir, unifiedPath.replace('/', '_'))
					localFile.writeBytes(audioBytes)
				} catch (_: Exception) {}

				try {
					val c = clientOf(s)
					runCatching { c.mkcol(voiceDir) }
					c.uploadBytes(
						bytes = audioBytes,
						remoteDir = voiceDir,
						name = voiceFileName,
						mimeType = "audio/mp4",
					)
					if (!oldAudioPath.isNullOrBlank() && oldAudioPath != unifiedPath) {
						runCatching { c.delete(oldAudioPath, false) }
					}
					null
				} catch (e: Exception) {
					if (e is CancellationException) throw e
					e.message ?: "上传伴随录音失败"
				}
			}

			if (err == null) {
				PhotoMetadataStore.updateVoice(photoPath, voiceId, unifiedPath, durationMs)
				val updatedMeta = PhotoMetadataStore.get(photoPath)
				if (updatedMeta != null) {
					viewModelScope.launch(Dispatchers.IO) {
						runCatching { clientOf(s).reportMetadata(listOf(updatedMeta)) }
					}
				}
				uiMessage = "录音已保存"
				onResult(true, null)
			} else {
				uiMessage = "保存录音失败：$err"
				onResult(false, err)
			}
		}
	}

	/**
	 * 删除照片伴随语音录音：
	 * 1. 从 NAS 删除语音文件（统一目录或历史同级隐藏 .m4a）；
	 * 2. 本地与服务端 SQLite 置空对应字段，绝不残留。
	 */
	fun deletePhotoVoiceNote(
		photoPath: String,
		onResult: (Boolean, String?) -> Unit = { _, _ -> },
	) {
		val meta = PhotoMetadataStore.get(photoPath)
		val companionPath = meta?.voiceId?.takeIf { it.isNotBlank() }?.let { ".nasdrop_voices/$it.m4a" }
			?: meta?.audioPath

		viewModelScope.launch {
			val s = app.prefs.current()
			withContext(Dispatchers.IO) {
				if (!companionPath.isNullOrBlank()) {
					runCatching { clientOf(s).delete(companionPath, false) }
				}
				if (meta != null) {
					runCatching { clientOf(s).reportMetadata(listOf(meta.copy(voiceId = "__CLEAR__", audioDurationMs = 0L))) }
				}
			}

			PhotoMetadataStore.updateVoice(photoPath, "", null, 0L)
			uiMessage = "伴随录音已删除"
			onResult(true, null)
		}
	}

	/**
	 * 保存照片/视频文字备注（写入本地 SQLite + 同步服务端 SQLite，不再在相册里写 .memo 隐藏文件）
	 */
	fun savePhotoMemo(photoPath: String, memo: String) {
		val cleanMemo = memo.trim()
		PhotoMetadataStore.updateMemo(photoPath, cleanMemo)
		uiMessage = "备注已保存"
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val s = app.prefs.current()
				val meta = PhotoMetadataStore.get(photoPath) ?: PhotoMetadata(
					path = photoPath.trim('/'),
					name = photoPath.trim('/').substringAfterLast('/'),
					dir = photoPath.trim('/').let { if (it.contains('/')) it.substringBeforeLast('/') else "" },
					size = 0L,
					modified = 0L,
					takenAt = 0L,
				)
				clientOf(s).reportMetadata(
					listOf(meta.copy(memo = if (cleanMemo.isEmpty()) "__CLEAR__" else cleanMemo))
				)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				android.util.Log.w("BrowserViewModel", "同步备注至服务端 SQLite 失败: ${e.message}")
			}
		}
	}

	/**
	 * 清空删除照片/视频文字备注（从本地 SQLite 清空 + 同步清空服务端 SQLite + 清理历史 .memo 文件）
	 */
	fun deletePhotoMemo(photoPath: String) {
		val meta = PhotoMetadataStore.get(photoPath)
		PhotoMetadataStore.updateMemo(photoPath, null)
		uiMessage = "备注已清空"
		viewModelScope.launch(Dispatchers.IO) {
			try {
				val s = app.prefs.current()
				val c = clientOf(s)
				if (meta != null) {
					c.reportMetadata(listOf(meta.copy(memo = "__CLEAR__")))
				}
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				android.util.Log.w("BrowserViewModel", "清空服务端备注失败: ${e.message}")
			}
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
