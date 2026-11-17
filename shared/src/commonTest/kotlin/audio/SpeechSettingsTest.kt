package com.valoser.futacha.shared.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechSettingsTest {
    @Test fun corruptValuesCannotReachTheSpeechEngine() {
        for (invalid in listOf("NaN", "Infinity", "-1", "0", "2.1", "bad")) {
            assertEquals(SpeechSettings(), speechSettingsFromPreferences(mapOf(SPEECH_RATE_KEY to invalid, SPEECH_PITCH_KEY to invalid)))
        }
    }
    @Test fun storedVoiceAndSupportedRateRangeAreRestored() {
        assertEquals(SpeechSettings("voice-ja", 0.5f, 2f), speechSettingsFromPreferences(mapOf(
            SPEECH_VOICE_KEY to "voice-ja", SPEECH_RATE_KEY to "0.5", SPEECH_PITCH_KEY to "2.0")))
    }
    @Test fun slowSpeechReceivesMoreTimeWithoutRemovingTheUpperBound() {
        assertTrue(calculateTextSpeakerTimeoutMillis("あ".repeat(100), 0.5f) > calculateTextSpeakerTimeoutMillis("あ".repeat(100)))
        assertEquals(TEXT_SPEAKER_MAX_TIMEOUT_MS, calculateTextSpeakerTimeoutMillis("あ".repeat(10000), 0.5f))
    }
}
