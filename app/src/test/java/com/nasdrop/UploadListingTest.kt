package com.nasdrop

import org.junit.Assert.*
import org.junit.Test

/** 覆盖旧目录响应、页面重建和失败回滚，避免上传项闪现后消失。 */
class UploadListingTest {
	@Test
	fun coalescedSnapshotsPreferActualWriteOrderForSameName() {
		val listing = UploadListing()
		val earlier = UploadTask("content://older", "same.txt", id = "earlier", dir = "Photos", done = true, writeOrder = 1)
		val later = UploadTask("content://later", "same.txt", id = "later", dir = "Photos", done = true, writeOrder = 2)
		listing.update(listOf(later, earlier))
		assertEquals("later", listing.pending("Photos").single().id)
	}

	private fun task(id: String = "one", done: Boolean = false, error: String? = null) =
		UploadTask("content://review/$id", "one.jpg", total = 10, sent = 10,
			done = done, error = error, id = id, dir = "Photos", bodySent = true)

	@Test
	fun staleListingDoesNotHideSentOrConfirmedUpload() {
		val listing = UploadListing()
		listing.update(listOf(task()))
		listing.reconcile("Photos", emptyMap())
		assertEquals(listOf("one.jpg"), listing.pending("Photos").map { it.name })
		listing.update(listOf(task(done = true)))
		listing.reconcile("Photos", emptyMap())
		assertEquals(1, listing.pending("Photos").size)
		listing.reconcile("Photos", mapOf("Photos/one.jpg" to 10L))
		assertTrue(listing.pending("Photos").isEmpty())
		listing.update(listOf(task(done = true)))
		assertTrue("已被目录确认的历史任务不能重新插入", listing.pending("Photos").isEmpty())
	}

	@Test
	fun recreatedPageRecoversAlreadyCompletedUpload() {
		val listing = UploadListing()
		assertEquals(setOf("Photos"), listing.update(listOf(task(done = true))))
		assertEquals(1, listing.pending("Photos").size)
		assertTrue(listing.pending("Photos/subdir").isEmpty())
	}

	@Test
	fun failedOlderAttemptDoesNotRemoveNewerUploadOfSamePath() {
		val listing = UploadListing()
		listing.update(listOf(task("old"), task("new")))
		listing.update(listOf(task("old", done = true, error = "timeout"), task("new")))
		assertEquals("new", listing.pending("Photos").single().id)
		listing.update(listOf(task("new", done = true, error = "timeout")))
		assertTrue(listing.pending("Photos").isEmpty())
	}

	@Test
	fun failedNewAttemptRestoresEarlierUnlistedSuccess() {
		val listing = UploadListing()
		listing.update(listOf(task("old", done = true), task("new")))
		listing.update(listOf(task("old", done = true), task("new", done = true, error = "rejected")))
		assertEquals("old", listing.pending("Photos").single().id)
	}

	@Test
	fun deletingUploadedDirectoryDoesNotResurrectPendingChildren() {
		val listing = UploadListing()
		listing.update(listOf(task(done = true)))
		listing.forget(listOf("Photos"))
		listing.update(listOf(task(done = true)))
		assertTrue(listing.pending("Photos").isEmpty())
	}

	@Test
	fun globallyListedHistoryDoesNotReappearInANewPage() {
		val listing = UploadListing()
		listing.update(listOf(task(done = true).copy(listed = true)))
		assertTrue(listing.pending("Photos").isEmpty())
	}

	@Test
	fun deletionPropagatesToOtherPagesAndKeepsTransferHistory() {
		val listing = UploadListing()
		listing.update(listOf(task(done = true)))
		listing.update(listOf(task(done = true).copy(listingActive = false)))
		assertTrue(listing.pending("Photos").isEmpty())
	}

	@Test
	fun overwriteIsNotAcknowledgedByAnOlderFileWithDifferentSize() {
		val listing = UploadListing()
		listing.update(listOf(task(done = true)))
		listing.reconcile("Photos", mapOf("Photos/one.jpg" to 3L))
		assertEquals(1, listing.pending("Photos").size)
	}
}
