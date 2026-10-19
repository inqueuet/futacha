@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.audio.voicevox.*
import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.media.analysis.CpuInferenceContract
import com.valoser.futacha.shared.ui.compat.VoiceAssetSettings
import kotlinx.coroutines.*
import okio.*
import okio.Path.Companion.toPath
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoicevoxInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.noBackupFilesDir, "voice-test-${System.nanoTime()}")
    private val arguments = InstrumentationRegistry.getArguments()
    private val fixtures = arguments.getString("voiceFixtureRoot")?.let(::File)
        ?: File(context.getExternalFilesDir(null), "voice-fixtures")
    private val transfers = AtomicInteger()
    private val platform: VoicePlatform by lazy {
        val real = createVoicePlatform(context)
        object : VoicePlatform by real { override fun directory() = root.absolutePath.toPath() }
    }
    private val assets: VoiceAssetStore by lazy { VoiceAssetStore({ platform.directory() }, { Long.MAX_VALUE }, downloader = {
        object : AssetDownloader {
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                transfers.incrementAndGet()
                val file = File(fixtures, if (distribution == VoiceAssetManifest.model) "0.vvm" else "dictionary.tar.gz")
                file.source().use { source ->
                    val buffer = Buffer()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = source.read(buffer, 65536)
                        if (n == -1L) break
                        sink.write(buffer, n)
                    }
                }
            }
        }
    }) }
    private var runtime: SpeechRuntime? = null
    @Before fun checkRequestedMute() {
        if (arguments.getString("voiceRequireMuted") == "true") {
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            assertEquals("Physical-device checks must remain silent", 0, audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
        }
    }
    @After fun cleanup() { runtime?.close(); root.deleteRecursively() }
    private fun runtime(): SpeechRuntime = SpeechRuntime(context, platform, assets).also { runtime = it }

    @Test fun explicitDownloadSelectDeleteAndDownloadAgain() {
        runtime()
        var engine by mutableStateOf(SpeechSettings())
        rule.runOnUiThread { rule.activity.setContent {
            MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                VoiceAssetSettings(requireNotNull(this@VoicevoxInstrumentedTest.runtime), engine, onEngine = {
                    engine = speechSettingsFromPreferences(mapOf(SPEECH_ENGINE_KEY to it))
                }, onStop = {})
            } }
        } }
        rule.waitUntil(10_000) { assets.state.value.stage == VoiceAssetStage.MISSING }
        assertEquals(0, transfers.get())
        rule.onNodeWithTag("speech-zundamon-select").assertIsNotEnabled()
        rule.onNodeWithTag("speech-zundamon-download").performScrollTo().performClick()
        rule.waitUntil(120_000) { assets.state.value.ready || assets.state.value.stage == VoiceAssetStage.FAILED }
        assertEquals(assets.state.value.toString(), VoiceAssetStage.READY, assets.state.value.stage)
        assertEquals(2, transfers.get())
        assertEquals(SpeechEngineKind.SYSTEM, engine.engine)
        rule.onNodeWithTag("speech-zundamon-select").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(SpeechEngineKind.VOICEVOX_ZUNDAMON, engine.engine) }
        rule.onNodeWithTag("speech-zundamon-delete").performScrollTo().performClick()
        rule.onNodeWithText("削除", useUnmergedTree = true).performClick()
        rule.waitUntil(10_000) { assets.state.value.stage == VoiceAssetStage.MISSING }
        assertFalse(root.exists())
        rule.onNodeWithTag("speech-zundamon-download").performScrollTo().performClick()
        rule.waitUntil(120_000) { assets.state.value.ready }
        assertEquals(4, transfers.get())
    }
    @Test fun bundledEngineSynthesizesAndPlaysOfflineAlongsideImageInference() = runBlocking {
        runtime()
        assets.install()
        val image = CpuInferenceContract(context.cacheDir.absolutePath.toPath())
        image.nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
        val speaker = createTextSpeaker(context, requireNotNull(this@VoicevoxInstrumentedTest.runtime))
        try {
            speaker.configure(SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON))
            withTimeout(60_000) { speaker.speak("ふたちゃの読み上げ音声を確認しています。") }
            image.nativeExecutionReusesCallerBuffersAndSurvivesInvalidRequests()
            speaker.configure(SpeechSettings(rate = 1.5f, engine = SpeechEngineKind.VOICEVOX_ZUNDAMON, voicevoxPitch = 0.1f))
            withTimeout(60_000) { speaker.speak("速度と声の高さを変えて再生します。😀") }
            assertEquals("Playback must never download", 2, transfers.get())
        } finally { speaker.close() }
    }
    @Test fun cancellingSynchronousSynthesisSuppressesLateAudioAndDefersDeletion() = runBlocking {
        assets.install()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = AtomicInteger()
        val played = AtomicInteger()
        val fake = object : VoicePlatform by platform {
            override fun createNative(pack: VoicePack) = object : VoiceNativeSession {
                override fun query(text: String) = "{}"
                override fun synthesize(query: String): ByteArray {
                    entered.countDown(); check(release.await(10, TimeUnit.SECONDS)); return ByteArray(44)
                }
                override fun close() { closed.incrementAndGet() }
            }
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) {}
                override suspend fun play(wav: ByteArray) { played.incrementAndGet() }
                override fun stop() {}
                override fun close() {}
            }
        }
        val runtime = SpeechRuntime(context, fake, assets).also { this@VoicevoxInstrumentedTest.runtime = it }
        val speaker = createTextSpeaker(context, runtime)
        speaker.configure(SpeechSettings(engine = SpeechEngineKind.VOICEVOX_ZUNDAMON))
        try {
            val speech = launch(Dispatchers.Default) { speaker.speak("取り消し") }
            assertTrue(entered.await(15, TimeUnit.SECONDS))
            speaker.stop()
            val deletion = async(Dispatchers.Default) { assets.delete() }
            delay(150); assertFalse(deletion.isCompleted)
            release.countDown(); speech.join(); deletion.await()
            assertEquals(1, closed.get()); assertEquals(0, played.get()); assertFalse(root.exists())
        } finally { release.countDown(); speaker.close() }
    }
}
