package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrl
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.ui.compat.buildCompatArchiveThreadCandidates
import com.valoser.futacha.shared.ui.compat.fetchCompatArchiveThreadPage
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    // The plan only produces validated inqueuet URLs. Recover the original
    // thread URL also when a previous load already switched to that archive.
    val archiveUrl = Url(matchUrl)
    val sourceUrl = "https://${archiveUrl.host.substringBefore('.')}.2chan.net${archiveUrl.encodedPath}"
    var fallbackOutcome: ArchiveFallbackOutcome = ArchiveFallbackOutcome.NoMatch
    var partialContent: ThreadPageContent? = null
    var partialUrl: String? = null
    val candidates = buildCompatArchiveThreadCandidates(sourceUrl)
    // Reserve time for merging and returning a partial result within the outer timeout.
    val providerTimeout = (ARCHIVE_FALLBACK_TIMEOUT_MS - 500L) / candidates.size.coerceAtLeast(1)
    for (candidate in candidates) {
        val content = try {
            withTimeoutOrNull(providerTimeout) {
                if (candidate == matchUrl) {
                    withContext(AppDispatchers.io) { repository.getThreadContentByUrl(candidate) }
                } else {
                    ThreadPageContent(fetchCompatArchiveThreadPage(httpClient, candidate))
                }.also {
                    require(it.page.posts.isNotEmpty() && it.page.threadId == threadId) {
                        "対象スレッドの本文を取得できませんでした"
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            val outcome = resolveArchiveThreadFetchOutcome(null, error, candidate)
            if (outcome == ArchiveFallbackOutcome.NotFound) fallbackOutcome = outcome
            onSearchFailure("アーカイブ取得失敗: $candidate (${error.message})")
            null
        }
        if (content != null) {
            val merged = partialContent?.let { previous ->
                previous.copy(page = withContext(AppDispatchers.parsing) {
                    com.valoser.futacha.shared.ui.compat.mergeCompatThreadPages(previous.page, listOf(content.page))
                })
            } ?: content
            if (merged.page.isTruncated) {
                partialContent = merged
                if (partialUrl == null) partialUrl = if (candidate == matchUrl) matchUrl else sourceUrl
                continue
            }
            onSuccessLog(buildArchiveRefreshSuccessLogMessage(threadId))
            return ArchiveFallbackOutcome.Success(
                page = merged.page,
                // Retry the live source on refresh. Third-party landing pages
                // require the archive fetcher rather than the regular parser.
                threadUrl = if (candidate == matchUrl) matchUrl else sourceUrl,
                embeddedHtml = merged.embeddedHtml
            )
        }
    }
    partialContent?.let { partial -> return ArchiveFallbackOutcome.Success(partial.page,
        partialUrl ?: sourceUrl, partial.embeddedHtml) }
    return fallbackOutcome
}
