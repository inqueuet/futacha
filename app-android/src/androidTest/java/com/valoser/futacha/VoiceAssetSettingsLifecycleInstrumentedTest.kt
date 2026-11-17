@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.audio.voicevox.*
import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.ui.compat.VoiceAssetSettings
import kotlinx.coroutines.*
import okio.*
import okio.Path.Companion.toPath
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Delayed local bytes exercise navigation/cancellation/deletion without network or audio. */
class VoiceAssetSettingsLifecycleInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val transfers = AtomicInteger()
    private lateinit var root: File
    private lateinit var assets: VoiceAssetStore
    private lateinit var runtime: SpeechRuntime
    private var showSettings by mutableStateOf(true)

    @Before fun prepare() {
        root = File(rule.activity.cacheDir, "audit-m18-${System.nanoTime()}")
        val platform = object : VoicePlatform {
            override val available = true
            override val unavailableReason = ""
            override fun directory() = root.absolutePath.toPath()
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession = error("no synthesis")
            override fun createPlayer(): VoicePcmPlayer = error("no audio")
        }
        assets = VoiceAssetStore(platform::directory, platform::availableBytes, downloader = {
            object : AssetDownloader {
                override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                    transfers.incrementAndGet()
                    sink.writeUtf8("partial"); sink.emit()
                    awaitCancellation()
                }
            }
        })
        runtime = SpeechRuntime(rule.activity, platform, assets)
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (showSettings) VoiceAssetSettings(runtime, SpeechSettings(), onEngine = {}, onStop = {})
                    else Text("別の画面")
                }
            }
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runtime.close()
        runBlocking { withTimeout(5_000) { assets.delete() } }
        root.deleteRecursively()
    }

    @Test fun downloadSurvivesLeavingSettingsAndPausedDataRemainsDeletableOnReturn() {
        rule.waitUntil(5_000) { assets.state.value.stage == VoiceAssetStage.MISSING }
        rule.onNodeWithTag("speech-zundamon-download").performScrollTo().performClick()
        rule.waitUntil(5_000) { assets.state.value.bytes == 7L }
        rule.runOnIdle { showSettings = false }
        rule.onNodeWithText("別の画面").assertIsDisplayed()
        rule.runOnIdle { showSettings = true }
        rule.onNodeWithText("中断").performScrollTo().assertIsDisplayed()
        assertTrue(assets.state.value.busy)
        assertEquals(1, transfers.get())
        rule.onNodeWithText("中断").performClick()
        rule.waitUntil(5_000) { assets.state.value.stage == VoiceAssetStage.PAUSED }
        rule.runOnIdle { showSettings = false }
        rule.runOnIdle { showSettings = true }
        rule.onNodeWithTag("speech-zundamon-download").performScrollTo().assertTextEquals("ダウンロードを再開")
        rule.onNodeWithTag("speech-zundamon-delete").performScrollTo().performClick()
        rule.onNodeWithText("削除").performClick()
        rule.waitUntil(5_000) { assets.state.value.stage == VoiceAssetStage.MISSING }
        assertFalse(root.exists())
        assertEquals(1, transfers.get())
    }
}
