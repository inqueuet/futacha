package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.*

class AppStateHistoryManifestFailureTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val main = "private/history_store/manifest.json"
    private val backup = "$main.backup"
    private val original = listOf(entry("111"), entry("222"))

    private class Files(val delegate: InMemoryFileSystem = InMemoryFileSystem()) : FileSystem by delegate {
        var unreadable = emptySet<String>()
        var failWrites = false
        val readFailed = CompletableDeferred<Unit>()
        override suspend fun readString(path: String): Result<String> {
            if (path !in unreadable) return delegate.readString(path)
            readFailed.complete(Unit)
            return Result.failure(IllegalStateException("temporarily unavailable"))
        }
        override suspend fun writeString(path: String, content: String): Result<Unit> =
            if (failWrites) Result.failure(IllegalStateException("read-only storage")) else delegate.writeString(path, content)
    }

    private fun entry(id: String) = ThreadHistoryEntry(id, "b", "title-$id", "", "board",
        "https://may.2chan.net/b/", lastVisitedEpochMillis = 100, replyCount = 1)

    private suspend fun files() = Files().also {
        AppStateHistoryFileStore(it, json, "seed").persistHistorySnapshot(original)
    }

    @Test fun existingUnreadableManifestsAreNotAnEmptyLegacyHistory(): Unit = runBlocking {
        val files = files().apply { unreadable = setOf(main, backup) }
        val reader = AppStateHistoryFileStore(files, json, "read")
        assertFailsWith<IllegalStateException> { reader.readHistorySnapshot { "[]" } }
    }

    @Test fun aFailedReadCanBeRetriedWithoutRecreatingTheStore(): Unit = runBlocking {
        val files = files().apply { unreadable = setOf(main, backup) }
        val reader = AppStateHistoryFileStore(files, json, "read")
        runCatching { reader.readHistorySnapshot { "[]" } }
        files.unreadable = emptySet()
        assertEquals(original, reader.readHistorySnapshot { "[]" })
    }

    @Test fun unreadableManifestsCannotBeReplacedByALaterWrite(): Unit = runBlocking {
        val files = files()
        val before = files.delegate.readString(main).getOrThrow()
        files.unreadable = setOf(main, backup)
        val writer = AppStateHistoryFileStore(files, json, "write")
        assertFailsWith<IllegalStateException> { writer.persistHistorySnapshot(listOf(entry("333"))) }
        assertEquals(before, files.delegate.readString(main).getOrThrow())
        assertEquals(before, files.delegate.readString(backup).getOrThrow())
        files.unreadable = emptySet()
        assertEquals(original, writer.readHistorySnapshot { "[]" })
    }

    /** L134: broken manifests are made again from the entry files, not from a stale legacy copy. */
    @Test fun corruptExistingManifestsDoNotImportAStaleLegacyCopy(): Unit = runBlocking {
        val files = files()
        files.delegate.writeString(main, "corrupt").getOrThrow()
        files.delegate.writeString(backup, "corrupt").getOrThrow()
        val reader = AppStateHistoryFileStore(files, json, "corrupt")
        assertEquals(original.toSet(), reader.readHistorySnapshot { encodeAppStateHistory(listOf(entry("obsolete")), json) }.toSet())
        assertNotEquals("corrupt", files.delegate.readString(main).getOrThrow())
        assertNotEquals("corrupt", files.delegate.readString(backup).getOrThrow())
    }

    /** L134: the rebuilt manifest lists the newest visit first, and a later start reads it as usual. */
    @Test fun brokenManifestsAreRebuiltNewestVisitFirst(): Unit = runBlocking {
        val files = Files()
        val visits = listOf(entry("1").copy(lastVisitedEpochMillis = 100), entry("2").copy(lastVisitedEpochMillis = 300),
            entry("3").copy(lastVisitedEpochMillis = 200))
        AppStateHistoryFileStore(files, json, "seed").persistHistorySnapshot(visits)
        files.delegate.writeString(main, "{").getOrThrow()
        files.delegate.writeString(backup, "{").getOrThrow()
        val expected = listOf("2", "3", "1")
        assertEquals(expected, AppStateHistoryFileStore(files, json, "rebuild").readHistorySnapshot { "[]" }.map { it.threadId })
        assertEquals(expected, AppStateHistoryFileStore(files, json, "restart").readHistorySnapshot { "[]" }.map { it.threadId })
    }

    /** L134: the observable history comes (a mode switch waiting on it goes on), and it can be cleared again. */
    @Test fun brokenManifestsDoNotKeepTheObservedHistoryWaiting(): Unit = runBlocking {
        val files = files()
        files.delegate.writeString(main, "corrupt").getOrThrow()
        files.delegate.writeString(backup, "corrupt").getOrThrow()
        val store = AppStateStore(FakePlatformStateStorage(), AppStateHistoryFileStore(files, json, "observe"), json)
        try {
            assertEquals(original.toSet(), withTimeout(5_000) { store.history.first() }.toSet())
            store.setHistory(emptyList())
            assertEquals(emptyList(), AppStateHistoryFileStore(files, json, "after-clear").readHistorySnapshot { "[]" })
        } finally { store.close() }
    }

    /** Entry files that cannot be read now may be readable later: nothing is replaced, the read still waits. */
    @Test fun brokenManifestsWithUnreadableEntriesStillWait(): Unit = runBlocking {
        val files = files()
        files.delegate.writeString(main, "corrupt").getOrThrow()
        files.delegate.writeString(backup, "corrupt").getOrThrow()
        files.unreadable = files.delegate.listFiles("private/history_store/entries").map { "private/history_store/entries/$it" }.toSet()
        assertFailsWith<IllegalStateException> { AppStateHistoryFileStore(files, json, "wait").readHistorySnapshot { "[]" } }
        assertEquals("corrupt", files.delegate.readString(main).getOrThrow())
    }

    @Test fun theObservableStoreDoesNotPublishOrPersistAFabricatedEmptyHistory(): Unit = runBlocking {
        val files = files().apply { unreadable = setOf(main, backup) }
        val storage = FakePlatformStateStorage().apply { updateHistoryJson("[]") }
        val store = AppStateStore(storage, AppStateHistoryFileStore(files, json, "observe"), json)
        val observation = async { store.history.first() }
        try {
            withTimeout(5_000) { files.readFailed.await() }
            assertNull(withTimeoutOrNull(200) { observation.await() }, "An unavailable read must not publish an empty snapshot")
            store.prependOrReplaceHistoryEntry(entry("333"))
            files.unreadable = emptySet()
            // A subsequent storage event must retry the read in the same subscription.
            storage.updateHistoryJson(" [] ")
            assertEquals(original, withTimeout(5_000) { observation.await() })
            assertEquals(original, AppStateHistoryFileStore(files, json, "restart").readHistorySnapshot { "[]" })
        } finally { observation.cancel(); store.close() }
    }

    @Test fun aLegacyBackupUpgradeFailureStillAllowsReadingTheHistory(): Unit = runBlocking {
        val files = files()
        val legacy = files.delegate.readString(main).getOrThrow().replace(Regex("\"version\":\\d+,"), "")
        files.delegate.writeString(main, legacy).getOrThrow()
        files.delegate.writeString(backup, legacy).getOrThrow()
        files.failWrites = true
        val reader = AppStateHistoryFileStore(files, json, "read-only")
        assertEquals(original, reader.readHistorySnapshot { "[]" })
        assertEquals(legacy, files.delegate.readString(main).getOrThrow())
        assertEquals(legacy, files.delegate.readString(backup).getOrThrow())
    }
}
