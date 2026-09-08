package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.postDeletionKind
import com.valoser.futacha.shared.model.postDeletionNoticeRanges
import com.valoser.futacha.shared.model.threadDeletionSummary
import com.valoser.futacha.shared.model.threadNoticeWithoutDeletionCount

internal const val COMPAT_ISOLATED_POST_NOTICE = "削除依頼によって隔離されました"
internal const val COMPAT_ADMIN_DELETED_POST_NOTICE = "スレッドを立てた人によって削除されました"

internal data class CompatDeletedNoticeRange(
    val start: Int,
    val endExclusive: Int
)

/** The aggregate belongs below the OP; other thread notices keep their own row. */
internal fun compatThreadNoticeForDisplay(notice: String?): String? = threadNoticeWithoutDeletionCount(notice)

internal fun compatThreadDeletionSummary(snapshot: CompatThreadSnapshot): String? = threadDeletionSummary(
    snapshot.deletedNotice,
    snapshot.posts.drop(1).mapNotNull { post ->
        postDeletionKind(post.messageHtml.toCompatPlainText(), post.isDeleted, post.isIsolated)
    }
)

/**
 * The reference keeps deleted/isolation rows in their original position.
 * With `threadAdminDeleteShow` off it hides the media and replaces the body,
 * rather than dropping the complete response from the thread.
 */
internal fun presentCompatPostsForDeletedVisibility(
    posts: List<CompatPostSnapshot>,
    showDeletedContent: Boolean
): List<CompatPostSnapshot> {
    if (showDeletedContent) return posts
    return posts.map { post ->
        val notice = postDeletionKind(
            post.messageHtml.toCompatPlainText(), post.isDeleted, post.isIsolated
        )?.notice
        if (notice == null) post else post.copy(
            messageHtml = notice,
            imageUrl = null,
            thumbnailUrl = null,
            mediaKey = null,
            isContentRedacted = true
        )
    }
}

/**
 * A redacted row consists only of the synthetic deletion notice and therefore
 * keeps the reference client's alert colour. When deleted content is visible,
 * however, the original body must use the normal theme text colour; only the
 * server-provided deletion notice remains red.
 */
internal fun compatPostBodyUsesAlertColor(post: CompatPostSnapshot): Boolean =
    post.isContentRedacted

internal fun compatDeletedNoticeRanges(
    post: CompatPostSnapshot,
    plainMessage: String
): List<CompatDeletedNoticeRange> {
    if (post.isContentRedacted || (!post.isDeleted && !post.isIsolated)) return emptyList()
    return postDeletionNoticeRanges(plainMessage).map { range ->
        CompatDeletedNoticeRange(range.first, range.last + 1)
    }
}
