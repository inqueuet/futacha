package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class MediaOnlyFolderSaveTest {
    private class Batch : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Batch>
    }

    @Test
    fun folderSaveSharesOneIndexAcrossFilesAndCreatesANewIndexForTheNextSave() = runBlocking {
        val backing = InMemoryFileSystem()
        val batches = mutableListOf<Batch>()
        val writes = mutableListOf<Batch?>()
        val fs = object : FileSystem by backing {
            override fun saveBatchContext(): CoroutineContext = Batch().also { batches += it }
            override suspend fun writeByteStreamReplacing(base: SaveLocation, relativePath: String,
                block: suspend (FileWriteSink) -> Unit): Result<String> {
                writes += currentCoroutineContext()[Batch]
                return backing.writeByteStreamReplacing(base, relativePath, block)
            }
        }
        val page = ThreadPage("1", "b", null, null, (0..2).map {
            Post(id = "$it", order = it, author = "", subject = "", timestamp = "", messageHtml = "",
                imageUrl = "https://example.invalid/$it.jpg", thumbnailUrl = null)
        })
        HttpClient(MockEngine { request ->
            val body = request.url.encodedPath.encodeToByteArray()
            respond(body, headers = headersOf(HttpHeaders.ContentLength, body.size.toString()))
        }).use { client ->
            repeat(2) {
                val result = runFutachaMediaOnlySave(FutachaMediaOnlySaveKind.FOLDER, page, "b", "may", "test",
                    SaveLocation.Path("manual"), client, fs, MutableStateFlow<SaveProgress?>(null))
                assertTrue("成功3件 / 失敗0件" in result, result)
            }
        }
        assertEquals(2, batches.size)
        assertEquals<List<Batch?>>(List(3) { batches[0] } + List(3) { batches[1] }, writes)
        val folder = buildCompatManualImageFolderName("may", "test", "1")
        repeat(3) { assertContentEquals("/$it.jpg".encodeToByteArray(), backing.readBytes("manual/$folder/$it.jpg").getOrThrow()) }
    }

    /** L74: a folder save with no media says so (as ふたちゃ did, now in every mode), and reports what it saved. */
    @Test
    fun aFolderSaveWithoutMediaSaysSoAndASaveReportsEachFileSaved() = runBlocking {
        val fs = InMemoryFileSystem()
        HttpClient(MockEngine { request ->
            val body = request.url.encodedPath.encodeToByteArray()
            respond(body, headers = headersOf(HttpHeaders.ContentLength, body.size.toString()))
        }).use { client ->
            val saver = com.valoser.futacha.shared.service.SingleMediaSaveService(client, fs)
            assertEquals(COMPAT_NO_MEDIA_TO_SAVE_MESSAGE, saveCompatMediaFolder(emptyList(), saver, "b", "1", "may", "test",
                SaveLocation.Path("manual"), fs, onProgress = {}))
            val saved = mutableListOf<Int>()
            val result = saveCompatMediaFolder(listOf("https://example.invalid/1.jpg", "https://example.invalid/2.jpg"), saver,
                "b", "1", "may", "test", SaveLocation.Path("manual"), fs, onProgress = {}, onSaved = { saved += it })
            assertTrue("成功2件 / 失敗0件" in result, result)
            assertEquals(listOf(1, 2), saved)
        }
    }
}
