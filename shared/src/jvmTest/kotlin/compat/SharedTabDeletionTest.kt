package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class SharedTabDeletionTest {
    private val board = BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")
    private val url = "https://may.2chan.net/b/res/123.htm"
    private val key = compatTabKey(url)
    private val tab = CompatTab(key, url, url, compatBoardKey(board.url), "may", "123", "題", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val ownTabs = futaberTabsToStore(listOf(FutaberTabKey(board.id, "123")), listOf(board))
    private val prefs get() = mapOf(FutaberPreferenceKeys.TABS to ownTabs, FtutterKeys.TABS to url)

    /** ふたばー風's strip as the screen shows it (plan B8a): the old row only adds what was not taken in. */
    private suspend fun strip(store: DesktopCompatibilityStore) =
        futaberStoreTabSnapshot(store, listOf(board), futaberConsumedTabs(store.loadSharedData())).keys

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("shared-tab-delete").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize(); store.importModernBoards(listOf(board)); store.openTab(tab); store.savePreferences(prefs)
            migrateFutaberLegacyTabs(store, listOf(board), emptyList(), 1)
            block(store)
        }
        finally { store.close(); root.deleteRecursively() }
    }

    @Test fun closingSharedTabRemovesBothPersistedModeListsAndKeepsHistory() = withStore { store ->
        store.recordHistoryVisit(CompatHistoryEntry(url, url, tab.boardKey, "may", "123", "題", null, 1, 1))
        store.closeTabs(setOf(key), 100)
        assertTrue(store.tabs.first().isEmpty())
        // Plan B7b: ふたったー風's old row is neither read nor written by the reconciliation (its bar is the shared tabs).
        assertEquals(url, store.loadPreference(FtutterKeys.TABS))
        // Plan B8b: ふたばー風's old row is not edited either; its strip no longer shows the tab.
        assertEquals(ownTabs, store.loadPreference(FutaberPreferenceKeys.TABS))
        assertTrue(strip(store).isEmpty())
        assertEquals(listOf(url), store.history.first().map { it.canonicalUrl })
    }

    /** Audit H1 / plan B5: ふたばー風's favourite is a bookmark of its own; closing its tab (in any mode) leaves it. */
    @Test fun closingATabKeepsTheFutaberFavourite() = withStore { store ->
        val stars = futaberFavoritesToStore(listOf(FutaberFavorite(board.id, "123", "題", "", "may", 3, 1L)), listOf(board))
        store.savePreference(FutaberPreferenceKeys.FAVORITES, stars)
        store.closeTabs(setOf(key), 100, closedBy = FUTABER_TAB_PROFILE)
        assertTrue(store.tabs.first().isEmpty(), "the tab closes in every mode")
        assertEquals(stars, store.loadPreference(FutaberPreferenceKeys.FAVORITES), "closed in ふたばー風")
        store.openTab(tab)
        store.closeTabs(setOf(key), 200)
        assertEquals(stars, store.loadPreference(FutaberPreferenceKeys.FAVORITES), "closed in another mode")
    }

    @Test fun closingInFutaberClosesTheSharedTabEvenWhenFtutterAlsoShowsIt() = withStore { store ->
        store.replaceTabViews(FTUTTER_TAB_PROFILE, listOf(CompatSharedTabView(key, FTUTTER_TAB_PROFILE, 0, 1L)))
        store.closeTabs(setOf(key), 100, closedBy = FUTABER_TAB_PROFILE)
        assertTrue(store.tabs.first().isEmpty())
        assertTrue(store.loadTabViews(FTUTTER_TAB_PROFILE).isEmpty(), "ふたったー風's mark goes with the tab")
    }

    /** Plan B7b: writing ふたったー風's old row closes nothing; ふたったー風 closes with `closeTabs(closedBy = ftutter)`. */
    @Test fun writingFtutterOldRowClosesNothing() = withStore { store ->
        store.savePreference(FtutterKeys.TABS, "")
        assertEquals(listOf(key), store.tabs.first().map { it.key })
        assertEquals(ownTabs, store.loadPreference(FutaberPreferenceKeys.TABS))
        store.closeTabs(setOf(key), 100, closedBy = FTUTTER_TAB_PROFILE)
        assertTrue(store.tabs.first().isEmpty())
        assertTrue(strip(store).isEmpty(), "and ふたばー風's strip follows")
    }

    /** Plan B8b: writing ふたばー風's old row neither closes nor brings back a tab; only an explicit opening does. */
    @Test fun writingFutaberOldRowClosesAndRecreatesNothing() = withStore { store ->
        store.savePreference(FutaberPreferenceKeys.TABS, encodeFutaberTabs(emptyList()))
        assertEquals(listOf(key), store.tabs.first().map { it.key }, "an empty row closes nothing")
        store.closeTabs(setOf(key), 100)
        store.savePreference(FutaberPreferenceKeys.TABS, ownTabs)
        assertTrue(store.tabs.first().isEmpty(), "a stale row does not bring the tab back")
        assertTrue(strip(store).isEmpty(), "nor show it in the strip")
        store.openTab(tab)
        assertEquals(listOf(key), store.tabs.first().map { it.key })
    }

    @Test fun clearingHistoryLeavesAllTabsOpen() = withStore { store ->
        store.clearHistory()
        assertEquals(listOf(key), store.tabs.first().map { it.key })
        assertEquals(url, store.loadPreference(FtutterKeys.TABS))
        assertEquals(ownTabs, store.loadPreference(FutaberPreferenceKeys.TABS))
    }
    @Test fun deletedModeTabsStayDeletedAfterRestart() = runBlocking {
        val root = Files.createTempDirectory("shared-tab-restart").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize(); store.importModernBoards(listOf(board)); store.openTab(tab); store.savePreferences(prefs)
            migrateFutaberLegacyTabs(store, listOf(board), emptyList(), 1)
            store.closeTabs(setOf(key), 100); store.close()
            store = DesktopCompatibilityStore(JvmFileSystem(root)); store.initialize()
            assertTrue(store.tabs.first().isEmpty())
            assertTrue(strip(store).isEmpty())
        } finally { store.close(); root.deleteRecursively() }
    }

}
