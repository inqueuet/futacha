package com.valoser.futacha.shared.compat

import kotlinx.serialization.Serializable

/**
 * D-B3: the rows of Android's `compat_tab_view` and `compat_draft`, for the stores that keep their state
 * in one record (iOS, Desktop). Nothing reads or writes them yet; later batches move each mode onto them.
 */
@Serializable
data class CompatSharedTabView(
    val tabKey: String,
    val profile: String,
    val ordinal: Int,
    val addedAtEpochMillis: Long
)

@Serializable
data class CompatSharedDraft(
    /** "reply:<tab key>" or "build:<board key>". */
    val draftKey: String,
    val name: String = "",
    val email: String = "",
    val subject: String = "",
    val comment: String = "",
    val attachmentUri: String? = null,
    val attachmentHandwriting: Boolean = false,
    val attachmentOwner: String? = null,
    val deleteKey: String = "",
    val writerProfile: String = "",
    val updatedAtEpochMillis: Long
)
