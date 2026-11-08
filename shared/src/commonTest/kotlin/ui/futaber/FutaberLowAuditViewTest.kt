package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Audit 2026-11-08 A4・A7: the bubble's pointer and the wallpaper's bytes. */
class FutaberLowAuditViewTest {
    @Test fun aWindowNarrowerThanTheMarginsPutsThePointerInTheMiddle() {
        // Before the fix coerceIn threw on the empty range.
        assertEquals(10f, futaberBubblePointerX(anchorX = 3f, width = 20f, marginPx = 8f, pointerHalfPx = 6f))
        assertEquals(0f, futaberBubblePointerX(anchorX = 3f, width = 0f, marginPx = 8f, pointerHalfPx = 6f))
    }

    @Test fun aWideWindowKeepsThePointerUnderTheAnchorAndOffTheEdges() {
        assertEquals(150f, futaberBubblePointerX(150f, 400f, 8f, 6f))
        assertEquals(20f, futaberBubblePointerX(0f, 400f, 8f, 6f))
        assertEquals(380f, futaberBubblePointerX(999f, 400f, 8f, 6f))
    }

    @Test fun theWallpaperBytesAreKeptPerFileAndTheOldestGoes() {
        val cache = FutaberWallpaperBytesCache(2)
        val a = byteArrayOf(1)
        cache.put("a", a)
        cache.put("b", byteArrayOf(2))
        assertSame(a, cache.get("a"), "the same array comes back, so the image loader finds the decoded picture")
        cache.put("c", byteArrayOf(3))
        assertNull(cache.get("a"))
        assertEquals(2, cache.get("b")?.single()?.toInt())
    }
}
