@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.ftutter.ftutterZipSavedThread
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.coroutines.*

class FtutterZipBatchInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val actual = createFileSystem(context)
    private val fs = RecordingFs(actual)
    private val control = Uri.parse("content://${SafRenameAuditProvider.AUTHORITY}")
    private val location = SaveLocation.TreeUri(
        DocumentsContract.buildTreeDocumentUri(SafRenameAuditProvider.AUTHORITY, "root").toString())
    private val id = "ftutter-batch"
    private val sources = linkedMapOf<String, ByteArray>()
    private val mediaPaths = (2..40).mapTo(linkedSetOf()) { "media-$it.jpg" }

    @Before fun seed() = runBlocking<Unit> {
        context.contentResolver.call(control, "grant", context.packageName, null)
        context.contentResolver.call(control, "seed", "0", null)
        sources["thread.html"] = "<html>local thread</html>".encodeToByteArray()
        (1..40).forEach { number -> sources["media-$number.jpg"] = ByteArray(64) { (number + it).toByte() } }
        val metadata = SavedThreadMetadata(
            threadId = "100", boardId = "b", boardName = "board", boardUrl = "https://img.2chan.net/b/", title = "audit",
            storageId = id, savedAt = 1, expiresAtLabel = null, totalSize = 0, rawHtmlPath = "thread.html",
            posts = listOf(SavedPost("1", 1, null, null, "", "body", null, "media-1.jpg", null, null, null, null)))
        sources["metadata.json"] = Json.encodeToString(metadata).encodeToByteArray()
        withContext(actual.saveBatchContext()) {
            sources.forEach { (name, bytes) -> actual.writeBytes(location, "$id/$name", bytes).getOrThrow() }
            actual.writeBytes(location, "$id/old-unused.jpg", byteArrayOf(99)).getOrThrow()
        }
        context.contentResolver.call(control, "resetCounts", null, null)
    }
    @After fun cleanup() { context.contentResolver.call(control, "seed", "0", null) }

    @Test fun zipSharesOneIndexForMetadataSizesAndContentAndContainsOnlyThisSave() = runBlocking<Unit> {
        assertEquals("$id.zip", ftutterZipSavedThread(fs, location, id, mediaPaths))
        val stats = requireNotNull(context.contentResolver.call(control, "stats", null, null))
        Log.i("AuditM24", "files=${sources.size} providerQueries=${stats.getInt("queries")} listings=${stats.getInt("listings")}")
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(actual.readBytes(location, "$id.zip").getOrThrow())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        assertEquals(sources.keys, entries.keys)
        sources.forEach { (name, bytes) -> assertArrayEquals(name, bytes, entries[name]) }
        assertSharedBatch()
        assertTrue("Repeated SAF scans must not grow quadratically", stats.getInt("queries") < 500)
    }

    @Test fun cancellingAReadRemovesTheIncompleteZipAndKeepsEverySource() = runBlocking<Unit> {
        withTimeout(10_000) {
            fs.blockPath = "$id/media-20.jpg"
            val job = launch(Dispatchers.Default) { ftutterZipSavedThread(fs, location, id, mediaPaths) }
            fs.blocked.await()
            job.cancelAndJoin()
            assertFalse(actual.exists(location, "$id.zip"))
            sources.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual.readBytes(location, "$id/$name").getOrThrow()) }
            assertSharedBatch()
        }
    }
    private fun assertSharedBatch() {
        assertEquals("One ZIP must own one listing index", 1, fs.batches.get())
        assertTrue(fs.tokens.isNotEmpty())
        val first = fs.tokens.first()
        assertNotNull(first)
        assertTrue(fs.tokens.all { it === first })
    }
    private class Token : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Token>
    }
    private class RecordingFs(private val delegate: FileSystem) : FileSystem by delegate {
        val batches = AtomicInteger()
        val tokens = CopyOnWriteArrayList<Token?>()
        val blocked = CompletableDeferred<Unit>()
        var blockPath: String? = null
        override fun saveBatchContext(): CoroutineContext {
            batches.incrementAndGet()
            return delegate.saveBatchContext() + Token()
        }
        private suspend fun record() { tokens += coroutineContext[Token] }
        override suspend fun readString(base: SaveLocation, relativePath: String): Result<String> {
            record(); return delegate.readString(base, relativePath)
        }
        override suspend fun getFileSize(base: SaveLocation, relativePath: String): Long {
            record(); return delegate.getFileSize(base, relativePath)
        }
        override suspend fun readBytes(base: SaveLocation, relativePath: String): Result<ByteArray> {
            record()
            if (relativePath == blockPath) { blocked.complete(Unit); awaitCancellation() }
            return delegate.readBytes(base, relativePath)
        }
        override suspend fun writeByteStream(base: SaveLocation, relativePath: String, block: suspend (FileWriteSink) -> Unit): Result<Unit> {
            record(); return delegate.writeByteStream(base, relativePath, block)
        }
        override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
            record(); return delegate.delete(base, relativePath)
        }
    }
}
