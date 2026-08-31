package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

internal data class ThreadSingleMediaSaveRunnerConfig(
    val mediaUrl: String,
    val boardId: String,
    val threadId: String,
    val baseSaveLocation: SaveLocation?,
    val baseDirectory: String
)

internal fun buildThreadSingleMediaSaveRunnerConfig(
    mediaUrl: String,
    boardId: String,
    threadId: String,
    baseSaveLocation: SaveLocation?,
    baseDirectory: String
): ThreadSingleMediaSaveRunnerConfig {
    return ThreadSingleMediaSaveRunnerConfig(
        mediaUrl = mediaUrl,
        boardId = boardId,
        threadId = threadId,
        baseSaveLocation = baseSaveLocation,
        baseDirectory = baseDirectory
    )
}

internal sealed interface ThreadSingleMediaSaveRunResult {
    data class Success(val savedMedia: SavedMediaFile) : ThreadSingleMediaSaveRunResult
    data class Failure(
        val error: Throwable,
        val isUnexpected: Boolean
    ) : ThreadSingleMediaSaveRunResult
}

internal data class ThreadSingleMediaSaveRunnerCallbacks(
    val saveMedia: suspend (ThreadSingleMediaSaveRunnerConfig) -> Result<SavedMediaFile>
)

internal fun buildThreadSingleMediaSaveRunnerCallbacks(
    httpClient: HttpClient,
    fileSystem: FileSystem
): ThreadSingleMediaSaveRunnerCallbacks {
    return ThreadSingleMediaSaveRunnerCallbacks(
        saveMedia = { config ->
            SingleMediaSaveService(
                httpClient = httpClient,
                fileSystem = fileSystem
            ).saveMedia(
                mediaUrl = config.mediaUrl,
                boardId = config.boardId,
                threadId = config.threadId,
                baseSaveLocation = config.baseSaveLocation,
                baseDirectory = config.baseDirectory
            )
        }
    )
}

internal suspend fun performThreadSingleMediaSave(
    config: ThreadSingleMediaSaveRunnerConfig,
    callbacks: ThreadSingleMediaSaveRunnerCallbacks
): ThreadSingleMediaSaveRunResult {
    return try {
        callbacks.saveMedia(config)
            .fold(
                onSuccess = { ThreadSingleMediaSaveRunResult.Success(it) },
                onFailure = { ThreadSingleMediaSaveRunResult.Failure(it, isUnexpected = false) }
            )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ThreadSingleMediaSaveRunResult.Failure(error, isUnexpected = true)
    }
}
