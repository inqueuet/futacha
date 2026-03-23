package com.valoser.futacha.shared.ui.board

private val VIDEO_MEDIA_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "avi", "ts", "flv")

internal data class MediaUrlInfo(
    val normalizedUrl: String,
    val fileName: String?,
    val extension: String,
    val mediaType: MediaType,
    val isGif: Boolean
)

internal fun parseMediaUrlInfo(url: String?): MediaUrlInfo? {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val normalizedUrl = trimmed.substringBefore('#').substringBefore('?')
    val fileName = normalizedUrl.substringAfterLast('/', "").takeIf { it.isNotBlank() }
    val extension = fileName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        .orEmpty()
    val mediaType = if (extension in VIDEO_MEDIA_EXTENSIONS) {
        MediaType.Video
    } else {
        MediaType.Image
    }
    return MediaUrlInfo(
        normalizedUrl = normalizedUrl,
        fileName = fileName,
        extension = extension,
        mediaType = mediaType,
        isGif = extension == "gif"
    )
}
