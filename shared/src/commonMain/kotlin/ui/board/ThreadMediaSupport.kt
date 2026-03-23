package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

internal enum class MediaType {
    Image,
    Video
}

internal fun isRemoteMediaUrl(url: String): Boolean {
    val normalized = url.trim()
    return normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("http://", ignoreCase = true)
}

internal fun determineMediaType(url: String): MediaType {
    return parseMediaUrlInfo(url)?.mediaType ?: MediaType.Image
}

internal data class MediaPreviewEntry(
    val url: String,
    val mediaType: MediaType,
    val postId: String,
    val title: String
)

internal data class ThreadAttachmentActionTarget(
    val post: Post,
    val url: String,
    val mediaType: MediaType,
    val canJumpToPost: Boolean
)

internal fun resolvePostDisplayMediaUrl(post: Post): String? {
    return post.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: post.imageUrl?.takeIf { it.isNotBlank() }
}

internal fun resolvePostTargetMediaUrl(
    post: Post,
    preferredUrl: String? = null
): String? {
    return preferredUrl?.takeIf { it.isNotBlank() }
        ?: post.imageUrl?.takeIf { it.isNotBlank() }
        ?: post.thumbnailUrl?.takeIf { it.isNotBlank() }
}

internal fun resolvePostTargetMediaType(
    post: Post,
    preferredUrl: String? = null,
    preferredMediaType: MediaType? = null
): MediaType {
    return preferredMediaType
        ?: resolvePostTargetMediaUrl(post, preferredUrl)
            ?.let(::determineMediaType)
        ?: MediaType.Image
}

internal fun buildMediaPreviewEntry(
    post: Post,
    preferredUrl: String? = null,
    preferredMediaType: MediaType? = null
): MediaPreviewEntry? {
    val targetUrl = resolvePostTargetMediaUrl(
        post = post,
        preferredUrl = preferredUrl
    )
    if (targetUrl.isNullOrBlank()) return null
    val resolvedMediaType = resolvePostTargetMediaType(
        post = post,
        preferredUrl = targetUrl,
        preferredMediaType = preferredMediaType
    )
    return MediaPreviewEntry(
        url = targetUrl,
        mediaType = resolvedMediaType,
        postId = post.id,
        title = extractPreviewTitle(post)
    )
}

internal fun buildThreadAttachmentActionTarget(
    post: Post,
    preferredUrl: String? = null,
    preferredMediaType: MediaType? = null,
    canJumpToPost: Boolean = false
): ThreadAttachmentActionTarget? {
    val entry = buildMediaPreviewEntry(
        post = post,
        preferredUrl = preferredUrl,
        preferredMediaType = preferredMediaType
    ) ?: return null
    return ThreadAttachmentActionTarget(
        post = post,
        url = entry.url,
        mediaType = entry.mediaType,
        canJumpToPost = canJumpToPost
    )
}

internal fun buildMediaPreviewEntries(posts: List<Post>): List<MediaPreviewEntry> {
    return posts.mapNotNull { post ->
        buildMediaPreviewEntry(post)
    }
}

private fun extractPreviewTitle(post: Post): String {
    val firstLine = messageHtmlToLines(post.messageHtml).firstOrNull()?.trim()
    if (!firstLine.isNullOrBlank()) return firstLine
    val subject = post.subject?.trim()
    if (!subject.isNullOrBlank()) return subject
    return "No.${post.id}"
}
