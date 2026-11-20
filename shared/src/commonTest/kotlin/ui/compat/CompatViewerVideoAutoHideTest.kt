package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.board.VideoPlayerState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** aimg seq46: 「動画の操作パネルを自動で隠す」 hides the panel once the video plays. */
class CompatViewerVideoAutoHideTest {
    @Test
    fun theSwitchOnHidesOnlyAPlayingVideosChrome() {
        val on = compatViewerBehaviour(mapOf(compatPreferenceStorageKey("viewer", COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY) to "ON"))
            .videoOptions.controlsAutoHideMillis ?: 0
        assertEquals(COMPAT_VIEWER_VIDEO_AUTO_HIDE_MILLIS, on)
        assertTrue(compatViewerShouldAutoHideVideoChrome(true, VideoPlayerState.Ready, true, on))
        assertFalse(compatViewerShouldAutoHideVideoChrome(true, VideoPlayerState.Buffering, true, on))
        assertFalse(compatViewerShouldAutoHideVideoChrome(true, VideoPlayerState.Idle, true, on))
        assertFalse(compatViewerShouldAutoHideVideoChrome(false, VideoPlayerState.Ready, true, on))
        assertFalse(compatViewerShouldAutoHideVideoChrome(true, VideoPlayerState.Ready, false, on))
    }

    @Test
    fun theSwitchOffKeepsThePanelUntilATap() {
        val off = compatViewerBehaviour(emptyMap()).videoOptions.controlsAutoHideMillis ?: 0
        assertFalse(compatViewerShouldAutoHideVideoChrome(true, VideoPlayerState.Ready, true, off))
    }
}
