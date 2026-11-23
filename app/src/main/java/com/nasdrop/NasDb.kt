package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地轻量级 SQLite 存储。
 * 用于持久化视频断点进度、文件夹自定义图标、离线缓存元数据等。
 */
class NasDb private constructor(context: Context) : SQLiteOpenHelper(
	context.applicationContext,
	DATABASE_NAME,
	null,
	DATABASE_VERSION,
) {
	companion object {
		private const val DATABASE_NAME = "nasdrop.db"
		private const val DATABASE_VERSION = 2

		private const val TABLE_VIDEO_PROGRESS = "video_progress"
		private const val COL_PATH = "path"
		private const val COL_POS = "position_ms"
		private const val COL_DURATION = "duration_ms"
		private const val COL_UPDATED_AT = "updated_at"

		private const val TABLE_FOLDER_ICONS = "folder_icons"
		private const val COL_ICON_KEY = "icon_key"

		@Volatile
		private var instance: NasDb? = null

		fun getInstance(context: Context): NasDb =
			instance ?: synchronized(this) {
				instance ?: NasDb(context).also { instance = it }
			}
	}

	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS $TABLE_VIDEO_PROGRESS (
				$COL_PATH TEXT PRIMARY KEY,
				$COL_POS INTEGER NOT NULL,
				$COL_DURATION INTEGER NOT NULL,
				$COL_UPDATED_AT INTEGER NOT NULL
			)
			""".trimIndent(),
		)

		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS $TABLE_FOLDER_ICONS (
				$COL_PATH TEXT PRIMARY KEY,
				$COL_ICON_KEY TEXT NOT NULL
			)
			""".trimIndent(),
		)
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		if (oldVersion < 2) {
			db.execSQL(
				"""
				CREATE TABLE IF NOT EXISTS $TABLE_FOLDER_ICONS (
					$COL_PATH TEXT PRIMARY KEY,
					$COL_ICON_KEY TEXT NOT NULL
				)
				""".trimIndent(),
			)
		}
	}

	/**
	 * 保存视频播放进度。
	 * 若播放位置距离结尾不足 5 秒，视为播放完毕，自动清理该进度记录。
	 */
	fun saveVideoProgress(path: String, positionMs: Long, durationMs: Long) {
		val p = path.trim()
		if (p.isEmpty()) return

		try {
			val db = writableDatabase
			// 如果接近结尾（剩余小于 5 秒），视为已看完，清除记录
			if (durationMs > 0 && positionMs >= durationMs - 5000L) {
				db.delete(TABLE_VIDEO_PROGRESS, "$COL_PATH = ?", arrayOf(p))
				return
			}
			// 小于 3 秒不记为有效断点
			if (positionMs < 3000L) {
				return
			}

			val values = ContentValues().apply {
				put(COL_PATH, p)
				put(COL_POS, positionMs)
				put(COL_DURATION, durationMs)
				put(COL_UPDATED_AT, System.currentTimeMillis())
			}
			db.insertWithOnConflict(TABLE_VIDEO_PROGRESS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to save video progress: ${e.message}")
		}
	}

	/**
	 * 获取指定视频的历史播放进度（毫秒）。
	 * 若无记录或小于 3 秒则返回 0L。
	 */
	fun getVideoProgress(path: String): Long {
		val p = path.trim()
		if (p.isEmpty()) return 0L

		return try {
			val db = readableDatabase
			db.query(
				TABLE_VIDEO_PROGRESS,
				arrayOf(COL_POS),
				"$COL_PATH = ?",
				arrayOf(p),
				null,
				null,
				null,
			).use { cursor ->
				if (cursor.moveToFirst()) {
					cursor.getLong(0)
				} else {
					0L
				}
			}
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to get video progress: ${e.message}")
			0L
		}
	}

	/** 清除指定视频的进度 */
	fun clearVideoProgress(path: String) {
		val p = path.trim()
		if (p.isEmpty()) return
		try {
			writableDatabase.delete(TABLE_VIDEO_PROGRESS, "$COL_PATH = ?", arrayOf(p))
		} catch (_: Exception) {}
	}

	/**
	 * 设置文件夹自定义图标
	 */
	fun setFolderIcon(path: String, iconKey: String) {
		val p = path.trim('/')
		if (p.isEmpty()) return
		try {
			val db = writableDatabase
			if (iconKey == "default" || iconKey.isEmpty()) {
				db.delete(TABLE_FOLDER_ICONS, "$COL_PATH = ?", arrayOf(p))
			} else {
				val values = ContentValues().apply {
					put(COL_PATH, p)
					put(COL_ICON_KEY, iconKey)
				}
				db.insertWithOnConflict(TABLE_FOLDER_ICONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
			}
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to set folder icon: ${e.message}")
		}
	}

	/**
	 * 当某张照片被删除时，若其曾被选为某个相册的自定义封面（icon_key == path），自动解绑清除，杜绝死链。
	 */
	fun clearIconIfMatching(path: String) {
		val p = path.trim('/')
		if (p.isEmpty()) return
		try {
			writableDatabase.delete(TABLE_FOLDER_ICONS, "$COL_ICON_KEY = ?", arrayOf(p))
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to clear icon matching $path: ${e.message}")
		}
	}

	/**
	 * 当某个相册或目录被删除时，清除其对应的图标/封面映射。
	 */
	fun clearIconsForDir(dirPath: String) {
		val p = dirPath.trim('/')
		if (p.isEmpty()) return
		try {
			writableDatabase.delete(TABLE_FOLDER_ICONS, "$COL_PATH = ? OR $COL_PATH LIKE ?", arrayOf(p, "$p/%"))
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to clear icons for dir $dirPath: ${e.message}")
		}
	}

	/**
	 * 获取所有文件夹自定义图标映射表
	 */
	fun getAllFolderIcons(): Map<String, String> {
		val result = mutableMapOf<String, String>()
		try {
			val db = readableDatabase
			db.query(
				TABLE_FOLDER_ICONS,
				arrayOf(COL_PATH, COL_ICON_KEY),
				null,
				null,
				null,
				null,
				null,
			).use { cursor ->
				while (cursor.moveToNext()) {
					val path = cursor.getString(0)
					val key = cursor.getString(1)
					if (!path.isNullOrEmpty() && !key.isNullOrEmpty()) {
						result[path] = key
					}
				}
			}
		} catch (e: Exception) {
			android.util.Log.w("NasDb", "Failed to read folder icons: ${e.message}")
		}
		return result
	}
}
