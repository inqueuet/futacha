package com.valoser.futacha.shared.compat

internal fun compatCatalogCachedAtForSession(
    snapshotRevision: Long,
    fetchedAtEpochMillis: Long,
    freshSnapshotRevision: Long?
): Long? = fetchedAtEpochMillis.takeUnless { snapshotRevision == freshSnapshotRevision }

/**
 * Chooses whether an already persisted device snapshot is safer to display than a
 * newly fetched server/cache response.  The server cache has no trustworthy
 * client-visible generation timestamp, so post coverage is the conservative signal.
 */
internal fun shouldPreferLocalCompatSnapshot(
    local: CompatThreadSnapshot?,
    fetched: CompatThreadSnapshot,
    /**
     * True for a site whose server leaves removed posts out of the answer (あいもげ): a longer device copy then
     * only means posts were removed, so it must not win and bring them back.
     */
    serverDropsRemovedPosts: Boolean = false
): Boolean {
    if (local == null || local.posts.isEmpty()) return false
    if (serverDropsRemovedPosts) return fetched.posts.isEmpty()
    // A refresh that parsed no posts is not a valid replacement for a usable
    // device copy.  This can happen when a live request briefly returns an
    // empty body while the board is updating; committing it makes the thread
    // appear to disappear until the next successful refresh.
    if (fetched.posts.isEmpty()) return true

    val localIds = local.posts.map(CompatPostSnapshot::postNo)
    val fetchedIds = fetched.posts.map(CompatPostSnapshot::postNo)
    val fetchedIsLocalPrefix = localIds.size >= fetchedIds.size &&
        localIds.take(fetchedIds.size) == fetchedIds
    if (!fetchedIsLocalPrefix) return false

    // A complete device copy wins over an explicitly truncated server response,
    // including the equal-count case where the server stopped at the same post.
    if (!local.isTruncated && fetched.isTruncated) return true
    if (local.isTruncated && !fetched.isTruncated) return false

    // If both sides have the same parser state, retain the device copy only when
    // it demonstrably contains additional replies. Equal-sized complete responses
    // use the newly fetched response because it may contain edits to existing posts.
    return local.posts.size > fetched.posts.size
}

/**
 * Applies the local result of a successful 本人削除 request immediately.
 *
 * The legacy client replaces the affected response from the board's JSON
 * endpoint after submitting the request.  Keeping the same visible state
 * locally first avoids leaving the old image/text on screen while that
 * follow-up request is in flight (and also gives image-only deletion its
 * expected behaviour on a slow board).
 */
internal fun applyCompatOwnDeletion(
    snapshot: CompatThreadSnapshot,
    postNo: String,
    imageOnly: Boolean,
    revision: Long = snapshot.revision
): CompatThreadSnapshot? {
    if (snapshot.posts.none { it.postNo == postNo }) return null
    return snapshot.copy(
        revision = revision,
        fetchedAtEpochMillis = revision,
        posts = snapshot.posts.map { post ->
            if (post.postNo != postNo) {
                post
            } else if (imageOnly) {
                post.copy(imageUrl = null, thumbnailUrl = null)
            } else {
                post.copy(
                    messageHtml = com.valoser.futacha.shared.model.PostDeletionKind.AUTHOR.notice,
                    imageUrl = null,
                    thumbnailUrl = null,
                    isDeleted = true
                )
            }
        }
    )
}

/**
 * may 1447437384: a post seen before a refresh keeps the body and the picture it had when the refresh comes back with
 * it deleted or its picture taken away. The post stays marked deleted (the deleted display setting still applies);
 * only what was already on this device is put back. Posts missing from the answer are not brought back, and a site
 * whose server leaves removed posts out (あいもげ) is not touched.
 */
internal fun keepSeenRemovedCompatContent(
    previous: CompatThreadSnapshot?,
    fetched: CompatThreadSnapshot,
    serverDropsRemovedPosts: Boolean = false
): CompatThreadSnapshot {
    if (serverDropsRemovedPosts || previous == null || previous.posts.isEmpty()) return fetched
    val seen = previous.posts.associateBy { it.postNo }
    var changed = false
    val posts = fetched.posts.map { post ->
        val old = seen[post.postNo] ?: return@map post
        if (old.isContentRedacted) return@map post
        val lostImage = post.imageUrl == null && old.imageUrl != null
        val lostBody = post.isDeleted && post.hasOnlyDeletionNotice() &&
            !old.hasOnlyDeletionNotice()
        if (!lostImage && !lostBody) return@map post
        changed = true
        post.copy(
            messageHtml = if (lostBody) old.messageHtml else post.messageHtml,
            imageUrl = if (lostImage) old.imageUrl else post.imageUrl,
            thumbnailUrl = if (lostImage) old.thumbnailUrl else post.thumbnailUrl,
            thumbnailWidth = if (lostImage) old.thumbnailWidth else post.thumbnailWidth,
            thumbnailHeight = if (lostImage) old.thumbnailHeight else post.thumbnailHeight,
            imageFileSizeBytes = if (lostImage) old.imageFileSizeBytes else post.imageFileSizeBytes
        )
    }
    return if (changed) fetched.copy(posts = posts) else fetched
}

private fun CompatPostSnapshot.hasOnlyDeletionNotice(): Boolean {
    val text = messageHtml.toCompatPlainText().trim()
    return text.isBlank() || com.valoser.futacha.shared.model.PostDeletionKind.entries.any { it.notice == text }
}
