package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** 2026-10-13 全体検証 3.4 / 3.2 on the iOS store. */
class IosBoardDeletionPreferenceTest {
    @Test fun deletingABoardRemovesItsCatalogExtraOrderOnly() = runBlocking {
        val fs = createFileSystem()
        fs.deleteRecursively("compatibility").getOrThrow()
        try {
            val store = IosCompatibilityStore(fs)
            store.initialize()
            assertTrue(store.holdsBundledTutorialBoard)
            val may = "https://may.2chan.net/b/"
            val img = "https://img.2chan.net/b/"
            store.upsertBoards(listOf(CompatBoard(compatBoardKey(may), "may", may, may, 0),
                CompatBoard(compatBoardKey(img), "img", img, img, 1)))
            store.savePreference(compatCatalogExtraModePreferenceKey(compatBoardKey(may)), "SO")
            store.savePreference(compatCatalogExtraModePreferenceKey(compatBoardKey(img)), "WATCH")
            store.deleteBoard(compatBoardKey(may))
            val preferences = store.preferences.first()
            assertNull(preferences[compatCatalogExtraModePreferenceKey(compatBoardKey(may))])
            assertEquals("WATCH", preferences[compatCatalogExtraModePreferenceKey(compatBoardKey(img))])
            val reopened = IosCompatibilityStore(fs)
            reopened.initialize()
            assertNull(reopened.preferences.first()[compatCatalogExtraModePreferenceKey(compatBoardKey(may))])
        } finally { fs.deleteRecursively("compatibility").getOrThrow() }
    }
}
