package com.valoser.futacha.shared.watch

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.replaceHtmlBreakTags
import com.valoser.futacha.shared.util.stripHtmlTagsLinear
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DEFAULT_MAX_THREADS = 20
private const val DEFAULT_MAX_BOARDS = 80
private const val DEFAULT_MAX_WATCH_WORDS = 50
private const val DEFAULT_MAX_WATCH_WORD_LENGTH = 40
private const val DEFAULT_MAX_PREVIEW_POSTS = 5
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
                val previousReplyCount = previousReplyCounts[key]
                val newReplyCount = previousReplyCount
                    ?.let { (entry.replyCount - it).coerceAtLeast(0) }
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
                    replyCount = entry.replyCount,
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
            unreadTotal = threads.sumOf { it.newReplyCount },
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
    val normalized = stripHtmlTagsLinear(
        replaceHtmlBreakTags(this, lineBreakReplacement = " ", paragraphReplacement = " ")
    )
        .decodeWatchHtmlEntities()
        .replace(WATCH_WHITESPACE_REGEX, " ")
        .trim()
    if (normalized.length <= maxLength) return normalized
    return normalized.take(maxLength).trimEnd() + "..."
}

private fun normalizeWatchWords(
    watchWords: List<String>,
    maxWords: Int,
    maxWordLength: Int
): List<String> {
    if (maxWords <= 0 || maxWordLength <= 0) return emptyList()
    return watchWords
        .mapNotNull { word ->
            word.trim()
                .take(maxWordLength)
                .takeIf { it.isNotBlank() }
                ?.lowercase()
        }
        .distinct()
        .take(maxWords)
}

private fun String.matchesAnyWatchWord(normalizedWatchWords: List<String>): Boolean {
    if (normalizedWatchWords.isEmpty()) return false
    val target = lowercase()
    return normalizedWatchWords.any { target.contains(it) }
}

private fun WatchReadAloudStatus.matches(key: WatchThreadKey): Boolean {
    return boardId == key.boardId &&
        boardUrl == key.boardUrl &&
        threadId == key.threadId
}

private fun WatchReadAloudStatus.isFreshAt(nowMillis: Long): Boolean {
    return updatedAtMillis > 0 &&
        nowMillis - updatedAtMillis <= WATCH_READ_ALOUD_STATUS_MAX_AGE_MILLIS
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
