package com.valoser.futacha.shared.watch

import kotlinx.serialization.Serializable

@Serializable
data class WatchSnapshot(
    val generatedAtMillis: Long,
    val boards: List<WatchBoard>,
    val threads: List<WatchThreadSummary>,
    val watchWords: List<String>,
    val unreadTotal: Int,
    val watchMatchTotal: Int
)

@Serializable
data class WatchBoard(
    val id: String,
    val name: String,
    val category: String,
    val url: String,
    val pinned: Boolean
)

@Serializable
data class WatchThreadSummary(
    val threadId: String,
    val boardId: String,
    val boardName: String,
    val boardUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val previousReplyCount: Int?,
    val newReplyCount: Int,
    val lastVisitedEpochMillis: Long,
    val isWatchWordMatch: Boolean,
    val previewPosts: List<WatchPostPreview>,
    val readAloudStatus: WatchReadAloudStatus? = null
)

@Serializable
data class WatchPostPreview(
    val postId: String,
    val text: String,
    val postedAtText: String?
)

@Serializable
data class WatchThreadKey(
    val boardId: String,
    val boardUrl: String,
    val threadId: String
)

@Serializable
data class WatchReadAloudStatus(
    val boardId: String,
    val boardUrl: String,
    val threadId: String,
    val state: WatchReadAloudPlaybackState,
    val postId: String?,
    val currentIndex: Int,
    val totalPosts: Int,
    val updatedAtMillis: Long
)

@Serializable
data class WatchReadAloudStatusUpdate(
    val status: WatchReadAloudStatus?,
    val updatedAtMillis: Long
)

@Serializable
enum class WatchReadAloudPlaybackState {
    Speaking,
    Paused
}

@Serializable
data class WatchCommand(
    val type: WatchCommandType,
    val boardId: String? = null,
    val boardUrl: String? = null,
    val threadId: String? = null,
    val commandId: String? = null
)

@Serializable
enum class WatchCommandType {
    OpenThreadOnPhone,
    Refresh,
    SelectBoard,
    StartReadAloudOnPhone,
    PauseReadAloudOnPhone,
    StopReadAloudOnPhone,
    NextReadAloudOnPhone,
    PreviousReadAloudOnPhone
}
