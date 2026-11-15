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
    val quoteReferencesComplete: Boolean = true,
    /** あいもげ: when the thread falls (epoch millis), null for Futaba (it uses [expiresAtLabel]). */
    val expiresAtEpochMillis: Long? = null,
    /** あいもげ: when the thread was closed (epoch millis). */
    val closedAtEpochMillis: Long? = null,
    /** あいもげ: the thread never falls. */
    val isPermanent: Boolean = false,
    /** あいもげ: read from the archive. */
    val archived: Boolean = false,
    /** あいもげ: replies may attach images; null when the site did not say (Futaba). */
    val allowImageReplies: Boolean? = null,
    /** あいもげ: the thread's own NG words. */
    val threadNgWords: List<String> = emptyList(),
    /** あいもげ: the largest post sequence number read, for `/state?after`. */
    val maxSeq: Long? = null
)

/**
 * The posts of the thread counting the OP: the posts read for Futaba, and for あいもげ `maxSeq + 1` (removed posts the
 * server skips still count, so the number matches the background count and never falls when a post is deleted).
 */
fun ThreadPage.sitePostCount(): Int = maxSeq?.let { maxOf(posts.size, (it + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) } ?: posts.size

fun ThreadPage.resolveHistoryReplyCount(previousReplyCount: Int? = null): Int {
    val parsedReplyCount = sitePostCount()
    return if ((isTruncated || maxSeq != null) && previousReplyCount != null) {
        maxOf(previousReplyCount, parsedReplyCount)
    } else {
        parsedReplyCount
    }
}
