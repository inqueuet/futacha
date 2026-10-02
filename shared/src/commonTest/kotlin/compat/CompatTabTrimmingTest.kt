package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatTabTrimmingTest {
    private fun tab(no: Int, favorite: Boolean = false) = CompatTab(
        key = "tab-$no",
        canonicalUrl = "u$no",
        originalUrl = "u$no",
        boardKey = "b",
        boardName = "板",
        threadNo = no.toString(),
        title = "スレ",
        favorite = favorite,
        insertedAtEpochMillis = no.toLong(),
        contentUpdatedAtEpochMillis = no.toLong()
    )

    @Test
    fun belowTheTriggerNothingIsRemovedAndTabsAreNewestFirst() {
        val tabs = (1..COMPAT_TAB_LIMIT_TRIGGER).map { tab(it) }.shuffled()
        val trimmed = trimCompatTabs(tabs, activeTabKey = null)
        assertEquals((COMPAT_TAB_LIMIT_TRIGGER downTo 1).map { "tab-$it" }, trimmed.map(CompatTab::key))
    }

    @Test
    fun oldestNonFavouriteInactiveTabsAreRemovedBackToTheTarget() {
        val tabs = (1..105).map { tab(it, favorite = it == 1 || it == 5) }
        val trimmed = trimCompatTabs(tabs, activeTabKey = "tab-2").map(CompatTab::key)
        assertEquals(COMPAT_TAB_LIMIT_AFTER_TRIM, trimmed.size)
        assertTrue(listOf("tab-1", "tab-2", "tab-5").all { it in trimmed })
        // 15 removed: tab-3, tab-4 and tab-6..tab-18.
        assertTrue((listOf(3, 4) + (6..18)).none { "tab-$it" in trimmed })
        assertTrue("tab-19" in trimmed)
    }

    @Test
    fun protectedTabsMayKeepMoreThanTheTarget() {
        val tabs = (1..101).map { tab(it, favorite = it <= 95) }
        assertEquals(95, trimCompatTabs(tabs, activeTabKey = null).size)
    }

    @Test
    fun deleteKeysAreLeftOutOfBackupPreferences() {
        val exported = compatBackupExportPreferences(
            mapOf(
                "compat.common.commonPostDeleteKey" to "pass",
                "compat.lastDeleteKey" to "legacy",
                "compat.thread.threadFontSize" to "18"
            )
        )
        assertEquals(mapOf("compat.thread.threadFontSize" to "18"), exported)
    }
}
