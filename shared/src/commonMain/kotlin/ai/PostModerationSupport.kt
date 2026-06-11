package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post

private const val POST_MODERATION_MAX_BODY_CHARS = 220
private const val POST_MODERATION_MAX_REASON_CHARS = 80
private const val POST_MODERATION_MAX_SOURCE_CHARS = 12_000

private val moderationHtmlBreakRegex = Regex("(?i)<br\\s*/?>|</p>")
private val moderationHtmlTagRegex = Regex("<[^>]+>")
private val moderationWhitespaceRegex = Regex("\\s+")
private val moderationQuoteLineRegex = Regex("^>+")
private val moderationUrlRegex = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
private val moderationFallbackLineRegex = Regex("""^\s*(\S+)\s+HIDE(?:\s+(.+))?\s*$""", RegexOption.IGNORE_CASE)

fun buildPostModerationSourceText(input: PostModerationInput): String {
    return buildPostModerationSourceLines(input.posts)
        .joinToString(separator = "\n")
        .trim()
}

internal fun buildPostModerationSourceChunks(
    input: PostModerationInput,
    maxChunkChars: Int = POST_MODERATION_MAX_SOURCE_CHARS
): List<String> {
    val limit = maxChunkChars.coerceAtLeast(1)
    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    buildPostModerationSourceLines(input.posts).forEach { line ->
        val extraChars = line.length + if (current.isEmpty()) 0 else 1
        if (current.isNotEmpty() && current.length + extraChars > limit) {
            chunks += current.toString()
            current.clear()
        }
        if (current.isNotEmpty()) {
            current.append('\n')
        }
        current.append(line)
    }
    if (current.isNotEmpty()) {
        chunks += current.toString()
    }
    return chunks
}

private fun buildPostModerationSourceLines(posts: List<Post>): Sequence<String> {
    return posts.asSequence().mapNotNull { post ->
        val body = buildPostModerationBody(post.messageHtml)
        body.takeIf { it.isNotBlank() }?.let { "${post.id}\t$it" }
    }
}

fun parsePostModerationResponse(response: String): Map<String, PostModerationResult> {
    return response
        .lineSequence()
        .mapNotNull { line -> parsePostModerationResponseLine(line) }
        .toMap()
}

private fun buildPostModerationBody(messageHtml: String): String {
    return messageHtml
        .replace(moderationHtmlBreakRegex, "\n")
        .replace(moderationHtmlTagRegex, " ")
        .decodeBasicHtmlEntities()
        .lines()
        .map { line ->
            line
                .replace(moderationUrlRegex, " ")
                .replace(moderationWhitespaceRegex, " ")
                .trim()
        }
        .filter { it.isNotBlank() && !moderationQuoteLineRegex.containsMatchIn(it) }
        .joinToString(separator = " ")
        .replace(moderationWhitespaceRegex, " ")
        .trim()
        .take(POST_MODERATION_MAX_BODY_CHARS)
}

private fun parsePostModerationResponseLine(line: String): Pair<String, PostModerationResult>? {
    val trimmedLine = line.trim()
    if (trimmedLine.isBlank()) return null

    val tabParts = trimmedLine.split('\t')
    val parsed = if (tabParts.size >= 2 && tabParts[1].equals("HIDE", ignoreCase = true)) {
        tabParts[0].trim() to tabParts.drop(2).joinToString(separator = " ").trim()
    } else {
        val match = moderationFallbackLineRegex.matchEntire(trimmedLine) ?: return null
        match.groupValues[1].trim() to match.groupValues.getOrNull(2).orEmpty().trim()
    }

    val postId = parsed.first.takeIf { it.isNotBlank() } ?: return null
    val reason = parsed.second
        .normalizePostModerationReason()
        .ifBlank { "端末AIが荒らしの可能性を検出しました。" }

    return postId to PostModerationResult(
        postId = postId,
        shouldHide = true,
        reason = reason
    )
}

private fun String.normalizePostModerationReason(): String {
    val normalized = replace(moderationWhitespaceRegex, " ").trim()
    return if (normalized.length <= POST_MODERATION_MAX_REASON_CHARS) {
        normalized
    } else {
        normalized.take(POST_MODERATION_MAX_REASON_CHARS - 1).trimEnd() + "…"
    }
}
