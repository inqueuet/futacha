package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit C2: the catalog's position net puts the list back only when nobody moved it to the top. */
class FtutterKeptScrollStepTest {
    @Test fun awayFromTheTopThePlaceIsKept() {
        assertEquals(FtutterKeptScrollStep.KEEP, ftutterKeptScrollStep(atTop = false, hasKept = true, scrolling = true, userGesture = true))
        assertEquals(FtutterKeptScrollStep.KEEP, ftutterKeptScrollStep(atTop = false, hasKept = false, scrolling = false, userGesture = false))
    }

    @Test fun anUnrequestedJumpToTheTopIsPutBack() {
        assertEquals(FtutterKeptScrollStep.RESTORE, ftutterKeptScrollStep(atTop = true, hasKept = true, scrolling = false, userGesture = false))
    }

    @Test fun aFlingThatSettlesAtTheTopIsTheUsersAndIsNotPutBack() {
        // The fling has stopped (no scroll running) but it began with the user's drag.
        assertEquals(FtutterKeptScrollStep.FORGET, ftutterKeptScrollStep(atTop = true, hasKept = true, scrolling = false, userGesture = true))
        assertEquals(FtutterKeptScrollStep.FORGET, ftutterKeptScrollStep(atTop = true, hasKept = true, scrolling = true, userGesture = false))
    }

    @Test fun atTheTopWithNothingKeptNothingHappens() {
        assertEquals(FtutterKeptScrollStep.NOTHING, ftutterKeptScrollStep(atTop = true, hasKept = false, scrolling = false, userGesture = false))
    }
}
