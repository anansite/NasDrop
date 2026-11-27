package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewerAlbumTest {

	private fun img(name: String, modified: Long, effectiveDate: Long = modified, path: String = "Photos/宝宝/$name") =
		DavEntry(name = name, path = path, isDir = false, modified = modified, takenAt = effectiveDate)

	private fun vid(name: String, modified: Long, effectiveDate: Long = modified, path: String = "Photos/宝宝/$name") =
		DavEntry(name = name, path = path, isDir = false, modified = modified, takenAt = effectiveDate)

	@Test
	fun homeRecentGridSwipeOrderMatchesVisualModifiedOrderInsteadOfAllPhotosTakenAtOrder() {
		// 首页「近期精彩动态」展示最近上传/修改的 6 张照片（包含一张 2024 年拍摄但最近才上传的照片）
		val r0 = img("p0.jpg", modified = 6000L, effectiveDate = 6000L)
		val r1OldShot = img("IMG_20240810_113422.jpg", modified = 5000L, effectiveDate = 1000L)
		val r2 = img("p2.jpg", modified = 4000L, effectiveDate = 4000L)
		val r3 = img("p3.jpg", modified = 3000L, effectiveDate = 3000L)
		val r4 = img("p4.jpg", modified = 2000L, effectiveDate = 2000L)
		val r5 = img("p5.jpg", modified = 1500L, effectiveDate = 1500L)
		val extraRecent = img("p6.jpg", modified = 1400L, effectiveDate = 1400L)

		// allPhotos 仅包含最近拍摄的，不含 r1OldShot 且顺序完全不同
		val allPhotos = listOf(r0, r2, r3, r4, r5, extraRecent)
		val homeRecentMedia = listOf(r1OldShot, r5, r0, r3, r2, r4, extraRecent)
		val visualGrid = selectHomeRecentDisplayItems(homeRecentMedia, allPhotos, emptyList(), limit = 6)

		assertEquals(listOf(r0, r1OldShot, r2, r3, r4, r5).map { it.path }, visualGrid.map { it.path })

		val (viewerList, startIdx) = resolveViewerImageAlbum(
			entry = r1OldShot,
			currentTab = MainTab.Home,
			activePhotoGroupId = null,
			photoGroups = emptyList(),
			activeMemoryYearGroup = null,
			homeRecentMedia = homeRecentMedia,
			photoTimelineGroups = emptyList(),
			filteredEntries = listOf(r5, r1OldShot, r0), // 即使 Files 页缓存了乱序列表也不应干扰首页
			allPhotos = allPhotos,
			allVideos = emptyList(),
		)

		assertEquals(listOf(r0, r1OldShot, r2, r3, r4, r5).map { it.path }, viewerList.map { it.path })
		assertEquals(1, startIdx)
	}

	@Test
	fun homeRecentGridSkipsVideosWhileKeepingImageLeftRightOrder() {
		val r0 = img("a.jpg", modified = 6000L)
		val v1 = vid("clip.mp4", modified = 5000L)
		val r2 = img("b.jpg", modified = 4000L)
		val r3 = img("c.jpg", modified = 3000L)

		val (viewerList, startIdx) = resolveViewerImageAlbum(
			entry = r2,
			currentTab = MainTab.Home,
			activePhotoGroupId = null,
			photoGroups = emptyList(),
			activeMemoryYearGroup = null,
			homeRecentMedia = listOf(r0, v1, r2, r3),
			photoTimelineGroups = emptyList(),
			filteredEntries = emptyList(),
			allPhotos = listOf(r3, r0),
			allVideos = listOf(v1),
		)

		assertEquals(listOf(r0, r2, r3).map { it.path }, viewerList.map { it.path })
		assertEquals(1, startIdx)
	}

	@Test
	fun memoryDetailSheetUsesAlbumGroupedVisualOrderWhenOpenedFromHome() {
		val m1 = img("m1.jpg", modified = 100L, effectiveDate = 200L, path = "Photos/A/m1.jpg")
		val mv = vid("mv.mp4", modified = 100L, effectiveDate = 150L, path = "Photos/A/mv.mp4")
		val m2 = img("m2.jpg", modified = 100L, effectiveDate = 100L, path = "Photos/A/m2.jpg")
		val m3 = img("m3.jpg", modified = 100L, effectiveDate = 300L, path = "Photos/B/m3.jpg")
		val yearGroup = MemoryYearGroup(
			year = 2024,
			yearsAgo = 2,
			totalCount = 4,
			photoCount = 3,
			videoCount = 1,
			coverEntry = m1,
			albumGroups = listOf(
				MemoryAlbumGroup("A", "Photos/A", listOf(m1, mv, m2)),
				MemoryAlbumGroup("B", "Photos/B", listOf(m3)),
			),
		)

		val (viewerList, startIdx) = resolveViewerImageAlbum(
			entry = m3,
			currentTab = MainTab.Home,
			activePhotoGroupId = null,
			photoGroups = emptyList(),
			activeMemoryYearGroup = yearGroup,
			homeRecentMedia = emptyList(),
			photoTimelineGroups = emptyList(),
			filteredEntries = emptyList(),
			allPhotos = emptyList(),
			allVideos = emptyList(),
		)

		assertEquals(listOf(m1, m2, m3).map { it.path }, viewerList.map { it.path })
		assertEquals(2, startIdx)
	}
}
