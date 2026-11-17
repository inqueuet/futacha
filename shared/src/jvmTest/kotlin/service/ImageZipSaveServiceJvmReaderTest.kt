package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ImageZipSaveServiceJvmReaderTest {
    private val large = ByteArray(200_000) { (it * 31).toByte() }

    private fun client(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                when (request.url.encodedPath.substringAfterLast('/')) {
                    "broken.mp4" -> {
                        // Sends part of the body and then fails mid-stream.
                        @OptIn(DelicateCoroutinesApi::class)
                        val channel = GlobalScope.writer(Dispatchers.IO) {
                            this.channel.writeFully(ByteArray(70_000) { 7 })
                            this.channel.flush()
                            throw IOException("connection reset")
                        }.channel
                        respond(channel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "300000"))
                    }
                    "short.jpg" -> respond(
                        "abc".encodeToByteArray(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, "10")
                    )
                    "large.png" -> respond(
                        large,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, large.size.toString())
                    )
                    else -> respond(
                        "xyz".encodeToByteArray(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, "3")
                    )
                }
            }
        }
    }

    @Test
    fun archiveOpensWithZipInputStreamAndZipFileAndSkipsEntriesThatFailMidway() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val client = client()
        val saved = ImageZipSaveService(client, fileSystem).save(
            mediaUrls = listOf(
                "https://may.2chan.net/b/src/a.jpg",
                "https://may.2chan.net/b/src/broken.mp4",
                "https://may.2chan.net/b/src/large.png",
                "https://may.2chan.net/b/src/short.jpg",
                "https://may.2chan.net/b/src/c.gif"
            ),
            boardId = "b",
            threadId = "1",
            baseDirectory = "manual"
        ).getOrThrow()
        client.close()

        assertEquals(3, saved.savedItems)
        assertEquals(
            listOf("https://may.2chan.net/b/src/broken.mp4", "https://may.2chan.net/b/src/short.jpg"),
            saved.failedUrls
        )
        val bytes = fileSystem.readBytes("manual/${saved.fileName}").getOrThrow()

        val file = Files.createTempFile("media", ".zip").toFile()
        try {
            file.writeBytes(bytes)
            ZipFile(file).use { zip ->
                val names = zip.entries().toList().map { it.name }
                assertEquals(listOf("a.jpg", "large.png", "c.gif"), names)
                assertContentEquals(large, zip.getInputStream(zip.getEntry("large.png")).readBytes())
                assertContentEquals("xyz".encodeToByteArray(), zip.getInputStream(zip.getEntry("a.jpg")).readBytes())
            }
        } finally {
            file.delete()
        }

        // Streaming and central-directory readers must see the same complete entries.
        val streamed = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                streamed[entry.name] = zip.readBytes()
            }
        }
        assertEquals(setOf("a.jpg", "large.png", "c.gif"), streamed.keys)
        assertContentEquals(large, streamed.getValue("large.png"))
        assertContentEquals("xyz".encodeToByteArray(), streamed.getValue("a.jpg"))
        assertContentEquals("xyz".encodeToByteArray(), streamed.getValue("c.gif"))
    }
}
