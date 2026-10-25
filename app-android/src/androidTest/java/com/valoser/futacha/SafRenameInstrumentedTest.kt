package com.valoser.futacha

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.SingleMediaSaveService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

/** Counts real framework provider checks, which are absent from ContentProvider query stats. */
class SafRenameInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private class CountingContext(base: Context) : ContextWrapper(base) {
        val packageManagerLookups = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun getPackageManager(): PackageManager {
            packageManagerLookups.incrementAndGet()
            return super.getPackageManager()
        }
    }
    private val counted = CountingContext(context)
    private val fs = createFileSystem(counted)
    private val control = Uri.parse("content://${SafRenameAuditProvider.AUTHORITY}")
    private val location = SaveLocation.TreeUri(
        DocumentsContract.buildTreeDocumentUri(SafRenameAuditProvider.AUTHORITY, "root").toString())
    @Before fun grantTree() {
        context.contentResolver.call(control, "grant", context.packageName, null)
    }
    @After fun clearFixture() { context.contentResolver.call(control, "seed", "0", null) }

    @Test fun freshRenameChecksCreateDocumentsOnlyForTheRequestedNames() = runBlocking<Unit> {
        suspend fun measure(rows: Int): Int {
            context.contentResolver.call(control, "seed", rows.toString(), null)
            context.contentResolver.call(control, "resetCounts", null, null)
            counted.packageManagerLookups.set(0)
            withContext(fs.saveBatchContext()) {
                repeat(10) { index ->
                    assertEquals("done-$index.jpg", fs.renameIfAbsent(location, "$index.jpg", "done-$index.jpg").getOrThrow())
                }
            }
            val lookups = counted.packageManagerLookups.get()
            val stats = requireNotNull(context.contentResolver.call(control, "stats", null, null))
            Log.i("AuditM22", "rows=$rows renames=10 packageManagerLookups=$lookups listings=${stats.getInt("listings")}")
            assertEquals("Every rename must still check outside collisions afresh", 10, stats.getInt("listings"))
            repeat(10) { index ->
                assertArrayEquals(byteArrayOf(index.toByte()), fs.readBytes(location, "done-$index.jpg").getOrThrow())
                assertFalse(fs.exists(location, "$index.jpg"))
            }
            return lookups
        }
        val small = measure(20)
        val large = measure(200)
        assertTrue("Package-manager lookups must not grow with unrelated rows: $small -> $large", large <= small + 10)
    }

    /** L71: a single save at the folder's root asks for its own name, not for every file already there. */
    @Test fun singleRootSaveDoesNotScanTheWholeFolder() = runBlocking<Unit> {
        val payload = byteArrayOf(7, 8, 9)
        HttpClient(MockEngine { respond(payload, headers = headersOf(HttpHeaders.ContentLength, "3")) }).use { client ->
            val saver = SingleMediaSaveService(client, fs)
            suspend fun measure(rows: Int): Pair<Int, Int> {
                context.contentResolver.call(control, "seed", rows.toString(), null)
                context.contentResolver.call(control, "resetCounts", null, null)
                counted.packageManagerLookups.set(0)
                val saved = saver.saveMedia("https://example.invalid/new.jpg", "b", "1", baseSaveLocation = location,
                    storageDirectoryOverride = "", useTypeSubdirectory = false).getOrThrow()
                val queries = requireNotNull(context.contentResolver.call(control, "stats", null, null)).getInt("queries")
                val lookups = counted.packageManagerLookups.get()
                assertArrayEquals(payload, fs.readBytes(location, saved.relativePath).getOrThrow())
                Log.i("AuditL71", "rows=$rows queries=$queries packageManagerLookups=$lookups")
                return queries to lookups
            }
            val small = measure(20)
            val large = measure(200)
            assertTrue("A root save must not grow with the files already there: $small -> $large",
                large.first <= small.first + 4 && large.second <= small.second + 4)
        }
    }

    /** L73: a file renamed outside the app during a save (its ID kept) is not deleted as the old file. */
    @Test fun replacingKeepsAFileRenamedOutsideTheSave() = runBlocking<Unit> {
        context.contentResolver.call(control, "seed", "3", null)
        withContext(fs.saveBatchContext()) {
            assertTrue(fs.exists(location, "0.jpg"))
            context.contentResolver.call(control, "renameOutsideKeepingId", "0.jpg", Bundle().apply { putString("to", "kept.jpg") })
            assertEquals("0.jpg", fs.writeByteStreamReplacing(location, "0.jpg") { it.write(byteArrayOf(42)) }.getOrThrow())
        }
        assertArrayEquals(byteArrayOf(0), fs.readBytes(location, "kept.jpg").getOrThrow())
        assertArrayEquals(byteArrayOf(42), fs.readBytes(location, "0.jpg").getOrThrow())
    }

    @Test fun outsideCollisionIsStillProtectedAndRecordedInTheBatch() = runBlocking<Unit> {
        context.contentResolver.call(control, "seed", "20", null)
        withContext(fs.saveBatchContext()) {
            assertFalse(fs.exists(location, "outside.jpg"))
            context.contentResolver.call(control, "addOutsideTarget", "outside.jpg", null)
            assertNull(fs.renameIfAbsent(location, "0.jpg", "outside.jpg").getOrThrow())
            assertTrue(fs.exists(location, "outside.jpg"))
            assertArrayEquals(byteArrayOf(99), fs.readBytes(location, "outside.jpg").getOrThrow())
            assertArrayEquals(byteArrayOf(0), fs.readBytes(location, "0.jpg").getOrThrow())
        }
    }
}
