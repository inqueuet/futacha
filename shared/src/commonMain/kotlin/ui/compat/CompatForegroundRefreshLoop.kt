package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_FOREGROUND_TICK_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_THREAD_EXISTENCE_STALE_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_WATCH_INTERVAL_MILLIS
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatWatchMatch
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.applyCatalogReplyCount
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
    persist: suspend (operation: String, block: suspend () -> Unit) -> Unit
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
        persist("foreground check timestamps") {
            store.savePreferences(buildMap {
                if (plan.checkUpdates) put(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE, stored)
                if (plan.checkExistence) put(COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE, stored)
            })
        }
        val tabsToCheck = tabs().filterNot(CompatTab::isDead)
        // Same catalog layout as the catalog screen; a different one makes the
        // repository redo the board's catalog setup (and its Cookie) each time.
        val catalogSettings = compatCatalogFetchSettingsFromPreferences(currentPreferences)
        suspend fun fetchCatalog(board: CompatBoard, mode: CatalogMode): List<CatalogItem> =
            catalogSettings?.let { repository.getCatalogWithSettings(board.originalUrl, mode, it) }
                ?: repository.getCatalog(board.originalUrl, mode)
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
                    runSuspendCatchingPreservingCancellation { fetchCatalog(board, CatalogMode.Catalog) }
                        .onSuccess { catalog ->
                            val byCanonicalUrl = catalog.mapNotNull { item ->
                                canonicalizeThreadUrl(item.threadUrl)?.canonicalUrl?.let { it to item }
                            }.toMap()
                            val byThreadId = catalog.associateBy(CatalogItem::id)
                            boardTabs.forEach tabLoop@{ checkedTab ->
                                val item = byCanonicalUrl[checkedTab.canonicalUrl]
                                    ?: byThreadId[checkedTab.threadNo]
                                    ?: return@tabLoop
                                if (item.replyCount != checkedTab.replyCount) {
                                    persist("foreground tab refresh") {
                                        store.applyCatalogReplyCount(
                                            checkedTab.key,
                                            checkedTab.canonicalUrl,
                                            item.replyCount
                                        )
                                    }
                                }
                            }
                            if (watchDue) {
                                recordMatches(
                                    collectCompatWatchMatches(
                                        board = board,
                                        items = catalog,
                                        watchWords = compatWatchWordsForBoard(currentPreferences, board.key),
                                        existingHistory = histories(),
                                        nowEpochMillis = now
                                    )
                                )
                            }
                        }
                }
                // A watched board can have no open tabs. It still must be checked
                // and added to the compatibility watcher's history page, but a
                // board without applicable rules has nothing to match.
                if (watchDue) {
                    val fetchedBoardKeys = tabBoards.keys
                    compatBoardsWithWatchWords(currentBoards, currentPreferences)
                        .filterNot { board -> board.key in fetchedBoardKeys }
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
                    hasEpochIntervalElapsed(
                        nowMillis = now,
                        startedAtMillis = tab.contentUpdatedAtEpochMillis,
                        intervalMillis = COMPAT_THREAD_EXISTENCE_STALE_MILLIS
                    )
                }.forEach { checkedTab ->
                    runSuspendCatchingPreservingCancellation {
                        repository.probeThreadGone(checkedTab.originalUrl)
                    }
                        .onSuccess { isGone ->
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
