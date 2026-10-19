package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import kotlinx.coroutines.runBlocking
import okio.*
import kotlin.random.Random
import kotlin.test.*

class VoicevoxContractsTest {
    @Test fun previousPreferencesKeepTheirSystemVoiceAndHeight() {
        val old = mapOf(SPEECH_VOICE_KEY to "ja-original", SPEECH_RATE_KEY to "1.5", SPEECH_PITCH_KEY to "0.7")
        val settings = speechSettingsFromPreferences(old)
        assertEquals(SpeechEngineKind.SYSTEM, settings.engine)
        val voice = speechSettingsFromPreferences(old + (SPEECH_ENGINE_KEY to "voicevox_zundamon"))
        assertEquals("ja-original", voice.voiceId)
        assertEquals(0.7f, voice.pitch)
        assertEquals(0f, voice.voicevoxPitch)
        assertEquals(settings, speechSettingsFromPreferences(old + (SPEECH_ENGINE_KEY to "future_unknown")))
    }
    @Test fun voiceHeightRejectsNonFiniteAndOutOfRangeInput() {
        for (input in listOf("NaN", "Infinity", "-Infinity", "0.16", "-0.16", "bad")) {
            assertEquals(0f, speechSettingsFromPreferences(mapOf(SPEECH_VOICEVOX_PITCH_KEY to input)).voicevoxPitch)
        }
        assertEquals(-0.15f, speechSettingsFromPreferences(mapOf(SPEECH_VOICEVOX_PITCH_KEY to "-0.15")).voicevoxPitch)
    }
    @Test fun longJapaneseAndSupplementaryCharactersAreNeverDroppedOrSplit() {
        val text = "あいうえお😀𠮷。".repeat(200) + "終わり\n" + "漢字".repeat(500)
        val chunks = voicevoxTextChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.size > 10)
        for (chunk in chunks) {
            assertFalse(chunk.first().isLowSurrogate())
            assertFalse(chunk.last().isHighSurrogate())
            assertTrue(chunk.count { !it.isLowSurrogate() } <= 128)
        }
    }
    @Test fun symbolOnlySentencesAreSilentWhileSupplementaryJapaneseRemainsReadable() {
        assertFalse(voicevoxHasSpokenText("😀。…> \n"))
        assertTrue(voicevoxHasSpokenText("𠮷"))
        assertTrue(voicevoxHasSpokenText("二日目🌱"))
        assertTrue(voicevoxHasSpokenText("123"))
        val text = "普通の文章。😀次の返信。"
        assertEquals(text, voicevoxTextChunks(text).joinToString(""))
    }
    @Test fun nativeStringsCannotBeTruncatedByEmbeddedNul() {
        assertEquals("前 後😀", voicevoxTextChunks("前\u0000後😀").joinToString(""))
        assertTrue(voicevoxTextChunks("").isEmpty())
    }
    @Test fun wavParserAcceptsPcmAndRejectsTruncationOrUnsupportedAudio() {
        val wav = wav()
        assertEquals(VoicePcm(44, 4, 24000, 1), voicePcm(wav))
        assertFails { voicePcm(wav.copyOf(46)) }
        assertFails { voicePcm(wav.copyOf().also { it[22] = 2 }) }
        assertFails { voicePcm(wav.copyOf().also { it[40] = 3 }) }
        assertFails { voicePcm(wav.copyOf().also { it[20] = 3 }) }
        assertFails { voicePcm(wav.copyOf().also { it[43] = 127 }) }
    }
    @Test fun noDownloadOrNativeWorkOccursWhileCheckingMissingAssets() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-empty-${Random.nextLong()}")
        val store = VoiceAssetStore({ root }, { error("must not check storage") }, downloader = { error("no HTTP") })
        try {
            store.refresh()
            assertEquals(VoiceAssetStage.MISSING, store.state.value.stage)
            assertFalse(FileSystem.SYSTEM.exists(root))
            assertFails { store.withVerifiedPack { error("must not call native") } }
            store.delete()
            assertEquals(VoiceAssetStage.MISSING, store.state.value.stage)
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    private fun wav(): ByteArray = Buffer().apply {
        writeUtf8("RIFF"); writeIntLe(40); writeUtf8("WAVEfmt "); writeIntLe(16)
        writeShortLe(1); writeShortLe(1); writeIntLe(24000); writeIntLe(48000)
        writeShortLe(2); writeShortLe(16); writeUtf8("data"); writeIntLe(4); writeIntLe(123)
    }.readByteArray()
}
