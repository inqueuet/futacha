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
/** Key of the last tab whose existence was probed; the next run continues after it. */
const val COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY = "compat.background.existence_cursor"

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
    /** Existence probes per run. Reply counts are applied to every live tab of each fetched catalog. */
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
    existenceBudgetMillis: Long = COMPAT_EXISTENCE_BUDGET_MILLIS,
    /**
     * Cap of the update phase (within [budgetMillis]); null = none. A short
     * background run passes one so the existence and watch phases after it
     * still get time instead of every catalog using up the whole run.
     */
    updateBudgetMillis: Long? = null,
    /** Cap of the watched-thread liveness probes (within [budgetMillis]). */
    watchCheckBudgetMillis: Long = COMPAT_EXISTENCE_BUDGET_MILLIS,
    /**
     * Called right after newly matched watch threads are recorded, before any
     * later phase runs. Recording marks a match as seen, so a host that notifies
     * only from the returned result loses the notification when its timeout
     * cancels the remaining phases. Runs non-cancellably.
     */
    onWatchMatchesRecorded: (suspend (List<CompatWatchMatch>) -> Unit)? = null
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

    // Every live tab: one catalog per board already lists them all, so limiting
    // the update phase to the oldest tabs only hid the counts of newer ones.
    val tabs = store.tabs.first().filterNot(CompatTab::isDead)
    val boards = store.boards.first()
    // Same catalog layout as the catalog screens; a different one makes the
    // repository redo the board's catalog setup on each switch.
    val catalogSettings = compatCatalogFetchSettingsFromPreferences(store.preferences.first())
    suspend fun fetchCatalog(boardUrl: String, mode: CatalogMode) =
        catalogSettings?.let { repository.getCatalogWithSettings(boardUrl, mode, it) }
            ?: repository.getCatalog(boardUrl, mode)

    if (checkUpdates) {
        val updateDeadline = updateBudgetMillis?.let { TimeSource.Monotonic.markNow() + it.milliseconds }
        fun updateRemainingMillis(): Long =
            updateDeadline?.let { (-it.elapsedNow()).inWholeMilliseconds } ?: Long.MAX_VALUE
        boards.forEach boardLoop@{ board ->
            val boardTabs = tabs.filter { it.boardKey == board.key }
            if (boardTabs.isEmpty()) return@boardLoop
            try {
                val catalog = withinBudget(updateRemainingMillis()) { fetchCatalog(board.originalUrl, CatalogMode.Catalog) }
                    ?: run { failures++; return@boardLoop }
                val byCanonicalUrl = catalog.mapNotNull { item ->
                    com.valoser.futacha.shared.compat.canonicalizeThreadUrl(item.threadUrl)
                        ?.canonicalUrl
                        ?.let { it to item }
                }.toMap()
                val byThreadId = catalog.associateBy { it.id }
                boardTabs.forEach tabLoop@{ tab ->
                    if (remainingMillis() <= 0L) return@boardLoop
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
        // A live probe does not change the tab, so picking the stalest tabs
        // probed the same ones forever; rotate through them with a cursor.
        val stale = tabs.filter { tab ->
            hasEpochIntervalElapsed(
                nowMillis = nowEpochMillis,
                startedAtMillis = tab.contentUpdatedAtEpochMillis,
                intervalMillis = existenceStaleMillis
            )
        }
        val previousCursor = try {
            store.loadPreference(COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        var cursor = previousCursor
        val completed = try {
            withinBudget(existenceBudgetMillis) {
                compatRotatedBackgroundTabs(stale, previousCursor, maxTabs).forEach { tab ->
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
                    cursor = tab.key
                }
                true
            } ?: false
        } finally {
            // A run that reaches every stale tab needs no cursor (foreground refreshes).
            val next = cursor
            if (next != null && next != previousCursor && stale.size > maxTabs.coerceAtLeast(1)) {
                withContext(NonCancellable) {
                    runCatching {
                        commitGate { store.savePreference(COMPAT_BACKGROUND_EXISTENCE_CURSOR_KEY, next) }
                    }
                }
            }
        }
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
                            val recorded = matches.filter { it.history.canonicalUrl in newUrls }
                                .distinctBy { it.history.canonicalUrl }
                            newWatchMatches += recorded
                            if (recorded.isNotEmpty() && onWatchMatchesRecorded != null) {
                                withContext(NonCancellable) {
                                    runCatching { onWatchMatchesRecorded(recorded) }
                                }
                            }
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
            withinBudget(watchCheckBudgetMillis) {
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

/**
 * Up to [maxTabs] of [tabs] in stable key order, starting after [afterKey] and
 * wrapping around, so successive runs cover every tab.
 */
internal fun compatRotatedBackgroundTabs(
    tabs: List<CompatTab>,
    afterKey: String?,
    maxTabs: Int
): List<CompatTab> {
    val ordered = tabs.sortedBy(CompatTab::key)
    val start = afterKey?.let { key -> ordered.indexOfFirst { it.key > key } }?.takeIf { it >= 0 } ?: 0
    return (ordered.subList(start, ordered.size) + ordered.subList(0, start)).take(maxTabs.coerceAtLeast(1))
}

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
