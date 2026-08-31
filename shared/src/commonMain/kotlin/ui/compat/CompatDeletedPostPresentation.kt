package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot

internal const val COMPAT_ISOLATED_POST_NOTICE = "削除依頼によって隔離されました"
internal const val COMPAT_ADMIN_DELETED_POST_NOTICE = "スレッドを立てた人によって削除されました"

private val compatDeletedResponseCountNoticeRegex = Regex(
    """削除された記事が\s*\d+\s*件あります"""
)

/**
 * The reference thread UI does not render Futaba's aggregate deleted-response
 * counter above the OP. Keep the raw notice in the snapshot because it still
 * participates in thread status detection, but suppress only this presentation
 * row; other thread-wide notices remain available to the UI.
 */
internal fun compatThreadNoticeForDisplay(notice: String?): String? = notice
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.takeUnless(compatDeletedResponseCountNoticeRegex::containsMatchIn)

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
        val notice = when {
            post.isIsolated -> COMPAT_ISOLATED_POST_NOTICE
            post.isDeleted -> COMPAT_ADMIN_DELETED_POST_NOTICE
            else -> null
        }
        if (notice == null) post else post.copy(
            messageHtml = notice,
            imageUrl = null,
            thumbnailUrl = null,
            mediaKey = null,
            isContentRedacted = true
        )
    }
}
