package com.valoser.futacha.shared.service

import kotlin.test.Test
import kotlin.test.assertEquals

class SingleMediaSaveSupportTest {
    @Test
    fun buildSingleMediaRelativePath_placesPreviewMediaUnderThreadStorageDirectory() {
        assertEquals(
            "b__777/preview_media/images/sample_1.jpg",
            buildSingleMediaRelativePath(
                storageId = "b__777",
                targetSubDirectory = "images",
                fileName = "sample_1.jpg"
            )
        )
        assertEquals(
            "img__888/preview_media/videos/clip_1.webm",
            buildSingleMediaRelativePath(
                storageId = "img__888",
                targetSubDirectory = "videos",
                fileName = "clip_1.webm"
            )
        )
    }
}
