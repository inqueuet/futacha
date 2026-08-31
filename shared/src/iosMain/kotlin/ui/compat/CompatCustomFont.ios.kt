@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.memcpy

private const val MAX_CUSTOM_FONT_BYTES = 16L * 1024L * 1024L

@Composable
internal actual fun rememberCompatCustomFontFamily(path: String?): FontFamily? {
    return produceState<FontFamily?>(initialValue = null, key1 = path) {
        value = withContext(AppDispatchers.io) {
            path
                ?.takeIf { it.isNotBlank() }
                ?.let(::readCustomFontBytes)
                ?.let { bytes ->
                    runCatching {
                        FontFamily(Font(identity = path, data = bytes))
                    }.getOrNull()
                }
        }
    }.value
}

private fun readCustomFontBytes(path: String): ByteArray? {
    val size = (NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)?.get(NSFileSize) as? NSNumber)
        ?.longValue
        ?: return null
    if (size !in 1L..MAX_CUSTOM_FONT_BYTES) return null
    val handle = NSFileHandle.fileHandleForReadingAtPath(path) ?: return null
    val output = ByteArray(size.toInt())
    var offset = 0
    try {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            while (offset < output.size) {
                error.value = null
                val requested = minOf(64 * 1024, output.size - offset)
                val data = handle.readDataUpToLength(requested.toULong(), error = error.ptr) ?: return null
                val length = data.length.toInt()
                if (length <= 0) break
                output.usePinned { pinned ->
                    memcpy(pinned.addressOf(offset), data.bytes, data.length)
                }
                offset += length
            }
            val extra = handle.readDataUpToLength(1uL, error = error.ptr) ?: return null
            if (extra.length > 0uL) return null
        }
    } finally {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            handle.closeAndReturnError(error.ptr)
        }
    }
    if (offset <= 0) return null
    return if (offset == output.size) output else output.copyOf(offset)
}
