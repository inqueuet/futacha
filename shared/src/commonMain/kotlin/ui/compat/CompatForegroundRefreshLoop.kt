package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_DEFAULT_CATALOG_THREAD_COUNT
import com.valoser.futacha.shared.compat.COMPAT_FOREGROUND_TICK_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_THREAD_EXISTENCE_STALE_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_WATCH_INTERVAL_MILLIS
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatWatchMatch
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.CompatCatalogReplyCountUpdate
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.collectCompatWatchMatches
import com.valoser.futacha.shared.compat.compatForegroundLastCheckStoredValue
import com.valoser.futacha.shared.compat.compatWatchAllowed
import com.valoser.futacha.shared.compat.compatWatchWordsForBoard
import com.valoser.futacha.shared.compat.parseCompatForegroundLastCheckEpochMillis
import com.valoser.futacha.shared.compat.parseCompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.planCompatForegroundChecks
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Boards whose catalogs the watcher pass must fetch: only boards with at least
 * one enabled rule that applies to them. Boards without rules have nothing to
 * match, so fetching their catalogs is wasted traffic.
 */
internal fun compatBoardsWithWatchWords(
    boards: List<CompatBoard>,
    preferences: Map<String, String>
): List<CompatBoard> = boards.filter { board -> compatWatchWordsForBoard(preferences, board.key).isNotEmpty() }

/** Whether the watcher pass is due, from the persisted last-check time. */
internal fun isCompatForegroundWatchDue(
    preferences: Map<String, String>,
    wifiConnected: Boolean,
    nowEpochMillis: Long
): Boolean = compatWatchAllowed(preferences, wifiConnected) && hasEpochIntervalElapsed(
    nowMillis = nowEpochMillis,
    startedAtMillis = parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE]),
    intervalMillis = COMPAT_WATCH_INTERVAL_MILLIS
)

/**
 * What a foreground pass has already done, kept by the screen across restarts of the loop (audit M21: opening the viewer,
 * the post form or the settings cancels the loop; the pass then started again from the first board, so with many boards
 * or a slow network the later boards were never reached in the foreground). A pass is known by the stored check times it
 * started from: they change only when a pass completes (or the background refresher records its watch), and then the
 * next pass starts afresh.
 */
internal class CompatForegroundPassProgress {
    private var passKey: List<String?>? = null
    private val done = mutableSetOf<String>()

    /** Starts (or goes on with) the pass of [key]. */
    fun begin(key: List<String?>) {
        if (key != passKey) {
            passKey = key
            done.clear()
        }
    }

    fun isDone(step: String): Boolean = step in done

    fun markDone(step: String) {
        done += step
    }

    /**
     * The pass reached its end: the next one starts afresh even when the stored times could not be written (re-audit L10:
     * with the writes failing, every step stayed "done" and nothing was checked again).
     */
    fun finish() {
        passKey = null
        done.clear()
    }
}

/**
 * The stored check times a pass starts from (its identity for [CompatForegroundPassProgress]). The watch time is not part
 * of it: it is written during the pass, after the catalog walk, and a pass cut off later (in the existence probes) then
 * started again from the first board (re-audit L10). A completed pass clears the progress itself ([finish]).
 */
internal fun compatForegroundPassKey(preferences: Map<String, String>): List<String?> = listOf(
    preferences[COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE],
    preferences[COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE]
)

/**
 * The reference app's one-minute foreground TimerTask: tab reply counts, the
 * stale existence probe and the keyword watcher.
 *
 * Every interval is measured from a persisted timestamp, so restarting this
 * loop (for example when the app returns to the foreground) never postpones a
 * check. The watcher time is shared with the background refresher.
 */
internal suspend fun runCompatForegroundRefreshLoop(
    store: CompatibilityStore,
    repository: BoardRepository,
    platformContext: Any?,
    preferences: () -> Map<String, String>,
    tabs: () -> List<CompatTab>,
    boards: () -> List<CompatBoard>,
    histories: () -> List<CompatHistoryEntry>,
    persist: suspend (operation: String, block: suspend () -> Unit) -> Unit,
    progress: CompatForegroundPassProgress = CompatForegroundPassProgress()
) {
    var firstTick = true
    while (true) {
        // The reference TimerTask is scheduled with delay=0 and then every
        // minute. Hidden persisted timestamps prevent an app restart from
        // repeating a check before its five/fifteen-minute deadline.
        if (firstTick) firstTick = false else delay(COMPAT_FOREGROUND_TICK_MILLIS)
        val currentPreferences = preferences()
        val now = Clock.System.now().toEpochMilliseconds()
        val wifiConnected = withContext(AppDispatchers.io) { isCompatWifiConnected(platformContext) }
        val plan = withContext(AppDispatchers.io) {
            planCompatForegroundChecks(
                nowEpochMillis = now,
                lastUpdateCheckEpochMillis = parseCompatForegroundLastCheckEpochMillis(
                    currentPreferences[COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE]
                ),
                lastExistenceCheckEpochMillis = parseCompatForegroundLastCheckEpochMillis(
                    currentPreferences[COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE]
                ),
                updatePolicy = parseCompatForegroundNetworkPolicy(
                    currentPreferences.compatPreferenceValue(
                        "background", "backgroundThreadUpdateCheck", "スレッドの更新確認"
                    )
                ),
                existencePolicy = parseCompatForegroundNetworkPolicy(
                    currentPreferences.compatPreferenceValue(
                        "background", "backgroundThreadExistCheck", "スレッドの生存確認"
                    )
                ),
                isWifiConnected = wifiConnected
            )
        }
        val watchDue = isCompatForegroundWatchDue(currentPreferences, wifiConnected, now)
        if (!plan.hasWork && !watchDue) continue
        val stored = compatForegroundLastCheckStoredValue(now)
        progress.begin(compatForegroundPassKey(currentPreferences))
        val tabsToCheck = tabs().filterNot(CompatTab::isDead)
        // Same catalog layout as the catalog screen; a different one makes the
        // repository redo the board's catalog setup (and its Cookie) each time.
        // Unset catalogThreadSize: the catalog screen fetches 300 threads, so do the same here.
        val catalogSettings = compatCatalogFetchSettingsFromPreferences(currentPreferences)
            ?: compatCatalogFetchSettings(COMPAT_DEFAULT_CATALOG_THREAD_COUNT)
        suspend fun fetchCatalog(board: CompatBoard, mode: CatalogMode): List<CatalogItem> =
            repository.getCatalogWithSettings(board.originalUrl, mode, catalogSettings)
        suspend fun recordMatches(matches: List<CompatWatchMatch>) {
            if (matches.isEmpty()) return
            persist("foreground watch history refresh") {
                CompatWatcherRepository(store).recordAll(matches)
            }
        }
        val currentBoards = boards()
        if (plan.checkUpdates || watchDue) {
            withContext(AppDispatchers.io) {
                // The APK does not download every thread here. It fetches each board's
                // catalog once and reflects only the latest reply count in tabs/history.
                val tabBoards = if (plan.checkUpdates) tabsToCheck.groupBy(CompatTab::boardKey) else emptyMap()
                tabBoards.forEach boardLoop@{ (boardKey, boardTabs) ->
                    val board = currentBoards.firstOrNull { it.key == boardKey } ?: return@boardLoop
                    // The reply counts and the watch words are marked apart: a pass resumed with the watch due checks
                    // the words also on the boards whose counts were done before (re-audit L10).
                    val needCounts = !progress.isDone("tabs:$boardKey")
                    val needWatch = watchDue && !progress.isDone("watch:$boardKey")
                    if (!needCounts && !needWatch) return@boardLoop
                    runSuspendCatchingPreservingCancellation { fetchCatalog(board, CatalogMode.Catalog) }
                        .onSuccess { catalog ->
                            val byCanonicalUrl = catalog.mapNotNull { item ->
                                canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl?.let { it to item }
                            }.toMap()
                            val byThreadId = catalog.associateBy(CatalogItem::id)
                            val replyCountUpdates = boardTabs.mapNotNull { checkedTab ->
                                val item = byCanonicalUrl[checkedTab.canonicalUrl]
                                    ?: byThreadId[checkedTab.threadNo]
                                    ?: return@mapNotNull null
                                if (item.replyCount == checkedTab.replyCount) return@mapNotNull null
                                CompatCatalogReplyCountUpdate(checkedTab.key, checkedTab.canonicalUrl, item.replyCount)
                            }
                            if (needCounts && replyCountUpdates.isNotEmpty()) {
                                // One write per board; iOS and desktop rewrite their whole state per change.
                                persist("foreground tab refresh") {
                                    store.applyCatalogReplyCounts(replyCountUpdates)
                                }
                            }
                            if (needWatch) {
                                recordMatches(
                                    collectCompatWatchMatches(
                                        board = board,
                                        items = catalog,
                                        watchWords = compatWatchWordsForBoard(currentPreferences, board.key),
                                        existingHistory = histories(),
                                        nowEpochMillis = now
                                    )
                                )
                                progress.markDone("watch:$boardKey")
                            }
                            progress.markDone("tabs:$boardKey")
                        }
                }
                // A watched board can have no open tabs. It still must be checked
                // and added to the compatibility watcher's history page, but a
                // board without applicable rules has nothing to match.
                if (watchDue) {
                    compatBoardsWithWatchWords(currentBoards, currentPreferences)
                        .filterNot { board -> progress.isDone("watch:${board.key}") }
                        .forEach { board ->
                            runSuspendCatchingPreservingCancellation { fetchCatalog(board, CatalogMode.New) }
                                .onSuccess { catalog ->
                                    recordMatches(
                                        collectCompatWatchMatches(
                                            board = board,
                                            items = catalog,
                                            watchWords = compatWatchWordsForBoard(currentPreferences, board.key),
                                            existingHistory = histories(),
                                            nowEpochMillis = now
                                        )
                                    )
                                    progress.markDone("watch:${board.key}")
                                }
                        }
                }
            }
        }
        // Publish only after the catalog walk and match recording have completed.
        // Leaving the foreground mid-pass must not postpone the background watcher.
        if (watchDue) persist("foreground watch timestamp") {
            store.savePreference(COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE, stored)
        }
        // BackgroundThreadUpdateCheckAsyncTask also performs this stale existence
        // pass; the dedicated 15-minute setting can trigger the same pass alone.
        if (plan.checkExistence || plan.checkUpdates) {
            withContext(AppDispatchers.io) {
                tabsToCheck.filter { tab ->
                    !progress.isDone("probe:${tab.key}") && hasEpochIntervalElapsed(
                        nowMillis = now,
                        startedAtMillis = tab.contentUpdatedAtEpochMillis,
                        intervalMillis = COMPAT_THREAD_EXISTENCE_STALE_MILLIS
                    )
                }.forEach { checkedTab ->
                    runSuspendCatchingPreservingCancellation {
                        repository.probeThreadGone(checkedTab.originalUrl)
                    }
                        .onSuccess { isGone ->
                            progress.markDone("probe:${checkedTab.key}")
                            if (isGone) persist("foreground dead-thread update") {
                                // Write onto the current stored tab: a copy taken before
                                // the probe would revert favourites/read counts changed
                                // meanwhile and re-add a tab closed during the probe.
                                store.updateTabIfPresent(checkedTab.key) { current ->
                                    compatDeadTabUpdate(current, checkedTab)
                                }
                            }
                        }
                }
            }
        }
        // Stored only once the pass has finished. Written first, a pass cancelled by leaving this
        // screen (image viewer, post form, settings) still counted as done and the next check was
        // postponed by the whole 5/15-minute interval.
        persist("foreground check timestamps") {
            store.savePreferences(buildMap {
                if (plan.checkUpdates) put(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE, stored)
                if (plan.checkExistence) put(COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE, stored)
            })
        }
        progress.finish()
    }
}

/**
 * The dead flag a stale existence probe may write, or null for no change. A
 * body fetched while the probe ran proves the thread is alive.
 */
internal fun compatDeadTabUpdate(current: CompatTab, probed: CompatTab): CompatTab? =
    if (current.isDead || current.contentUpdatedAtEpochMillis > probed.contentUpdatedAtEpochMillis) {
        null
    } else {
        current.copy(isDead = true)
    }
