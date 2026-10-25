package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.audio.SpeechBackend
import com.valoser.futacha.shared.audio.SpeechEngineKind
import com.valoser.futacha.shared.audio.SpeechRuntime
import com.valoser.futacha.shared.audio.SpeechSettings
import com.valoser.futacha.shared.audio.SpeechVoice
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.audio.voicevox.VoiceAssetStore
import com.valoser.futacha.shared.audio.voicevox.VoiceNativeSession
import com.valoser.futacha.shared.audio.voicevox.VoicePack
import com.valoser.futacha.shared.audio.voicevox.VoicePackLease
import com.valoser.futacha.shared.audio.voicevox.VoicePcmPlayer
import com.valoser.futacha.shared.audio.voicevox.VoicePlatform
import com.valoser.futacha.shared.audio.voicevox.VoicevoxSpeechEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M73: Futacha's read-aloud wiring through the real bindings, TextSpeaker and VOICEVOX engine
 * (fake native/player only). Reading to the end must stop playback; a seek must only interrupt it.
 */
class FutachaReadAloudVoiceWiringTest {
    @Test fun readingToTheEndReleasesThePlaybackProtectionAndTheVoiceModel() = runBlocking {
        val voice = Voice()
        val speaker = TextSpeaker(null, null, systemEngine = { voice.engine })
        try {
            val harness = Harness(this, segments(2), speaker)
            harness.bindings.startReadAloud()
            requireNotNull(harness.state.job).join()
            assertEquals(2, voice.played.value)
            assertFalse(voice.audioHeld.value, "a finished reading must hand the audio session/focus back")
            assertEquals(1, voice.audioReleases.value)
            voice.awaitReleased(1)
            assertEquals(1, voice.closed.value, "the loaded model must not stay resident until the screen closes")
        } finally { speaker.close(); voice.awaitReleased(voice.verifications.value) }
    }

    @Test fun seekingWhileSpeakingKeepsTheVerifiedModelAndProtection() = runBlocking {
        val voice = Voice()
        val entered = CompletableDeferred<Unit>()
        voice.onPlay = { entered.complete(Unit); awaitCancellation() }
        val speaker = TextSpeaker(null, null, systemEngine = { voice.engine })
        try {
            val harness = Harness(this, segments(4), speaker)
            harness.bindings.startReadAloud()
            entered.await()
            voice.onPlay = {}
            harness.bindings.seekReadAloudToIndex(2, false)
            assertTrue(voice.audioHeld.value, "a seek keeps the foreground protection between replies")
            assertEquals(0, voice.audioReleases.value)
            requireNotNull(harness.state.job).join()
            assertEquals(3, voice.played.value, "p0 was interrupted, then p2 and p3 were read")
            assertEquals(listOf("p2", "p3"), voice.spoken.value)
            assertEquals(1, voice.verifications.value, "a seek must not verify the 166MB pack again")
            assertEquals(1, voice.created.value, "a seek must not reload the dictionary/model")
            assertFalse(voice.audioHeld.value)
        } finally { speaker.close(); voice.awaitReleased(voice.verifications.value) }
    }

    @Test fun seekingDoesNotRepeatTheSystemVoiceFallbackNotice() = runBlocking {
        val root = "/unused-futacha-read-aloud-notice".toPath()
        val platform = object : VoicePlatform {
            override val available = false
            override val unavailableReason = "test unsupported"
            override fun directory() = root
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession = error("must not load native voice")
            override fun createPlayer(): VoicePcmPlayer = error("must not play audio")
        }
        val runtime = SpeechRuntime(null, platform, VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { error("must not download") }))
        val entered = CompletableDeferred<Unit>()
        val system = object : SpeechBackend {
            val spoken = mutableListOf<String>()
            override val supportsPitch = true
            override suspend fun availableVoices() = emptyList<SpeechVoice>()
            override fun configure(settings: SpeechSettings) {}
            override suspend fun prepare() {}
            override suspend fun speak(text: String) {
                spoken += text
                if (spoken.size == 1) { entered.complete(Unit); awaitCancellation() }
            }
            override fun stop() {}
            override fun close() {}
        }
        val speaker = TextSpeaker(null, runtime, systemEngine = { system })
        try {
            speaker.configure(SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON))
            val harness = Harness(this, segments(3), speaker)
            harness.bindings.startReadAloud()
            entered.await()
            assertNotNull(runtime.notice.value)
            runtime.clearNotice()
            harness.bindings.seekReadAloudToIndex(1, false)
            requireNotNull(harness.state.job).join()
            assertEquals(listOf("p0", "p1", "p2"), system.spoken)
            assertNull(runtime.notice.value, "moving the reading position is not a new reading session")
        } finally { speaker.close(); runtime.close() }
    }

    private fun segments(count: Int) = List(count) { ReadAloudSegment(it, "p$it", "p$it") }

    /** The same wiring as ThreadScreen: cancel = runtime stop binding, interrupt = TextSpeaker.interrupt. */
    private class Harness(scope: CoroutineScope, segments: List<ReadAloudSegment>, speaker: TextSpeaker) {
        var state = ThreadReadAloudRuntimeState(null, ReadAloudStatus.Idle, 0, false)
        private val stateBindings = ThreadScreenReadAloudStateBindings({ state }, { state = it })
        private val runtime = buildThreadReadAloudRuntimeBindings(stateBindings.currentState, stateBindings.setState, speaker::stop)
        val bindings = buildThreadScreenReadAloudBindings(
            scope,
            stateBindings,
            ThreadScreenReadAloudCallbacks(
                showMessage = {},
                showOptionalMessage = {},
                scrollToPostIndex = {},
                speakText = speaker::speak,
                cancelActiveReadAloud = runtime.cancelActive,
                interruptActiveReadAloud = { interruptThreadReadAloud(stateBindings, speaker::interrupt) }
            ),
            ThreadScreenReadAloudDependencies(currentSegments = { segments })
        )
    }

    private class Voice {
        val verifications = MutableStateFlow(0)
        val released = MutableStateFlow(0)
        val created = MutableStateFlow(0)
        val closed = MutableStateFlow(0)
        val played = MutableStateFlow(0)
        val spoken = MutableStateFlow<List<String>>(emptyList())
        val audioHeld = MutableStateFlow(false)
        val audioReleases = MutableStateFlow(0)
        var onPlay: suspend () -> Unit = {}
        private var lastQuery = ""
        private val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = "/unused-futacha-read-aloud-voice".toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession {
                created.update { it + 1 }
                return object : VoiceNativeSession {
                    private var open = true
                    override fun query(text: String): String { check(open); lastQuery = text.trim(); return "{}" }
                    override fun synthesize(query: String): ByteArray { check(open); return ByteArray(48) }
                    override fun close() { check(open); open = false; closed.update { it + 1 } }
                }
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) { audioHeld.value = true }
                override suspend fun play(wav: ByteArray) {
                    played.update { it + 1 }
                    val text = lastQuery
                    onPlay()
                    spoken.update { it + text }
                }
                override fun interrupt() {}
                override fun stop() { if (audioHeld.compareAndSet(true, false)) audioReleases.update { it + 1 } }
                override fun close() { stop() }
            }
        }
        private val runtime = SpeechRuntime(null, platform)
        val engine = VoicevoxSpeechEngine(runtime) {
            verifications.update { it + 1 }
            VoicePackLease(VoicePack("/fixture/futacha-read-aloud.vvm".toPath(), "/fixture/dict".toPath())) { released.update { it + 1 } }
        }
        suspend fun awaitReleased(count: Int) { withTimeout(5_000) { while (released.value < count) delay(1) } }
    }
}
