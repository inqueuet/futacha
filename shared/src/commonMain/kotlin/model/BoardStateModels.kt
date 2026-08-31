package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class BoardSummary(
    val id: String,
    val name: String,
    val category: String,
    val url: String,
    val description: String,
    val pinned: Boolean = false
)

@Serializable
data class ThreadHistoryEntry(
    val threadId: String,
    val boardId: String = "",
    val title: String,
    val titleImageUrl: String,
    val boardName: String,
    val boardUrl: String,
    val lastVisitedEpochMillis: Long,
    val replyCount: Int,
    val lastReadItemIndex: Int = 0,
    val lastReadItemOffset: Int = 0,
    /** Stable rendered post identity used when headers/filters change list indexes. */
    val lastReadPostId: String? = null,
    val hasAutoSave: Boolean = false,  // FIX: 自動保存の有無を追跡
    val isAutoRefreshDisabled: Boolean = false,
    val lastConfirmedAliveEpochMillis: Long? = null,
    val hasSelfPost: Boolean = false,
    val lastSelfPostEpochMillis: Long? = null
)
