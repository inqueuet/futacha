@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

import java.nio.charset.Charset

actual object TextEncoding {
    private val shiftJis: Charset = Charset.forName("Shift_JIS")

    actual fun encodeToShiftJis(text: String): ByteArray {
        // Replace unmappable characters so Futaba accepts the payload instead of failing.
        return text
            .toByteArray(shiftJis)
    }

    actual fun decodeToString(bytes: ByteArray, contentType: String?): String {
        val charset = when {
            contentType?.contains("shift_jis", ignoreCase = true) == true -> shiftJis
            contentType?.contains("shift-jis", ignoreCase = true) == true -> shiftJis
            contentType?.contains("utf-8", ignoreCase = true) == true -> Charsets.UTF_8
            else -> shiftJis
        }
        return String(bytes, charset)
    }
}
