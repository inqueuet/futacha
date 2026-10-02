package com.valoser.futacha.shared.media.video

import kotlin.test.Test
import kotlin.test.assertEquals

class RetrieverTimelineOffsetTest {
    @Test fun ignoredEditListMediaTimeIsMeasuredFromTheEarliestPresentedSample() {
        // B-frames in decode order: I(66_666) P(200_000) B(133_333) B(100_000) with a 2-frame
        // composition delay that the platform keeps and Media3 removes (first frame at 0).
        assertEquals(66_666L, platformTimelineOffsetUs(listOf(66_666, 200_000, 133_333, 100_000), 0))
    }

    @Test fun agreeingTimelinesNeedNoOffset() {
        assertEquals(0L, platformTimelineOffsetUs(listOf(0, 100_000, 33_333, 66_666), 0))
        assertEquals(0L, platformTimelineOffsetUs(listOf(1_000, 34_333), 1_000))
    }

    @Test fun noPlatformSamplesKeepsTheRequestedTime() {
        assertEquals(0L, platformTimelineOffsetUs(emptyList(), 33_333))
    }
}
