package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.SpeechRuntime
import com.valoser.futacha.shared.download.*
import kotlinx.coroutines.*
import okio.*
import kotlin.random.Random
import kotlin.test.*

class VoiceDownloadLifecycleTest {
    @Test fun oneRuntimeOwnsOneTransferAndClosingItLeavesRecoverableBytes() = runBlocking<Unit> {
        withTimeout(5_000) {
            val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-owner-${Random.nextLong()}")
            val started = CompletableDeferred<Unit>()
            var transfers = 0
            val assets = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = {
                object : AssetDownloader {
                    override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                        transfers++
                        sink.writeUtf8("partial"); sink.emit(); started.complete(Unit)
                        awaitCancellation()
                    }
                }
            })
            val platform = object : VoicePlatform {
                override val available = true
                override val unavailableReason = ""
                override fun directory() = root
                override fun availableBytes() = Long.MAX_VALUE
                override fun createNative(pack: VoicePack): VoiceNativeSession = error("no native model")
                override fun createPlayer(): VoicePcmPlayer = error("no audio")
            }
            val runtime = SpeechRuntime(null, platform, assets)
            try {
                val first = runtime.downloadVoiceAssets()
                started.await()
                assertSame(first, runtime.downloadVoiceAssets())
                assertEquals(1, transfers)
                assertTrue(first.isActive)
                runtime.close()
                first.join()
                assertEquals(VoiceAssetStage.PAUSED, assets.state.value.stage)
                val recreated = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { error("refresh must not transfer") })
                recreated.refresh()
                assertEquals(VoiceAssetStage.PAUSED, recreated.state.value.stage)
                assertEquals(7L, recreated.state.value.bytes)
                recreated.delete()
                assertFalse(FileSystem.SYSTEM.exists(root))
            } finally { runtime.close(); FileSystem.SYSTEM.deleteRecursively(root, false) }
        }
    }
}
