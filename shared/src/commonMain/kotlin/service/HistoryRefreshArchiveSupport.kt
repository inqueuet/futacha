package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.network.selectLatestArchiveMatch
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.resolveThreadTitle
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal sealed interface ArchiveRefreshResult {
    data class Success(val entry: ThreadHistoryEntry) : ArchiveRefreshResult
    data object NotFound : ArchiveRefreshResult
    data object NoMatch : ArchiveRefreshResult
    data class Error(val message: String) : ArchiveRefreshResult
}

internal fun resolveArchiveBaseUrl(
    threadUrl: String,
    fallbackBoardUrl: String?
): String? {
    if (threadUrl.isBlank()) return fallbackBoardUrl?.takeIf { it.isNotBlank() }
    if (!threadUrl.contains("://")) {
        return fallbackBoardUrl?.takeIf { it.isNotBlank() }
    }
    return runCatching {
        val url = Url(threadUrl)
        val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
        val boardSegments = segments.takeWhile { it.lowercase() != "res" }
        if (boardSegments.isEmpty()) {
            fallbackBoardUrl?.takeIf { it.isNotBlank() }
        } else {
            val path = "/" + boardSegments.joinToString("/")
            buildString {
                append(url.protocol.name)
                append("://")
                append(url.host)
                if (url.port != url.protocol.defaultPort) {
                    append(":${url.port}")
                }
                append(path.trimEnd('/'))
            }
        }
    }.getOrElse { fallbackBoardUrl?.takeIf { it.isNotBlank() } }
}

internal fun normalizeHistoryArchiveQuery(raw: String, maxLength: Int): String {
    if (maxLength <= 0) return ""
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val builder = StringBuilder(minOf(trimmed.length, maxLength))
    var previousWasWhitespace = false
    for (ch in trimmed) {
        val isWhitespace = ch.isWhitespace()
        if (isWhitespace) {
            if (!previousWasWhitespace && builder.isNotEmpty()) {
                builder.append(' ')
            }
        } else {
            builder.append(ch)
            if (builder.length >= maxLength) break
        }
        previousWasWhitespace = isWhitespace
    }
    return builder.toString().trim()
}

internal suspend fun tryRefreshHistoryEntryFromArchive(
    entry: ThreadHistoryEntry,
    board: BoardSummary?,
    httpClient: HttpClient?,
    repository: BoardRepository,
    fetchTimeoutMillis: Long,
    archiveSearchJson: Json,
    tag: String
): ArchiveRefreshResult {
    val client = httpClient ?: return ArchiveRefreshResult.NoMatch
    val scope = extractArchiveSearchScope(board?.url ?: entry.boardUrl)
    val queryCandidates = buildList {
        normalizeHistoryArchiveQuery(entry.threadId, maxLength = 64)
            .takeIf { it.isNotBlank() }
            ?.let { add(it) }
        normalizeHistoryArchiveQuery(entry.title, maxLength = 120)
            .takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }.distinct()
    if (queryCandidates.isEmpty()) return ArchiveRefreshResult.NoMatch

    var lastErrorMessage: String? = null
    var hadSuccessfulSearch = false
    queryCandidates.forEach { query ->
        val results = try {
            withTimeoutOrNull(fetchTimeoutMillis) {
                fetchArchiveSearchResults(client, query, scope, archiveSearchJson)
            } ?: run {
                val detail = "Archive search timed out"
                lastErrorMessage = detail
                Logger.w(tag, "Archive search timed out for ${entry.threadId}")
                return@forEach
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Throwable) {
            val detail = error.message ?: "Archive search failed"
            lastErrorMessage = detail
            Logger.w(tag, "Archive search failed for ${entry.threadId}: $detail")
            return@forEach
        }
        hadSuccessfulSearch = true
        val matched = selectLatestArchiveMatch(results, entry.threadId) ?: return@forEach
        return resolveHistoryArchiveEntry(
            entry = entry,
            board = board,
            match = matched,
            repository = repository,
            fetchTimeoutMillis = fetchTimeoutMillis,
            tag = tag
        )
    }
    if (hadSuccessfulSearch) return ArchiveRefreshResult.NoMatch
    return lastErrorMessage?.let { ArchiveRefreshResult.Error(it) } ?: ArchiveRefreshResult.NoMatch
}

internal suspend fun resolveHistoryArchiveEntry(
    entry: ThreadHistoryEntry,
    board: BoardSummary?,
    match: ArchiveSearchItem,
    repository: BoardRepository,
    fetchTimeoutMillis: Long,
    tag: String
): ArchiveRefreshResult {
    val baseUrl = resolveArchiveBaseUrl(match.htmlUrl, board?.url ?: entry.boardUrl)
    val boardName = entry.boardName.ifBlank { board?.name.orEmpty() }
    val pageResult = try {
        val page = withTimeoutOrNull(fetchTimeoutMillis) {
            if (match.htmlUrl.isNotBlank()) {
                repository.getThreadByUrl(match.htmlUrl)
            } else {
                baseUrl?.let { repository.getThread(it, entry.threadId) }
            }
        }
        if (page == null) {
            Result.failure(NetworkException("Archive thread fetch timed out"))
        } else {
            Result.success(page)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
    val page = pageResult.getOrNull()
    val pageError = pageResult.exceptionOrNull()
    if (page == null && pageError != null && isHistoryRefreshNotFound(pageError)) {
        Logger.i(tag, "Archive thread missing for ${entry.threadId}")
        return ArchiveRefreshResult.NotFound
    }
    return if (page != null) {
        val opPost = page.posts.firstOrNull()
        val resolvedTitle = resolveThreadTitle(opPost, match.title ?: entry.title)
        val resolvedImage = opPost?.thumbnailUrl ?: match.thumbUrl ?: entry.titleImageUrl
        Logger.i(tag, "Archive refresh succeeded for ${entry.threadId}")
        ArchiveRefreshResult.Success(
            entry.copy(
                title = resolvedTitle,
                titleImageUrl = resolvedImage,
                boardName = page.boardTitle ?: boardName,
                boardUrl = baseUrl ?: entry.boardUrl,
                replyCount = page.posts.size
            )
        )
    } else {
        val detail = pageError?.message?.takeIf { it.isNotBlank() } ?: "Archive thread fetch failed"
        ArchiveRefreshResult.Error(detail)
    }
}
