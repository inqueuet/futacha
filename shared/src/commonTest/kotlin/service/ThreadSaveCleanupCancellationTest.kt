package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/** L72: a save cancelled half way still removes the partial file it left. */
class ThreadSaveCleanupCancellationTest {
    @Test fun theCleanupOfACancelledSaveStillDeletesThePartialFile() = runBlocking {
        val deleted = mutableListOf<String>()
        val fileSystem = object : FileSystem by InMemoryFileSystem() {
            // A real delete suspends (it switches to the IO dispatcher), which a cancelled coroutine cannot do.
            override suspend fun delete(path: String): Result<Unit> {
                yield(); deleted += path; return Result.success(Unit)
            }
            override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
                yield(); deleted += relativePath; return Result.success(Unit)
            }
        }
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cleanupThreadSaveBinaryWriteTarget(fileSystem, ThreadSaveBinaryWriteTarget(null, null, "partial.jpg"))
                cleanupThreadSaveBinaryWriteTarget(
                    fileSystem, ThreadSaveBinaryWriteTarget(SaveLocation.Path("/saves"), "media/partial.jpg", null)
                )
            }
        }
        save.cancelAndJoin()
        assertEquals(listOf("partial.jpg", "media/partial.jpg"), deleted)
    }
}
