package com.valoser.futacha.shared.model

data class CatalogItem(
    val id: String,
    val threadUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val replyCount: Int,
    val expiresAtEpochMillis: Long? = null
)
