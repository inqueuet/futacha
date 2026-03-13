package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileSystemSupportTest {
    @Test
    fun validateFileSystemPath_accepts_normal_relative_path() {
        validateFileSystemPath("threads/123/index.json")
    }

    @Test
    fun validateFileSystemPath_rejects_invalid_inputs() {
        assertFailsWith<IllegalArgumentException> {
            validateFileSystemPath("")
        }
        assertFailsWith<IllegalArgumentException> {
            validateFileSystemPath("../index.json")
        }
        assertFailsWith<IllegalArgumentException> {
            validateFileSystemPath("dir/\u0000file.txt")
        }
        assertFailsWith<IllegalArgumentException> {
            validateFileSystemPath("a".repeat(MAX_FILE_SYSTEM_FILENAME_LENGTH + 1))
        }
    }

    @Test
    fun validateFileSystemSize_accepts_boundary_and_rejects_invalid_values() {
        validateFileSystemSize(0L, "file")
        validateFileSystemSize(MAX_FILE_SYSTEM_FILE_SIZE, "file")

        val negative = assertFailsWith<IllegalArgumentException> {
            validateFileSystemSize(-1L, "file")
        }
        assertEquals("file size cannot be negative: -1", negative.message)

        val tooLarge = assertFailsWith<IllegalArgumentException> {
            validateFileSystemSize(MAX_FILE_SYSTEM_FILE_SIZE + 1L, "file")
        }
        assertEquals(
            "file size (${MAX_FILE_SYSTEM_FILE_SIZE + 1L} bytes) exceeds maximum allowed ($MAX_FILE_SYSTEM_FILE_SIZE bytes)",
            tooLarge.message
        )
    }
}
