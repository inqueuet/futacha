package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelHtml
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelText
import com.valoser.futacha.shared.parser.HtmlEntityDecoder

private val compatNumericQuote = Regex(
    "^[>＞]+\\s*(?:No\\s*\\.\\s*)?([0-9]{1,20})(?=\\D|$)",
    RegexOption.IGNORE_CASE
)
private val compatIdentityQuote = Regex(
    "^[>＞]+\\s*(ID|IP)\\s*:\\s*([^\\s<>]+)",
    RegexOption.IGNORE_CASE
)
private val compatMediaFileName = Regex(
    "(?<![A-Za-z0-9._/-])([A-Za-z0-9._-]+\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?![A-Za-z0-9._-])",
    RegexOption.IGNORE_CASE
)
private val compatMediaUrlFileName = Regex(
    "https?://[^\\s<>]+/([A-Za-z0-9._-]+\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?:[?#][^\\s<>]*)?",
    RegexOption.IGNORE_CASE
)

/** Converts the small HTML subset retained by the compatibility snapshot into tappable text. */
fun String.toCompatPlainText(): String = HtmlEntityDecoder.decode(
    normalizeFutabaArchiveApuViewLabelHtml(this)
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
).let(::normalizeFutabaArchiveApuViewLabelText)

fun String.normalizeCompatQuoteText(): String = trim().replace(Regex("\\s+"), " ")

/** 1.apk intentionally does nothing when a tapped quote has no source post. */
fun compatMissingQuoteNotice(): String? = null

/** Encodes a displayed quote line into the resolver query used by recursive quote popups. */
fun compatQuoteQueryForLine(line: String): String? {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith(">") && !trimmed.startsWith("＞")) return null
    compatIdentityQuote.find(trimmed)?.let { match ->
        val kind = match.groupValues[1].lowercase()
        val value = match.groupValues[2]
            .trimEnd('.', ',', '。', '、', '！', '!')
            .takeIf(String::isNotBlank)
            ?: return@let
        return "$kind:$value"
    }
    val text = trimmed.trimStart('>', '＞').normalizeCompatQuoteText()
    compatMediaFileName.find(text)?.groupValues?.getOrNull(1)?.let { return "file:$it" }
    compatMediaUrlFileName.find(text)?.groupValues?.getOrNull(1)?.let { return "file:$it" }
    // Futaba source names are commonly numeric timestamps such as
    // `1786103362453.jpg`. Check media before the numeric No. shorthand, or
    // the filename would be misread as `No.1786103362453` and fail to resolve.
    compatNumericQuote.find(trimmed)?.groupValues?.getOrNull(1)?.let { return "no:$it" }
    return text.takeIf { it.isNotBlank() }?.let { "text:$it" }
}

/**
 * Returns every media filename which is visible or attached to a post.
 *
 * The old viewer stores a dedicated `strFileName`, but a live Futaba page can
 * expose the same value through `/src/`, `/thumb/`, an external uploader link,
 * or just the filename in the response body.  Keeping these forms together
 * makes file-name quotations resolve identically for fresh and cached posts.
 */
fun compatPostMediaFileNames(post: CompatPostSnapshot): List<String> = buildList {
    fun addUrlFileName(url: String?) {
        val fileName = url
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
        if (fileName != null && compatMediaFileName.matches(fileName)) add(fileName)
    }

    addUrlFileName(post.imageUrl)
    addUrlFileName(post.thumbnailUrl)
    val plainMessage = post.messageHtml.toCompatPlainText()
    compatInlineLinks(post.messageHtml)
        .filter { link ->
            val lineStart = plainMessage.lastIndexOf('\n', (link.start - 1).coerceAtLeast(0)) + 1
            val linePrefix = plainMessage.substring(lineStart, link.start.coerceIn(lineStart, plainMessage.length))
            val trimmedPrefix = linePrefix.trimStart()
            !trimmedPrefix.startsWith(">") && !trimmedPrefix.startsWith("＞")
        }
        .forEach { link -> addUrlFileName(link.url) }
    plainMessage.lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith(">") || trimmed.startsWith("＞")
        }
        .forEach { line ->
            compatMediaFileName.findAll(line).forEach { match ->
                add(match.groupValues[1])
            }
        }
}.distinctBy(String::lowercase)

/**
 * Resolves only posts before the source post. Text quotations intentionally scan newest-first,
 * matching the legacy app when identical text occurs more than once.
 */
fun resolveCompatQuotePosts(
    posts: List<CompatPostSnapshot>,
    sourcePosition: Int,
    query: String
): List<CompatPostSnapshot> {
    val candidates = posts.asSequence()
        .filter { it.position < sourcePosition }
        .sortedByDescending { it.position }
        .toList()
    when {
        query.startsWith("no:", ignoreCase = true) -> {
            val postNo = query.substringAfter(':').trim()
            return candidates.firstOrNull { it.postNo == postNo }?.let(::listOf).orEmpty()
        }
        query.startsWith("id:", ignoreCase = true) -> {
            val id = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPosterIdentities(post).any { identity ->
                    identity.kind == CompatHeaderExtractionKind.ID && identity.value == id
                }
            }?.let(::listOf).orEmpty()
        }
        query.startsWith("ip:", ignoreCase = true) -> {
            val ip = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPosterIdentities(post).any { identity ->
                    identity.kind == CompatHeaderExtractionKind.IP && identity.value == ip
                }
            }?.let(::listOf).orEmpty()
        }
        query.startsWith("file:", ignoreCase = true) -> {
            val fileName = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPostMediaFileNames(post).any { it.equals(fileName, ignoreCase = true) }
            }?.let(::listOf).orEmpty()
        }
    }
    val normalizedQuery = query.removePrefix("text:").normalizeCompatQuoteText()
    if (normalizedQuery.isBlank()) return emptyList()
    return candidates.filter { it.matchesCompatQuote(normalizedQuery) }
}

internal fun CompatPostSnapshot.matchesCompatQuote(query: String): Boolean {
    val messageLines = messageHtml.toCompatPlainText()
        .lineSequence()
        .map(String::normalizeCompatQuoteText)
    if (messageLines.any { it == query || it.contains(query) }) return true
    // The reference APK also checks the uploaded file-name column when a
    // quoted line is `>fu12345.jpg`/`>f12345.png`. Include every media form,
    // not only the primary source/thumbnail pair.
    val identityHeaders = compatPosterIdentities(this).map(CompatPosterIdentity::display)
    val header = listOfNotNull(subject, author, mail, posterId, postNo)
        .plus(identityHeaders)
        .plus(compatPostMediaFileNames(this))
        .joinToString(" ")
        .normalizeCompatQuoteText()
    return header.contains(query)
}
