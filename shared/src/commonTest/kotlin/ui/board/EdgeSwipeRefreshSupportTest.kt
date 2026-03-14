package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeSwipeRefreshSupportTest {
    @Test
    fun resolveScrollEdgeState_mapsScrollabilityToEdgeFlags() {
        assertEquals(
            ScrollEdgeState(isAtTop = true, isAtBottom = false),
            resolveScrollEdgeState(
                canScrollBackward = false,
                canScrollForward = true
            )
        )
        assertEquals(
            ScrollEdgeState(isAtTop = false, isAtBottom = true),
            resolveScrollEdgeState(
                canScrollBackward = true,
                canScrollForward = false
            )
        )
        assertEquals(
            ScrollEdgeState(isAtTop = true, isAtBottom = true),
            resolveScrollEdgeState(
                canScrollBackward = false,
                canScrollForward = false
            )
        )
    }

    @Test
    fun updateEdgeSwipeRefreshDragState_accumulatesTopAndBottomEdgeDrag() {
        val topState = updateEdgeSwipeRefreshDragState(
            totalDrag = 0f,
            dragAmount = 80f,
            isRefreshing = false,
            isAtTop = true,
            isAtBottom = false,
            maxOverscrollPx = 64f
        )
        assertEquals(80f, topState.totalDrag)
        assertEquals(32f, topState.overscrollTarget)
        assertTrue(topState.shouldConsume)

        val bottomState = updateEdgeSwipeRefreshDragState(
            totalDrag = 0f,
            dragAmount = -80f,
            isRefreshing = false,
            isAtTop = false,
            isAtBottom = true,
            maxOverscrollPx = 64f
        )
        assertEquals(-80f, bottomState.totalDrag)
        assertEquals(-32f, bottomState.overscrollTarget)
        assertTrue(bottomState.shouldConsume)
    }

    @Test
    fun releaseEdgeSwipeRefreshDrag_consumesOppositeDragUntilNeutral() {
        val partiallyReleased = releaseEdgeSwipeRefreshDrag(
            totalDrag = 80f,
            dragAmount = -20f,
            maxOverscrollPx = 64f
        )
        assertEquals(60f, partiallyReleased.totalDrag)
        assertEquals(-20f, partiallyReleased.consumedDrag)
        assertEquals(24f, partiallyReleased.overscrollTarget)

        val fullyReleased = releaseEdgeSwipeRefreshDrag(
            totalDrag = 30f,
            dragAmount = -80f,
            maxOverscrollPx = 64f
        )
        assertEquals(0f, fullyReleased.totalDrag)
        assertEquals(-30f, fullyReleased.consumedDrag)
        assertEquals(0f, fullyReleased.overscrollTarget)
    }

    @Test
    fun releaseEdgeSwipeRefreshDrag_ignoresSameDirectionOrNeutralInput() {
        val sameDirection = releaseEdgeSwipeRefreshDrag(
            totalDrag = 40f,
            dragAmount = 10f,
            maxOverscrollPx = 64f
        )
        assertEquals(40f, sameDirection.totalDrag)
        assertEquals(0f, sameDirection.consumedDrag)

        val neutral = releaseEdgeSwipeRefreshDrag(
            totalDrag = 0f,
            dragAmount = -20f,
            maxOverscrollPx = 64f
        )
        assertEquals(0f, neutral.totalDrag)
        assertEquals(0f, neutral.consumedDrag)
    }

    @Test
    fun shouldTriggerEdgeSwipeRefresh_requiresThresholdExceeded() {
        assertFalse(shouldTriggerEdgeSwipeRefresh(totalDrag = 56f, refreshTriggerPx = 56f))
        assertTrue(shouldTriggerEdgeSwipeRefresh(totalDrag = 56.1f, refreshTriggerPx = 56f))
        assertTrue(shouldTriggerEdgeSwipeRefresh(totalDrag = -60f, refreshTriggerPx = 56f))
    }
}
