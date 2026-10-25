package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.audio.voicevox.*
import kotlinx.coroutines.*
import okio.FileSystem
import okio.IOException
import kotlin.random.Random
import kotlin.test.*

/** L10: deletion runs in the app runtime, commits the system voice first and keeps the data when it cannot. */
class SpeechRuntimeDeletionTest {
    private val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-deletion-${Random.nextLong()}")
    private val retained = root.resolve("partial")
    private val platform = object : VoicePlatform {
        override val available = true
        override val unavailableReason = ""
        override fun directory() = root
        override fun availableBytes() = Long.MAX_VALUE
        override fun createNative(pack: VoicePack): VoiceNativeSession = error("No synthesis in the deletion test")
        override fun createPlayer(): VoicePcmPlayer = error("No playback in the deletion test")
    }
    private val runtime = SpeechRuntime(null, platform, VoiceAssetStore(platform::directory, platform::availableBytes))

    @BeforeTest fun seed() {
        FileSystem.SYSTEM.createDirectories(root)
        FileSystem.SYSTEM.write(retained) { writeUtf8("retained") }
    }
    @AfterTest fun cleanup() { runtime.close(); FileSystem.SYSTEM.deleteRecursively(root, false) }

    @Test fun theSystemVoiceIsCommittedBeforeTheDataIsRemoved() = runBlocking {
        var dataPresentAtCommit = false
        withTimeout(5_000) { runtime.deleteVoiceAssets { dataPresentAtCommit = FileSystem.SYSTEM.exists(retained) }.join() }
        assertTrue(dataPresentAtCommit, "The preference must be saved while the data still exists")
        assertFalse(FileSystem.SYSTEM.exists(root))
        assertEquals(VoiceDeletionState(), runtime.deletion.value)
    }

    @Test fun cancellingTheCallerDoesNotStopTheDeletion() = runBlocking {
        val committing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var deletion: Job? = null
        val caller = launch { deletion = runtime.deleteVoiceAssets { committing.complete(Unit); release.await() }; awaitCancellation() }
        withTimeout(5_000) { committing.await() }
        assertTrue(runtime.deletion.value.removing)
        caller.cancelAndJoin()
        release.complete(Unit)
        withTimeout(5_000) { requireNotNull(deletion).join() }
        assertFalse(FileSystem.SYSTEM.exists(root))
        assertFalse(runtime.deletion.value.removing)
    }

    @Test fun aPreferenceThatCannotBeSavedKeepsTheDataAndExplainsWhy() = runBlocking {
        withTimeout(5_000) { runtime.deleteVoiceAssets { throw IOException("private storage path") }.join() }
        assertTrue(FileSystem.SYSTEM.exists(retained))
        assertEquals(VoiceDeletionState(error = VOICE_DELETE_PREFERENCE_FAILURE), runtime.deletion.value)
        runtime.clearVoiceDeletionError()
        assertNull(runtime.deletion.value.error)
    }

    @Test fun aSecondRequestJoinsTheRunningDeletion() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var commits = 0
        val first = runtime.deleteVoiceAssets { commits++; release.await() }
        val second = runtime.deleteVoiceAssets { commits++ }
        assertSame(first, second)
        release.complete(Unit)
        withTimeout(5_000) { first.join() }
        assertEquals(1, commits)
    }
}
