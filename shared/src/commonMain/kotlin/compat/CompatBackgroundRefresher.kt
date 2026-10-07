package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettings
import com.valoser.futacha.shared.ui.compat.compatCatalogFetchSettingsFromPreferences
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
/**
 * Key of the last board whose catalog the update phase started; the next run continues after it,
 * so boards at the end of the list are not starved when a run's time runs out (empty = none).
 */
const val COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY = "compat.background.update_board_cursor"
/** Thread count the catalog screen assumes when `catalogThreadSize` is not set. */
internal const val COMPAT_DEFAULT_CATALOG_THREAD_COUNT = 300
/** Existence probes in flight at once; the budget and the per-probe timeout are unchanged. */
internal const val COMPAT_EXISTENCE_PROBE_PARALLELISM = 3

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
    // With no catalogThreadSize stored the catalog screen still fetches 300 threads, so use that
    // layout here too instead of the repository default.
    val catalogSettings = compatCatalogFetchSettingsFromPreferences(store.preferences.first())
        ?: compatCatalogFetchSettings(COMPAT_DEFAULT_CATALOG_THREAD_COUNT)
    suspend fun fetchCatalog(boardUrl: String, mode: CatalogMode) =
        repository.getCatalogWithSettings(boardUrl, mode, catalogSettings)
    suspend fun loadCursor(key: String): String? = try {
        store.loadPreference(key)?.takeIf(String::isNotBlank)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    if (checkUpdates) {
        val updateDeadline = updateBudgetMillis?.let { TimeSource.Monotonic.markNow() + it.milliseconds }
        fun updateRemainingMillis(): Long =
            updateDeadline?.let { (-it.elapsedNow()).inWholeMilliseconds } ?: Long.MAX_VALUE
        // Boards are visited starting after the last one the previous run began, so a run that
        // runs out of time does not leave the same boards at the end of the list unvisited.
        val tabBoardKeys = tabs.mapTo(HashSet(), CompatTab::boardKey)
        val previousBoardCursor = loadCursor(COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY)
        val orderedBoards = compatRotatedBackgroundBoards(boards, previousBoardCursor)
            .filter { it.key in tabBoardKeys }
        var boardCursor = previousBoardCursor
        var startedBoards = 0
        try {
            orderedBoards.forEach boardLoop@{ board ->
                val boardTabs = tabs.filter { it.boardKey == board.key }
                if (boardTabs.isEmpty()) return@boardLoop
                // No time left before this board started: it was not visited, so the next run starts here.
                if (minOf(updateRemainingMillis(), remainingMillis()) <= 0L) {
                    failures++
                    return@boardLoop
                }
                // A board that was started counts as visited even if it times out or is cancelled,
                // otherwise a slow board would be retried first forever.
                boardCursor = board.key
                startedBoards++
                try {
                    val catalog = withinBudget(updateRemainingMillis()) { fetchCatalog(board.originalUrl, CatalogMode.Catalog) }
                        ?: run { failures++; return@boardLoop }
                    // Canonicalizing every catalog URL is CPU work (thousands of items for a large
                    // catalog); keep it off a caller that runs on the main dispatcher.
                    val (byCanonicalUrl, byThreadId) = withContext(AppDispatchers.parsing) {
                        catalog.mapNotNull { item ->
                            com.valoser.futacha.shared.compat.canonicalizeThreadUrl(item.threadUrl)
                                ?.canonicalUrl
                                ?.let { it to item }
                        }.toMap() to catalog.associateBy { it.id }
                    }
                    val replyCountUpdates = ArrayList<CompatCatalogReplyCountUpdate>()
                    for (tab in boardTabs) {
                        if (remainingMillis() <= 0L) break
                        val item = byCanonicalUrl[tab.canonicalUrl]
                            ?: byThreadId[tab.threadNo]
                            ?: continue
                        if (item.replyCount == tab.replyCount) continue
                        replyCountUpdates += CompatCatalogReplyCountUpdate(tab.key, tab.canonicalUrl, item.replyCount)
                    }
                    if (replyCountUpdates.isNotEmpty()) {
                        // One write for the board's tabs instead of two per tab.
                        var changedTabs = 0
                        val committed = commitGate {
                            changedTabs = store.applyCatalogReplyCounts(replyCountUpdates)
                        }
                        if (committed) updated += changedTabs
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    failures++
                }
            }
        } finally {
            // A run that started every board needs no cursor (the next one starts from the top, as
            // before); otherwise remember where this run stopped.
            val next = if (startedBoards >= orderedBoards.size) null else boardCursor
            val save = if (next != null) next != previousBoardCursor else previousBoardCursor != null
            if (save) {
                withContext(NonCancellable) {
                    runCatching {
                        commitGate { store.savePreference(COMPAT_BACKGROUND_UPDATE_BOARD_CURSOR_KEY, next.orEmpty()) }
                    }
                }
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
        var reachedEnd = false
        val completed = try {
            withinBudget(existenceBudgetMillis) {
                // A few probes at a time: a run is limited by time, and the probes are
                // independent network waits. Results are applied in tab order after each
                // group, so the cursor still only moves past tabs that were really handled.
                compatRotatedBackgroundTabs(stale, previousCursor, maxTabs)
                    .chunked(COMPAT_EXISTENCE_PROBE_PARALLELISM)
                    .forEach { group ->
                        val outcomes = coroutineScope {
                            group.map { tab ->
                                async {
                                    try {
                                        val gone = withTimeoutOrNull(existenceProbeTimeoutMillis) {
                                            repository.probeThreadGone(tab.originalUrl)
                                        }
                                        when (gone) {
                                            null -> CompatExistenceProbeOutcome.UNKNOWN
                                            true -> CompatExistenceProbeOutcome.GONE
                                            false -> CompatExistenceProbeOutcome.ALIVE
                                        }
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Throwable) {
                                        CompatExistenceProbeOutcome.FAILED
                                    }
                                }
                            }.awaitAll()
                        }
                        group.forEachIndexed { index, tab ->
                            when (outcomes[index]) {
                                // Unknown is not dead.
                                CompatExistenceProbeOutcome.UNKNOWN,
                                CompatExistenceProbeOutcome.FAILED -> failures++
                                CompatExistenceProbeOutcome.ALIVE -> skipped++
                                CompatExistenceProbeOutcome.GONE -> try {
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
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Throwable) {
                                    failures++
                                }
                            }
                            cursor = tab.key
                        }
                    }
                reachedEnd = true
                true
            } ?: false
        } finally {
            // A run that reaches every stale tab needs no cursor (foreground refreshes); one that
            // was cut short by the time budget or a cancellation remembers where it stopped even
            // when all tabs were requested, so the next run continues there.
            val next = cursor
            if (next != null && next != previousCursor && (stale.size > maxTabs.coerceAtLeast(1) || !reachedEnd)) {
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
 * [boards] in their stored order, starting after the board with key [afterKey] and wrapping
 * around. An unknown or missing key starts from the top.
 */
internal fun compatRotatedBackgroundBoards(boards: List<CompatBoard>, afterKey: String?): List<CompatBoard> {
    if (boards.isEmpty()) return boards
    val index = afterKey?.let { key -> boards.indexOfFirst { it.key == key } } ?: -1
    val start = if (index < 0) 0 else (index + 1) % boards.size
    return boards.drop(start) + boards.take(start)
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

private enum class CompatExistenceProbeOutcome { GONE, ALIVE, UNKNOWN, FAILED }

/** One tab's catalog reply count, applied together with the others of its board. */
data class CompatCatalogReplyCountUpdate(
    val tabKey: String,
    val canonicalUrl: String,
    val replyCount: Int
)

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

/** [applyCatalogReplyCount] for a whole catalog's worth of tabs; returns how many tabs changed. */
suspend fun CompatibilityStore.applyCatalogReplyCountsOneByOne(updates: List<CompatCatalogReplyCountUpdate>): Int =
    updates.count { applyCatalogReplyCount(it.tabKey, it.canonicalUrl, it.replyCount) }
