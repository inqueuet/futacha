package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MediaUrlSupportTest {
    @Test
    fun parseMediaUrlInfo_extracts_expected_metadata() {
        val info = parseMediaUrlInfo("https://example.com/src/sample.GIF?token=1#frag")

        assertNotNull(info)
        assertEquals("https://example.com/src/sample.GIF", info.normalizedUrl)
        assertEquals("sample.GIF", info.fileName)
        assertEquals("gif", info.extension)
        assertEquals(MediaType.Image, info.mediaType)
        assertTrue(info.isGif)
    }

    @Test
    fun parseMediaUrlInfo_detects_video_extensions() {
        val info = parseMediaUrlInfo("https://example.com/src/movie.webm")

        assertNotNull(info)
        assertEquals(MediaType.Video, info.mediaType)
        assertFalse(info.isGif)
    }

    @Test
    fun parseMediaUrlInfo_returns_null_for_blank_input() {
        assertEquals(null, parseMediaUrlInfo(" "))
        assertEquals(null, parseMediaUrlInfo(null))
    }
}
