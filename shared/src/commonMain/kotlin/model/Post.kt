package com.valoser.futacha.shared.model

import androidx.compose.runtime.Immutable

@Immutable
data class Post(
    val id: String,
    val order: Int? = null,
    val author: String?,
    val subject: String?,
    val timestamp: String,
    val posterId: String? = null,
    val messageHtml: String,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val saidaneLabel: String? = null,
    val isDeleted: Boolean = false,
    val referencedCount: Int = 0,
    val quoteReferences: List<QuoteReference> = emptyList()
)

data class QuoteReference(
    val text: String,
    val targetPostIds: List<String>
)
