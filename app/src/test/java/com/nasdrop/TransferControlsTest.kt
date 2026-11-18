package com.nasdrop

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

/** 真实 Call 的取消边界和目录范围，不依赖 Android 页面或私有 NAS。 */
class TransferControlsTest {
	@Test
	fun anotherBatchCannotBypassOverwriteConfirmationWhileFirstFileIsInvisibleRemotely() {
		val queued = UploadTask("content://queue", "same.jpg", dir = "Photos", ownerKey = "server-a")
		assertEquals(listOf("same.jpg"), findUploadConflicts(listOf("same.jpg"), emptySet(), listOf(queued), "Photos", "server-a"))
		assertTrue(findUploadConflicts(listOf("same.jpg"), emptySet(), listOf(queued), "Photos", "server-b").isEmpty())
		assertTrue(findUploadConflicts(listOf("same.jpg"), emptySet(), listOf(queued.copy(cancelled = true)), "Photos", "server-a").isEmpty())
	}

	@Test
	fun connectionPolicyChangesDoNotChangeServerIdentityButOtherAccountsOrPathsDo() {
		val first = Settings(baseUrl = "http://127.0.0.1/nas/", user = "one")
		assertEquals(first.serverIdentity, first.copy(trustAllCerts = false, lastDir = "Photos").serverIdentity)
		assertNotEquals(first.serverIdentity, first.copy(baseUrl = "http://127.0.0.1/other/").serverIdentity)
		assertNotEquals(first.serverIdentity, first.copy(user = "two").serverIdentity)
	}
	@Test
	fun cancellingOneUploadStopsItsCallAndKeepsOtherTasks() {
		val one = Uploads.begin(listOf("content://control/one" to "one.txt"), "controls").single()
		val two = Uploads.begin(listOf("content://control/two" to "two.txt"), "controls").single()
		val call = OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1/").build())
		Uploads.registerCall(one.id, call)
		Uploads.cancel(one.id)
		assertTrue(call.isCanceled())
		val result = Uploads.tasks.value.first { it.id == one.id }
		assertTrue(result.cancelled)
		assertTrue(result.done)
		assertFalse(Uploads.tasks.value.first { it.id == two.id }.done)
		Uploads.update(one.id) { it.copy(done = true, error = null) }
		assertTrue(Uploads.tasks.value.first { it.id == one.id }.cancelled)
		Uploads.update(two.id) { it.copy(done = true) }
		Uploads.remove(one.id)
		Uploads.remove(two.id)
	}

	@Test
	fun cancellationBeforeCallRegistrationAlsoStopsTheNewCall() {
		val task = Uploads.begin(listOf("content://control/queued" to "queued.txt"), "controls").single()
		Uploads.cancel(task.id)
		val call = OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1/").build())
		Uploads.registerCall(task.id, call)
		assertTrue(call.isCanceled())
		Uploads.remove(task.id)
	}

	@Test
	fun directoryMatchingUsesWholePathSegments() {
		assertTrue(isPathWithin("Photos/旅行/one.png", "Photos/旅行"))
		assertFalse(isPathWithin("Photos/旅行备份/two.png", "Photos/旅行"))
		assertTrue(isPathWithin("movies/sub/video.mp4", ""))
		assertTrue(isPathWithin("Photos/旅行", "Photos/旅行"))
	}
}
