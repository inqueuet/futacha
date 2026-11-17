@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.*

class CompatGalleryFolderBatchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = "audit_m23_gallery.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var fs: RecordingFs
    private lateinit var client: HttpClient
    private lateinit var loader: ImageLoader
    private lateinit var payload: ByteArray
    private val failSecond = AtomicBoolean(false)
    private val blockSecond = AtomicBoolean(false)
    private val secondEntered = CompletableDeferred<Unit>()
    private val requests = AtomicInteger()
    private val location = SaveLocation.TreeUri(
        DocumentsContract.buildTreeDocumentUri(SafRenameAuditProvider.AUTHORITY, "root").toString())
    private val control = Uri.parse("content://${SafRenameAuditProvider.AUTHORITY}")
    private val tab = CompatTab("gallery-audit", "https://may.2chan.net/b/res/100.htm", "https://may.2chan.net/b/res/100.htm",
        "b", "板", "100", "監査M23", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val folder = buildCompatManualImageFolderName(tab.boardName, tab.title, tab.threadNo)

    @Before fun prepare() {
        val context = rule.activity
        context.contentResolver.call(control, "grant", context.packageName, null)
        context.contentResolver.call(control, "seed", "0", null)
        context.deleteDatabase(db)
        store = AndroidCompatibilityStore(context, databaseName = db)
        runBlocking { store.initialize() }
        fs = RecordingFs(createFileSystem(context))
        payload = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE)
                compress(Bitmap.CompressFormat.JPEG, 85, out); recycle()
            }
        }.toByteArray()
        client = HttpClient(MockEngine { request ->
            requests.incrementAndGet()
            val number = request.url.encodedPath.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: 0
            if (number == 2 && blockSecond.get() && fs.tokens.isNotEmpty()) {
                secondEntered.complete(Unit); awaitCancellation()
            }
            if (number == 2 && failSecond.get()) respond("fixture failure", HttpStatusCode.InternalServerError)
            else respond(payload, headers = headersOf(HttpHeaders.ContentType to listOf("image/jpeg"), HttpHeaders.ContentLength to listOf(payload.size.toString())))
        })
        // All image-loader requests use the same local mock engine as saving.
        loader = ImageLoader.Builder(context).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { client.asNetworkClient() }))
        }.build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        scope.cancel(); loader.shutdown(); client.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
        rule.activity.contentResolver.call(control, "seed", "0", null)
    }
    private fun open() {
        val snapshot = CompatThreadSnapshot(tab.key, 1, 1, posts = (1..3).map {
            CompatPostSnapshot(it, it.toString(), timestamp = "", messageHtml = "本文", imageUrl = "https://img.2chan.net/b/src/$it.jpg")
        })
        val prefs = mapOf(compatPreferenceStorageKey("storage", "dummyDownloadDir") to location.toRawString())
        val markers = CompatApngMarkerCache(scope)
        rule.setContent {
            MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                CompatGalleryScreen(tab = tab, store = store, preferences = prefs, ngRules = emptyList(), httpClient = client,
                    apngMarkerCache = markers, fileSystem = fs, preparedSnapshot = snapshot,
                    onOpenViewer = { _, _ -> }, onOpenSettings = {}, onOpenCommonSettings = {}, onBack = {})
            } }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-gallery-item-3").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun saveSelected() {
        rule.onNodeWithContentDescription("一括保存").performClick()
        rule.onNodeWithText("全選択").performClick()
        rule.onNodeWithContentDescription("選択したメディアを保存").performClick()
        rule.onNodeWithText("フォルダ").performClick()
    }
    private fun awaitMessage(text: String) {
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            val roots = rule.onAllNodes(isRoot(), useUnmergedTree = true)
            repeat(roots.fetchSemanticsNodes().size) { roots[it].printToLog("AuditM23") }
            android.util.Log.i("AuditM23", "batches=${fs.batches.get()} writes=${fs.tokens.size}")
            throw failure
        }
    }
    private fun saved(number: Int): ByteArray = runBlocking { fs.readBytes(location, "$folder/$number.jpg").getOrThrow() }
    private fun assertOneBatch(tokenCount: Int) {
        assertEquals("The gallery must request one context for the entire selection", 1, fs.batches.get())
        assertEquals(tokenCount, fs.tokens.size)
        val first = fs.tokens.first()
        assertNotNull("Every stream must retain the gallery's batch context", first)
        assertTrue(fs.tokens.all { it === first })
    }
    @Test fun allSelectedFilesShareOneContextAndKeepTheirBytes() {
        open(); saveSelected(); awaitMessage("フォルダに3件を保存しました")
        (1..3).forEach { assertArrayEquals(payload, saved(it)) }
        assertOneBatch(3)
    }
    @Test fun retryKeepsSuccessfulFilesAndUsesANewContextOnlyForTheFailedSelection() {
        failSecond.set(true)
        open(); saveSelected(); awaitMessage("1件失敗しました")
        assertArrayEquals(payload, saved(1)); assertArrayEquals(payload, saved(3))
        assertOneBatch(2)
        val previous = fs.tokens.first()
        failSecond.set(false)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("失敗分を再試行").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("失敗分を再試行").performClick()
        awaitMessage("フォルダに1件を保存しました")
        (1..3).forEach { assertArrayEquals(payload, saved(it)) }
        assertEquals(2, fs.batches.get()); assertEquals(3, fs.tokens.size)
        assertNotNull(fs.tokens.last()); assertNotSame(previous, fs.tokens.last())
    }
    /** L142: a save while another runs is told as such, with nothing to retry and nothing fetched. */
    @Test fun aSaveWhileAnotherRunsIsToldAsBusyWithoutARetry() {
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val other = scope.launch {
            com.valoser.futacha.shared.service.runExclusiveUserThreadSave { holding.complete(Unit); release.await() }
        }
        try {
            runBlocking { holding.await() }
            open()
            rule.waitForIdle()
            val thumbnails = requests.get() // the grid's own images come from the same mock engine
            saveSelected(); awaitMessage("別の保存を実行中です")
            assertEquals(0, rule.onAllNodesWithText("保存できませんでした", substring = true).fetchSemanticsNodes().size)
            assertEquals(0, rule.onAllNodesWithText("失敗分を再試行").fetchSemanticsNodes().size)
            assertEquals("Nothing was fetched for the save", thumbnails, requests.get())
            assertEquals(0, fs.tokens.size)
        } finally {
            release.complete(Unit)
            runBlocking { other.join() }
        }
    }
    @Test fun cancellationKeepsTheCompletedFileAndItsCount() {
        open(); blockSecond.set(true); saveSelected()
        rule.waitUntil(15_000) {
            secondEntered.isCompleted && rule.onAllNodesWithText("2.jpg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("キャンセル").performClick()
        awaitMessage("1件のメディアをここまで保存しました")
        assertArrayEquals(payload, saved(1))
        runBlocking { assertFalse(fs.exists(location, "$folder/2.jpg")); assertFalse(fs.exists(location, "$folder/3.jpg")) }
        assertOneBatch(1)
    }
    @Test fun inaccessibleFolderShowsHowToReselectItWithoutDownloadingTheSelection() {
        open()
        rule.waitUntil(15_000) { requests.get() >= 3 }
        rule.activity.contentResolver.call(control, "setWritable", "false", null)
        requests.set(0)
        saveSelected()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("選び直し", substring = true) or hasText("失敗", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals("No selected media should be requested after the destination becomes unwritable", 0, requests.get())
        rule.onNodeWithText("選び直し", substring = true).assertIsDisplayed()
        assertEquals(0, fs.tokens.size)
    }
    private class BatchToken : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<BatchToken>
    }
    private class RecordingFs(private val delegate: FileSystem) : FileSystem by delegate {
        val batches = AtomicInteger()
        val tokens = CopyOnWriteArrayList<BatchToken?>()
        override fun saveBatchContext(): CoroutineContext {
            batches.incrementAndGet()
            return delegate.saveBatchContext() + BatchToken()
        }
        override suspend fun writeByteStreamReplacing(base: SaveLocation, relativePath: String, block: suspend (FileWriteSink) -> Unit): Result<String> {
            tokens += coroutineContext[BatchToken]
            return delegate.writeByteStreamReplacing(base, relativePath, block)
        }
    }
}
