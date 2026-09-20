package com.valoser.futacha.shared.ui.board

import kotlin.test.*

class WebVideoPlaybackSupportTest {
    @Test fun webmOsBoundaryDoesNotUseDeviceModel() {
        assertFalse(supportsIosWebmPath(15, 9)); assertFalse(supportsIosWebmPath(17, 3))
        assertTrue(supportsIosWebmPath(17, 4)); assertTrue(supportsIosWebmPath(18, 0))
    }

    @Test fun fallbackOnlyUsesExplicitDistinctAppleContainerUrls() {
        val url = "https://example.test/video.webm"
        assertEquals(listOf(url), videoPlaybackSources(url, emptyList()))
        assertEquals(listOf(url, "https://cdn.test/alternate.mp4?token=1", "file:///local/test.mov"), videoPlaybackSources(url,
            listOf(url, "javascript:evil.mp4", "https://example.test/a.webm", "https://cdn.test/alternate.mp4?token=1",
                "https://cdn.test/alternate.mp4?token=1", "file:///local/test.mov")))
    }

    @Test fun trackHeadersDetermineActualCodecsWithoutGuessingAudioOrProfile() {
        for (video in listOf("V_VP8", "V_VP9")) for (audio in listOf("A_OPUS", "A_VORBIS")) {
            val info = assertNotNull(readWebmPlaybackInfo(fixture(video, audio)))
            assertEquals(video, info.videoCodec); assertEquals(audio, info.audioCodec)
            assertEquals(320, info.width); assertEquals(240, info.height)
            assertEquals(2, info.channels); assertEquals(48000, info.sampleRate)
            assertEquals("video/webm; codecs=\"${if (video == "V_VP8") "vp8" else "vp9"}, ${if (audio == "A_OPUS") "opus" else "vorbis"}\"", info.mimeType)
            assertNull(info.mediaInfo().profile)
        }
        assertEquals("video/webm; codecs=\"vp9\"", readWebmPlaybackInfo(fixture("V_VP9", null))?.mimeType)
        assertEquals("video/webm", readWebmPlaybackInfo(fixture("V_AV1", "A_UNKNOWN"))?.mimeType)
    }

    @Test fun malformedAndOversizedTrackHeadersStayUnknown() {
        assertNull(readWebmPlaybackInfo("broken webm".encodeToByteArray()))
        assertNull(readWebmPlaybackInfo(ByteArray(WEBM_TRACK_PROBE_BYTES + 1)))
        val bytes = fixture("V_VP9", "A_OPUS")
        for (end in 0..15) assertNull(readWebmPlaybackInfo(bytes.copyOf(end)))
        // Arbitrarily large declared segment sizes cannot allocate or overflow the prefix.
        val unknown = ebml(0x1a45dfa3, ebml(0x4282, "webm".encodeToByteArray())) +
            byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67, 0x01, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xfe.toByte())
        assertNull(readWebmPlaybackInfo(unknown))
    }

    private fun fixture(video: String, audio: String?): ByteArray {
        val v = ebml(0xae, ebml(0x83, byteArrayOf(1)) + ebml(0x86, video.encodeToByteArray()) +
            ebml(0xe0, ebml(0xb0, byteArrayOf(1, 64)) + ebml(0xba, byteArrayOf(0xf0.toByte()))))
        val a = audio?.let { ebml(0xae, ebml(0x83, byteArrayOf(2)) + ebml(0x86, it.encodeToByteArray()) +
            ebml(0xe1, ebml(0x9f, byteArrayOf(2)) + ebml(0xb5, byteArrayOf(0x47, 0x3b, 0x80.toByte(), 0)))) } ?: byteArrayOf()
        return ebml(0x1a45dfa3, ebml(0x4282, "webm".encodeToByteArray())) + ebml(0x18538067, ebml(0x1654ae6b, v + a))
    }
    private fun ebml(id: Int, bytes: ByteArray): ByteArray {
        val all = byteArrayOf((id ushr 24).toByte(), (id ushr 16).toByte(), (id ushr 8).toByte(), id.toByte())
        return all.dropWhile { it == 0.toByte() }.toByteArray() +
            (if (bytes.size < 127) byteArrayOf((0x80 or bytes.size).toByte()) else byteArrayOf(0x40, bytes.size.toByte())) + bytes
    }
}
