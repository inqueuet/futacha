package com.valoser.futacha.shared.compat

import kotlin.test.*

/** Plan B9: the shared tab limit's setting (ふたちゃ and ふたったー風): the floor, the refusals and the question. */
class CompatTabLimitSettingTest {
    private val boardKey = compatBoardKey("https://may.2chan.net/b/")
    private fun tab(no: Int, favorite: Boolean = false) = "https://may.2chan.net/b/res/$no.htm".let { url ->
        CompatTab(compatTabKey(url), url, url, boardKey, "may", "$no", "t$no", favorite = favorite,
            insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())
    }

    @Test fun theFloorIsTheStarsPlusTenWithinTheRange() {
        assertEquals(COMPAT_SHARED_TAB_LIMIT_MIN, compatTabLimitFloor(emptyList()))
        assertEquals(40, compatTabLimitFloor((1..30).map { tab(it, favorite = true) }))
        assertEquals(COMPAT_SHARED_TAB_LIMIT_MAX, compatTabLimitFloor((1..995).map { tab(it, favorite = true) }))
    }

    @Test fun aValueBelowTheFloorOrAboveTheMaximumIsRefused() {
        val tabs = (1..30).map { tab(it, favorite = true) }
        assertNotNull(compatTabLimitRefusal(39, tabs))
        assertNull(compatTabLimitRefusal(40, tabs))
        assertNotNull(compatTabLimitRefusal(COMPAT_SHARED_TAB_LIMIT_MAX + 1, tabs))
        assertNotNull(compatTabLimitRefusal(19, emptyList()))
    }

    @Test fun loweringCountsTheTabsTheNextTrimClosesAndAsks() {
        val tabs = (1..100).map { tab(it, favorite = it <= 5) }
        // 95 without a star, limit 50: budget max(50 − 5, 25) = 45 is exceeded, kept 40, so 55 close.
        val count = compatTabLimitTrimCount(tabs, 50, activeTabKey = null, emptyMap(), emptyList(), emptyList())
        assertEquals(55, count)
        assertTrue(compatTabLimitQuestion(50, count)!!.contains("55件"))
        // The active tab is exempt: one tab fewer to count, and the budget is one smaller (44, kept 39 of 94), so 55 again.
        assertEquals(55, compatTabLimitTrimCount(tabs, 50, activeTabKey = tabs[5].key, emptyMap(), emptyList(), emptyList()))
    }

    @Test fun nothingClosingAsksNothing() {
        val tabs = (1..10).map { tab(it) }
        assertEquals(0, compatTabLimitTrimCount(tabs, 20, null, emptyMap(), emptyList(), emptyList()))
        assertNull(compatTabLimitQuestion(20, 0))
    }
}
