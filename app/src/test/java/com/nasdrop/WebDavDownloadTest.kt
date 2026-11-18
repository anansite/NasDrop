package com.nasdrop

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/** 使用真实 HTTP 响应验证续传边界，失败时不能把整文件追加到已有片段。 */
class WebDavDownloadTest {
	@Test
	fun ignoredRangeRequiresRestartBeforeWriting() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(200).setBody("abcdef"))
	}

	@Test
	fun unsatisfiableRangeRequiresRestartBeforeWriting() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */6"))
	}

	@Test
	fun mismatchedRangeRequiresRestartBeforeWriting() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(206)
			.setHeader("Content-Range", "bytes 2-5/6").setBody("cdef"))
	}

	@Test
	fun missingRangeRequiresRestartBeforeWriting() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(206).setBody("def"))
	}

	@Test
	fun overflowingRangeTotalCannotBeTreatedAsUnknownLength() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(206)
			.setHeader("Content-Range", "bytes 3-5/999999999999999999999999").setBody("def"))
	}

	@Test
	fun unknownRangeTotalCannotFinishAnOpenEndedResume() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(206)
			.setHeader("Content-Range", "bytes 3-5/*").setBody("def"))
	}

	@Test
	fun initialPartialResponseWithUnknownTotalIsRejectedBeforeWriting() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(206).setBody("abc")
				.setHeader("Content-Range", "bytes 0-2/*"))
			val output = ByteArrayOutputStream()
			val error = assertThrows(ResumeRestartRequiredException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", output) { _, _ -> }
			}
			assertEquals(206, error.code)
			assertEquals("总长未知时无法确认文件完整，不能写入片段", 0, output.size())
		}
	}

	@Test
	fun matchingRangeAppendsAndReportsWholeFileProgress() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(206)
				.setHeader("Content-Range", "bytes 3-5/6").setBody("def"))
			val output = ByteArrayOutputStream().apply { write("abc".toByteArray()) }
			var progress = 0L to 0L
			WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", output, 3) { sent, total ->
				progress = sent to total
			}
			assertEquals("abcdef", output.toString("UTF-8"))
			assertEquals(6L to 6L, progress)
			assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
		}
	}

	@Test
	fun declaredRangeLengthMustMatchBodyLengthBeforeWriting() {
		assertRestartWithoutWriting(MockResponse().setResponseCode(206)
			.setHeader("Content-Range", "bytes 3-5/6").setBody("de"))
	}

	@Test
	fun truncatedBodyFailsInsteadOfReportingSuccess() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(206).setBody("de")
				.setHeader("Content-Length", "3").setHeader("Content-Range", "bytes 3-5/6")
				.setSocketPolicy(SocketPolicy.DISCONNECT_AT_END))
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			assertThrows(IOException::class.java) {
				client.download("one.txt", ByteArrayOutputStream(), 3) { _, _ -> }
			}
		}
	}

	@Test
	fun chunkedBodyMustStillCompleteItsDeclaredRange() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(206).setChunkedBody("de", 1)
				.setHeader("Content-Range", "bytes 3-5/6"))
			assertThrows(IOException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", ByteArrayOutputStream(), 3) { _, _ -> }
			}
		}
	}

	@Test
	fun strongEtagIsPublishedBeforeWritingAndReturnedWithIfRange() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setBody("abcdef").setHeader("ETag", "\"v1\"")
				.setHeader("Last-Modified", "Wed, 18 Nov 2026 01:00:00 GMT"))
			val output = ByteArrayOutputStream()
			var validator: String? = null
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			client.download("one.txt", output, onValidator = {
				assertEquals("身份元数据应先于响应体写入", 0, output.size())
				validator = it
			}) { _, _ -> }
			assertEquals("\"v1\"", validator)
			assertNull(server.takeRequest().getHeader("If-Range"))
			server.enqueue(MockResponse().setResponseCode(206).setBody("def")
				.setHeader("Content-Range", "bytes 3-5/6").setHeader("ETag", "\"v1\""))
			val resumed = ByteArrayOutputStream().apply { write("abc".toByteArray()) }
			client.download("one.txt", resumed, 3, validator = validator) { _, _ -> }
			assertEquals("\"v1\"", server.takeRequest().getHeader("If-Range"))
			assertEquals("abcdef", resumed.toString("UTF-8"))
		}
	}

	@Test
	fun weakEtagFallsBackToLastModifiedForResume() {
		MockWebServer().use { server ->
			val modified = "Wed, 18 Nov 2026 01:00:00 GMT"
			server.enqueue(MockResponse().setBody("abcdef").setHeader("ETag", "W/\"v1\"")
				.setHeader("Last-Modified", modified))
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			var validator: String? = null
			client.download("one.txt", ByteArrayOutputStream(), onValidator = { validator = it }) { _, _ -> }
			assertEquals(modified, validator)
			server.takeRequest()
			server.enqueue(MockResponse().setResponseCode(206).setBody("def")
				.setHeader("Content-Range", "bytes 3-5/6").setHeader("Last-Modified", modified))
			client.download("one.txt", ByteArrayOutputStream(), 3, validator = validator) { _, _ -> }
			assertEquals(modified, server.takeRequest().getHeader("If-Range"))
		}
	}

	@Test
	fun weakEtagAloneCannotIdentifyAFileVersion() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setBody("abc").setHeader("ETag", "W/\"v1\""))
			var reported = false
			WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", ByteArrayOutputStream(),
				onValidator = { reported = true; assertNull(it) }) { _, _ -> }
			assertTrue("缺少可用文件身份时仍需通知调用方清理旧身份", reported)
		}
	}

	@Test
	fun changedResumeValidatorRequiresRestartBeforeWriting() {
		MockWebServer().use { server ->
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			for ((old, fresh) in listOf("\"v1\"" to "\"v2\"",
				"Wed, 18 Nov 2026 01:00:00 GMT" to "Wed, 18 Nov 2026 02:00:00 GMT")) {
				val header = if (old.startsWith('"')) "ETag" else "Last-Modified"
				server.enqueue(MockResponse().setResponseCode(206).setBody("XYZ")
					.setHeader("Content-Range", "bytes 3-5/6").setHeader(header, fresh))
				val output = ByteArrayOutputStream().apply { write("abc".toByteArray()) }
				assertThrows(ResumeRestartRequiredException::class.java) {
					client.download("one.txt", output, 3, validator = old) { _, _ -> }
				}
				assertEquals("abc", output.toString("UTF-8"))
				assertEquals(old, server.takeRequest().getHeader("If-Range"))
			}
		}
	}

	private fun assertRestartWithoutWriting(response: MockResponse) {
		MockWebServer().use { server ->
			server.enqueue(response)
			val output = ByteArrayOutputStream().apply { write("abc".toByteArray()) }
			val error = assertThrows(ResumeRestartRequiredException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).download("one.txt", output, 3) { _, _ -> }
			}
			assertEquals(response.status.substring(9, 12).toInt(), error.code)
			assertEquals("校验失败前不得写入响应数据", "abc", output.toString("UTF-8"))
		}
	}
}
