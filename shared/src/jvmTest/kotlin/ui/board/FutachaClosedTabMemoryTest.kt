package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/**
 * Plan B9, Q9 (audit L3): a ふたちゃ thread screen opened its tab again with every page that arrived after the tab was
 * closed (in the tab list, in another mode, or by the trim). With [FutachaClosedTabMemory] the screen remembers that
 * its tab went and does not open it again; the first open and a tab opened again elsewhere are unchanged.
 */
class FutachaClosedTabMemoryTest {
    private val url = "https://may.2chan.net/b/res/100.htm"
    private val boardKey = compatBoardKey("https://may.2chan.net/b/")
    private val tabKey = compatTabKey(url)

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking<Unit> {
        val root = Files.createTempDirectory("futacha-closed-memory").toFile()
        try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("b", "may", "", "https://may.2chan.net/b/", "")))
            block(store)
        } finally { root.deleteRecursively() }
    }

    private fun pageWrite(replyCount: Int) = FutachaThreadTabWrite(
        tabKey = tabKey, sourceUrl = url, boardKey = boardKey, boardName = "may", threadId = "100", title = "title",
        replyCount = replyCount, thumbnailUrl = null, snapshotChanged = true, snapshotRevision = 1, storedSnapshotRevision = 0,
        confirmedGone = false, confirmedStatus = CompatThreadStatusFlags(), nowEpochMillis = 50
    )

    @Test fun aTabClosedWhileTheScreenShowsItIsNotOpenedAgainByTheNextPage() = withStore { store ->
        val memory = FutachaClosedTabMemory()
        assertTrue(writeFutachaThreadTab(store, pageWrite(5), memory), "the first page opens the tab")
        store.closeTabs(setOf(tabKey), 60, closedBy = "futaber")
        memory.observe(store.tabs.first().any { it.key == tabKey })
        assertFalse(writeFutachaThreadTab(store, pageWrite(6), memory))
        assertTrue(store.tabs.first().none { it.key == tabKey }, "the closed tab came back")
    }

    @Test fun theMemoryAloneNoticesTheCloseAtTheNextWrite() = withStore { store ->
        val memory = FutachaClosedTabMemory()
        writeFutachaThreadTab(store, pageWrite(5), memory)
        writeFutachaThreadTab(store, pageWrite(6), memory) // the tab is seen
        store.closeTabs(setOf(tabKey), 60)
        assertFalse(writeFutachaThreadTab(store, pageWrite(7), memory))
        assertTrue(store.tabs.first().none { it.key == tabKey })
    }

    @Test fun aTabOpenedAgainElsewhereIsWrittenAgain() = withStore { store ->
        val memory = FutachaClosedTabMemory()
        writeFutachaThreadTab(store, pageWrite(5), memory)
        store.closeTabs(setOf(tabKey), 60)
        memory.observe(false)
        store.openTab(CompatTab(tabKey, url, url, boardKey, "may", "100", "title", insertedAtEpochMillis = 70, contentUpdatedAtEpochMillis = 70))
        memory.observe(true)
        assertTrue(writeFutachaThreadTab(store, pageWrite(9), memory))
        assertEquals(9, store.tabs.first().single { it.key == tabKey }.replyCount)
    }

    @Test fun withoutTheMemoryTheWriteIsAsBefore() = withStore { store ->
        writeFutachaThreadTab(store, pageWrite(5))
        store.closeTabs(setOf(tabKey), 60)
        assertTrue(writeFutachaThreadTab(store, pageWrite(6)))
        assertTrue(store.tabs.first().any { it.key == tabKey })
    }

    @Test fun aScreenThatNeverSawItsTabStillOpensIt() {
        val memory = FutachaClosedTabMemory()
        memory.observe(false)
        assertFalse(memory.closedWhileShown)
    }
}
