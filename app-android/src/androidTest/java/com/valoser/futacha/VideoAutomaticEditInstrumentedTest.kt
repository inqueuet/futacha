package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.media.analysis.VideoAutomaticEditContract
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VideoAutomaticEditInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun detectionContoursReviewAndIntervalPreservation() = runBlocking {
        VideoAutomaticEditContract.detectionContoursReviewAndIntervalPreservation(context)
    }
    @Test fun cancellationAndVideoOffNeverPublishPartialAnalysis() = runBlocking {
        VideoAutomaticEditContract.cancellationAndVideoOffNeverPublishPartialAnalysis(context)
    }
}
