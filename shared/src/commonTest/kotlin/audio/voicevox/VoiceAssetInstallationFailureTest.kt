package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.*
import okio.*
import kotlin.random.Random
import kotlin.test.*

/** Production's post-verification promotion boundary, with small verified inputs and real filesystem move faults. */
class VoiceAssetInstallationFailureTest {
    @Test fun aProcessDeathBetweenMovesRecoversTheModelAndFormerPackBeforeCleanup() = runBlocking<Unit> {
        val f = Fixture()
        try {
            FileSystem.SYSTEM.atomicMove(f.model, f.staging.resolve("0.vvm"))
            FileSystem.SYSTEM.atomicMove(f.current, f.root.resolve("pack.previous"))
            recoverInterruptedVoicePack(f.fs, f.model, f.staging, f.current)
            f.assertOriginals()
            FileSystem.SYSTEM.deleteRecursively(f.staging, false)
            f.assertOriginals()
        } finally { f.close() }
    }

    @Test fun finalMoveFailureKeepsTheVerifiedDownloadAndRestoresTheFormerPackForOfflineRetry() = runBlocking<Unit> {
        val f = Fixture()
        try {
            f.onPromotion = { throw IOException("injected final directory move failure") }
            assertFailsWith<IOException> { promoteVerifiedVoicePack(f.fs, f.model, f.staging, f.current) }
            f.assertOriginals()
            // This is the install caller's existing unconditional staging cleanup.
            FileSystem.SYSTEM.deleteRecursively(f.staging, false)
            assertEquals(f.modelBytes, FileSystem.SYSTEM.read(f.model) { readUtf8() })
            f.stageDictionary()
            f.onPromotion = null
            promoteVerifiedVoicePack(f.fs, f.model, f.staging, f.current)
            f.assertInstalled()
        } finally { f.close() }
    }

    @Test fun cancellingAtPublicationDoesNotLetStagingCleanupRemoveTheOnlyVerifiedModel() = runBlocking<Unit> {
        val f = Fixture()
        var reachedPublication = false
        try {
            val installation = launch {
                val installJob = requireNotNull(currentCoroutineContext()[Job])
                f.onPromotion = {
                    reachedPublication = true
                    installJob.cancel()
                    throw CancellationException("injected cancellation immediately before publication")
                }
                promoteVerifiedVoicePack(f.fs, f.model, f.staging, f.current)
            }
            installation.join()
            assertTrue(reachedPublication)
            f.assertOriginals()
            FileSystem.SYSTEM.deleteRecursively(f.staging, false)
            assertEquals(f.modelBytes, FileSystem.SYSTEM.read(f.model) { readUtf8() })
        } finally { f.close() }
    }

    @Test fun successfulPublicationContainsTheCompletePackAndReleasesTheVerifiedDownload() = runBlocking<Unit> {
        val f = Fixture()
        try { promoteVerifiedVoicePack(f.fs, f.model, f.staging, f.current); f.assertInstalled() }
        finally { f.close() }
    }

    private class Fixture {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-promotion-${Random.nextLong()}")
        val model = root.resolve("verified-model.vvm")
        val staging = root.resolve("pack.staging")
        val current = root.resolve("pack")
        val modelBytes = "verified model bytes"
        var onPromotion: (() -> Unit)? = null
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(source: Path, target: Path) {
                if (source == staging && target == current) onPromotion?.invoke()
                super.atomicMove(source, target)
            }
        }
        init {
            FileSystem.SYSTEM.createDirectories(current)
            FileSystem.SYSTEM.write(model) { writeUtf8(modelBytes) }
            FileSystem.SYSTEM.write(current.resolve("original")) { writeUtf8("former installation") }
            stageDictionary()
        }
        fun stageDictionary() {
            FileSystem.SYSTEM.createDirectories(staging.resolve("dict"))
            FileSystem.SYSTEM.write(staging.resolve("dict/sys.dic")) { writeUtf8("verified dictionary bytes") }
        }
        fun assertOriginals() {
            assertTrue(FileSystem.SYSTEM.exists(model), "A final install failure must retain the verified download outside disposable staging")
            assertEquals(modelBytes, FileSystem.SYSTEM.read(model) { readUtf8() })
            assertTrue(FileSystem.SYSTEM.exists(current.resolve("original")), "The previous installation must remain available after rollback")
            assertEquals("former installation", FileSystem.SYSTEM.read(current.resolve("original")) { readUtf8() })
        }
        fun assertInstalled() {
            assertEquals(modelBytes, FileSystem.SYSTEM.read(current.resolve("0.vvm")) { readUtf8() })
            assertEquals("verified dictionary bytes", FileSystem.SYSTEM.read(current.resolve("dict/sys.dic")) { readUtf8() })
            assertFalse(FileSystem.SYSTEM.exists(current.resolve("original")))
            assertFalse(FileSystem.SYSTEM.exists(model))
            assertFalse(FileSystem.SYSTEM.exists(staging))
        }
        fun close() { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
}
