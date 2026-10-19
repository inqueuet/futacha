@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.media.analysis.CpuInferenceContract
import kotlinx.coroutines.*
import okio.*
import okio.Path.Companion.toPath
import platform.Foundation.*
import kotlin.test.*

class IosVoicevoxIntegrationTest {
    /** Opt-in fixtures are outside the app and repository assets. The run record
     * must state whether FUTACHA_VOICE_TEST_ROOT was supplied. */
    @Test fun localPackSynthesizesValidPcmAlongsideExistingImageRuntime() {
        val path = assertNotNull(NSProcessInfo.processInfo.environment["FUTACHA_VOICE_TEST_ROOT"] as? String,
            "Supply the external voice fixtures explicitly; never count an absent fixture as a pass")
        val fixture = path.toPath()
        val root = fixture.resolve("installed-ios-test")
        val fs = FileSystem.SYSTEM
        fs.deleteRecursively(root, false)
        val real = createVoicePlatform(null)
        val platform = object : VoicePlatform by real {
            override fun directory() = root
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) {
                    val pcm = voicePcm(wav)
                    assertTrue(pcm.bytes > 24000)
                    assertTrue((pcm.offset until wav.size).any { wav[it] != 0.toByte() })
                }
                override fun stop() {}
                override fun close() {}
            }
            override fun createNative(pack: VoicePack) = IosVoiceNativeSession(
                fixture.resolve("voicevox_core.framework/voicevox_core").toString(),
                fixture.resolve("voicevox_onnxruntime.framework/voicevox_onnxruntime").toString(), pack)
        }
        var transfers = 0
        val assets = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { object : AssetDownloader {
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                transfers++
                val file = fixture.resolve(if (distribution == VoiceAssetManifest.model) "0.vvm" else "dictionary.tar.gz")
                fs.source(file).use { input ->
                    val buffer = Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer, 65536)
                        if (n == -1L) break
                        sink.write(buffer, n)
                    }
                }
            }
        } })
        val runtime = SpeechRuntime(null, platform, assets)
        val speaker = createTextSpeaker(null, runtime)
        try {
            runPumpingMain {
                assets.install()
                val image = CpuInferenceContract(fixture)
                image.nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
                speaker.configure(SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON))
                withTimeout(60_000) { speaker.speak("ふたちゃの読み上げ音声を確認しています。") }
                image.nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
                speaker.configure(SpeechSettings(rate = 1.5f, engine = SpeechEngineKind.VOICEVOX_ZUNDAMON, voicevoxPitch = 0.1f))
                withTimeout(60_000) { speaker.speak("速さと声の高さを変えます。😀") }
                assertEquals(2, transfers)
                runtime.stop(); assets.delete()
                assertFalse(fs.exists(root))
            }
        } finally { speaker.close(); runtime.close(); fs.deleteRecursively(root, false) }
    }
    private fun runPumpingMain(block: suspend () -> Unit) = runBlocking {
        val work = async(Dispatchers.Default) { block() }
        while (!work.isCompleted) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
        work.await()
    }
}
