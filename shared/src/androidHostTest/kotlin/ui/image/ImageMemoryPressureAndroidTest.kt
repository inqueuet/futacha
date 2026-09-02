@file:Suppress("DEPRECATION")

package com.valoser.futacha.shared.ui.image

import android.content.ComponentCallbacks2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageMemoryPressureAndroidTest {
    @Test
    fun trimMemorySignalsMapToBoundedPressureLevels() {
        assertNull(androidTrimMemoryPressureLevel(0))
        assertEquals(
            ImageMemoryPressureLevel.MODERATE,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        )
        assertEquals(
            ImageMemoryPressureLevel.MODERATE,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        )
        assertEquals(
            ImageMemoryPressureLevel.CRITICAL,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        )
        assertEquals(
            ImageMemoryPressureLevel.CRITICAL,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        )
    }
}
