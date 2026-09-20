package com.valoser.futacha.shared.media.analysis

import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class VideoAutomaticEditNativeTest {
    @Test fun detectionContoursReviewAndIntervalPreservation() = runBlocking {
        VideoAutomaticEditContract.detectionContoursReviewAndIntervalPreservation()
    }
    @Test fun cancellationAndVideoOffNeverPublishPartialAnalysis() = runBlocking {
        VideoAutomaticEditContract.cancellationAndVideoOffNeverPublishPartialAnalysis()
    }
}
