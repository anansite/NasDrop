package com.nasdrop

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Locale

/** 只有完整文件才发布到系统下载；取消/失败回滚本次新建的存储项，不覆盖已有文件。 */
object DownloadStorage {
	suspend fun publish(context: Context, task: TransferTask, part: File): String {
		val name = task.name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "下载文件" }
		val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
			?: "application/octet-stream"
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			val resolver = context.contentResolver
			val values = ContentValues().apply {
				put(MediaStore.Downloads.DISPLAY_NAME, name)
				put(MediaStore.Downloads.MIME_TYPE, mime)
				put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NasDrop/")
				put(MediaStore.Downloads.IS_PENDING, 1)
			}
			val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
				?: error("无法创建本机下载文件")
			try {
				resolver.openOutputStream(uri)?.use { copy(part, it) } ?: error("无法写入本机下载文件")
				currentCoroutineContext().ensureActive()
				check(resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) > 0) {
					"无法完成本机下载保存"
				}
				return uri.toString()
			} catch (e: Exception) {
				resolver.delete(uri, null, null)
				throw e
			}
		}
		// Android 8/9 用应用外部文件目录，不增加广泛存储权限；FileProvider 授权系统查看器打开。
		val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
			?: File(context.filesDir, "downloads/saved")
		check(directory.exists() || directory.mkdirs()) { "无法创建本机下载目录" }
		var target = File(directory, name)
		var suffix = 1
		while (!target.createNewFile()) {
			val stem = name.substringBeforeLast('.', name)
			val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
			target = File(directory, "$stem (${suffix++})$ext")
		}
		try {
			FileOutputStream(target).use { copy(part, it) }
			currentCoroutineContext().ensureActive()
			return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString()
		} catch (e: Exception) {
			target.delete()
			throw e
		}
	}

	/** 分块复制检查取消，避免保存大文件时取消按钮只改变界面。 */
	private suspend fun copy(part: File, output: OutputStream) {
		part.inputStream().use { input ->
			val buffer = ByteArray(64 * 1024)
			while (true) {
				currentCoroutineContext().ensureActive()
				val count = input.read(buffer)
				if (count < 0) break
				output.write(buffer, 0, count)
			}
		}
		output.flush()
	}
}
