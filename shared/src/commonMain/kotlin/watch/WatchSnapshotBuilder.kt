package com.valoser.futacha.shared.watch

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear
import com.valoser.futacha.shared.util.isWithinEpochInterval
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DEFAULT_MAX_THREADS = WATCH_SNAPSHOT_MAX_THREADS
private const val DEFAULT_MAX_BOARDS = WATCH_SNAPSHOT_MAX_BOARDS
private const val DEFAULT_MAX_WATCH_WORDS = WATCH_SNAPSHOT_MAX_WATCH_WORDS
private const val DEFAULT_MAX_WATCH_WORD_LENGTH = 40
private const val DEFAULT_MAX_PREVIEW_POSTS = WATCH_SNAPSHOT_MAX_PREVIEW_POSTS_PER_THREAD
private const val DEFAULT_MAX_PREVIEW_TEXT_LENGTH = 140

private val WATCH_WHITESPACE_REGEX = Regex("\\s+")

class WatchSnapshotBuilder(
    private val maxThreads: Int = DEFAULT_MAX_THREADS,
    private val maxBoards: Int = DEFAULT_MAX_BOARDS,
    private val maxWatchWords: Int = DEFAULT_MAX_WATCH_WORDS,
    private val maxWatchWordLength: Int = DEFAULT_MAX_WATCH_WORD_LENGTH,
    private val maxPreviewPosts: Int = DEFAULT_MAX_PREVIEW_POSTS,
    private val maxPreviewTextLength: Int = DEFAULT_MAX_PREVIEW_TEXT_LENGTH,
    private val nowMillis: () -> Long = ::currentEpochMillis
) {
    fun build(
        boards: List<BoardSummary>,
        history: List<ThreadHistoryEntry>,
        watchWords: List<String>,
        threadPages: Map<WatchThreadKey, ThreadPage> = emptyMap(),
        previousReplyCounts: Map<WatchThreadKey, Int> = emptyMap(),
        readAloudStatus: WatchReadAloudStatus? = null
    ): WatchSnapshot {
        val normalizedWatchWords = normalizeWatchWords(
            watchWords = watchWords,
            maxWords = maxWatchWords,
            maxWordLength = maxWatchWordLength
        )
        val boardById = boards.associateBy { it.id }
        val watchBoards = boards
            .sortedWith(compareByDescending<BoardSummary> { it.pinned }.thenBy { it.name })
            .take(maxBoards.coerceAtLeast(0))
            .map { board ->
                WatchBoard(
                    id = board.id,
                    name = board.name,
                    category = board.category,
                    url = board.url,
                    pinned = board.pinned
                )
            }
        val threads = history
            .sortedByDescending { it.lastVisitedEpochMillis }
            .take(maxThreads.coerceAtLeast(0))
            .map { entry ->
                val board = boardById[entry.boardId]
                val key = entry.toWatchThreadKey()
                val replyCount = entry.replyCount.coerceAtLeast(0)
                val previousReplyCount = previousReplyCounts[key]?.coerceAtLeast(0)
                val newReplyCount = previousReplyCount
                    ?.let {
                        (replyCount.toLong() - it.toLong())
                            .coerceIn(0L, Int.MAX_VALUE.toLong())
                            .toInt()
                    }
                    ?: 0
                val previewPosts = threadPages[key]
                    ?.posts
                    ?.toWatchPreviewPosts(
                        maxPosts = maxPreviewPosts,
                        maxTextLength = maxPreviewTextLength
                    )
                    .orEmpty()

                WatchThreadSummary(
                    threadId = entry.threadId,
                    boardId = entry.boardId,
                    boardName = board?.name ?: entry.boardName,
                    boardUrl = board?.url ?: entry.boardUrl,
                    title = entry.title,
                    thumbnailUrl = entry.titleImageUrl.takeIf { it.isNotBlank() },
                    replyCount = replyCount,
                    previousReplyCount = previousReplyCount,
                    newReplyCount = newReplyCount,
                    lastVisitedEpochMillis = entry.lastVisitedEpochMillis,
                    isWatchWordMatch = entry.title.matchesAnyWatchWord(normalizedWatchWords),
                    previewPosts = previewPosts,
                    readAloudStatus = readAloudStatus?.takeIf {
                        it.matches(key) && it.isFreshAt(nowMillis())
                    }
                )
            }

        return WatchSnapshot(
            generatedAtMillis = nowMillis(),
            boards = watchBoards,
            threads = threads,
            watchWords = normalizedWatchWords,
            unreadTotal = threads
                .sumOf { it.newReplyCount.toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
            watchMatchTotal = threads.count { it.isWatchWordMatch }
        )
    }
}

fun ThreadHistoryEntry.toWatchThreadKey(): WatchThreadKey = WatchThreadKey(
    boardId = boardId,
    boardUrl = boardUrl,
    threadId = threadId
)

internal fun List<Post>.toWatchPreviewPosts(
    maxPosts: Int,
    maxTextLength: Int
): List<WatchPostPreview> {
    if (maxPosts <= 0) return emptyList()
    val previews = ArrayList<WatchPostPreview>(maxPosts)
    for (post in asReversed()) {
        if (post.isDeleted) continue
        val text = post.messageHtml.toWatchPlainText(maxTextLength)
        if (text.isBlank()) continue
        previews += WatchPostPreview(
            postId = post.id,
            text = text,
            postedAtText = post.timestamp.takeIf { it.isNotBlank() }
        )
        if (previews.size >= maxPosts) {
            break
        }
    }
    return previews.asReversed()
}

internal fun String.toWatchPlainText(maxLength: Int): String {
    if (maxLength <= 0) return ""
    val maxSourceChars = (maxLength.toLong() * 8L)
        .coerceIn(WATCH_PLAIN_TEXT_MIN_SOURCE_CHARS.toLong(), WATCH_PLAIN_TEXT_MAX_SOURCE_CHARS.toLong())
        .toInt()
    val normalized = stripHtmlTagsLinear(
        replaceHtmlBreakTags(
            take(maxSourceChars),
            lineBreakReplacement = " ",
            paragraphReplacement = " "
        )
    )
        .decodeWatchHtmlEntities()
        .replace(WATCH_WHITESPACE_REGEX, " ")
        .trim()
    if (normalized.length <= maxLength) return normalized
    return normalized.take(maxLength).trimEnd() + "..."
}

private const val WATCH_PLAIN_TEXT_MIN_SOURCE_CHARS = 4 * 1024
private const val WATCH_PLAIN_TEXT_MAX_SOURCE_CHARS = 12 * 1024

private fun normalizeWatchWords(
    watchWords: List<String>,
    maxWords: Int,
    maxWordLength: Int
): List<String> {
    if (maxWords <= 0 || maxWordLength <= 0) return emptyList()
    val maxWordSourceChars = (maxWordLength.toLong() * 4L)
        .coerceAtMost(WATCH_PLAIN_TEXT_MAX_SOURCE_CHARS.toLong())
        .toInt()
    return watchWords
        .asSequence()
        .mapNotNull { word ->
            word.take(maxWordSourceChars)
                .trim()
                .take(maxWordLength)
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        }
        .distinct()
        .take(maxWords)
        .toList()
}

private fun String.matchesAnyWatchWord(normalizedWatchWords: List<String>): Boolean {
    if (normalizedWatchWords.isEmpty()) return false
    val target = lowercase()
    return normalizedWatchWords.any { target.contains(it) }
}

internal fun WatchReadAloudStatus.matches(key: WatchThreadKey): Boolean {
    return boardId == key.boardId &&
        boardUrl == key.boardUrl &&
        threadId == key.threadId
}

internal fun WatchReadAloudStatus.isFreshAt(nowMillis: Long): Boolean {
    return updatedAtMillis > 0 &&
        isWithinEpochInterval(nowMillis, updatedAtMillis, WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS)
}

private fun String.decodeWatchHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace("&#12288;", " ")
}

@OptIn(ExperimentalTime::class)
private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
