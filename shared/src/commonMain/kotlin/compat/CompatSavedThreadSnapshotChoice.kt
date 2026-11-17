package com.valoser.futacha.shared.compat

/**
 * Body chosen when a manually saved thread is opened, and whether the store
 * must be updated to show it.
 */
internal data class CompatSavedThreadSnapshotChoice(
    val snapshot: CompatThreadSnapshot,
    val needsSave: Boolean
)

/**
 * A fresher (or fuller) cached body is retained over the saved copy, but its
 * media URLs point at the board. Once the thread has fallen, those URLs no
 * longer resolve even though the saved copy has the same files on the device
 * (E-4). Keep the cached text and replace each post's remote media with the
 * saved local file of the same post.
 */
internal fun chooseCompatSavedThreadSnapshot(
    cached: CompatThreadSnapshot?,
    saved: CompatThreadSnapshot
): CompatSavedThreadSnapshotChoice {
    val retainCache = cached != null && (
        cached.fetchedAtEpochMillis > saved.fetchedAtEpochMillis ||
            shouldPreferLocalCompatSnapshot(cached, saved)
        )
    if (cached == null || !retainCache) return CompatSavedThreadSnapshotChoice(saved, needsSave = true)
    val merged = overlayCompatSavedLocalMedia(cached, saved)
    if (merged === cached) return CompatSavedThreadSnapshotChoice(cached, needsSave = false)
    // Stores reject a body whose revision is not newer than the stored one.
    return CompatSavedThreadSnapshotChoice(merged.copy(revision = cached.revision + 1L), needsSave = true)
}

internal fun overlayCompatSavedLocalMedia(
    cached: CompatThreadSnapshot,
    saved: CompatThreadSnapshot
): CompatThreadSnapshot {
    val savedPosts = saved.posts.filter { it.mediaKey == null }.associateBy(CompatPostSnapshot::postNo)
    var changed = false
    val posts = cached.posts.map { post ->
        if (post.mediaKey != null) return@map post
        val local = savedPosts[post.postNo] ?: return@map post
        // Only replace media the cached post still has. A cached post without
        // an image reflects a deletion on the board and stays as it is.
        val image = local.imageUrl?.takeIf { post.imageUrl != null && isCompatSavedLocalMediaPath(it) }
        val thumbnail = local.thumbnailUrl?.takeIf { post.thumbnailUrl != null && isCompatSavedLocalMediaPath(it) }
        val updated = post.copy(
            imageUrl = image ?: post.imageUrl,
            thumbnailUrl = thumbnail ?: post.thumbnailUrl
        )
        if (updated == post) post else updated.also { changed = true }
    }
    return if (changed) cached.copy(posts = posts) else cached
}

private fun isCompatSavedLocalMediaPath(url: String): Boolean {
    val normalized = url.trim()
    return normalized.isNotEmpty() &&
        !normalized.startsWith("http://", ignoreCase = true) &&
        !normalized.startsWith("https://", ignoreCase = true)
}
