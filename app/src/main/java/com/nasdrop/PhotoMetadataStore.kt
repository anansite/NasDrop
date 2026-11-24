package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class PhotoMetadata(
	val path: String,
	val name: String,
	val dir: String,
	val size: Long,
	val modified: Long,
	val takenAt: Long,
	val width: Int = 0,
	val height: Int = 0,
	val hasThumbnail: Boolean = false,
	val audioPath: String? = null,
	val audioDurationMs: Long = 0L,
	val memo: String? = null,
	val memoUpdatedAt: Long = 0L,
	val mediaType: Int = 0, // 0: 图片, 1: 视频
	val videoDurationMs: Long = 0L,
	val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 手机端本地图片与媒体元数据存储中心（SQLite + 内存快照双层缓存）。
 * 严禁存储二进制图片或音频文件原文（零 BLOB）。
 * 所有音频等文件型数据均以隐藏文件（"."开头）就地存放在照片所在同级目录下。
 * 负责记录照片拍摄时间、伴随录音指针、文字备注、视频时长及伴生缩略图状态。
 * 并严格保持新增、删除、修改、重命名、移动的级联生命周期一致性（删除照片时级联清理录音、缓存与索引，零残留）。
 */
object PhotoMetadataStore {

	private const val TAG = "PhotoMetadataStore"
	private const val DB_NAME = "nasdrop_photos.db"
	private const val DB_VERSION = 2
	private const val TABLE_NAME = "photo_metadata"

	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var dbHelper: DBHelper? = null
	private val cache = ConcurrentHashMap<String, PhotoMetadata>()

	// Compose 快照依赖追踪版本号：任何元数据更新时自动递增，触发 Compose 状态瞬时重构
	var revision by mutableIntStateOf(0)
		private set

	fun notifyUpdated() {
		if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
			revision++
		} else {
			android.os.Handler(android.os.Looper.getMainLooper()).post {
				revision++
			}
		}
	}

	fun init(context: Context) {
		if (dbHelper == null) {
			synchronized(this) {
				if (dbHelper == null) {
					dbHelper = DBHelper(context.applicationContext)
					// 同步毫秒级加载本地 SQLite 存量元数据，保证后续 ViewModel 初始化瞬间就能直接读到全部照片
					loadInitialCache()
				}
			}
		}
	}

	private fun loadInitialCache() {
		try {
			val db = dbHelper?.readableDatabase ?: return
			val cursor = db.query(TABLE_NAME, null, null, null, null, null, null)
			cursor.use { c ->
				val pIdx = c.getColumnIndexOrThrow("path")
				val nIdx = c.getColumnIndexOrThrow("name")
				val dIdx = c.getColumnIndexOrThrow("dir")
				val sIdx = c.getColumnIndexOrThrow("size")
				val mIdx = c.getColumnIndexOrThrow("modified")
				val tIdx = c.getColumnIndexOrThrow("taken_at")
				val wIdx = c.getColumnIndexOrThrow("width")
				val hIdx = c.getColumnIndexOrThrow("height")
				val thIdx = c.getColumnIndexOrThrow("has_thumb")
				val uIdx = c.getColumnIndexOrThrow("updated_at")

				val aPathIdx = c.getColumnIndex("audio_path")
				val aDurIdx = c.getColumnIndex("audio_duration_ms")
				val memoIdx = c.getColumnIndex("memo")
				val memoUpIdx = c.getColumnIndex("memo_updated_at")
				val mTypeIdx = c.getColumnIndex("media_type")
				val vDurIdx = c.getColumnIndex("video_duration_ms")

				while (c.moveToNext()) {
					val meta = PhotoMetadata(
						path = c.getString(pIdx),
						name = c.getString(nIdx),
						dir = c.getString(dIdx),
						size = c.getLong(sIdx),
						modified = c.getLong(mIdx),
						takenAt = c.getLong(tIdx),
						width = c.getInt(wIdx),
						height = c.getInt(hIdx),
						hasThumbnail = c.getInt(thIdx) == 1,
						audioPath = if (aPathIdx >= 0 && !c.isNull(aPathIdx)) c.getString(aPathIdx) else null,
						audioDurationMs = if (aDurIdx >= 0) c.getLong(aDurIdx) else 0L,
						memo = if (memoIdx >= 0 && !c.isNull(memoIdx)) c.getString(memoIdx) else null,
						memoUpdatedAt = if (memoUpIdx >= 0) c.getLong(memoUpIdx) else 0L,
						mediaType = if (mTypeIdx >= 0) c.getInt(mTypeIdx) else 0,
						videoDurationMs = if (vDurIdx >= 0) c.getLong(vDurIdx) else 0L,
						updatedAt = c.getLong(uIdx)
					)
					cache[meta.path.trim('/')] = meta
				}
			}
			Log.i(TAG, "已加载 ${cache.size} 条图片元数据到内存缓存")
			notifyUpdated()
		} catch (e: Exception) {
			Log.e(TAG, "加载图片元数据缓存失败", e)
		}
	}

	fun getAll(): List<PhotoMetadata> = cache.values.toList()

	fun getAllEntries(): List<DavEntry> {
		return cache.values.map { m ->
			DavEntry(
				name = m.name,
				path = m.path,
				isDir = false,
				size = m.size,
				modified = m.modified,
				takenAt = m.takenAt,
			)
		}
	}

	fun get(path: String): PhotoMetadata? {
		val key = path.trim('/')
		return cache[key]
	}

	fun putMemoryOnly(meta: PhotoMetadata) {
		val key = meta.path.trim('/')
		cache[key] = meta
	}

	fun put(meta: PhotoMetadata) {
		val key = meta.path.trim('/')
		cache[key] = meta
		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				val cv = toContentValues(meta)
				db.insertWithOnConflict(TABLE_NAME, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
			} catch (e: Exception) {
				Log.e(TAG, "写入图片元数据失败: ${meta.path}", e)
			}
		}
	}

	fun putBatch(list: List<PhotoMetadata>) {
		if (list.isEmpty()) return
		list.forEach { cache[it.path.trim('/')] = it }
		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.beginTransaction()
				try {
					for (meta in list) {
						db.insertWithOnConflict(TABLE_NAME, null, toContentValues(meta), SQLiteDatabase.CONFLICT_REPLACE)
					}
					db.setTransactionSuccessful()
				} finally {
					db.endTransaction()
				}
			} catch (e: Exception) {
				Log.e(TAG, "批量写入图片元数据失败 count=${list.size}", e)
			}
		}
	}

	fun putBatchAsync(list: List<PhotoMetadata>) = putBatch(list)

	/**
	 * 删除单个文件元数据
	 */
	fun delete(path: String) {
		val key = path.trim('/')
		cache.remove(key)
		notifyUpdated()
		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.delete(TABLE_NAME, "path = ?", arrayOf(key))
			} catch (e: Exception) {
				Log.e(TAG, "删除图片元数据失败: $path", e)
			}
		}
	}

	/**
	 * 删除某个目录及其所有子孙文件的元数据（用于目录删除）
	 */
	fun deleteDir(dirPrefix: String) {
		val prefix = dirPrefix.trim('/')
		if (prefix.isEmpty()) {
			cache.clear()
		} else {
			val toRemove = cache.keys.filter { it == prefix || it.startsWith("$prefix/") }
			for (k in toRemove) {
				cache.remove(k)
			}
		}
		notifyUpdated()
		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				if (prefix.isEmpty()) {
					db.delete(TABLE_NAME, null, null)
				} else {
					db.delete(TABLE_NAME, "path = ? OR path LIKE ?", arrayOf(prefix, "$prefix/%"))
				}
			} catch (e: Exception) {
				Log.e(TAG, "删除目录图片元数据失败: $dirPrefix", e)
			}
		}
	}

	/**
	 * 单文件重命名或移动
	 */
	fun move(oldPath: String, newPath: String, newName: String = newPath.substringAfterLast('/')) {
		val oldKey = oldPath.trim('/')
		val newKey = newPath.trim('/')
		val existing = cache.remove(oldKey)
		val newDir = if (newKey.contains('/')) newKey.substringBeforeLast('/') else ""
		val updated = existing?.copy(
			path = newKey,
			name = newName,
			dir = newDir,
			updatedAt = System.currentTimeMillis()
		) ?: PhotoMetadata(
			path = newKey,
			name = newName,
			dir = newDir,
			size = 0L,
			modified = System.currentTimeMillis(),
			takenAt = PhotoTimeParser.parseFromFilename(newName) ?: System.currentTimeMillis()
		)
		cache[newKey] = updated

		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.beginTransaction()
				try {
					db.delete(TABLE_NAME, "path = ?", arrayOf(oldKey))
					db.insertWithOnConflict(TABLE_NAME, null, toContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
					db.setTransactionSuccessful()
				} finally {
					db.endTransaction()
				}
			} catch (e: Exception) {
				Log.e(TAG, "移动/重命名元数据失败: $oldPath -> $newPath", e)
			}
		}
	}

	/**
	 * 整个目录重命名或移动（前缀批量替换）
	 */
	fun moveDir(oldPrefix: String, newPrefix: String) {
		val oldP = oldPrefix.trim('/')
		val newP = newPrefix.trim('/')
		if (oldP.isEmpty()) return

		val toUpdate = mutableListOf<PhotoMetadata>()
		val toRemove = mutableListOf<String>()

		for ((k, meta) in cache) {
			if (k == oldP || k.startsWith("$oldP/")) {
				toRemove.add(k)
				val replaced = if (k == oldP) newP else newP + k.removePrefix(oldP)
				val newDir = if (replaced.contains('/')) replaced.substringBeforeLast('/') else ""
				val newName = replaced.substringAfterLast('/')
				toUpdate.add(meta.copy(path = replaced, dir = newDir, name = newName, updatedAt = System.currentTimeMillis()))
			}
		}
		toRemove.forEach { cache.remove(it) }
		toUpdate.forEach { cache[it.path] = it }

		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.beginTransaction()
				try {
					db.delete(TABLE_NAME, "path = ? OR path LIKE ?", arrayOf(oldP, "$oldP/%"))
					for (meta in toUpdate) {
						db.insertWithOnConflict(TABLE_NAME, null, toContentValues(meta), SQLiteDatabase.CONFLICT_REPLACE)
					}
					db.setTransactionSuccessful()
				} finally {
					db.endTransaction()
				}
			} catch (e: Exception) {
				Log.e(TAG, "批量移动目录元数据失败: $oldPrefix -> $newPrefix", e)
			}
		}
	}

	/**
	 * 差量对齐：比对远端实际存在的 entry 列表，自动清理已被在电脑端/外部删除的孤儿元数据记录。
	 * 返回被清理的文件相对路径列表。
	 */
	fun reconcile(dir: String, liveEntries: List<DavEntry>): List<String> {
		val cleanDir = dir.trim('/')
		val livePaths = liveEntries.map { it.path.trim('/') }.toSet()
		val purged = mutableListOf<String>()

		for ((k, meta) in cache) {
			if (meta.dir == cleanDir) {
				if (!livePaths.contains(k)) {
					purged.add(k)
					cache.remove(k)
				}
			}
		}

		if (purged.isNotEmpty()) {
			ioScope.launch {
				try {
					val db = dbHelper?.writableDatabase ?: return@launch
					db.beginTransaction()
					try {
						for (p in purged) {
							db.delete(TABLE_NAME, "path = ?", arrayOf(p))
						}
						db.setTransactionSuccessful()
					} finally {
						db.endTransaction()
					}
					Log.i(TAG, "已自动对齐并清理外部删除的照片记录 count=${purged.size} dir=$dir")
				} catch (e: Exception) {
					Log.e(TAG, "差量对齐清理失败 dir=$dir", e)
				}
			}
		}
		return purged
	}

	/**
	 * 标记伴生缩略图状态
	 */
	/**
	 * 计算照片伴随录音的隐藏文件相对路径（保存在同级目录下，以"."开头，默认不可见）。
	 * 例如: "family/baby/IMG_001.jpg" -> "family/baby/.IMG_001.m4a"
	 */
	fun computeCompanionAudioPath(photoPath: String): String {
		val clean = photoPath.trim('/')
		val parentDir = if (clean.contains('/')) clean.substringBeforeLast('/') else ""
		val fileName = clean.substringAfterLast('/')
		val baseName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
		val dotAudioName = ".$baseName.m4a"
		return if (parentDir.isEmpty()) dotAudioName else "$parentDir/$dotAudioName"
	}

	/**
	 * 计算照片/视频伴随文字备忘的隐藏文件相对路径（保存在同级目录下，以"."开头，以".memo"结尾）。
	 * 例如: "Photos/作业/IMG_001.jpg" -> "Photos/作业/.IMG_001.memo"
	 */
	fun computeCompanionMemoPath(photoPath: String): String {
		val clean = photoPath.trim('/')
		val parentDir = if (clean.contains('/')) clean.substringBeforeLast('/') else ""
		val fileName = clean.substringAfterLast('/')
		val baseName = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
		val dotMemoName = ".$baseName.memo"
		return if (parentDir.isEmpty()) dotMemoName else "$parentDir/$dotMemoName"
	}

	/**
	 * 更新照片伴随录音信息（audioPath 为 null 时代表删除伴随录音）。
	 * 纯文本路径与时长毫秒，绝对不存录音文件二进制。
	 */
	fun updateAudio(path: String, audioPath: String?, audioDurationMs: Long) {
		val key = path.trim('/')
		val existing = cache[key]
		val fileName = key.substringAfterLast('/')
		val parentDir = if (key.contains('/')) key.substringBeforeLast('/') else ""
		val now = System.currentTimeMillis()
		val updated = existing?.copy(
			audioPath = audioPath?.trim('/'),
			audioDurationMs = audioDurationMs,
			updatedAt = now
		) ?: PhotoMetadata(
			path = key,
			name = fileName,
			dir = parentDir,
			size = 0L,
			modified = now,
			takenAt = now,
			audioPath = audioPath?.trim('/'),
			audioDurationMs = audioDurationMs,
			updatedAt = now,
		)
		cache[key] = updated
		notifyUpdated()

		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.insertWithOnConflict(TABLE_NAME, null, toContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
			} catch (e: Exception) {
				Log.e(TAG, "更新伴随录音失败: $path", e)
			}
		}
	}

	/**
	 * 更新照片文字备注（memo 为 null 或空时代表删除备注）。
	 */
	fun updateMemo(path: String, memo: String?) {
		val key = path.trim('/')
		val cleanMemo = memo?.trim().takeIf { !it.isNullOrEmpty() }
		val now = System.currentTimeMillis()
		val existing = cache[key]
		val fileName = key.substringAfterLast('/')
		val parentDir = if (key.contains('/')) key.substringBeforeLast('/') else ""
		val updated = existing?.copy(
			memo = cleanMemo,
			memoUpdatedAt = if (cleanMemo != null) now else 0L,
			updatedAt = now
		) ?: PhotoMetadata(
			path = key,
			name = fileName,
			dir = parentDir,
			size = 0L,
			modified = now,
			takenAt = now,
			memo = cleanMemo,
			memoUpdatedAt = if (cleanMemo != null) now else 0L,
			updatedAt = now,
		)
		cache[key] = updated
		notifyUpdated()

		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.insertWithOnConflict(TABLE_NAME, null, toContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
			} catch (e: Exception) {
				Log.e(TAG, "更新文字备注失败: $path", e)
			}
		}
	}

	/**
	 * 标记视频播放总时长与类型
	 */
	fun updateVideoDuration(path: String, durationMs: Long) {
		val key = path.trim('/')
		val existing = cache[key]
		val fileName = key.substringAfterLast('/')
		val parentDir = if (key.contains('/')) key.substringBeforeLast('/') else ""
		val now = System.currentTimeMillis()
		val updated = existing?.copy(
			mediaType = 1,
			videoDurationMs = durationMs,
			updatedAt = now
		) ?: PhotoMetadata(
			path = key,
			name = fileName,
			dir = parentDir,
			size = 0L,
			modified = now,
			takenAt = now,
			mediaType = 1,
			videoDurationMs = durationMs,
			updatedAt = now,
		)
		cache[key] = updated
		notifyUpdated()

		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.insertWithOnConflict(TABLE_NAME, null, toContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
			} catch (e: Exception) {
				Log.e(TAG, "更新视频时长失败: $path", e)
			}
		}
	}

	fun markThumbnail(path: String, hasThumbnail: Boolean) {
		val key = path.trim('/')
		val existing = cache[key]
		val fileName = key.substringAfterLast('/')
		val parentDir = if (key.contains('/')) key.substringBeforeLast('/') else ""
		val now = System.currentTimeMillis()
		val updated = existing?.copy(
			hasThumbnail = hasThumbnail,
			updatedAt = now
		) ?: PhotoMetadata(
			path = key,
			name = fileName,
			dir = parentDir,
			size = 0L,
			modified = now,
			takenAt = now,
			hasThumbnail = hasThumbnail,
			updatedAt = now,
		)
		cache[key] = updated
		notifyUpdated()
		ioScope.launch {
			try {
				val db = dbHelper?.writableDatabase ?: return@launch
				db.insertWithOnConflict(TABLE_NAME, null, toContentValues(updated), SQLiteDatabase.CONFLICT_REPLACE)
			} catch (e: Exception) {
				Log.e(TAG, "更新缩略图标记失败: $path", e)
			}
		}
	}

	private fun toContentValues(meta: PhotoMetadata) = ContentValues().apply {
		put("path", meta.path.trim('/'))
		put("name", meta.name)
		put("dir", meta.dir.trim('/'))
		put("size", meta.size)
		put("modified", meta.modified)
		put("taken_at", meta.takenAt)
		put("width", meta.width)
		put("height", meta.height)
		put("has_thumb", if (meta.hasThumbnail) 1 else 0)
		put("audio_path", meta.audioPath)
		put("audio_duration_ms", meta.audioDurationMs)
		put("memo", meta.memo)
		put("memo_updated_at", meta.memoUpdatedAt)
		put("media_type", meta.mediaType)
		put("video_duration_ms", meta.videoDurationMs)
		put("updated_at", meta.updatedAt)
	}

	private class DBHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
		override fun onCreate(db: SQLiteDatabase) {
			db.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS $TABLE_NAME (
					path TEXT PRIMARY KEY,
					name TEXT NOT NULL,
					dir TEXT NOT NULL,
					size INTEGER NOT NULL DEFAULT 0,
					modified INTEGER NOT NULL DEFAULT 0,
					taken_at INTEGER NOT NULL DEFAULT 0,
					width INTEGER NOT NULL DEFAULT 0,
					height INTEGER NOT NULL DEFAULT 0,
					has_thumb INTEGER NOT NULL DEFAULT 0,
					audio_path TEXT DEFAULT NULL,
					audio_duration_ms INTEGER NOT NULL DEFAULT 0,
					memo TEXT DEFAULT NULL,
					memo_updated_at INTEGER NOT NULL DEFAULT 0,
					media_type INTEGER NOT NULL DEFAULT 0,
					video_duration_ms INTEGER NOT NULL DEFAULT 0,
					updated_at INTEGER NOT NULL DEFAULT 0
				)
				""".trimIndent()
			)
			db.execSQL("CREATE INDEX IF NOT EXISTS idx_photo_dir ON $TABLE_NAME (dir)")
			db.execSQL("CREATE INDEX IF NOT EXISTS idx_photo_taken ON $TABLE_NAME (taken_at)")
		}

		override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			if (oldVersion < 2) {
				try {
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN audio_path TEXT DEFAULT NULL")
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN audio_duration_ms INTEGER NOT NULL DEFAULT 0")
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN memo TEXT DEFAULT NULL")
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN memo_updated_at INTEGER NOT NULL DEFAULT 0")
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN media_type INTEGER NOT NULL DEFAULT 0")
					db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN video_duration_ms INTEGER NOT NULL DEFAULT 0")
				} catch (e: Exception) {
					Log.w(TAG, "DB upgrade to v2 failed: ${e.message}")
				}
			}
		}
	}
}

/**
 * DavEntry 便捷扩展：毫秒级直读 SQLite 内存快照，无 JSON 损耗。
 * 内部读取 PhotoMetadataStore.revision，自动与 Compose 快照状态引擎建立依赖绑定！
 */
val DavEntry.photoMeta: PhotoMetadata? get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	return PhotoMetadataStore.get(path)
}
val DavEntry.hasAudio: Boolean get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	return (PhotoMetadataStore.get(path)?.audioDurationMs ?: 0L) > 0L
}
val DavEntry.audioDurationText: String? get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	val ms = PhotoMetadataStore.get(path)?.audioDurationMs ?: 0L
	if (ms <= 0L) return null
	val totalSec = ms / 1000
	val min = totalSec / 60
	val sec = totalSec % 60
	return String.format(java.util.Locale.ROOT, "%d:%02d", min, sec)
}
val DavEntry.hasMemo: Boolean get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	return !PhotoMetadataStore.get(path)?.memo.isNullOrEmpty()
}
val DavEntry.memoText: String? get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	return PhotoMetadataStore.get(path)?.memo
}
val DavEntry.videoDurationMs: Long get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	return PhotoMetadataStore.get(path)?.videoDurationMs ?: 0L
}
val DavEntry.videoDurationText: String? get() {
	@Suppress("UNUSED_VARIABLE") val rev = PhotoMetadataStore.revision
	val ms = PhotoMetadataStore.get(path)?.videoDurationMs ?: 0L
	if (ms <= 0L) return null
	val totalSec = ms / 1000
	val min = totalSec / 60
	val sec = totalSec % 60
	return String.format(java.util.Locale.ROOT, "%d:%02d", min, sec)
}
