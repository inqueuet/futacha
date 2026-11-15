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
    val thumbnailHeight: Int? = null,
    /** Original file size advertised by the thumbnail tag (`alt="12345 B"`), when available. */
    val imageFileSizeBytes: Long? = null,
    /** Distinguishes current unresolved quotes from legacy caches without parsed mappings. */
    val quoteReferencesParsed: Boolean = false,
    /** Every file of the post (あいもげ: up to 3, the first equals [imageUrl]). Empty for Futaba. */
    val attachments: List<PostAttachment> = emptyList(),
    /** The あいもげ post UUID (where a reply or a vote is sent); null for Futaba. */
    val siteUuid: String? = null,
    /** The number shown in the header for あいもげ (its `seq`, the OP 0, like the other modes' ordinal); null for Futaba. */
    val displayNumber: Int? = null
)

@Serializable
data class QuoteReference(
    val text: String,
    val targetPostIds: List<String>,
    /** Nested explicit links remain tappable, but are not direct replies. */
    val isDirectReply: Boolean = true,
    /** A copied anchor can link to an ancestor while replying to its immediate source. */
    val replyTargetPostIds: List<String> = if (isDirectReply) targetPostIds else emptyList()
)
