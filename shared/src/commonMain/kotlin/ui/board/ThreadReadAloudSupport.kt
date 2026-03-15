package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

internal data class ReadAloudSegment(
    val postIndex: Int,
    val postId: String,
    val body: String
)

internal sealed interface ReadAloudStatus {
    data object Idle : ReadAloudStatus
    data class Speaking(val segment: ReadAloudSegment) : ReadAloudStatus
    data class Paused(val segment: ReadAloudSegment) : ReadAloudStatus
}

private val READ_ALOUD_SKIPPED_PHRASES = listOf(
    "スレッドを立てた人によって削除されました",
    "書き込みをした人によって削除されました",
    "管理者によって削除されました"
)
private val READ_ALOUD_URL_REGEX = Regex("(?i)\\b(?:https?|ftp)://\\S+|\\bttps?://\\S+|\\bttp://\\S+")

internal fun buildReadAloudSegments(posts: List<Post>): List<ReadAloudSegment> {
    return posts.mapIndexedNotNull { index, post ->
        if (post.isDeleted) return@mapIndexedNotNull null
        val lines = messageHtmlToLines(post.messageHtml)
            .map { stripUrlsForReadAloud(it).trim() }
            .filter { it.isNotBlank() && !it.startsWith(">") && !it.startsWith("＞") }
        if (lines.isEmpty()) return@mapIndexedNotNull null
        if (containsDeletionNotice(lines)) return@mapIndexedNotNull null
        val body = lines.joinToString("\n")
        ReadAloudSegment(index, post.id, body)
    }
}

internal fun stripUrlsForReadAloud(value: String): String {
    val withoutUrls = READ_ALOUD_URL_REGEX.replace(value, "")
    return withoutUrls.replace(Regex("\\s{2,}"), " ")
}

internal fun containsDeletionNotice(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    return lines.any { line ->
        READ_ALOUD_SKIPPED_PHRASES.any { phrase ->
            line.contains(phrase)
        }
    }
}

internal fun normalizeReadAloudCurrentIndex(
    currentIndex: Int,
    segmentCount: Int
): Int {
    return currentIndex.coerceIn(0, segmentCount.coerceAtLeast(0))
}

internal fun findFirstVisibleReadAloudSegmentIndex(
    segments: List<ReadAloudSegment>,
    firstVisibleItemIndex: Int
): Int {
    if (segments.isEmpty()) return -1
    var left = 0
    var right = segments.lastIndex
    var result = -1
    while (left <= right) {
        val mid = left + (right - left) / 2
        val postIndex = segments[mid].postIndex
        if (postIndex >= firstVisibleItemIndex) {
            result = mid
            right = mid - 1
        } else {
            left = mid + 1
        }
    }
    return result
}
