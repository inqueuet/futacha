package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class AvPlayerStatusPollPolicyTest {
    @Test
    fun pollsQuicklyUntilPausedAndFullyReported() {
        assertEquals(200L, avPlayerStatusPollDelayMillis(VideoPlayerState.Buffering, itemReady = false, mediaInfoReported = false))
        assertEquals(200L, avPlayerStatusPollDelayMillis(VideoPlayerState.Ready, itemReady = true, mediaInfoReported = true))
        assertEquals(200L, avPlayerStatusPollDelayMillis(VideoPlayerState.Idle, itemReady = true, mediaInfoReported = false))
        assertEquals(200L, avPlayerStatusPollDelayMillis(VideoPlayerState.Idle, itemReady = false, mediaInfoReported = true))
        assertEquals(2_000L, avPlayerStatusPollDelayMillis(VideoPlayerState.Idle, itemReady = true, mediaInfoReported = true))
    }
}
