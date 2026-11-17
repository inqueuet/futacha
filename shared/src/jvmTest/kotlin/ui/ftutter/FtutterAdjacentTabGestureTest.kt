package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterAdjacentTabGestureTest {
    @Test
    fun theNewActionsAreStoredAfterTheOriginalsAndListed() {
        assertEquals(FtutterGestureAction.LEFT_TAB, FtutterGestureAction.fromStored(19))
        assertEquals(FtutterGestureAction.RIGHT_TAB, FtutterGestureAction.fromStored(20))
        assertEquals("左隣のタブ", FtutterGestureAction.LEFT_TAB.label)
        assertEquals("右隣のタブ", FtutterGestureAction.RIGHT_TAB.label)
        // 原作の番号は変わらない。
        assertEquals(FtutterGestureAction.IMAGES, FtutterGestureAction.fromStored(18))
        assertEquals(21, FtutterGestureAction.entries.size)
    }

    @Test
    fun adjacentTabStopsAtTheEnds() {
        val tabs = listOf("a", "b", "c")
        assertEquals("c", ftutterAdjacentTab(tabs, "b", forward = true))
        assertEquals("a", ftutterAdjacentTab(tabs, "b", forward = false))
        assertNull(ftutterAdjacentTab(tabs, "c", forward = true))
        assertNull(ftutterAdjacentTab(tabs, "a", forward = false))
        assertNull(ftutterAdjacentTab(tabs, null, forward = true))
    }

    @Test
    fun aTapHidesTheImageViewersBarsButNotTheVideos() {
        assertTrue(ftutterViewerShowsBars(hiddenByTap = false, isVideo = false))
        assertFalse(ftutterViewerShowsBars(hiddenByTap = true, isVideo = false))
        assertTrue(ftutterViewerShowsBars(hiddenByTap = true, isVideo = true))
    }
}
