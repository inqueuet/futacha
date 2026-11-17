package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertContentEquals

class ShiftJisEncodingSupportTest {
    @Test
    fun encodeShiftJisDeterministically_returnsWholeEncodingWhenAvailable() {
        val result = encodeShiftJisDeterministically("ABC") { text ->
            if (text == "ABC") byteArrayOf(1, 2, 3) else error("chunk fallback should not run")
        }

        assertContentEquals(byteArrayOf(1, 2, 3), result)
    }

    @Test
    fun encodeShiftJisDeterministically_replacesUnmappableChunkWithQuestionMark() {
        val result = encodeShiftJisDeterministically("A😀B") { text ->
            when (text) {
                "A😀B" -> null
                "A" -> byteArrayOf(0x41)
                "😀" -> null
                "B" -> byteArrayOf(0x42)
                else -> error("unexpected chunk: $text")
            }
        }

        assertContentEquals(byteArrayOf(0x41, SHIFT_JIS_REPLACEMENT_BYTE, 0x42), result)
    }

    @Test
    fun encodeShiftJisDeterministically_treatsInvalidSurrogateAsSingleReplacement() {
        val result = encodeShiftJisDeterministically("A\uD83DB") { text ->
            when (text) {
                "A\uD83DB" -> null
                "A" -> byteArrayOf(0x41)
                "\uD83D" -> null
                "B" -> byteArrayOf(0x42)
                else -> error("unexpected chunk: $text")
            }
        }

        assertContentEquals(byteArrayOf(0x41, SHIFT_JIS_REPLACEMENT_BYTE, 0x42), result)
    }
}
