package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompatImagePhashTest {
    @Test
    fun thresholdDefaultsAndGuideMatchTheReferenceImageNgDialog() {
        assertEquals(8, CompatImagePhash.DEFAULT_THRESHOLD)
        assertEquals(0, CompatImagePhash.MIN_THRESHOLD)
        assertEquals(16, CompatImagePhash.MAX_THRESHOLD)
        assertEquals(
            listOf(
                "0" to "同一画像",
                "1～5" to "ほぼ同一（リサイズ・JPEG差分）",
                "6～10" to "軽微編集",
                "11～20" to "類似",
                "20以上" to "別画像"
            ),
            CompatImagePhash.thresholdGuideRows
        )
        assertEquals("NG画像判定中... 3/10", CompatImagePhash.progressLabel(3, 10))
        assertEquals("NG画像判定中... 10/10", CompatImagePhash.progressLabel(99, 10))
    }

    @Test
    fun matchesReferenceShapeAndDistanceRules() {
        val pixels = IntArray(32 * 32) { 0xff336699.toInt() }
        val hash = CompatImagePhash.computeFromArgbPixels(pixels)
        assertNotNull(hash)
        assertEquals(16, hash.length)
        assertEquals(hash, CompatImagePhash.computeFromArgbPixels(pixels.copyOf()))
        assertEquals(0, CompatImagePhash.hammingDistance(hash, hash))
        assertTrue(CompatImagePhash.isSimilar(hash, hash, CompatImagePhash.DEFAULT_THRESHOLD))
    }

    @Test
    fun rejectsMalformedPixelBuffersAndHashes() {
        assertEquals(null, CompatImagePhash.computeFromArgbPixels(IntArray(1)))
        assertEquals(Int.MAX_VALUE, CompatImagePhash.hammingDistance("bad", "00"))
        assertEquals(Int.MAX_VALUE, CompatImagePhash.hammingDistance("gg", "00"))
    }

    @Test
    fun persistentCacheKeysAreStableAndHashesAreValidated() {
        val url = "https://may.2chan.net/b/src/123.jpg"
        assertEquals(compatImagePhashCachePreferenceKey(url), compatImagePhashCachePreferenceKey(url))
        assertTrue(compatImagePhashCachePreferenceKey(url).startsWith("compat.imagePhash."))
        assertTrue(isValidCompatImagePhash("0123456789abcdef"))
        assertEquals(false, isValidCompatImagePhash("not-a-hash"))
    }
}
