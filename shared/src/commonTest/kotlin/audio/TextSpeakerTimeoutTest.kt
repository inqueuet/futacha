package com.valoser.futacha.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSpeakerTimeoutTest {
    @Test
    fun calculateTextSpeakerTimeoutMillis_scalesWithTextLengthAndCaps() {
        assertEquals(TEXT_SPEAKER_MIN_TIMEOUT_MS, calculateTextSpeakerTimeoutMillis(""))
        assertTrue(calculateTextSpeakerTimeoutMillis("あ".repeat(300)) > TEXT_SPEAKER_MIN_TIMEOUT_MS)
        assertEquals(TEXT_SPEAKER_MAX_TIMEOUT_MS, calculateTextSpeakerTimeoutMillis("あ".repeat(5_000)))
    }
}
