package com.nasdrop

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 取消必须终止真实 HTTP 调用，不能被成功响应或上传重试策略覆盖。 */
class WebDavCancellationTest {
	@Test
	fun downloadCanBeCanceledBeforeExecution() {
		MockWebServer().use { server ->
			val output = ByteArrayOutputStream()
			assertThrows(TransferCanceledException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", output,
					onCall = { it.cancel() }) { _, _ -> }
			}
			assertEquals(0, server.requestCount)
			assertEquals(0, output.size())
		}
	}

	@Test
	fun canceledDownloadStopsBeforeWritingTheRemainingBody() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setBody("abcdefghij").throttleBody(1, 100, TimeUnit.MILLISECONDS))
			lateinit var activeCall: Call
			val output = ByteArrayOutputStream()
			assertThrows(TransferCanceledException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", output,
					onCall = { activeCall = it }) { _, _ -> activeCall.cancel() }
			}
			assertEquals("a", output.toString("UTF-8"))
			assertEquals(1, server.requestCount)
		}
	}

	@Test
	fun cancelInterruptsDownloadWaitingForTheServer() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setBody("abcdef").setHeadersDelay(2, TimeUnit.SECONDS))
			val activeCall = AtomicReference<Call>()
			val executor = Executors.newSingleThreadExecutor()
			try {
				val download = executor.submit {
					WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", ByteArrayOutputStream(),
						onCall = { activeCall.set(it) }) { _, _ -> }
				}
				assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
				activeCall.get().cancel()
				val error = assertThrows(ExecutionException::class.java) { download.get(1, TimeUnit.SECONDS) }
				assertTrue("等待响应时取消必须立即中断 HTTP 调用", error.cause is TransferCanceledException)
			} finally {
				executor.shutdownNow()
			}
		}
	}

	@Test
	fun canceledPutCannotRetryOrReportSuccess() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(201))
			lateinit var activeCall: Call
			var registered = 0
			assertThrows(TransferCanceledException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).uploadStream("one.txt", 3, "text/plain", "",
					{ ByteArrayInputStream("abc".toByteArray()) }, { _, _ -> },
					onBodySent = { activeCall.cancel() }, onCall = { activeCall = it; registered++ })
			}
			assertEquals("取消不能触发第二次 PUT", 1, registered)
			assertTrue(server.requestCount <= 1)
		}
	}

	@Test
	fun canceledDirectoryPreflightMustNotContinueToPut() {
		MockWebServer().use { server ->
			var opened = false
			assertThrows(TransferCanceledException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).uploadStream("one.txt", -1, "text/plain", "folder",
					{ opened = true; ByteArrayInputStream("abc".toByteArray()) }, { _, _ -> }, onCall = { it.cancel() })
			}
			assertEquals(0, server.requestCount)
			assertFalse("取消父目录预检后不能读取上传源", opened)
		}
	}

	@Test
	fun everyPutAndParentDirectoryAttemptRegistersItsCall() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(409))
			server.enqueue(MockResponse().setResponseCode(405))
			server.enqueue(MockResponse().setResponseCode(201))
			val methods = mutableListOf<String>()
			WebDavClient(server.url("/dav/"), OkHttpClient()).uploadStream("one.txt", 3, "text/plain", "folder",
				{ ByteArrayInputStream("abc".toByteArray()) }, { _, _ -> }, onCall = { methods += it.request().method })
			assertEquals(listOf("PUT", "MKCOL", "PUT"), methods)
			assertEquals(3, server.requestCount)
		}
	}
}
