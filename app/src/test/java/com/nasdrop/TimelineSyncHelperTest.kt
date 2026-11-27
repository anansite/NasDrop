package com.nasdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimelineSyncHelperTest {

	private fun img(name: String, dir: String = "Photos/宝宝") =
		DavEntry(name = name, path = "$dir/$name", isDir = false, modified = 1000L, takenAt = 1000L)

	@Test
	fun findTimelineGridScrollTargetAccountsForCollapsedPhotoGroupsAndEmptyDays() {
		val p1 = img("p1.jpg")
		val p2 = img("p2.jpg")
		val p3 = img("p3.jpg")
		val p4 = img("p4.jpg")
		val groups = listOf(
			TimelineGroup("今天", 1, listOf(p1)),
			TimelineGroup("昨天", 2, listOf(p2, p3)),
			TimelineGroup("前天", 1, listOf(p4)),
		)
		val photoGroups = listOf(
			PhotoGroup(id = "g1", albumDir = "Photos/宝宝", title = "合集", items = listOf(p1.path, p2.path)),
		)

		// p1 作为封面展示在「今天」(gridIndex: 0=hdr今天, 1=p1)
		// 「昨天」里 p2 被合集折叠，仅剩 p3 (gridIndex: 2=hdr昨天, 3=p3)
		// 「前天」里是 p4 (gridIndex: 4=hdr前天, 5=p4)
		val p4Target = findTimelineGridScrollTarget(groups, photoGroups, "Photos/宝宝", true, p4.path)
		assertNotNull(p4Target)
		assertEquals(p4.path, p4Target!!.itemKey)
		assertEquals(5, p4Target.gridIndex)

		// 若退出时停在被折叠的 p2，应精准回退定位到它所属合集封面 p1 的网格位置 (gridIndex = 1)
		val collapsedTarget = findTimelineGridScrollTarget(groups, photoGroups, "Photos/宝宝", true, p2.path)
		assertNotNull(collapsedTarget)
		assertEquals(p1.path, collapsedTarget!!.itemKey)
		assertEquals(1, collapsedTarget.gridIndex)

		// 展开合集或全局时光轴下不折叠，p4 顺延为 gridIndex = 6 (0=hdr,1=p1,2=hdr,3=p2,4=p3,5=hdr,6=p4)
		val expandedTarget = findTimelineGridScrollTarget(groups, photoGroups, "Photos/宝宝", false, p4.path)
		assertNotNull(expandedTarget)
		assertEquals(6, expandedTarget!!.gridIndex)
	}
}
