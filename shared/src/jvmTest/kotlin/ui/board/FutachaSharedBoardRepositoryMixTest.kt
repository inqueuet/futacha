package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FutachaSharedBoardRepositoryMixTest {
    @Test fun aMixFetchLeavesTheBoardsFirstOpenAndTheCacheAlone() = runBlocking {
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader,
            arrayOf(CompatibilityStore::class.java)) { _, method, _ ->
            when (method.name) {
                "getPreferences" -> MutableStateFlow<Map<String, String>>(emptyMap())
                else -> error("Unexpected call: ${method.name}")
            }
        } as CompatibilityStore
        var fetches = 0
        val source = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
                fetches++
                return CatalogPageContent(emptyList())
            }
        }
        val cache = FutachaCatalogMemoryCache()
        val repo = FutachaSharedBoardRepository(source, store, null, cache)
        val board = "https://example.com/b/"
        val mode = CatalogMode.Catalog
        repo.fetchCatalogPageForMix(board, mode)
        repo.fetchCatalogPageForMix(board, mode)
        assertEquals(2, fetches, "every MIX fetch goes out and is not cached")
        assertNull(cache.get("$board|$mode"))
        repo.getCatalogPage(board, mode)
        assertEquals(3, fetches, "the board's first open still fetches, as if no MIX fetch happened")
        assertEquals(4, run { fetchMixCatalogPage(source, store, board, mode); fetches }, "the plain-repository path fetches too")
    }
}
