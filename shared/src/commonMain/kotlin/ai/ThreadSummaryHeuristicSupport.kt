package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post

private const val SUMMARY_MAX_SOURCE_POSTS = 80
private const val SUMMARY_MAX_SOURCE_CHARS = 10_000
private const val SUMMARY_MAX_BULLETS = 4
private const val SUMMARY_MAX_HEADLINE_CHARS = 120
private const val SUMMARY_MAX_BULLET_CHARS = 300
private const val SUMMARY_MAX_OUTPUT_CHARS = 1_000

private val htmlBreakRegex = Regex("(?i)<br\\s*/?>|</p>")
private val htmlTagRegex = Regex("<[^>]+>")
private val hexEntityRegex = Regex("&#x([0-9a-fA-F]+);")
private val numEntityRegex = Regex("&#(\\d+);")
private val whitespaceRegex = Regex("\\s+")
private val quoteLineRegex = Regex("^>+")
private val summaryLineMarkerRegex = Regex("""^(\d+[.)、]\s*|[-・*•●]\s*)""")
private val urlRegex = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)

fun buildExtractiveThreadSummary(
    input: ThreadSummaryInput,
    providerLabel: String
): ThreadSummary {
    val lines = input.posts
        .asSequence()
        .take(SUMMARY_MAX_SOURCE_POSTS)
        .flatMap { post -> post.toSummaryLines().asSequence() }
        .map { it.trim() }
        .filter { it.length >= 8 && !quoteLineRegex.containsMatchIn(it) }
        .distinctBy { it.lowercase() }
        .take(SUMMARY_MAX_BULLETS)
        .toList()

    val headline = input.title
        ?.takeIf { it.isNotBlank() }
        ?: lines.firstOrNull()
        ?: "このスレの要約"

    val bullets = if (lines.isNotEmpty()) {
        lines
    } else {
        listOf("本文が短いため、要約できる内容がまだありません。")
    }

    return buildLimitedThreadSummary(
        headline = headline,
        bullets = bullets,
        providerLabel = providerLabel
    )
}

fun buildThreadSummarySourceText(input: ThreadSummaryInput): String {
    return input.posts
        .asSequence()
        .take(SUMMARY_MAX_SOURCE_POSTS)
        .flatMap { post -> post.toSummaryBodyLines().asSequence() }
        .map { it.trim() }
        .filter { it.length >= 4 }
        .distinctBy { it.lowercase() }
        .joinToString(separator = "\n")
        .take(SUMMARY_MAX_SOURCE_CHARS)
        .trim()
}

fun parseGeneratedThreadSummary(
    input: ThreadSummaryInput,
    generatedText: String,
    providerLabel: String,
    generatedTextHasHeadline: Boolean
): ThreadSummary {
    val lines = generatedText
        .lines()
        .map { it.trim().replace(summaryLineMarkerRegex, "").trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    if (lines.isEmpty()) {
        return buildExtractiveThreadSummary(input, providerLabel = providerLabel)
    }

    val generatedHeadline = lines.firstOrNull()
    val fallbackHeadline = input.title?.takeIf { it.isNotBlank() }
        ?: "このスレの要約"
    val headline = if (generatedTextHasHeadline) {
        generatedHeadline ?: fallbackHeadline
    } else {
        fallbackHeadline
    }
    val bullets = if (generatedTextHasHeadline) {
        lines.drop(1)
    } else {
        lines
    }
        .take(SUMMARY_MAX_BULLETS)
        .ifEmpty { buildExtractiveThreadSummary(input, providerLabel = providerLabel).bullets }

    return buildLimitedThreadSummary(
        headline = headline,
        bullets = bullets,
        providerLabel = providerLabel
    )
}

private fun buildLimitedThreadSummary(
    headline: String,
    bullets: List<String>,
    providerLabel: String
): ThreadSummary {
    val limitedHeadline = headline.limitSummaryText(SUMMARY_MAX_HEADLINE_CHARS)
    var remainingChars = (SUMMARY_MAX_OUTPUT_CHARS - limitedHeadline.length).coerceAtLeast(0)
    val limitedBullets = bullets
        .mapNotNull { bullet ->
            if (remainingChars <= 0) return@mapNotNull null
            val normalized = bullet.replace(whitespaceRegex, " ").trim()
            if (normalized.isBlank()) return@mapNotNull null
            val limit = minOf(SUMMARY_MAX_BULLET_CHARS, remainingChars)
            val limited = normalized.limitSummaryText(limit)
            remainingChars = (remainingChars - limited.length).coerceAtLeast(0)
            limited
        }
        .ifEmpty { listOf("本文が短いため、要約できる内容がまだありません。") }

    return ThreadSummary(
        headline = limitedHeadline,
        bullets = limitedBullets,
        providerLabel = providerLabel
    )
}

private fun Post.toSummaryLines(): List<String> {
    return toSummaryBodyLines()
        .filter { !quoteLineRegex.containsMatchIn(it) }
}

private fun Post.toSummaryBodyLines(): List<String> {
    return messageHtml
        .replace(htmlBreakRegex, "\n")
        .replace(htmlTagRegex, "")
        .decodeBasicHtmlEntities()
        .split('\n')
        .map { line ->
            line
                .replace(urlRegex, " ")
                .replace(whitespaceRegex, " ")
                .trim()
        }
        .filter { it.isNotBlank() }
}

private fun String.limitSummaryText(maxChars: Int): String {
    val normalized = replace(whitespaceRegex, " ").trim()
    if (maxChars <= 0) return ""
    return if (normalized.length <= maxChars) {
        normalized
    } else if (maxChars == 1) {
        "…"
    } else {
        normalized.take(maxChars - 1).trimEnd() + "…"
    }
}

internal fun String.decodeBasicHtmlEntities(): String {
    var result = replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&nbsp;", " ")

    result = hexEntityRegex.replace(result) { match ->
        val value = match.groupValues.getOrNull(1)?.toIntOrNull(16)
        if (value != null && value in 0x20..0x10FFFF) {
            codePointToString(value)
        } else {
            match.value
        }
    }
    result = numEntityRegex.replace(result) { match ->
        val value = match.groupValues.getOrNull(1)?.toIntOrNull()
        if (value != null && value in 0x20..0x10FFFF) {
            codePointToString(value)
        } else {
            match.value
        }
    }
    return result
}

private fun codePointToString(codePoint: Int): String {
    return if (codePoint <= 0xFFFF) {
        codePoint.toChar().toString()
    } else {
        val high = ((codePoint - 0x10000) shr 10) + 0xD800
        val low = ((codePoint - 0x10000) and 0x3FF) + 0xDC00
        "${high.toChar()}${low.toChar()}"
    }
}
