package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.SpeechRuntime
import com.valoser.futacha.shared.audio.SpeechSettings
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class VoicevoxLookaheadTest {
    @Test
    fun theNextSentenceIsMadeWhileTheCurrentOnePlays() = runBlocking {
        val events = CopyOnWriteArrayList<String>()
        val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = "/lookahead".toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack) = object : VoiceNativeSession {
                override fun query(text: String) = "{}"
                override fun synthesize(query: String): ByteArray { events += "make"; return ByteArray(48) }
                override fun close() {}
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) { events += "play-start"; delay(200); events += "play-end" }
                override fun stop() {}
                override fun close() {}
            }
        }
        val access = VoiceAssetAccess()
        val engine = VoicevoxSpeechEngine(SpeechRuntime(null, platform)) {
            access.lease { VoicePack("/l/0.vvm".toPath(), "/l/dict".toPath()) }
        }
        try {
            engine.prepare()
            engine.speak("一つ目の文。".repeat(30) + "二つ目の文。".repeat(30))
        } finally { engine.close() }
        val firstEnd = events.indexOf("play-end")
        assertTrue(events.count { it == "make" } >= 2, events.toString())
        assertTrue(events.lastIndexOf("make") < events.lastIndexOf("play-end"), events.toString())
        assertTrue(events.subList(0, firstEnd).count { it == "make" } >= 2, "the second sentence waits for the first to end: $events")
    }

    @Test
    fun thePausesAroundASentenceAreShort() {
        val overrides = voicevoxQueryOverrides(SpeechSettings())
        assertEquals(VOICEVOX_EDGE_PAUSE_SECONDS, overrides.getValue("prePhonemeLength").content.toFloat())
        assertEquals(VOICEVOX_EDGE_PAUSE_SECONDS, overrides.getValue("postPhonemeLength").content.toFloat())
        assertTrue(VOICEVOX_EDGE_PAUSE_SECONDS < 0.1f)
    }
}
