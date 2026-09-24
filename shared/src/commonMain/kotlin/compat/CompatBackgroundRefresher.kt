package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettingsFromPreferences
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

const val COMPAT_EXISTENCE_PROBE_TIMEOUT_MILLIS = 5_000L
const val COMPAT_EXISTENCE_BUDGET_MILLIS = 30_000L
private const val WATCH_CHECK_WRITE_BATCH = 10

data class CompatBackgroundRefreshResult(
    val updatedTabs: Int = 0,
    val deadTabs: Int = 0,
    val skippedTabs: Int = 0,
    val failures: Int = 0,
    val newWatchMatches: List<CompatWatchMatch> = emptyList()
)

/**
 * Refreshes compatibility tabs without requiring the compatibility UI to be alive.
 * The worker deliberately does not mark a tab as read: checkedReplyCount remains
 * unchanged so the next foreground visit still shows the unread count.
 */
suspend fun refreshCompatTabsInBackground(
    store: CompatibilityStore,
    repository: BoardRepository,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    maxTabs: Int = 20,
    checkUpdates: Boolean = true,
    checkExistence: Boolean = true,
    existenceStaleMillis: Long = COMPAT_THREAD_EXISTENCE_STALE_MILLIS,
    checkWatchWords: Boolean = false,
    /**
     * Platform hosts use this to reject writes after an experience-profile
     * switch.  The default preserves the existing Android call semantics.
     */
    commitGate: suspend (suspend () -> Unit) -> Boolean = { commit -> commit(); true },
    /**
     * Total time for all phases. Background hosts pass part of their run so the
     * history refresh after this call still gets time. Work done before the
     * deadline is kept and reported.
     */
    budgetMillis: Long? = null,
    existenceProbeTimeoutMillis: Long = COMPAT_EXISTENCE_PROBE_TIMEOUT_MILLIS,
    existenceBudgetMillis: Long = COMPAT_EXISTENCE_BUDGET_MILLIS
): CompatBackgroundRefreshResult {
    var updated = 0
    var dead = 0
    var skipped = 0
    var failures = 0
    val newWatchMatches = mutableListOf<CompatWatchMatch>()
    val deadline = budgetMillis?.let { TimeSource.Monotonic.markNow() + it.milliseconds }
    fun remainingMillis(): Long = deadline?.let { (-it.elapsedNow()).inWholeMilliseconds } ?: Long.MAX_VALUE
    /** Runs [block] within the remaining budget; null when it ran out. */
    suspend fun <T> withinBudget(limitMillis: Long = Long.MAX_VALUE, block: suspend () -> T): T? {
        val allowed = minOf(limitMillis, remainingMillis())
        if (allowed <= 0L) return null
        return if (allowed == Long.MAX_VALUE) block() else withTimeoutOrNull(allowed) { block() }
    }

    val tabs = store.tabs.firstCompatBackgroundTabs(maxTabs)
    val boards = store.boards.first()
    // Same catalog layout as the catalog screens; a different one makes the
    // repository redo the board's catalog setup on each switch.
    val catalogSettings = compatCatalogFetchSettingsFromPreferences(store.preferences.first())
    suspend fun fetchCatalog(boardUrl: String, mode: CatalogMode) =
        catalogSettings?.let { repository.getCatalogWithSettings(boardUrl, mode, it) }
            ?: repository.getCatalog(boardUrl, mode)

    if (checkUpdates) {
        boards.forEach boardLoop@{ board ->
            val boardTabs = tabs.filter { it.boardKey == board.key }
            if (boardTabs.isEmpty()) return@boardLoop
            try {
                val catalog = withinBudget { fetchCatalog(board.originalUrl, CatalogMode.Catalog) }
                    ?: run { failures++; return@boardLoop }
                val byCanonicalUrl = catalog.mapNotNull { item ->
                    com.valoser.futacha.shared.compat.canonicalizeThreadUrl(item.threadUrl)
                        ?.canonicalUrl
                        ?.let { it to item }
                }.toMap()
                val byThreadId = catalog.associateBy { it.id }
                boardTabs.forEach tabLoop@{ tab ->
                    val item = byCanonicalUrl[tab.canonicalUrl]
                        ?: byThreadId[tab.threadNo]
                        ?: return@tabLoop
                    if (item.replyCount == tab.replyCount) return@tabLoop
                    var changed = false
                    val committed = commitGate {
                        changed = store.applyCatalogReplyCount(tab.key, tab.canonicalUrl, item.replyCount)
                    }
                    if (committed && changed) updated++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                failures++
            }
        }
    }

    if (checkExistence) {
        // Bounded like the watcher's probes: slow servers or a captive portal
        // must not use up the run before the history refresh gets its turn.
        val completed = withinBudget(existenceBudgetMillis) {
            tabs.filter { tab ->
                hasEpochIntervalElapsed(
                    nowMillis = nowEpochMillis,
                    startedAtMillis = tab.contentUpdatedAtEpochMillis,
                    intervalMillis = existenceStaleMillis
                )
            }.forEach { tab ->
                try {
                    val gone = withTimeoutOrNull(existenceProbeTimeoutMillis) {
                        repository.probeThreadGone(tab.originalUrl)
                    }
                    if (gone == null) {
                        // Unknown is not dead.
                        failures++
                    } else if (gone) {
                        var changed = false
                        val committed = commitGate {
                            changed = store.updateTabIfPresent(tab.key) { current ->
                                // A body fetched while the probe ran proves the thread is alive.
                                if (current.isDead || current.contentUpdatedAtEpochMillis > tab.contentUpdatedAtEpochMillis) {
                                    null
                                } else {
                                    current.copy(isDead = true)
                                }
                            }
                        }
                        if (committed && changed) dead++
                    } else {
                        skipped++
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    failures++
                }
            }
            true
        } ?: false
        if (!completed) failures++
    }

    if (checkWatchWords) {
        val watchPreferences = store.preferences.first()
        val watcher = CompatWatcherRepository(store)
        if (compatWatchRules(watchPreferences).any { it.enabled }) {
            val existingHistory = store.history.first()
            boards.forEach { board ->
                val watchWords = compatWatchWordsForBoard(watchPreferences, board.key)
                if (watchWords.isEmpty()) return@forEach
                listOf(CatalogMode.New, CatalogMode.Old).forEach { mode ->
                    try {
                        val catalog = withinBudget { fetchCatalog(board.originalUrl, mode) }
                            ?: run { failures++; return@forEach }
                        val matches = collectCompatWatchMatches(
                            board = board,
                            items = catalog,
                            watchWords = watchWords,
                            existingHistory = existingHistory + newWatchMatches.map(CompatWatchMatch::history),
                            nowEpochMillis = nowEpochMillis
                        )
                        // One preference write per catalog instead of one per match.
                        var newUrls = emptySet<String>()
                        if (commitGate { newUrls = watcher.recordAll(matches) }) {
                            matches.filter { it.history.canonicalUrl in newUrls }
                                .distinctBy { it.history.canonicalUrl }
                                .forEach { newWatchMatches += it }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        failures++
                    }
                }
            }
        }
        // Outcomes are written in batches; the last batch is written even when
        // the time budget cancels the probes, so finished checks are kept.
        val pendingChecks = linkedMapOf<CompatWatchResult, Boolean>()
        suspend fun flushChecks() {
            if (pendingChecks.isEmpty()) return
            val batch = pendingChecks.toMap()
            pendingChecks.clear()
            commitGate { watcher.markCheckedAll(batch, nowEpochMillis) }
        }
        val completed = try {
            withinBudget(COMPAT_EXISTENCE_BUDGET_MILLIS) {
                watcher.load(nowEpochMillis).filter { it.active && it.checkedAtEpochMillis < nowEpochMillis }
                    .sortedBy { it.checkedAtEpochMillis }.take(100).forEach { result ->
                        try {
                            val gone = withTimeoutOrNull(existenceProbeTimeoutMillis) {
                                repository.probeThreadGone(result.history.originalUrl)
                            }
                            if (gone == null) failures++
                            pendingChecks[result] = gone ?: false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            failures++
                            pendingChecks[result] = false
                        }
                        if (pendingChecks.size >= WATCH_CHECK_WRITE_BATCH) flushChecks()
                    }
                true
            } ?: false
        } finally {
            withContext(NonCancellable) { flushChecks() }
        }
        if (!completed) failures++
    }

    return CompatBackgroundRefreshResult(updated, dead, skipped, failures, newWatchMatches)
}

private suspend fun kotlinx.coroutines.flow.Flow<List<CompatTab>>.firstCompatBackgroundTabs(
    maxTabs: Int
): List<CompatTab> = first()
    .asSequence()
    .filterNot(CompatTab::isDead)
    .sortedBy(CompatTab::contentUpdatedAtEpochMillis)
    .take(maxTabs.coerceAtLeast(1))
    .toList()

/**
 * Stores a catalog reply count on the current tab and history entry.
 *
 * Reads the latest stored rows instead of a copy taken before the catalog
 * request, so read counts, favourites and closed tabs changed meanwhile stay as
 * the user left them. Counts only grow: an older catalog cannot roll them back.
 * Returns whether the tab changed.
 */
suspend fun CompatibilityStore.applyCatalogReplyCount(
    tabKey: String,
    canonicalUrl: String,
    replyCount: Int
): Boolean {
    val tabChanged = updateTabIfPresent(tabKey) { current ->
        if (current.replyCount >= replyCount) null else current.copy(replyCount = replyCount)
    }
    updateHistoryIfPresent(canonicalUrl) { current ->
        if (current.replyCount >= replyCount) null else current.copy(replyCount = replyCount)
    }
    return tabChanged
}
