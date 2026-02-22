@file:OptIn(kotlin.ExperimentalMultiplatform::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.CFStringCreateWithBytes
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.NSString
import platform.Foundation.dataUsingEncoding
import platform.posix.memcpy

actual object TextEncoding {
    private val shiftJisEncoding = CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingDOSJapanese.toUInt())
    private val shiftJisCfEncoding: UInt = kCFStringEncodingDOSJapanese.toUInt()

    actual fun encodeToShiftJis(text: String): ByteArray {
        val nsString = text as NSString
        val data = nsString.dataUsingEncoding(shiftJisEncoding, allowLossyConversion = true)
            ?: return ByteArray(0)
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }

    actual fun decodeToString(bytes: ByteArray, contentType: String?): String {
        if (bytes.isEmpty()) return ""
        val encoding: UInt = when {
            contentType?.contains("shift_jis", ignoreCase = true) == true -> shiftJisCfEncoding
            contentType?.contains("shift-jis", ignoreCase = true) == true -> shiftJisCfEncoding
            contentType?.contains("utf-8", ignoreCase = true) == true -> kCFStringEncodingUTF8.toUInt()
            else -> shiftJisCfEncoding
        }
        val decoded = decodeWithCfEncoding(bytes, encoding)
        if (decoded != null) return decoded
        return decodeWithCfEncoding(bytes, kCFStringEncodingUTF8.toUInt()) ?: ""
    }

    private fun decodeWithCfEncoding(bytes: ByteArray, encoding: UInt): String? {
        val cfString = bytes.usePinned { pinned ->
            CFStringCreateWithBytes(
                alloc = kCFAllocatorDefault,
                bytes = pinned.addressOf(0).reinterpret(),
                numBytes = bytes.size.toLong(),
                encoding = encoding,
                isExternalRepresentation = false
            )
        } ?: return null
        return try {
            val length = CFStringGetLength(cfString)
            val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8) + 1
            val buffer = ByteArray(maxSize.toInt())
            val success = buffer.usePinned { pinned ->
                CFStringGetCString(
                    cfString,
                    pinned.addressOf(0),
                    maxSize,
                    kCFStringEncodingUTF8
                )
            }
            if (!success) return null
            buffer.usePinned { pinned ->
                pinned.addressOf(0).toKString()
            }
        } finally {
            CFRelease(cfString)
        }
    }
}
