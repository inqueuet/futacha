@file:OptIn(
    kotlin.ExperimentalMultiplatform::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

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
import platform.Foundation.*
import platform.posix.memcpy

actual object TextEncoding {
    private val shiftJisEncoding = CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingDOSJapanese.toUInt())
    private val shiftJisCfEncoding: UInt = kCFStringEncodingDOSJapanese.toUInt()
    private val utf8CfEncoding: UInt = kCFStringEncodingUTF8

    actual fun encodeToShiftJis(text: String): ByteArray {
        return encodeShiftJisDeterministically(text) { chunk ->
            val nsString = NSString.create(string = chunk)
            val data = nsString.dataUsingEncoding(shiftJisEncoding, allowLossyConversion = false)
                ?: return@encodeShiftJisDeterministically null
            data.toByteArray()
        }
    }

    actual fun decodeToString(bytes: ByteArray, contentType: String?): String {
        if (bytes.isEmpty()) return ""
        val encoding: UInt = when {
            contentType?.contains("shift_jis", ignoreCase = true) == true -> shiftJisCfEncoding
            contentType?.contains("shift-jis", ignoreCase = true) == true -> shiftJisCfEncoding
            contentType?.contains("utf-8", ignoreCase = true) == true -> utf8CfEncoding
            else -> shiftJisCfEncoding
        }
        val decoded = decodeWithCfEncoding(bytes, encoding)
        if (decoded != null) return decoded
        return decodeWithCfEncoding(bytes, utf8CfEncoding) ?: ""
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

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, this.length)
            }
        }
    }
}
