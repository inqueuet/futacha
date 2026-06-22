package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.network.BoardUrlResolver
import io.ktor.http.ContentType
import kotlin.text.RegexOption

private const val THREAD_SAVE_TAG_ATTR_LIMIT = 700
private const val THREAD_SAVE_EXTERNAL_TAG_BODY_LIMIT = 200_000
private val IMAGE_SRC_REGEX = Regex("""<img\b[^>]{0,700}\bsrc\s*=\s*['"]([^'"]+)['"][^>]{0,700}>""", RegexOption.IGNORE_CASE)
private val LINK_HREF_REGEX = Regex("""<a\b[^>]{0,700}\bhref\s*=\s*['"]([^'"]+)['"][^>]{0,700}>""", RegexOption.IGNORE_CASE)
private val VIDEO_SRC_REGEX = Regex("""<video\b[^>]{0,700}\bsrc\s*=\s*['"]([^'"]+)['"][^>]{0,700}>""", RegexOption.IGNORE_CASE)
private val SOURCE_SRC_REGEX = Regex("""<source\b[^>]{0,700}\bsrc\s*=\s*['"]([^'"]+)['"][^>]{0,700}>""", RegexOption.IGNORE_CASE)
private val CHARSET_REGEX = Regex("""<meta\b[^>]{0,700}\bcharset\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
private val CONTENT_TYPE_META_REGEX = Regex(
    """<meta\b[^>]{0,700}\bhttp-equiv\s*=\s*["']?Content-Type["']?[^>]{0,700}>""",
    RegexOption.IGNORE_CASE
)
private val CONTENT_TYPE_CHARSET_REGEX = Regex("""charset\s*=\s*[^"'>;\s]+""", RegexOption.IGNORE_CASE)
private val CONTENT_TYPE_CONTENT_ATTR_REGEX = Regex("""\bcontent\s*=\s*(["'])([^"']*)\1""", RegexOption.IGNORE_CASE)
private val HEAD_OPEN_REGEX = Regex("""<head\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_OPEN_REGEX = Regex("""<html\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SUPPORTED_IMAGE_EXTENSIONS = setOf("gif", "jpg", "jpeg", "png", "webp")
private val SUPPORTED_VIDEO_EXTENSIONS = setOf("webm", "mp4")

internal enum class ThreadSaveMediaRequestType {
    THUMBNAIL,
    FULL_IMAGE
}

internal data class ThreadSaveMediaCounts(
    val thumbnailPath: String? = null,
    val imageCount: Int = 0,
    val videoCount: Int = 0
)

internal fun convertSavedThreadHtmlPaths(html: String, urlToPathMap: Map<String, String>): String {
    var converted = html

    converted = IMAGE_SRC_REGEX.replace(converted) { matchResult ->
        val originalUrl = matchResult.groupValues[1]
        val relativePath = urlToPathMap[originalUrl]
        if (relativePath != null) {
            matchResult.value.replace(originalUrl, relativePath)
        } else {
            matchResult.value
        }
    }

    converted = LINK_HREF_REGEX.replace(converted) { matchResult ->
        val originalUrl = matchResult.groupValues[1]
        val relativePath = urlToPathMap[originalUrl]
        if (relativePath != null) {
            matchResult.value.replace(originalUrl, relativePath)
        } else {
            matchResult.value
        }
    }

    converted = VIDEO_SRC_REGEX.replace(converted) { matchResult ->
        val originalUrl = matchResult.groupValues[1]
        val relativePath = urlToPathMap[originalUrl]
        if (relativePath != null) {
            matchResult.value.replace(originalUrl, relativePath)
        } else {
            matchResult.value
        }
    }

    converted = SOURCE_SRC_REGEX.replace(converted) { matchResult ->
        val originalUrl = matchResult.groupValues[1]
        val relativePath = urlToPathMap[originalUrl]
        if (relativePath != null) {
            matchResult.value.replace(originalUrl, relativePath)
        } else {
            matchResult.value
        }
    }

    return converted
}

internal fun rewriteSavedOriginalHtml(
    html: String,
    boardPath: String,
    urlToPathMap: Map<String, String>,
    stripExternalResources: Boolean
): String {
    var updated = html
    if (stripExternalResources) {
        updated = stripSavedExternalScriptsAndIframes(updated)
    }
    updated = replaceSavedMediaPaths(updated, boardPath, urlToPathMap)
    updated = forceSavedHtmlUtf8Charset(updated)
    return updated
}

internal fun stripSavedExternalScriptsAndIframes(html: String): String {
    fun shouldStrip(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("/bin/") || normalized.contains("dec.2chan.net")
    }

    val withoutScripts = stripSavedExternalTagWithSrc(html, tagName = "script", shouldStrip = ::shouldStrip)
    return stripSavedExternalTagWithSrc(withoutScripts, tagName = "iframe", shouldStrip = ::shouldStrip)
}

private fun stripSavedExternalTagWithSrc(
    html: String,
    tagName: String,
    shouldStrip: (String) -> Boolean
): String {
    val startToken = "<$tagName"
    val endToken = "</$tagName>"
    val builder = StringBuilder(html.length)
    var searchStart = 0
    while (searchStart < html.length) {
        val tagStart = html.indexOf(startToken, startIndex = searchStart, ignoreCase = true)
        if (tagStart == -1) {
            builder.append(html, searchStart, html.length)
            break
        }
        builder.append(html, searchStart, tagStart)
        val tagEnd = findBoundedThreadSaveTagEnd(html, tagStart)
        if (tagEnd == -1) {
            builder.append(html[tagStart])
            searchStart = tagStart + 1
            continue
        }
        val tag = html.substring(tagStart, tagEnd + 1)
        val closeIndex = html.indexOf(endToken, startIndex = tagEnd + 1, ignoreCase = true)
        val boundedCloseIndex = closeIndex.takeIf {
            it != -1 && it - tagEnd <= THREAD_SAVE_EXTERNAL_TAG_BODY_LIMIT
        }
        val endExclusive = boundedCloseIndex?.plus(endToken.length) ?: tagEnd + 1
        val src = extractThreadSaveTagAttribute(tag, "src")
        if (src != null && shouldStrip(src)) {
            searchStart = endExclusive
        } else {
            builder.append(html, tagStart, endExclusive)
            searchStart = endExclusive
        }
    }
    return builder.toString()
}

private fun findBoundedThreadSaveTagEnd(html: String, startIndex: Int): Int {
    val limit = minOf(html.length, startIndex + THREAD_SAVE_TAG_ATTR_LIMIT)
    var index = startIndex + 1
    while (index < limit) {
        when (html[index]) {
            '>' -> return index
            '<' -> return -1
        }
        index += 1
    }
    return -1
}

private fun extractThreadSaveTagAttribute(tag: String, attributeName: String): String? {
    var searchStart = 0
    while (searchStart < tag.length) {
        val attrIndex = tag.indexOf(attributeName, startIndex = searchStart, ignoreCase = true)
        if (attrIndex == -1) return null
        val before = tag.getOrNull(attrIndex - 1)
        val after = tag.getOrNull(attrIndex + attributeName.length)
        val hasNameBoundary = before == null || !before.isLetterOrDigit()
        val hasValueBoundary = after == null || after.isWhitespace() || after == '='
        if (!hasNameBoundary || !hasValueBoundary) {
            searchStart = attrIndex + attributeName.length
            continue
        }
        var index = attrIndex + attributeName.length
        while (index < tag.length && tag[index].isWhitespace()) index += 1
        if (tag.getOrNull(index) != '=') {
            searchStart = index
            continue
        }
        index += 1
        while (index < tag.length && tag[index].isWhitespace()) index += 1
        val quote = tag.getOrNull(index)
        if (quote == '\'' || quote == '"') {
            val valueStart = index + 1
            val valueEnd = tag.indexOf(quote, startIndex = valueStart)
            if (valueEnd != -1) return tag.substring(valueStart, valueEnd)
            return null
        }
        val valueStart = index
        while (index < tag.length && !tag[index].isWhitespace() && tag[index] != '>') index += 1
        return tag.substring(valueStart, index).takeIf { it.isNotBlank() }
    }
    return null
}

internal fun forceSavedHtmlUtf8Charset(html: String): String {
    var updated = html
    var hasCharsetMeta = false
    updated = CHARSET_REGEX.replace(updated) { matchResult ->
        hasCharsetMeta = true
        matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
    }
    updated = CONTENT_TYPE_META_REGEX.replace(updated) { matchResult ->
        if (CONTENT_TYPE_CHARSET_REGEX.containsMatchIn(matchResult.value)) {
            CONTENT_TYPE_CHARSET_REGEX.replace(matchResult.value, "charset=UTF-8")
        } else {
            CONTENT_TYPE_CONTENT_ATTR_REGEX.replace(matchResult.value) { contentMatch ->
                val quote = contentMatch.groupValues[1]
                val content = contentMatch.groupValues[2].trimEnd()
                """content=$quote$content; charset=UTF-8$quote"""
            }
        }
    }
    return if (hasCharsetMeta) {
        updated
    } else {
        insertSavedHtmlUtf8CharsetMeta(updated)
    }
}

private fun insertSavedHtmlUtf8CharsetMeta(html: String): String {
    val headMatch = HEAD_OPEN_REGEX.find(html)
    if (headMatch != null) {
        return html.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, """<meta charset="UTF-8">""")
    }
    val htmlMatch = HTML_OPEN_REGEX.find(html)
    if (htmlMatch != null) {
        return html.replaceRange(
            htmlMatch.range.last + 1,
            htmlMatch.range.last + 1,
            """<head><meta charset="UTF-8"></head>"""
        )
    }
    return """<meta charset="UTF-8">""" + html
}

internal fun replaceSavedMediaPaths(
    html: String,
    boardPath: String,
    urlToPathMap: Map<String, String>
): String {
    var updated = convertSavedThreadHtmlPaths(html, urlToPathMap)

    val normalizedBoard = boardPath.trim('/').takeIf { it.isNotEmpty() } ?: return updated
    val escapedBoard = Regex.escape(normalizedBoard)

    val srcPatterns = listOf(
        Regex("https?://[^\"'>]+/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
        Regex("//[^\"'>]+/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
        Regex("/$escapedBoard/src/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
    )
    val thumbPatterns = listOf(
        Regex("https?://[^\"'>]+/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
        Regex("//[^\"'>]+/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE),
        Regex("/$escapedBoard/thumb/([A-Za-z0-9._-]+)", RegexOption.IGNORE_CASE)
    )

    srcPatterns.forEach { regex ->
        updated = regex.replace(updated) { matchResult ->
            "$normalizedBoard/src/${matchResult.groupValues[1]}"
        }
    }
    thumbPatterns.forEach { regex ->
        updated = regex.replace(updated) { matchResult ->
            "$normalizedBoard/thumb/${matchResult.groupValues[1]}"
        }
    }
    return updated
}

internal fun extractThreadSaveBoardPath(boardUrl: String, boardIdFallback: String): String {
    val fallback = boardIdFallback.trim('/').ifEmpty { "b" }
    return runCatching {
        val base = BoardUrlResolver.resolveBoardBaseUrl(boardUrl)
        val afterHost = base.substringAfter("://", base)
        val path = afterHost.substringAfter('/', "").trim('/')
        path.ifEmpty { fallback }
    }.getOrElse { fallback }
}

internal fun getThreadSaveExtensionFromUrl(url: String): String? {
    val sanitized = url
        .substringBefore('#')
        .substringBefore('?')
    return sanitized.substringAfterLast('.', "").takeIf { it.length in 3..4 }
}

internal fun getThreadSaveExtensionFromContentType(contentType: ContentType?): String {
    return when (contentType?.contentSubtype) {
        "jpeg", "jpg" -> "jpg"
        "png" -> "png"
        "gif" -> "gif"
        "webp" -> "webp"
        "mp4" -> "mp4"
        "webm" -> "webm"
        else -> "jpg"
    }
}

internal fun isThreadSaveSupportedExtension(extension: String): Boolean {
    val normalized = extension.lowercase()
    return normalized in SUPPORTED_IMAGE_EXTENSIONS || normalized in SUPPORTED_VIDEO_EXTENSIONS
}

internal fun resolveThreadSaveFileType(
    requestType: ThreadSaveMediaRequestType,
    extension: String
): FileType {
    val normalized = extension.lowercase()
    return when {
        requestType == ThreadSaveMediaRequestType.THUMBNAIL -> FileType.THUMBNAIL
        normalized in SUPPORTED_VIDEO_EXTENSIONS -> FileType.VIDEO
        else -> FileType.FULL_IMAGE
    }
}

internal fun buildThreadSaveRelativePath(
    boardPath: String,
    fileType: FileType,
    fileName: String
): String {
    val boardPrefix = boardPath.trim('/').takeIf { it.isNotEmpty() }?.let { "$it/" } ?: ""
    val subDir = when (fileType) {
        FileType.THUMBNAIL -> "${boardPrefix}thumb"
        FileType.FULL_IMAGE -> "${boardPrefix}src"
        FileType.VIDEO -> "${boardPrefix}videos"
    }
    return "$subDir/$fileName"
}

internal fun updateThreadSaveMediaCounts(
    current: ThreadSaveMediaCounts,
    fileType: FileType,
    relativePath: String,
    postId: String,
    opPostId: String?
): ThreadSaveMediaCounts {
    return when (fileType) {
        FileType.THUMBNAIL -> {
            if (current.thumbnailPath == null && postId == opPostId) {
                current.copy(thumbnailPath = relativePath)
            } else {
                current
            }
        }
        FileType.FULL_IMAGE -> current.copy(imageCount = current.imageCount + 1)
        FileType.VIDEO -> current.copy(videoCount = current.videoCount + 1)
    }
}

internal fun resolveThreadSaveStatus(
    incompleteMediaCount: Int,
    totalMediaCount: Int
): SaveStatus {
    return when {
        incompleteMediaCount <= 0 -> SaveStatus.COMPLETED
        incompleteMediaCount < totalMediaCount -> SaveStatus.PARTIAL
        else -> SaveStatus.FAILED
    }
}

internal fun resolveThreadSavedPostDownloadSuccess(
    originalImageUrl: String?,
    localImagePath: String?,
    localVideoPath: String?
): Boolean {
    return originalImageUrl == null || localImagePath != null || localVideoPath != null
}
