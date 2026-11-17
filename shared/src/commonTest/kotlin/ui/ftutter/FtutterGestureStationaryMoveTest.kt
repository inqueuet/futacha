package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** Stationary MOVE samples are delivered between movement samples by the app's pointer setting. */
class FtutterGestureStationaryMoveTest {
    @Test fun aPauseInsideTheUpStrokeStillOpensTheDrawer() {
        val tracker = FtutterGestureTracker()
        tracker.update(3f, 0f)
        tracker.update(0f, -10f)
        repeat(5) { tracker.update(0f, 0f) }
        tracker.update(0f, -11f)
        assertEquals("10", tracker.code)
        assertEquals(FtutterGestureAction.OPEN_DRAWER, ftutterGestureActionFor(tracker.code, FTUTTER_GESTURE_DEFAULTS))
    }

    @Test fun aPauseInsideTheFinalHorizontalStrokeDoesNotLeaveThePreviousActionSelected() {
        val tracker = FtutterGestureTracker()
        tracker.update(3f, 0f)
        tracker.update(0f, 21f)
        tracker.update(10f, 0f)
        tracker.update(0f, 0f)
        tracker.update(11f, 0f)
        val map = FTUTTER_GESTURE_DEFAULTS + ("131" to FtutterGestureAction.REFRESH)
        assertEquals("131", tracker.code)
        assertEquals(FtutterGestureAction.REFRESH, ftutterGestureActionFor(tracker.code, map))
    }

    @Test fun stationarySamplesLeaveAllTwentySixTrajectoriesAndTheirActionsUnchanged() {
        FTUTTER_GESTURE_DEFAULTS.keys.forEach { expected ->
            val tracker = FtutterGestureTracker()
            tracker.update(0f, 0f)
            expected.forEachIndexed { index, direction ->
                val (dx, dy) = when (direction) {
                    '0' -> 0f to -3f
                    '1' -> 3f to 0f
                    '2' -> -3f to 0f
                    else -> 0f to 3f
                }
                repeat(if (index == 0) 1 else 8) {
                    tracker.update(dx, dy)
                    repeat(3) { tracker.update(0f, 0f) }
                }
            }
            assertEquals(expected, tracker.code, "Stationary MOVE changed $expected")
            assertEquals(ftutterGestureActionFor(expected, FTUTTER_GESTURE_DEFAULTS),
                ftutterGestureActionFor(tracker.code, FTUTTER_GESTURE_DEFAULTS), expected)
        }
    }

    @Test fun pausesPreserveFractionalMovementAndTheStrictThresholdIncludingSignedZero() {
        val tracker = FtutterGestureTracker()
        tracker.update(-0.125f, 0f)
        repeat(160) {
            tracker.update(0f, -0.125f)
            tracker.update(-0.0f, 0.0f)
        }
        assertEquals("2", tracker.code) // Exactly 20dp still does not add a segment.
        tracker.update(0f, -0.125f)
        assertEquals("20", tracker.code)
        repeat(161) {
            tracker.update(0.125f, 0f)
            tracker.update(0.0f, -0.0f)
        }
        assertEquals("201", tracker.code)
        assertEquals(FtutterGestureAction.REFRESH, ftutterGestureActionFor(tracker.code, FTUTTER_GESTURE_DEFAULTS))
    }

    @Test fun realReversalsAxisChangesAndResetStillDiscardThePreviousPartialSegment() {
        val tracker = FtutterGestureTracker()
        tracker.update(3f, 0f)
        tracker.update(0f, 12f)
        tracker.update(0f, 0f)
        tracker.update(0f, -12f) // Opposite direction starts again.
        tracker.update(0f, 0f)
        tracker.update(0f, -9f)
        assertEquals("10", tracker.code)
        tracker.update(12f, 0f)
        tracker.update(0f, 0f)
        tracker.update(1f, 2f) // The dominant vertical axis really changes.
        tracker.update(9f, 0f)
        assertEquals("10", tracker.code)
        tracker.reset()
        tracker.update(0f, 0f)
        assertEquals("", tracker.code)
        tracker.update(-0.125f, 0.25f) // The first segment still uses the horizontal sign.
        assertEquals("2", tracker.code)
        tracker.update(12f, 12f) // A diagonal tie belongs to the vertical axis.
        tracker.update(0f, 0f)
        tracker.update(9f, 9f)
        assertEquals("23", tracker.code)
    }
}
