package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.WATCH_RUN_CATALOG_KEY
import com.valoser.futacha.shared.compat.decodeWatchRunDiagnostics
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Re-audit L82 (as the earlier M24 for the shared patrol): the catalog watch wrote its diagnostics and cursor outside the
 * mode-switch gate, and wrote the same cursor again on every run.
 */
class CatalogWatchAlertRefresherGateTest {
    private class CountingStore(private val inner: CompatibilityStore) : CompatibilityStore by inner {
        val writtenKeys = mutableListOf<String>()
        override suspend fun savePreference(key: String, value: String) {
            writtenKeys += key
            inner.savePreference(key, value)
        }
    }

    private val cursorKey = "compat.background.catalog_watch_cursor"
    private val may = BoardSummary("b", "二次元裏", "test", "https://may.2chan.net/b/futaba.php", "test")
    private val jun = BoardSummary("jun", "二次元実況", "test", "https://jun.2chan.net/jun/futaba.php", "test")

    private fun repository(hanging: Set<String> = emptySet()) = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
            if (board in hanging) awaitCancellation()
            return listOf(CatalogItem(id = "100", threadUrl = "https://may.2chan.net/b/res/100.htm", title = "cat",
                thumbnailUrl = null, fullImageUrl = null, replyCount = 0))
        }
    }

    private fun withStores(block: suspend (AppStateStore, CountingStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("futacha-watch-gate").toFile()
        val inner = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            inner.initialize()
            val state = AppStateStore(FakePlatformStateStorage()).apply {
                setBoards(listOf(may, jun)); setHistory(emptyList()); setWatchWords(listOf("cat"))
            }
            block(state, CountingStore(inner))
        } finally { inner.close(); root.deleteRecursively() }
    }

    @Test fun aClosedGateWritesNeitherTheDiagnosticsNorTheCursor() = withStores { state, store ->
        // Cut off, so the run has a cursor to write.
        assertNull(withTimeoutOrNull(1_000L) {
            CatalogWatchAlertRefresher(state, repository(hanging = setOf(jun.url)), Dispatchers.Default, maxConcurrency = 1,
                diagnosticsStore = store).refresh(commitGate = { false })
        })
        assertEquals(emptyList(), store.writtenKeys)
        assertNull(store.preferences.first()[WATCH_RUN_CATALOG_KEY])
        assertNull(store.preferences.first()[cursorKey])
    }

    @Test fun theCursorIsWrittenOnlyWhenItMoves() = withStores { state, store ->
        fun refresher(repository: BoardRepository) =
            CatalogWatchAlertRefresher(state, repository, Dispatchers.Default, maxConcurrency = 1, diagnosticsStore = store)
        // Two finished runs leave the cursor where it was (empty): it is not written again, the diagnostics are.
        refresher(repository()).refresh()
        refresher(repository()).refresh()
        assertEquals(listOf(WATCH_RUN_CATALOG_KEY, WATCH_RUN_CATALOG_KEY), store.writtenKeys)
        assertNotNull(decodeWatchRunDiagnostics(store.preferences.first()[WATCH_RUN_CATALOG_KEY]))
        // A cut-off run moves it to the board it stopped at, and the next finished run moves it back.
        store.writtenKeys.clear()
        assertNull(withTimeoutOrNull(1_000L) { refresher(repository(hanging = setOf(jun.url))).refresh() })
        assertEquals("jun", store.preferences.first()[cursorKey])
        refresher(repository()).refresh()
        assertEquals("", store.preferences.first()[cursorKey])
        assertEquals(2, store.writtenKeys.count { it == cursorKey })
        assertTrue(store.writtenKeys.count { it == WATCH_RUN_CATALOG_KEY } == 2)
    }
}
