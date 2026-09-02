package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerSupportTest {
    @Test
    fun videoPreview_doesNotAutoplay() {
        assertFalse(FUTACHA_VIDEO_PREVIEW_AUTOPLAY)
        assertFalse("autoplay" in buildEmbeddedVideoHtml("https://example.com/video.webm"))
    }

    @Test
    fun referenceViewerDoubleTapSeeksTenSecondsAndClampsAtMediaEdges() {
        assertEquals(10_000L, REFERENCE_VIDEO_SEEK_STEP_MILLIS)
        assertEquals(15_000L, resolveReferenceVideoDoubleTapPosition(5_000L, 30_000L, true))
        assertEquals(0L, resolveReferenceVideoDoubleTapPosition(5_000L, 30_000L, false))
        assertEquals(30_000L, resolveReferenceVideoDoubleTapPosition(25_000L, 30_000L, true))
        assertEquals(15_000L, resolveReferenceVideoDoubleTapPosition(25_000L, 30_000L, false))
        assertEquals(Long.MAX_VALUE, resolveReferenceVideoDoubleTapPosition(Long.MAX_VALUE, -1L, true))
    }

    @Test
    fun resolveVideoPreviewChromeState_maps_preview_ui_flags() {
        val idle = resolveVideoPreviewChromeState(VideoPlayerState.Idle)
        assertFalse(idle.isBuffering)
        assertFalse(idle.showsError)
        assertTrue(idle.showsCloseButton)

        val ready = resolveVideoPreviewChromeState(VideoPlayerState.Ready)
        assertFalse(ready.isBuffering)
        assertFalse(ready.showsError)
        assertFalse(ready.showsCloseButton)
        assertFalse(ready.showsControlPanel)

        val readyTouched = resolveVideoPreviewChromeState(VideoPlayerState.Ready, controlsVisible = true)
        assertTrue(readyTouched.showsCloseButton)
        assertTrue(readyTouched.showsControlPanel)

        val error = resolveVideoPreviewChromeState(VideoPlayerState.Error)
        assertFalse(error.isBuffering)
        assertTrue(error.showsError)
        assertTrue(error.showsCloseButton)
        assertTrue(error.showsControlPanel)
    }

    @Test
    fun extractVideoUrlExtension_ignores_query_and_lowercases() {
        assertEquals("mp4", extractVideoUrlExtension("https://example.com/movie.MP4?token=1"))
        assertEquals("webm", extractVideoUrlExtension("//example.com/video.webm"))
        assertEquals("", extractVideoUrlExtension("https://example.com/noext"))
    }

    @Test
    fun iosPlaybackBackendRoutesAppleContainersToAvPlayerAndKeepsWebmInWebView() {
        assertEquals(
            IosVideoPlaybackBackend.AV_PLAYER,
            resolveIosVideoPlaybackBackend("https://example.com/movie.MP4?token=1")
        )
        assertEquals(
            IosVideoPlaybackBackend.AV_PLAYER,
            resolveIosVideoPlaybackBackend("file:///saved/clip.mov")
        )
        assertEquals(
            IosVideoPlaybackBackend.AV_PLAYER,
            resolveIosVideoPlaybackBackend("https://example.com/clip.m4v")
        )
        assertEquals(
            IosVideoPlaybackBackend.WEB_VIEW,
            resolveIosVideoPlaybackBackend("https://example.com/movie.webm")
        )
        assertEquals(
            IosVideoPlaybackBackend.WEB_VIEW,
            resolveIosVideoPlaybackBackend("https://example.com/video")
        )
    }

    @Test
    fun buildEmbeddedVideoHtml_sanitizes_html_breakout_chars() {
        val html = buildEmbeddedVideoHtml("https://example.com/v\"ideo<1>.mp4?x=1&y=2")
        assertTrue("%22" in html)
        assertTrue("%3C" in html)
        assertTrue("%3E" in html)
        assertTrue("&y=2" in html)
        assertFalse("autoplay" in html)
        assertFalse("video<1>" in html)
        assertTrue("controls_visible" in html)
        assertTrue("controls_hidden" in html)
        assertTrue("media:" in html)
        assertTrue("seekFromDoubleTap" in html)
        assertTrue("right ? 10 : -10" in html)
        assertTrue("mediaError.code" in html)
        assertTrue("mediaError.message" in html)
    }

    @Test
    fun playbackErrorFormatsBoundedCodeAndReasonForViewer() {
        assertEquals(
            "エラー: ERROR_CODE_DECODING_FAILED (4003) / decoder unavailable",
            formatVideoPlaybackError(
                VideoPlaybackError(
                    code = "ERROR_CODE_DECODING_FAILED (4003)",
                    message = "decoder\nunavailable"
                )
            )
        )
        assertEquals(null, formatVideoPlaybackError(VideoPlaybackError()))
    }

    @Test
    fun videoMediaInfoFormatsTheTechnicalRowsShownByTheReferenceViewer() {
        assertEquals(
            listOf(
                "映像コーデック: video/webm",
                "Codec ID: vp09.00.10.08",
                "Profile: 0",
                "Level: 1.0",
                "解像度: 1920 × 1080",
                "フレームレート: 30 fps",
                "ビットレート: 2500 kbps",
                "音声コーデック: audio/opus",
                "サンプルレート: 48000 Hz",
                "チャンネル数: 2",
                "長さ: 12.5 秒"
            ),
            formatVideoMediaInfoLines(
                VideoMediaInfo(
                    videoCodec = "video/webm",
                    codecId = "vp09.00.10.08",
                    profile = "0",
                    level = "1.0",
                    width = 1920,
                    height = 1080,
                    frameRate = 30f,
                    bitrate = 2_500_000,
                    audioCodec = "audio/opus",
                    sampleRate = 48_000,
                    channelCount = 2,
                    durationMillis = 12_500
                )
            )
        )
    }

    @Test
    fun codecIdsExposeReferenceProfileAndLevelRows() {
        assertEquals(
            VideoCodecProfileLevel(profile = "High", level = "3.0"),
            parseVideoCodecProfileLevel("avc1.64001E")
        )
        assertEquals(
            VideoCodecProfileLevel(profile = "0", level = "1.0"),
            parseVideoCodecProfileLevel("vp09.00.10.08")
        )
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

    @Test
    fun resolveVideoPlayerWebSyncState_normalizesVolumeAndKeepsControlState() {
        assertEquals(
            VideoPlayerWebSyncState(
                isMuted = true,
                volume = 0f,
                areControlsVisible = false
            ),
            resolveVideoPlayerWebSyncState(
                volume = 0.8f,
                isMuted = true,
                areControlsVisible = false
            )
        )
        assertEquals(
            VideoPlayerWebSyncState(
                isMuted = false,
                volume = 1f,
                areControlsVisible = true
            ),
            resolveVideoPlayerWebSyncState(
                volume = 2f,
                isMuted = false,
                areControlsVisible = true
            )
        )
    }

    @Test
    fun shouldApplyVideoPlayerWebSyncState_skipsAppliedAndPendingStates() {
        val nextState = VideoPlayerWebSyncState(
            isMuted = false,
            volume = 0.9f,
            areControlsVisible = true
        )

        assertTrue(
            shouldApplyVideoPlayerWebSyncState(
                appliedState = null,
                pendingState = null,
                nextState = nextState
            )
        )
        assertFalse(
            shouldApplyVideoPlayerWebSyncState(
                appliedState = nextState,
                pendingState = null,
                nextState = nextState
            )
        )
        assertFalse(
            shouldApplyVideoPlayerWebSyncState(
                appliedState = null,
                pendingState = nextState,
                nextState = nextState
            )
        )
        assertTrue(
            shouldApplyVideoPlayerWebSyncState(
                appliedState = nextState,
                pendingState = null,
                nextState = nextState.copy(areControlsVisible = false)
            )
        )
    }
}
