package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatImageNgSource
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.registerFutachaImageNgRule
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The catalog's image NG of ふたばー風 (2026-10-13 全体検証 3.3): a "この板のみ" rule is written only after the shared
 * store has learnt this mode's boards; when that fails, the reason is reported and nothing is written.
 */
class FutaberCatalogImageNgOrderTest {
    private val mayUrl = "https://may.2chan.net/b/"
    private val boards = listOf(BoardSummary("may", "may", "", "${mayUrl}futaba.php", ""))
    private val boardKey = compatBoardKey(mayUrl)
    private val hash = "0123456789abcdef"

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("futaber-image-ng-order").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            block(store)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aBoardOnlyRuleWaitsForTheBoardsToBeShared() = withStore { store ->
        // The board is only in this mode's list, as before the dialog's import has finished.
        assertTrue(store.boards.first().isEmpty())
        registerFutachaImageNgRule(store, CompatImageNgSource.CATALOG, boardKey, "${mayUrl}src/1.jpg", "No.1",
            localOnly = true, fetchHash = { hash }, prepareBoardScope = futaberCatalogImageNgPreparation(store, boards))
        val rule = store.ngRules.first().single()
        assertEquals(CompatNgKind.CATALOG_IMAGE_PHASH, rule.kind)
        assertEquals(boardKey, rule.scopeKey)
        assertEquals(hash, rule.normalizedValue)
    }

    @Test
    fun aFailedShareIsReportedWithItsReasonAndWritesNothing() = withStore { desktop ->
        var hashFetched = false
        val failing = object : CompatibilityStore by desktop {
            override suspend fun importModernBoards(modernBoards: List<BoardSummary>): Int = throw IOException("disk full")
        }
        val failure = assertFailsWith<IllegalStateException> {
            registerFutachaImageNgRule(failing, CompatImageNgSource.CATALOG, boardKey, "${mayUrl}src/1.jpg", "No.1",
                localOnly = true, fetchHash = { hashFetched = true; hash },
                prepareBoardScope = futaberCatalogImageNgPreparation(failing, boards))
        }
        assertTrue(failure.message.orEmpty().contains("板を共有できませんでした"), failure.message)
        assertTrue(failure.message.orEmpty().contains("disk full"), failure.message)
        // Nothing is fetched or written once the share has failed.
        assertTrue(!hashFetched)
        assertTrue(desktop.ngRules.first().isEmpty())
    }

    @Test
    fun aRuleForEveryBoardNeedsNoShare() = withStore { desktop ->
        var shared = 0
        val counting = object : CompatibilityStore by desktop {
            override suspend fun importModernBoards(modernBoards: List<BoardSummary>): Int {
                shared++
                return desktop.importModernBoards(modernBoards)
            }
        }
        registerFutachaImageNgRule(counting, CompatImageNgSource.CATALOG, boardKey, "${mayUrl}src/1.jpg", "No.1",
            localOnly = false, fetchHash = { hash }, prepareBoardScope = futaberCatalogImageNgPreparation(counting, boards))
        assertEquals("*", desktop.ngRules.first().single().scopeKey)
        assertEquals(0, shared)
    }
}
