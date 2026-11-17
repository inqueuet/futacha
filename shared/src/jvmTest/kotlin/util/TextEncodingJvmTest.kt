package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TextEncodingJvmTest {
    @Test fun macPunctuationAliasesEncodeWithoutQuestionMarksOrNumericEscapes() {
        val aliases = "\u301c\u2212\u2016\u2014"
        val canonical = "\uff5e\uff0d\u2225\u2015"
        assertContentEquals(TextEncoding.encodeToShiftJis(canonical), TextEncoding.encodeToShiftJis(aliases))
        val sanitized = sanitizeForShiftJis(aliases)
        assertEquals(0, sanitized.escapedCodePointCount)
        assertEquals(canonical, canonicalCp932Text(TextEncoding.decodeToString(
            TextEncoding.encodeToShiftJis(sanitized.sanitizedText), "text/plain; charset=Shift_JIS")))
        assertEquals(com.valoser.futacha.shared.compat.normalizeCompatSearchText(canonical),
            com.valoser.futacha.shared.compat.normalizeCompatSearchText(aliases))
    }

    @Test
    fun shiftJisDecodesWindowsExtensionsWithoutCorruptingTheNextCharacter() {
        // ① (0x8740), № (0x8782), ㈱ (0x878A) followed by plain text.
        val bytes = byteArrayOf(
            0x87.toByte(), 0x40, 0x87.toByte(), 0x82.toByte(), 0x87.toByte(), 0x8A.toByte(), 'A'.code.toByte()
        )

        assertEquals("①№㈱A", TextEncoding.decodeToString(bytes, "text/html; charset=Shift_JIS"))
        assertEquals("①№㈱A", TextEncoding.decodeToString(bytes, contentType = null))
    }

    @Test
    fun shiftJisEncodesWindowsExtensionsForPosting() {
        assertContentEquals(
            byteArrayOf(0x87.toByte(), 0x40, 0x87.toByte(), 0x8A.toByte()),
            TextEncoding.encodeToShiftJis("①㈱")
        )
    }
}
