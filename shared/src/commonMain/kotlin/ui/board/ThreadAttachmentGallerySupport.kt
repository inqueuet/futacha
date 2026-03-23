package com.valoser.futacha.shared.ui.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.PlayArrow
import coil3.Bitmap
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.valoser.futacha.shared.model.Post

internal data class ThreadAttachmentGalleryItem(
    val post: Post,
    val previewUrl: String?,
    val targetUrl: String,
    val mediaType: MediaType,
    val fileName: String,
    val badge: AttachmentGalleryBadge?
)

internal data class AttachmentGalleryBadge(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

internal fun buildThreadAttachmentGalleryItems(posts: List<Post>): List<ThreadAttachmentGalleryItem> {
    return posts.mapNotNull(::buildThreadAttachmentGalleryItem)
}

internal fun buildThreadAttachmentPreviewRequest(
    platformContext: PlatformContext,
    previewUrl: String?
): ImageRequest? {
    val mediaInfo = parseMediaUrlInfo(previewUrl) ?: return null
    return ImageRequest.Builder(platformContext)
        .data(previewUrl)
        .crossfade(true)
        .apply {
            if (mediaInfo.isGif) {
                transformations(StaticGifThumbnailTransformation)
            }
        }
        .build()
}

private fun buildThreadAttachmentGalleryItem(post: Post): ThreadAttachmentGalleryItem? {
    val entry = buildMediaPreviewEntry(post) ?: return null
    val previewUrl = resolvePostDisplayMediaUrl(post)
    val targetInfo = parseMediaUrlInfo(entry.url) ?: return null
    val badge = when {
        entry.mediaType == MediaType.Video -> AttachmentGalleryBadge(
            label = "動画",
            icon = Icons.Rounded.PlayArrow
        )
        targetInfo.isGif -> AttachmentGalleryBadge(
            label = "GIF",
            icon = Icons.Outlined.Image
        )
        else -> null
    }
    return ThreadAttachmentGalleryItem(
        post = post,
        previewUrl = previewUrl,
        targetUrl = entry.url,
        mediaType = entry.mediaType,
        fileName = targetInfo.fileName ?: "No.${post.id}",
        badge = badge
    )
}

private object StaticGifThumbnailTransformation : Transformation() {
    override val cacheKey: String = "static-gif-thumbnail"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = input
}
