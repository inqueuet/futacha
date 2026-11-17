package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterList
import com.valoser.futacha.shared.ui.futaber.FutaberPreferenceKeys
import com.valoser.futacha.shared.ui.futaber.FutaberTabKey
import com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey
import com.valoser.futacha.shared.ui.futaber.futaberTabsToStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shared tab trim of the four modes (plan B4, 1-3, decision Q3). */
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
    fun upToTheLimitNothingIsRemovedAndTabsAreNewestFirst() {
        val tabs = (1..COMPAT_SHARED_TAB_LIMIT_DEFAULT).map { tab(it) }.shuffled()
        val trimmed = trimCompatTabs(tabs, activeTabKey = null)
        assertEquals((COMPAT_SHARED_TAB_LIMIT_DEFAULT downTo 1).map { "tab-$it" }, trimmed.map(CompatTab::key))
    }

    @Test
    fun theOldestTabsWithoutAStarGoBackToNinetyPercentOfWhatIsLeftForThem() {
        val tabs = (1..205).map { tab(it, favorite = it == 1 || it == 5) }
        val trimmed = trimCompatTabs(tabs, activeTabKey = "tab-2").map(CompatTab::key)
        // 3 exempt, budget 197, the 202 others go back to 177.
        assertEquals(180, trimmed.size)
        assertTrue(listOf("tab-1", "tab-2", "tab-5").all { it in trimmed })
        assertTrue((listOf(3, 4) + (6..28)).none { "tab-$it" in trimmed }, "the 25 oldest go")
        assertTrue("tab-29" in trimmed)
    }

    @Test
    fun manyStarsNeverMakeOneNewTabDropTheOthers() {
        // 195 stars + 6 others: the others keep half the limit, so nothing goes.
        val tabs = (1..201).map { tab(it, favorite = it <= 195) }
        assertEquals(201, trimCompatTabs(tabs, activeTabKey = null).size)
    }

    @Test
    fun withManyExemptTabsTenNewOnesStayAndOnlyTheOthersAreCounted() {
        val exempt = (1..195).mapTo(HashSet()) { "tab-$it" }
        val withTen = (1..205).map { tab(it) }
        assertEquals(emptySet(), planSharedTabTrim(withTen, exempt, limit = 200), "195 exempt + 10: nothing goes")
        val withMany = (1..305).map { tab(it) }
        val removed = planSharedTabTrim(withMany, exempt, limit = 200)
        assertEquals(20, removed.size, "the 110 others go back to 90")
        assertTrue(removed.none { it in exempt }, "an exempt tab is never trimmed")
        assertEquals((196..215).map { "tab-$it" }.toSet(), removed, "the oldest others")
        assertTrue(305 - removed.size >= 100)
    }

    @Test
    fun theLimitIsReadFromTheSharedSettingAndKeptInRange() {
        assertEquals(200, compatSharedTabLimit(emptyMap()))
        assertEquals(50, compatSharedTabLimit(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "50")))
        assertEquals(COMPAT_SHARED_TAB_LIMIT_MIN, compatSharedTabLimit(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "5")))
        assertEquals(COMPAT_SHARED_TAB_LIMIT_MAX, compatSharedTabLimit(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "9999")))
        assertEquals(200, compatSharedTabLimit(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "多い")))
        val tabs = (1..60).map { tab(it) }
        assertEquals(45, trimCompatTabs(tabs, null, mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "50")).size)
    }

    @Test
    fun theOpeningTheActiveAndTheHeldTabsAreNeverTrimmed() {
        val board = CompatBoard("may-b", "may/b", "https://may.2chan.net/b/", "https://may.2chan.net/b/futaba.php", 0)
        fun url(no: Int) = "https://may.2chan.net/b/res/$no.htm"
        fun key(no: Int) = compatTabKey(url(no))
        val tabs = (1..230).map { no ->
            CompatTab(key(no), url(no), url(no), board.key, board.name, "$no", "スレ$no",
                insertedAtEpochMillis = no.toLong(), contentUpdatedAtEpochMillis = no.toLong())
        }
        val summary = board.toBoardSummary()
        assertEquals(key(2), futaberCompatTabKey(summary, "2"))
        val preferences = mapOf(
            FutaberPreferenceKeys.TABS to futaberTabsToStore(listOf(FutaberTabKey(board.key, "2")), listOf(summary)),
            FtutterKeys.TABS to encodeFtutterList(listOf(url(3))),
            FtutterKeys.PROTECTED to encodeFtutterList(listOf(url(4))),
            FtutterKeys.OPENING to url(6)
        )
        val kept = trimCompatTabs(tabs, activeTabKey = key(7), preferences, listOf(board), screenHeldKeys = setOf(key(8)))
            .map(CompatTab::key)
        assertTrue(listOf(6, 7, 8).all { key(it) in kept }, "the opening, the active and the held tabs stay")
        // Plans B7b / B8b: ふたったー風's old rows and ふたばー風's old strip row keep nothing; their tabs are trimmed like the others.
        assertTrue(listOf(1, 2, 3, 4, 5).none { key(it) in kept }, "the oldest others go")
    }

    @Test
    fun theLimitIsNotRestoredFromABackup() {
        assertEquals(emptyMap(), compatBackupRestorePreferences(mapOf(COMPAT_SHARED_TAB_LIMIT_KEY to "20")))
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
