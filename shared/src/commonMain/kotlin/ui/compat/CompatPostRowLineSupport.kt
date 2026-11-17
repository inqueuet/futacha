package com.valoser.futacha.shared.ui.compat

/**
 * Returns the line of [message] that contains the caret [offset].
 *
 * A caret at 0 in a message starting with '\n' used to yield the range
 * (1, 0) and crash in `substring` (E-1); the offset is also clamped because
 * the tapped annotated text may be longer than the raw message.
 */
internal fun compatMessageLineAtOffset(message: String, offset: Int): String {
    val caret = offset.coerceIn(0, message.length)
    val lineStart = if (caret == 0) 0 else message.lastIndexOf('\n', caret - 1) + 1
    val lineEnd = message.indexOf('\n', caret).takeIf { it >= 0 } ?: message.length
    return message.substring(lineStart, maxOf(lineStart, lineEnd))
}
