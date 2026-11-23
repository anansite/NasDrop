package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
	val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 手机端本地图片元数据存储中心（SQLite + 内存快照双层缓存）。
 * 负责记录照片真实拍摄时间、尺寸、伴生缩略图状态，并严格保持新增、删除、修改、重命名、移动的生命周期一致性。
 * 所有 SQLite 写入均在后台 IO 协程执行，内存缓存瞬时更新，绝不阻塞主线程。
 */
object PhotoMetadataStore {

	private const val TAG = "PhotoMetadataStore"
	private const val DB_NAME = "nasdrop_photos.db"
	private const val DB_VERSION = 1
	private const val TABLE_NAME = "photo_metadata"

	private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private var dbHelper: DBHelper? = null
	private val cache = ConcurrentHashMap<String, PhotoMetadata>()

	fun init(context: Context) {
		if (dbHelper == null) {
			synchronized(this) {
				if (dbHelper == null) {
					dbHelper = DBHelper(context.applicationContext)
					ioScope.launch {
						loadInitialCache()
					}
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
						updatedAt = c.getLong(uIdx)
					)
					cache[meta.path.trim('/')] = meta
				}
			}
			Log.i(TAG, "已加载 ${cache.size} 条图片元数据到内存缓存")
		} catch (e: Exception) {
			Log.e(TAG, "加载图片元数据缓存失败", e)
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
	fun markThumbnail(path: String, hasThumbnail: Boolean) {
		val key = path.trim('/')
		val existing = cache[key]
		if (existing != null) {
			val updated = existing.copy(hasThumbnail = hasThumbnail, updatedAt = System.currentTimeMillis())
			cache[key] = updated
			ioScope.launch {
				try {
					val db = dbHelper?.writableDatabase ?: return@launch
					val cv = ContentValues().apply {
						put("has_thumb", if (hasThumbnail) 1 else 0)
						put("updated_at", updated.updatedAt)
					}
					db.update(TABLE_NAME, cv, "path = ?", arrayOf(key))
				} catch (e: Exception) {
					Log.e(TAG, "更新缩略图标记失败: $path", e)
				}
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
					updated_at INTEGER NOT NULL DEFAULT 0
				)
				""".trimIndent()
			)
			db.execSQL("CREATE INDEX IF NOT EXISTS idx_photo_dir ON $TABLE_NAME (dir)")
			db.execSQL("CREATE INDEX IF NOT EXISTS idx_photo_taken ON $TABLE_NAME (taken_at)")
		}

		override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
			// 当前为 v1，保留未来平滑迁移空间
		}
	}
}
