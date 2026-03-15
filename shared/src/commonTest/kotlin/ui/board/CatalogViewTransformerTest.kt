package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogViewTransformerTest {
    @Test
    fun buildVisibleCatalogItems_appliesWatchWordsThenNgThenQuery() {
        val items = listOf(
            catalogItem(id = "100", title = "猫 夏休み", replyCount = 30),
            catalogItem(id = "200", title = "猫 NG対象", replyCount = 10),
            catalogItem(id = "300", title = "犬 夏休み", replyCount = 50),
            catalogItem(id = "400", title = "猫 文化祭", replyCount = 40)
        )

        val result = buildVisibleCatalogItems(
            items = items,
            mode = CatalogMode.WatchWords,
            watchWords = listOf("猫", "夏休み"),
            catalogNgWords = listOf("NG"),
            catalogNgFilteringEnabled = true,
            query = "夏休み"
        )

        assertEquals(listOf("100", "300"), result.map { it.id })
    }

    @Test
    fun buildVisibleCatalogItems_keepsServerOrderForNormalModes() {
        val items = listOf(
            catalogItem(id = "300", title = "三番目", replyCount = 1),
            catalogItem(id = "100", title = "一番目", replyCount = 99),
            catalogItem(id = "200", title = "二番目", replyCount = 50)
        )

        val result = buildVisibleCatalogItems(
            items = items,
            mode = CatalogMode.Catalog,
            watchWords = emptyList(),
            catalogNgWords = emptyList(),
            catalogNgFilteringEnabled = true,
            query = ""
        )

        assertEquals(listOf("300", "100", "200"), result.map { it.id })
    }

    @Test
    fun buildVisibleCatalogItems_deduplicatesDuplicateThreadIdsBeforeRendering() {
        val items = listOf(
            catalogItem(id = "100", title = "先頭"),
            catalogItem(id = "100", title = "重複"),
            catalogItem(id = "200", title = "別スレ")
        )

        val result = buildVisibleCatalogItems(
            items = items,
            mode = CatalogMode.Catalog,
            watchWords = emptyList(),
            catalogNgWords = emptyList(),
            catalogNgFilteringEnabled = true,
            query = ""
        )

        assertEquals(listOf("100", "200"), result.map { it.id })
        assertEquals("先頭", result.first().title)
    }

    @Test
    fun mergeWatchSourceCatalogItems_deduplicatesByThreadId() {
        val merged = mergeWatchSourceCatalogItems(
            listOf(
                listOf(catalogItem(id = "100", title = "A"), catalogItem(id = "200", title = "B")),
                listOf(catalogItem(id = "200", title = "B2"), catalogItem(id = "300", title = "C"))
            )
        )

        assertEquals(listOf("100", "200", "300"), merged.map { it.id })
    }

    @Test
    fun loadCatalogItemsForMode_usesServerFetchForNormalMode() = runBlocking {
        val calls = mutableListOf<CatalogMode>()

        val items = loadCatalogItemsForMode(
            boardUrl = "https://may.2chan.net/b/futaba.php",
            mode = CatalogMode.New
        ) { _, mode ->
            calls += mode
            listOf(catalogItem(id = "100", title = mode.label))
        }

        assertEquals(listOf(CatalogMode.New), calls)
        assertEquals(listOf("100"), items.map { it.id })
    }

    @Test
    fun loadCatalogItemsForMode_mergesSuccessfulWatchSourceCatalogs() = runBlocking {
        val calls = mutableListOf<CatalogMode>()

        val items = loadCatalogItemsForMode(
            boardUrl = "https://may.2chan.net/b/futaba.php",
            mode = CatalogMode.WatchWords
        ) { _, mode ->
            calls += mode
            when (mode) {
                CatalogMode.Catalog -> listOf(catalogItem(id = "100", title = "A"))
                CatalogMode.New -> error("fetch failed")
                CatalogMode.Old -> listOf(catalogItem(id = "100", title = "A2"), catalogItem(id = "200", title = "B"))
                else -> emptyList()
            }
        }

        assertEquals(CatalogMode.watchSourceModes, calls)
        assertEquals(listOf("100", "200"), items.map { it.id })
    }

    @Test
    fun loadCatalogItemsForMode_throwsFirstErrorWhenAllWatchSourcesFail() = runBlocking {
        val error = assertFailsWith<IllegalStateException> {
            loadCatalogItemsForMode(
                boardUrl = "https://may.2chan.net/b/futaba.php",
                mode = CatalogMode.WatchWords
            ) { _, _ ->
                throw IllegalStateException("boom")
            }
        }

        assertEquals("boom", error.message)
    }

    @Test
    fun filterByQuery_matchesIdAndThreadUrl() {
        val items = listOf(
            catalogItem(id = "100", title = "title-a"),
            catalogItem(id = "abc", title = "title-b", threadUrl = "https://may.2chan.net/b/res/xyz.htm")
        )

        assertEquals(listOf("100"), items.filterByQuery("100").map { it.id })
        assertEquals(listOf("abc"), items.filterByQuery("xyz").map { it.id })
    }

    @Test
    fun filterByCatalogNgWords_returnsOriginalListWhenDisabled() {
        val items = listOf(
            catalogItem(id = "100", title = "NG target"),
            catalogItem(id = "200", title = "safe")
        )

        assertEquals(
            listOf("100", "200"),
            items.filterByCatalogNgWords(catalogNgWords = listOf("NG"), enabled = false).map { it.id }
        )
    }

    private fun catalogItem(
        id: String,
        title: String,
        replyCount: Int = 0,
        threadUrl: String = "https://may.2chan.net/b/res/$id.htm"
    ): CatalogItem {
        return CatalogItem(
            id = id,
            threadUrl = threadUrl,
            title = title,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = replyCount
        )
    }
}
