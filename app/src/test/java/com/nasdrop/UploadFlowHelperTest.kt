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
}
