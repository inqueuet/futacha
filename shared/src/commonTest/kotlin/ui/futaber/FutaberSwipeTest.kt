package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The swipes of the thread and the catalog follow the original (docs/thread-feedback-1444136530-2026-10-27.md §4). */
class FutaberSwipeTest {
    private val edge = 20f
    private val trigger = 56f

    @Test fun aRightSwipeStartedAtTheEdgeOpensTheBoardList() {
        assertEquals(FutaberSwipe.EdgeRight, futaberSwipeDecision(startX = 6f, dx = 60f, dy = 0f, edgePx = edge, triggerPx = trigger))
        assertEquals(FutaberSwipe.EdgeRight, futaberSwipeDecision(startX = 20f, dx = 60f, dy = 5f, edgePx = edge, triggerPx = trigger))
    }

    @Test fun aRightSwipeStartedFurtherInGoesBack() {
        assertEquals(FutaberSwipe.Right, futaberSwipeDecision(startX = 22f, dx = 60f, dy = 0f, edgePx = edge, triggerPx = trigger))
        assertEquals(FutaberSwipe.Right, futaberSwipeDecision(startX = 80f, dx = 200f, dy = 30f, edgePx = edge, triggerPx = trigger))
    }

    @Test fun aLeftSwipeGoesForwardWhereverItStarts() {
        assertEquals(FutaberSwipe.Left, futaberSwipeDecision(startX = 320f, dx = -60f, dy = 0f, edgePx = edge, triggerPx = trigger))
        // The original's right edge also opened the writing screen.
        assertEquals(FutaberSwipe.Left, futaberSwipeDecision(startX = 374f, dx = -274f, dy = 10f, edgePx = edge, triggerPx = trigger))
        // Started at the left edge, a swipe to the left is still a left swipe.
        assertEquals(FutaberSwipe.Left, futaberSwipeDecision(startX = 4f, dx = -60f, dy = 0f, edgePx = edge, triggerPx = trigger))
    }

    @Test fun nothingHappensBeforeTheFingerHasGoneFarEnough() {
        assertNull(futaberSwipeDecision(startX = 80f, dx = 55f, dy = 0f, edgePx = edge, triggerPx = trigger))
        assertNull(futaberSwipeDecision(startX = 300f, dx = -55f, dy = 0f, edgePx = edge, triggerPx = trigger))
    }

    @Test fun aMostlyVerticalMoveIsNeverASwipe() {
        // A diagonal scroll of the list: far enough sideways, but more vertical than the swipe allows.
        assertNull(futaberSwipeDecision(startX = 80f, dx = 70f, dy = 50f, edgePx = edge, triggerPx = trigger))
        assertTrue(futaberSwipeIsVertical(dx = 10f, dy = 30f, slopPx = 18f))
        // Below the touch slop nothing is decided yet, and a sideways move is not vertical.
        assertFalse(futaberSwipeIsVertical(dx = 2f, dy = 10f, slopPx = 18f))
        assertFalse(futaberSwipeIsVertical(dx = 60f, dy = 30f, slopPx = 18f))
    }
}
