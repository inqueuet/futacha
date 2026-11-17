package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatSharedTabLimit
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_TABS_CONSUMED_KEY
import com.valoser.futacha.shared.ui.ftutter.FTUTTER_TAB_PROFILE
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.FtutterSyncTabBar
import com.valoser.futacha.shared.ui.ftutter.decodeFtutterList
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterList
import com.valoser.futacha.shared.ui.ftutter.ftutterConsumedTabs
import com.valoser.futacha.shared.ui.ftutter.ftutterImportTabs
import com.valoser.futacha.shared.ui.ftutter.ftutterStarLimitNotice
import com.valoser.futacha.shared.ui.ftutter.ftutterTabSnapshot
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan B7a: ふたったー風's tab bar is a view of the shared tabs. Its old rows (`compat.ftutter.tabs` / `protected`) are
 * taken in once by the migration stage `tabs-ftutter-v1` and then only read for the threads with no shared place.
 */
class FtutterSharedTabBarTest {
    private fun url(threadId: String) = SingleMode.threadUrl(may, threadId)
    private val outside = "https://www.example.com/t/res/5.htm"

    @Test fun theBarShowsOtherModesTabsFirstThenItsOrderThenTheOldRows() {
        val tabs = listOf(SingleMode.tab(may, "1", 1), SingleMode.tab(may, "2", 2), SingleMode.tab(may, "3", 3), SingleMode.tab(img, "4", 4))
        val views = listOf(
            CompatSharedTabView(compatTabKey(url("2")), FTUTTER_TAB_PROFILE, 0, 1),
            CompatSharedTabView(compatTabKey(url("1")), FTUTTER_TAB_PROFILE, 1, 1),
            CompatSharedTabView(compatTabKey(url("3")), ExperienceProfile.FUTABER.persistedValue, 0, 1)
        )
        val snapshot = ftutterTabSnapshot(
            tabs, views, legacyTabs = listOf(outside, url("1"), url("9")), legacyProtected = setOf(outside, url("9")), consumed = setOf(url("9"))
        )
        assertEquals(listOf(SingleMode.threadUrl(img, "4"), url("3"), url("2"), url("1")), snapshot.shared, "tabs without this mode's mark newest first")
        assertEquals(listOf(outside), snapshot.legacy, "an old row already shared or taken in is not shown twice")
        assertEquals(setOf(outside), snapshot.protected)
    }

    @Test fun theStageTakesTheOldRowsInOnceAndAClosedTabDoesNotComeBack() = SingleMode.withStores { compat, state ->
        compat.savePreference(FtutterKeys.TABS, encodeFtutterList(listOf(url("1"), outside, url("2"))))
        compat.savePreference(FtutterKeys.PROTECTED, encodeFtutterList(listOf(url("2"), outside)))
        runSharedDataMigrations(state, compat)
        assertTrue(compat.sharedMigrationsSettled.value)
        val tabs = compat.tabs.first()
        assertEquals(setOf(url("1"), url("2")), tabs.mapTo(HashSet()) { it.canonicalUrl })
        assertTrue(tabs.single { it.canonicalUrl == url("2") }.favorite, "the protection is the ★")
        assertEquals(listOf(compatTabKey(url("1")), compatTabKey(url("2"))), compat.loadTabViews(FTUTTER_TAB_PROFILE).map { it.tabKey })
        assertEquals(setOf(url("1"), url("2")), ftutterConsumedTabs(compat.loadSharedData()))
        val bar = FtutterSyncTabBar(compat, SingleMode.boards, rows = mutableMapOf(
            FtutterKeys.TABS to compat.loadPreference(FtutterKeys.TABS)!!, FtutterKeys.PROTECTED to compat.loadPreference(FtutterKeys.PROTECTED)!!
        )).start()
        assertEquals(listOf(url("1"), url("2"), outside), bar.state.urls, "a thread with no shared place stays from the old row")
        assertTrue(outside in bar.state.protected)
        bar.state.close(listOf(url("1")))
        bar.settle()
        assertEquals(listOf(outside, url("2")), decodeFtutterList(bar.rows[FtutterKeys.TABS]), "closing a shared tab takes it out of the old row (audit L1)")
        compat.savePreference(FtutterKeys.TABS, encodeFtutterList(listOf(url("1"), outside, url("2"), url("3"))))
        runSharedDataMigrations(state, compat)
        assertEquals(setOf(url("2"), url("3")), compat.tabs.first().mapTo(HashSet()) { it.canonicalUrl }, "only the new row is taken in")
    }

    @Test fun closingAThreadOnlyInTheOldRowEditsTheRow() = SingleMode.withStores { compat, _ ->
        val bar = FtutterSyncTabBar(compat, SingleMode.boards, rows = mutableMapOf(
            FtutterKeys.TABS to encodeFtutterList(listOf(outside)), FtutterKeys.PROTECTED to encodeFtutterList(listOf(outside))
        )).start()
        bar.state.open(url("1"))
        bar.settle()
        bar.state.close(listOf(outside))
        bar.settle()
        assertEquals(listOf(url("1")), bar.state.urls)
        assertEquals(emptyList(), decodeFtutterList(bar.rows[FtutterKeys.TABS]).orEmpty())
        assertEquals(emptyList(), decodeFtutterList(bar.rows[FtutterKeys.PROTECTED]).orEmpty())
        assertEquals(1, compat.tabs.first().size, "the shared tab stays")
    }

    @Test fun manyOldTabsRaiseTheSharedLimitSoNoneIsTrimmed() = SingleMode.withStores { compat, state ->
        val urls = (1..250).map { url("$it") }
        compat.savePreference(FtutterKeys.TABS, encodeFtutterList(urls))
        runSharedDataMigrations(state, compat)
        assertEquals("250", compat.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
        assertEquals(250, compat.tabs.first().size)
        assertEquals(250, compat.loadTabViews(FTUTTER_TAB_PROFILE).size)
    }

    @Test fun theStarIsRefusedAtTheCap() = SingleMode.withStores { compat, _ ->
        compat.savePreference(COMPAT_SHARED_TAB_LIMIT_KEY, "20")
        val bar = FtutterSyncTabBar(compat, SingleMode.boards).start()
        (1..11).forEach { bar.state.open(url("$it")); bar.settle() }
        (1..10).forEach { bar.state.toggleProtect(url("$it")); bar.settle() }
        assertEquals(10, compat.tabs.first().count { it.favorite })
        bar.state.toggleProtect(url("11"))
        bar.settle()
        assertEquals(listOf(ftutterStarLimitNotice(10)), bar.notices)
        assertFalse(url("11") in bar.state.protected, "the refused ★ is not shown")
        assertEquals(10, compat.tabs.first().count { it.favorite })
    }

    @Test fun aBackupsTabsGoToTheSharedTabsAndTheRestToTheRows() = SingleMode.withStores { compat, state ->
        val rows = mutableMapOf<String, String>()
        val unstarred = ftutterImportTabs(
            compat, state.boards.first(), emptyList(),
            mapOf(FtutterKeys.TABS to encodeFtutterList(listOf(url("1"), outside)), FtutterKeys.PROTECTED to encodeFtutterList(listOf(url("1"), outside))),
            nowEpochMillis = 5
        ) { key, transform -> rows[key] = encodeFtutterList(transform(decodeFtutterList(rows[key]).orEmpty())) }
        assertEquals(0, unstarred)
        assertTrue(compat.tabs.first().single().favorite)
        assertEquals(listOf(outside), decodeFtutterList(rows[FtutterKeys.TABS]))
        assertEquals(listOf(outside), decodeFtutterList(rows[FtutterKeys.PROTECTED]))
        assertNull(compat.loadSharedData()[FTUTTER_TABS_CONSUMED_KEY], "an import is not the migration's record")
    }

    @Test fun aClosedThreadWithNoSharedPlaceComesBackAtItsPlaceInThisSession() = SingleMode.withStores { compat, _ ->
        val other = "https://www.example.com/t/res/6.htm"
        val bar = FtutterSyncTabBar(compat, SingleMode.boards, rows = mutableMapOf(FtutterKeys.TABS to encodeFtutterList(listOf(outside, other)))).start()
        bar.state.open(url("1"))
        bar.settle()
        bar.state.close(listOf(url("1")))
        bar.settle()
        bar.state.close(listOf(outside))
        bar.settle()
        assertEquals(1, bar.state.legacyClosedCount)
        val back = mutableListOf<String?>()
        bar.state.restore { back += it?.url }
        bar.settle()
        bar.state.restore { back += it?.url }
        bar.settle()
        assertEquals(listOf<String?>(outside, url("1")), back, "the newest close first")
        assertEquals(listOf(url("1"), outside, other), bar.state.urls)
        assertEquals(0, bar.state.legacyClosedCount)
    }

    /** The B4 test's continuation: 195 tabs taken in from ふたったー風, then ふたちゃ opens ten. */
    /** Plan B7b: the tabs taken in are trimmed like the others (the old row exempts nothing), and most stay. */
    @Test fun tabsTakenInAreTrimmedLikeTheOthersWhenAnotherModeOpensTen() = SingleMode.withStores { compat, state ->
        compat.savePreference(FtutterKeys.TABS, encodeFtutterList((1..195).map { url("$it") }))
        runSharedDataMigrations(state, compat)
        assertEquals(200, compatSharedTabLimit(compat.preferences.first()), "the limit is raised to fit them")
        (1001..1010).forEach { compat.openTab(SingleMode.tab(may, "$it", insertedAt = System.currentTimeMillis() + it)) }
        val kept = compat.tabs.first().mapTo(HashSet()) { it.canonicalUrl }
        assertTrue(kept.size in 100..200, "at least 100 left and within the limit: ${kept.size}")
        assertTrue((1001..1010).all { SingleMode.threadUrl(may, "$it") in kept }, "the new tabs are not the ones that go")
        assertTrue((1..195).count { url("$it") in kept } >= 100, "most of ふたったー風's tabs stay")
    }

    @Test fun aDragKeepsTheOrderOfSharedTabsAndOfThreadsOnlyInTheOldRow() = SingleMode.withStores { compat, _ ->
        val other = "https://www.example.com/t/res/6.htm"
        val bar = FtutterSyncTabBar(compat, SingleMode.boards, rows = mutableMapOf(FtutterKeys.TABS to encodeFtutterList(listOf(outside, other)))).start()
        listOf("2", "1").forEach { bar.state.open(url(it)); bar.settle() }
        assertEquals(listOf(url("1"), url("2"), outside, other), bar.state.urls)
        bar.state.move(3, 0)
        bar.settle()
        assertEquals(listOf(url("1"), url("2"), other, outside), bar.state.urls, "shared tabs first, then the old row in its new order")
        bar.state.move(1, 0)
        bar.settle()
        assertEquals(listOf(url("2"), url("1"), other, outside), bar.state.urls)
        assertEquals(listOf(url("2"), url("1")), compat.loadTabViews(FTUTTER_TAB_PROFILE).map { v -> compat.tabs.first().single { it.key == v.tabKey }.canonicalUrl })
        assertEquals(listOf(other, outside), decodeFtutterList(bar.rows[FtutterKeys.TABS]), "the old row's order follows the drag")
    }
}
