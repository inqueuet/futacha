package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import com.valoser.futacha.shared.media.source.bindOriginalMediaSource
import okio.Path.Companion.toPath

/** may 1448774997: saving a fallen thread always ended as "HTMLを保存できませんでした". */
class ThreadSaveGoneThreadTest {
    private fun post(image: String?) = Post("1", author = null, subject = null, timestamp = "", messageHtml = "本文",
        imageUrl = image, thumbnailUrl = image?.replace("/src/", "/thumb/"))

    private fun save(status: HttpStatusCode, mediaStatus: HttpStatusCode, image: String?, rawHtml: Boolean = true) = runBlocking {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".htm")) {
                if (status.isSuccess()) respond("<html><body>ok</body></html>", status,
                    headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
                else respond("", status)
            } else if (mediaStatus.isSuccess()) {
                respond("img".encodeToByteArray(), mediaStatus, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            } else respond("", mediaStatus)
        })
        try {
            ThreadSaveService(client, InMemoryFileSystem()).saveThread("1", "b", "may/b", "https://may.2chan.net/b/",
                "Test", null, listOf(post(image)), baseDirectory = "manual", writeMetadata = true,
                rawHtmlOptions = RawHtmlSaveOptions(enable = rawHtml)).getOrThrow()
        } finally { client.close() }
    }

    @Test fun aFallenThreadIsNotReportedAsMissingItsHtml() {
        for (gone in listOf(HttpStatusCode.NotFound, HttpStatusCode.Gone)) {
            val saved = save(gone, HttpStatusCode.OK, null)
            assertFalse(saved.isHtmlMissing, "$gone")
            assertEquals(SaveStatus.COMPLETED, saved.status, "$gone")
        }
    }

    @Test fun aFallenThreadWhosePicturesAreGoneIsPartlySavedNotFailed() {
        val saved = save(HttpStatusCode.NotFound, HttpStatusCode.NotFound, "https://may.2chan.net/b/src/1.jpg")
        assertFalse(saved.isHtmlMissing)
        assertEquals(SaveStatus.PARTIAL, saved.status)
        assertTrue(saved.incompleteMediaCount > 0)
    }

    @Test fun anotherServerErrorStillReportsTheMissingHtml() {
        val saved = save(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK, null)
        assertTrue(saved.isHtmlMissing)
        assertEquals(SaveStatus.PARTIAL, saved.status)
    }

    // Audit 2026-11-20 低1: the same fallen thread is PARTIAL with the raw-HTML setting off too.
    @Test fun withoutTheRawHtmlPicturesGoneFromTheServerArePartlySaved() {
        val saved = save(HttpStatusCode.NotFound, HttpStatusCode.NotFound, "https://may.2chan.net/b/src/1.jpg", rawHtml = false)
        assertFalse(saved.isHtmlMissing)
        assertEquals(SaveStatus.PARTIAL, saved.status)
    }

    // Audit 2026-11-20 低2: a picture failing for another reason is a real failure even on a fallen thread.
    @Test fun aPictureFailingForAnotherReasonStillFails() {
        val saved = save(HttpStatusCode.NotFound, HttpStatusCode.Forbidden, "https://may.2chan.net/b/src/1.jpg")
        assertEquals(SaveStatus.FAILED, saved.status)
    }

    // Audit 2026-11-20 round 2 中1: a full-image save goes through the shared original-media store, whose 404
    // arrives wrapped (OriginalMediaSaveFailure → coil HttpException). It must still count as gone from the server.
    @Test fun aWrappedOriginalMedia404CountsAsGone() {
        val http = coil3.network.HttpException(coil3.network.NetworkResponse(code = 404))
        val wrapped = com.valoser.futacha.shared.util.OriginalMediaSaveFailure(http)
        assertEquals(404, threadSaveHttpStatusInCauses(wrapped))
        assertEquals(410, threadSaveHttpStatusInCauses(
            com.valoser.futacha.shared.util.OriginalMediaSaveFailure(coil3.network.HttpException(coil3.network.NetworkResponse(code = 410)))))
        assertNull(threadSaveHttpStatusInCauses(com.valoser.futacha.shared.util.OriginalMediaSaveFailure(okio.IOException("disk"))))
    }

    // Audit 2026-11-20 round 2 低2 (decided): a live thread whose pictures are all gone from the server keeps its posts,
    // so it is "partly saved", not "failed".
    @Test fun aLiveThreadWhosePicturesAreAllGoneIsPartlySaved() {
        val saved = save(HttpStatusCode.OK, HttpStatusCode.NotFound, "https://may.2chan.net/b/src/1.jpg")
        assertEquals(SaveStatus.PARTIAL, saved.status)
    }

    // The same through a real save: the client carries the shared original-media store, as in the app.
    @Test fun aFullSaveThroughTheSharedOriginalStoreCountsA404AsGone() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("gone-original").toFile()
        val store = com.valoser.futacha.shared.media.source.OriginalMediaStore("gone-test", {
            coil3.disk.DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, com.valoser.futacha.shared.media.source.OriginalMediaDownloader { _, _ ->
            throw coil3.network.HttpException(coil3.network.NetworkResponse(code = 404))
        })
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith(".htm")) respond("", HttpStatusCode.NotFound)
            else respond("", HttpStatusCode.NotFound)
        })
        try {
            client.bindOriginalMediaSource(store)
            val saved = ThreadSaveService(client, InMemoryFileSystem()).saveThread("1", "b", "may/b", "https://may.2chan.net/b/",
                "Test", null, listOf(post("https://may.2chan.net/b/src/1.jpg")), baseDirectory = "manual", writeMetadata = true,
                rawHtmlOptions = RawHtmlSaveOptions(enable = false)).getOrThrow()
            assertEquals(SaveStatus.PARTIAL, saved.status)
        } finally { client.close(); directory.deleteRecursively() }
    }
}
