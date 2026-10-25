package com.valoser.futacha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeBoardRoundTripInstrumentedTest {
    @Test fun bootstrapKeepsAll201BoardsAcrossModeSwitchingAndReopening() = runBlocking { check(true) }
    @Test fun upsertKeepsAll201BoardsAcrossModeSwitchingAndReopening() = runBlocking { check(false) }

    private suspend fun check(bootstrap: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "audit_many_boards_${System.nanoTime()}.db"
        var store = AndroidCompatibilityStore(context, databaseName = database)
        val modern = (0..200).map { index ->
            BoardSummary("modern-$index", "板$index", "分類", "https://may.2chan.net/board$index/", "説明", index % 7 == 0)
        }
        try {
            store.initialize()
            if (bootstrap) {
                store.bootstrapBoardsIfNeeded(modern)
                store.importModernBoards(modern)
            } else store.upsertBoards(modernBoardsToCompatibility(modern))
            assertEquals(201, store.boards.first().size)
            assertEquals(modern, synchronizeModernBoardsFromCompatibility(modern, store.boards.first(), false))
            val last = store.boards.first().last()
            val thread = "${last.canonicalUrl}res/123456.htm"
            val tab = CompatTab(compatTabKey(thread), thread, thread, last.key, last.name, "123456", "末尾の板のスレ",
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
            store.openTab(tab)
            store.upsertHistory(CompatHistoryEntry(thread, thread, last.key, last.name, "123456", "履歴", contentUpdatedAtEpochMillis = 1))
            val preference = compatCatalogExtraModePreferenceKey(last.key)
            store.savePreference(preference, "WATCH")
            store.closeForTest()
            store = AndroidCompatibilityStore(context, databaseName = database)
            store.initialize()
            assertEquals(modern, synchronizeModernBoardsFromCompatibility(modern, store.boards.first(), false))
            assertEquals(tab.key, store.tabs.first().single().key)
            assertEquals(thread, store.history.first().single().canonicalUrl)
            assertEquals("WATCH", store.preferences.first()[preference])
            store.deleteBoard(store.boards.first().first().key)
            assertEquals(modern.drop(1), synchronizeModernBoardsFromCompatibility(modern, store.boards.first(), false))
        } finally { store.closeForTest(); context.deleteDatabase(database) }
    }
}
