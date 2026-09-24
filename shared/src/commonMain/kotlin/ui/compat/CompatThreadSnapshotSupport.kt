package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.model.Post

/** Converts the displayed thread posts for the page saver, keeping only the requested media URLs. */
internal fun compatPostsForSave(
    snapshot: CompatThreadSnapshot?,
    includeFullImages: Boolean,
    includeThumbnails: Boolean
): List<Post> =
    snapshot?.posts.orEmpty().map { post ->
        Post(
            id = post.postNo,
            order = post.position,
            author = post.author,
            subject = post.subject,
            timestamp = post.timestamp,
            posterId = post.posterId,
            messageHtml = post.messageHtml,
            imageUrl = post.imageUrl.takeIf { includeFullImages },
            thumbnailUrl = post.thumbnailUrl.takeIf { includeThumbnails },
            saidaneLabel = post.saidaneLabel,
            isDeleted = post.isDeleted,
            isIsolated = post.isIsolated,
            referencedCount = post.referencedCount,
            mail = post.mail
        )
    }

/** The OP's thumbnail, or its image when the OP has no thumbnail. */
internal fun compatSnapshotThumbnail(snapshot: CompatThreadSnapshot?, threadNo: String): String? = snapshot
    ?.posts
    ?.firstOrNull { it.position == 0 || it.postNo == threadNo }
    ?.let { post -> post.thumbnailUrl ?: post.imageUrl }
