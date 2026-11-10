package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.ftutter.toFtutterHistoryEntry
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SharedTabDeletionAuditTest {
    @Test fun closingTabAlsoRemovesLegacyBoardIdPreference() = runBlocking {
        val root = Files.createTempDirectory("shared-tabs-audit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        val board = BoardSummary("may-b", "may", "", "https://may.2chan.net/b/", "")
        val url = "https://may.2chan.net/b/res/123.htm"
        try {
            store.initialize(); store.importModernBoards(listOf(board))
            store.openTab(CompatTab(compatTabKey(url), url, url, compatBoardKey(board.url), "may", "123", "題", insertedAtEpochMillis=1, contentUpdatedAtEpochMillis=1))
            store.savePreference(FutaberPreferenceKeys.TABS, encodeFutaberTabs(listOf(FutaberTabKey("may-b", "123"))))
            // Plan B8b: the old row is taken in once; a tab closed in another mode then does not come back from it.
            migrateFutaberLegacyTabs(store, listOf(board), emptyList(), 1)
            store.closeTabs(setOf(compatTabKey(url)), 100)
            val strip = futaberStoreTabSnapshot(store, listOf(board), futaberConsumedTabs(store.loadSharedData()))
            assertTrue(strip.keys.isEmpty(), "旧形式のタブも消えるべき")
        } finally { store.close(); root.deleteRecursively() }
    }
    @Test fun sharedDrawerRowRetainsReplyCountWithoutHistory() {
        val url = "https://may.2chan.net/b/res/123.htm"
        val tab = CompatTab(compatTabKey(url), url, url, compatBoardKey("https://may.2chan.net/b/"), "may", "123", "題", replyCount=10, insertedAtEpochMillis=1, contentUpdatedAtEpochMillis=1)
        assertEquals(10, ftutterTabNumbers(tab.toFtutterHistoryEntry(), null).replies)
    }
    @Test fun bulkActionsUseTheSameSharedOrderAsTheDrawer() {
        val a = "https://may.2chan.net/b/res/1.htm"
        val b = "https://may.2chan.net/b/res/2.htm"
        val c = "https://may.2chan.net/b/res/3.htm"
        val tab = CompatTab(compatTabKey(c), c, c, compatBoardKey("https://may.2chan.net/b/"), "may", "3", "題", isDead=true, insertedAtEpochMillis=1, contentUpdatedAtEpochMillis=1)
        val entries = mapOf(c to tab.toFtutterHistoryEntry())
        assertEquals(listOf(c), ftutterSharedDrawerCloseTargets(FtutterTabAction.CLOSE_AFTER, b, FtutterDrawerContent.TABS, emptyList(), listOf(a), listOf(b,c), entries))
        assertEquals(listOf(c), ftutterSharedDrawerCloseTargets(FtutterTabAction.CLOSE_FALLEN, b, FtutterDrawerContent.TABS, emptyList(), listOf(a), listOf(b,c), entries))
        assertEquals(listOf(b,a), ftutterSharedDrawerCloseTargets(FtutterTabAction.CLOSE_AFTER, c, FtutterDrawerContent.TABS_BY_ACCESS, listOf(c,b,a), listOf(a), listOf(b,c), entries))
    }

}
