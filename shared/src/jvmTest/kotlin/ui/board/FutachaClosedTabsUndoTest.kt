package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ふたちゃ's "元に戻す" of closed tabs (2026-10-13 全体検証 3.1): one closed batch is restored once, whichever of the tab
 * list's button and the host's snackbar is pressed second, so a tab closed again in between stays closed.
 */
class FutachaClosedTabsUndoTest {
    private val boardUrl = "https://may.2chan.net/b/"

    private fun tab(no: String): CompatTab {
        val url = "${boardUrl}res/$no.htm"
        return CompatTab(key = compatTabKey(url), canonicalUrl = url, originalUrl = url, boardKey = compatBoardKey(boardUrl),
            boardName = "may", threadNo = no, title = "タブ$no", insertedAtEpochMillis = no.toLong(),
            contentUpdatedAtEpochMillis = 1_000L)
    }

    @Test
    fun theSameBatchIsRestoredOnlyOnce() = runBlocking {
        val directory = Files.createTempDirectory("futacha-closed-tabs-undo").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoard(CompatBoard(compatBoardKey(boardUrl), "may", boardUrl, boardUrl, 0))
            listOf(tab("1"), tab("2")).forEach { store.openTab(it, null) }
            val undo = FutachaClosedTabsUndo()

            val first = assertNotNull(store.closeTabs(setOf(tab("2").key), 10_000L))
            undo.restore(store, first)
            assertEquals(setOf("1", "2"), store.tabs.first().map { it.threadNo }.toSet())

            // Closed again; the other "元に戻す" still holding the first batch is pressed.
            val second = assertNotNull(store.closeTabs(setOf(tab("2").key), 20_000L))
            undo.restore(store, first)
            assertEquals(setOf("1"), store.tabs.first().map { it.threadNo }.toSet())
            assertTrue(undo.isRestored(first))
            assertFalse(undo.restore(store, first))
            // The newer batch is still restorable.
            assertFalse(undo.isRestored(second))
            assertTrue(undo.restore(store, second))
            assertEquals(setOf("1", "2"), store.tabs.first().map { it.threadNo }.toSet())
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }
}
