package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

fun sharedFeatureRefreshEnabled(preferences: Map<String, String>): Boolean =
    compatForegroundPolicyEnabled(preferences["compat.background.backgroundThreadUpdateCheck"]) ||
        compatForegroundPolicyEnabled(preferences["compat.background.backgroundThreadExistCheck"]) ||
        compatWatchEnabled(preferences)

/** Uses the same policies and commit guard in either presentation mode. */
suspend fun refreshSharedFeatures(
    store: CompatibilityStore, repository: BoardRepository, isWifiConnected: Boolean,
    maxTabs: Int = 20,
    onNewMatches: suspend (List<CompatWatchMatch>) -> Unit = {},
    commitGate: suspend (suspend () -> Unit) -> Boolean = { commit -> commit(); true }
) {
    val preferences = store.preferences.first()
    val now = Clock.System.now().toEpochMilliseconds()
    val plan = planCompatForegroundChecks(now,
        parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE]),
        parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE]),
        parseCompatForegroundNetworkPolicy(preferences["compat.background.backgroundThreadUpdateCheck"]),
        parseCompatForegroundNetworkPolicy(preferences["compat.background.backgroundThreadExistCheck"]), isWifiConnected)
    val watch = compatWatchAllowed(preferences, isWifiConnected)
    if (!plan.hasWork && !watch) return
    val result = refreshCompatTabsInBackground(store, repository, maxTabs = maxTabs,
        checkUpdates = plan.checkUpdates, checkExistence = plan.checkExistence,
        checkWatchWords = watch, commitGate = commitGate)
    commitGate {
        val completed = compatForegroundLastCheckStoredValue(now)
        if (plan.checkUpdates) store.savePreference(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE, completed)
        if (plan.checkExistence) store.savePreference(COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE, completed)
    }
    if (preferences[COMPAT_WATCH_NOTIFY_KEY] != "OFF" && result.newWatchMatches.isNotEmpty())
        commitGate { onNewMatches(result.newWatchMatches) }
}
