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
    readAloudStatus: ReadAloudStatus
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
    val statusLabel = buildString {
        resolvedReplyCount?.let { append("${it}レス") }
        if (!expiresLabel.isNullOrBlank()) {
            if (isNotEmpty()) append(" / ")
            append(expiresLabel)
        }
    }.ifBlank { null }
    val shouldPrepareReadAloudSegments = isReadAloudControlsVisible ||
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
