package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.download.*
import kotlinx.coroutines.runBlocking
import okio.*
import kotlin.random.Random
import kotlin.test.*

/** Real installer/files/offset decisions with a silent downloader that never performs HTTP. */
class VoiceAssetCapacityAdmissionTest {
    private fun root() = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("voice-capacity-resume-${Random.nextLong()}")
    private fun partial(root: Path) = root.resolve("${VoiceAssetManifest.dictionary.sha256}.part")
    private fun seed(root: Path) {
        FileSystem.SYSTEM.createDirectories(root)
        FileSystem.SYSTEM.write(partial(root)) { writeUtf8("partial") }
    }
    @Test fun theExactRemainingBudgetAdmitsTheActualResumeOffsetInsteadOfDemanding256MiBAgain() = runBlocking {
        val root = root(); seed(root)
        var resumed = false
        val marker = IOException("fixture transport reached")
        val store = VoiceAssetStore({ root }, { VoiceAssetManifest.STAGING_SPACE_BYTES - 7 }, downloader = { object : AssetDownloader {
            override val supportsResume = true
            override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) = error("Must resume the retained dictionary")
            override suspend fun resumeDownload(distribution: AssetDistribution, sink: BufferedSink, offset: Long) {
                assertEquals(VoiceAssetManifest.dictionary, distribution)
                assertEquals(7L, offset)
                resumed = true
                throw marker
            }
        } })
        try {
            // On the JVM the coroutine machinery may hand back a copy whose cause is the original.
            val failure = assertFailsWith<IOException> { store.install() }
            assertTrue(failure === marker || failure.cause === marker, "The transport's own failure must surface: $failure")
            assertTrue(resumed)
            assertEquals("partial", FileSystem.SYSTEM.read(partial(root)) { readUtf8() })
            assertEquals(VoiceAssetStage.FAILED, store.state.value.stage)
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun oneByteBelowTheRemainingBudgetRejectsBeforeCreatingATransportAndKeepsThePartial() = runBlocking {
        val root = root(); seed(root)
        var created = false
        val store = VoiceAssetStore({ root }, { VoiceAssetManifest.STAGING_SPACE_BYTES - 8 }, downloader = { created = true; error("no transfer") })
        try {
            assertFailsWith<IllegalStateException> { store.install() }
            assertFalse(created)
            assertEquals("partial", FileSystem.SYSTEM.read(partial(root)) { readUtf8() })
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun aNonResumingTransportCannotSpendThePartialCreditAndIsClosedWithoutTransferring() = runBlocking {
        val root = root(); seed(root)
        var calls = 0
        var created = false
        var closed = false
        val store = VoiceAssetStore({ root }, { VoiceAssetManifest.STAGING_SPACE_BYTES - 7 }, downloader = {
            created = true
            object : AssetDownloader, AutoCloseable {
                override suspend fun download(distribution: AssetDistribution, sink: BufferedSink) { calls++ }
                override fun close() { closed = true }
            }
        })
        try {
            assertFailsWith<IllegalStateException> { store.install() }
            assertEquals(0, calls)
            assertTrue(!created || closed, "An admission refusal must release any created transport")
            assertEquals("partial", FileSystem.SYSTEM.read(partial(root)) { readUtf8() })
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun anOversizedPartialCannotReduceTheAdmissionBudget() = runBlocking {
        val root = root(); seed(root)
        val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun metadataOrNull(path: Path): FileMetadata? = super.metadataOrNull(path)?.let {
                if (path == partial(root)) it.copy(size = VoiceAssetManifest.dictionary.bytes + 1) else it
            }
        }
        var created = false
        val store = VoiceAssetStore({ root }, { VoiceAssetManifest.STAGING_SPACE_BYTES - 7 }, fileSystem = fs,
            downloader = { created = true; error("invalid partial must give no credit") })
        try {
            assertFailsWith<IllegalStateException> { store.install() }
            assertFalse(created)
            assertEquals("partial", FileSystem.SYSTEM.read(partial(root)) { readUtf8() })
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun aCorruptCompletedArchiveCannotReduceTheAdmissionBudget() = runBlocking {
        val root = root(); FileSystem.SYSTEM.createDirectories(root)
        val archive = root.resolve("${VoiceAssetManifest.dictionary.sha256}.tar.gz")
        FileSystem.SYSTEM.write(archive) { writeUtf8("corrupt") }
        val store = VoiceAssetStore({ root }, { VoiceAssetManifest.STAGING_SPACE_BYTES - 7 }, downloader = { error("no credit for unverified archive") })
        try {
            assertFailsWith<IllegalStateException> { store.install() }
            assertEquals("corrupt", FileSystem.SYSTEM.read(archive) { readUtf8() })
        } finally { FileSystem.SYSTEM.deleteRecursively(root, false) }
    }
    @Test fun retainedCreditIsBoundedByPinnedDownloadsWithoutOverflowingOrRemovingTheExtractionReserve() {
        assertEquals(VoiceAssetManifest.STAGING_SPACE_BYTES, voiceAssetRequiredFreeBytes(-1))
        assertEquals(VoiceAssetManifest.STAGING_SPACE_BYTES - VoiceAssetManifest.DOWNLOAD_BYTES, voiceAssetRequiredFreeBytes(Long.MAX_VALUE))
        assertTrue(voiceAssetRequiredFreeBytes(Long.MAX_VALUE) >= VoiceAssetManifest.DICTIONARY_BYTES)
    }
}
