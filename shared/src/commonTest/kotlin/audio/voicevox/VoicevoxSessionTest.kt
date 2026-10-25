package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import okio.Path.Companion.toPath
import kotlin.test.*

class VoicevoxSessionTest {
    @Test fun consecutiveRepliesReuseTheVerifiedPackAndNativeModelUntilStop() = runBlocking {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            engine.speak("最初のレス。")
            engine.configure(SpeechSettings(rate = 1.5f, voicevoxPitch = 0.1f))
            engine.speak("次のレス。")
            assertEquals(1, fixture.verifications.value, "verify the pack once per reading session")
            assertEquals(1, fixture.created.value, "load the dictionary/model once per reading session")
            assertEquals(0, fixture.released.value)
            assertEquals(0, fixture.closed.value)
            assertEquals(listOf(1f, 1.5f), fixture.rates.value)
            engine.stop()
            fixture.awaitReleased(1)
            assertEquals(1, fixture.closed.value)
            engine.speak("停止後の新しい読み上げ。")
            assertEquals(2, fixture.created.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun audioProtectionStaysHeldAcrossRepliesUntilTheUserStops() = runBlocking {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            engine.speak("最初のレス。")
            assertTrue(fixture.audioHeld.value, "the gap between replies must retain playback protection")
            engine.speak("次のレス。")
            assertEquals(0, fixture.audioReleases.value)
            engine.stop()
            assertFalse(fixture.audioHeld.value)
            assertEquals(1, fixture.audioReleases.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun anIdleModelIsClosedExactlyOnceWhenTheSpeakerCloses() = runBlocking {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            engine.speak("読み終わりました。")
            assertEquals(0, fixture.closed.value)
        } finally { engine.close(); engine.close() }
        fixture.awaitReleased(1)
        assertEquals(1, fixture.closed.value)
    }

    @Test fun aThreadAndSampleShareOneNativeModelAndCloseItAfterTheLastUser() = runBlocking {
        val fixture = Fixture()
        val thread = fixture.engine()
        val sample = fixture.engine()
        try {
            thread.prepare(); sample.prepare()
            thread.speak("スレッドの読み上げ。")
            sample.speak("試聴。")
            assertEquals(1, fixture.created.value, "do not load a second model for a settings sample")
            thread.stop()
            fixture.awaitReleased(1)
            assertEquals(0, fixture.closed.value, "the sample still owns the shared model")
            sample.stop()
            fixture.awaitReleased(2)
            assertEquals(1, fixture.closed.value)
        } finally { thread.close(); sample.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun cancellationReleasesTheModelAndNeverPlaysTheNextChunk() = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        fixture.onPlay = { entered.complete(Unit); awaitCancellation() }
        val engine = fixture.engine()
        try {
            engine.prepare()
            val reading = launch { engine.speak("最初の文章。次の文章。") }
            entered.await()
            reading.cancelAndJoin()
            fixture.awaitReleased(1)
            assertEquals(1, fixture.played.value)
            assertEquals(1, fixture.closed.value)
        } finally { engine.close() }
    }

    @Test fun seekInterruptsAudioAndReusesTheModelForTheNextReply() = runBlocking {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        fixture.onPlay = { entered.complete(Unit); awaitCancellation() }
        val engine = fixture.engine()
        try {
            engine.prepare()
            val reading = launch { engine.speak("移動する前のレス。") }
            entered.await()
            engine.interrupt()
            reading.join()
            assertEquals(0, fixture.closed.value)
            assertTrue(fixture.audioHeld.value)
            fixture.onPlay = {}
            engine.speak("移動した後のレス。")
            assertEquals(1, fixture.verifications.value)
            assertEquals(1, fixture.created.value)
            assertEquals(2, fixture.played.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun synthesisFailureReleasesResourcesAndTheNextReadingCanStart() = runBlocking {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            fixture.failSynthesis = true
            assertFailsWith<IllegalStateException> { engine.speak("エラー。") }
            fixture.awaitReleased(1)
            fixture.failSynthesis = false
            engine.speak("次の読み上げ。")
            assertEquals(2, fixture.created.value)
            assertEquals(1, fixture.played.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun unsupportedChunksDoNotStopReadableTextOrTheFollowingReply() = runBlocking<Unit> {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            engine.speak("先頭。한글\nʕ•ᴥ•ʔ\n後続。")
            assertEquals(2, fixture.played.value)
            engine.speak("次のレス。")
            assertEquals(3, fixture.played.value)
            assertEquals(1, fixture.created.value)
            assertEquals(0, fixture.released.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    @Test fun nativeFailuresOtherThanTextAnalysisStillFailAndReleaseTheModel() = runBlocking<Unit> {
        val fixture = Fixture()
        val engine = fixture.engine()
        try {
            engine.prepare()
            fixture.failQuery = true
            assertFailsWith<IllegalStateException> { engine.speak("普通の本文。") }
            fixture.awaitReleased(1)
            assertEquals(0, fixture.played.value)
            fixture.failQuery = false
            engine.speak("復帰後の本文。")
            assertEquals(1, fixture.played.value)
        } finally { engine.close(); fixture.awaitReleased(fixture.verifications.value) }
    }

    private class Fixture {
        val verifications = MutableStateFlow(0)
        val released = MutableStateFlow(0)
        val created = MutableStateFlow(0)
        val closed = MutableStateFlow(0)
        val played = MutableStateFlow(0)
        val rates = MutableStateFlow<List<Float>>(emptyList())
        val audioHeld = MutableStateFlow(false)
        val audioReleases = MutableStateFlow(0)
        var failSynthesis = false
        var failQuery = false
        var onPlay: suspend () -> Unit = {}
        private val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = "/unused-session-fixture".toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession {
                created.update { it + 1 }
                return object : VoiceNativeSession {
                    private var open = true
                    override fun query(text: String): String {
                        check(open)
                        check(!failQuery) { "native query failed" }
                        if (text.trim() in setOf("한글", "ʕ•ᴥ•ʔ")) throw VoiceTextAnalysisException("unreadable text")
                        return "{}"
                    }
                    override fun synthesize(query: String): ByteArray {
                        check(open)
                        check(!failSynthesis) { "synthesis failed" }
                        rates.update { it + Json.parseToJsonElement(query).jsonObject.getValue("speedScale").jsonPrimitive.float }
                        return ByteArray(48)
                    }
                    override fun close() { check(open); open = false; closed.update { it + 1 } }
                }
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) { audioHeld.value = true }
                override suspend fun play(wav: ByteArray) { played.update { it + 1 }; onPlay() }
                override fun interrupt() {}
                override fun stop() { if (audioHeld.compareAndSet(true, false)) audioReleases.update { it + 1 } }
                override fun close() { stop() }
            }
        }
        private val runtime = SpeechRuntime(null, platform)
        fun engine() = VoicevoxSpeechEngine(runtime) {
            verifications.update { it + 1 }
            VoicePackLease(VoicePack("/fixture/0.vvm".toPath(), "/fixture/dict".toPath())) { released.update { it + 1 } }
        }
        suspend fun awaitReleased(count: Int) { withTimeout(5_000) { while (released.value < count) delay(1) } }
    }
}
