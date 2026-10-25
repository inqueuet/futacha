package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.FileWriteSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class MediaFolderDestinationFailureTest {
    private val base = SaveLocation.TreeUri("content://tree/chosen")
    private val page = ThreadPage("1", "b", null, null, (1..3).map {
        Post("$it", author = null, subject = null, timestamp = "", messageHtml = "",
            imageUrl = "https://example.invalid/$it.jpg", thumbnailUrl = null)
    })
    @Test fun inaccessibleFolderAbortsTheBatchBeforeDownloadingAnyOfItsMedia() = runBlocking {
        checkDestinationLoss(afterFirst = false)
    }
    @Test fun folderAccessLostDuringTheBatchKeepsCompletedBytesAndStopsFurtherDownloads() = runBlocking {
        checkDestinationLoss(afterFirst = true)
    }
    private suspend fun checkDestinationLoss(afterFirst: Boolean) {
        val backing = InMemoryFileSystem()
        var writes = 0
        var requests = 0
        val fs = object : FileSystem by backing {
            override suspend fun createDirectory(base: SaveLocation, relativePath: String): Result<Unit> =
                if (afterFirst && writes == 0) backing.createDirectory(base, relativePath)
                else Result.failure(IllegalStateException("permission lost"))
            override suspend fun writeByteStreamReplacing(base: SaveLocation, relativePath: String,
                block: suspend (FileWriteSink) -> Unit): Result<String> {
                if (!afterFirst || writes > 0) return Result.failure(IllegalStateException("permission lost"))
                return backing.writeByteStreamReplacing(base, relativePath, block).also { if (it.isSuccess) writes++ }
            }
        }
        HttpClient(MockEngine { requests++; respond(byteArrayOf(1, 2, 3)) }).use { client ->
            val result = runCatching {
                runFutachaMediaOnlySave(FutachaMediaOnlySaveKind.FOLDER, page, "b", "board", "title",
                    base, client, fs, MutableStateFlow<SaveProgress?>(null))
            }
            assertTrue(result.isFailure, "A destination failure must reach the result UI instead of a reasonless count")
            assertEquals(if (afterFirst) 1 else 0, requests)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("選び直し"))
        }
        if (afterFirst) {
            val folder = buildCompatManualImageFolderName("board", "title", "1")
            assertContentEquals(byteArrayOf(1, 2, 3), backing.readBytes(base, "$folder/1.jpg").getOrThrow())
        }
    }
}
