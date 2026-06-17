package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private const val THREAD_MEDIA_PREVIEW_CANCELLATION_CHECK_INTERVAL = 32

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
    val title: String,
    val messageHtml: String = ""
)

internal data class MediaPreviewKey(
    val url: String,
    val mediaType: MediaType
)

internal data class MediaPreviewCollection(
    val entries: List<MediaPreviewEntry>,
    val indexByKey: Map<MediaPreviewKey, Int>
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
        title = buildMediaPreviewFallbackTitle(post),
        messageHtml = post.messageHtml
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

internal suspend fun buildMediaPreviewEntries(posts: List<Post>): List<MediaPreviewEntry> {
    return buildMediaPreviewCollection(posts).entries
}

internal suspend fun buildMediaPreviewCollection(posts: List<Post>): MediaPreviewCollection {
    val entries = ArrayList<MediaPreviewEntry>()
    posts.forEachIndexed { index, post ->
        if (index % THREAD_MEDIA_PREVIEW_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        buildMediaPreviewEntry(post)?.let(entries::add)
    }
    return MediaPreviewCollection(
        entries = entries,
        indexByKey = buildMediaPreviewIndexByKey(entries)
    )
}

internal fun buildMediaPreviewIndexByKey(entries: List<MediaPreviewEntry>): Map<MediaPreviewKey, Int> {
    if (entries.isEmpty()) return emptyMap()
    val indexByKey = LinkedHashMap<MediaPreviewKey, Int>()
    entries.forEachIndexed { index, entry ->
        val key = MediaPreviewKey(url = entry.url, mediaType = entry.mediaType)
        if (key !in indexByKey) {
            indexByKey[key] = index
        }
    }
    return indexByKey
}

internal fun resolveMediaPreviewDisplayTitle(entry: MediaPreviewEntry): String {
    val firstLine = messageHtmlToLines(entry.messageHtml).firstOrNull()?.trim()
    if (!firstLine.isNullOrBlank()) return firstLine
    return entry.title
}

private fun buildMediaPreviewFallbackTitle(post: Post): String {
    val subject = post.subject?.trim()
    if (!subject.isNullOrBlank()) return subject
    return "No.${post.id}"
}
