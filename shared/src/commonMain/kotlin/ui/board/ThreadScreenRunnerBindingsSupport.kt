package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

internal data class ThreadScreenRunnerBindings(
    val loadRunnerCallbacks: ThreadLoadRunnerCallbacks,
    val loadRunnerConfig: ThreadLoadRunnerConfig,
    val singleMediaSaveRunnerCallbacks: ThreadSingleMediaSaveRunnerCallbacks?,
    val deleteByUserActionCallbacks: ThreadDeleteByUserActionCallbacks,
    val replyActionCallbacks: ThreadReplyActionCallbacks
)

internal fun buildThreadScreenRunnerBindings(
    repository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    threadId: String,
    threadTitle: String?,
    boardUrl: String,
    effectiveBoardUrl: String,
    threadUrlOverride: String?,
    archiveSearchJson: Json,
    offlineLookupContext: OfflineThreadLookupContext,
    offlineSources: List<OfflineThreadSource>,
    currentThreadUrlOverride: () -> String?,
    onWarning: (String) -> Unit = {},
    onInfo: (String) -> Unit = {}
): ThreadScreenRunnerBindings {
    return ThreadScreenRunnerBindings(
        loadRunnerCallbacks = buildThreadLoadRunnerCallbacks(
            repository = repository,
            httpClient = httpClient,
            fileSystem = fileSystem,
            threadId = threadId,
            threadTitle = threadTitle,
            boardUrl = boardUrl,
            archiveSearchJson = archiveSearchJson,
            offlineLookupContext = offlineLookupContext,
            offlineSources = offlineSources,
            currentThreadUrlOverride = currentThreadUrlOverride,
            onWarning = onWarning,
            onInfo = onInfo
        ),
        loadRunnerConfig = buildThreadLoadRunnerConfig(
            threadId = threadId,
            effectiveBoardUrl = effectiveBoardUrl,
            threadUrlOverride = threadUrlOverride,
            allowOfflineFallback = true,
            archiveFallbackTimeoutMillis = ARCHIVE_FALLBACK_TIMEOUT_MS,
            offlineFallbackTimeoutMillis = OFFLINE_FALLBACK_TIMEOUT_MS
        ),
        singleMediaSaveRunnerCallbacks = buildOptionalThreadSingleMediaSaveRunnerCallbacks(
            httpClient = httpClient,
            fileSystem = fileSystem
        ),
        deleteByUserActionCallbacks = buildThreadDeleteByUserActionCallbacks(repository),
        replyActionCallbacks = buildThreadReplyActionCallbacks(repository)
    )
}
