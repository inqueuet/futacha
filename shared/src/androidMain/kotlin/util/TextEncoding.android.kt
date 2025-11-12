package com.valoser.futacha.shared.util

import java.nio.charset.Charset

actual object TextEncoding {
    private val shiftJis: Charset = Charset.forName("Shift_JIS")

    actual fun encodeToShiftJis(text: String): ByteArray {
        // Replace unmappable characters so Futaba accepts the payload instead of failing.
        return text
            .toByteArray(shiftJis)
    }
}
