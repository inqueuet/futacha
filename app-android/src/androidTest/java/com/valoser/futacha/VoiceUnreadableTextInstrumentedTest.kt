@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.audio.SpeechRuntime
import com.valoser.futacha.shared.audio.voicevox.*
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/** Uses explicit local public fixtures and real query/synthesis, with a silent PCM sink. */
class VoiceUnreadableTextInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun pack(): VoicePack {
        val root = requireNotNull(context.getExternalFilesDir(null)).resolve("audit-m19/pack").absolutePath.toPath()
        return VoicePack(root.resolve("0.vvm"), root.resolve("dict"))
    }
    @Test fun unreadableTextHasASpecificAnalysisFailure() = runBlocking<Unit> {
        withContext(voiceNativeDispatcher) {
            val native = createVoicePlatform(context).createNative(pack())
            try {
                assertTrue(native.query("普通の本文です。").contains("accent_phrases"))
                for (text in listOf("한글", "ʕ•ᴥ•ʔ")) {
                    val failure = runCatching { native.query(text) }.exceptionOrNull()
                    android.util.Log.i("AuditM19", "query=$text failure=${failure?.javaClass?.simpleName}: ${failure?.message}")
                    assertTrue("$text: $failure", failure is VoiceTextAnalysisException)
                }
            } finally { native.close() }
        }
    }
    @Test fun readableChunksContinueThroughRealUnsupportedTextWithoutPlayingAudio() = runBlocking<Unit> {
        val played = AtomicInteger()
        val closed = CompletableDeferred<Unit>()
        val real = createVoicePlatform(context)
        val platform = object : VoicePlatform by real {
            override fun createNative(pack: VoicePack): VoiceNativeSession {
                val native = real.createNative(pack)
                return object : VoiceNativeSession by native {
                    override fun close() { native.close(); closed.complete(Unit) }
                }
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) {
                    assertTrue(voicePcm(wav).bytes > 0)
                    played.incrementAndGet()
                }
                override fun stop() {}
                override fun close() {}
            }
        }
        val runtime = SpeechRuntime(context, platform)
        val engine = VoicevoxSpeechEngine(runtime) { VoicePackLease(pack()) {} }
        try {
            engine.prepare()
            withTimeout(60_000) { engine.speak("先頭の本文。한글\nʕ•ᴥ•ʔ\n後ろの本文。") }
            assertEquals(2, played.get())
            withTimeout(60_000) { engine.speak("次のレスです。") }
            assertEquals(3, played.get())
        } finally {
            engine.close(); runtime.close()
            withTimeout(10_000) { closed.await() }
        }
    }
}
