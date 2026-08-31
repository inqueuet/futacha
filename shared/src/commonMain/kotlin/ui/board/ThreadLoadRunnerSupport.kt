package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.compat.toThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal data class ThreadLoadRunnerConfig(
    val threadId: String,
    val effectiveBoardUrl: String,
    val threadUrlOverride: String?,
    val allowOfflineFallback: Boolean,
    val archiveFallbackTimeoutMillis: Long,
    val offlineFallbackTimeoutMillis: Long,
    val localStaleLoadTimeoutMillis: Long,
    val remoteLoadTimeoutMillis: Long,
    val preferOfflineFallbackAfterLocalStale: Boolean
)

internal fun buildThreadLoadRunnerConfig(
    threadId: String,
    effectiveBoardUrl: String,
    threadUrlOverride: String?,
    allowOfflineFallback: Boolean,
    archiveFallbackTimeoutMillis: Long,
    offlineFallbackTimeoutMillis: Long,
    localStaleLoadTimeoutMillis: Long = THREAD_LOCAL_STALE_LOAD_TIMEOUT_MS,
    remoteLoadTimeoutMillis: Long = THREAD_REMOTE_LOAD_TIMEOUT_MS,
    preferOfflineFallbackAfterLocalStale: Boolean = false
): ThreadLoadRunnerConfig {
    return ThreadLoadRunnerConfig(
        threadId = threadId,
        effectiveBoardUrl = effectiveBoardUrl,
        threadUrlOverride = threadUrlOverride,
        allowOfflineFallback = allowOfflineFallback,
        archiveFallbackTimeoutMillis = archiveFallbackTimeoutMillis,
        offlineFallbackTimeoutMillis = offlineFallbackTimeoutMillis,
        localStaleLoadTimeoutMillis = localStaleLoadTimeoutMillis.coerceAtLeast(1L),
        remoteLoadTimeoutMillis = remoteLoadTimeoutMillis.coerceAtLeast(1_000L),
        preferOfflineFallbackAfterLocalStale = preferOfflineFallbackAfterLocalStale
    )
}

internal data class ThreadLoadRunnerCallbacks(
    val loadRemoteByUrl: suspend (String) -> ThreadPageContent,
    val loadRemoteByBoard: suspend (String, String) -> ThreadPageContent,
    val loadArchiveFallback: suspend () -> ArchiveFallbackOutcome,
    val loadOfflineFallback: suspend () -> ThreadPage?,
    val loadLocalStalePage: suspend () -> ThreadPage? = loadOfflineFallback,
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
    boardName: String = "",
    boardUrl: String,
    archiveSearchJson: Json,
    offlineLookupContext: OfflineThreadLookupContext,
    offlineSources: List<OfflineThreadSource>,
    currentThreadUrlOverride: () -> String?,
    onWarning: (String) -> Unit = {},
    onInfo: (String) -> Unit = {},
    compatibilityStore: CompatibilityStore? = null
): ThreadLoadRunnerCallbacks {
    suspend fun cacheRemoteContent(rawUrl: String, content: ThreadPageContent) {
        val store = compatibilityStore ?: return
        val parsed = canonicalizeThreadUrl(rawUrl) ?: return
        val revision = Clock.System.now().toEpochMilliseconds()
        runSuspendCatchingPreservingCancellation {
            store.saveSharedThreadSnapshot(
                canonicalUrl = parsed.canonicalUrl,
                originalUrl = rawUrl,
                boardName = boardName,
                title = threadTitle.orEmpty(),
                thumbnailUrl = content.page.posts.firstOrNull()?.thumbnailUrl
                    ?: content.page.posts.firstOrNull()?.imageUrl,
                snapshot = content.page.toCompatThreadSnapshot(
                    tabKey = compatTabKey(parsed.canonicalUrl),
                    revision = revision
                )
            )
        }.onFailure { error ->
            onWarning("共有スレキャッシュの保存に失敗しました: ${error.message}")
        }
    }

    suspend fun loadSharedCachedPage(): ThreadPage? {
        val store = compatibilityStore ?: return null
        val rawUrl = currentThreadUrlOverride()
            ?: "${boardUrl.trimEnd('/')}/res/$threadId.htm"
        val parsed = canonicalizeThreadUrl(rawUrl) ?: return null
        return runSuspendCatchingPreservingCancellation {
            store.loadThreadSnapshotByCanonicalUrl(parsed.canonicalUrl)
                ?.toThreadPage(threadId)
        }.onFailure { error ->
            onWarning("共有スレキャッシュの読み込みに失敗しました: ${error.message}")
        }.getOrNull()
    }

    return ThreadLoadRunnerCallbacks(
        loadRemoteByUrl = { url ->
            val content = withContext(AppDispatchers.io) {
                repository.getThreadContentByUrl(url)
            }
            cacheRemoteContent(url, content)
            content
        },
        loadRemoteByBoard = { effectiveBoardUrl, targetThreadId ->
            val content = withContext(AppDispatchers.io) {
                repository.getThreadContent(effectiveBoardUrl, targetThreadId)
            }
            cacheRemoteContent(
                "${effectiveBoardUrl.trimEnd('/')}/res/$targetThreadId.htm",
                content
            )
            content
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
                ) ?: loadSharedCachedPage()
            }
        },
        loadLocalStalePage = {
            withContext(AppDispatchers.io) {
                loadOfflineThreadPage(
                    threadId = threadId,
                    lookupContext = offlineLookupContext,
                    fileSystem = fileSystem,
                    sources = offlineSources
                ) ?: loadSharedCachedPage()
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

internal suspend fun loadThreadLocalStalePageIfAvailable(
    config: ThreadLoadRunnerConfig,
    callbacks: ThreadLoadRunnerCallbacks
): ThreadLoadExecutionResult? {
    if (!config.allowOfflineFallback) return null
    val localPage = withTimeoutOrNull(config.localStaleLoadTimeoutMillis) {
        callbacks.loadLocalStalePage()
    } ?: return null
    return ThreadLoadExecutionResult(
        page = localPage,
        usedOffline = true,
        nextThreadUrlOverride = config.threadUrlOverride
    )
}

internal suspend fun performThreadLoadWithOfflineFallback(
    config: ThreadLoadRunnerConfig,
    callbacks: ThreadLoadRunnerCallbacks
): ThreadLoadExecutionResult {
    try {
        val content = withTimeoutOrNull(config.remoteLoadTimeoutMillis) {
            when (
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
        } ?: throw IllegalStateException("Thread load timed out after ${config.remoteLoadTimeoutMillis}ms")
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
        val shouldPreferOffline = shouldPreferOfflineFallbackAfterLocalStale(
            config = config,
            fallbackState = fallbackState
        )
        val archiveOutcome = if (!shouldPreferOffline && fallbackState.shouldTryArchiveFallback) {
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
            val archiveDecision = if (shouldPreferOffline) {
                ThreadLoadPostArchiveDecision.TryOffline
            } else {
                resolveThreadLoadPostArchiveDecision(
                    primaryError = e,
                    fallbackState = fallbackState,
                    archiveOutcome = archiveOutcome
                )
            }
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
