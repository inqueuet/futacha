package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SingleMediaSaveReplaceTest {
    private fun offlineClient() = HttpClient(MockEngine { error("Local exports must not use the network") })

    private suspend fun SingleMediaSaveService.saveFixed() = saveMedia(
        "/cache/source.jpg", "b", "1",
        baseDirectory = "manual",
        storageDirectoryOverride = "",
        useTypeSubdirectory = false,
        outputFileNameOverride = "fixed.jpg"
    )

    @Test
    fun failedReSaveKeepsTheExistingFixedNameFile() = runBlocking {
        val backing = InMemoryFileSystem()
        val previous = "previous".encodeToByteArray()
        backing.writeBytes("manual/fixed.jpg", previous).getOrThrow()
        backing.writeBytes("/cache/source.jpg", byteArrayOf(1, 2)).getOrThrow()
        // Declares more bytes than the source delivers: the save must fail midway.
        val fs = object : FileSystem by backing {
            override suspend fun getFileSize(path: String): Long =
                if (path.endsWith("cache/source.jpg")) 100L else backing.getFileSize(path)
        }
        offlineClient().use { client ->
            val result = SingleMediaSaveService(client, fs).saveFixed()
            assertTrue(result.isFailure)
        }
        assertContentEquals(previous, backing.readBytes("manual/fixed.jpg").getOrThrow())
    }

    @Test
    fun completedReSaveReplacesTheFixedNameFile() = runBlocking {
        val fs = InMemoryFileSystem()
        fs.writeBytes("manual/fixed.jpg", "previous".encodeToByteArray()).getOrThrow()
        val fresh = ByteArray(5_000) { (it % 200).toByte() }
        fs.writeBytes("/cache/source.jpg", fresh).getOrThrow()
        offlineClient().use { client ->
            val saved = SingleMediaSaveService(client, fs).saveFixed().getOrThrow()
            assertContentEquals(fresh, fs.readBytes("manual/${saved.relativePath}").getOrThrow())
        }
    }
}
