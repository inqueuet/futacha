package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.analytics.PerformanceTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsFailureCategory
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.ThreadSaveStorageOptions
import com.valoser.futacha.shared.service.buildThreadStorageId
import kotlinx.coroutines.CancellationException

internal data class ThreadManualSaveRunnerConfig(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val expiresAtLabel: String?,
    val posts: List<Post>,
    val isTruncated: Boolean,
    val truncationReason: String?,
    val baseSaveLocation: SaveLocation?,
    val baseDirectory: String
)

internal fun buildThreadManualSaveRunnerConfig(
    threadId: String,
    boardId: String,
    boardName: String,
    boardUrl: String,
    title: String,
    expiresAtLabel: String?,
    posts: List<Post>,
    isTruncated: Boolean = false,
    truncationReason: String? = null,
    baseSaveLocation: SaveLocation?,
    baseDirectory: String
): ThreadManualSaveRunnerConfig {
    return ThreadManualSaveRunnerConfig(
        threadId = threadId,
        boardId = boardId,
        boardName = boardName,
        boardUrl = boardUrl,
        title = title,
        expiresAtLabel = expiresAtLabel,
        posts = posts,
        isTruncated = isTruncated,
        truncationReason = truncationReason,
        baseSaveLocation = baseSaveLocation,
        baseDirectory = baseDirectory
    )
}

internal sealed interface ThreadManualSaveRunResult {
    data class Success(val savedThread: SavedThread) : ThreadManualSaveRunResult
    data class Failure(
        val error: Throwable,
        val isUnexpected: Boolean
    ) : ThreadManualSaveRunResult
}

internal data class ThreadManualSaveRunnerCallbacks(
    val saveThread: suspend (ThreadManualSaveRunnerConfig) -> Result<SavedThread>
)

internal fun buildManualThreadSaveStorageOptions(
    boardId: String,
    threadId: String
): ThreadSaveStorageOptions = ThreadSaveStorageOptions(
    storageIdOverride = buildThreadStorageId(boardId, threadId),
    clearExistingOutput = false,
    reuseExistingMedia = true,
    pruneUnreferencedExistingMedia = true
)

internal fun buildThreadManualSaveRunnerCallbacks(
    saveService: ThreadSaveService
): ThreadManualSaveRunnerCallbacks {
    return ThreadManualSaveRunnerCallbacks(
        saveThread = { config ->
            saveService.saveThread(
                threadId = config.threadId,
                boardId = config.boardId,
                boardName = config.boardName,
                boardUrl = config.boardUrl,
                title = config.title,
                expiresAtLabel = config.expiresAtLabel,
                posts = config.posts,
                isTruncated = config.isTruncated,
                truncationReason = config.truncationReason,
                baseSaveLocation = config.baseSaveLocation,
                baseDirectory = config.baseDirectory,
                writeMetadata = true,
                storageOptions = buildManualThreadSaveStorageOptions(config.boardId, config.threadId)
            )
        }
    )
}

internal suspend fun performThreadManualSave(
    config: ThreadManualSaveRunnerConfig,
    callbacks: ThreadManualSaveRunnerCallbacks
): ThreadManualSaveRunResult {
    val analyticsContext = mapOf(
        "board_context" to analyticsSessionContextId("board", config.boardId, config.boardUrl),
        "thread_context" to analyticsSessionContextId("thread", config.boardUrl, config.threadId),
        "title_length_bucket" to analyticsTextLengthBucket(config.title),
        "title_has_url" to analyticsTextHasUrl(config.title)
    )
    AnalyticsTracker.event(
        "thread_save_started",
        analyticsContext + mapOf(
            "source" to "manual",
            "board_kind" to analyticsBoardKind(config.boardUrl),
            "post_count_bucket" to analyticsCountBucket(config.posts.size),
            "is_truncated" to config.isTruncated.toString()
        )
    )
    CrashReporter.log("thread_save_started source=manual board_kind=${analyticsBoardKind(config.boardUrl)}")
    return try {
        PerformanceTracker.measureSuspend(
            traceName = "thread_manual_save",
            attributes = mapOf(
                "feature" to "save",
                "source" to "manual",
                "board_kind" to analyticsBoardKind(config.boardUrl),
                "post_count_bucket" to analyticsCountBucket(config.posts.size)
            )
        ) {
            callbacks.saveThread(config)
        }
            .fold(
                onSuccess = {
                    AnalyticsTracker.event(
                        "thread_save_result",
                        analyticsContext + mapOf(
                            "source" to "manual",
                            "result" to "success",
                            "board_kind" to analyticsBoardKind(config.boardUrl),
                            "post_count_bucket" to analyticsCountBucket(config.posts.size),
                            "image_count_bucket" to analyticsCountBucket(it.imageCount),
                            "video_count_bucket" to analyticsCountBucket(it.videoCount),
                            "status" to it.status.name.lowercase()
                        )
                    )
                    CrashReporter.setKey("last_thread_save_result", "success")
                    CrashReporter.setKey("last_thread_save_status", it.status.name.lowercase())
                    ThreadManualSaveRunResult.Success(it)
                },
                onFailure = {
                    val category = analyticsFailureCategory(it)
                    AnalyticsTracker.event(
                        "thread_save_result",
                        analyticsContext + mapOf(
                            "source" to "manual",
                            "result" to "failure",
                            "board_kind" to analyticsBoardKind(config.boardUrl),
                            "failure_category" to category,
                            "error_type" to (it::class.simpleName ?: "unknown")
                        )
                    )
                    CrashReporter.log("thread_save_failed source=manual category=$category")
                    CrashReporter.recordNonFatal(
                        error = it,
                        keys = mapOf(
                            "last_thread_save_result" to "failure",
                            "last_thread_save_category" to category
                        )
                    )
                    ThreadManualSaveRunResult.Failure(it, isUnexpected = false)
                }
            )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val category = analyticsFailureCategory(error)
        AnalyticsTracker.event(
            "thread_save_result",
            analyticsContext + mapOf(
                "source" to "manual",
                "result" to "failure",
                "board_kind" to analyticsBoardKind(config.boardUrl),
                "failure_category" to category,
                "error_type" to (error::class.simpleName ?: "unknown")
            )
        )
        CrashReporter.log("thread_save_failed source=manual category=$category unexpected=true")
        CrashReporter.recordNonFatal(
            error = error,
            keys = mapOf(
                "last_thread_save_result" to "failure",
                "last_thread_save_category" to category
            )
        )
        ThreadManualSaveRunResult.Failure(error, isUnexpected = true)
    }
}
