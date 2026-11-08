package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.board.VideoPlayerState
import kotlin.test.*

class CompatViewerKeepScreenOnTest {
    @Test fun onlyThePlayingVideoOnTheShownPageKeepsTheScreenOn() {
        assertTrue(compatViewerKeepsScreenOn(true, VideoPlayerState.Ready))
        // Ready is reported only while playing; paused or ended is Idle.
        for (state in listOf(VideoPlayerState.Idle, VideoPlayerState.Buffering, VideoPlayerState.Error)) {
            assertFalse(compatViewerKeepsScreenOn(true, state), "$state")
        }
        assertFalse(compatViewerKeepsScreenOn(false, VideoPlayerState.Ready))
    }
}
