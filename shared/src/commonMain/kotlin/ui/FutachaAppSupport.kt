package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.util.Logger
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath

private const val FUTACHA_APP_SUPPORT_LOG_TAG = "FutachaApp"

internal data class RepositoryHolder(
    val repository: BoardRepository,
    val ownsRepository: Boolean
)

internal data class FutachaThreadSelection(
    val boardId: String,
    val threadId: String,
    val threadTitle: String?,
    val threadReplies: Int?,
    val threadThumbnailUrl: String?,
    val threadUrl: String?,
    val isSavedThreadsVisible: Boolean = false
)

internal fun resolveHistoryEntrySelection(
    entry: ThreadHistoryEntry,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val entryBoardUrlKey = entry.boardUrl
        .trim()
        .substringBefore('?')
        .trimEnd('/')
        .lowercase()
    val targetBoard = boards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
        ?: boards.firstOrNull {
            it.url.trim().substringBefore('?').trimEnd('/').lowercase() == entryBoardUrlKey
        }
        ?: boards.firstOrNull { it.name == entry.boardName }

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            threadId = entry.threadId,
            threadTitle = entry.title,
            threadReplies = entry.replyCount,
            threadThumbnailUrl = entry.titleImageUrl,
            threadUrl = entry.boardUrl.takeIf { url ->
                Regex("""/res/\d+\.html?""", RegexOption.IGNORE_CASE).containsMatchIn(url)
            }
        )
    }
}

internal fun resolveSavedThreadSelection(
    thread: SavedThread,
    boards: List<BoardSummary>
): FutachaThreadSelection? {
    val targetBoard = boards.firstOrNull { it.id == thread.boardId }
        ?: boards.firstOrNull { it.name == thread.boardName }

    return targetBoard?.let { board ->
        FutachaThreadSelection(
            boardId = board.id,
            threadId = thread.threadId,
            threadTitle = thread.title,
            threadReplies = thread.postCount,
            threadThumbnailUrl = null,
            threadUrl = null,
            isSavedThreadsVisible = false
        )
    }
}

internal fun shouldApplyRegisteredThreadNavigation(
    currentBoardId: String?,
    currentThreadId: String?,
    currentThreadUrl: String?,
    target: RegisteredThreadNavigation
): Boolean {
    return !(currentBoardId == target.board.id &&
        currentThreadId == target.threadId &&
        currentThreadUrl == target.threadUrl)
}

internal fun isSelectedBoardStillMissing(
    selectedBoardId: String?,
    missingBoardId: String,
    boards: List<BoardSummary>
): Boolean {
    return selectedBoardId == missingBoardId && boards.none { it.id == missingBoardId }
}

internal fun resolveHistoryEntryBoardId(entry: ThreadHistoryEntry): String? {
    val resolvedBoardId = entry.boardId
        .ifBlank { runCatching { BoardUrlResolver.resolveBoardSlug(entry.boardUrl) }.getOrDefault("") }
        .ifBlank { "" }
    return resolvedBoardId.ifBlank { null }
}

internal suspend fun dismissHistoryEntry(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    entry: ThreadHistoryEntry,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    val resolvedBoardId = resolveHistoryEntryBoardId(entry)
    stateStore.removeSelfPostIdentifiersForThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )
    stateStore.removeHistoryEntry(entry)
    autoSavedThreadRepository?.deleteThread(
        threadId = entry.threadId,
        boardId = resolvedBoardId
    )?.onFailure(onAutoSavedThreadDeleteFailure)
}

internal suspend fun clearHistory(
    stateStore: AppStateStore,
    autoSavedThreadRepository: SavedThreadRepository?,
    onSkippedThreadsCleared: () -> Unit,
    onAutoSavedThreadDeleteFailure: (Throwable) -> Unit = {}
) {
    stateStore.clearSelfPostIdentifiers()
    stateStore.setHistory(emptyList())
    onSkippedThreadsCleared()
    autoSavedThreadRepository?.deleteAllThreads()?.onFailure(onAutoSavedThreadDeleteFailure)
}

internal fun isDefaultManualSaveRoot(directory: String): Boolean {
    val normalized = directory.trim().removePrefix("./").trimEnd('/')
    return normalized.equals(DEFAULT_MANUAL_SAVE_ROOT, ignoreCase = true)
}

internal fun BoardSummary.isMockBoard(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

internal fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            Logger.w(
                FUTACHA_APP_SUPPORT_LOG_TAG,
                "HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed"
            )
            trimmed
        }
        else -> "https://$trimmed"
    }

    if (withScheme.contains("futaba.php", ignoreCase = true)) {
        return withScheme
    }

    return runCatching {
        val parsed = Url(withScheme)
        val normalizedPath = when {
            parsed.encodedPath.isBlank() || parsed.encodedPath == "/" -> "/futaba.php"
            parsed.encodedPath.endsWith("/") -> "${parsed.encodedPath}futaba.php"
            else -> "${parsed.encodedPath}/futaba.php"
        }
        URLBuilder(parsed).apply { encodedPath = normalizedPath }.buildString()
    }.getOrElse {
        val fragment = withScheme.substringAfter('#', missingDelimiterValue = "")
        val withoutFragment = withScheme.substringBefore('#')
        val base = withoutFragment.substringBefore('?').trimEnd('/')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        buildString {
            append(base)
            append("/futaba.php")
            if (query.isNotEmpty()) {
                append('?')
                append(query)
            }
            if (fragment.isNotEmpty()) {
                append('#')
                append(fragment)
            }
        }
    }
}
