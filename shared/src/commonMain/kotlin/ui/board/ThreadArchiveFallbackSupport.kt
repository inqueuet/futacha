package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

internal data class ThreadArchiveFallbackPlan(
    val scope: ArchiveSearchScope?,
    val queryCandidates: List<String>
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
        queryCandidates = buildArchiveFallbackQueryCandidates(
            threadId = threadId,
            threadTitle = threadTitle
        )
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
    val client = httpClient ?: return ArchiveFallbackOutcome.NoMatch
    val plan = buildThreadArchiveFallbackPlan(
        threadId = threadId,
        threadTitle = threadTitle,
        boardUrl = boardUrl,
        threadUrlOverride = threadUrlOverride
    )
    if (plan.queryCandidates.isEmpty()) return ArchiveFallbackOutcome.NoMatch
    for (query in plan.queryCandidates) {
        val results = try {
            withContext(AppDispatchers.io) {
                fetchArchiveSearchResults(client, query, plan.scope, archiveSearchJson)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            onSearchFailure(buildArchiveSearchFailureLogMessage(threadId, error))
            continue
        }
        val matchUrl = resolveArchiveFallbackMatchUrl(results, threadId) ?: continue
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
        if (attemptState.outcome !is ArchiveFallbackOutcome.NoMatch) {
            return attemptState.outcome
        }
    }
    return ArchiveFallbackOutcome.NoMatch
}
