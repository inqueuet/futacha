package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FtutterCatalogFetchTest {
    @Test fun selectedCountAndFullTextReachBothTheMainAndPickupRequests() = runTest {
        val settings = mutableListOf<CatalogFetchSettings>()
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, value: CatalogFetchSettings): CatalogPageContent {
                settings += value
                val text = "前半".repeat(20) + "後半の監視語"
                return CatalogPageContent(listOf(CatalogItem(id = "1", title = text.take(value.titleLines),
                    thumbnailUrl = null, fullImageUrl = null, threadUrl = "${board}res/1.htm", replyCount = 1)))
            }
        }
        val board = "https://img.2chan.net/b/"
        val main = ftutterFetchCatalogPage(repo, board, CatalogMode.Catalog, 2000)
        val rule = FtutterNgRule("後半の監視語", FtutterNgType.PICKUP.stored)
        val all = ftutterCatalogItemsWithPickups(main.items, FtutterOrder.CATALOG, board, listOf(rule), emptyMap()) { mode ->
            ftutterFetchCatalogPage(repo, board, mode, 2000).items
        }
        assertEquals(listOf(2000, 2000), settings.map { it.approximateThreadCount })
        assertTrue(settings.all { it.titleLines == 256 })
        assertEquals("後半の監視語", FtutterNgMatcher(listOf(rule), board).pickupWord(all.single().title!!))
    }

    @Test fun pickupBoardScopesCanonicalizeUrlsWithoutTrailingSlash() {
        val rule = FtutterNgRule("ねこ", FtutterNgType.PICKUP.stored, ita = FtutterNgBoard("img", "https://IMG.2chan.net/b"))
        assertEquals("ねこ", FtutterNgMatcher(listOf(rule), "https://img.2chan.net/b/futaba.php?mode=cat").pickupWord("ネコのスレ"))
        assertNull(FtutterNgMatcher(listOf(rule), "https://may.2chan.net/b/").pickupWord("ネコのスレ"))
    }
}
