package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadQueueOrderTest {

	@Test
	fun activeAndConfirmingShareSameTierInSlotOrderSoRowsNeverSwap() {
		val doneFirst = UploadTask(uri = "u0", name = "done.jpg", started = true, bodySent = true, done = true, seq = 1, writeOrder = 1)
		val slot2 = UploadTask(uri = "u2", name = "slot2.jpg", started = true, bodySent = false, seq = 2, writeOrder = 3)
		val slot1Confirming = UploadTask(uri = "u1", name = "slot1.jpg", started = true, bodySent = true, seq = 3, writeOrder = 2)
		val queuedA = UploadTask(uri = "u3", name = "queuedA.jpg", started = false, seq = 4)
		val queuedB = UploadTask(uri = "u4", name = "queuedB.jpg", started = false, seq = 5)

		val ordered = UploadQueueOrder.sortUploadTasks(listOf(queuedB, doneFirst, slot2, queuedA, slot1Confirming))
		assertEquals(
			listOf("slot1.jpg", "slot2.jpg", "queuedA.jpg", "queuedB.jpg", "done.jpg"),
			ordered.map { it.name },
		)
	}

	@Test
	fun transferCenterKeepsRunningAndConfirmingLockedInOrderAndQueuedBySeq() {
		val tQueued2 = TransferTask(id = "a", name = "q2.jpg", path = "q2", isUpload = true, status = TransferStatus.QUEUED, seq = 5)
		val tQueued1 = TransferTask(id = "z", name = "q1.jpg", path = "q1", isUpload = true, status = TransferStatus.QUEUED, seq = 4)
		val tConfirm = TransferTask(id = "b", name = "confirm.jpg", path = "c", isUpload = true, status = TransferStatus.CONFIRMING, seq = 1, activeOrder = 1)
		val tRunning = TransferTask(id = "c", name = "run.jpg", path = "r", isUpload = true, status = TransferStatus.RUNNING, seq = 2, activeOrder = 2)
		val tDone = TransferTask(id = "d", name = "done.jpg", path = "d", isUpload = true, status = TransferStatus.COMPLETED, seq = 0, activeOrder = 0)

		val ordered = UploadQueueOrder.sortTransferTasks(listOf(tQueued2, tDone, tRunning, tQueued1, tConfirm))
		assertEquals(
			listOf("confirm.jpg", "run.jpg", "q1.jpg", "q2.jpg", "done.jpg"),
			ordered.map { it.name },
		)
	}
}
