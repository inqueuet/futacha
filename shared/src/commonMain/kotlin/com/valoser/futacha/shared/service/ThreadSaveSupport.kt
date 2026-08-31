package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
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
private val META_REFRESH_REGEX = Regex(
    """<meta\b[^>]{0,700}\bhttp-equiv\s*=\s*(["']?)refresh\1[^>]{0,700}>""",
    RegexOption.IGNORE_CASE
)
private val BASE_TAG_REGEX = Regex("""<base\b[^>]{0,700}>""", RegexOption.IGNORE_CASE)
private val HEAD_OPEN_REGEX = Regex("""<head\b[^>]*>""", RegexOption.IGNORE_CASE)
private val HTML_OPEN_REGEX = Regex("""<html\b[^>]*>""", RegexOption.IGNORE_CASE)
private const val SAVED_HTML_CONTENT_SECURITY_POLICY =
    "default-src 'none'; img-src 'self' data:; media-src 'self'; " +
        "style-src 'self' 'unsafe-inline'; font-src 'self' data:; " +
        "script-src 'none'; frame-src 'none'; object-src 'none'; " +
        "connect-src 'none'; base-uri 'none'; form-action 'none'"
private val SUPPORTED_IMAGE_EXTENSIONS = FUTABA_COMPAT_IMAGE_EXTENSIONS
private val SUPPORTED_VIDEO_EXTENSIONS = FUTABA_COMPAT_VIDEO_EXTENSIONS

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
        updated = insertSavedHtmlContentSecurityPolicy(updated)
    }
    updated = replaceSavedMediaPaths(updated, boardPath, urlToPathMap)
    updated = forceSavedHtmlUtf8Charset(updated)
    return updated
}

internal fun stripSavedExternalScriptsAndIframes(html: String): String {
    val withoutScripts = stripSavedActiveTag(html, tagName = "script")
    val withoutFrames = stripSavedActiveTag(withoutScripts, tagName = "iframe")
    val withoutObjects = stripSavedActiveTag(withoutFrames, tagName = "object")
    val withoutEmbeds = stripSavedActiveTag(withoutObjects, tagName = "embed")
    val withoutRefresh = META_REFRESH_REGEX.replace(withoutEmbeds, "")
    return BASE_TAG_REGEX.replace(withoutRefresh, "")
}

private fun stripSavedActiveTag(
    html: String,
    tagName: String
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
        searchStart = endExclusive
    }
    return builder.toString()
}

private fun insertSavedHtmlContentSecurityPolicy(html: String): String {
    val meta = """<meta http-equiv="Content-Security-Policy" content="$SAVED_HTML_CONTENT_SECURITY_POLICY">"""
    val headMatch = HEAD_OPEN_REGEX.find(html)
    if (headMatch != null) {
        return html.replaceRange(headMatch.range.last + 1, headMatch.range.last + 1, meta)
    }
    val htmlMatch = HTML_OPEN_REGEX.find(html)
    if (htmlMatch != null) {
        return html.replaceRange(
            htmlMatch.range.last + 1,
            htmlMatch.range.last + 1,
            "<head>$meta</head>"
        )
    }
    return meta + html
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
    totalMediaCount: Int,
    isContentTruncated: Boolean = false
): SaveStatus {
    val mediaStatus = when {
        incompleteMediaCount <= 0 -> SaveStatus.COMPLETED
        incompleteMediaCount < totalMediaCount -> SaveStatus.PARTIAL
        else -> SaveStatus.FAILED
    }
    return if (isContentTruncated && mediaStatus == SaveStatus.COMPLETED) {
        SaveStatus.PARTIAL
    } else {
        mediaStatus
    }
}

internal fun resolveThreadSavedPostDownloadSuccess(
    originalImageUrl: String?,
    localImagePath: String?,
    localVideoPath: String?
): Boolean {
    return originalImageUrl == null || localImagePath != null || localVideoPath != null
}
