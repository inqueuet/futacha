package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.FutachaCatalogMemoryCache
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * L-B1 (docs/futaber-evolution-look-2026-10-31.md §5.3): the whole titles ふたばー風 fetches never reach the catalog
 * ふたちゃ keeps in memory between screens (FutachaSharedBoardRepository's cache, keyed "board|mode"). ふたばー風 hands
 * its catalog the plain repository; even given the shared one, its fetch must go around that cache, or ふたちゃ would
 * show ふたばー風's list when it comes back to the board.
 */
class FutaberCatalogSharedCacheTest {
    @Test fun aFutaberCatalogFetchLeavesTheSharedCatalogCacheAlone() = runBlocking {
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader, arrayOf(CompatibilityStore::class.java)) { _, method, _ ->
            when (method.name) {
                "getPreferences" -> MutableStateFlow<Map<String, String>>(emptyMap())
                "loadCatalogSnapshot" -> null
                else -> error("Unexpected call: ${method.name}")
            }
        } as CompatibilityStore
        val titleLetters = mutableListOf<Int?>()
        val source = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogFetchSettings(): CatalogFetchSettings = CatalogFetchSettings(rows = 60)
            override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
                titleLetters += null
                return page("ふたちゃの題名")
            }
            override suspend fun getCatalogPageWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): CatalogPageContent {
                titleLetters += settings.titleLines
                return page("ふたばー風の題名")
            }
        }
        val cache = FutachaCatalogMemoryCache()
        val board = "https://may.2chan.net/b/"
        val mode = CatalogMode.Catalog

        futaberFetchCatalogPage(FutachaSharedBoardRepository(source, store, null, cache), board, mode, emptyList())
        assertNull(cache.get("$board|$mode"), "ふたばー風's catalog must not be kept for ふたちゃ")
        assertEquals(listOf<Int?>(256), titleLetters)

        // ふたちゃ, coming to the board next, fetches its own catalog with its own layout and keeps that one.
        val futacha = FutachaSharedBoardRepository(source, store, null, cache).getCatalogPage(board, mode)
        assertEquals("ふたちゃの題名", futacha.items.single().title)
        assertEquals(listOf(256, null), titleLetters)
        assertEquals("ふたちゃの題名", cache.get("$board|$mode")?.items?.single()?.title)
    }

    private fun page(title: String) = CatalogPageContent(
        listOf(CatalogItem("1", "https://may.2chan.net/b/res/1.htm", title, null, null, replyCount = 1))
    )
}
