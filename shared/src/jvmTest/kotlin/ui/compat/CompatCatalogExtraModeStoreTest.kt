package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The per-board "そ順"/"監視" key of としあき(仮) (2026-10-13 全体検証 3.4): an order chosen by the AI replaces it, as the
 * catalog's dialog does, and deleting the board removes it, so the board added again starts in its own order.
 */
class CompatCatalogExtraModeStoreTest {
    private val mayUrl = "https://may.2chan.net/b/"
    private val may = CompatBoard(compatBoardKey(mayUrl), "may", mayUrl, mayUrl, 0)
    private val imgUrl = "https://img.2chan.net/b/"
    private val img = CompatBoard(compatBoardKey(imgUrl), "img", imgUrl, imgUrl, 1)

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("catalog-extra-mode").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            store.upsertBoards(listOf(may, img))
            block(store)
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun anOrderChosenByTheAiReplacesTheExtraOrder() = withStore { store ->
        store.savePreference(compatCatalogExtraModeKey(may.key), CompatCatalogExtraMode.SO.storedValue)
        store.savePreference(compatCatalogExtraModeKey(img.key), CompatCatalogExtraMode.WATCH.storedValue)
        saveCompatCatalogSortChoice(store, may.key, CompatCatalogSort.NEW)
        assertEquals(CompatCatalogSort.NEW, store.loadCatalogPreference(may.key).sort)
        val preferences = store.preferences.first()
        // What the screen shows first is now the chosen order.
        assertNull(compatCatalogExtraMode(preferences, may.key))
        assertNull(preferences[compatCatalogExtraModeKey(may.key)])
        // Another board keeps its own.
        assertEquals(CompatCatalogExtraMode.WATCH, compatCatalogExtraMode(preferences, img.key))
    }

    @Test
    fun deletingTheBoardRemovesItsExtraOrderAndTheBoardAddedAgainStartsWithoutIt() = withStore { store ->
        store.savePreference(compatCatalogExtraModeKey(may.key), CompatCatalogExtraMode.SO.storedValue)
        store.savePreference(compatCatalogExtraModeKey(img.key), CompatCatalogExtraMode.WATCH.storedValue)
        store.deleteBoard(may.key)
        assertNull(store.preferences.first()[compatCatalogExtraModeKey(may.key)])
        store.upsertBoard(may)
        val preferences = store.preferences.first()
        assertNull(compatCatalogExtraMode(preferences, may.key))
        assertEquals(CompatCatalogExtraMode.WATCH, compatCatalogExtraMode(preferences, img.key))
    }
}
