package com.valoser.futacha.shared.util

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
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
internal actual fun resolveBookmarkPathForDisplay(bookmarkData: String): String? {
    val normalized = bookmarkData.trim()
        .replace("\n", "")
        .replace("\r", "")
    if (normalized.isEmpty()) return null

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
            url.path ?: url.absoluteString?.removePrefix("file://")
        } finally {
            if (started) {
                url.stopAccessingSecurityScopedResource()
            }
        }
    }
}
