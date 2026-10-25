package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

/** The board address used to play a saved thread whose board is no longer registered; it can never be fetched. */
const val SAVED_THREAD_PLACEHOLDER_HOST = "saved.invalid"

/** Whether [url] is such a placeholder board (or a thread address under it). */
fun isSavedThreadPlaceholderUrl(url: String?): Boolean =
    url?.trim()?.substringAfter("://", "")?.substringBefore('/')?.substringBefore(':')
        ?.equals(SAVED_THREAD_PLACEHOLDER_HOST, ignoreCase = true) == true

/** Shared across modern and compatibility stores so mode changes cannot truncate boards. */
const val MAX_REGISTERED_BOARDS = 20_000

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
    val lastSelfPostEpochMillis: Long? = null,
    /** Null in old histories or until a board response confirms the status. */
    val threadStatus: ThreadStatus? = null,
    /** A foreground read, independent of counts refreshed by background/catalog requests. */
    val lastViewedSnapshot: ThreadViewedSnapshot? = null
)

@Serializable
data class ThreadViewedSnapshot(
    val postCount: Int,
    val lastPostId: String?,
    val viewedAtEpochMillis: Long
)
