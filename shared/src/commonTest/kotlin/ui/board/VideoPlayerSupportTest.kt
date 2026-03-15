package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerSupportTest {
    @Test
    fun resolveVideoPreviewChromeState_maps_preview_ui_flags() {
        val idle = resolveVideoPreviewChromeState(VideoPlayerState.Idle)
        assertTrue(idle.isBuffering)
        assertFalse(idle.showsError)
        assertTrue(idle.showsCloseButton)

        val ready = resolveVideoPreviewChromeState(VideoPlayerState.Ready)
        assertFalse(ready.isBuffering)
        assertFalse(ready.showsError)
        assertFalse(ready.showsCloseButton)

        val error = resolveVideoPreviewChromeState(VideoPlayerState.Error)
        assertFalse(error.isBuffering)
        assertTrue(error.showsError)
        assertTrue(error.showsCloseButton)
    }

    @Test
    fun extractVideoUrlExtension_ignores_query_and_lowercases() {
        assertEquals("mp4", extractVideoUrlExtension("https://example.com/movie.MP4?token=1"))
        assertEquals("webm", extractVideoUrlExtension("//example.com/video.webm"))
        assertEquals("", extractVideoUrlExtension("https://example.com/noext"))
    }

    @Test
    fun buildEmbeddedVideoHtml_sanitizes_html_breakout_chars() {
        val html = buildEmbeddedVideoHtml("https://example.com/v\"ideo<1>.mp4?x=1&y=2")
        assertTrue("%22" in html)
        assertTrue("%3C" in html)
        assertTrue("%3E" in html)
        assertTrue("&y=2" in html)
        assertFalse("video<1>" in html)
    }

    @Test
    fun resolveReadyVideoPlayerState_and_normalizeVolume_match_expected_behavior() {
        assertEquals(VideoPlayerState.Ready, resolveReadyVideoPlayerState(true))
        assertEquals(VideoPlayerState.Idle, resolveReadyVideoPlayerState(false))

        assertEquals(0f, normalizeVideoPlayerVolume(0.8f, isMuted = true))
        assertEquals(0f, normalizeVideoPlayerVolume(-1f, isMuted = false))
        assertEquals(1f, normalizeVideoPlayerVolume(2f, isMuted = false))
        assertEquals(0.35f, normalizeVideoPlayerVolume(0.35f, isMuted = false))
    }
}
