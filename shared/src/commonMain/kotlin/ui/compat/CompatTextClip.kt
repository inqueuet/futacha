package com.valoser.futacha.shared.ui.compat

/**
 * The first [count] user-perceived characters of [text], like reference app 18-20's TextClip.take
 * (BreakIterator.getCharacterInstance). An emoji sequence, a flag, a skin tone, a keycap or a
 * letter with combining marks is never cut. This is an approximation of the Unicode grapheme rules
 * that covers what thread titles carry; common code has no BreakIterator.
 */
internal fun compatTakeCharacters(text: String?, count: Int): String {
    if (text == null || count <= 0) return ""
    if (text.length <= count) return text
    var index = 0
    var taken = 0
    while (index < text.length && taken < count) {
        index = compatCharacterEnd(text, index)
        taken++
    }
    return text.substring(0, index)
}

private fun codePointAt(text: String, index: Int): Int {
    val high = text[index]
    if (high.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
        return ((high.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00) + 0x10000
    }
    return high.code
}

private fun charCount(codePoint: Int) = if (codePoint >= 0x10000) 2 else 1

private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

/** Marks that always stay with the character before them. */
private fun isExtender(cp: Int) =
    cp in 0x0300..0x036F || cp in 0x1AB0..0x1AFF || cp in 0x1DC0..0x1DFF || cp in 0x20D0..0x20FF ||
        cp in 0xFE20..0xFE2F || cp in 0x3099..0x309A || // combining marks, incl. the kana voicing marks
        cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF || // variation selectors
        cp in 0x1F3FB..0x1F3FF || // skin tones
        cp in 0xE0020..0xE007F // emoji tag characters (subdivision flags)

private fun compatCharacterEnd(text: String, start: Int): Int {
    val first = codePointAt(text, start)
    var index = start + charCount(first)
    if (first == '\r'.code && index < text.length && text[index] == '\n') return index + 1
    if (isRegionalIndicator(first) && index < text.length) {
        val next = codePointAt(text, index)
        if (isRegionalIndicator(next)) index += charCount(next)
        return index
    }
    while (index < text.length) {
        val cp = codePointAt(text, index)
        when {
            isExtender(cp) -> index += charCount(cp)
            cp == 0x200D -> { // zero width joiner: the next character joins this one
                index += 1
                if (index < text.length) index += charCount(codePointAt(text, index))
            }
            else -> return index
        }
    }
    return index
}
