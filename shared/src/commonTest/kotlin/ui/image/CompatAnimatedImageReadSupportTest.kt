package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import okio.Buffer

class CompatAnimatedImageReadSupportTest {
    @Test
    fun boundedAnimatedImageRead_acceptsPayloadAtLimit() {
        val payload = ByteArray(32) { it.toByte() }

        assertContentEquals(
            payload,
            Buffer().write(payload).readBoundedCompatAnimatedImageBytes(maxBytes = 32)
        )
    }

    @Test
    fun boundedAnimatedImageRead_rejectsPayloadBeyondLimit() {
        assertFailsWith<IllegalArgumentException> {
            Buffer().write(ByteArray(33)).readBoundedCompatAnimatedImageBytes(maxBytes = 32)
        }
    }
}
