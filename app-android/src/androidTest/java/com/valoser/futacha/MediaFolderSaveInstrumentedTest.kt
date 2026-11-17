package com.valoser.futacha

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before

class MediaFolderSaveInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fs = createFileSystem(context)
    private val control = Uri.parse("content://${MediaFolderTestProvider.AUTHORITY}")
    private val location = SaveLocation.TreeUri(
        DocumentsContract.buildTreeDocumentUri(MediaFolderTestProvider.AUTHORITY, "root").toString())
    private val payload = ByteArray(4096) { (it * 31).toByte() }

    @Before
    fun grantTestTree() {
        context.contentResolver.call(control, "grant", context.packageName, null)
    }

    @Test
    fun folderSaveProviderQueriesGrowLinearlyAndSavedBytesMatch(): Unit = runBlocking {
        HttpClient(MockEngine { respond(payload, headers = headersOf(HttpHeaders.ContentLength, payload.size.toString())) }).use { client ->
            val saver = SingleMediaSaveService(client, fs)
            suspend fun measure(count: Int, batch: Boolean = true): Pair<Int, Int> {
                val folder = "batch-$count-${System.nanoTime()}"
                try {
                    // Isolate the per-file work from unrelated folders left by other tests.
                    fs.createDirectory(location, folder).getOrThrow()
                    context.contentResolver.call(control, "resetCounts", null, null)
                    val started = SystemClock.elapsedRealtime()
                    withContext(if (batch) fs.saveBatchContext() else EmptyCoroutineContext) {
                        repeat(count) { index ->
                            saver.saveMedia("https://example.invalid/$index.jpg", "b", "1",
                                baseSaveLocation = location, storageDirectoryOverride = folder,
                                useTypeSubdirectory = false, outputFileNameOverride = "$index.jpg").getOrThrow()
                        }
                    }
                    val stats = requireNotNull(context.contentResolver.call(control, "stats", null, null))
                    val queries = stats.getInt("queries")
                    val listings = stats.getInt("listings")
                    Log.i("MediaFolderSaveTest", "files=$count batch=$batch queries=$queries listings=$listings elapsedMs=${SystemClock.elapsedRealtime() - started}")
                    assertEquals(count, fs.listFiles(location, folder).size)
                    repeat(count) { assertArrayEquals(payload, fs.readBytes(location, "$folder/$it.jpg").getOrThrow()) }
                    return queries to listings
                } finally {
                    fs.delete(location, folder).getOrThrow()
                }
            }
            val small = measure(20)
            val large = measure(40)
            val withoutBatch = measure(40, batch = false)
            assertTrue("Queries must scale with file count: $small -> $large", large.first < small.first * 3)
            assertTrue("Folder entry points must share their index: $large vs $withoutBatch", large.first * 2 < withoutBatch.first)
            // One initial listing per folder, plus one fresh collision check per rename.
            assertTrue("Unexpected repeated folder scans: $large", large.second <= 43)
        }
    }

    @Test
    fun replacementUpdatesTheBatchIndexAndKeepsThePreviousFileOnFailure(): Unit = runBlocking {
        val folder = "replace-${System.nanoTime()}"
        try {
            withContext(fs.saveBatchContext()) {
                fs.writeByteStreamReplacing(location, "$folder/image.jpg") { it.write(payload) }.getOrThrow()
                assertTrue(fs.exists(location, "$folder/image.jpg"))
                val failed = fs.writeByteStreamReplacing(location, "$folder/image.jpg") {
                    it.write(byteArrayOf(1))
                    error("interrupted transfer")
                }
                assertTrue(failed.isFailure)
                assertArrayEquals(payload, fs.readBytes(location, "$folder/image.jpg").getOrThrow())
                assertEquals("$folder/image.jpg", fs.writeByteStreamReplacing(location, "$folder/image.jpg") {
                    it.write(byteArrayOf(2, 3))
                }.getOrThrow())
                assertArrayEquals(byteArrayOf(2, 3), fs.readBytes(location, "$folder/image.jpg").getOrThrow())
                assertEquals(listOf("image.jpg"), fs.listFiles(location, folder))
            }
        } finally {
            fs.delete(location, folder).getOrThrow()
        }
    }

    @Test
    fun renameChecksForAnOutsideFileEvenWhenTheBatchCachedItsAbsence(): Unit = runBlocking {
        val folder = "collision-${System.nanoTime()}"
        try {
            withContext(fs.saveBatchContext()) {
                fs.writeBytes(location, "$folder/source.jpg", payload).getOrThrow()
                assertFalse(fs.exists(location, "$folder/target.jpg"))
                // A different save's context represents an outside change.
                withContext(fs.saveBatchContext()) {
                    fs.writeBytes(location, "$folder/target.jpg", byteArrayOf(9)).getOrThrow()
                }
                assertNull(fs.renameIfAbsent(location, "$folder/source.jpg", "$folder/target.jpg").getOrThrow())
                assertArrayEquals(byteArrayOf(9), fs.readBytes(location, "$folder/target.jpg").getOrThrow())
                assertArrayEquals(payload, fs.readBytes(location, "$folder/source.jpg").getOrThrow())
            }
        } finally {
            fs.delete(location, folder).getOrThrow()
        }
    }
}
