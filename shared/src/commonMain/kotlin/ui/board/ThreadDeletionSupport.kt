package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.postDeletionKind
import com.valoser.futacha.shared.model.threadDeletionSummary

internal fun threadDeletionSummaryForPage(page: ThreadPage): String? = threadDeletionSummary(
    page.deletedNotice,
    page.posts.drop(1).mapNotNull { post ->
        // The plain text is only read for a deleted, not isolated, post; skip converting every other body.
        if (!post.isDeleted && !post.isIsolated) return@mapNotNull null
        val plainMessage = if (post.isDeleted && !post.isIsolated) messageHtmlToPlainText(post.messageHtml) else ""
        postDeletionKind(plainMessage, post.isDeleted, post.isIsolated)
    }
)
