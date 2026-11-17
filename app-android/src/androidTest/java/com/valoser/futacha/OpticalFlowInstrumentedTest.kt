package com.valoser.futacha

import com.valoser.futacha.testing.video.OpticalFlowContract
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking

class OpticalFlowInstrumentedTest {
    @Test fun nativeTrackerFollowsMovementFreezesOnLossAndAcceptsManualReseed() =
        OpticalFlowContract.translationLossAndExplicitReseed()
    @Test fun decodedVideoTracksBothDirectionsPreservesManualAnchorsAndCancelsAtomically() = runBlocking {
        OpticalFlowContract.actualTimelineManualAnchorsAndCancellation(InstrumentationRegistry.getInstrumentation().targetContext)
    }
}
