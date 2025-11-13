@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.length
import platform.Foundation.bytes
import platform.posix.memcpy

actual object TextEncoding {
    private val shiftJisEncoding = CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingDOSJapanese)

    actual fun encodeToShiftJis(text: String): ByteArray {
        val nsString = text as NSString
        val data = nsString.dataUsingEncoding(shiftJisEncoding, allowLossyConversion = true)
            ?: nsString.dataUsingEncoding(NSUTF8StringEncoding, allowLossyConversion = true)
            ?: return ByteArray(0)
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return bytes
    }
}
