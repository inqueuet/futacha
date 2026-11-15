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
    return posts.flatMap(::postsForEachAttachment).mapNotNull(::buildThreadAttachmentGalleryItem)
}

/**
 * The post itself, then one copy per 2nd/3rd あいもげ file whose image is that file, so a tap opens that file.
 * A Futaba post has no [Post.attachments] and stays a single item.
 */
internal fun postsForEachAttachment(post: Post): List<Post> {
    val extras = com.valoser.futacha.shared.service.threadSaveExtraAttachments(post)
    val inline = threadInlineApuCopies(post, extras.map { it.url })
    if (extras.isEmpty() && inline.isEmpty()) return listOf(post)
    return listOf(post) + inline.takeIf { extras.isEmpty() }.orEmpty() + extras.map { attachment ->
        post.copy(
            imageUrl = attachment.url,
            thumbnailUrl = attachment.thumbnailUrl,
            thumbnailWidth = attachment.thumbnailWidth,
            thumbnailHeight = attachment.thumbnailHeight,
            imageFileSizeBytes = attachment.sizeBytes,
            attachments = emptyList()
        )
    }
}

internal fun buildThreadAttachmentPreviewRequest(
    platformContext: PlatformContext,
    previewUrl: String?,
    previewSizePx: Int
): ImageRequest? {
    val mediaInfo = parseMediaUrlInfo(previewUrl) ?: return null
    return ImageRequest.Builder(platformContext)
        .data(previewUrl)
        .crossfade(false)
        .size(previewSizePx, previewSizePx)
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
            label = targetInfo.extension.uppercase().ifBlank { "VIDEO" },
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

/**
 * The 2nd and later あぷ/あぷ小 files written in the body: one copy per file whose image is that file, so the gallery
 * and the viewer can open each of them (aimg 01a1d21b post-1: only the first of several loaded in ふたちゃ mode,
 * because the viewer had a page only for the post's first file and a tap on another one found nothing).
 */
internal fun threadInlineApuCopies(post: Post, alsoShown: List<String> = emptyList()): List<Post> {
    if (post.messageHtml.isBlank()) return emptyList()
    val shown = (listOfNotNull(post.imageUrl, post.thumbnailUrl) + alsoShown)
        .mapNotNull { com.valoser.futacha.shared.ui.compat.compatMediaFileIdentity(it) }.toSet()
    return com.valoser.futacha.shared.ui.compat.compatInlineApuSmallMediaUrls(post.messageHtml)
        .filterNot { com.valoser.futacha.shared.ui.compat.compatMediaFileIdentity(it) in shown }
        .map { url ->
            post.copy(
                imageUrl = url,
                thumbnailUrl = com.valoser.futacha.shared.ui.compat.compatApuSmallThumbnailUrl(url)
                    .takeIf { determineMediaType(url) == MediaType.Video },
                thumbnailWidth = null,
                thumbnailHeight = null,
                imageFileSizeBytes = null,
                attachments = emptyList()
            )
        }
}
