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
        assertEquals(
            ImageData(bytes, "picked.png"),
            buildPickedImageData(bytes, "picked.png")
        )
        assertEquals(
            ImageData(bytes, DEFAULT_PICKED_IMAGE_FILE_NAME),
            buildPickedImageData(bytes, "   ")
        )
        assertNull(buildPickedImageData(ByteArray(0), "empty.png"))
    }
}
