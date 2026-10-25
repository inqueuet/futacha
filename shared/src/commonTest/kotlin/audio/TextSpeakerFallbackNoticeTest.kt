package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.audio.voicevox.*
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.*

/** Exercises real routing with a silent backend; no audio, network, or voice model. */
class TextSpeakerFallbackNoticeTest {
    @Test fun consecutiveRepliesNotifyOnlyOnceForBothFallbackReasons() = runBlocking<Unit> {
        for (available in listOf(false, true)) {
            val f = Fixture(available)
            try {
                f.speaker.configure(f.voiceSettings)
                f.speaker.prepare()
                assertNotNull(f.runtime.notice.value)
                f.runtime.clearNotice() // The notice host expires the first notice.
                repeat(3) { f.speaker.speak("本文$it") }
                assertEquals(3, f.backend.spoken)
                assertNull(f.runtime.notice.value)
            } finally { f.close() }
        }
    }

    @Test fun stoppingAllowsANewReadingToExplainTheFallbackAgain() = runBlocking<Unit> {
        val f = Fixture(false)
        try {
            f.speaker.configure(f.voiceSettings)
            f.speaker.speak("最初")
            assertNotNull(f.runtime.notice.value)
            f.runtime.clearNotice()
            f.speaker.stop()
            f.speaker.speak("次の読み上げ")
            assertNotNull(f.runtime.notice.value)
            assertEquals(2, f.backend.spoken)
        } finally { f.close() }
    }

    @Test fun seekingAndUpdatingRateDoNotRestartTheFallbackNotice() = runBlocking<Unit> {
        val f = Fixture(false)
        try {
            f.speaker.configure(f.voiceSettings)
            f.speaker.speak("最初")
            f.runtime.clearNotice()
            f.speaker.interrupt()
            f.speaker.configure(f.voiceSettings.copy(rate = 1.5f))
            f.speaker.speak("移動後")
            assertNull(f.runtime.notice.value)
            assertEquals(1.5f, f.backend.settings.rate)
        } finally { f.close() }
    }

    @Test fun explicitSystemSpeechIsSilentAndSwitchingEngineBeginsANewSession() = runBlocking<Unit> {
        val f = Fixture(false)
        try {
            f.speaker.speak("端末の音声")
            assertNull(f.runtime.notice.value)
            f.speaker.configure(f.voiceSettings)
            f.speaker.speak("ずんだもんの代替")
            assertNotNull(f.runtime.notice.value)
            f.runtime.clearNotice()
            f.speaker.configure(SpeechSettings())
            f.speaker.speak("端末の音声")
            assertNull(f.runtime.notice.value)
            f.speaker.configure(f.voiceSettings)
            f.speaker.speak("新しい代替")
            assertNotNull(f.runtime.notice.value)
        } finally { f.close() }
    }

    private class SilentBackend : SpeechBackend {
        var spoken = 0
        var settings = SpeechSettings()
        override val supportsPitch = true
        override suspend fun availableVoices() = emptyList<SpeechVoice>()
        override fun configure(settings: SpeechSettings) { this.settings = settings }
        override suspend fun prepare() {}
        override suspend fun speak(text: String) { spoken++ }
        override fun stop() {}
        override fun close() {}
    }
    private class Fixture(available: Boolean) {
        private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("speech-notice-${Random.nextLong()}")
        private val platform = object : VoicePlatform {
            override val available = available
            override val unavailableReason = "test unsupported"
            override fun directory(): Path = root
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession = error("must not load native voice")
            override fun createPlayer(): VoicePcmPlayer = error("must not play audio")
        }
        val runtime = SpeechRuntime(null, platform, VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { error("must not download") }))
        val backend = SilentBackend()
        val speaker = TextSpeaker(null, runtime, systemEngine = { backend })
        val voiceSettings = SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON)
        fun close() { speaker.close(); runtime.close(); FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
}
