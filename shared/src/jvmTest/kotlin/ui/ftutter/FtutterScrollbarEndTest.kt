package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit 2026-11-17 L9: the ふたったー風 scroll bar reaches the bottom at the end of the list. */
class FtutterScrollbarEndTest {
    @Test fun atTheEndTheThumbIsAtTheBottom() {
        val count = ftutterScrollbarCount(total = 100, estimated = 8, scrollable = true)
        assertEquals(92, ftutterScrollbarFirst(100, first = 85, count = count, canScrollForward = false))
        assertEquals(85, ftutterScrollbarFirst(100, first = 85, count = count, canScrollForward = true))
    }

    @Test fun aScrollableListAlwaysShowsTheBar() {
        assertEquals(9, ftutterScrollbarCount(total = 10, estimated = 12, scrollable = true))
        assertEquals(12, ftutterScrollbarCount(total = 10, estimated = 12, scrollable = false))
    }
}
