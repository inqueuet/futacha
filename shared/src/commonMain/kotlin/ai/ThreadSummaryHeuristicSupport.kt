package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post

private const val SUMMARY_MAX_SOURCE_POSTS = 80
private const val SUMMARY_MAX_BULLETS = 4
private const val SUMMARY_MAX_BULLET_CHARS = 72

private val htmlBreakRegex = Regex("(?i)<br\\s*/?>|</p>")
private val htmlTagRegex = Regex("<[^>]+>")
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
        .map { it.limitSummaryLine() }
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

    return ThreadSummary(
        headline = headline.limitSummaryLine(),
        bullets = bullets,
        providerLabel = providerLabel
    )
}

fun buildThreadSummarySourceText(input: ThreadSummaryInput): String {
    return input.posts
        .asSequence()
        .take(SUMMARY_MAX_SOURCE_POSTS)
        .flatMap { post -> post.toSummaryLines().asSequence() }
        .map { it.trim() }
        .filter { it.length >= 4 }
        .distinctBy { it.lowercase() }
        .joinToString(separator = "\n")
        .take(12_000)
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
        ?: generatedHeadline
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

    return ThreadSummary(
        headline = headline.limitSummaryLine(),
        bullets = bullets.map { it.limitSummaryLine() },
        providerLabel = providerLabel
    )
}

private fun Post.toSummaryLines(): List<String> {
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

private fun String.limitSummaryLine(): String {
    val normalized = replace(whitespaceRegex, " ").trim()
    return if (normalized.length <= SUMMARY_MAX_BULLET_CHARS) {
        normalized
    } else {
        normalized.take(SUMMARY_MAX_BULLET_CHARS - 1).trimEnd() + "…"
    }
}

internal fun String.decodeBasicHtmlEntities(): String {
    return replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
