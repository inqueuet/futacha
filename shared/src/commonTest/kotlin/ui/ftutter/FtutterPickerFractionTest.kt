package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit 2026-11-08 C3: a bar of no width gives 0, not NaN. */
class FtutterPickerFractionTest {
    @Test fun aBarOfNoWidthGivesZero() {
        assertEquals(0f, ftutterPickerFraction(10f, 0f))
        assertEquals(0f, ftutterPickerFraction(Float.NaN, 100f))
    }

    @Test fun thePositionIsAShareKeptInsideZeroToOne() {
        assertEquals(0.25f, ftutterPickerFraction(25f, 100f))
        assertEquals(1f, ftutterPickerFraction(150f, 100f))
        assertEquals(0f, ftutterPickerFraction(-5f, 100f))
    }
}
