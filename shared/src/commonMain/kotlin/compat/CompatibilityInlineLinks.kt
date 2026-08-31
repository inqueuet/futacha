package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.parser.HtmlEntityDecoder
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN

/** A link range in the text rendered by the compatibility thread view. */
data class CompatInlineLink(
    val start: Int,
    val endExclusive: Int,
    val url: String
)

sealed interface CompatInlineUrlRoute {
    data class RegisteredThread(
        val thread: CanonicalThreadUrl,
        val boardKey: String
    ) : CompatInlineUrlRoute

    data class UnregisteredThread(val thread: CanonicalThreadUrl) : CompatInlineUrlRoute
    data object External : CompatInlineUrlRoute
}

/**
 * Mirrors old.apk/1.apk's reply-link routing: Futaba thread URLs stay inside
 * the reader only when their board is registered; every other URL is external.
 */
fun resolveCompatInlineUrlRoute(
    rawUrl: String,
    registeredBoardsByCanonicalUrl: Map<String, String>
): CompatInlineUrlRoute {
    val thread = canonicalizeThreadUrl(rawUrl) ?: return CompatInlineUrlRoute.External
    val boardKey = registeredBoardsByCanonicalUrl[thread.canonicalBoardUrl]
        ?: return CompatInlineUrlRoute.UnregisteredThread(thread)
    return CompatInlineUrlRoute.RegisteredThread(thread, boardKey)
}

private val compatInlineUrlRegex = Regex(
    """https?://[^\s<>\"']+""",
    RegexOption.IGNORE_CASE
)
private val compatBareApuSmallFileRegex = Regex(
    "(?:^|[^A-Za-z0-9])((?:fu\\d+|f\\d+)\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?![A-Za-z0-9])",
    RegexOption.IGNORE_CASE
)
private val compatBareSioFileRegex = Regex(
    "(?:^|[^A-Za-z0-9])(s[apusqz]\\d{5,10}(?:\\.[0-9a-z]{2,4})?)(?![A-Za-z0-9])",
    RegexOption.IGNORE_CASE
)
private val compatBareVoirodaFileRegex = Regex(
    "(?:^|[^A-Za-z0-9])(vo\\d{1,6}\\.(?:mp3|wav|webm|mp4|ccs))(?![A-Za-z0-9])",
    RegexOption.IGNORE_CASE
)
private val compatAnchorRegex = Regex(
    """(?is)<a\b[^>]*\bhref\s*=\s*(['\"])(.*?)\1[^>]*>(.*?)</a\s*>"""
)
private val compatInlineUrlSchemes = setOf("http://", "https://")
private const val COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN = 256
private const val COMPAT_INLINE_LINK_MAX_RESULTS = 512

/**
 * Finds URLs that are visible in a post body, including normal Futaba
 * autolinks whose `<a>` label is not itself the URL. The returned offsets are
 * offsets in [String.toCompatPlainText], which is the text shown by Compose.
 */
fun compatInlineLinks(messageHtml: String): List<CompatInlineLink> {
    val plainText = messageHtml.toCompatPlainText()
    if (plainText.isBlank()) return emptyList()

    val links = mutableListOf<CompatInlineLink>()
    compatInlineUrlRegex.findAll(plainText).take(COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN).forEach { match ->
        val url = normalizeCompatInlineUrl(match.value) ?: return@forEach
        val end = match.range.first + url.length
        links += CompatInlineLink(match.range.first, end, url)
    }

    // up2 often displays only `fu1234567.jpg` in the post body. Keep that
    // filename tappable even when the cached HTML has no anchor or image tag.
    compatBareApuSmallFileRegex.findAll(plainText).take(COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN).forEach { match ->
        val fileName = match.groupValues.getOrNull(1) ?: return@forEach
        val start = match.range.first + match.value.indexOf(fileName)
        links += CompatInlineLink(
            start = start,
            endExclusive = start + fileName.length,
            url = "https://dec.2chan.net/${if (fileName.startsWith("fu", ignoreCase = true)) "up2" else "up"}/src/$fileName"
        )
    }

    compatBareSioFileRegex.findAll(plainText).take(COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN).forEach { match ->
        val fileName = match.groupValues.getOrNull(1) ?: return@forEach
        val start = match.range.first + match.value.indexOf(fileName)
        links += CompatInlineLink(
            start = start,
            endExclusive = start + fileName.length,
            url = buildCompatSioUrl(fileName)
        )
    }
    compatBareVoirodaFileRegex.findAll(plainText).take(COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN).forEach { match ->
        val fileName = match.groupValues.getOrNull(1) ?: return@forEach
        val start = match.range.first + match.value.indexOf(fileName)
        links += CompatInlineLink(
            start = start,
            endExclusive = start + fileName.length,
            url = "https://voiroda.git-server.com/v/$fileName"
        )
    }

    var labelSearchStart = 0
    compatAnchorRegex.findAll(messageHtml).take(COMPAT_INLINE_LINK_MAX_MATCHES_PER_PATTERN).forEach { match ->
        val url = normalizeCompatInlineUrl(match.groupValues[2]) ?: return@forEach
        val label = match.groupValues[3].toCompatPlainText().trim()
        if (label.isBlank()) return@forEach
        val labelStart = plainText.indexOf(label, labelSearchStart)
        if (labelStart < 0) return@forEach
        labelSearchStart = labelStart + label.length
        links += CompatInlineLink(labelStart, labelStart + label.length, url)
    }

    val sorted = links
        .distinct()
        .sortedWith(compareBy<CompatInlineLink> { it.start }.thenByDescending { it.endExclusive })
    val accepted = ArrayList<CompatInlineLink>(minOf(sorted.size, COMPAT_INLINE_LINK_MAX_RESULTS))
    var previousAcceptedEnd = -1
    for (link in sorted) {
        if (accepted.size >= COMPAT_INLINE_LINK_MAX_RESULTS) break
        // Sorting by start (and longest first for equal starts) means only the
        // last accepted end is needed to reject every overlapping range.
        if (link.start >= previousAcceptedEnd) {
            accepted += link
            previousAcceptedEnd = link.endExclusive
        }
    }
    return accepted
}

private fun buildCompatSioUrl(fileName: String): String {
    val lower = fileName.lowercase()
    val base = when {
        lower.startsWith("sa") -> "http://www.nijibox6.com/futabafiles/001/src/"
        lower.startsWith("sp") -> "http://www.nijibox2.com/futabafiles/003/src/"
        lower.startsWith("su") -> "http://www.nijibox5.com/futabafiles/tubu/src/"
        lower.startsWith("ss") -> "http://www.nijibox5.com/futabafiles/kobin/src/"
        lower.startsWith("sq") -> "http://www.nijibox6.com/futabafiles/mid/src/"
        else -> "http://www.siokarabin.com/futabafiles/big/src/"
    }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val canOpenAsMedia = extension in (FUTABA_COMPAT_IMAGE_EXTENSIONS + FUTABA_COMPAT_VIDEO_EXTENSIONS)
    return if (canOpenAsMedia) "$base$fileName" else "$base$fileName.html"
}

private fun normalizeCompatInlineUrl(raw: String): String? {
    var value = HtmlEntityDecoder.decode(raw.trim())
    if (compatInlineUrlSchemes.none { value.startsWith(it, ignoreCase = true) }) return null

    // Do not make sentence punctuation part of the browser URL. Keep balanced
    // parentheses because they are common in Wikipedia and documentation URLs.
    while (value.isNotEmpty() && value.last() in ".,!?;:、。，．！？」』".toCharArray()) {
        value = value.dropLast(1)
    }
    while (value.endsWith(")") && value.count { it == ')' } > value.count { it == '(' }) {
        value = value.dropLast(1)
    }
    return value.takeIf { it.length > "https://".length }
}
