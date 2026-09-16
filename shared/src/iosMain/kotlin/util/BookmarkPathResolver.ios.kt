@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import platform.Foundation.*

private val mediaBookmarkLock = NSLock()
private val mediaBookmarkDirectories = linkedMapOf<String, NSURL>()

/** Keep the security-scoped URL, not just its display path, while a saved thread is open. */
internal fun bookmarkedMediaDirectoryForPath(path: String): NSURL? {
    mediaBookmarkLock.lock()
    return try {
        mediaBookmarkDirectories.entries
            .filter { path.startsWith(it.key.trimEnd('/') + "/") }
            .maxByOrNull { it.key.length }?.value
    } finally { mediaBookmarkLock.unlock() }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal actual fun resolveBookmarkPathForDisplay(bookmarkData: String): String? {
    if (bookmarkData.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS) return null
    val normalized = bookmarkData.trim()
        .replace("\n", "")
        .replace("\r", "")
    if (normalized.isEmpty() || normalized.length > MAX_SAVE_LOCATION_BOOKMARK_BASE64_CHARS) return null

    fun decode(candidate: String): ByteArray? {
        return runCatching { Base64.decode(candidate) }.getOrNull()
    }

    val standard = normalized
        .replace('-', '+')
        .replace('_', '/')
    val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
    val bytes = decode(normalized)
        ?: decode(standard)
        ?: decode(padded)
        ?: return null
    if (bytes.isEmpty()) return null

    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }

    return memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val isStale = alloc<BooleanVar>()
        val url = NSURL.URLByResolvingBookmarkData(
            data,
            options = NSURLBookmarkResolutionWithSecurityScope,
            relativeToURL = null,
            bookmarkDataIsStale = isStale.ptr,
            error = error.ptr
        ) ?: return@memScoped null

        val started = url.startAccessingSecurityScopedResource()
        try {
            url.path?.let { path ->
                mediaBookmarkLock.lock()
                try {
                    mediaBookmarkDirectories.remove(path)
                    mediaBookmarkDirectories[path] = url
                    while (mediaBookmarkDirectories.size > 32) {
                        mediaBookmarkDirectories.remove(mediaBookmarkDirectories.keys.first())
                    }
                } finally { mediaBookmarkLock.unlock() }
            }
            url.path ?: url.absoluteString?.removePrefix("file://")
        } finally {
            if (started) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}
