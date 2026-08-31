package com.valoser.futacha

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.CompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.compatForegroundLastCheckStoredValue
import com.valoser.futacha.shared.compat.parseCompatForegroundNetworkPolicy
import com.valoser.futacha.shared.compat.parseCompatWatchWords
import com.valoser.futacha.shared.compat.refreshCompatTabsInBackground
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsFailureCategory
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import com.valoser.futacha.shared.service.CatalogWatchAlertRefresher
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.service.HistoryRefreshCommitRejectedException
import com.valoser.futacha.shared.service.WatchAlertNotificationLedger
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class HistoryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FutachaApplication
            ?: return Result.failure()
        val expectedGeneration = inputData.getLong(INPUT_PROFILE_GENERATION, -1L)
        if (app.experienceProfileStore.readActiveProfile() == ExperienceProfile.TOSHIAKI_COMPAT) {
            return doCompatibilityWork(app, expectedGeneration)
        }
        fun isCurrentModernGeneration(): Boolean =
            expectedGeneration >= 0L &&
                app.experienceProfileStore.isGenerationCommitAllowed(
                    ExperienceProfile.FUTACHA,
                    expectedGeneration
                )
        if (!isCurrentModernGeneration()) {
            Logger.d(TAG, "Inactive or stale experience profile; skipping work")
            return Result.success()
        }

        val enabledState = try {
            BackgroundWorkerEnabledState(
                isBackgroundRefreshEnabled = app.appStateStore.isBackgroundRefreshEnabled.first(),
                isWatchAlertEnabled = app.appStateStore.isWatchAlertEnabled.first()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read background refresh setting", e)
            return if (shouldRetryBackgroundSettingRead(runAttemptCount, MAX_SETTING_READ_RETRIES)) {
                Result.retry()
            } else {
                Logger.e(TAG, "Aborting after repeated setting read failures (attempt=$runAttemptCount)")
                Result.failure()
            }
        }
        if (!enabledState.hasAnyEnabled) {
            Logger.d(TAG, "Background refresh disabled; skipping work")
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf("source" to "workmanager", "result" to "disabled")
            )
            return Result.success()
        }

        return try {
            AnalyticsTracker.event("background_refresh_started", mapOf("source" to "workmanager"))
            CrashReporter.log("background_refresh_started source=workmanager")
            withTimeout(REFRESH_TIMEOUT_MILLIS) {
                if (enabledState.isBackgroundRefreshEnabled) {
                    app.historyRefresher.refresh(
                        autoSaveBudgetMillis = AUTO_SAVE_BUDGET_MILLIS,
                        maxThreadsPerRun = MAX_THREADS_PER_RUN,
                        maxAutoSavesPerRun = MAX_AUTO_SAVES_PER_RUN,
                        historyCommitGate = { commit ->
                            app.experienceProfileStore.runIfGenerationCurrent(
                                ExperienceProfile.FUTACHA,
                                expectedGeneration,
                                commit
                            )
                        },
                        autoSaveCommitGate = { commit ->
                            app.experienceProfileStore.runIfGenerationCurrent(
                                ExperienceProfile.FUTACHA,
                                expectedGeneration,
                                commit
                            )
                        }
                    )
                }
                if (!isCurrentModernGeneration()) return@withTimeout
                if (enabledState.isWatchAlertEnabled) {
                    val result = app.catalogWatchAlertRefresher.refresh()
                    val newMatches = filterNewWatchAlertMatches(applicationContext, result.matches)
                    if (newMatches.isNotEmpty() && isCurrentModernGeneration()) {
                        WatchAlertNotifier(applicationContext).notifyMatches(newMatches)
                        app.watchSyncManager.sendWatchAlert(newMatches)
                        markWatchAlertMatchesNotified(applicationContext, newMatches)
                    }
                    if (result.failureCount > 0) {
                        Logger.w(TAG, "Catalog watch alert partial failures: ${result.failureCount}")
                    }
                }
            }
            val errorSnapshot = app.historyRefresher.lastRefreshError.value
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf(
                    "source" to "workmanager",
                    "result" to "success",
                    "thread_count_bucket" to analyticsCountBucket(errorSnapshot?.totalThreads ?: 0),
                    "error_count_bucket" to analyticsCountBucket(errorSnapshot?.errorCount ?: 0)
                )
            )
            CrashReporter.setKey("last_background_refresh_result", "success")
            Result.success()
        } catch (e: CatalogWatchAlertRefresher.RefreshAlreadyRunningException) {
            Logger.d(TAG, "Catalog watch alert refresh already running; skip duplicate worker execution")
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf("source" to "workmanager", "result" to "busy")
            )
            Result.success()
        } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
            Logger.d(TAG, "History refresh already running; skip duplicate worker execution")
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf("source" to "workmanager", "result" to "busy")
            )
            Result.success()
        } catch (e: HistoryRefreshCommitRejectedException) {
            Logger.d(TAG, "Experience generation changed during refresh; dropping buffered updates")
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Background refresh timed out after ${REFRESH_TIMEOUT_MILLIS}ms")
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf(
                    "source" to "workmanager",
                    "result" to "failure",
                    "failure_category" to "timeout",
                    "attempt_bucket" to analyticsCountBucket(runAttemptCount + 1)
                )
            )
            CrashReporter.recordNonFatal(
                e,
                keys = mapOf(
                    "last_background_refresh_result" to "timeout",
                    "last_background_refresh_source" to "workmanager"
                )
            )
            if (shouldRetryBackgroundRefreshTimeout(runAttemptCount, MAX_TIMEOUT_RETRIES)) {
                Result.retry()
            } else {
                Logger.e(TAG, "Timeout retry limit reached; marking run as failure (attempt=$runAttemptCount)")
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            Logger.e(TAG, "Background history refresh failed", t)
            val category = analyticsFailureCategory(t)
            AnalyticsTracker.event(
                "background_refresh_result",
                mapOf(
                    "source" to "workmanager",
                    "result" to "failure",
                    "failure_category" to category,
                    "attempt_bucket" to analyticsCountBucket(runAttemptCount + 1)
                )
            )
            CrashReporter.recordNonFatal(
                t,
                keys = mapOf(
                    "last_background_refresh_result" to "failure",
                    "last_background_refresh_category" to category,
                    "last_background_refresh_source" to "workmanager"
                )
            )
            if (hasHistoryFlushFailure(app.historyRefresher.lastRefreshError.value?.stageCounts.orEmpty())) {
                Logger.e(TAG, "History flush failed; skipping immediate retry to avoid retry churn")
                return Result.failure()
            }
            if (shouldRetryBackgroundRefreshFailure(t, runAttemptCount, MAX_RETRY_ATTEMPTS)) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun doCompatibilityWork(
        app: FutachaApplication,
        expectedGeneration: Long
    ): Result {
        fun isCurrentGeneration(): Boolean =
            expectedGeneration >= 0L &&
                app.experienceProfileStore.isGenerationCommitAllowed(
                    ExperienceProfile.TOSHIAKI_COMPAT,
                    expectedGeneration
                )
        if (!isCurrentGeneration()) return Result.success()

        return try {
            // The Application initializes this store asynchronously. Waiting here
            // makes a cold-start WorkManager run see the same tabs/preferences as UI.
            app.compatibilityStore.initialize()
            val preferences = app.compatibilityStore.preferences.first()
            val updatePolicy = parseCompatForegroundNetworkPolicy(
                preferences["compat.background.backgroundThreadUpdateCheck"]
            )
            val existencePolicy = parseCompatForegroundNetworkPolicy(
                preferences["compat.background.backgroundThreadExistCheck"]
            )
            val compatWatchWordsEnabled = parseCompatWatchWords(
                preferences["compat.catalog.監視ワード"]
            ).isNotEmpty()
            val wifi = isWifiConnected()
            fun allowed(policy: CompatForegroundNetworkPolicy): Boolean = when (policy) {
                CompatForegroundNetworkPolicy.ALWAYS -> true
                CompatForegroundNetworkPolicy.WIFI_ONLY -> wifi
                CompatForegroundNetworkPolicy.NONE -> false
            }
            val updateAllowed = allowed(updatePolicy)
            val existenceAllowed = allowed(existencePolicy)
            val enabled = updateAllowed || existenceAllowed || compatWatchWordsEnabled
            if (!enabled) return Result.success()

            withTimeout(REFRESH_TIMEOUT_MILLIS) {
                val refreshResult = refreshCompatTabsInBackground(
                    store = app.compatibilityStore,
                    repository = app.boardRepository,
                    maxTabs = MAX_COMPAT_TABS_PER_RUN,
                    checkUpdates = updateAllowed,
                    checkExistence = existenceAllowed,
                    checkWatchWords = compatWatchWordsEnabled
                )
                val newMatches = refreshResult.newWatchMatches
                    .map { it.toCatalogWatchAlertMatch() }
                val notifyMatches = filterNewWatchAlertMatches(applicationContext, newMatches)
                if (notifyMatches.isNotEmpty() && isCurrentGeneration()) {
                    WatchAlertNotifier(applicationContext).notifyMatches(notifyMatches)
                    markWatchAlertMatchesNotified(applicationContext, notifyMatches)
                }
                if (isCurrentGeneration()) {
                    val completedAt = compatForegroundLastCheckStoredValue(System.currentTimeMillis())
                    if (updateAllowed) {
                        app.compatibilityStore.savePreference(
                            COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE,
                            completedAt
                        )
                    }
                    if (existenceAllowed) {
                        app.compatibilityStore.savePreference(
                            COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE,
                            completedAt
                        )
                    }
                }
            }
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Compatibility background refresh timed out after ${REFRESH_TIMEOUT_MILLIS}ms")
            if (runAttemptCount < MAX_COMPAT_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Compatibility background refresh failed", e)
            if (runAttemptCount < MAX_COMPAT_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.let { capabilities ->
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            } == true
    }

    private fun com.valoser.futacha.shared.compat.CompatWatchMatch.toCatalogWatchAlertMatch(): CatalogWatchAlertMatch {
        return CatalogWatchAlertMatch(
            threadId = history.threadNo.take(WATCH_ALERT_THREAD_ID_MAX_CHARS),
            boardId = history.boardKey.take(WATCH_ALERT_BOARD_ID_MAX_CHARS),
            boardName = history.boardName.take(WATCH_ALERT_BOARD_NAME_MAX_CHARS),
            boardUrl = history.originalUrl.substringBefore("/res/").take(WATCH_ALERT_URL_MAX_CHARS),
            title = history.title.take(WATCH_ALERT_TITLE_MAX_CHARS),
            titleImageUrl = history.thumbnailUrl.orEmpty().take(WATCH_ALERT_URL_MAX_CHARS),
            replyCount = history.replyCount,
            detectedAtEpochMillis = history.contentUpdatedAtEpochMillis
        )
    }

    companion object {
        private const val TAG = "HistoryRefreshWorker"
        private const val WATCH_ALERT_THREAD_ID_MAX_CHARS = 128
        private const val WATCH_ALERT_BOARD_ID_MAX_CHARS = 256
        private const val WATCH_ALERT_BOARD_NAME_MAX_CHARS = 256
        private const val WATCH_ALERT_TITLE_MAX_CHARS = 1_000
        private const val WATCH_ALERT_URL_MAX_CHARS = 8_192
        private const val INPUT_PROFILE_GENERATION = "profile_generation"
        const val UNIQUE_WORK_NAME = "history_refresh_periodic"
        private const val UNIQUE_ONE_TIME_NAME = "history_refresh_once"
        private val REFRESH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(6)
        private val AUTO_SAVE_BUDGET_MILLIS = TimeUnit.SECONDS.toMillis(90)
        private const val INTERVAL_MINUTES = 15L
        private const val MAX_THREADS_PER_RUN = 20
        private const val HISTORY_FLUSH_STAGE = "history_flush"
        // Keep full media auto-save enabled, but cap how many threads can download media per BG run.
        private const val MAX_AUTO_SAVES_PER_RUN = 2
        private const val MAX_COMPAT_TABS_PER_RUN = 20
        private const val MAX_COMPAT_RETRY_ATTEMPTS = 2
        private const val MAX_SETTING_READ_RETRIES = 3
        private const val MAX_TIMEOUT_RETRIES = 2
        private const val MAX_RETRY_ATTEMPTS = 3

        private val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun enqueuePeriodic(workManager: WorkManager, profileGeneration: Long) {
            val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<HistoryRefreshWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(INPUT_PROFILE_GENERATION to profileGeneration))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediate(workManager: WorkManager, profileGeneration: Long) {
            val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<HistoryRefreshWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(INPUT_PROFILE_GENERATION to profileGeneration))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            workManager.cancelUniqueWork(UNIQUE_ONE_TIME_NAME)
        }

        suspend fun cancelAndAwait(workManager: WorkManager): Boolean = withContext(Dispatchers.IO) {
            listOf(
                workManager.cancelUniqueWork(UNIQUE_WORK_NAME),
                workManager.cancelUniqueWork(UNIQUE_ONE_TIME_NAME)
            ).all { operation ->
                runCatching {
                    operation.result.get(WORK_CANCEL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    true
                }.getOrElse { error ->
                    Logger.w(TAG, "Timed out while quiescing background refresh: ${error.message}")
                    false
                }
            }
        }

        internal fun hasHistoryFlushFailure(stageCounts: Map<String, Int>): Boolean =
            stageCounts[HISTORY_FLUSH_STAGE]?.let { it > 0 } == true

        private const val WORK_CANCEL_TIMEOUT_SECONDS = 5L
    }
}

private fun filterNewWatchAlertMatches(
    context: Context,
    matches: List<CatalogWatchAlertMatch>
): List<CatalogWatchAlertMatch> {
    val prefs = context.getSharedPreferences("watch_alert_notifications", Context.MODE_PRIVATE)
    return WatchAlertNotificationLedger.filterNewMatches(
        serializedEntries = prefs.getString(NOTIFIED_MATCH_ENTRIES_PREF, null),
        legacyKeys = prefs.getStringSet(LEGACY_NOTIFIED_MATCH_KEYS_PREF, emptySet()).orEmpty(),
        matches = matches
    )
}

private fun markWatchAlertMatchesNotified(
    context: Context,
    matches: List<CatalogWatchAlertMatch>
) {
    if (matches.isEmpty()) return
    val prefs = context.getSharedPreferences("watch_alert_notifications", Context.MODE_PRIVATE)
    val serialized = WatchAlertNotificationLedger.markMatches(
        serializedEntries = prefs.getString(NOTIFIED_MATCH_ENTRIES_PREF, null),
        legacyKeys = prefs.getStringSet(LEGACY_NOTIFIED_MATCH_KEYS_PREF, emptySet()).orEmpty(),
        matches = matches,
        nowMillis = System.currentTimeMillis()
    )
    prefs.edit()
        .putString(NOTIFIED_MATCH_ENTRIES_PREF, serialized)
        .remove(LEGACY_NOTIFIED_MATCH_KEYS_PREF)
        .apply()
}

private data class BackgroundWorkerEnabledState(
    val isBackgroundRefreshEnabled: Boolean,
    val isWatchAlertEnabled: Boolean
) {
    val hasAnyEnabled: Boolean
        get() = isBackgroundRefreshEnabled || isWatchAlertEnabled
}

private const val NOTIFIED_MATCH_ENTRIES_PREF = "notified_match_entries"
private const val LEGACY_NOTIFIED_MATCH_KEYS_PREF = "notified_match_keys"
