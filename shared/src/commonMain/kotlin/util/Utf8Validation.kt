package com.valoser.futacha.shared.util

internal fun isValidUtf8Bytes(bytes: ByteArray): Boolean {
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xFF
        when {
            first <= 0x7F -> {
                index += 1
            }
            first in 0xC2..0xDF -> {
                if (!hasUtf8Continuation(bytes, index + 1)) return false
                index += 2
            }
            first == 0xE0 -> {
                if (!hasUtf8ContinuationIn(bytes, index + 1, 0xA0, 0xBF)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                index += 3
            }
            first in 0xE1..0xEC || first in 0xEE..0xEF -> {
                if (!hasUtf8Continuation(bytes, index + 1)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                index += 3
            }
            first == 0xED -> {
                if (!hasUtf8ContinuationIn(bytes, index + 1, 0x80, 0x9F)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                index += 3
            }
            first == 0xF0 -> {
                if (!hasUtf8ContinuationIn(bytes, index + 1, 0x90, 0xBF)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                if (!hasUtf8Continuation(bytes, index + 3)) return false
                index += 4
            }
            first in 0xF1..0xF3 -> {
                if (!hasUtf8Continuation(bytes, index + 1)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                if (!hasUtf8Continuation(bytes, index + 3)) return false
                index += 4
            }
            first == 0xF4 -> {
                if (!hasUtf8ContinuationIn(bytes, index + 1, 0x80, 0x8F)) return false
                if (!hasUtf8Continuation(bytes, index + 2)) return false
                if (!hasUtf8Continuation(bytes, index + 3)) return false
                index += 4
            }
            else -> return false
        }
    }
    return true
}

private fun hasUtf8Continuation(bytes: ByteArray, index: Int): Boolean {
    return hasUtf8ContinuationIn(bytes, index, 0x80, 0xBF)
}

private fun hasUtf8ContinuationIn(
    bytes: ByteArray,
    index: Int,
    min: Int,
    max: Int
): Boolean {
    val value = bytes.getOrNull(index)?.toInt()?.and(0xFF) ?: return false
    return value in min..max
}
