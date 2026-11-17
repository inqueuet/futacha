package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.video.model.MosaicBounds
import kotlin.test.*

class VideoDetectionMatchingTest {
    @Test fun wideMarginAndSmallTrackingDriftKeepTheSameDetectionTrack() {
        val detection = Detection(MosaicBounds(.5f, .5f, .025f, .025f), "target", .9f)
        val expanded = expandVideoDetectionBounds(detection.bounds, .75f)
        val predicted = expanded.copy(width = expanded.width * 1.05f, height = expanded.height * 1.05f)
        assertTrue(intersectionOverUnion(detection.bounds, predicted) < .15f)
        repeat(20) {
            assertSame(detection, matchVideoTrackDetection(listOf(detection), "target", predicted, .75f))
        }
        assertNull(matchVideoTrackDetection(listOf(detection), "other", predicted, .75f))
    }
}
