package com.valoser.futacha.shared.model

import kotlinx.serialization.Serializable

/**
 * One file of a post. An あいもげ post can carry up to 3; a Futaba post keeps its single image in
 * [Post.imageUrl] and has an empty attachment list.
 */
@Serializable
data class PostAttachment(
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val sizeBytes: Long? = null,
    val mimeType: String? = null
)
