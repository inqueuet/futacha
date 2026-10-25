package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MediaSaveDestinationTest {
    private val location = SaveLocation.TreeUri("content://tree/chosen")
    private class DestinationFs(val backing: InMemoryFileSystem = InMemoryFileSystem()) : FileSystem by backing {
        var writable = false
        var cancelled = false
        override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> {
            if (cancelled) throw CancellationException("cancel destination check")
            return if (writable) backing.createDirectory(base, relativePath)
                else Result.failure(IllegalStateException("permission lost"))
        }
        override suspend fun writeByteStreamReplacing(base: SaveLocation, relativePath: String,
            block: suspend (FileWriteSink) -> Unit): Result<String> =
            if (writable) backing.writeByteStreamReplacing(base, relativePath, block)
            else Result.failure(IllegalStateException("permission lost"))
    }
    @Test fun unavailableFolderIsRejectedBeforeAnyMediaRequestForRootAndNestedSaves() = runBlocking {
        for (root in listOf(true, false)) {
            val fs = DestinationFs()
            var requests = 0
            HttpClient(MockEngine { requests++; respond(byteArrayOf(1, 2, 3)) }).use { client ->
                val result = SingleMediaSaveService(client, fs).saveMedia("https://example.invalid/one.jpg", "b", "1",
                    baseSaveLocation = location, storageDirectoryOverride = if (root) "" else "folder",
                    useTypeSubdirectory = !root, outputFileNameOverride = "one.jpg")
                assertTrue(result.isFailure)
                assertEquals(0, requests, "An unavailable destination must not download a payload")
                assertTrue(result.exceptionOrNull()!!.message!!.contains("選び直し"))
            }
        }
    }
    @Test fun reselectedFolderCanBeRetriedAndTheFailedAttemptPreservesOldBytes() = runBlocking {
        val fs = DestinationFs()
        val old = byteArrayOf(9, 8)
        fs.backing.writeBytes(location, "one.jpg", old).getOrThrow()
        var requests = 0
        val fresh = byteArrayOf(1, 2, 3)
        HttpClient(MockEngine { requests++; respond(fresh) }).use { client ->
            val saver = SingleMediaSaveService(client, fs)
            suspend fun save() = saver.saveMedia("https://example.invalid/one.jpg", "b", "1",
                baseSaveLocation = location, storageDirectoryOverride = "", useTypeSubdirectory = false,
                outputFileNameOverride = "one.jpg")
            assertTrue(save().isFailure)
            assertEquals(0, requests)
            assertContentEquals(old, fs.backing.readBytes(location, "one.jpg").getOrThrow())
            fs.writable = true
            save().getOrThrow()
            assertEquals(1, requests)
            assertContentEquals(fresh, fs.backing.readBytes(location, "one.jpg").getOrThrow())
        }
    }
    @Test fun cancellationDuringDestinationCheckDoesNotDownloadOrBecomeASaveFailure() = runBlocking {
        val fs = DestinationFs().apply { cancelled = true }
        var requests = 0
        HttpClient(MockEngine { requests++; respond(byteArrayOf(1)) }).use { client ->
            assertFailsWith<CancellationException> {
                SingleMediaSaveService(client, fs).saveMedia("https://example.invalid/one.jpg", "b", "1",
                    baseSaveLocation = location)
            }
            assertEquals(0, requests)
        }
    }
}
