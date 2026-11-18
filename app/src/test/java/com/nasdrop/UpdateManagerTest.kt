package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

	@Test
	fun `version comparison handles cross-version jumps and equality`() {
		assertTrue("跨版本更新 73 -> 75", UpdateManager.isNewer("1.0.75", "1.0.73"))
		assertTrue("跨版本更新 74 -> 76", UpdateManager.isNewer("1.0.76", "1.0.74"))
		assertTrue("常规升级 74 -> 75", UpdateManager.isNewer("1.0.75", "1.0.74"))
		assertTrue("大版本升级", UpdateManager.isNewer("2.0.0", "1.0.99"))

		assertFalse("同版本不更新", UpdateManager.isNewer("1.0.74", "1.0.74"))
		assertFalse("本地高于远端不更新", UpdateManager.isNewer("1.0.73", "1.0.74"))
	}

	@Test
	fun `selectBestApk picks arm64 for arm64 devices`() {
		val assets = listOf(
			ApkAsset("NasDrop-v1.0.76-x86_64.apk", "http://dl/x86", 2000),
			ApkAsset("NasDrop-v1.0.76-universal.apk", "http://dl/uni", 4000),
			ApkAsset("NasDrop-v1.0.76-arm64-v8a.apk", "http://dl/arm64", 2000),
		)
		val selected = UpdateManager.selectBestApk(assets, arrayOf("arm64-v8a", "armeabi-v7a"))
		assertEquals("NasDrop-v1.0.76-arm64-v8a.apk", selected?.name)
	}

	@Test
	fun `selectBestApk picks x86_64 for x86_64 emulator`() {
		val assets = listOf(
			ApkAsset("NasDrop-v1.0.76-universal.apk", "http://dl/uni", 4000),
			ApkAsset("NasDrop-v1.0.76-arm64-v8a.apk", "http://dl/arm64", 2000),
			ApkAsset("NasDrop-v1.0.76-x86_64.apk", "http://dl/x86", 2000),
		)
		val selected = UpdateManager.selectBestApk(assets, arrayOf("x86_64", "arm64-v8a"))
		assertEquals("NasDrop-v1.0.76-x86_64.apk", selected?.name)
	}

	@Test
	fun `selectBestApk falls back to universal when ABI not explicitly matched`() {
		val assets = listOf(
			ApkAsset("NasDrop-v1.0.76-x86_64.apk", "http://dl/x86", 2000),
			ApkAsset("NasDrop-v1.0.76-universal.apk", "http://dl/uni", 4000),
		)
		val selected = UpdateManager.selectBestApk(assets, arrayOf("mips64"))
		assertEquals("NasDrop-v1.0.76-universal.apk", selected?.name)
	}

	@Test
	fun `selectBestApk handles legacy single package name`() {
		val assets = listOf(
			ApkAsset("NasDrop-a31262b.apk", "http://dl/legacy", 48000000),
		)
		val selected = UpdateManager.selectBestApk(assets, arrayOf("arm64-v8a"))
		assertEquals("NasDrop-a31262b.apk", selected?.name)
	}

	@Test
	fun `selectBestApk avoids dirty legacy file when arch package exists`() {
		val assets = listOf(
			ApkAsset("NasDrop-c372e44.apk", "http://dl/dirty", 45000000),
			ApkAsset("NasDrop-v1.0.76-arm64-v8a.apk", "http://dl/arm64", 25000000),
		)
		val selected = UpdateManager.selectBestApk(assets, arrayOf("arm64-v8a"))
		assertEquals("NasDrop-v1.0.76-arm64-v8a.apk", selected?.name)
	}

	@Test
	fun `selectBestApk returns null when assets is empty`() {
		assertNull(UpdateManager.selectBestApk(emptyList(), arrayOf("arm64-v8a")))
	}
}
