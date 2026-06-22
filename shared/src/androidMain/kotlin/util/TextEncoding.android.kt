@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

actual object TextEncoding {
    private val shiftJis: Charset = Charset.forName("Shift_JIS")

    actual fun encodeToShiftJis(text: String): ByteArray {
        // Replace unmappable characters so Futaba accepts the payload instead of failing.
        return text
            .toByteArray(shiftJis)
    }

    actual fun decodeToString(bytes: ByteArray, contentType: String?): String {
        return when {
            contentType?.contains("shift_jis", ignoreCase = true) == true -> String(bytes, shiftJis)
            contentType?.contains("shift-jis", ignoreCase = true) == true -> String(bytes, shiftJis)
            contentType?.contains("utf-8", ignoreCase = true) == true ->
                decodeUtf8Strict(bytes) ?: String(bytes, shiftJis)
            else -> decodeUtf8Strict(bytes) ?: String(bytes, shiftJis)
        }
    }

    private fun decodeUtf8Strict(bytes: ByteArray): String? {
        if (!isValidUtf8Bytes(bytes)) return null
        return runCatching {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrNull()
    }
}
