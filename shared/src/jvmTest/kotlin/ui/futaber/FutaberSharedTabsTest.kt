package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-B4: this mode's strip and stars as shared tabs, without taking the other modes' selected tab. */
class FutaberSharedTabsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val tutorial = BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", "")
    private fun entry(id: String, title: String, posts: Int) = ThreadHistoryEntry(
        threadId = id, boardId = "may-b", title = title, titleImageUrl = "", boardName = "二次元裏",
        boardUrl = "https://may.2chan.net/b/res/$id.htm", lastVisitedEpochMillis = 1, replyCount = posts
    )
    private fun star(id: String) = FutaberFavorite("may-b", id, "星$id", "", "二次元裏", 11, 1L)

    @Test
    fun theStripBecomesViewsInOrderAndStarsBecomeFavorites() {
        val plan = futaberSharedTabsPlan(
            tabKeys = listOf(FutaberTabKey("may-b", "2"), FutaberTabKey("may-b", "1"), FutaberTabKey("t", "9")),
            favorites = listOf(star("3")),
            boards = listOf(may, tutorial),
            history = listOf(entry("1", "一", 6), entry("2", "二", 3)),
            nowEpochMillis = 100L
        )
        // The tutorial board has no place in the shared store.
        assertEquals(listOf("2", "1", "3"), plan.tabs.map { it.threadNo })
        assertEquals(listOf(0, 1), plan.views.map { it.ordinal })
        assertEquals(listOf(compatTabKey("https://may.2chan.net/b/res/2.htm"), compatTabKey("https://may.2chan.net/b/res/1.htm")), plan.views.map { it.tabKey })
        // Replies, not posts: 6 posts are 5 replies; the star's 11 posts are 10.
        assertEquals(listOf(2, 5, 10), plan.tabs.map { it.replyCount })
        assertEquals(setOf(compatTabKey("https://may.2chan.net/b/res/3.htm")), plan.favoriteKeys)
        assertEquals("星3", plan.tabs.last().title)
    }

    @Test
    fun applyingAddsTabsStarsAndViewsAndLeavesTheOtherModesSelectedTab() = runBlocking {
        val root = Files.createTempDirectory("futaber-shared-tabs").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            store.importModernBoards(listOf(may))
            val boardKey = compatBoardKey("https://may.2chan.net/b/")
            val theirUrl = "https://may.2chan.net/b/res/50.htm"
            val theirs = CompatTab(compatTabKey(theirUrl), theirUrl, theirUrl, boardKey, "二次元裏", "50", "他モード", replyCount = 1, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)
            store.openTab(theirs)
            val selected = store.workspace.first().activeTabKey
            assertEquals(theirs.key, selected)

            val plan = futaberSharedTabsPlan(listOf(FutaberTabKey("may-b", "1")), listOf(star("3")), listOf(may), listOf(entry("1", "一", 6)), 100L)
            val first = futaberApplySharedTabs(store, plan, listOf(may), emptySet())
            val tabs = store.tabs.first()
            assertEquals(setOf("50", "1", "3"), tabs.map { it.threadNo }.toSet())
            assertEquals(selected, store.workspace.first().activeTabKey, "the other mode's selected tab does not move")
            assertTrue(tabs.single { it.threadNo == "3" }.favorite)
            assertFalse(tabs.single { it.threadNo == "1" }.favorite)
            assertEquals(1, store.loadTabViews(FUTABER_TAB_PROFILE).size)

            // The star is taken off in this mode: it is taken off the shared tab, which stays.
            val none = futaberSharedTabsPlan(listOf(FutaberTabKey("may-b", "1")), emptyList(), listOf(may), listOf(entry("1", "一", 6)), 200L)
            futaberApplySharedTabs(store, none, listOf(may), first)
            assertFalse(store.tabs.first().single { it.threadNo == "3" }.favorite)
            // The strip dropped to nothing: the views go, the tabs stay for the others.
            futaberApplySharedTabs(store, futaberSharedTabsPlan(emptyList(), emptyList(), listOf(may), emptyList(), 300L), listOf(may), emptySet())
            assertTrue(store.loadTabViews(FUTABER_TAB_PROFILE).isEmpty())
            assertEquals(3, store.tabs.first().size)
        } finally { store.close(); root.deleteRecursively() }
    }

    /** Audit A6: a star this mode takes off is its own before the store reports it, so it is not read as another mode's. */
    @Test fun aStarThisModeTakesOffIsNotReadAsAnotherModes() {
        val url = "https://may.2chan.net/b/res/1.htm"
        val starred = CompatTab(compatTabKey(url), url, url, "b", "二次元裏", "1", "題", replyCount = 1,
            insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L, favorite = true)
        val first = futaberSharedStarChanges(emptySet(), listOf(starred), setOf(starred.key))
        assertEquals(setOf(starred.key), first.seen)
        // The mirror takes the star off: the set is updated before the write, and the store's report changes nothing.
        val mirrored = futaberMirroredBeforeApply(setOf(starred.key), emptySet())
        val after = futaberSharedStarChanges(first.seen, listOf(starred.copy(favorite = false)), mirrored)
        assertTrue(after.unstarred.isEmpty())
        // Read with the old set (as before the fix), the same report looked like another mode's.
        assertEquals(setOf(starred.key), futaberSharedStarChanges(first.seen, listOf(starred.copy(favorite = false)), setOf(starred.key)).unstarred)
    }
}

