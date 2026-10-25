package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrl
import com.valoser.futacha.shared.network.buildInqueuetArchiveThreadUrlFromUrl
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.isInqueuetArchiveUrl
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

/**
 * A row an archive refresh pointed at the archive's board (L90: before the fix it took
 * `https://may.inqueuet.com/b` and the archive page's title), put back on its own: the
 * registered board's address and name, or, with the board no longer registered, the 2chan
 * address the archive copies (the name then stays; nothing tells the right one). Only rows
 * with a board id: the fault could write no others (a row without one is found by its address,
 * so that update was dropped), and their identity does not change with the address.
 */
internal fun repairArchiveRewrittenHistoryBoard(
    entry: ThreadHistoryEntry,
    boardById: Map<String, BoardSummary>
): ThreadHistoryEntry {
    if (entry.boardId.isBlank() || !entry.boardUrl.contains(INQUEUET_DOMAIN, ignoreCase = true)) return entry
    if (!isInqueuetArchiveUrl(entry.boardUrl)) return entry
    val board = boardById[entry.boardId]
    val boardUrl = board?.url?.takeIf { it.isNotBlank() }
        ?: futabaUrlOfInqueuetUrl(entry.boardUrl)
        ?: return entry
    return entry.copy(
        boardUrl = boardUrl,
        boardName = board?.name?.takeIf { it.isNotBlank() } ?: entry.boardName
    )
}

private const val INQUEUET_DOMAIN = "inqueuet.com"

/** `https://may.inqueuet.com/b` → `https://may.2chan.net/b`; null without a server name. */
private fun futabaUrlOfInqueuetUrl(url: String): String? = runCatching {
    val parsed = Url(url.trim())
    val server = parsed.host.lowercase().trim('.').removeSuffix(".$INQUEUET_DOMAIN")
        .takeIf { it.isNotBlank() && '.' !in it }
        ?: return null
    "https://$server.2chan.net${parsed.encodedPath}"
}.getOrNull()

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
    if (httpClient == null) return ArchiveRefreshResult.NoMatch
    val scope = extractArchiveSearchScope(board?.url ?: entry.boardUrl)
    val archiveThreadUrl = buildInqueuetArchiveThreadUrlFromUrl(entry.boardUrl)
        ?: buildInqueuetArchiveThreadUrl(board?.url ?: entry.boardUrl, entry.threadId)
        ?: return ArchiveRefreshResult.NoMatch
    return resolveHistoryArchiveEntry(
        entry = entry,
        board = board,
        match = ArchiveSearchItem(
            threadId = entry.threadId,
            server = scope?.server.orEmpty(),
            board = scope?.board.orEmpty(),
            title = entry.title,
            htmlUrl = archiveThreadUrl,
            thumbUrl = entry.titleImageUrl
        ),
        repository = repository,
        fetchTimeoutMillis = fetchTimeoutMillis,
        tag = tag
    )
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
            // The row keeps its own board (L90): the copy's address and title are the archive's.
            // Written over the row they pointed it at inqueuet, and a row without a board id,
            // found by that address, was no longer found, so the fall mark was dropped with it.
            entry.copy(
                title = resolvedTitle,
                titleImageUrl = resolvedImage,
                boardName = boardName.ifBlank { page.boardTitle.orEmpty() },
                replyCount = page.posts.size
            )
        )
    } else {
        val detail = pageError?.message?.takeIf { it.isNotBlank() } ?: "Archive thread fetch failed"
        ArchiveRefreshResult.Error(detail)
    }
}
