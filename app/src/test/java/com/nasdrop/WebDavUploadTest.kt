package com.nasdrop

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.IOException

/** 用真实 HTTP 边界区分“数据已发送”和“服务端已确认”，不依赖 NAS 或 Android UI。 */
class WebDavUploadTest {
	@Test
	fun lostResponseDoesNotSilentlyResendLargeBodyAndKeepsCause() {
		val server = MockWebServer()
		repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) }
		server.start()
		try {
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			val error = assertThrows(IOException::class.java) {
				client.uploadStream("video.mp4", 2L * 1024 * 1024, "video/mp4", "",
					{ ByteArrayInputStream(ByteArray(2 * 1024 * 1024)) }, { _, _ -> })
			}
			assertNotNull("保留传输异常，供日志定位真实失败原因", error.cause)
			assertEquals("已写出大文件不能自动整段重传", 1, server.requestCount)
		} finally {
			server.shutdown()
		}
	}

	@Test
	fun sentCallbackPrecedesServerConfirmationAndReportsUnknownLength() {
		val bodyRead = CountDownLatch(1)
		val allowResponse = CountDownLatch(1)
		val sent = CountDownLatch(1)
		val server = MockWebServer()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				bodyRead.countDown()
				allowResponse.await(5, TimeUnit.SECONDS)
				return MockResponse().setResponseCode(201)
			}
		}
		server.start()
		val executor = Executors.newSingleThreadExecutor()
		try {
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			var lastProgress = -1L
			val upload = executor.submit {
				client.uploadStream("one.txt", -1, "text/plain", "", { ByteArrayInputStream("upload-body".toByteArray()) },
					onProgress = { bytes, _ -> lastProgress = bytes }, onBodySent = { sent.countDown() })
			}
			assertTrue(sent.await(3, TimeUnit.SECONDS))
			assertTrue(bodyRead.await(3, TimeUnit.SECONDS))
			assertEquals("upload-body", server.takeRequest(3, TimeUnit.SECONDS)!!.body.readUtf8())
			assertEquals(11L, lastProgress)
			assertFalse("收到成功响应前不能完成上传", upload.isDone)
			allowResponse.countDown()
			upload.get(3, TimeUnit.SECONDS)
		} finally {
			allowResponse.countDown()
			executor.shutdownNow()
			server.shutdown()
		}
	}

	@Test
	fun largeUploadsToSameFolderCacheVerifiedParentMkcol() {
		val mkcolCount = java.util.concurrent.atomic.AtomicInteger(0)
		val server = MockWebServer()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				return when (request.method) {
					"MKCOL" -> {
						mkcolCount.incrementAndGet()
						MockResponse().setResponseCode(405)
					}
					"PUT" -> MockResponse().setResponseCode(201)
					else -> MockResponse().setResponseCode(200)
				}
			}
		}
		server.start()
		try {
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			val body = ByteArray(8 * 1024 * 1024)
			client.uploadStream("a.mp4", body.size.toLong(), "video/mp4", "Photos/Trip", { ByteArrayInputStream(body) }, { _, _ -> })
			client.uploadStream("b.mp4", body.size.toLong(), "video/mp4", "Photos/Trip", { ByteArrayInputStream(body) }, { _, _ -> })
			// 第一次逐级 MKCOL (Photos, Photos/Trip = 2次)，第二次命中缓存不再发 MKCOL
			assertEquals(2, mkcolCount.get())
		} finally {
			server.shutdown()
		}
	}
}
