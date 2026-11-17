@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.audio.voicevox.*
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.ui.compat.CompatSpeechSettingsScreen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.*
import okio.Path.Companion.toPath
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual shared settings + actual voice engine cancellation; no network, synthesis or PCM. */
class SpeechSettingsRecoveryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var root: File
    private lateinit var runtime: SpeechRuntime
    private val prepared = AtomicBoolean()
    private val preferencesRendered = AtomicBoolean()
    private var showSettings by mutableStateOf(true)
    private lateinit var database: String
    private val releaseWrite = CompletableDeferred<Unit>()
    private val releaseDelete = CountDownLatch(1)

    private fun open(playerPrepare: suspend (() -> Unit) -> Unit, supported: Boolean = true,
        fileSystem: FileSystem = FileSystem.SYSTEM, decorate: (CompatibilityStore) -> CompatibilityStore = { it },
        downloaded: Boolean = true) {
        root = File(rule.activity.cacheDir, "voice-settings-${System.nanoTime()}")
        database = "voice-settings-${System.nanoTime()}.db"
        storage = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { storage.initialize(); storage.savePreference(SPEECH_ENGINE_KEY, "voicevox_zundamon") }
        val directory = root.absolutePath.toPath()
        val expectedSizes = mutableMapOf<Path, Long>()
        if (downloaded) {
            val pack = directory.resolve(VoiceAssetManifest.PACK)
            expectedSizes[pack.resolve("0.vvm")] = VoiceAssetManifest.model.bytes
            VoiceAssetManifest.files.forEach { expectedSizes[pack.resolve("dict").resolve(it.name)] = it.bytes }
            expectedSizes.forEach { (path, _) ->
                FileSystem.SYSTEM.createDirectories(path.parent!!)
                FileSystem.SYSTEM.write(path) { writeByte(0) }
            }
        } else {
            FileSystem.SYSTEM.createDirectories(directory)
            FileSystem.SYSTEM.write(directory.resolve("partial")) { writeUtf8("retained") }
        }
        // Synthetic declared-size-correct bytes select VOICEVOX. Every test stops
        // in prepare(), before verification/native creation, so audio cannot run.
        val metadataFs = object : ForwardingFileSystem(fileSystem) {
            override fun metadataOrNull(path: Path): FileMetadata? {
                val actual = super.metadataOrNull(path) ?: return null
                return expectedSizes[path]?.let { FileMetadata(isRegularFile = true, isDirectory = false, size = it) } ?: actual
            }
        }
        val platform = object : VoicePlatform {
            override val available = supported
            override val unavailableReason = "この端末はずんだもんに対応していません"
            override fun directory() = directory
            override fun availableBytes() = Long.MAX_VALUE
            override fun createNative(pack: VoicePack): VoiceNativeSession = error("No synthesis in settings regression")
            override fun createPlayer() = object : VoicePcmPlayer {
                override suspend fun prepare(onStop: () -> Unit) { prepared.set(true); playerPrepare(onStop) }
                override suspend fun play(wav: ByteArray): Unit = error("No audio in settings regression")
                override fun stop() = Unit
                override fun close() = Unit
            }
        }
        runtime = SpeechRuntime(rule.activity, platform, VoiceAssetStore(platform::directory, platform::availableBytes,
            fileSystem = metadataFs, downloader = { error("No network in settings regression") }))
        val store = decorate(storage)
        rule.setContent {
            val preferences by storage.preferences.collectAsState(emptyMap())
            // Read during composition so this scope recomposes (and the effect runs again) when the value arrives.
            val restoredEngine = preferences[SPEECH_ENGINE_KEY]
            SideEffect { preferencesRendered.set(restoredEngine == "voicevox_zundamon") }
            MaterialTheme {
                CompositionLocalProvider(LocalSpeechRuntime provides runtime) {
                    if (showSettings) CompatSpeechSettingsScreen(store, preferences, onBack = { showSettings = false })
                    else Text("別の画面")
                }
            }
        }
        rule.waitUntil(5_000) { preferencesRendered.get() }
        if (downloaded && supported) {
            rule.waitUntil(5_000) { runtime.assets.state.value.ready }
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("speech-zundamon-pitch").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @After fun cleanup() {
        releaseWrite.complete(Unit)
        releaseDelete.countDown()
        if (::runtime.isInitialized) {
            rule.runOnUiThread { rule.activity.setContent {} }
            runtime.close()
            runBlocking { withTimeout(5_000) { runtime.assets.delete() } }
        }
        if (::root.isInitialized) root.deleteRecursively()
        if (::database.isInitialized) rule.activity.deleteDatabase(database)
    }

    @Test fun notificationStopDoesNotShowATimeout() {
        open(playerPrepare = { stop -> stop(); awaitCancellation() })
        rule.onNodeWithText("試聴").performScrollTo().performClick()
        rule.waitUntil(5_000) { prepared.get() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("試聴を停止").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("試聴").assertIsDisplayed()
        rule.onNodeWithTag("speech-error").assertDoesNotExist()
        rule.onNodeWithTag("speech-sample-finished").assertDoesNotExist()
    }

    @Test fun anActualTimeoutStillExplainsWhyTheSampleStopped() {
        open(playerPrepare = { withTimeout(1) { awaitCancellation() } })
        rule.onNodeWithText("試聴").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("speech-error").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("speech-error").performScrollTo().assertTextEquals("試聴がタイムアウトしました")
        rule.onNodeWithTag("speech-sample-finished").assertDoesNotExist()
    }
    @Test fun unsupportedVoicevoxShowsTheEffectiveSystemSelectionWithoutOverwritingTheSavedChoice() {
        open(playerPrepare = { error("Unsupported device must not start voice playback") }, supported = false)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("この端末はずんだもんに対応していません").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(isSelectable()).onFirst().assertIsSelected()
        rule.onNodeWithText("端末の既定音声").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("speech-zundamon-pitch").assertDoesNotExist()
        assertEquals("voicevox_zundamon", runBlocking { storage.preferences.first()[SPEECH_ENGINE_KEY] })
        assertFalse(prepared.get())
    }

    @Test fun leavingDuringTheSystemPreferenceCommitStillFinishesBothPartsOfDeletion() {
        val writing = CompletableDeferred<Unit>()
        open(playerPrepare = { error("No playback during deletion") }, downloaded = false, decorate = { original ->
            object : CompatibilityStore by original {
                override suspend fun savePreference(key: String, value: String) {
                    if (key == SPEECH_ENGINE_KEY && value == "system") { writing.complete(Unit); releaseWrite.await() }
                    original.savePreference(key, value)
                }
            }
        })
        confirmDeletion()
        rule.waitUntil(5_000) { writing.isCompleted }
        rule.runOnIdle { showSettings = false }
        rule.onNodeWithText("別の画面").assertIsDisplayed()
        releaseWrite.complete(Unit)
        rule.waitUntil(5_000) { runBlocking { storage.preferences.first()[SPEECH_ENGINE_KEY] == "system" } }
        rule.waitUntil(5_000) { runtime.assets.state.value.stage == VoiceAssetStage.MISSING }
        assertFalse(root.exists())
    }

    @Test fun leavingWhileTheFileDeletionIsRunningDoesNotCancelTheEngineCommit() {
        val deleting = AtomicBoolean()
        val gateOnce = AtomicBoolean(true)
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun delete(path: Path, mustExist: Boolean) {
                if (path.name == "partial" && gateOnce.compareAndSet(true, false)) {
                    deleting.set(true)
                    check(releaseDelete.await(5, TimeUnit.SECONDS)) { "File deletion gate was not released" }
                }
                super.delete(path, mustExist)
            }
        }
        open(playerPrepare = { error("No playback during deletion") }, downloaded = false, fileSystem = fs)
        confirmDeletion()
        rule.waitUntil(5_000) { deleting.get() }
        rule.runOnIdle { showSettings = false }
        rule.onNodeWithText("別の画面").assertIsDisplayed()
        releaseDelete.countDown()
        rule.waitUntil(5_000) { !root.exists() }
        rule.waitUntil(5_000) { runBlocking { storage.preferences.first()[SPEECH_ENGINE_KEY] == "system" } }
        rule.waitUntil(5_000) { runtime.assets.state.value.stage == VoiceAssetStage.MISSING }
    }

    @Test fun failedSystemPreferenceCommitPreservesThePackAndShowsAUsefulDeletionError() {
        val failed = AtomicBoolean()
        open(playerPrepare = { error("No playback during deletion") }, downloaded = false, decorate = { original ->
            object : CompatibilityStore by original {
                override suspend fun savePreference(key: String, value: String) {
                    if (key == SPEECH_ENGINE_KEY && value == "system") { failed.set(true); throw IOException("private storage path") }
                    original.savePreference(key, value)
                }
            }
        })
        confirmDeletion()
        rule.waitUntil(5_000) { failed.get() }
        rule.waitForIdle()
        assertTrue("Do not remove the user's pack before its engine preference is committed", File(root, "partial").exists())
        assertEquals("voicevox_zundamon", runBlocking { storage.preferences.first()[SPEECH_ENGINE_KEY] })
        val message = "読み上げ設定を保存できなかったため、音声データを削除しませんでした。再試行してください"
        rule.waitUntil(5_000) { rule.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
    }

    /** L9: the ずんだもん notice opens the licenses itself; ふたちゃ's settings have no 「ライセンス」 row. */
    @Test fun theVoiceNoticeOpensTheLicensesAtTheVoicevoxCredit() {
        open(playerPrepare = { error("No playback while reading licenses") })
        rule.onNodeWithTag("speech-zundamon-license").performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-license-voicevox-credit").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-license-voicevox-credit").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-license-list").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("speech-zundamon-license").assertIsDisplayed()
        assertFalse(prepared.get())
    }

    private fun confirmDeletion() {
        rule.waitUntil(5_000) { runtime.assets.state.value.stage == VoiceAssetStage.PAUSED }
        rule.onNodeWithTag("speech-zundamon-delete").performScrollTo().performClick()
        rule.onNodeWithText("削除").performClick()
    }

}
