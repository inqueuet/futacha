package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImagePickerSupportTest {
    @Test
    fun isPickedImagePayloadSizeValid_accepts_positive_lengths_within_limit() {
        assertTrue(isPickedImagePayloadSizeValid(1))
        assertTrue(isPickedImagePayloadSizeValid(MAX_PICKED_IMAGE_BYTES))
        assertFalse(isPickedImagePayloadSizeValid(0))
        assertFalse(isPickedImagePayloadSizeValid(MAX_PICKED_IMAGE_BYTES + 1))
    }

    @Test
    fun buildPickedImageData_applies_filename_fallback_and_rejects_invalid_size() {
        val bytes = byteArrayOf(1, 2, 3)
        val picked = buildPickedImageData(bytes, "picked.png")
        val fallback = buildPickedImageData(bytes, "   ")

        assertPickedImageData(bytes, "picked.png", picked)
        assertPickedImageData(bytes, DEFAULT_PICKED_IMAGE_FILE_NAME, fallback)
        assertNull(buildPickedImageData(ByteArray(0), "empty.png"))
    }

    @Test
    fun imageDataEquality_doesNotScanPayloadContents() {
        val bytes = byteArrayOf(1, 2, 3)

        assertEquals(ImageData(bytes, "picked.png"), ImageData(bytes, "picked.png"))
        assertFalse(ImageData(byteArrayOf(1, 2, 3), "picked.png") == ImageData(byteArrayOf(1, 2, 3), "picked.png"))
    }

    @Test
    fun normalizePickedImageData_rejects_invalid_payload_sizes() {
        val valid = ImageData(byteArrayOf(1, 2, 3), "picked.png")

        assertEquals(valid, normalizePickedImageData(valid, maxBytes = 3))
        assertNull(normalizePickedImageData(valid, maxBytes = 2))
        assertNull(normalizePickedImageData(ImageData(ByteArray(0), "empty.png"), maxBytes = 3))
    }

    private fun assertPickedImageData(
        expectedBytes: ByteArray,
        expectedFileName: String,
        actual: ImageData?
    ) {
        assertTrue(actual != null)
        assertTrue(expectedBytes.contentEquals(actual.bytes))
        assertEquals(expectedFileName, actual.fileName)
    }
}
