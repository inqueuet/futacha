package com.valoser.futacha.shared.model

data class ThreadPage(
    val threadId: String,
    val boardTitle: String?,
    val expiresAtLabel: String?,
    val deletedNotice: String?,
    val posts: List<Post>,
    val isTruncated: Boolean = false,
    val truncationReason: String? = null,
    /** All posts can be present while quote processing ran out of time. */
    val quoteReferencesComplete: Boolean = true
)

fun ThreadPage.resolveHistoryReplyCount(previousReplyCount: Int? = null): Int {
    val parsedReplyCount = posts.size
    return if (isTruncated && previousReplyCount != null) {
        maxOf(previousReplyCount, parsedReplyCount)
    } else {
        parsedReplyCount
    }
}
