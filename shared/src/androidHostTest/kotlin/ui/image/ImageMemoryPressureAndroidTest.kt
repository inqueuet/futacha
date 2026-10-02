@file:Suppress("DEPRECATION")

package com.valoser.futacha.shared.ui.image

import android.content.ComponentCallbacks2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageMemoryPressureAndroidTest {
    @Test
    fun heapPressureIsDetectedWithoutTrimCallbacks() {
        assertNull(androidHeapMemoryPressureLevel(79, 100))
        assertEquals(ImageMemoryPressureLevel.MODERATE, androidHeapMemoryPressureLevel(80, 100))
        assertEquals(ImageMemoryPressureLevel.CRITICAL, androidHeapMemoryPressureLevel(90, 100))
        assertNull(androidHeapMemoryPressureLevel(1, 0))
    }

    @Test
    fun trimMemorySignalsMapToBoundedPressureLevels() {
        assertNull(androidTrimMemoryPressureLevel(0))
        assertEquals(
            ImageMemoryPressureLevel.MODERATE,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        )
        // Leaving the screen is not memory pressure.
        assertNull(androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertNull(androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertEquals(
            ImageMemoryPressureLevel.CRITICAL,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        )
        assertEquals(
            ImageMemoryPressureLevel.CRITICAL,
            androidTrimMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        )
    }

    private fun sample(
        heapUsed: Long = 10,
        native: Long = 0,
        available: Long = 4_000,
        threshold: Long = 500,
        total: Long = 8_000,
        low: Boolean = false
    ) = AndroidMemorySample(heapUsed, 100, native, available, threshold, total, low)

    @Test
    fun nativeAndSystemMemoryAreDetectedWhileTheJavaHeapIsLow() {
        assertNull(androidMemoryPressureLevel(sample()))
        // Android 8+ bitmaps: a quarter of the device's RAM in the native heap.
        assertEquals(ImageMemoryPressureLevel.MODERATE, androidMemoryPressureLevel(sample(native = 2_000)))
        assertNull(androidMemoryPressureLevel(sample(native = 1_999)))
        assertEquals(ImageMemoryPressureLevel.MODERATE, androidMemoryPressureLevel(sample(available = 999)))
        assertEquals(ImageMemoryPressureLevel.CRITICAL, androidMemoryPressureLevel(sample(low = true)))
        assertEquals(ImageMemoryPressureLevel.CRITICAL, androidMemoryPressureLevel(sample(heapUsed = 90, native = 2_000)))
        // An unavailable ActivityManager reports no system values.
        assertNull(androidMemoryPressureLevel(sample(available = -1, threshold = 0, total = 0, native = 5_000)))
    }

    @Test
    fun heldPressureNeedsALowerValueToClear() {
        assertNull(androidHeapMemoryPressureLevel(75, 100))
        assertEquals(ImageMemoryPressureLevel.MODERATE, androidHeapMemoryPressureLevel(75, 100, ImageMemoryPressureLevel.MODERATE))
        assertNull(androidHeapMemoryPressureLevel(69, 100, ImageMemoryPressureLevel.MODERATE))
        assertEquals(ImageMemoryPressureLevel.CRITICAL, androidHeapMemoryPressureLevel(86, 100, ImageMemoryPressureLevel.CRITICAL))
        assertEquals(ImageMemoryPressureLevel.MODERATE, androidHeapMemoryPressureLevel(86, 100, ImageMemoryPressureLevel.MODERATE))
    }

    @Test
    fun persistentPressureIsNotReportedEveryFiveSeconds() {
        val sampler = AndroidMemoryPressureSampler(initialRepeatMillis = 120_000, maxRepeatMillis = 480_000)
        val moderate = ImageMemoryPressureLevel.MODERATE
        assertEquals(moderate, sampler.report(moderate, 0))
        // Before: the heap staying above 80% re-throttled images on every 5 s sample.
        val repeated = (1..23).mapNotNull { sampler.report(moderate, it * 5_000L) }
        assertEquals(emptyList(), repeated)
        assertEquals(moderate, sampler.report(moderate, 120_000))
        assertNull(sampler.report(moderate, 300_000))
        assertEquals(moderate, sampler.report(moderate, 360_000)) // doubled to 240 s
        assertNull(sampler.report(moderate, 800_000))
        assertEquals(moderate, sampler.report(moderate, 840_000)) // capped at 480 s
        // A rise is reported at once, and so is pressure that returns after it cleared.
        assertEquals(ImageMemoryPressureLevel.CRITICAL, sampler.report(ImageMemoryPressureLevel.CRITICAL, 845_000))
        assertNull(sampler.report(moderate, 850_000))
        assertNull(sampler.report(null, 855_000))
        assertEquals(moderate, sampler.report(moderate, 860_000))
    }

    @Test
    fun samplerAppliesHysteresisToItsOwnLevel() {
        val sampler = AndroidMemoryPressureSampler()
        assertNull(sampler.level(sample(heapUsed = 75)))
        sampler.report(sampler.level(sample(heapUsed = 82)), 0)
        assertEquals(ImageMemoryPressureLevel.MODERATE, sampler.level(sample(heapUsed = 75)))
    }
}
