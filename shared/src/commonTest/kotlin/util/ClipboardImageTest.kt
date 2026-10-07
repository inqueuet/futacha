package com.valoser.futacha.shared.util

import kotlin.test.*

class ClipboardImageTest {
    @Test fun recognizesImageBytesPreservesAnimationAndNeverMarksHandwriting() {
        val samples = listOf(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10) to "png",
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()) to "jpg",
            "GIF89a".encodeToByteArray() to "gif",
            "RIFF0000WEBP".encodeToByteArray() to "webp"
        )
        samples.forEach { (bytes, extension) ->
            val result = clipboardImageData(bytes)
            assertSame(bytes, result.bytes)
            assertEquals("clipboard.$extension", result.fileName)
            assertFalse(result.isHandwriting)
        }
    }
    @Test fun rejectsEmptyTextUrlVideoAndOversizedClipboardWithoutAttachment() {
        listOf(byteArrayOf(), "https://may.2chan.net/b/src/1.png".encodeToByteArray(),
            "not an image".encodeToByteArray(), "0000ftypmp42".encodeToByteArray()).forEach {
            assertFails { clipboardImageData(it) }
        }
        assertFails { clipboardImageData(ByteArray(32 * 1024 * 1024 + 1)) }
    }
}
