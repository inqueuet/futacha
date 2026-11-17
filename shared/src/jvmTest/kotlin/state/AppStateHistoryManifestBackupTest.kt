package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.*

/** Use actual files: the memory file system's linkOrCopy cannot reproduce shared inodes. */
class AppStateHistoryManifestBackupTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val original = listOf(ThreadHistoryEntry("111", "b", "title", "", "board",
        "https://may.2chan.net/b/", lastVisitedEpochMillis = 100, replyCount = 1))

    @Test fun manifestBackupHasAnIndependentFileIdentity(): Unit = runBlocking {
        val root = Files.createTempDirectory("futacha-history-backup").toFile()
        try {
            AppStateHistoryFileStore(JvmFileSystem(root), json, "seed").persistHistorySnapshot(original)
            assertFalse(Files.isSameFile(File(root, "private/history_store/manifest.json").toPath(),
                File(root, "private/history_store/manifest.json.backup").toPath()))
        } finally { root.deleteRecursively() }
    }

    @Test fun anInPlaceManifestCorruptionCanBeRecoveredFromTheBackup(): Unit = runBlocking {
        val root = Files.createTempDirectory("futacha-history-backup").toFile()
        try {
            val fs = JvmFileSystem(root)
            AppStateHistoryFileStore(fs, json, "seed").persistHistorySnapshot(original)
            // Simulate corruption of the existing file, rather than an atomic replacement.
            RandomAccessFile(File(root, "private/history_store/manifest.json"), "rw").use {
                it.setLength(0); it.write("corrupt".toByteArray())
            }
            val reopened = AppStateHistoryFileStore(fs, json, "recover")
            assertEquals(original, reopened.readHistorySnapshot { "[]" })
        } finally { root.deleteRecursively() }
    }

    @Test fun anExistingLegacyHardLinkIsSeparatedOnReadBeforeAnyHistoryMutation(): Unit = runBlocking {
        val root = Files.createTempDirectory("futacha-history-legacy-backup").toFile()
        try {
            val fs = JvmFileSystem(root)
            AppStateHistoryFileStore(fs, json, "seed").persistHistorySnapshot(original)
            val main = File(root, "private/history_store/manifest.json")
            val backup = File(root, "private/history_store/manifest.json.backup")
            // Older manifests omitted their default version=1. Recreate one
            // with the real hard-link API, including when seeded by a newer build.
            val legacy = fs.readString("private/history_store/manifest.json").getOrThrow()
                .replace(Regex("\"version\":\\d+,"), "")
            fs.writeString("private/history_store/manifest.json", legacy).getOrThrow()
            fs.linkOrCopy("private/history_store/manifest.json", "private/history_store/manifest.json.backup").getOrThrow()
            assertTrue(Files.isSameFile(main.toPath(), backup.toPath()))
            assertEquals(original, AppStateHistoryFileStore(fs, json, "upgrade").readHistorySnapshot { "[]" })
            assertFalse(Files.isSameFile(main.toPath(), backup.toPath()))
            RandomAccessFile(main, "rw").use { it.setLength(0); it.write("corrupt".toByteArray()) }
            assertEquals(original, AppStateHistoryFileStore(fs, json, "recover").readHistorySnapshot { "[]" })
        } finally { root.deleteRecursively() }
    }
}
