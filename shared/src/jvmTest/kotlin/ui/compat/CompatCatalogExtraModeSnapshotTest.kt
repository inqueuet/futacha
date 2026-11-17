package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * としあき(仮)'s "そ順"/"監視" (A12 of the 2026-10-14 audit): a list fetched in an extra order is shown only. It must not
 * replace the board's snapshot of its own order, nor make the threads it leaves out "dropped" (nor probe them).
 */
class CompatCatalogExtraModeSnapshotTest {
    private fun item(id: String, replies: Int = 1) =
        CatalogItem(id, "https://may.2chan.net/b/res/$id.htm", "スレ$id", null, null, replyCount = replies)

    private fun withStore(block: suspend (DesktopCompatibilityStore, String) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("compat-extra-mode").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.importModernBoards(listOf(BoardSummary("may-b", "may", "", "https://may.2chan.net/b/futaba.php", "")))
            block(store, store.boards.first().single().key)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    private suspend fun commit(
        store: DesktopCompatibilityStore,
        boardKey: String,
        items: List<CatalogItem>,
        revision: Long,
        extraMode: CompatCatalogExtraMode?,
        probed: MutableList<String>
    ): Boolean = commitCompatCatalogRefresh(
        store = store,
        snapshot = CompatCatalogSnapshot(boardKey, CompatCatalogSort.CATALOG, revision, revision, items),
        extraMode = extraMode,
        previousSnapshot = store.loadCatalogSnapshot(boardKey, CompatCatalogSort.CATALOG),
        trackDropped = true,
        requestedThreadCount = 300,
        probeMaxItems = 64,
        probeTimeoutMillis = 5_000L,
        probe = { probed += it.id; false }
    )

    @Test
    fun aWatchListDoesNotReplaceTheBoardsSnapshotNorDropTheOtherThreads() = withStore { store, key ->
        val normal = listOf(item("1"), item("2"), item("3"), item("4"))
        val probed = mutableListOf<String>()
        assertTrue(commit(store, key, normal, 10L, extraMode = null, probed = probed))

        // "監視" keeps only the thread with a watched word.
        assertFalse(commit(store, key, listOf(item("3")), 20L, CompatCatalogExtraMode.WATCH, probed))
        assertFalse(commit(store, key, listOf(item("4"), item("2")), 30L, CompatCatalogExtraMode.SO, probed))

        assertEquals(normal.map { it.id }, store.loadCatalogSnapshot(key, CompatCatalogSort.CATALOG)?.items?.map { it.id })
        assertTrue(store.loadDroppedCatalogItems(key).isEmpty())
        assertTrue(probed.isEmpty(), "no thread is probed for a list of an extra order: $probed")
    }

    @Test
    fun theBoardsOwnOrderStillTracksTheThreadsThatLeftIt() = withStore { store, key ->
        val probed = mutableListOf<String>()
        assertTrue(commit(store, key, listOf(item("1"), item("2"), item("3")), 10L, null, probed))
        assertTrue(commit(store, key, listOf(item("1"), item("3")), 20L, null, probed))
        assertEquals(listOf("1", "3"), store.loadCatalogSnapshot(key, CompatCatalogSort.CATALOG)?.items?.map { it.id })
        assertEquals(listOf("2"), probed)
        assertEquals(listOf("2"), store.loadDroppedCatalogItems(key).map { it.item.id })
    }

    @Test
    fun theNewRepliesOfAnExtraOrderAreCountedAgainstTheSameOrderShownBefore() {
        val snapshot = listOf(item("1", 5), item("2", 5))
        val shownWatch = listOf(item("2", 8))
        // The board's own order: always its snapshot, whatever is on screen.
        assertSame(snapshot, compatCatalogReplyDeltaBaseline(null, CompatCatalogExtraMode.WATCH, shownWatch, snapshot))
        // The same extra order again: the list shown before.
        assertSame(shownWatch, compatCatalogReplyDeltaBaseline(CompatCatalogExtraMode.WATCH, CompatCatalogExtraMode.WATCH, shownWatch, snapshot))
        // Another extra order, or nothing shown yet: the snapshot (read only).
        assertSame(snapshot, compatCatalogReplyDeltaBaseline(CompatCatalogExtraMode.SO, CompatCatalogExtraMode.WATCH, shownWatch, snapshot))
        assertSame(snapshot, compatCatalogReplyDeltaBaseline(CompatCatalogExtraMode.WATCH, null, snapshot, snapshot))
    }
}
