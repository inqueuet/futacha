package com.valoser.futacha.shared.media

/**
 * Media formats accepted by the current Futaba posting forms.
 *
 * Keep this list in one place.  The compatibility client used to have a
 * separate list in the HTML parser, post picker, inline-link parser and
 * viewer, which made PNG/WebP/GIF behave differently depending on where a
 * media URL was encountered.
 */
internal val FUTABA_IMAGE_EXTENSIONS: Set<String> =
    setOf("gif", "jpg", "jpeg", "jpe", "png", "webp")

internal val FUTABA_VIDEO_EXTENSIONS: Set<String> =
    setOf("webm", "mp4")

/** Formats found in older archives and external Futaba-compatible uploaders. */
internal val FUTABA_COMPAT_IMAGE_EXTENSIONS: Set<String> =
    FUTABA_IMAGE_EXTENSIONS + setOf("bmp", "apng", "avif")

internal val FUTABA_COMPAT_VIDEO_EXTENSIONS: Set<String> =
    FUTABA_VIDEO_EXTENSIONS + setOf("m4v", "mov", "mkv", "avi", "ts", "flv")

internal val FUTABA_COMPAT_MEDIA_EXTENSIONS: Set<String> =
    FUTABA_COMPAT_IMAGE_EXTENSIONS + FUTABA_COMPAT_VIDEO_EXTENSIONS

/** Regex fragment for every media extension accepted by parser/viewer code. */
internal val FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN: String =
    FUTABA_COMPAT_MEDIA_EXTENSIONS
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

internal enum class FutabaMediaKind {
    IMAGE,
    VIDEO,
    UNSUPPORTED
}

internal fun mediaFileExtension(value: String?): String {
    val clean = value.orEmpty()
        .trim()
        .substringBefore('#')
        .substringBefore('?')
    return clean.substringAfterLast('/', clean)
        .substringAfterLast('.', "")
        .lowercase()
}

internal fun classifyFutabaMedia(
    value: String?,
    contentType: String? = null
): FutabaMediaKind {
    val extension = mediaFileExtension(value)
    return when {
        extension in FUTABA_COMPAT_VIDEO_EXTENSIONS -> FutabaMediaKind.VIDEO
        extension in FUTABA_COMPAT_IMAGE_EXTENSIONS -> FutabaMediaKind.IMAGE
        contentType?.substringBefore(';')?.trim()?.lowercase()?.let { it.startsWith("video/") } == true ->
            FutabaMediaKind.VIDEO
        contentType?.substringBefore(';')?.trim()?.lowercase()?.let { it.startsWith("image/") } == true ->
            FutabaMediaKind.IMAGE
        else -> FutabaMediaKind.UNSUPPORTED
    }
}

internal fun isFutabaImageExtension(extension: String?): Boolean =
    extension?.lowercase() in FUTABA_COMPAT_IMAGE_EXTENSIONS

internal fun isFutabaVideoExtension(extension: String?): Boolean =
    extension?.lowercase() in FUTABA_COMPAT_VIDEO_EXTENSIONS
