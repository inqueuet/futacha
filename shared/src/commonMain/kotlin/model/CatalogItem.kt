package com.valoser.futacha.shared.model

data class CatalogItem(
    val id: String,
    val threadUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val replyCount: Int,
    val expiresAtEpochMillis: Long? = null
)
