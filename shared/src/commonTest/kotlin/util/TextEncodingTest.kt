package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
