package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals

/** Exercise the same store/synchronization boundary used when switching mobile modes. */
internal suspend fun verifyLargeBoardRoundTrip(
    store: CompatibilityStore,
    bootstrap: Boolean,
    reopen: suspend () -> CompatibilityStore
) {
    val modern = (0..200).map { index ->
        BoardSummary("modern-$index", "板$index", "分類", "https://may.2chan.net/board$index/", "説明", index % 7 == 0)
    }
    if (bootstrap) {
        store.bootstrapBoardsIfNeeded(modern)
        store.importModernBoards(modern)
    } else store.upsertBoards(modernBoardsToCompatibility(modern))
    assertEquals(201, store.boards.first().size)
    assertEquals(modern, synchronizeModernBoardsFromCompatibility(modern, store.boards.first(), store.holdsBundledTutorialBoard))

    val last = store.boards.first().last()
    val thread = "${last.canonicalUrl}res/123456.htm"
    val tab = CompatTab(compatTabKey(thread), thread, thread, last.key, last.name, "123456", "末尾の板のスレ",
        insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    store.openTab(tab)
    store.upsertHistory(CompatHistoryEntry(thread, thread, last.key, last.name, "123456", "履歴", contentUpdatedAtEpochMillis = 1))
    val preference = compatCatalogExtraModePreferenceKey(last.key)
    store.savePreference(preference, "WATCH")
    val restarted = reopen()
    assertEquals(modern, synchronizeModernBoardsFromCompatibility(modern, restarted.boards.first(), restarted.holdsBundledTutorialBoard))
    assertEquals(tab.key, restarted.tabs.first().single().key)
    assertEquals(thread, restarted.history.first().single().canonicalUrl)
    assertEquals("WATCH", restarted.preferences.first()[preference])

    restarted.deleteBoard(restarted.boards.first().first().key)
    assertEquals(modern.drop(1), synchronizeModernBoardsFromCompatibility(modern, restarted.boards.first(), restarted.holdsBundledTutorialBoard))
}
