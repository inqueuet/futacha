package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY
import com.valoser.futacha.shared.compat.CompatSharedTabView
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.shared.runSharedDataMigrations
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan B8a: ふたばー風's strip is a view of the shared tabs (its marks, then the other modes' tabs, then the old row's
 * threads with no shared place); its old row `compat.futaber.tabs` is taken in once by the stage `tabs-futaber-v1`.
 */
class FutaberSharedTabsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private val boards = listOf(may, tutorial)
    private val boardKey = compatBoardKey("https://may.2chan.net/b/")
    private fun url(id: String) = "https://may.2chan.net/b/res/$id.htm"
    private fun key(id: String) = FutaberTabKey("may-b", id)
    private fun tab(id: String, insertedAt: Long, replies: Int = 0, favorite: Boolean = false) =
        CompatTab(compatTabKey(url(id)), url(id), url(id), boardKey, "二次元裏", id, "題$id", replyCount = replies,
            insertedAtEpochMillis = insertedAt, contentUpdatedAtEpochMillis = insertedAt, favorite = favorite)
    private fun entry(id: String, title: String, posts: Int) = ThreadHistoryEntry(
        threadId = id, boardId = "may-b", title = title, titleImageUrl = "", boardName = "二次元裏",
        boardUrl = url(id), lastVisitedEpochMillis = 1, replyCount = posts
    )

    private fun withStores(block: suspend (DesktopCompatibilityStore, AppStateStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("futaber-shared-tabs").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        val state = AppStateStore(FakePlatformStateStorage())
        try {
            store.initialize()
            store.importModernBoards(listOf(may))
            state.setBoards(boards)
            block(store, state)
        } finally { store.close(); root.deleteRecursively() }
    }

    private class Row(var keys: List<FutaberTabKey> = emptyList())

    private fun writer(store: DesktopCompatibilityStore, row: Row, notices: MutableList<String> = mutableListOf(), now: () -> Long = { 1_000L }) =
        FutaberStoreTabWriter(store, { boards }, { emptyList() }, { transform -> row.keys = transform(row.keys) }, { notices += it }, now)

    @Test fun theStripShowsItsMarksThenTheOtherModesTabsOldestFirstThenTheOldRow() {
        val shared = listOf(tab("1", 1), tab("2", 2), tab("3", 3), tab("4", 4))
        val views = listOf(
            CompatSharedTabView(compatTabKey(url("3")), FUTABER_TAB_PROFILE, 0, 1),
            CompatSharedTabView(compatTabKey(url("1")), FUTABER_TAB_PROFILE, 1, 1),
            CompatSharedTabView(compatTabKey(url("4")), "ftutter", 0, 1)
        )
        val row = listOf(FutaberTabKey("t", "9"), key("1"), key("8"), key("7"))
        val snapshot = futaberTabSnapshot(shared, views, row, consumed = setOf(compatTabKey(url("7"))), boards)
        assertEquals(
            listOf(key("3"), key("1"), key("2"), key("4"), FutaberTabKey("t", "9"), key("8")), snapshot.keys,
            "Q1: the other modes' tabs show; a thread taken in and closed since (7) does not come back"
        )
        assertEquals(setOf(key("1"), key("2"), key("3"), key("4")), snapshot.shared.keys)
    }

    /** Audit F1: a tab whose thread has no history row is shown from the shared tab (replies + 1 = posts). */
    @Test fun aTabWithoutAHistoryRowShowsFromTheSharedTab() {
        val views = futaberTabViews(listOf(key("1"), key("2")), listOf(entry("1", "一", 6)), shared = mapOf(key("2") to tab("2", 1, replies = 4)))
        assertEquals(listOf("一", "題2"), views.map { it.title })
        assertEquals(listOf(6, 5), views.map { it.replyCount })
        assertEquals(4, futaberShownReplyCount(views[1].replyCount))
    }

    @Test fun theStageTakesTheOldRowInOnceAndAClosedTabDoesNotComeBack() = withStores { store, state ->
        store.savePreference(FutaberPreferenceKeys.TABS, futaberTabsToStore(listOf(key("1"), FutaberTabKey("t", "9"), key("2")), boards))
        runSharedDataMigrations(state, store)
        assertEquals(listOf(url("1"), url("2")), store.tabs.first().sortedBy { it.threadNo }.map { it.canonicalUrl })
        assertEquals(listOf(compatTabKey(url("1")), compatTabKey(url("2"))), store.loadTabViews(FUTABER_TAB_PROFILE).map { it.tabKey })
        assertEquals(setOf(compatTabKey(url("1")), compatTabKey(url("2"))), futaberConsumedTabs(store.loadSharedData()))
        store.closeTabs(setOf(compatTabKey(url("1"))), 2_000L, closedBy = FUTABER_TAB_PROFILE)
        store.savePreference(FutaberPreferenceKeys.TABS, futaberTabsToStore(listOf(key("1"), FutaberTabKey("t", "9"), key("2"), key("3")), boards))
        runSharedDataMigrations(state, store)
        assertEquals(setOf("2", "3"), store.tabs.first().mapTo(HashSet()) { it.threadNo }, "only the new row key is taken in")
    }

    @Test fun manyOldTabsRaiseTheSharedLimitSoNoneIsTrimmed() = withStores { store, state ->
        store.savePreference(FutaberPreferenceKeys.TABS, futaberTabsToStore((1..250).map { key("$it") }, boards))
        runSharedDataMigrations(state, store)
        assertEquals("250", store.loadPreference(COMPAT_SHARED_TAB_LIMIT_KEY))
        assertEquals(250, store.tabs.first().size)
    }

    /** Q3b: the 51st tab is added (this mode has no cap of its own any more). */
    @Test fun addingASharedTabMarksItAndTheFiftyFirstIsTaken() = withStores { store, _ ->
        val row = Row()
        val w = writer(store, row)
        var strip = emptyList<FutaberTabKey>()
        (1..51).forEach { id ->
            w.apply(strip, futaberToggleTab(strip, key("$id")))
            strip = futaberStoreTabSnapshot(store, boards, emptySet()).keys
        }
        assertEquals((1..51).map { key("$it") }, strip)
        assertEquals(51, store.loadTabViews(FUTABER_TAB_PROFILE).size)
        assertTrue(row.keys.isEmpty(), "a shared tab is not written to the old row")
    }

    @Test fun closingAnotherModesTabClosesItEverywhereAndRestoreBringsItBack() = withStores { store, _ ->
        store.openTab(tab("5", 5))
        val w = writer(store, Row())
        val strip = futaberStoreTabSnapshot(store, boards, emptySet()).keys
        assertEquals(listOf(key("5")), strip)
        w.apply(strip, emptyList())
        assertTrue(store.tabs.first().isEmpty())
        assertEquals(1, w.restorableCount())
        assertEquals(key("5"), w.restore())
        assertEquals(listOf("5"), store.tabs.first().map { it.threadNo })
        assertEquals(0, w.restorableCount())
    }

    @Test fun aThreadWithNoSharedPlaceStaysInTheRowAndComesBackAtItsPlace() = withStores { store, _ ->
        val other = FutaberTabKey("t", "9")
        val row = Row(listOf(FutaberTabKey("t", "8"), other))
        val w = writer(store, row)
        w.apply(row.keys, listOf(FutaberTabKey("t", "8")))
        assertEquals(listOf(FutaberTabKey("t", "8")), row.keys)
        assertEquals(other, w.restore())
        assertEquals(listOf(FutaberTabKey("t", "8"), other), row.keys)
        assertNull(w.restore())
    }

    /**
     * Plan B8b: nothing but this mode edits its old row now, so a row entry of a thread that gets a shared place leaves the
     * row when this mode opens or closes that tab (it would otherwise come back from the row once the tab is gone).
     */
    @Test fun aSharedTabsKeyLeavesTheOldRowWhenThisModeOpensOrClosesIt() = withStores { store, _ ->
        val row = Row(listOf(key("1"), key("2"), FutaberTabKey("t", "9")))
        val w = writer(store, row)
        w.apply(emptyList(), listOf(key("1")))
        assertEquals(listOf(key("2"), FutaberTabKey("t", "9")), row.keys, "opened as a shared tab: the row entry goes")
        store.openTab(tab("2", 2))
        w.apply(listOf(key("1"), key("2")), listOf(key("1")))
        assertEquals(listOf(FutaberTabKey("t", "9")), row.keys, "closed as a shared tab: the row entry goes too")
        assertEquals(listOf(compatTabKey(url("1"))), store.tabs.first().map { it.key })
        store.savePreference(FutaberPreferenceKeys.TABS, futaberTabsToStore(row.keys, boards))
        assertEquals(listOf(key("1"), FutaberTabKey("t", "9")), futaberStoreTabSnapshot(store, boards, emptySet()).keys)
    }

    @Test fun theStarredKeysAreTheSharedTabsWithAStar() {
        val starred = tab("1", 1, favorite = true)
        assertEquals(setOf(starred.key), futaberStarredTabKeys(listOf(starred, starred.copy(key = "other", favorite = false))))
    }
}
