package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftJisSanitizerTest {
    @Test
    fun sanitizeForShiftJis_escapesEmojiAsNumericEntity() {
        val result = sanitizeForShiftJis("テスト😊です")

        assertEquals("テスト&#128522;です", result.sanitizedText)
        assertEquals(0, result.removedCodePointCount)
        assertEquals(1, result.escapedCodePointCount)
    }

    @Test
    fun sanitizeForShiftJis_keepsShiftJisCharacters() {
        val result = sanitizeForShiftJis("これはテストです")

        assertEquals("これはテストです", result.sanitizedText)
        assertEquals(0, result.removedCodePointCount)
        assertEquals(0, result.escapedCodePointCount)
    }

    @Test
    fun sanitizeForShiftJis_escapesMultipleSupplementaryCodePoints() {
        val result = sanitizeForShiftJis("A😀B👀C")

        assertEquals("A&#128512;B&#128064;C", result.sanitizedText)
        assertEquals(0, result.removedCodePointCount)
        assertEquals(2, result.escapedCodePointCount)
    }

    @Test
    fun sanitizeForShiftJis_keepsLiteralQuestionMark() {
        val result = sanitizeForShiftJis("abc?def")

        assertEquals("abc?def", result.sanitizedText)
        assertEquals(0, result.removedCodePointCount)
        assertEquals(0, result.escapedCodePointCount)
    }

    @Test
    fun sanitizeForShiftJis_removesInvalidSurrogate() {
        val result = sanitizeForShiftJis("A\uD83DB")

        assertEquals("AB", result.sanitizedText)
        assertEquals(1, result.removedCodePointCount)
        assertEquals(0, result.escapedCodePointCount)
    }

    @Test
    fun shiftJisSanitizedByteCount_matchesSanitizedWirePayload() {
        assertEquals(0, shiftJisSanitizedByteCount(""))
        assertEquals(8, shiftJisSanitizedByteCount("テテテテ"))
        for (text in listOf("これはテストです", "abc\r\ndef", "A😀B", "A\uD83DB", "テスト😊です")) {
            assertEquals(
                TextEncoding.encodeToShiftJis(sanitizeForShiftJis(text).sanitizedText).size,
                shiftJisSanitizedByteCount(text),
                text
            )
        }
    }
}
