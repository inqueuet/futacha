package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.testing.video.OpticalFlowContract
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class OpticalFlowNativeTest {
    @Test fun nativeTrackerFollowsMovementFreezesOnLossAndAcceptsManualReseed() =
        OpticalFlowContract.translationLossAndExplicitReseed()
    @Test fun decodedVideoTracksBothDirectionsPreservesManualAnchorsAndCancelsAtomically() = runBlocking {
        OpticalFlowContract.actualTimelineManualAnchorsAndCancellation()
    }
}
