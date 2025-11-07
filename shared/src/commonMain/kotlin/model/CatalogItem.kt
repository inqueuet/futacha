package com.valoser.futacha.shared.model

data class CatalogItem(
    val id: String,
    val threadUrl: String,
    val thumbnailUrl: String?,
    val replyCount: Int
)
