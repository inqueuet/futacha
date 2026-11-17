package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.*
import com.valoser.futacha.shared.media.analysis.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import okio.*
import kotlin.random.Random
import kotlin.test.*

/** Real store and shared HTTP transport, with an in-process MockEngine only. */
class VoiceAssetFailureMessageTest {
    @Test fun sharedImageHttpFailureKeepsItsContractWhileVoiceUsesAudioWords() = runBlocking {
        fun client() = HttpClient(MockEngine { respond("", HttpStatusCode.ServiceUnavailable) }) { configureAssetDownloads() }
        KtorModelDownloader(client()).use { model ->
            val failure = assertFailsWith<IOException> { model.download(
                ModelDistribution("https://model.test/file", 1, "0".repeat(64)), Buffer()) }
            assertEquals("モデルを取得できません（HTTP 503）", failure.message)
        }
        using({ KtorAssetDownloader(client()) }) { store ->
            assertFailsWith<IOException> { store.install() }
            assertEquals("音声データを取得できません（HTTP 503）。時間をおいて再試行してください", store.state.value.message)
        }
    }

    @Test fun rawNetworkAndStorageExceptionsNeverReachTheVoiceSettingsMessage() = runBlocking {
        val raw = "Network is unreachable: https://private.test/file?token=secret"
        using({ object : AssetDownloader {
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink): Unit = throw IOException(raw)
        } }) { store ->
            assertEquals(raw, assertFailsWith<IOException> { store.install() }.message)
            assertEquals(VoiceAssetStage.FAILED, store.state.value.stage)
            assertEquals("音声データを取得・保存できませんでした。通信状態と空き容量を確認して再試行してください", store.state.value.message)
        }
        val unavailable = VoiceAssetStore({ throw IOException("storage unavailable at /private/path") }, { Long.MAX_VALUE })
        assertFailsWith<IOException> { unavailable.install() }
        assertEquals("音声データの保存先を確認できませんでした", unavailable.state.value.message)
    }

    @Test fun actionableVoiceCapacityAndValidationMessagesRemainSpecific() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-message-${Random.nextLong()}")
        val store = VoiceAssetStore({ root }, { 1 }, downloader = { error("No network") })
        try {
            assertFails { store.install() }
            assertEquals("音声データの準備には約256MiBの空き容量が必要です", store.state.value.message)
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }

    private suspend fun using(downloader: () -> AssetDownloader, block: suspend (VoiceAssetStore) -> Unit) {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-message-${Random.nextLong()}")
        try { block(VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = downloader)) }
        finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
}
