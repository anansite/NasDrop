package com.nasdrop

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalCleanupCandidate(
	val uriString: String,
	val name: String,
	val sizeBytes: Long = 0L,
)

data class LocalDeletePlan(
	val mediaStoreUris: List<Uri>,
	val directUris: List<Uri>,
	val skippedCount: Int,
) {
	val deletableCount: Int get() = mediaStoreUris.size + directUris.size
}

data class LocalDeleteExecution(
	val directDeleted: Int,
	val failedCount: Int,
	val intentSenders: List<IntentSender>,
	val pendingMediaCount: Int,
)

internal enum class MediaStoreKind { IMAGE, VIDEO, AUDIO }

internal fun parseMediaDocumentSpec(authority: String?, documentId: String?): Pair<MediaStoreKind, Long>? {
	if (authority != "com.android.providers.media.documents" || documentId.isNullOrBlank()) return null
	val parts = documentId.split(':', limit = 2)
	if (parts.size != 2) return null
	val id = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
	val kind = when (parts[0]) {
		"image" -> MediaStoreKind.IMAGE
		"video" -> MediaStoreKind.VIDEO
		"audio" -> MediaStoreKind.AUDIO
		else -> return null
	}
	return kind to id
}

internal fun formatLocalCleanupResultMessage(
	deletedCount: Int,
	failedCount: Int,
	userCancelledSystemPrompt: Boolean,
): String = when {
	userCancelledSystemPrompt && deletedCount > 0 -> "已删除 $deletedCount 个本地文件，其余已取消清理"
	userCancelledSystemPrompt -> "已取消删除，本地文件已保留"
	deletedCount > 0 && failedCount > 0 -> "已清理 $deletedCount 个本地文件，$failedCount 个受系统限制未删除"
	deletedCount > 0 -> "已删除 $deletedCount 个本地文件，已释放本地空间"
	failedCount > 0 -> "本地文件删除失败（受系统权限限制，请在系统相册中清理）"
	else -> "未发现可清理的本地文件"
}

private fun uriExists(context: Context, uri: Uri): Boolean {
	if (uri.scheme == ContentResolver.SCHEME_FILE) {
		val path = uri.path ?: return false
		val f = File(path)
		return f.exists() && f.canWrite()
	}
	return runCatching {
		context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
	}.getOrDefault(false)
}

internal fun resolveMediaStoreUri(context: Context, uri: Uri): Uri? {
	if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
	if (uri.authority == MediaStore.AUTHORITY && !uri.path.orEmpty().contains("/picker")) {
		return uri.takeIf { uriExists(context, it) }
	}
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
		val official = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
		if (official != null && uriExists(context, official)) return official
	}
	if (DocumentsContract.isDocumentUri(context, uri)) {
		val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
		val spec = parseMediaDocumentSpec(uri.authority, docId)
		if (spec != null) {
			val base = when (spec.first) {
				MediaStoreKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
				MediaStoreKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
				MediaStoreKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
			}
			val candidate = ContentUris.withAppendedId(base, spec.second)
			if (uriExists(context, candidate)) return candidate
		}
	}
	return null
}

private fun supportsDocumentDelete(context: Context, uri: Uri): Boolean {
	if (!DocumentsContract.isDocumentUri(context, uri)) return false
	return runCatching {
		context.contentResolver.query(
			uri,
			arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
			null,
			null,
			null,
		)?.use { cursor ->
			if (!cursor.moveToFirst()) return@use false
			val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
			if (idx < 0) false
			else (cursor.getInt(idx) and DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0
		} ?: false
	}.getOrDefault(false)
}

fun buildLocalCleanupPlan(context: Context, candidates: List<LocalCleanupCandidate>): LocalDeletePlan {
	val mediaUris = LinkedHashSet<Uri>()
	val directUris = LinkedHashSet<Uri>()
	var skipped = 0

	for (item in candidates.distinctBy { it.uriString }) {
		val uri = runCatching { Uri.parse(item.uriString) }.getOrNull()
		if (uri == null) {
			skipped++
			continue
		}
		if (uri.scheme == ContentResolver.SCHEME_FILE) {
			val path = uri.path
			val f = path?.let { File(it) }
			if (f != null && f.exists() && f.canWrite()) {
				directUris.add(uri)
			} else {
				skipped++
			}
			continue
		}
		val mediaUri = resolveMediaStoreUri(context, uri)
		if (mediaUri != null) {
			mediaUris.add(mediaUri)
			continue
		}
		if (supportsDocumentDelete(context, uri)) {
			directUris.add(uri)
		} else {
			skipped++
		}
	}

	return LocalDeletePlan(
		mediaStoreUris = mediaUris.toList(),
		directUris = directUris.toList(),
		skippedCount = skipped,
	)
}

suspend fun executeLocalDeletePlan(context: Context, plan: LocalDeletePlan): LocalDeleteExecution =
	withContext(Dispatchers.IO) {
		var directDeleted = 0
		var failed = 0
		val intentSenders = mutableListOf<IntentSender>()
		var pendingMediaCount = 0

		for (uri in plan.directUris) {
			val ok = runCatching {
				when {
					uri.scheme == ContentResolver.SCHEME_FILE -> {
						val path = uri.path
						path != null && File(path).delete()
					}
					DocumentsContract.isDocumentUri(context, uri) -> {
						DocumentsContract.deleteDocument(context.contentResolver, uri)
					}
					else -> {
						context.contentResolver.delete(uri, null, null) > 0
					}
				}
			}.getOrDefault(false)
			if (ok) directDeleted++ else failed++
		}

		if (plan.mediaStoreUris.isNotEmpty()) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				runCatching {
					val pi = MediaStore.createDeleteRequest(context.contentResolver, plan.mediaStoreUris)
					intentSenders.add(pi.intentSender)
					pendingMediaCount = plan.mediaStoreUris.size
				}.onFailure {
					failed += plan.mediaStoreUris.size
				}
			} else {
				for (mediaUri in plan.mediaStoreUris) {
					try {
						val rows = context.contentResolver.delete(mediaUri, null, null)
						if (rows > 0) directDeleted++ else failed++
					} catch (sec: SecurityException) {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sec is RecoverableSecurityException) {
							intentSenders.add(sec.userAction.actionIntent.intentSender)
							pendingMediaCount++
						} else {
							failed++
						}
					} catch (_: Exception) {
						failed++
					}
				}
			}
		}

		LocalDeleteExecution(
			directDeleted = directDeleted,
			failedCount = failed,
			intentSenders = intentSenders,
			pendingMediaCount = pendingMediaCount,
		)
	}
