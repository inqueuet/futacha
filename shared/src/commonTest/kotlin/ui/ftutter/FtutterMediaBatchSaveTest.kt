package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.runProtectedThreadSave
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/** L140 (rest of M26/M27): the ftutter gallery selection save gets the other modes' safeguards. */
class FtutterMediaBatchSaveTest {
    private val location = SaveLocation.TreeUri("content://tree/chosen")
    private class Batch : AbstractCoroutineContextElement(Key) { companion object Key : CoroutineContext.Key<Batch> }
    private class Fs(val backing: InMemoryFileSystem = InMemoryFileSystem()) : FileSystem by backing {
        var writable = true
        var batches = 0
        val batch = Batch()
        override fun saveBatchContext(): CoroutineContext { batches++; return batch }
        val withoutIndex = mutableListOf<String>()
        override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> {
            if (kotlin.coroutines.coroutineContext[Batch] == null) withoutIndex += relativePath
            return if (writable) backing.createDirectory(base, relativePath) else Result.failure(IllegalStateException("permission lost"))
        }
    }
    private val urls = listOf("https://example.invalid/1.jpg", "https://example.invalid/2.jpg", "https://example.invalid/3.jpg")

    @Test fun oneBatchIndexSavesTheSelectionAndCountsFailedFiles() = runBlocking {
        val fs = Fs()
        HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("2.jpg")) respondError(HttpStatusCode.NotFound) else respond(byteArrayOf(1, 2, 3))
        }).use { client ->
            val result = ftutterSaveMediaBatch(SingleMediaSaveService(client, fs), fs, urls, "b", "1", location, "スレ") {}
            assertEquals(FtutterBatchSaveResult(saved = 2, failed = 1), result)
            assertEquals(1, fs.batches)
            assertEquals(emptyList(), fs.withoutIndex, "every destination access uses the one batch index")
            assertEquals("2個のファイルを保存しました（1個は保存できませんでした）", ftutterBatchSaveMessage(result))
        }
    }

    @Test fun aLostDestinationStopsBeforeDownloadingTheRestAndExplainsTheFix() = runBlocking {
        val fs = Fs().apply { writable = false }
        var requests = 0
        HttpClient(MockEngine { requests++; respond(byteArrayOf(1)) }).use { client ->
            val result = ftutterSaveMediaBatch(SingleMediaSaveService(client, fs), fs, urls, "b", "1", location, "スレ") {}
            assertEquals(0, requests, "no media is downloaded into an unwritable folder")
            assertEquals(0, result.saved)
            assertEquals(3, result.failed)
            assertTrue(ftutterBatchSaveMessage(result).contains("選び直し"), ftutterBatchSaveMessage(result))
        }
    }

    @Test fun anotherRunningSaveIsReportedInsteadOfAnEmptySuccess() = runBlocking {
        val fs = Fs()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val other = async(start = CoroutineStart.UNDISPATCHED) {
            runProtectedThreadSave("別の保存", MutableStateFlow<SaveProgress?>(null)) { holding.complete(Unit); release.await() }
        }
        holding.await()
        try {
            HttpClient(MockEngine { respond(byteArrayOf(1)) }).use { client ->
                val result = ftutterSaveMediaBatch(SingleMediaSaveService(client, fs), fs, urls, "b", "1", location, "スレ") {}
                assertEquals(0, result.saved)
                assertEquals("別の保存を実行中です", ftutterBatchSaveMessage(result))
            }
        } finally { release.complete(Unit); other.await() }
    }

    @Test fun messagesKeepTheOrdinarySuccessText() {
        assertEquals("3個のファイルを保存しました", ftutterBatchSaveMessage(FtutterBatchSaveResult(3, 0)))
        assertEquals("保存先に書き込めません（1個は保存しました）", ftutterBatchSaveMessage(FtutterBatchSaveResult(1, 2, "保存先に書き込めません")))
    }
}
