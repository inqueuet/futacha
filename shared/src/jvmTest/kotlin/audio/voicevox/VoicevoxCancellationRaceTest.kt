package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.SpeechRuntime
import kotlinx.coroutines.*
import okio.Path.Companion.toPath
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class VoicevoxCancellationRaceTest {
    @Test fun stoppingDuringSynthesisDefersDeletionAndSuppressesLateAudio() = checkRace(duringCreate = false)
    @Test fun stoppingDuringNativeCreationClosesTheLateHandleBeforeDeletion() = checkRace(duringCreate = true)

    private fun checkRace(duringCreate: Boolean) = runBlocking {
        val entered = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val models = AtomicInteger()
        val closed = AtomicInteger()
        val played = AtomicInteger()
        val events = CopyOnWriteArrayList<String>()
        val access = VoiceAssetAccess()
        fun blockNative() { entered.countDown(); check(resume.await(10, TimeUnit.SECONDS)) }
        val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = "/race-fixture".toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession {
                val first = models.incrementAndGet() == 1
                if (first && duringCreate) blockNative()
                return object : VoiceNativeSession {
                    override fun query(text: String) = "{}"
                    override fun synthesize(query: String): ByteArray {
                        if (first && !duringCreate) blockNative()
                        return ByteArray(48)
                    }
                    override fun close() { closed.incrementAndGet(); events += "native-close" }
                }
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) { played.incrementAndGet() }
                override fun stop() {}
                override fun close() {}
            }
        }
        val runtime = SpeechRuntime(null, platform)
        val engine = VoicevoxSpeechEngine(runtime) {
            access.lease { VoicePack("/race/0.vvm".toPath(), "/race/dict".toPath()) }
        }
        try {
            engine.prepare()
            val reading = launch { engine.speak("停止する文章。") }
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            val deletion = async(start = CoroutineStart.UNDISPATCHED) { access.mutate { events += "delete" } }
            engine.stop()
            assertFalse(deletion.isCompleted, "native code still owns the mapped dictionary/model")
            assertEquals(0, closed.get())
            resume.countDown()
            withTimeout(5_000) { reading.join(); deletion.await() }
            assertEquals(listOf("native-close", "delete"), events.toList())
            assertEquals(0, played.get(), "a cancelled synchronous call must not emit its late PCM")
            engine.speak("新しい読み上げ。")
            assertEquals(2, models.get())
            assertEquals(1, played.get())
        } finally {
            resume.countDown()
            engine.close()
            withTimeout(5_000) { access.mutate {} }
        }
        assertEquals(2, closed.get())
    }
}
