package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Round2SaveProgressTest {
    @Test fun networkProgressCanOutliveTheDiskWriteDeadline() = runBlocking {
        val files = InMemoryFileSystem()
        writeThreadSaveBinaryStream(files, ThreadSaveBinaryWriteTarget(null, null, "media/video"), 25L) { sink ->
            delay(75L)
            sink.write(byteArrayOf(1), 0, 1)
            delay(75L)
            sink.write(byteArrayOf(2), 0, 1)
        }
        assertContentEquals(byteArrayOf(1, 2), files.readBytes("media/video").getOrThrow())
    }

    @Test fun mediaBudgetPublishesAReusablePartialGeneration() = runBlocking {
        val files = InMemoryFileSystem()
        val posts = (1..2).map { Post(it.toString(), author = null, subject = null, timestamp = "",
            messageHtml = "body", imageUrl = "https://example.test/b/src/$it.jpg", thumbnailUrl = null) }
        val secondRequests = mutableListOf<String>()
        val firstClient = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("2.jpg")) delay(2_000L)
            respond("image")
        })
        val secondClient = HttpClient(MockEngine { request -> secondRequests += request.url.toString(); respond("image") })
        suspend fun save(client: HttpClient, generation: String, seed: String?, budget: Long?) =
            ThreadSaveService(client, files).saveThread("123", "b", "board", "https://example.test/b/", "title", null,
                posts, baseDirectory = "auto", writeMetadata = true, rawHtmlOptions = RawHtmlSaveOptions(enable = false),
                limits = ThreadSaveLimits(maxParallelDownloads = 1, mediaDownloadBudgetMs = budget),
                storageOptions = ThreadSaveStorageOptions(storageIdOverride = generation, seedFromStorageId = seed))
        try {
            val partial = save(firstClient, "first", null, 500L).getOrThrow()
            assertEquals(SaveStatus.PARTIAL, partial.status)
            assertTrue(files.exists("auto/first/b/src/1.jpg"))
            val complete = save(secondClient, "second", "first", null).getOrThrow()
            assertEquals(SaveStatus.COMPLETED, complete.status)
            assertTrue(secondRequests.none { it.endsWith("1.jpg") })
            assertTrue(secondRequests.any { it.endsWith("2.jpg") })
        } finally { firstClient.close(); secondClient.close() }
    }
}
