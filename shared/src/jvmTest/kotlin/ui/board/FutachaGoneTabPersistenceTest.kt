package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class FutachaGoneTabPersistenceTest {
    private val url = "https://may.2chan.net/b/res/100.htm"
    private val tab = CompatTab(compatTabKey(url), url, url, compatBoardKey("https://may.2chan.net/b/"), "board", "100", "title",
        replyCount = 10, checkedReplyCount = 3, insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 2, snapshotRevision = 7)
    private class FaultStore(val inner: CompatibilityStore) : CompatibilityStore by inner {
        var failure: Exception? = null
        override suspend fun updateTabIfPresent(tabKey: String, transform: (CompatTab) -> CompatTab?): Boolean {
            failure?.let { throw it }
            return inner.updateTabIfPresent(tabKey, transform)
        }
    }
    private suspend fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) {
        val root = Files.createTempDirectory("futacha-gone-write").toFile()
        try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize(); store.importModernBoards(listOf(BoardSummary("b", "board", "", "https://may.2chan.net/b/", "")))
            store.openTab(tab)
            block(store)
        } finally { root.deleteRecursively() }
    }
    @Test fun failureKeepsTheExistingTabAndRetryMergesIntoItsLatestFields() = runBlocking<Unit> {
        withStore { store ->
            val error = IOException("disk full")
            val faulty = FaultStore(store).apply { failure = error }
            assertSame(error, persistFutachaGoneTab(faulty, tab.key).exceptionOrNull())
            assertEquals(listOf(tab), store.tabs.first())
            val changed = tab.copy(favorite = true, title = "new title", checkedReplyCount = 9,
                scrollAnchor = ScrollAnchor(fallbackIndex = 3, offsetPx = 17, postNo = "104"))
            store.updateTab(changed)
            store.updateScrollAnchor(tab.key, changed.scrollAnchor)
            faulty.failure = null
            assertTrue(persistFutachaGoneTab(faulty, tab.key).getOrThrow())
            assertEquals(listOf(changed.copy(isDead = true)), store.tabs.first())
        }
    }
    @Test fun aRetryDoesNotRecreateATabClosedAfterTheFailedWrite() = runBlocking<Unit> {
        withStore { store ->
            val faulty = FaultStore(store).apply { failure = IOException("database unavailable") }
            assertTrue(persistFutachaGoneTab(faulty, tab.key).isFailure)
            store.closeTabs(setOf(tab.key), 100)
            faulty.failure = null
            assertFalse(persistFutachaGoneTab(faulty, tab.key).getOrThrow())
            assertTrue(store.tabs.first().isEmpty())
        }
    }
    @Test fun cancellationIsPropagatedWithoutWritingOrConvertingItToARecoverableError() = runBlocking<Unit> {
        withStore { store ->
            val cancelled = CancellationException("screen left")
            val faulty = FaultStore(store).apply { failure = cancelled }
            val caught = assertFailsWith<CancellationException> { persistFutachaGoneTab(faulty, tab.key) }
            assertSame(cancelled, caught)
            assertEquals(listOf(tab), store.tabs.first())
        }
    }
}
