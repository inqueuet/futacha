package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrl
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal data class ThreadArchiveFallbackPlan(
    val scope: ArchiveSearchScope?,
    val threadUrl: String?
)

internal fun buildThreadArchiveFallbackPlan(
    threadId: String,
    threadTitle: String?,
    boardUrl: String,
    threadUrlOverride: String?
): ThreadArchiveFallbackPlan {
    val sourceUrl = threadUrlOverride?.trim()?.takeIf { it.isNotBlank() } ?: boardUrl
    return ThreadArchiveFallbackPlan(
        scope = extractArchiveSearchScope(sourceUrl),
        threadUrl = threadUrlOverride
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { buildInqueuetArchiveThreadUrlFromUrl(it) }
            ?: buildInqueuetArchiveThreadUrl(boardUrl, threadId)
    )
}

internal suspend fun performThreadArchiveFallback(
    httpClient: HttpClient?,
    repository: BoardRepository,
    threadId: String,
    threadTitle: String?,
    boardUrl: String,
    threadUrlOverride: String?,
    archiveSearchJson: Json,
    onSearchFailure: (String) -> Unit = {},
    onSuccessLog: (String) -> Unit = {}
): ArchiveFallbackOutcome {
    if (httpClient == null) return ArchiveFallbackOutcome.NoMatch
    val plan = buildThreadArchiveFallbackPlan(
        threadId = threadId,
        threadTitle = threadTitle,
        boardUrl = boardUrl,
        threadUrlOverride = threadUrlOverride
    )
    val matchUrl = plan.threadUrl ?: return ArchiveFallbackOutcome.NoMatch
    val pageResult = try {
        Result.success(
            withContext(AppDispatchers.io) {
                repository.getThreadContentByUrl(matchUrl)
            }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (error: Throwable) {
        Result.failure(error)
    }
    val content = pageResult.getOrNull()
    val attemptState = resolveArchiveFallbackAttemptState(
        threadId = threadId,
        threadUrl = matchUrl,
        page = content?.page,
        embeddedHtml = content?.embeddedHtml.orEmpty(),
        error = pageResult.exceptionOrNull()
    )
    attemptState.successLogMessage?.let(onSuccessLog)
    return attemptState.outcome
}
