package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.*

class FtutterHtmlSaveIntegrationTest {
    @Test fun savedHtmlAndZipContainOnlySelectedAttachmentsWithoutAddingReplies() = runBlocking {
        val fs = InMemoryFileSystem()
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            synchronized(requested) { requested += request.url.encodedPath }
            if (request.url.encodedPath.endsWith(".htm")) respond(
                "<html><body><img src=\"https://may.2chan.net/b/thumb/1s.jpg\"><a href=\"https://dec.2chan.net/up2/src/fu1234567.jpg\">あぷ小</a></body></html>",
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            else respond("image".encodeToByteArray(), headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
        }
        val client = HttpClient(engine)
        try {
            val page = ThreadPage("1", null, null, null, listOf(Post("1", author = null, subject = null,
                timestamp = "", messageHtml = "fu1234567.jpg", imageUrl = "https://may.2chan.net/b/src/1.jpg",
                thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg")))
            val options = FtutterHtmlOptions(thumbnails = true, files = false, apu = true, zip = true)
            var paths = emptySet<String>()
            val saved = ThreadSaveService(client, fs).saveThread("1", "b", "may/b", "https://may.2chan.net/b/",
                "Test", null, ftutterHtmlPosts(page, options), baseDirectory = "manual", writeMetadata = true,
                rawHtmlOptions = RawHtmlSaveOptions(enable = true, stripExternalResources = true),
                additionalMediaUrls = page.posts.flatMap { ftutterApuImages(it.messageHtml) },
                onMediaPathsSaved = { paths = it }).getOrThrow()
            assertEquals(1, saved.postCount)
            assertFalse("/b/src/1.jpg" in requested)
            assertTrue("/b/thumb/1s.jpg" in requested)
            assertTrue("/up2/src/fu1234567.jpg" in requested)
            val id = requireNotNull(saved.storageId)
            val base = SaveLocation.Path("manual")
            val zipPath = ftutterZipSavedThread(fs, base, id, paths)
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(fs.readBytes(base, zipPath).getOrThrow())).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes(); zip.closeEntry() }
            }
            assertTrue("metadata.json" in entries); assertTrue("1.htm" in entries)
            assertTrue(entries.keys.any { it.endsWith("1s.jpg") })
            assertTrue(entries.keys.any { it.endsWith("fu1234567.jpg") })
            assertFalse(entries.keys.any { it.endsWith("src/1.jpg") })
            assertFalse(entries.getValue("1.htm").decodeToString().contains("https://dec.2chan.net/up2/src/fu1234567.jpg"))
        } finally { client.close() }
    }
    @Test fun aFailedZipWriteRemovesThePartialArchiveAndKeepsTheSavedThread() = runBlocking {
        val fs = InMemoryFileSystem()
        val base = SaveLocation.Path("manual")
        val metadata = SavedThreadMetadata(threadId = "1", boardId = "b", boardName = "b",
            boardUrl = "https://may.2chan.net/b/", title = "Test", savedAt = 0,
            expiresAtLabel = null, posts = emptyList(), totalSize = 0)
        fs.writeString(base, "one/metadata.json", Json.encodeToString(metadata)).getOrThrow()
        val failing = object : FileSystem by fs {
            override suspend fun writeByteStream(base: SaveLocation, relativePath: String,
                block: suspend (FileWriteSink) -> Unit): Result<Unit> {
                fs.writeString(base, relativePath, "partial").getOrThrow()
                return Result.failure(IllegalStateException("simulated write failure"))
            }
        }
        assertFailsWith<IllegalStateException> { ftutterZipSavedThread(failing, base, "one", emptySet()) }
        assertFalse(fs.exists(base, "one.zip"))
        assertTrue(fs.exists(base, "one/metadata.json"))
    }

}
