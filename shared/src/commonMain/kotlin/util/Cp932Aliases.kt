package com.valoser.futacha.shared.util

/** Unicode spellings of the same CP932 punctuation byte pairs. */
internal fun canonicalCp932Character(char: Char): Char = when (char) {
    '\u301c' -> '\uff5e'
    '\u2212' -> '\uff0d'
    '\u2016' -> '\u2225'
    '\u2014' -> '\u2015'
    else -> char
}

internal fun canonicalCp932Text(text: String): String =
    if (text.none { canonicalCp932Character(it) != it }) text
    else buildString(text.length) { text.forEach { append(canonicalCp932Character(it)) } }
