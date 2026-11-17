package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FutaberVideoPosterTest {
    @Test fun absentPostersStayPlaceholders() {
        assertNull(futaberVideoPosterUrl(null))
        assertNull(futaberVideoPosterUrl("  "))
    }
    @Test fun videoUrlsCannotBeUsedAsImagePosters() {
        listOf("https://may.2chan.net/b/src/1.webm", "https://may.2chan.net/b/src/1.MP4?x=1",
            "file:///cache/1.webm#position").forEach { assertNull(futaberVideoPosterUrl(it)) }
    }
    @Test fun staticThumbnailUrlIsRetainedWithoutGuessingAnOriginal() {
        val thumbnail = "https://may.2chan.net/b/thumb/1s.jpg?x=1"
        assertEquals(thumbnail, futaberVideoPosterUrl(thumbnail))
    }
}
