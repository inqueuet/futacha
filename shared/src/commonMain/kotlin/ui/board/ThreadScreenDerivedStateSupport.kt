package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage

internal data class ThreadScreenDerivedUiState(
    val successState: ThreadUiState.Success?,
    val currentPage: ThreadPage?,
    val currentPosts: List<Post>,
    val resolvedReplyCount: Int?,
    val resolvedThreadTitle: String,
    val statusLabel: String?,
    val shouldPrepareReadAloudSegments: Boolean
)

internal fun buildThreadScreenDerivedUiState(
    currentState: ThreadUiState,
    initialReplyCount: Int?,
    threadTitle: String?,
    isReadAloudControlsVisible: Boolean,
    readAloudStatus: ReadAloudStatus,
    shouldPrepareReadAloudForCommand: Boolean = false,
    isOfflineCopy: Boolean = false
): ThreadScreenDerivedUiState {
    val successState = currentState as? ThreadUiState.Success
    val currentPage = successState?.page
    val currentPosts = currentPage?.posts.orEmpty()
    val resolvedReplyCount = successState?.page?.posts?.size ?: initialReplyCount
    val resolvedThreadTitle = successState?.page?.posts?.firstOrNull()?.subject
        ?.let { threadTitle ?: it }
        ?: threadTitle
        ?: "スレッド"
    val fallenLabel = buildThreadFallenStatusLabel(currentState, isOfflineCopy)
    // A fallen thread has no "〜頃消えます" any more: the stale expiry is replaced by 落ち (may 1449174385).
    val expiresLabel = if (fallenLabel != null) null else currentPage?.expiresAtLabel?.takeIf { it.isNotBlank() }
    val truncationLabel = currentPage?.takeIf { it.isTruncated }?.let(::buildThreadTruncationStatusLabel)
    val statusLabel = buildString {
        // 落ち leads, so the compact one-line header (which cuts the end) still shows it.
        fallenLabel?.let { append(it) }
        resolvedReplyCount?.let {
            if (isNotEmpty()) append(" / ")
            append("${it}レス")
        }
        if (!expiresLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" / ")
            append(expiresLabel)
        }
        if (!truncationLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" / ")
            append(truncationLabel)
        }
    }.ifBlank { null }
    val shouldPrepareReadAloudSegments = isReadAloudControlsVisible ||
        shouldPrepareReadAloudForCommand ||
        readAloudStatus is ReadAloudStatus.Speaking ||
        readAloudStatus is ReadAloudStatus.Paused
    return ThreadScreenDerivedUiState(
        successState = successState,
        currentPage = currentPage,
        currentPosts = currentPosts,
        resolvedReplyCount = resolvedReplyCount,
        resolvedThreadTitle = resolvedThreadTitle,
        statusLabel = statusLabel,
        shouldPrepareReadAloudSegments = shouldPrepareReadAloudSegments
    )
}

/**
 * "落ち（過去ログ）" when the page came from an archive, "落ち" when the board answered 404/410
 * (the shown copy, if any, is a local one); null while the thread is not known to have fallen.
 */
internal fun buildThreadFallenStatusLabel(currentState: ThreadUiState, isOfflineCopy: Boolean): String? = when (currentState) {
    is ThreadUiState.Error -> if (currentState.confirmedGone) "落ち" else null
    is ThreadUiState.Success -> when {
        currentState.isArchived && !isOfflineCopy -> "落ち（過去ログ）"
        currentState.isArchived || currentState.confirmedGone -> "落ち"
        else -> null
    }
    ThreadUiState.Loading -> null
}

internal fun buildThreadTruncationStatusLabel(page: ThreadPage): String {
    val reason = when {
        page.truncationReason.isNullOrBlank() -> null
        page.truncationReason.contains("more than", ignoreCase = true) -> "投稿数上限"
        page.truncationReason.contains("maximum post", ignoreCase = true) -> "投稿数上限"
        page.truncationReason.contains("oversized", ignoreCase = true) -> "大きすぎるレスをスキップ"
        page.truncationReason.contains("timeout", ignoreCase = true) -> "解析時間上限"
        page.truncationReason.contains("iteration", ignoreCase = true) -> "解析回数上限"
        page.truncationReason.contains("Reference rebuild", ignoreCase = true) -> "参照解析上限"
        else -> "解析上限"
    }
    return if (reason == null) {
        "一部のみ表示"
    } else {
        "一部のみ表示（$reason）"
    }
}
