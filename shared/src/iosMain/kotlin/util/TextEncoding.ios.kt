@file:OptIn(
    kotlin.ExperimentalMultiplatform::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.valoser.futacha.shared.util

import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRangeMake
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.CFStringCreateWithBytes
import platform.CoreFoundation.CFStringGetCharacters
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingDOSJapanese
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.*
import platform.posix.memcpy

actual object TextEncoding {
    private const val MAX_DECODED_UTF16_UNITS = 32L * 1024L * 1024L
    // Bounds the bytes re-converted after each unmappable character in the
    // lossy fallback, so a page with many bad bytes stays near linear time.
    private const val LOSSY_RUN_MAX_BYTES = 32 * 1024
    private const val REPLACEMENT_CHAR = '�'
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

    /**
     * Mirrors the Android/JVM decoder: an explicit Shift_JIS header or a body
     * that is not UTF-8 is decoded as Shift_JIS with U+FFFD replacing each
     * malformed or unmappable byte (CFStringCreateWithBytes itself is strict
     * and would otherwise empty the whole page). With an explicit UTF-8 header,
     * a multi-byte character cut off by a Range read is replaced instead of
     * forcing the whole body through the Shift_JIS fallback.
     */
    actual fun decodeToString(bytes: ByteArray, contentType: String?): String {
        if (bytes.isEmpty()) return ""
        when {
            contentType?.contains("shift_jis", ignoreCase = true) == true ->
                return decodeShiftJisLossy(bytes)
            contentType?.contains("shift-jis", ignoreCase = true) == true ->
                return decodeShiftJisLossy(bytes)
            contentType?.contains("utf-8", ignoreCase = true) == true ->
                return decodeUtf8Strict(bytes)
                    ?: decodeUtf8WithTruncatedTail(bytes)
                    ?: decodeShiftJisLossy(bytes)
        }
        return decodeUtf8Strict(bytes) ?: decodeShiftJisLossy(bytes)
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? {
        if (!isValidUtf8Bytes(bytes)) return null
        return decodeWithCfEncoding(bytes, 0, bytes.size, utf8CfEncoding)
    }

    private fun decodeUtf8WithTruncatedTail(bytes: ByteArray): String? {
        val tailStart = incompleteUtf8TailStart(bytes) ?: return null
        val prefix = bytes.copyOfRange(0, tailStart)
        if (!isValidUtf8Bytes(prefix)) return null
        val decoded = decodeWithCfEncoding(prefix, 0, prefix.size, utf8CfEncoding) ?: return null
        return decoded + REPLACEMENT_CHAR
    }

    /** Start of a final UTF-8 sequence whose lead byte announces more bytes than remain. */
    private fun incompleteUtf8TailStart(bytes: ByteArray): Int? {
        val lastIndex = bytes.lastIndex
        for (back in 0..minOf(2, lastIndex)) {
            val index = lastIndex - back
            val value = bytes[index].toInt() and 0xFF
            if (value in 0x80..0xBF) continue
            val required = when (value) {
                in 0xC2..0xDF -> 2
                in 0xE0..0xEF -> 3
                in 0xF0..0xF4 -> 4
                else -> return null
            }
            return if (back + 1 < required) index else null
        }
        return null
    }

    internal fun decodeShiftJisLossy(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        decodeWithCfEncoding(bytes, 0, bytes.size, shiftJisCfEncoding)?.let { return it }
        val output = StringBuilder(bytes.size)
        var position = 0
        while (position < bytes.size) {
            val runEnd = shiftJisStructuralRunEnd(bytes, position)
            if (runEnd == position) {
                // Undefined single byte (0x80, 0xA0, 0xFD-0xFF), a lead byte
                // without a valid trail byte, or a lead byte cut off at the end.
                output.append(REPLACEMENT_CHAR)
                position += 1
                continue
            }
            val failedAt = appendMappablePrefix(bytes, position, runEnd, output)
            if (failedAt == runEnd) {
                position = runEnd
            } else {
                // Structurally valid but unmapped pair: like the JVM decoder,
                // only the lead byte is malformed and the trail is reprocessed.
                output.append(REPLACEMENT_CHAR)
                position = failedAt + 1
            }
        }
        return output.toString()
    }

    /**
     * Appends the longest decodable prefix of the structurally valid run
     * [start, end) and returns the offset of the first unmappable character,
     * or [end] when the whole run decoded.
     */
    private fun appendMappablePrefix(bytes: ByteArray, start: Int, end: Int, output: StringBuilder): Int {
        decodeWithCfEncoding(bytes, start, end, shiftJisCfEncoding)?.let {
            output.append(it)
            return end
        }
        if (start + shiftJisCharLength(bytes, start) >= end) return start
        val middle = shiftJisCharBoundaryAtOrAfter(bytes, start, end, start + (end - start) / 2)
        val failedInFirstHalf = appendMappablePrefix(bytes, start, middle, output)
        if (failedInFirstHalf < middle) return failedInFirstHalf
        return appendMappablePrefix(bytes, middle, end, output)
    }

    private fun shiftJisStructuralRunEnd(bytes: ByteArray, start: Int): Int {
        val limit = minOf(bytes.size, start + LOSSY_RUN_MAX_BYTES)
        var index = start
        while (index < limit) {
            val value = bytes[index].toInt() and 0xFF
            when {
                isShiftJisSingleByte(value) -> index += 1
                isShiftJisLeadByte(value) -> {
                    val trail = bytes.getOrNull(index + 1)?.toInt()?.and(0xFF) ?: return index
                    if (!isShiftJisTrailByte(trail)) return index
                    // Keep a pair split by the run limit for the next run.
                    if (index + 2 > limit && index > start) return index
                    index += 2
                }
                else -> return index
            }
        }
        return index
    }

    private fun shiftJisCharLength(bytes: ByteArray, index: Int): Int =
        if (isShiftJisLeadByte(bytes[index].toInt() and 0xFF)) 2 else 1

    /** First character boundary at or after [target] within a structurally valid run. */
    private fun shiftJisCharBoundaryAtOrAfter(bytes: ByteArray, start: Int, end: Int, target: Int): Int {
        var index = start
        while (index < target) index += shiftJisCharLength(bytes, index)
        return minOf(index, end)
    }

    private fun isShiftJisSingleByte(value: Int): Boolean = value <= 0x7F || value in 0xA1..0xDF

    private fun isShiftJisLeadByte(value: Int): Boolean = value in 0x81..0x9F || value in 0xE0..0xFC

    private fun isShiftJisTrailByte(value: Int): Boolean = value in 0x40..0x7E || value in 0x80..0xFC

    private fun decodeWithCfEncoding(bytes: ByteArray, start: Int, end: Int, encoding: UInt): String? {
        if (start >= end) return ""
        val cfString = bytes.usePinned { pinned ->
            CFStringCreateWithBytes(
                alloc = kCFAllocatorDefault,
                bytes = pinned.addressOf(start).reinterpret(),
                numBytes = (end - start).toLong(),
                encoding = encoding,
                isExternalRepresentation = false
            )
        } ?: return null
        return try {
            val length = CFStringGetLength(cfString)
            if (length !in 0L..MAX_DECODED_UTF16_UNITS) return null
            if (length == 0L) return ""
            val chars = CharArray(length.toInt())
            chars.usePinned { pinned ->
                CFStringGetCharacters(
                    cfString,
                    CFRangeMake(0, length),
                    pinned.addressOf(0).reinterpret<UShortVar>()
                )
            }
            chars.concatToString()
        } finally {
            CFRelease(cfString)
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        if (this.length > Int.MAX_VALUE.toULong()) return ByteArray(0)
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        return ByteArray(length).also { bytes ->
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, this.length)
            }
        }
    }
}
