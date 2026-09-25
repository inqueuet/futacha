package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.PostDeletionKind
import com.valoser.futacha.shared.model.postDeletionKind
import com.valoser.futacha.shared.model.postDeletionNoticeRanges
import com.valoser.futacha.shared.model.threadDeletionSummary
import com.valoser.futacha.shared.model.threadNoticeWithoutDeletionCount
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

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
    snapshot.posts.drop(1).mapNotNull(::compatPostDeletionKind)
)

/**
 * Same result as [postDeletionKind], but the body is converted to plain text
 * only for a deleted, non-isolated row. [postDeletionKind] ignores the text for
 * every other row, and the HTML conversion runs several regular expressions.
 */
internal fun compatPostDeletionKind(post: CompatPostSnapshot): PostDeletionKind? = when {
    post.isIsolated -> PostDeletionKind.ISOLATED
    !post.isDeleted -> null
    else -> postDeletionKind(post.messageHtml.toCompatPlainText(), isDeleted = true, isIsolated = false)
}

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
    // Keep the input instance when nothing is redacted so that callers keyed on
    // the list (and Compose skipping) see an unchanged value.
    if (posts.none { it.isDeleted || it.isIsolated }) return posts
    return posts.map { post ->
        val notice = compatPostDeletionKind(post)?.notice
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
 * [presentCompatPostsForDeletedVisibility] for effects: a list that needs no
 * redaction returns without a dispatcher hop, and a large list is redacted on
 * the parsing dispatcher instead of the Compose main thread.
 */
internal suspend fun presentCompatPostsForDeletedVisibilityOffMain(
    posts: List<CompatPostSnapshot>,
    showDeletedContent: Boolean
): List<CompatPostSnapshot> {
    if (showDeletedContent || posts.none { it.isDeleted || it.isIsolated }) return posts
    if (posts.size <= COMPAT_MAIN_THREAD_ANALYSIS_POST_LIMIT) {
        return presentCompatPostsForDeletedVisibility(posts, showDeletedContent)
    }
    return withContext(AppDispatchers.parsing) {
        presentCompatPostsForDeletedVisibility(posts, showDeletedContent)
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
