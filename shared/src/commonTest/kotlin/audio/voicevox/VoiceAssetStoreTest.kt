package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.*
import kotlinx.coroutines.*
import okio.*
import kotlin.random.Random
import kotlin.test.*

class VoiceAssetStoreTest {
    @Test fun cancellationRetainsPartialAndResumesOnlyAfterAnExplicitInstall() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-resume-${Random.nextLong()}")
        val written = CompletableDeferred<Unit>()
        var calls = 0
        val transport = object : AssetDownloader {
            override val supportsResume = true
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) {
                calls++; sink.writeUtf8("partial"); sink.emit(); written.complete(Unit); awaitCancellation()
            }
            override suspend fun resumeDownload(distribution: AssetDistribution, sink: BufferedSink, offset: Long) {
                calls++; assertEquals(7L, offset); throw IOException("offline")
            }
        }
        val store = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { transport })
        try {
            val job = launch { store.install() }
            written.await(); job.cancelAndJoin()
            assertEquals(VoiceAssetStage.PAUSED, store.state.value.stage)
            val partial = root.resolve("${VoiceAssetManifest.dictionary.sha256}.part")
            assertEquals("partial", FileSystem.SYSTEM.read(partial) { readUtf8() })
            val recreated = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { transport })
            recreated.refresh(); assertEquals(1, calls)
            assertFailsWith<IOException> { recreated.install() }
            assertEquals(2, calls)
            assertFalse(recreated.state.value.ready)
            recreated.delete(); assertFalse(FileSystem.SYSTEM.exists(root))
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun wrongHashCannotBeInstalledAndRequiresASecondExplicitAttempt() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-corrupt-${Random.nextLong()}")
        var calls = 0
        val store = VoiceAssetStore({ root }, { Long.MAX_VALUE }, downloader = { object : AssetDownloader {
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) { calls++; sink.writeUtf8("wrong payload") }
        } })
        try {
            assertFails { store.install() }
            assertEquals(1, calls)
            assertEquals(VoiceAssetStage.FAILED, store.state.value.stage)
            assertFalse(FileSystem.SYSTEM.exists(root.resolve(VoiceAssetManifest.PACK)))
            assertFalse(FileSystem.SYSTEM.list(root).any { it.name.endsWith(".part") || it.name.endsWith(".staging") })
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun anUnavailableStorageDirectoryIsAnErrorInsteadOfAnUncaughtSettingsFailure() = runBlocking {
        val store = VoiceAssetStore({ throw IOException("storage unavailable") }, { Long.MAX_VALUE }, downloader = { error("no transfer") })
        store.refresh()
        assertEquals(VoiceAssetStage.FAILED, store.state.value.stage)
        assertFailsWith<IOException> { store.install() }
        assertEquals(VoiceAssetStage.FAILED, store.state.value.stage)
        assertEquals("storage unavailable", store.state.value.message)
    }
    @Test fun lowStorageIsRejectedBeforeStartingAnyTransfer() = runBlocking {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-capacity-${Random.nextLong()}")
        val store = VoiceAssetStore({ root }, { 1 }, downloader = { error("must not download") })
        try { assertFails { store.install() }; assertEquals(VoiceAssetStage.FAILED, store.state.value.stage) }
        finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
}
