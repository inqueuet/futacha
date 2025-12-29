package com.valoser.futacha

import android.content.Context
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
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

class HistoryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FutachaApplication
            ?: return Result.failure()

        val isEnabled = runCatching { app.appStateStore.isBackgroundRefreshEnabled.first() }
            .getOrDefault(false)
        if (!isEnabled) {
            Logger.d(TAG, "Background refresh disabled; skipping work")
            return Result.success()
        }

        return try {
            withTimeout(REFRESH_TIMEOUT_MILLIS) {
                app.historyRefresher.refresh()
            }
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Background refresh timed out after ${REFRESH_TIMEOUT_MILLIS}ms")
            Result.retry()
        } catch (t: Throwable) {
            Logger.e(TAG, "Background history refresh failed", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HistoryRefreshWorker"
        const val UNIQUE_WORK_NAME = "history_refresh_periodic"
        private const val UNIQUE_ONE_TIME_NAME = "history_refresh_once"
        private val REFRESH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5)
        private const val INTERVAL_MINUTES = 15L

        private val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun enqueuePeriodic(workManager: WorkManager) {
            val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<HistoryRefreshWorker>(
                INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
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

        fun enqueueImmediate(workManager: WorkManager) {
            val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<HistoryRefreshWorker>()
                .setConstraints(constraints)
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
    }
}
