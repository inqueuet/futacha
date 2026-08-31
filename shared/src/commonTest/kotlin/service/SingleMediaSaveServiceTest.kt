package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SingleMediaSaveServiceTest {
    @Test
    fun saveMedia_rejectsNonHttpUrls() = runBlocking {
        val service = SingleMediaSaveService(
            httpClient = createClient { error("network should not be used") },
            fileSystem = InMemoryFileSystem()
        )

        val result = service.saveMedia(
            mediaUrl = "file:///tmp/test.jpg",
            boardId = "b",
            threadId = "777",
            baseDirectory = "manual"
        )

        val error = assertFailsWith<IllegalArgumentException> { result.getOrThrow() }
        assertTrue(error.message!!.contains("保存に対応"))
    }

    @Test
    fun saveMedia_savesImageUnderPreviewMediaDirectory_forPathStorage() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = "jpeg-binary".encodeToByteArray()
        val service = SingleMediaSaveService(
            httpClient = createClient {
                htmlBinaryResponse(
                    body = payload,
                    contentType = "image/jpeg",
                    contentLength = payload.size.toLong()
                )
            },
            fileSystem = fileSystem
        )

        val result = service.saveMedia(
            mediaUrl = "https://img.2chan.net/src/cat photo.jpg?foo=1",
            boardId = "img",
            threadId = "777",
            baseDirectory = "manual"
        ).getOrThrow()

        val storageId = buildThreadStorageId("img", "777")
        assertEquals(SavedMediaType.IMAGE, result.mediaType)
        assertEquals(payload.size.toLong(), result.byteSize)
        assertTrue(result.relativePath.startsWith("$storageId/preview_media/images/cat_photo_"))
        assertTrue(result.relativePath.endsWith(".jpg"))
        assertTrue(fileSystem.exists("manual/${result.relativePath}"))
    }

    @Test
    fun saveMedia_usesContentTypeFallbackAndTreeUriStorage_forVideos() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = "video-bytes".encodeToByteArray()
        val base = SaveLocation.TreeUri("content://tree/root")
        val service = SingleMediaSaveService(
            httpClient = createClient {
                htmlBinaryResponse(
                    body = payload,
                    contentType = "video/webm",
                    contentLength = payload.size.toLong()
                )
            },
            fileSystem = fileSystem
        )

        val result = service.saveMedia(
            mediaUrl = "https://img.2chan.net/bin/download",
            boardId = "img",
            threadId = "888",
            baseSaveLocation = base,
            baseDirectory = "ignored"
        ).getOrThrow()

        val storageId = buildThreadStorageId("img", "888")
        assertEquals(SavedMediaType.VIDEO, result.mediaType)
        assertTrue(result.relativePath.startsWith("$storageId/preview_media/videos/download_"))
        assertTrue(result.relativePath.endsWith(".webm"))
        assertTrue(fileSystem.exists(base, result.relativePath))
    }

    @Test
    fun compatibilityManualSaveCanTargetRootOrReadableBatchFolder() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = "image".encodeToByteArray()
        val service = SingleMediaSaveService(
            httpClient = createClient {
                htmlBinaryResponse(payload, "image/jpeg", payload.size.toLong())
            },
            fileSystem = fileSystem
        )

        val root = service.saveMedia(
            mediaUrl = "https://may.2chan.net/b/src/123.jpg",
            boardId = "b",
            threadId = "777",
            baseDirectory = "manual",
            storageDirectoryOverride = "",
            useTypeSubdirectory = false
        ).getOrThrow()
        assertTrue('/' !in root.relativePath)
        assertTrue(fileSystem.exists("manual/${root.relativePath}"))

        val folder = buildCompatManualImageFolderName("二次元裏", "長いスレ文", "777")
        val byteProgress = mutableListOf<Pair<Long, Long>>()
        val batched = service.saveMedia(
            mediaUrl = "https://may.2chan.net/b/src/124.jpg",
            boardId = "b",
            threadId = "777",
            baseDirectory = "manual",
            storageDirectoryOverride = folder,
            useTypeSubdirectory = false,
            outputFileNameOverride = "124(1).jpg",
            onProgress = { current, total -> byteProgress += current to total }
        ).getOrThrow()
        assertEquals("二次元裏 長いスレ 777", folder)
        assertEquals("$folder/124(1).jpg", batched.relativePath)
        assertEquals(listOf(payload.size.toLong() to payload.size.toLong()), byteProgress)
        assertTrue(fileSystem.exists("manual/${batched.relativePath}"))
    }

    @Test
    fun saveMedia_ignoresPathSeparatorsInApparentUrlExtension() = runBlocking {
        val fileSystem = InMemoryFileSystem()
        val payload = "jpeg-binary".encodeToByteArray()
        val service = SingleMediaSaveService(
            httpClient = createClient {
                htmlBinaryResponse(
                    body = payload,
                    contentType = "image/jpeg",
                    contentLength = payload.size.toLong()
                )
            },
            fileSystem = fileSystem
        )

        val result = service.saveMedia(
            mediaUrl = "https://img.2chan.net/src/photo.x/y",
            boardId = "img",
            threadId = "889",
            baseDirectory = "manual"
        ).getOrThrow()

        assertTrue(result.fileName.endsWith(".jpg"))
        assertTrue('/' !in result.fileName)
        assertTrue(fileSystem.exists("manual/${result.relativePath}"))
    }

    @Test
    fun saveMedia_failsWhenContentLengthExceedsLimit() {
        runBlocking {
        val service = SingleMediaSaveService(
            httpClient = createClient {
                htmlBinaryResponse(
                    body = ByteArray(8),
                    contentType = "image/png",
                    contentLength = 8_192_001L
                )
            },
            fileSystem = InMemoryFileSystem()
        )

        val result = service.saveMedia(
            mediaUrl = "https://img.2chan.net/src/huge.png",
            boardId = "img",
            threadId = "999",
            baseDirectory = "manual"
        )

        assertTrue(result.isFailure)
        assertFailsWith<IllegalStateException> { result.getOrThrow() }
        }
    }

    private fun createClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): HttpClient {
        val engine = MockEngine(
            MockEngineConfig().apply {
                addHandler(handler)
            }
        )
        return HttpClient(engine)
    }

    private fun MockRequestHandleScope.htmlBinaryResponse(
        body: ByteArray,
        contentType: String,
        contentLength: Long
    ): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = Headers.build {
            append(HttpHeaders.ContentType, contentType)
            append(HttpHeaders.ContentLength, contentLength.toString())
        }
    )
}
