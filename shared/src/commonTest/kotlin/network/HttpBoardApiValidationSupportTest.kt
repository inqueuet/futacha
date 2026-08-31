package com.valoser.futacha.shared.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpBoardApiValidationSupportTest {
    @Test
    fun validateHttpBoardApiPostInput_acceptsReasonableInput() {
        validateHttpBoardApiPostInput(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "comment",
            password = "1234",
            imageFile = ByteArray(16)
        )
    }

    @Test
    fun validateHttpBoardApiPostInput_rejectsNullCharacterAndOversizeImage() {
        val nullCharError = assertFailsWith<IllegalArgumentException> {
            validateHttpBoardApiPostInput(
                name = "bad\u0000name",
                email = "",
                subject = "",
                comment = "",
                password = "",
                imageFile = null
            )
        }
        assertEquals("name contains null character", nullCharError.message)

        val imageError = assertFailsWith<IllegalArgumentException> {
            validateHttpBoardApiPostInput(
                name = "",
                email = "",
                subject = "",
                comment = "",
                password = "",
                imageFile = ByteArray(8_192_001)
            )
        }
        assertEquals(
            "Image file size (8192001 bytes) exceeds maximum (8192000 bytes)",
            imageError.message
        )
    }

    @Test
    fun validateHttpBoardApiPostInput_rejectsOverlongFields() {
        val error = assertFailsWith<IllegalArgumentException> {
            validateHttpBoardApiPostInput(
                name = "n".repeat(101),
                email = "",
                subject = "",
                comment = "",
                password = "",
                imageFile = null
            )
        }

        assertEquals("Name exceeds maximum length (100): 101", error.message)
    }

    @Test
    fun deletionValidation_rejectsBlankNullAndLongPassword() {
        assertEquals(
            "Password must not be blank",
            assertFailsWith<IllegalArgumentException> {
                validateHttpBoardApiDeletionPassword("   ")
            }.message
        )
        assertEquals(
            "password contains null character",
            assertFailsWith<IllegalArgumentException> {
                validateHttpBoardApiDeletionPassword("ab\u0000cd")
            }.message
        )
        assertEquals(
            "Password exceeds maximum length (100): 101",
            assertFailsWith<IllegalArgumentException> {
                validateHttpBoardApiDeletionPassword("p".repeat(101))
            }.message
        )
    }

    @Test
    fun reasonCodeValidation_rejectsNullAndOverlongValues() {
        assertEquals(
            "reasonCode contains null character",
            assertFailsWith<IllegalArgumentException> {
                validateHttpBoardApiReasonCode("11\u00000")
            }.message
        )
        assertEquals(
            "Reason code exceeds maximum length (50): 51",
            assertFailsWith<IllegalArgumentException> {
                validateHttpBoardApiReasonCode("1".repeat(51))
            }.message
        )
    }

    @Test
    fun determineHttpBoardApiEncoding_detectsUtf8AndShiftJis() {
        assertEquals(HttpBoardApiPostEncoding.UTF8, determineHttpBoardApiEncoding("unicode"))
        assertEquals(HttpBoardApiPostEncoding.UTF8, determineHttpBoardApiEncoding("UTF-8"))
        assertEquals(HttpBoardApiPostEncoding.SHIFT_JIS, determineHttpBoardApiEncoding("文字"))
        assertEquals(HttpBoardApiPostEncoding.SHIFT_JIS, determineHttpBoardApiEncoding("sjis"))
    }
}
