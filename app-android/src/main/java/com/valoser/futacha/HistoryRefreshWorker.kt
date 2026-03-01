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
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.TimeUnit

class HistoryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FutachaApplication
            ?: return Result.failure()

        val isEnabled = try {
            app.appStateStore.isBackgroundRefreshEnabled.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read background refresh setting", e)
            return if (runAttemptCount < MAX_SETTING_READ_RETRIES) {
                Result.retry()
            } else {
                Logger.e(TAG, "Aborting after repeated setting read failures (attempt=$runAttemptCount)")
                Result.failure()
            }
        }
        if (!isEnabled) {
            Logger.d(TAG, "Background refresh disabled; skipping work")
            return Result.success()
        }

        return try {
            withTimeout(REFRESH_TIMEOUT_MILLIS) {
                app.historyRefresher.refresh(
                    autoSaveBudgetMillis = AUTO_SAVE_BUDGET_MILLIS,
                    maxThreadsPerRun = MAX_THREADS_PER_RUN
                )
            }
            Result.success()
        } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
            Logger.d(TAG, "History refresh already running; skip duplicate worker execution")
            Result.success()
        } catch (e: TimeoutCancellationException) {
            Logger.w(TAG, "Background refresh timed out after ${REFRESH_TIMEOUT_MILLIS}ms")
            if (runAttemptCount < MAX_TIMEOUT_RETRIES) {
                Result.retry()
            } else {
                Logger.e(TAG, "Timeout retry limit reached; marking run as failure (attempt=$runAttemptCount)")
                Result.failure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Exception) {
            Logger.e(TAG, "Background history refresh failed", t)
            if (isRetriable(t) && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun isRetriable(t: Throwable): Boolean {
        return when (t) {
            is IOException,
            is NetworkException,
            is TimeoutCancellationException -> true
            else -> false
        }
    }

    companion object {
        private const val TAG = "HistoryRefreshWorker"
        const val UNIQUE_WORK_NAME = "history_refresh_periodic"
        private const val UNIQUE_ONE_TIME_NAME = "history_refresh_once"
        private val REFRESH_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(9)
        private val AUTO_SAVE_BUDGET_MILLIS = TimeUnit.MINUTES.toMillis(3)
        private const val INTERVAL_MINUTES = 15L
        private const val MAX_THREADS_PER_RUN = 120
        private const val MAX_SETTING_READ_RETRIES = 3
        private const val MAX_TIMEOUT_RETRIES = 2
        private const val MAX_RETRY_ATTEMPTS = 3

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
    }
}
