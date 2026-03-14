package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class ThreadSaveMediaAttemptResult(
    val mediaItem: ThreadSaveScheduledMediaItem,
    val result: Result<ThreadSaveLocalFileInfo>?
)

internal data class ThreadSaveMediaDownloadExecutionContext(
    val maxRetries: Int,
    val retryDelayMillis: Long,
    val progressTotal: Int,
    val logTag: String,
    val updateProgress: (current: Int, total: Int) -> Unit,
    val downloadMedia: suspend (ThreadSaveScheduledMediaItem) -> Result<ThreadSaveLocalFileInfo>
)

internal data class ThreadSaveMediaDownloadAccumulator(
    val urlToPathMap: MutableMap<String, String>,
    val mediaKeyToFileInfoMap: MutableMap<String, ThreadSaveLocalFileInfo>,
    var mediaCounts: ThreadSaveMediaCounts = ThreadSaveMediaCounts(),
    var totalSizeBytes: Long = 0L,
    var downloadFailureCount: Int = 0
)

internal suspend fun executeThreadSaveMediaBatch(
    itemBatch: List<ThreadSaveScheduledMediaItem>,
    firstProgressIndex: Int,
    execution: ThreadSaveMediaDownloadExecutionContext
): List<ThreadSaveMediaAttemptResult> = coroutineScope {
    itemBatch.mapIndexed { offset, mediaItem ->
        async(AppDispatchers.io) {
            coroutineContext.ensureActive()
            execution.updateProgress(firstProgressIndex + offset, execution.progressTotal)
            ThreadSaveMediaAttemptResult(
                mediaItem = mediaItem,
                result = retryThreadSaveMediaDownload(mediaItem, execution)
            )
        }
    }.map { it.await() }
}

internal fun applyThreadSaveMediaBatchResults(
    results: List<ThreadSaveMediaAttemptResult>,
    accumulator: ThreadSaveMediaDownloadAccumulator,
    opPostId: String?,
    enforceBudget: (Long) -> Unit,
    logTag: String
) {
    results.forEach { attempt ->
        val result = attempt.result
        if (result == null) {
            accumulator.downloadFailureCount += 1
            return@forEach
        }

        result
            .onSuccess { fileInfo ->
                accumulator.totalSizeBytes += fileInfo.byteSize
                enforceBudget(accumulator.totalSizeBytes)
                val mediaKey = buildThreadSaveMediaDownloadKey(
                    attempt.mediaItem.url,
                    attempt.mediaItem.requestType
                )
                accumulator.mediaKeyToFileInfoMap[mediaKey] = fileInfo
                when (attempt.mediaItem.requestType) {
                    ThreadSaveMediaRequestType.THUMBNAIL -> {
                        if (accumulator.urlToPathMap[attempt.mediaItem.url] == null) {
                            accumulator.urlToPathMap[attempt.mediaItem.url] = fileInfo.relativePath
                        }
                    }
                    ThreadSaveMediaRequestType.FULL_IMAGE -> {
                        accumulator.urlToPathMap[attempt.mediaItem.url] = fileInfo.relativePath
                    }
                }
                accumulator.mediaCounts = updateThreadSaveMediaCounts(
                    current = accumulator.mediaCounts,
                    fileType = fileInfo.fileType,
                    relativePath = fileInfo.relativePath,
                    postId = attempt.mediaItem.postId,
                    opPostId = opPostId
                )
            }
            .onFailure { error ->
                accumulator.downloadFailureCount += 1
                Logger.e(logTag, "Failed to download ${attempt.mediaItem.url}: ${error.message}")
            }
    }
}

private suspend fun retryThreadSaveMediaDownload(
    mediaItem: ThreadSaveScheduledMediaItem,
    execution: ThreadSaveMediaDownloadExecutionContext
): Result<ThreadSaveLocalFileInfo>? {
    return try {
        var downloadResult: Result<ThreadSaveLocalFileInfo>? = null
        var lastError: Throwable? = null
        for (attempt in 1..execution.maxRetries) {
            coroutineContext.ensureActive()
            downloadResult = execution.downloadMedia(mediaItem)
            if (downloadResult.isSuccess) break

            lastError = downloadResult.exceptionOrNull()
            if (attempt < execution.maxRetries) {
                Logger.w(
                    execution.logTag,
                    "Download attempt $attempt failed for ${mediaItem.url}, retrying..."
                )
                delay(execution.retryDelayMillis * attempt)
            }
        }

        if (downloadResult?.isFailure == true) {
            Logger.e(
                execution.logTag,
                "Failed to download ${mediaItem.url} after ${execution.maxRetries} attempts: ${lastError?.message}"
            )
        }
        downloadResult
    } catch (error: CancellationException) {
        throw error
    }
}
