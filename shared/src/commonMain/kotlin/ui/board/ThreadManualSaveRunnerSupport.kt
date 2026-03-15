package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.service.ThreadSaveService
import kotlinx.coroutines.CancellationException

internal data class ThreadManualSaveRunnerConfig(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val expiresAtLabel: String?,
    val posts: List<Post>,
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
                baseSaveLocation = config.baseSaveLocation,
                baseDirectory = config.baseDirectory,
                writeMetadata = true
            )
        }
    )
}

internal suspend fun performThreadManualSave(
    config: ThreadManualSaveRunnerConfig,
    callbacks: ThreadManualSaveRunnerCallbacks
): ThreadManualSaveRunResult {
    return try {
        callbacks.saveThread(config)
            .fold(
                onSuccess = { ThreadManualSaveRunResult.Success(it) },
                onFailure = { ThreadManualSaveRunResult.Failure(it, isUnexpected = false) }
            )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ThreadManualSaveRunResult.Failure(error, isUnexpected = true)
    }
}
