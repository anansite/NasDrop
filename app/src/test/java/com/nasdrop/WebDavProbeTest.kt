package com.nasdrop

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** 连通性测试必须校验 WebDAV 权限和配置路径。 */
class WebDavProbeTest {
	@Test
	fun directoryAuthenticationFailureExplainsCredentialRecovery() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(401))
			val error = assertThrows(DavException::class.java) {
				WebDavClient(server.url("/dav/"), OkHttpClient()).list("")
			}
			assertTrue(error.message.orEmpty().contains("账号"))
			assertTrue(error.message.orEmpty().contains("密码"))
		}
	}
	@Test
	fun authenticationAndMissingPathCannotPassProbe() {
		MockWebServer().use { server ->
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			for (code in listOf(401, 403, 404)) {
				server.enqueue(MockResponse().setResponseCode(code))
				val error = assertThrows(DavException::class.java) { client.probe() }
				assertEquals(code, error.code)
			}
		}
	}

	@Test
	fun multiStatusPassesLightweightProbe() {
		MockWebServer().use { server ->
			server.enqueue(MockResponse().setResponseCode(207))
			WebDavClient(server.url("/dav/"), OkHttpClient()).probe()
			val request = server.takeRequest()
			assertEquals("PROPFIND", request.method)
			assertEquals("0", request.getHeader("Depth"))
		}
	}

	@Test
	fun existenceCheckOnlyTreatsMissingFileAsAbsent() {
		MockWebServer().use { server ->
			val client = WebDavClient(server.url("/dav/"), OkHttpClient())
			server.enqueue(MockResponse().setResponseCode(207))
			assertTrue(client.exists("folder/one.txt"))
			val request = server.takeRequest()
			assertEquals("/dav/folder/one.txt", request.path)
			assertEquals("PROPFIND", request.method)
			assertEquals("0", request.getHeader("Depth"))
			server.enqueue(MockResponse().setResponseCode(404))
			assertFalse(client.exists("missing.txt"))
			for (code in listOf(401, 403, 500)) {
				server.enqueue(MockResponse().setResponseCode(code))
				val error = assertThrows(DavException::class.java) { client.exists("one.txt") }
				assertEquals(code, error.code)
			}
		}
	}
}
