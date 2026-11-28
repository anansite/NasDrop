package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadFlowHelperTest {

	@Test
	fun parseMediaDocumentSpecSupportsImageVideoAudioAndRejectsOthers() {
		assertEquals(
			MediaStoreKind.IMAGE to 12345L,
			parseMediaDocumentSpec("com.android.providers.media.documents", "image:12345"),
		)
		assertEquals(
			MediaStoreKind.VIDEO to 987L,
			parseMediaDocumentSpec("com.android.providers.media.documents", "video:987"),
		)
		assertEquals(
			MediaStoreKind.AUDIO to 42L,
			parseMediaDocumentSpec("com.android.providers.media.documents", "audio:42"),
		)
		assertNull(parseMediaDocumentSpec("com.android.externalstorage.documents", "primary:DCIM/a.jpg"))
		assertNull(parseMediaDocumentSpec("com.android.providers.media.documents", "document:12"))
		assertNull(parseMediaDocumentSpec("com.android.providers.media.documents", "image:not_num"))
		assertNull(parseMediaDocumentSpec("com.android.providers.media.documents", "image:0"))
	}

	@Test
	fun formatLocalCleanupResultMessageCoversAllOutcomes() {
		assertEquals(
			"已删除 3 个本地文件，已释放本地空间",
			formatLocalCleanupResultMessage(deletedCount = 3, failedCount = 0, userCancelledSystemPrompt = false),
		)
		assertEquals(
			"已清理 2 个本地文件，1 个受系统限制未删除",
			formatLocalCleanupResultMessage(deletedCount = 2, failedCount = 1, userCancelledSystemPrompt = false),
		)
		assertEquals(
			"已取消删除，本地文件已保留",
			formatLocalCleanupResultMessage(deletedCount = 0, failedCount = 0, userCancelledSystemPrompt = true),
		)
		assertEquals(
			"已删除 1 个本地文件，其余已取消清理",
			formatLocalCleanupResultMessage(deletedCount = 1, failedCount = 0, userCancelledSystemPrompt = true),
		)
		assertEquals(
			"本地文件删除失败（受系统权限限制，请在系统相册中清理）",
			formatLocalCleanupResultMessage(deletedCount = 0, failedCount = 2, userCancelledSystemPrompt = false),
		)
	}

	@Test
	fun originalFilenameHelpersHandlePhotoPickerAndDataPaths() {
		assertEquals(
			1000012345L,
			parsePhotoPickerMediaId("/picker/0/com.android.providers.media.photopicker/media/1000012345"),
		)
		assertEquals(
			42L,
			parsePhotoPickerMediaId("/picker_get_content/0/com.android.providers.media.photopicker/media/42"),
		)
		assertNull(parsePhotoPickerMediaId("/external/images/media/123"))

		assertEquals(
			"IMG_20261127_151200.jpg",
			extractOriginalFilenameFromDataPath("/storage/emulated/0/DCIM/Camera/IMG_20261127_151200.jpg"),
		)
		assertEquals("photo.png", extractOriginalFilenameFromDataPath("C:\\Photos\\photo.png"))
		assertNull(extractOriginalFilenameFromDataPath("  "))

		assertEquals("clip.mp4", sanitizeUploadedFileName("DCIM/clip.mp4"))
		assertNull(sanitizeUploadedFileName(".."))
	}

	@Test
	fun remoteConflictClassificationHandlesOriginalAndRedactedExifMatches() {
		assertEquals(
			BrowserViewModel.UploadConflictKind.IDENTICAL,
			classifyRemoteUploadConflict(
				localSize = 1024L,
				remoteSize = 1024L,
				localPrimarySha256 = "ABC123",
				localFallbackSha256 = null,
				remoteSha256 = "abc123",
			),
		)
		// 原图与分区存储脱敏流大小一致但仅脱敏前/后哈希命中远端时仍判为相同文件
		assertEquals(
			BrowserViewModel.UploadConflictKind.IDENTICAL,
			classifyRemoteUploadConflict(
				localSize = 1024L,
				remoteSize = 1024L,
				localPrimarySha256 = "original_hash",
				localFallbackSha256 = "redacted_hash",
				remoteSha256 = "redacted_hash",
			),
		)
		assertEquals(
			BrowserViewModel.UploadConflictKind.DIFFERENT_CONTENT,
			classifyRemoteUploadConflict(
				localSize = 1024L,
				remoteSize = 1024L,
				localPrimarySha256 = "original_hash",
				localFallbackSha256 = "redacted_hash",
				remoteSha256 = "other_hash",
			),
		)
		assertEquals(
			BrowserViewModel.UploadConflictKind.DIFFERENT_CONTENT,
			classifyRemoteUploadConflict(
				localSize = 2048L,
				remoteSize = 1024L,
				localPrimarySha256 = null,
				localFallbackSha256 = null,
				remoteSha256 = null,
			),
		)
	}
}
