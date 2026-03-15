package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.FileType
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.network.BoardUrlResolver
import io.ktor.http.ContentType
import kotlin.text.RegexOption

private val IMAGE_SRC_REGEX = Regex("""<img[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
private val LINK_HREF_REGEX = Regex("""<a[^>]+href\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
private val VIDEO_SRC_REGEX = Regex("""<video[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
private val SOURCE_SRC_REGEX = Regex("""<source[^>]+src\s*=\s*['"]([^'"]+)['"][^>]*>""", RegexOption.IGNORE_CASE)
private val SCRIPT_SRC_REGEX = Regex("""(?si)<script[^>]+src="([^"]+)"[^>]*>.*?</script>""")
private val IFRAME_SRC_REGEX = Regex("""(?si)<iframe[^>]+src="([^"]+)"[^>]*>.*?</iframe>""")
private val CHARSET_REGEX = Regex("""<meta[^>]+charset\s*=\s*["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)
private val CONTENT_TYPE_REGEX = Regex(
    """<meta[^>]+http-equiv\s*=\s*["']?Content-Type["']?[^>]+content\s*=\s*["']?([^"'>\s]+)""",
    RegexOption.IGNORE_CASE
)
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

    var updated = SCRIPT_SRC_REGEX.replace(html) { matchResult ->
        val src = matchResult.groupValues.getOrNull(1).orEmpty()
        if (shouldStrip(src)) "" else matchResult.value
    }
    updated = IFRAME_SRC_REGEX.replace(updated) { matchResult ->
        val src = matchResult.groupValues.getOrNull(1).orEmpty()
        if (shouldStrip(src)) "" else matchResult.value
    }
    return updated
}

internal fun forceSavedHtmlUtf8Charset(html: String): String {
    var updated = CHARSET_REGEX.replace(html) { matchResult ->
        matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
    }
    updated = CONTENT_TYPE_REGEX.replace(updated) { matchResult ->
        matchResult.value.replace(matchResult.groupValues[1], "UTF-8")
    }
    return updated
}

internal fun replaceSavedMediaPaths(
    html: String,
    boardPath: String,
    urlToPathMap: Map<String, String>
): String {
    var updated = html
    urlToPathMap.forEach { (original, relative) ->
        updated = updated.replace(original, relative)
    }

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
        FileType.FULL_IMAGE, FileType.VIDEO -> "${boardPrefix}src"
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
    downloadFailureCount: Int,
    totalMediaCount: Int
): SaveStatus {
    return when {
        downloadFailureCount <= 0 -> SaveStatus.COMPLETED
        downloadFailureCount < totalMediaCount -> SaveStatus.PARTIAL
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
