package com.valoser.futacha.shared.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

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
    val isIsolated: Boolean = false,
    val referencedCount: Int = 0,
    val quoteReferences: List<QuoteReference> = emptyList(),
    /** Raw Futaba mail field (for example `sage`); retained for target-compatible search. */
    val mail: String? = null,
    /** Dimensions advertised by the thread thumbnail tag, when available. */
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null
)

@Serializable
data class QuoteReference(
    val text: String,
    val targetPostIds: List<String>
)
