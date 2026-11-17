package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextEncodingTest {
    @Test
    fun decodeToString_prefersUtf8WhenCharsetIsMissingAndBytesAreValidUtf8() {
        val text = "過去ログ"

        assertEquals(text, TextEncoding.decodeToString(text.encodeToByteArray(), contentType = "text/html"))
    }

    @Test
    fun decodeToString_fallsBackToShiftJisWhenCharsetIsMissingAndBytesAreNotUtf8() {
        val text = "過去ログ"

        assertEquals(text, TextEncoding.decodeToString(TextEncoding.encodeToShiftJis(text), contentType = "text/html"))
    }

    @Test
    fun decodeToString_fallsBackToShiftJisWhenUtf8HeaderHasShiftJisBytes() {
        val text = "株スレ"

        assertEquals(
            text,
            TextEncoding.decodeToString(
                TextEncoding.encodeToShiftJis(text),
                contentType = "text/html; charset=UTF-8"
            )
        )
    }

    @Test
    fun decodeToString_keepsUtf8PageWhenTheLastCharacterIsCutOff() {
        val full = "過去ログ".encodeToByteArray()
        // Cut 1 or 2 bytes into the final 3-byte character (head-only / Range read).
        for (cut in 1..2) {
            assertEquals(
                "過去ロ\uFFFD",
                TextEncoding.decodeToString(full.copyOfRange(0, full.size - cut), contentType = "text/html; charset=utf-8")
            )
        }
        val emoji = "a😀".encodeToByteArray()
        assertEquals(
            "a\uFFFD",
            TextEncoding.decodeToString(emoji.copyOfRange(0, emoji.size - 2), contentType = "text/html; charset=UTF-8")
        )
    }

    @Test
    fun incompleteUtf8TailStart_onlyReportsAnIncompleteFinalSequence() {
        val full = "あい".encodeToByteArray()
        assertEquals(null, incompleteUtf8TailStart(full))
        assertEquals(null, incompleteUtf8TailStart(ByteArray(0)))
        assertEquals(null, incompleteUtf8TailStart("abc".encodeToByteArray()))
        assertEquals(3, incompleteUtf8TailStart(full.copyOfRange(0, 4)))
        assertEquals(3, incompleteUtf8TailStart(full.copyOfRange(0, 5)))
        // A genuinely malformed tail (stray continuation bytes) is not "truncated".
        assertEquals(null, incompleteUtf8TailStart(byteArrayOf(0x41, 0x80.toByte(), 0x80.toByte(), 0x80.toByte())))
    }

    @Test
    fun decodeToString_doesNotMistakeShiftJisWithALeadByteTailForUtf8() {
        val sjis = TextEncoding.encodeToShiftJis("株スレ")
        val decoded = TextEncoding.decodeToString(sjis + byteArrayOf(0xE3.toByte()), contentType = "text/html; charset=UTF-8")
        assertTrue(decoded.startsWith("株スレ"))
    }
}
