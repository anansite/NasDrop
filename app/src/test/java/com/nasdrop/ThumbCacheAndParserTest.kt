package com.nasdrop

import com.nasdrop.ui.thumbCacheKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbCacheAndParserTest {

	@Test
	fun thumbCacheKeyStaysIdenticalAcrossAlbumRenameForSharedThumbs() {
		val urlBefore = "http://192.168.1.1:8881/p/.nasdrop_thumbs/t_123456_abc.jpg?sign=old"
		val urlAfter = "http://192.168.1.1:8881/p/.nasdrop_thumbs/t_123456_abc.jpg?sign=new"
		val key1 = thumbCacheKey("Photos/旧相册/photo.jpg", urlBefore)
		val key2 = thumbCacheKey("Photos/新相册/photo.jpg", urlAfter)
		assertEquals("thumb:id:t_123456_abc.jpg", key1)
		assertEquals(key1, key2)
	}

	@Test
	fun thumbCacheKeyDifferentiatesDistinctLegacyUrls() {
		val k1 = thumbCacheKey("Photos/a.jpg", "http://host/dav/Photos/a.jpg")
		val k2 = thumbCacheKey("Photos/b.jpg", "http://host/dav/Photos/b.jpg")
		assertNotEquals(k1, k2)
	}

	@Test
	fun photoTimeParserParsesStandardAndWeChatTimestamps() {
		val imgTs = PhotoTimeParser.parseFromFilename("IMG_20240810_132352.jpg")
		val wxTs = PhotoTimeParser.parseFromFilename("wx_camera_1715000000000.jpg")
		assertTrue((imgTs ?: 0L) > 1_700_000_000_000L)
		assertEquals(1_715_000_000_000L, wxTs)
	}
}
