package com.nasdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 上传文件的本地缩略图。
 *
 * 为什么需要它：OpenList 的 `thumb` 是懒生成的，刚 PUT 完的文件在 fs/list 里往往还没有
 * 缩略图直链；而列表又不能拿原图当缩略图——一张 3MB 的 PNG 在隧道里要好几秒，
 * 用户看到的就是"上传完了，列表里一片灰"。视频更糟：没有服务端缩略图时干脆没有图。
 *
 * 既然文件本来就在手机里，上传开始时就地生成一张小图（图片降采样、视频取封面帧），
 * 存进 cache，上传完成的那一刻列表里就已经是真图了，完全不依赖服务端。
 * 之后服务端缩略图就绪时会被 [WebDavClient] 的真缩略图覆盖（本地图优先，仅用于"刚上传"这个窗口）。
 */
object LocalThumbs {

	/** entryPath(相对 WebDAV 根, 无首尾斜杠) -> 本地小图文件 */
	private val map = ConcurrentHashMap<String, File>()

	/** 正在生成的路径，避免同一文件重复解码 */
	private val busy = ConcurrentHashMap.newKeySet<String>()

	/**
	 * 供 Compose 观察：新缩略图落盘后自增，触发列表重组。
	 * 没有它，异步生成完的本地图 Compose 无从得知，仍然是一片灰。
	 */
	var revision by mutableIntStateOf(0)
		private set

	/** 仅返回仍然存在的有效文件，系统或手动清缓存后移除失效映射，允许重新生成。 */
	fun get(path: String): File? {
		val key = path.trim('/')
		val file = map[key] ?: return null
		if (file.isFile && file.length() > 0L) return file
		map.remove(key, file)
		return null
	}

	private fun dir(context: Context) = File(context.cacheDir, "local_thumbs")

	private fun target(context: Context, key: String) =
		File(dir(context), Integer.toHexString(key.hashCode()) + ".jpg")

	/**
	 * 幂等生成。任何失败都静默——缩略图是增强项，绝不能让它拖累或中断上传。
	 * 调用方应在 IO 线程调用。
	 */
	fun ensure(context: Context, uri: Uri, entryPath: String, isVideo: Boolean) {
		val key = entryPath.trim('/')
		if (key.isEmpty()) return
		if (get(key) != null) return
		if (!busy.add(key)) return
		try {
			val out = target(context, key)
			if (!out.exists() || out.length() == 0L) {
				val bmp = try {
					if (isVideo) videoFrame(context, uri) else imageThumb(context, uri)
				} catch (_: Throwable) {
					null
				}
				if (bmp != null) {
					out.parentFile?.mkdirs()
					out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
					bmp.recycle()
				}
			}
			if (out.exists() && out.length() > 0L) {
				map[key] = out
				revision++
			}
		} catch (_: Throwable) {
			// 忽略：没有本地图就退回服务端缩略图/占位
		} finally {
			busy.remove(key)
		}
	}

	fun notifyRevision() {
		revision++
	}

	/**
	 * 直接存入字节数组缩略图，立即建立本地缓存并触发 Compose 列表重组
	 */
	fun put(context: Context, entryPath: String, bytes: ByteArray) {
		val key = entryPath.trim('/')
		if (key.isEmpty() || bytes.isEmpty()) return
		try {
			val out = target(context, key)
			out.parentFile?.mkdirs()
			out.writeBytes(bytes)
			map[key] = out
			revision++
		} catch (_: Throwable) {}
	}

	/**
	 * 严禁客户端后台静默拉取几十MB原视频或高清大图来生成缩略图，避免消耗巨量网络带宽与服务端计算资源。
	 */
	fun generateRemoteThumbnail(
		context: Context,
		client: WebDavClient,
		entry: DavEntry,
	): ByteArray? {
		return null
	}

	/** 移动/重命名时将旧路径的本地缩略图无缝迁移到新路径，避免移动后缩略图瞬间消失 */
	fun move(context: Context, oldPath: String, newPath: String) {
		val oldKey = oldPath.trim('/')
		val newKey = newPath.trim('/')
		if (oldKey.isEmpty() || newKey.isEmpty() || oldKey == newKey) return
		val oldFile = map.remove(oldKey) ?: target(context, oldKey).takeIf { it.isFile && it.length() > 0L } ?: return
		val newFile = target(context, newKey)
		try {
			newFile.parentFile?.mkdirs()
			if (oldFile.absolutePath != newFile.absolutePath) {
				oldFile.copyTo(newFile, overwrite = true)
				runCatching { oldFile.delete() }
			}
			if (newFile.isFile && newFile.length() > 0L) {
				map[newKey] = newFile
			}
		} catch (_: Throwable) {
			runCatching { oldFile.delete() }
		}
		revision++
	}

	/** 清掉某个路径的本地图（删除时调用，避免旧图被复用） */
	fun forget(vararg entryPaths: String) {
		var changed = false
		for (p in entryPaths) {
			val key = p.trim('/')
			val f = map.remove(key) ?: continue
			runCatching { f.delete() }
			changed = true
		}
		if (changed) revision++
	}

	/** 清理可再生成的本地缩略图；保留生成中标记，避免与上传流程重复解码。 */
	fun clear(context: Context) {
		map.clear()
		val directory = dir(context)
		if (directory.exists() && !directory.deleteRecursively()) {
			revision++
			throw java.io.IOException("部分本地缩略图无法清理，请稍后重试")
		}
		revision++
	}

	/** 清理过旧的本地缩略图，避免 cache 目录无限增长 */
	fun cleanup(context: Context, keepDays: Int = 7) {
		runCatching {
			val d = dir(context)
			if (!d.exists()) return
			val deadline = System.currentTimeMillis() - keepDays * 24L * 3600 * 1000
			d.listFiles()?.forEach { f ->
				if (f.lastModified() < deadline) f.delete()
			}
		}
	}

	// ==================== 解码 ====================

	private fun imageThumb(context: Context, uri: Uri): Bitmap? {
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		context.contentResolver.openInputStream(uri)?.use {
			BitmapFactory.decodeStream(it, null, bounds)
		}
		val w = bounds.outWidth
		val h = bounds.outHeight
		if (w <= 0 || h <= 0) return null
		var sample = 1
		while (w / (sample * 2) >= 512 && h / (sample * 2) >= 512) sample *= 2
		val opts = BitmapFactory.Options().apply {
			inSampleSize = sample
			inPreferredConfig = Bitmap.Config.RGB_565
		}
		return context.contentResolver.openInputStream(uri)?.use {
			BitmapFactory.decodeStream(it, null, opts)
		}
	}

	private fun videoFrame(context: Context, uri: Uri): Bitmap? {
		val mmr = MediaMetadataRetriever()
		return try {
			mmr.setDataSource(context, uri)
			val raw = mmr.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
				?: mmr.frameAtTime
			raw?.let { scaleDown(it, 640) }
		} catch (_: Throwable) {
			null
		} finally {
			runCatching { mmr.release() }
		}
	}

	private fun scaleDown(src: Bitmap, max: Int): Bitmap {
		val w = src.width
		val h = src.height
		if (w <= 0 || h <= 0) return src
		val ratio = minOf(max.toFloat() / w, max.toFloat() / h, 1f)
		if (ratio >= 1f) return src
		val out = Bitmap.createScaledBitmap(src, (w * ratio).toInt(), (h * ratio).toInt(), true)
		if (out !== src) src.recycle()
		return out
	}
}
