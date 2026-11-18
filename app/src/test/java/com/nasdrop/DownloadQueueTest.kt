package com.nasdrop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** 用真实慢速 HTTP 响应验证暂停/取消和唯一写者，不借助 Android UI 状态。 */
class DownloadQueueTest {
	@Test
	fun pauseResumeKeepsOneWriterAndPublishesExactFile() = runBlocking {
		val root = Files.createTempDirectory("nasdrop-download-test").toFile()
		val data = ByteArray(256 * 1024) { (it % 251).toByte() }
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		MockWebServer().use { server ->
			server.dispatcher = object : Dispatcher() {
				override fun dispatch(request: RecordedRequest): MockResponse {
					val offset = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toInt() ?: 0
					return MockResponse().setResponseCode(if (offset > 0) 206 else 200)
						.setHeader("ETag", "\"v1\"")
						.apply { if (offset > 0) setHeader("Content-Range", "bytes $offset-${data.lastIndex}/${data.size}") }
						.setBody(Buffer().write(data, offset, data.size - offset))
						.throttleBody(8192, 10, TimeUnit.MILLISECONDS)
				}
			}
			val published = root.resolve("published.bin")
			val queue = DownloadQueue(scope, root.resolve("parts"),
				{ WebDavClient(server.url("/dav/"), OkHttpClient()) },
				{ _, part -> part.copyTo(published, overwrite = true); published.toURI().toString() })
			try {
				val task = queue.enqueue("one.bin", "folder/one.bin", data.size.toLong())
				withTimeout(5000) { queue.tasks.first { tasks -> tasks.any { it.id == task.id && it.sentBytes > 0 } } }
				queue.pause(task.id)
				assertEquals(TransferStatus.PAUSED, queue.tasks.value.single().status)
				assertFalse(published.exists())
				queue.resume(task.id)
				withTimeout(5000) { queue.tasks.first { tasks -> tasks.any { it.id == task.id && it.status == TransferStatus.COMPLETED } } }
				assertArrayEquals(data, published.readBytes())
				assertEquals(2, server.requestCount)
			} finally {
				queue.close()
				scope.cancel()
				root.deleteRecursively()
			}
		}
	}

	@Test
	fun cancelledDownloadNeverPublishesAndCanBeRetried() = runBlocking {
		val root = Files.createTempDirectory("nasdrop-cancel-test").toFile()
		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		val published = AtomicInteger()
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setHeader("ETag", "\"v1\"")
				.setBody(Buffer().write(ByteArray(256 * 1024))).throttleBody(8192, 20, TimeUnit.MILLISECONDS))
			server.enqueue(MockResponse().setBody("retry"))
			val queue = DownloadQueue(scope, root.resolve("parts"),
				{ WebDavClient(server.url("/dav/"), OkHttpClient()) },
				{ _, _ -> published.incrementAndGet(); "file:///published" })
			try {
				val task = queue.enqueue("one.bin", "one.bin", 256 * 1024L)
				withTimeout(5000) { queue.tasks.first { tasks -> tasks.any { it.id == task.id && it.sentBytes > 0 } } }
				queue.cancel(task.id)
				assertEquals(TransferStatus.CANCELLED, queue.tasks.value.single().status)
				assertEquals(0, published.get())
				queue.retry(task.id)
				withTimeout(5000) { queue.tasks.first { tasks -> tasks.any { it.status == TransferStatus.COMPLETED } } }
				assertEquals(1, published.get())
			} finally {
				queue.close()
				scope.cancel()
				root.deleteRecursively()
			}
		}
	}
}
