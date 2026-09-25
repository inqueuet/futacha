package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FutachaCatalogReentryTest {
    @Test fun reentryUsesSessionCacheButManualRefreshAndReloadSettingFetch() = runBlocking {
        val preferences = MutableStateFlow<Map<String, String>>(emptyMap())
        var diskReads = 0
        val store = Proxy.newProxyInstance(CompatibilityStore::class.java.classLoader,
            arrayOf(CompatibilityStore::class.java)) { _, method, _ ->
            when (method.name) {
                "getPreferences" -> preferences
                "loadCatalogSnapshot" -> { diskReads++; null }
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
        val board = "https://example.com/b/"
        val mode = CatalogMode.entries.first { it.sharedCatalogSort() != null }
        val firstScreen = FutachaSharedBoardRepository(source, store, null, cache)
        firstScreen.getCatalogPage(board, mode)
        assertEquals(1, fetches)
        val returnedScreen = FutachaSharedBoardRepository(source, store, null, cache)
        returnedScreen.getCatalogPage(board, mode)
        assertEquals(1, fetches)
        assertEquals(0, diskReads, "An unset reload setting must not show a possibly days-old disk snapshot")
        returnedScreen.getCatalogPage(board, mode)
        assertEquals(2, fetches, "Explicit refresh must bypass both caches")
        preferences.value = mapOf(compatPreferenceStorageKey("catalog", "catalogOpenWithReload") to "ON")
        FutachaSharedBoardRepository(source, store, null, cache).getCatalogPage(board, mode)
        assertEquals(3, fetches)
        preferences.value = emptyMap()
        FutachaSharedBoardRepository(source, store, null, cache).getCatalogPage("https://example.com/other/", mode)
        assertEquals(4, fetches, "A different board cannot reuse this catalog")
        preferences.value = mapOf(compatPreferenceStorageKey("catalog", "catalogOpenWithReload") to "OFF")
        FutachaSharedBoardRepository(source, store, null, FutachaCatalogMemoryCache()).getCatalogPage(board, mode)
        assertEquals(1, diskReads, "An explicit OFF may use the stored snapshot")
        assertEquals(5, fetches, "No stored snapshot falls back to fetching")
    }
}
