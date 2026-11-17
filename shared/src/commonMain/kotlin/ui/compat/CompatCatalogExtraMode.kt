package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatCatalogExtraModePreferenceKey
import com.valoser.futacha.shared.compat.diffCompatCatalogGenerations
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.normalizeWatchWords
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.loadCatalogItemsForMode
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.withContext

/*
 * The catalog orders "そ順" and "監視" in としあき(仮) (feature-parity batch 5). CompatCatalogSort is not extended: a new
 * value in the stored catalog preference would make older versions unable to read a backup. A board's extra order is
 * kept apart, in "compat.catalog.extraMode.<boardKey>" ("SO" / "WATCH"); without it the board uses its own order as
 * before, and choosing one of the six orders removes it. Snapshots and the fetch-count key stay those of the board's
 * own order (CompatCatalogSnapshot.sort is the enum): a list of an extra order is shown for the session only and is
 * never saved as, nor compared with, the board's snapshot ([commitCompatCatalogRefresh]).
 */

internal enum class CompatCatalogExtraMode(val storedValue: String, val label: String) {
    SO("SO", "そ順"),
    WATCH("WATCH", "監視")
}

internal fun compatCatalogExtraModeKey(boardKey: String): String = compatCatalogExtraModePreferenceKey(boardKey)

/**
 * One of the six orders chosen for [boardKey] outside the catalog's dialog (the AI's SetCatalogMode). It is saved as the
 * board's own order and, as the dialog does, the board's extra order is removed: the screen shows an extra order first,
 * so a kept "そ順"/"監視" would hide the new order.
 */
internal suspend fun saveCompatCatalogSortChoice(store: CompatibilityStore, boardKey: String, sort: CompatCatalogSort) {
    val current = store.loadCatalogPreference(boardKey)
    store.saveCatalogPreference(current.copy(sort = sort))
    store.savePreferences(mapOf(compatCatalogExtraModeKey(boardKey) to null))
}

/** The board's extra order, or null (the board's own order) for a missing or unknown value. */
internal fun compatCatalogExtraMode(preferences: Map<String, String>, boardKey: String): CompatCatalogExtraMode? {
    val stored = preferences[compatCatalogExtraModeKey(boardKey)] ?: return null
    return CompatCatalogExtraMode.entries.firstOrNull { it.storedValue == stored }
}

/**
 * The words of "監視": the board's patrol (巡回) keywords, then its catalog extraction words (監視ワード of the
 * catalog menu), each once.
 */
internal fun compatCatalogWatchModeWords(
    patrolWords: List<String>,
    boardCatalogRules: List<CompatNgRule>
): List<String> = (patrolWords + boardCatalogRules.filter { it.kind == CompatNgKind.CATALOG_EXTRACT }.map { it.normalizedValue })
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

/** The catalog mode an extra order fetches, or null for "監視" (several catalogs, see [fetchCompatCatalogItems]). */
internal fun CompatCatalogExtraMode.fetchMode(): CatalogMode? = when (this) {
    CompatCatalogExtraMode.SO -> CatalogMode.So
    CompatCatalogExtraMode.WATCH -> null
}

/**
 * The catalog of [boardUrl] in [baseMode], or in the board's [extraMode]. "監視" reads the shared watch-source
 * catalogs (as ふたちゃ's 監視 does) and keeps the threads whose titles contain [watchWords], best matches first;
 * without words it is empty, as in ふたちゃ.
 */
internal suspend fun fetchCompatCatalogItems(
    repository: BoardRepository,
    boardUrl: String,
    baseMode: CatalogMode,
    extraMode: CompatCatalogExtraMode?,
    settings: CatalogFetchSettings,
    watchWords: () -> List<String>
): List<CatalogItem> = when (extraMode) {
    null -> repository.getCatalogWithSettings(board = boardUrl, mode = baseMode, settings = settings)
    CompatCatalogExtraMode.SO -> repository.getCatalogWithSettings(board = boardUrl, mode = CatalogMode.So, settings = settings)
    CompatCatalogExtraMode.WATCH -> {
        // The words come from the screen's state, so they are read here; merging the catalogs, matching the words
        // and ordering the threads run away from the caller (the screen's thread), with the same result (L43).
        val words = watchWords()
        withContext(AppDispatchers.parsing) {
            if (normalizeWatchWords(words).isEmpty()) {
                emptyList()
            } else {
                val page = loadCatalogItemsForMode(boardUrl, CatalogMode.WatchWords, words) { url, mode ->
                    repository.getCatalogPageWithSettings(url, mode, settings)
                }
                CatalogMode.WatchWords.applyClientTransform(page.items, words)
            }
        }
    }
}

/**
 * The list a refresh counts the new replies ("+N") against. The board's own order compares with its saved snapshot, as
 * before. A list of an extra order is never saved, so it compares with the list of the same extra order shown before it
 * in this session, or else with the board's snapshot (only the threads in both are counted; nothing is written).
 */
internal fun compatCatalogReplyDeltaBaseline(
    fetchedExtraMode: CompatCatalogExtraMode?,
    shownExtraMode: CompatCatalogExtraMode?,
    shownItems: List<CatalogItem>,
    boardSnapshotItems: List<CatalogItem>
): List<CatalogItem> =
    if (fetchedExtraMode != null && fetchedExtraMode == shownExtraMode && shownItems.isNotEmpty()) shownItems
    else boardSnapshotItems

/**
 * Saves the result of a refresh as the board's snapshot of its own order ([snapshot].sort), after asking which of the
 * threads that left [previousSnapshot] are still alive. A list of an extra order ([extraMode], "そ順"/"監視") is not the
 * board's order: it is neither saved over the snapshot nor compared with it, so "監視" does not make every thread
 * without a watched word "dropped" and does not probe them. Returns whether the board's snapshot was replaced.
 */
internal suspend fun commitCompatCatalogRefresh(
    store: CompatibilityStore,
    snapshot: CompatCatalogSnapshot,
    extraMode: CompatCatalogExtraMode?,
    previousSnapshot: CompatCatalogSnapshot?,
    trackDropped: Boolean,
    requestedThreadCount: Int,
    probeMaxItems: Int,
    probeTimeoutMillis: Long,
    probe: suspend (CatalogItem) -> Boolean
): Boolean {
    if (extraMode != null) return false
    val activeDroppedThreadIds = previousSnapshot?.takeIf { trackDropped }?.let { previous ->
        val vanished = withContext(AppDispatchers.parsing) {
            diffCompatCatalogGenerations(
                current = snapshot.items,
                previous = previous.items,
                requestedThreadCount = requestedThreadCount,
                enabled = true
            ).vanishedWithin.take(probeMaxItems)
        }
        probeCompatDroppedThreadsNotDeleted(vanished, probeTimeoutMillis, probe)
    }.orEmpty()
    return runSuspendCatchingPreservingCancellation {
        store.saveCatalogSnapshot(
            snapshot,
            trackDropped = trackDropped,
            requestedThreadCount = requestedThreadCount,
            activeDroppedThreadIds = activeDroppedThreadIds
        )
    }.getOrDefault(false)
}

/** Why "前の状態に戻す" does nothing in [extraMode], or null when it can go back (re-audit L14). */
internal fun compatCatalogRollbackRefusal(extraMode: CompatCatalogExtraMode?): String? =
    if (extraMode == null) null else "「${extraMode.label}」の一覧は前の状態に戻せません"
