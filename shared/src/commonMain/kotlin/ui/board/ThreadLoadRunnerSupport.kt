package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

internal data class ThreadLoadRunnerConfig(
    val threadId: String,
    val effectiveBoardUrl: String,
    val threadUrlOverride: String?,
    val allowOfflineFallback: Boolean,
    val archiveFallbackTimeoutMillis: Long,
    val offlineFallbackTimeoutMillis: Long
)

internal fun buildThreadLoadRunnerConfig(
    threadId: String,
    effectiveBoardUrl: String,
    threadUrlOverride: String?,
    allowOfflineFallback: Boolean,
    archiveFallbackTimeoutMillis: Long,
    offlineFallbackTimeoutMillis: Long
): ThreadLoadRunnerConfig {
    return ThreadLoadRunnerConfig(
        threadId = threadId,
        effectiveBoardUrl = effectiveBoardUrl,
        threadUrlOverride = threadUrlOverride,
        allowOfflineFallback = allowOfflineFallback,
        archiveFallbackTimeoutMillis = archiveFallbackTimeoutMillis,
        offlineFallbackTimeoutMillis = offlineFallbackTimeoutMillis
    )
}

internal data class ThreadLoadRunnerCallbacks(
    val loadRemoteByUrl: suspend (String) -> ThreadPageContent,
    val loadRemoteByBoard: suspend (String, String) -> ThreadPageContent,
    val loadArchiveFallback: suspend () -> ArchiveFallbackOutcome,
    val loadOfflineFallback: suspend () -> ThreadPage?,
    val onArchiveFallbackTimeout: (String) -> Unit = {},
    val onOfflineFallbackMiss: () -> Unit = {}
)

internal data class ThreadLoadExecutionResult(
    val page: ThreadPage,
    val embeddedHtml: List<com.valoser.futacha.shared.model.EmbeddedHtmlContent> = emptyList(),
    val usedOffline: Boolean,
    val nextThreadUrlOverride: String?
)

internal fun buildThreadLoadRunnerCallbacks(
    repository: BoardRepository,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    threadId: String,
    threadTitle: String?,
    boardUrl: String,
    archiveSearchJson: Json,
    offlineLookupContext: OfflineThreadLookupContext,
    offlineSources: List<OfflineThreadSource>,
    currentThreadUrlOverride: () -> String?,
    onWarning: (String) -> Unit = {},
    onInfo: (String) -> Unit = {}
): ThreadLoadRunnerCallbacks {
    return ThreadLoadRunnerCallbacks(
        loadRemoteByUrl = { url ->
            withContext(AppDispatchers.io) {
                repository.getThreadContentByUrl(url)
            }
        },
        loadRemoteByBoard = { effectiveBoardUrl, targetThreadId ->
            withContext(AppDispatchers.io) {
                repository.getThreadContent(effectiveBoardUrl, targetThreadId)
            }
        },
        loadArchiveFallback = {
            performThreadArchiveFallback(
                httpClient = httpClient,
                repository = repository,
                threadId = threadId,
                threadTitle = threadTitle,
                boardUrl = boardUrl,
                threadUrlOverride = currentThreadUrlOverride(),
                archiveSearchJson = archiveSearchJson,
                onSearchFailure = onWarning,
                onSuccessLog = onInfo
            )
        },
        loadOfflineFallback = {
            withContext(AppDispatchers.io) {
                loadOfflineThreadPage(
                    threadId = threadId,
                    lookupContext = offlineLookupContext,
                    fileSystem = fileSystem,
                    sources = offlineSources,
                    onBoardMismatch = { metadata ->
                        onWarning(
                            buildOfflineMetadataBoardMismatchLogMessage(
                                threadId = threadId,
                                boardUrl = metadata.boardUrl
                            )
                        )
                    }
                )
            }
        },
        onArchiveFallbackTimeout = onWarning,
        onOfflineFallbackMiss = {
            if (hasOfflineThreadSources(offlineSources)) {
                onInfo(
                    buildOfflineMetadataNotFoundLogMessage(
                        threadId = threadId,
                        boardIdCandidates = offlineLookupContext.boardIdCandidates
                    )
                )
            }
        }
    )
}

internal suspend fun performThreadLoadWithOfflineFallback(
    config: ThreadLoadRunnerConfig,
    callbacks: ThreadLoadRunnerCallbacks
): ThreadLoadExecutionResult {
    try {
        val content = when (
            val fetchRequest = resolveThreadRemoteFetchRequest(
                threadUrl = config.threadUrlOverride,
                targetThreadId = config.threadId,
                boardUrl = config.effectiveBoardUrl
            )
        ) {
            is ThreadRemoteFetchRequest.ByUrl -> callbacks.loadRemoteByUrl(fetchRequest.url)
            is ThreadRemoteFetchRequest.ByBoard -> callbacks.loadRemoteByBoard(
                fetchRequest.boardUrl,
                fetchRequest.threadId
            )
        }
        return ThreadLoadExecutionResult(
            page = content.page,
            embeddedHtml = content.embeddedHtml,
            usedOffline = false,
            nextThreadUrlOverride = config.threadUrlOverride
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val fallbackState = resolveThreadLoadFallbackState(
            error = e,
            allowOfflineFallback = config.allowOfflineFallback
        )
        val archiveOutcome = if (fallbackState.shouldTryArchiveFallback) {
            withTimeoutOrNull(config.archiveFallbackTimeoutMillis) {
                callbacks.loadArchiveFallback()
            } ?: run {
                callbacks.onArchiveFallbackTimeout(
                    buildArchiveFallbackTimeoutMessage(
                        threadId = config.threadId,
                        timeoutMillis = config.archiveFallbackTimeoutMillis
                    )
                )
                ArchiveFallbackOutcome.NoMatch
            }
        } else {
            ArchiveFallbackOutcome.NoMatch
        }
        return when (
            val archiveDecision = resolveThreadLoadPostArchiveDecision(
                primaryError = e,
                fallbackState = fallbackState,
                archiveOutcome = archiveOutcome
            )
        ) {
            is ThreadLoadPostArchiveDecision.UseArchive -> ThreadLoadExecutionResult(
                page = archiveDecision.page,
                embeddedHtml = archiveDecision.embeddedHtml,
                usedOffline = false,
                nextThreadUrlOverride = archiveDecision.threadUrl ?: config.threadUrlOverride
            )
            is ThreadLoadPostArchiveDecision.Fail -> throw archiveDecision.error
            ThreadLoadPostArchiveDecision.TryOffline -> {
                val offlinePage = withTimeoutOrNull(config.offlineFallbackTimeoutMillis) {
                    callbacks.loadOfflineFallback()
                }
                if (offlinePage == null) {
                    callbacks.onOfflineFallbackMiss()
                }
                when (
                    val offlineDecision = resolveThreadLoadPostOfflineDecision(
                        primaryError = e,
                        offlinePage = offlinePage
                    )
                ) {
                    is ThreadLoadPostOfflineDecision.UseOffline -> ThreadLoadExecutionResult(
                        page = offlineDecision.page,
                        embeddedHtml = offlineDecision.embeddedHtml,
                        usedOffline = true,
                        nextThreadUrlOverride = config.threadUrlOverride
                    )
                    is ThreadLoadPostOfflineDecision.Fail -> throw offlineDecision.error
                }
            }
        }
    }
}
