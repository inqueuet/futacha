package com.valoser.futacha.shared.ui.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatAnimatedImageSamplingTest {
    @Test
    fun targetSize_usesBothRequestedSides() {
        assertEquals(512 to 256, compatAnimatedTargetSize(512, 256, sourceWidth = 4096, sourceHeight = 4096))
    }

    @Test
    fun targetSize_derivesOpenSideFromSourceAspectRatio() {
        assertEquals(400 to 200, compatAnimatedTargetSize(400, null, sourceWidth = 4000, sourceHeight = 2000))
        assertEquals(600 to 300, compatAnimatedTargetSize(null, 300, sourceWidth = 4000, sourceHeight = 2000))
        assertEquals(1 to 1, compatAnimatedTargetSize(1, null, sourceWidth = 4000, sourceHeight = 2))
    }

    @Test
    fun targetSize_isNullForOriginalSizeOrUnknownSource() {
        assertNull(compatAnimatedTargetSize(null, null, sourceWidth = 4096, sourceHeight = 4096))
        assertNull(compatAnimatedTargetSize(512, 512, sourceWidth = 0, sourceHeight = 0))
        assertNull(compatAnimatedTargetSize(0, -1, sourceWidth = 100, sourceHeight = 100))
    }
}
