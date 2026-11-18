package com.nasdrop

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

/** 通过真实 TLS 握手证明证书开关生效，并验证两种策略的连接池隔离。 */
class NetworkSecurityTest {
	@Test fun credentialOriginsRequireMatchingSchemeHostAndPort() {
		val origin = "https://nas.example.com:8443/dav/".toHttpUrl()
		assertTrue("https://nas.example.com:8443/d/image?sign=test".toHttpUrl().matchesOrigin(origin))
		listOf("https://cdn.example.com:8443/image", "http://nas.example.com:8443/", "https://nas.example.com/", "https://nas.example.com.evil.invalid:8443/").forEach {
			assertFalse(it.toHttpUrl().matchesOrigin(origin))
		}
		assertFalse(origin.matchesOrigin(null))
		assertTrue("http://127.0.0.1:9321/dav/".toHttpUrl().isBridgeOrigin(9321))
		listOf("https://127.0.0.1:9321/", "http://127.0.0.1:9322/", "http://localhost:9321/", "http://192.168.1.1:9321/").forEach {
			assertFalse(it.toHttpUrl().isBridgeOrigin(9321))
		}
		assertFalse("http://127.0.0.1:9321/".toHttpUrl().isBridgeOrigin(0))
	}

	@Test fun selfSignedServerRequiresExplicitCertificateBypass() {
		val store = KeyStore.getInstance("PKCS12").apply {
			NetworkSecurityTest::class.java.getResourceAsStream("/test-server.p12").use { load(it, "test-only".toCharArray()) }
		}
		val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "test-only".toCharArray()) }
		val ssl = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
		MockWebServer().use { server ->
			server.useHttps(ssl.socketFactory, false)
			server.start()
			val strict = OkHttpClient.Builder().withCertificatePolicy(false).build()
			val permissive = strict.newBuilder().withCertificatePolicy(true).build()
			val request = Request.Builder().url(server.url("/dav/")).build()
			server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
			assertTrue(runCatching { strict.newCall(request).execute().close() }.exceptionOrNull() is SSLHandshakeException)
			permissive.newCall(request).execute().use { assertEquals("ok", it.body!!.string()) }
			assertNotSame(strict.connectionPool, permissive.connectionPool)
			// 放行请求建立过连接后，校验请求仍须拒绝自签证书。
			assertTrue(runCatching { strict.newCall(request).execute().close() }.exceptionOrNull() is SSLHandshakeException)
		}
	}
}
