package com.valoser.futacha.shared.media.source

import com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS
import com.valoser.futacha.shared.media.FUTABA_COMPAT_VIDEO_EXTENSIONS
import io.ktor.http.Url

/** Thumbnail and video requests must not implicitly download an original image. */
internal fun isSharedOriginalImageUrl(value: String): Boolean = isSharedOriginalUrl(value, FUTABA_COMPAT_IMAGE_EXTENSIONS)

internal fun isSharedOriginalVideoUrl(value: String): Boolean = isSharedOriginalUrl(value, FUTABA_COMPAT_VIDEO_EXTENSIONS)

internal fun isSharedOriginalMediaUrl(value: String): Boolean =
    isSharedOriginalImageUrl(value) || isSharedOriginalVideoUrl(value)

/** Strip only UI fragments; signed query bytes remain part of the original's identity. */
internal fun originalVideoRequest(value: String): OriginalMediaRequest = OriginalMediaRequest(
    url = value.substringBefore('#'),
    reloadToken = value.substringAfter('#', "").takeIf { it.startsWith("compat-reload=") }
        ?.substringAfter('=')?.toLongOrNull() ?: 0L
)

private fun isSharedOriginalUrl(value: String, extensions: Set<String>): Boolean {
    val url = runCatching { Url(value) }.getOrNull() ?: return false
    return url.protocol.name in setOf("http", "https") &&
        (url.host.equals("2chan.net", true) || url.host.endsWith(".2chan.net", true)) &&
        url.encodedPath.contains("/src/") &&
        url.encodedPath.substringAfterLast('.').lowercase() in extensions
}
