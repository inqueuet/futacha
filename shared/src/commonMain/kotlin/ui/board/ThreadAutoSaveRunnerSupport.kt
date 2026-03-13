package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.service.ThreadSaveService
import kotlinx.coroutines.CancellationException

internal data class ThreadAutoSaveRunnerConfig(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val expiresAtLabel: String?,
    val posts: List<Post>,
    val previousTimestampMillis: Long,
    val attemptStartedAtMillis: Long,
    val completionTimestampMillis: Long
)

internal fun buildThreadAutoSaveRunnerConfig(
    threadId: String,
    boardId: String,
    boardName: String,
    boardUrl: String,
    title: String,
    expiresAtLabel: String?,
    posts: List<Post>,
    previousTimestampMillis: Long,
    attemptStartedAtMillis: Long,
    completionTimestampMillis: Long
): ThreadAutoSaveRunnerConfig {
    return ThreadAutoSaveRunnerConfig(
        threadId = threadId,
        boardId = boardId,
        boardName = boardName,
        boardUrl = boardUrl,
        title = title,
        expiresAtLabel = expiresAtLabel,
        posts = posts,
        previousTimestampMillis = previousTimestampMillis,
        attemptStartedAtMillis = attemptStartedAtMillis,
        completionTimestampMillis = completionTimestampMillis
    )
}

internal data class ThreadAutoSaveRunResult(
    val completionState: ThreadAutoSaveCompletionState
)

internal data class ThreadAutoSaveRunnerCallbacks(
    val saveThread: suspend (ThreadAutoSaveRunnerConfig) -> Result<SavedThread>
)

internal fun buildThreadAutoSaveRunnerCallbacks(
    saveService: ThreadSaveService
): ThreadAutoSaveRunnerCallbacks {
    return ThreadAutoSaveRunnerCallbacks(
        saveThread = { config ->
            saveService.saveThread(
                threadId = config.threadId,
                boardId = config.boardId,
                boardName = config.boardName,
                boardUrl = config.boardUrl,
                title = config.title,
                expiresAtLabel = config.expiresAtLabel,
                posts = config.posts,
                baseDirectory = com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY,
                writeMetadata = true
            )
        }
    )
}

internal suspend fun performThreadAutoSave(
    config: ThreadAutoSaveRunnerConfig,
    callbacks: ThreadAutoSaveRunnerCallbacks
): ThreadAutoSaveRunResult {
    val saveResult = try {
        callbacks.saveThread(config)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
    return ThreadAutoSaveRunResult(
        completionState = resolveThreadAutoSaveCompletionState(
            threadId = config.threadId,
            saveResult = saveResult,
            previousTimestampMillis = config.previousTimestampMillis,
            attemptStartedAtMillis = config.attemptStartedAtMillis,
            completionTimestampMillis = config.completionTimestampMillis
        )
    )
}
