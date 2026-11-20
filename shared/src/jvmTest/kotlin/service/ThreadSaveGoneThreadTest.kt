package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

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
}
