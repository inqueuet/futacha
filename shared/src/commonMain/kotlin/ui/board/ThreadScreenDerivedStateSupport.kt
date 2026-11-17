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
    shouldPrepareReadAloudForCommand: Boolean = false
): ThreadScreenDerivedUiState {
    val successState = currentState as? ThreadUiState.Success
    val currentPage = successState?.page
    val currentPosts = currentPage?.posts.orEmpty()
    val resolvedReplyCount = successState?.page?.posts?.size ?: initialReplyCount
    val resolvedThreadTitle = successState?.page?.posts?.firstOrNull()?.subject
        ?.let { threadTitle ?: it }
        ?: threadTitle
        ?: "スレッド"
    val expiresLabel = currentPage?.expiresAtLabel?.takeIf { it.isNotBlank() }
    val truncationLabel = currentPage?.takeIf { it.isTruncated }?.let(::buildThreadTruncationStatusLabel)
    val statusLabel = buildString {
        resolvedReplyCount?.let { append("${it}レス") }
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
