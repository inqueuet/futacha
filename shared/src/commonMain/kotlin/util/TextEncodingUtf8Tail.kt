package com.valoser.futacha.shared.util

private const val UTF8_TAIL_REPLACEMENT_CHAR = '�'

/**
 * Start of a final UTF-8 sequence whose lead byte announces more bytes than remain,
 * or null when the data does not end in such an incomplete sequence.
 */
internal fun incompleteUtf8TailStart(bytes: ByteArray): Int? {
    if (bytes.isEmpty()) return null
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

/**
 * Decodes data whose last UTF-8 character was cut off (a Range / head-only read):
 * only the incomplete tail of 1-3 bytes is dropped and replaced by U+FFFD. The rest
 * must still be strictly valid UTF-8, so Shift_JIS data is never mistaken for UTF-8.
 */
internal fun decodeUtf8WithTruncatedTail(bytes: ByteArray, decodeStrict: (ByteArray) -> String?): String? {
    val tailStart = incompleteUtf8TailStart(bytes) ?: return null
    val prefix = bytes.copyOfRange(0, tailStart)
    if (!isValidUtf8Bytes(prefix)) return null
    val decoded = decodeStrict(prefix) ?: return null
    return decoded + UTF8_TAIL_REPLACEMENT_CHAR
}
