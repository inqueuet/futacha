package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** L21: long ranges become a continuous slider with ± steps; values stay on the original step. */
class FtutterSliderTest {
    @Test fun aFewStepsKeepTheirTicksAndThousandsDropThem() {
        assertEquals(38, ftutterSliderSteps(1.0, 40.0, 1.0))
        assertEquals(19, ftutterSliderSteps(0.0, 1.0, 0.05))
        assertEquals(0, ftutterSliderSteps(0.1, 1000.0, 0.1))
        assertEquals(0, ftutterSliderSteps(1.0, 4096.0, 1.0))
    }

    @Test fun nudgingMovesOneStepWithoutDecimalNoiseAndStopsAtTheEnds() {
        assertEquals(20.1, ftutterSliderNudge(20.0, 0.1, 1000.0, 0.1, 1))
        assertEquals(19.9, ftutterSliderNudge(20.0, 0.1, 1000.0, 0.1, -1))
        assertEquals(0.1, ftutterSliderNudge(0.1, 0.1, 1000.0, 0.1, -1))
        assertEquals(1000.0, ftutterSliderNudge(1000.0, 0.1, 1000.0, 0.1, 1))
        assertEquals(0.35, ftutterSliderNudge(0.3, 0.0, 1.0, 0.05, 1))
        assertEquals(20.1, ftutterSliderSnap(20.0999999, 0.1, 1000.0, 0.1))
        assertEquals(4096.0, ftutterSliderSnap(5000.0, 1.0, 4096.0, 1.0))
    }
}
