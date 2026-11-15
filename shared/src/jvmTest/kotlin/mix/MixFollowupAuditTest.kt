package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.board.addFutachaCatalogTab
import com.valoser.futacha.shared.ui.board.mixEntriesWithoutNg
import com.valoser.futacha.shared.ui.ftutter.ftutterMixEntries
import com.valoser.futacha.shared.ui.ftutter.FtutterNgMatcher
import com.valoser.futacha.shared.ui.ftutter.FtutterNgRule
import com.valoser.futacha.shared.ui.ftutter.FtutterNgType
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

/** Regression checks for the second MIX audit. */
class MixFollowupAuditTest {
    private val url = "https://may.2chan.net/b/"
    private val board = BoardSummary("may-b", "may", "", url, "")
    private fun entry(count: Int = 1) = MixEntry(board,
        CatalogItem("1", "${url}res/1.htm", "隠したい題名", null, null, replyCount = count),
        MixEntryKey(url, "1"), null, false, 0, 1)

    @Test fun ftutterTitleNgHidesTheNormalCatalogAndMix() {
        val row = entry()
        val rule = FtutterNgRule("隠したい", FtutterNgType.TITLE.stored)
        assertTrue(FtutterNgMatcher(listOf(rule), url).hidesTitle(row.item.title!!))
        assertTrue(ftutterMixEntries(listOf(row), listOf(rule), emptySet(), emptyList()).isEmpty())
    }

    @Test fun soLongPressTabDoesNotStoreSoudaneAsReplyCount() = runBlocking {
        val root = Files.createTempDirectory("mix-followup-audit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            val row = entry(4242).copy(countKind = MixCountKind.SOUDANE)
            assertNull(row.replyCountOrNull)
            // The shared MIX long-press calls this exact helper with the raw CatalogItem.
            addFutachaCatalogTab(store, board, compatBoardKey(url), row.item, row.replyCountOrNull)
            assertEquals(0, store.tabs.first().single().replyCount)
            addFutachaCatalogTab(store, board, compatBoardKey(url), row.item.copy(replyCount = 12))
            addFutachaCatalogTab(store, board, compatBoardKey(url), row.item, row.replyCountOrNull)
            assertEquals(12, store.tabs.first().single().replyCount)
        } finally {
            store.close()
            root.deleteRecursively()
        }
    }
    @Test fun ftutterHiddenAndBoardScopedNgKeepTheOtherBoardsRows() {
        val may = entry()
        val img = may.copy(board = board.copy(id = "img", url = "https://img.2chan.net/b/"),
            item = may.item.copy(threadUrl = "https://img.2chan.net/b/res/1.htm"),
            key = MixEntryKey("https://img.2chan.net/b/", "1"))
        val rule = FtutterNgRule("隠したい", FtutterNgType.TITLE.stored,
            ita = com.valoser.futacha.shared.ui.ftutter.FtutterNgBoard("may", url))
        assertEquals(listOf(img), ftutterMixEntries(listOf(may, img), listOf(rule), emptySet(), emptyList()))
        assertEquals(listOf(img), ftutterMixEntries(listOf(may, img), emptyList(), setOf(may.item.threadUrl), emptyList()))
    }

    @Test fun movingAndClosingTabsKeepsTheMixReturnAndPane() {
        val screen = com.valoser.futacha.shared.ui.ftutter.FtutterScreen.Thread(board.id,
            com.valoser.futacha.shared.ui.ftutter.FtutterThreadRef(board.id, "1", "thread"),
            com.valoser.futacha.shared.ui.ftutter.FtutterScreen.Boards)
        assertEquals(com.valoser.futacha.shared.ui.ftutter.FtutterScreen.Boards,
            com.valoser.futacha.shared.ui.ftutter.ftutterTabBack(screen, "mix", "another-board"))
        assertTrue(com.valoser.futacha.shared.ui.ftutter.ftutterShowsMixPane(screen, "mix"))
        assertEquals(com.valoser.futacha.shared.ui.ftutter.FtutterScreen.Catalog("another-board"),
            com.valoser.futacha.shared.ui.ftutter.ftutterTabBack(screen, null, "another-board"))
        assertFalse(com.valoser.futacha.shared.ui.ftutter.ftutterShowsMixPane(screen, null))
    }
    @Test fun commonWordSwitchDoesNotDisableThreadNg() {
        val row = entry()
        val word = com.valoser.futacha.shared.compat.CompatNgRule("word",
            com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_WORD, "*", "隠したい", 1)
        assertTrue(mixEntriesWithoutNg(listOf(row), listOf(word)).isEmpty())
        assertEquals(listOf(row), mixEntriesWithoutNg(listOf(row), listOf(word), catalogWordNgEnabled = false))
        val thread = word.copy(id = "thread", kind = com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_REFUSE,
            normalizedValue = row.item.threadUrl)
        assertTrue(mixEntriesWithoutNg(listOf(row), listOf(word, thread), catalogWordNgEnabled = false).isEmpty())
    }
}
