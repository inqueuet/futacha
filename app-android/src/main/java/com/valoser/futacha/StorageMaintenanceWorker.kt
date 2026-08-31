package com.valoser.futacha

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.valoser.futacha.shared.util.AndroidFileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Cleans temporary files in app-scoped external storage only while the device is idle and charging.
 * Startup cleanup intentionally excludes these roots because Context.getExternalFilesDirs may block.
 */
class StorageMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? FutachaApplication
            ?: return Result.failure()
        val fileSystem = runCatching { app.fileSystem }.getOrNull() as? AndroidFileSystem
            ?: return Result.success()

        return try {
            val deletedCount = withContext(Dispatchers.IO) {
                fileSystem.cleanupTempFiles(includeExternalStorage = true).getOrThrow()
            }
            if (deletedCount > 0) {
                Logger.i(TAG, "Cleaned up $deletedCount stale temporary files")
            }
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Logger.w(TAG, "Storage maintenance failed: ${error.message}")
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "StorageMaintenanceWorker"
        internal const val UNIQUE_WORK_NAME = "storage_temp_cleanup_periodic"
        private const val REPEAT_INTERVAL_HOURS = 24L
        private const val MAX_RETRY_ATTEMPTS = 2

        internal fun buildConstraints(): Constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        fun enqueuePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<StorageMaintenanceWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(buildConstraints())
                .build()
            workManager.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
