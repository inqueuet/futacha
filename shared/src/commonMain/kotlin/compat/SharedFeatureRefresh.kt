package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.hasEpochIntervalElapsed
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
    commitGate: suspend (suspend () -> Unit) -> Boolean = { commit -> commit(); true },
    /** See [refreshCompatTabsInBackground]; background hosts leave time for the history refresh. */
    budgetMillis: Long? = null
) {
    val preferences = store.preferences.first()
    val now = Clock.System.now().toEpochMilliseconds()
    val plan = planCompatForegroundChecks(now,
        parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE]),
        parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE]),
        parseCompatForegroundNetworkPolicy(preferences["compat.background.backgroundThreadUpdateCheck"]),
        parseCompatForegroundNetworkPolicy(preferences["compat.background.backgroundThreadExistCheck"]), isWifiConnected)
    // Persisted like the other check times, so returning to the foreground or a
    // new process does not restart the interval (the foreground loop ticks every minute).
    val watch = compatWatchAllowed(preferences, isWifiConnected) && hasEpochIntervalElapsed(
        nowMillis = now,
        startedAtMillis = parseCompatForegroundLastCheckEpochMillis(preferences[COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE]),
        intervalMillis = COMPAT_WATCH_INTERVAL_MILLIS
    )
    if (!plan.hasWork && !watch) return
    val notifyWatchMatches = preferences[COMPAT_WATCH_NOTIFY_KEY] != "OFF"
    val phases = sharedFeaturePhaseBudgets(budgetMillis, plan.checkUpdates, plan.checkExistence, watch)
    // Notify right after each batch is recorded (and marked seen): notifying only
    // from the final result lost the alerts when a later phase timed out (G9).
    refreshCompatTabsInBackground(store, repository, maxTabs = maxTabs,
        checkUpdates = plan.checkUpdates, checkExistence = plan.checkExistence,
        checkWatchWords = watch, commitGate = commitGate, budgetMillis = budgetMillis,
        updateBudgetMillis = phases.updateMillis, existenceBudgetMillis = phases.existenceMillis,
        watchCheckBudgetMillis = phases.watchCheckMillis,
        onWatchMatchesRecorded = if (notifyWatchMatches) { recorded -> commitGate { onNewMatches(recorded) } } else null)
    commitGate {
        val completed = compatForegroundLastCheckStoredValue(now)
        store.savePreferences(buildMap {
            if (plan.checkUpdates) put(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE, completed)
            if (plan.checkExistence) put(COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE, completed)
            if (watch) put(COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE, completed)
        })
    }
}

/** Caps of the phases of one shared-feature run, within its total budget. */
internal data class SharedFeaturePhaseBudgets(
    val updateMillis: Long? = null,
    val existenceMillis: Long = COMPAT_EXISTENCE_BUDGET_MILLIS,
    val watchCheckMillis: Long = COMPAT_EXISTENCE_BUDGET_MILLIS
)

/**
 * Splits a short [budgetMillis] among the due phases (Z4 follow-up).
 *
 * An iOS short run gives the patrol a few seconds. The update phase fetched
 * catalogs until the whole budget was gone, so the watch-word catalogs after it
 * were never fetched although their check time was saved. Below
 * [SHARED_FEATURE_SPLIT_BELOW_MILLIS] the update, existence and watched-thread
 * phases get capped shares and the watch-word catalogs keep at least theirs.
 * Longer budgets (Android, BGProcessing, foreground) keep the phase defaults.
 */
internal fun sharedFeaturePhaseBudgets(
    budgetMillis: Long?,
    checkUpdates: Boolean,
    checkExistence: Boolean,
    checkWatchWords: Boolean
): SharedFeaturePhaseBudgets {
    val defaults = SharedFeaturePhaseBudgets()
    if (budgetMillis == null || budgetMillis >= SHARED_FEATURE_SPLIT_BELOW_MILLIS) return defaults
    val updateShare = if (checkUpdates) 3 else 0
    val existenceShare = if (checkExistence) 2 else 0
    // Watch-word catalogs (no cap: they use what is left) and watched-thread probes.
    val watchShare = if (checkWatchWords) 3 + 2 else 0
    val total = updateShare + existenceShare + watchShare
    if (total == 0) return defaults
    fun share(weight: Int): Long = budgetMillis * weight / total
    return SharedFeaturePhaseBudgets(
        // Alone, the update phase may use the whole budget.
        updateMillis = if (checkUpdates && total > updateShare) share(updateShare) else null,
        existenceMillis = if (checkExistence) minOf(defaults.existenceMillis, share(existenceShare)) else defaults.existenceMillis,
        watchCheckMillis = if (checkWatchWords) minOf(defaults.watchCheckMillis, share(2)) else defaults.watchCheckMillis
    )
}

internal const val SHARED_FEATURE_SPLIT_BELOW_MILLIS = 60_000L
