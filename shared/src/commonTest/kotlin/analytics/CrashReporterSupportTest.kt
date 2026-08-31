package com.valoser.futacha.shared.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CrashReporterSupportTest {
    @Test
    fun sanitizedExceptionMessageOmitsOriginalMessageAndUrl() {
        val sensitive = "https://user:secret@example.com/b/res/123.htm?token=private"
        val message = buildSanitizedCrashExceptionMessage(
            IllegalStateException("request failed at $sensitive")
        )

        assertEquals("type=IllegalStateException category=network_error", message)
        assertFalse(message.contains("example.com"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("123"))
    }
}
