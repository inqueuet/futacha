package com.valoser.futacha.shared.model

data class Post(
    val id: String,
    val author: String?,
    val subject: String?,
    val timestamp: String,
    val messageHtml: String,
    val imageUrl: String?,
    val thumbnailUrl: String?
)
