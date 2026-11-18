package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 配置规范化应拒绝不可连接地址，并保留用户明确选择的连接模式及密码。 */
class SettingsTest {
	@Test fun normalizesAddressWithoutChangingPasswordOrSelectedMode() {
		val normalized = Settings(
			baseUrl = "  NAS.example.com/dav ", user = " alice ", password = " password ",
			connectionMode = ConnectionMode.Tailscale, useTailscale = false,
			hostname = " my-phone ", authKey = " key ", lastDir = "/Photos/",
		).normalized()
		assertEquals("https://nas.example.com/dav/", normalized.baseUrl)
		assertEquals("alice", normalized.user)
		assertEquals(" password ", normalized.password)
		assertEquals(ConnectionMode.Tailscale, normalized.connectionMode)
		assertTrue(normalized.useTailscale)
		assertEquals("my-phone", normalized.hostname)
		assertEquals("key", normalized.authKey)
		assertEquals("Photos", normalized.lastDir)
	}

	@Test fun directModeDisablesTunnelAndBlankHostnameUsesDefault() {
		val normalized = Settings(connectionMode = ConnectionMode.Direct, hostname = " ").normalized()
		assertFalse(normalized.useTailscale)
		assertEquals("nasdrop-android", normalized.hostname)
		assertEquals(normalized, normalized.normalized())
	}

	@Test fun rejectsInvalidServerAddressesBeforePersistence() {
		listOf("", "ftp://nas.example.com/dav", "https://", "https://user:secret@nas.example.com/", "https://nas.example.com/dav?token=secret", "https://nas.example.com/#fragment").forEach { url ->
			val error = runCatching { Settings(baseUrl = url).normalized() }.exceptionOrNull()
			assertTrue("Expected invalid address: $url", error is IllegalArgumentException)
		}
		assertTrue(runCatching { Settings(user = " ").normalized() }.exceptionOrNull() is IllegalArgumentException)
	}
}
