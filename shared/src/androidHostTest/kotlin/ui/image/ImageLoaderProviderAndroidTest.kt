package com.valoser.futacha.shared.ui.image

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageLoaderProviderAndroidTest {
    @Test
    fun removableStorageCheck_treatsUnknownStoragePathAsUnavailable() {
        val unknownStoragePath = File("/data/local/tmp/external")

        assertFalse(
            isRemovableStorageDirectory(unknownStoragePath) {
                throw IllegalArgumentException("Failed to find storage device at $it")
            }
        )
    }

    @Test
    fun removableStorageCheck_preservesSuccessfulPlatformResult() {
        val storagePath = File("/storage/0000-0000")

        assertTrue(isRemovableStorageDirectory(storagePath) { true })
        assertFalse(isRemovableStorageDirectory(storagePath) { false })
    }
}
