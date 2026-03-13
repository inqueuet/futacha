package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.network.selectLatestArchiveMatch
import io.ktor.client.plugins.ResponseException

internal sealed interface ThreadUiState {
    data object Loading : ThreadUiState
    data class Error(val message: String = "スレッドを読み込めませんでした") : ThreadUiState
    data class Success(val page: ThreadPage) : ThreadUiState
}

internal fun buildThreadInitialLoadErrorMessage(error: Throwable, statusCode: Int?): String {
    val message = error.message
    return when {
        message?.contains("timeout", ignoreCase = true) == true -> "タイムアウト: サーバーが応答しません"
        statusCode == 404 -> "スレッドが見つかりません (404)"
        statusCode == 410 -> "スレッドは削除済みです (410)"
        statusCode != null && statusCode >= 500 -> "サーバーエラー ($statusCode)"
        message?.contains("HTTP error") == true -> "ネットワークエラー: $message"
        message?.contains("exceeds maximum") == true -> "データサイズが大きすぎます"
        else -> "スレッドを読み込めませんでした: ${message ?: "不明なエラー"}"
    }
}

internal fun buildThreadInitialLoadFailureState(
    error: Throwable,
    statusCode: Int?
): ThreadUiState.Error {
    return ThreadUiState.Error(buildThreadInitialLoadErrorMessage(error, statusCode))
}

internal data class ThreadLoadSuccessState(
    val uiState: ThreadUiState.Success,
    val historyEntry: ThreadHistoryEntry
)

internal data class ThreadLoadUiOutcome(
    val uiState: ThreadUiState? = null,
    val historyEntry: ThreadHistoryEntry? = null,
    val snackbarMessage: String? = null
)

internal fun buildThreadLoadSuccessState(
    page: ThreadPage,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    overrideThreadUrl: String?
): ThreadLoadSuccessState {
    return ThreadLoadSuccessState(
        uiState = ThreadUiState.Success(page),
        historyEntry = buildHistoryEntryFromPage(
            page = page,
            history = history,
            threadId = threadId,
            threadTitle = threadTitle,
            board = board,
            overrideThreadUrl = overrideThreadUrl
        )
    )
}

internal fun buildThreadInitialLoadUiOutcome(
    page: ThreadPage,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    overrideThreadUrl: String?,
    usedOffline: Boolean
): ThreadLoadUiOutcome {
    val successState = buildThreadLoadSuccessState(
        page = page,
        history = history,
        threadId = threadId,
        threadTitle = threadTitle,
        board = board,
        overrideThreadUrl = overrideThreadUrl
    )
    return ThreadLoadUiOutcome(
        uiState = successState.uiState,
        historyEntry = successState.historyEntry,
        snackbarMessage = if (usedOffline) "ローカルコピーを表示しています" else null
    )
}

internal fun buildThreadInitialLoadFailureUiOutcome(
    error: Throwable,
    statusCode: Int?
): ThreadLoadUiOutcome {
    val failureState = buildThreadInitialLoadFailureState(error, statusCode)
    return ThreadLoadUiOutcome(
        uiState = failureState,
        snackbarMessage = failureState.message
    )
}

internal fun buildThreadRefreshSuccessMessage(usedOffline: Boolean): String {
    return if (usedOffline) {
        "ネットワーク接続不可: ローカルコピーを表示しています"
    } else {
        "スレッドを更新しました"
    }
}

internal fun buildThreadRefreshFailureMessage(error: Throwable, statusCode: Int?): String {
    return when (statusCode) {
        404 -> "更新に失敗しました: スレッドが見つかりません (404)"
        410 -> "更新に失敗しました: スレッドは削除済みです (410)"
        else -> "更新に失敗しました: ${error.message ?: "不明なエラー"}"
    }
}

internal fun buildThreadManualRefreshUiOutcome(
    page: ThreadPage,
    history: List<ThreadHistoryEntry>,
    threadId: String,
    threadTitle: String?,
    board: BoardSummary,
    overrideThreadUrl: String?,
    usedOffline: Boolean
): ThreadLoadUiOutcome {
    val successState = buildThreadLoadSuccessState(
        page = page,
        history = history,
        threadId = threadId,
        threadTitle = threadTitle,
        board = board,
        overrideThreadUrl = overrideThreadUrl
    )
    return ThreadLoadUiOutcome(
        uiState = successState.uiState,
        historyEntry = successState.historyEntry,
        snackbarMessage = buildThreadRefreshSuccessMessage(usedOffline)
    )
}

internal fun buildThreadManualRefreshFailureUiOutcome(
    error: Throwable,
    statusCode: Int?
): ThreadLoadUiOutcome {
    return ThreadLoadUiOutcome(
        snackbarMessage = buildThreadRefreshFailureMessage(error, statusCode)
    )
}

internal enum class ThreadRefreshAvailability {
    Busy,
    Ready
}

internal fun resolveThreadRefreshAvailability(
    isRefreshing: Boolean
): ThreadRefreshAvailability {
    return if (isRefreshing) {
        ThreadRefreshAvailability.Busy
    } else {
        ThreadRefreshAvailability.Ready
    }
}

internal fun buildThreadRefreshBusyMessage(): String = "更新中です…"

private val THREAD_URL_ID_REGEX = Regex("""/res/(\d+)\.html?""", RegexOption.IGNORE_CASE)

internal sealed interface ThreadRemoteFetchRequest {
    data class ByUrl(val url: String) : ThreadRemoteFetchRequest
    data class ByBoard(
        val boardUrl: String,
        val threadId: String
    ) : ThreadRemoteFetchRequest
}

internal fun resolveThreadRemoteFetchRequest(
    threadUrl: String?,
    targetThreadId: String,
    boardUrl: String
): ThreadRemoteFetchRequest {
    val normalizedUrl = threadUrl?.trim()?.takeIf { it.isNotBlank() }
    val matchedThreadId = normalizedUrl
        ?.let(THREAD_URL_ID_REGEX::find)
        ?.groupValues
        ?.getOrNull(1)
    return if (matchedThreadId == targetThreadId) {
        ThreadRemoteFetchRequest.ByUrl(normalizedUrl)
    } else {
        ThreadRemoteFetchRequest.ByBoard(
            boardUrl = boardUrl,
            threadId = targetThreadId
        )
    }
}

internal sealed interface ArchiveFallbackOutcome {
    data class Success(val page: ThreadPage, val threadUrl: String?) : ArchiveFallbackOutcome
    data object NotFound : ArchiveFallbackOutcome
    data object NoMatch : ArchiveFallbackOutcome
}

internal data class ThreadLoadFallbackState(
    val statusCode: Int?,
    val shouldTryArchiveFallback: Boolean,
    val shouldTryOfflineFallback: Boolean,
    val shouldThrowWhenArchiveNotFound: Boolean
)

internal sealed interface ThreadLoadPostArchiveDecision {
    data class UseArchive(
        val page: ThreadPage,
        val threadUrl: String?
    ) : ThreadLoadPostArchiveDecision
    data object TryOffline : ThreadLoadPostArchiveDecision
    data class Fail(val error: Throwable) : ThreadLoadPostArchiveDecision
}

internal sealed interface ThreadLoadPostOfflineDecision {
    data class UseOffline(val page: ThreadPage) : ThreadLoadPostOfflineDecision
    data class Fail(val error: Throwable) : ThreadLoadPostOfflineDecision
}

internal fun buildArchiveFallbackTimeoutMessage(
    threadId: String,
    timeoutMillis: Long
): String {
    return "Archive fallback timed out for threadId=$threadId after ${timeoutMillis}ms"
}

internal fun buildArchiveSearchFailureLogMessage(threadId: String, error: Throwable): String {
    return "Archive search failed for $threadId: ${error.message}"
}

internal fun buildArchiveRefreshSuccessLogMessage(threadId: String): String {
    return "Archive refresh succeeded for $threadId"
}

internal fun resolveArchiveFallbackMatchUrl(
    items: List<ArchiveSearchItem>,
    threadId: String
): String? {
    return selectLatestArchiveMatch(items, threadId)?.htmlUrl
}

internal data class ArchiveFallbackAttemptState(
    val outcome: ArchiveFallbackOutcome,
    val successLogMessage: String? = null
)

internal fun resolveArchiveFallbackAttemptState(
    threadId: String,
    threadUrl: String,
    page: ThreadPage?,
    error: Throwable?
): ArchiveFallbackAttemptState {
    val outcome = resolveArchiveThreadFetchOutcome(
        page = page,
        error = error,
        threadUrl = threadUrl
    )
    return ArchiveFallbackAttemptState(
        outcome = outcome,
        successLogMessage = if (page != null) buildArchiveRefreshSuccessLogMessage(threadId) else null
    )
}

internal fun resolveThreadLoadPostArchiveDecision(
    primaryError: Throwable,
    fallbackState: ThreadLoadFallbackState,
    archiveOutcome: ArchiveFallbackOutcome
): ThreadLoadPostArchiveDecision {
    return when (archiveOutcome) {
        is ArchiveFallbackOutcome.Success -> ThreadLoadPostArchiveDecision.UseArchive(
            page = archiveOutcome.page,
            threadUrl = archiveOutcome.threadUrl
        )
        ArchiveFallbackOutcome.NotFound -> {
            if (fallbackState.shouldThrowWhenArchiveNotFound) {
                ThreadLoadPostArchiveDecision.Fail(primaryError)
            } else if (fallbackState.shouldTryOfflineFallback) {
                ThreadLoadPostArchiveDecision.TryOffline
            } else {
                ThreadLoadPostArchiveDecision.Fail(primaryError)
            }
        }
        ArchiveFallbackOutcome.NoMatch -> {
            if (fallbackState.shouldTryOfflineFallback) {
                ThreadLoadPostArchiveDecision.TryOffline
            } else {
                ThreadLoadPostArchiveDecision.Fail(primaryError)
            }
        }
    }
}

internal fun resolveThreadLoadPostOfflineDecision(
    primaryError: Throwable,
    offlinePage: ThreadPage?
): ThreadLoadPostOfflineDecision {
    return if (offlinePage != null) {
        ThreadLoadPostOfflineDecision.UseOffline(offlinePage)
    } else {
        ThreadLoadPostOfflineDecision.Fail(primaryError)
    }
}

internal fun normalizeArchiveQuery(raw: String?, maxLength: Int): String {
    if (raw.isNullOrBlank() || maxLength <= 0) return ""
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

internal fun buildArchiveFallbackQueryCandidates(
    threadId: String,
    threadTitle: String?
): List<String> {
    return buildList {
        normalizeArchiveQuery(threadId, maxLength = 64)
            .takeIf { it.isNotBlank() }
            ?.let { add(it) }
        normalizeArchiveQuery(threadTitle, maxLength = 120)
            .takeIf { it.isNotBlank() }
            ?.let { add(it) }
    }.distinct()
}

internal fun resolveArchiveThreadFetchOutcome(
    page: ThreadPage?,
    error: Throwable?,
    threadUrl: String?
): ArchiveFallbackOutcome {
    if (page != null) {
        return ArchiveFallbackOutcome.Success(page, threadUrl)
    }
    return when (error?.statusCodeOrNull()) {
        404, 410 -> ArchiveFallbackOutcome.NotFound
        else -> ArchiveFallbackOutcome.NoMatch
    }
}

internal fun Throwable.statusCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        when (current) {
            is NetworkException -> {
                current.statusCode?.let { return it }
            }
            is ResponseException -> return current.response.status.value
        }
        current = current.cause
    }
    return null
}

internal fun isOfflineFallbackCandidate(error: Throwable): Boolean {
    if (error.statusCodeOrNull() != null) return true
    var current: Throwable? = error
    while (current != null) {
        if (current is NetworkException) return true
        val message = current.message?.lowercase().orEmpty()
        if (
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("http error") ||
            message.contains("connection") ||
            message.contains("unable to resolve") ||
            message.contains("dns") ||
            message.contains("socket")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun resolveThreadLoadFallbackState(
    error: Throwable,
    allowOfflineFallback: Boolean
): ThreadLoadFallbackState {
    val statusCode = error.statusCodeOrNull()
    val shouldTryArchiveFallback = statusCode == 404 || statusCode == 410
    val shouldTryOfflineFallback = allowOfflineFallback && isOfflineFallbackCandidate(error)
    return ThreadLoadFallbackState(
        statusCode = statusCode,
        shouldTryArchiveFallback = shouldTryArchiveFallback,
        shouldTryOfflineFallback = shouldTryOfflineFallback,
        shouldThrowWhenArchiveNotFound = !allowOfflineFallback
    )
}
