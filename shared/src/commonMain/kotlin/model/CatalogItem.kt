package com.valoser.futacha.shared.model

data class CatalogItem(
    val id: String,
    val threadUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val fullImageUrl: String?,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val replyCount: Int,
    val expiresAtEpochMillis: Long? = null,
    /** The opening post's sequence number on あいもげ (the id is the thread UUID there); null for Futaba. */
    val siteOrder: Long? = null
)
