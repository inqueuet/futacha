package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.postDeletionKind
import com.valoser.futacha.shared.model.threadDeletionSummary

internal fun threadDeletionSummaryForPage(page: ThreadPage): String? = threadDeletionSummary(
    page.deletedNotice,
    page.posts.drop(1).mapNotNull { post ->
        postDeletionKind(messageHtmlToPlainText(post.messageHtml), post.isDeleted, post.isIsolated)
    }
)
