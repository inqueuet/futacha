package com.valoser.futacha.shared.background

/**
 * iOS runs a BGProcessing request rarely (usually only while idle and charging), so
 * the 15-minute refresh also submits a BGAppRefresh request. Both run the same
 * single-flight refresh. Every identifier must be listed in Info.plist
 * BGTaskSchedulerPermittedIdentifiers. The processing id keeps its original value
 * so requests submitted by earlier versions are still handled.
 */
internal enum class BackgroundRefreshTaskKind(val identifier: String) {
    APP_REFRESH("com.valoser.futacha.appRefresh"),
    PROCESSING("com.valoser.futacha.refresh");

    companion object {
        fun fromIdentifier(identifier: String?): BackgroundRefreshTaskKind? =
            entries.firstOrNull { it.identifier == identifier }
    }
}

/** Permitted kinds without a pending request, in submission order. */
internal fun resolveBackgroundRefreshKindsToSubmit(
    permittedKinds: Set<BackgroundRefreshTaskKind>,
    pendingKinds: Set<BackgroundRefreshTaskKind>
): List<BackgroundRefreshTaskKind> =
    BackgroundRefreshTaskKind.entries.filter { it in permittedKinds && it !in pendingKinds }

internal sealed interface BackgroundRefreshScheduleAction {
    data object SkipDisabled : BackgroundRefreshScheduleAction
    data object SkipPending : BackgroundRefreshScheduleAction
    data class DelayRetry(val delayMillis: Long) : BackgroundRefreshScheduleAction
    data object SubmitNow : BackgroundRefreshScheduleAction
}

internal data class BackgroundRefreshSubmitFailureState(
    val nextScheduleAllowedAtMillis: Long,
    val nextRetryAttempts: Int,
    val shouldScheduleRetry: Boolean,
    val retryDelayMillis: Long
)

internal fun resolveBackgroundRefreshScheduleAction(
    enabled: Boolean,
    hasPendingRefreshRequest: Boolean,
    nextScheduleAllowedAtMillis: Long,
    nowEpochMillis: Long
): BackgroundRefreshScheduleAction {
    if (!enabled) {
        return BackgroundRefreshScheduleAction.SkipDisabled
    }
    if (hasPendingRefreshRequest) {
        return BackgroundRefreshScheduleAction.SkipPending
    }

    val remainingBackoff = when {
        nextScheduleAllowedAtMillis <= nowEpochMillis -> 0L
        nextScheduleAllowedAtMillis - nowEpochMillis < 0L -> Long.MAX_VALUE
        else -> nextScheduleAllowedAtMillis - nowEpochMillis
    }
    return if (remainingBackoff > 0L) {
        BackgroundRefreshScheduleAction.DelayRetry(remainingBackoff)
    } else {
        BackgroundRefreshScheduleAction.SubmitNow
    }
}

internal fun resolveBackgroundRefreshSubmitFailureState(
    failureNowEpochMillis: Long,
    currentRetryAttempts: Int,
    scheduleBackoffMillis: Long,
    maxRetryAttempts: Int
): BackgroundRefreshSubmitFailureState {
    val nextRetryAttempts = if (currentRetryAttempts == Int.MAX_VALUE) {
        Int.MAX_VALUE
    } else {
        currentRetryAttempts + 1
    }
    val normalizedBackoffMillis = scheduleBackoffMillis.coerceAtLeast(1L)
    val nextScheduleAllowedAtMillis = if (
        failureNowEpochMillis > Long.MAX_VALUE - normalizedBackoffMillis
    ) {
        Long.MAX_VALUE
    } else {
        failureNowEpochMillis + normalizedBackoffMillis
    }
    return BackgroundRefreshSubmitFailureState(
        nextScheduleAllowedAtMillis = nextScheduleAllowedAtMillis,
        nextRetryAttempts = nextRetryAttempts,
        shouldScheduleRetry = nextRetryAttempts <= maxRetryAttempts,
        retryDelayMillis = normalizedBackoffMillis
    )
}

internal fun shouldScheduleBackgroundRefreshRetry(
    enabled: Boolean,
    retryAttempts: Int,
    maxRetryAttempts: Int,
    hasActiveRetryJob: Boolean
): Boolean {
    if (!enabled) {
        return false
    }
    if (retryAttempts > maxRetryAttempts) {
        return false
    }
    return !hasActiveRetryJob
}

internal fun normalizeBackgroundRefreshRetryDelay(delayMillis: Long): Long =
    delayMillis.coerceAtLeast(1L)

/** Stages of one iOS background run in ふたちゃ mode. */
internal enum class IosBackgroundRefreshStage { SHARED_FEATURES, HISTORY, WATCH_ALERTS, ARCHIVE_REPORTS }

/**
 * Time plan of one iOS background run (H4-1). BGProcessing gets minutes, but a
 * BGAppRefresh task and the Watch-requested refresh get only about 30 s. Running
 * the 9-minute plan there was cut off inside the first stage every time, before
 * it committed anything, so the later stages never ran. A short plan caps every
 * stage so each makes and commits bounded progress, history and alerts first.
 */
internal data class IosBackgroundRefreshPlan(
    /** Hard limit of the whole run. */
    val totalTimeoutMillis: Long,
    val stageOrder: List<IosBackgroundRefreshStage>,
    val maxThreadsPerRun: Int,
    /** See HistoryRefresher.refresh; null keeps its default. */
    val historyRunBudgetMillis: Long? = null,
    val threadFetchTimeoutMillis: Long? = null,
    /** Hard cap of the history stage; null = only [totalTimeoutMillis]. */
    val historyTimeoutMillis: Long? = null,
    val autoSaveBudgetMillis: Long,
    val maxAutoSavesPerRun: Int,
    val sharedFeaturesBudgetMillis: Long,
    /** Hard caps of the stages that have no budget of their own; null = only the total. */
    val watchAlertTimeoutMillis: Long? = null,
    val archiveReportTimeoutMillis: Long? = null,
    /** としあき profile: budget of the tab refresh and of its phases. */
    val compatRefreshBudgetMillis: Long? = null,
    val compatUpdateBudgetMillis: Long? = null,
    val compatExistenceBudgetMillis: Long = com.valoser.futacha.shared.compat.COMPAT_EXISTENCE_BUDGET_MILLIS,
    val compatWatchCheckBudgetMillis: Long = com.valoser.futacha.shared.compat.COMPAT_EXISTENCE_BUDGET_MILLIS
) {
    /** Sum of the stage limits of a short plan; must fit in [totalTimeoutMillis]. */
    internal fun futachaStageLimitsMillis(): Long? {
        val history = historyTimeoutMillis ?: return null
        val alerts = watchAlertTimeoutMillis ?: return null
        val archive = archiveReportTimeoutMillis ?: return null
        return history + alerts + sharedFeaturesBudgetMillis + archive
    }

    internal fun compatStageLimitsMillis(): Long? {
        val refresh = compatRefreshBudgetMillis ?: return null
        val archive = archiveReportTimeoutMillis ?: return null
        return refresh + archive
    }
}

private const val IOS_BG_LONG_TIMEOUT_MILLIS = 9 * 60 * 1000L

/** BGProcessing: minutes of runtime; the plan used before H4-1, unchanged. */
internal val IOS_BG_PROCESSING_PLAN = IosBackgroundRefreshPlan(
    totalTimeoutMillis = IOS_BG_LONG_TIMEOUT_MILLIS,
    stageOrder = listOf(
        IosBackgroundRefreshStage.SHARED_FEATURES,
        IosBackgroundRefreshStage.HISTORY,
        IosBackgroundRefreshStage.WATCH_ALERTS,
        IosBackgroundRefreshStage.ARCHIVE_REPORTS
    ),
    maxThreadsPerRun = 40,
    autoSaveBudgetMillis = 90 * 1000L,
    maxAutoSavesPerRun = 2,
    // Leave most of the window for the history refresh after it.
    sharedFeaturesBudgetMillis = IOS_BG_LONG_TIMEOUT_MILLIS / 3
)

private val IOS_BG_SHORT_STAGE_ORDER = listOf(
    IosBackgroundRefreshStage.HISTORY,
    IosBackgroundRefreshStage.WATCH_ALERTS,
    IosBackgroundRefreshStage.SHARED_FEATURES,
    IosBackgroundRefreshStage.ARCHIVE_REPORTS
)

/** BGAppRefresh: about 30 s, so the run stops at 25 s with a safety margin. */
internal val IOS_BG_APP_REFRESH_PLAN = IosBackgroundRefreshPlan(
    totalTimeoutMillis = 25_000L,
    stageOrder = IOS_BG_SHORT_STAGE_ORDER,
    maxThreadsPerRun = 8,
    // No fetch starts after 4 s, so the last one ends by 10 s.
    historyRunBudgetMillis = 10_000L,
    threadFetchTimeoutMillis = 6_000L,
    historyTimeoutMillis = 11_000L,
    autoSaveBudgetMillis = 8_000L,
    maxAutoSavesPerRun = 1,
    watchAlertTimeoutMillis = 6_000L,
    sharedFeaturesBudgetMillis = 4_000L,
    archiveReportTimeoutMillis = 3_000L,
    compatRefreshBudgetMillis = 19_000L,
    compatUpdateBudgetMillis = 6_000L,
    compatExistenceBudgetMillis = 4_000L,
    compatWatchCheckBudgetMillis = 3_000L
)

/**
 * Watch-requested refresh: a UIApplication background task of about 30 s that
 * must also build and send the snapshot afterwards, so it stops at 20 s.
 */
internal val IOS_WATCH_REFRESH_PLAN = IosBackgroundRefreshPlan(
    totalTimeoutMillis = 20_000L,
    stageOrder = IOS_BG_SHORT_STAGE_ORDER,
    maxThreadsPerRun = 8,
    historyRunBudgetMillis = 8_000L,
    threadFetchTimeoutMillis = 5_000L,
    historyTimeoutMillis = 9_000L,
    autoSaveBudgetMillis = 6_000L,
    maxAutoSavesPerRun = 1,
    watchAlertTimeoutMillis = 5_000L,
    sharedFeaturesBudgetMillis = 3_000L,
    archiveReportTimeoutMillis = 2_000L,
    compatRefreshBudgetMillis = 15_000L,
    compatUpdateBudgetMillis = 5_000L,
    compatExistenceBudgetMillis = 3_000L,
    compatWatchCheckBudgetMillis = 2_000L
)

internal fun iosBackgroundRefreshPlanFor(kind: BackgroundRefreshTaskKind): IosBackgroundRefreshPlan = when (kind) {
    BackgroundRefreshTaskKind.APP_REFRESH -> IOS_BG_APP_REFRESH_PLAN
    BackgroundRefreshTaskKind.PROCESSING -> IOS_BG_PROCESSING_PLAN
}
