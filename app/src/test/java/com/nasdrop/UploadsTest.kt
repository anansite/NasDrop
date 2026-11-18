package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** 防止用户连续添加文件时，新批次覆盖尚在上传的旧批次。 */
class UploadsTest {
	@After
	fun finishTestTasks() {
		Uploads.tasks.value.forEach { task ->
			Uploads.update(task.id) { it.copy(done = true) }
			Uploads.remove(task.id)
		}
	}

	@Test
	fun addingAnotherBatchKeepsEarlierTasks() {
		Uploads.begin(listOf("content://review/one" to "one.jpg"))
		Uploads.begin(listOf("content://review/two" to "two.jpg"))

		assertEquals(listOf("one.jpg", "two.jpg"), Uploads.tasks.value.filterNot { it.dismissed }.map { it.name })
	}

	@Test
	fun listedAndDeletedResultsCannotBeReplayedByAnotherPage() {
		val task = Uploads.begin(listOf("content://review/history" to "one.jpg"), "history").single()
		Uploads.update(task.id) { it.copy(done = true, bodySent = true) }
		val firstPage = UploadListing()
		firstPage.update(Uploads.tasks.value.filter { it.id == task.id })
		Uploads.markListed(firstPage.reconcile("history", mapOf("history/one.jpg" to 0L)))
		val newPage = UploadListing()
		newPage.update(Uploads.tasks.value.filter { it.id == task.id })
		assertTrue(newPage.pending("history").isEmpty())
		Uploads.forgetPaths(listOf("history"))
		assertFalse(Uploads.tasks.value.first { it.id == task.id }.listingActive)
		val replacement = Uploads.begin(listOf("content://review/history" to "one.jpg"), "history").single()
		assertTrue(replacement.listingActive)
	}

	@Test
	fun stableIdsKeepDuplicateUrisAndBatchesIndependent() {
		val one = Uploads.begin(listOf("content://review/same" to "same.jpg"), "Photos", "one").single()
		val two = Uploads.begin(listOf("content://review/same" to "same.jpg"), "movies", "two").single()
		Uploads.update(one.id) { it.copy(started = true, sent = 10, total = 10, bodySent = true) }
		assertFalse(Uploads.tasks.value.first { it.id == one.id }.done)
		assertEquals(0L, Uploads.tasks.value.first { it.id == two.id }.sent)
		Uploads.remove(one.id)
		assertEquals(2, Uploads.tasks.value.count { !it.dismissed })
		Uploads.update(one.id) { it.copy(done = true) }
		Uploads.remove(one.id)
		assertEquals(listOf(two.id), Uploads.tasks.value.filterNot { it.dismissed }.map { it.id })
		assertTrue(Uploads.running())
	}
}
