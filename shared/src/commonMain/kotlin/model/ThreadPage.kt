package com.valoser.futacha.shared.model

data class ThreadPage(
    val threadId: String,
    val boardTitle: String?,
    val expiresAtLabel: String?,
    val deletedNotice: String?,
    val posts: List<Post>
)
