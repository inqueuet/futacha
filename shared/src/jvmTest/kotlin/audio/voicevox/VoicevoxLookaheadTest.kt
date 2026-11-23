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

    private class Recording {
        val events = CopyOnWriteArrayList<String>()
        val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = "/lookahead".toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack) = object : VoiceNativeSession {
                override fun query(text: String) = "{\"t\":\"" + text + "\"}"
                override fun synthesize(query: String): ByteArray {
                    events += "make:" + query.substringAfter("\"t\":\"").substringBefore('"'); return ByteArray(48)
                }
                override fun close() {}
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) { events += "play"; delay(100) }
                override fun stop() {}
                override fun close() {}
            }
        }
        val access = VoiceAssetAccess()
        val engine = VoicevoxSpeechEngine(SpeechRuntime(null, platform)) {
            access.lease { VoicePack("/l/0.vvm".toPath(), "/l/dict".toPath()) }
        }
    }

    /** may 1449255847: the next post's first sentence is made while this post plays, and not made again. */
    @Test
    fun theNextPostsStartIsMadeWhileThisPostPlays() = runBlocking {
        val r = Recording()
        try {
            r.engine.prepare()
            r.engine.hintNext("次のレス。")
            r.engine.speak("今のレス。")
            assertEquals(setOf("make:今のレス。", "make:次のレス。", "play"), r.events.toSet(), r.events.toString())
            assertEquals("make:今のレス。", r.events.first())
            r.events.clear()
            r.engine.speak("次のレス。")
            assertEquals(listOf("play"), r.events.toList(), "the made-ahead sentence is played without making it again")
        } finally { r.engine.close() }
    }

    @Test
    fun aStopOrNewSettingsDropTheMadeAheadSentence() = runBlocking {
        val r = Recording()
        try {
            r.engine.prepare()
            r.engine.hintNext("次のレス。")
            r.engine.speak("今のレス。")
            r.engine.configure(SpeechSettings(rate = 1.5f))
            r.events.clear()
            r.engine.speak("次のレス。")
            assertEquals(listOf("make:次のレス。", "play"), r.events.toList(), "new settings make it again")

            r.engine.hintNext("三つ目。")
            r.engine.speak("今のレス。")
            r.engine.stop()
            r.engine.prepare()
            r.events.clear()
            r.engine.speak("三つ目。")
            assertEquals(listOf("make:三つ目。", "play"), r.events.toList(), "a stop makes it again")

            r.engine.hintNext("四つ目。")
            r.engine.speak("今のレス。")
            r.events.clear()
            r.engine.speak("別のレス。")
            assertEquals(listOf("make:別のレス。", "play"), r.events.toList(), "another post (a skip) is not given the made-ahead one")
        } finally { r.engine.close() }
    }

    @Test
    fun withoutAHintNothingIsMadeAhead() = runBlocking {
        val r = Recording()
        try {
            r.engine.prepare()
            r.engine.speak("今のレス。")
            assertEquals(listOf("make:今のレス。", "play"), r.events.toList())
        } finally { r.engine.close() }
    }
}
