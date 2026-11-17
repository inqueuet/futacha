@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.net.Uri
import android.app.ActivityManager
import android.app.NotificationManager
import android.Manifest
import android.content.Context
import android.provider.DocumentsContract
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.service.AndroidProtectedThreadSaveRegistry
import com.valoser.futacha.shared.service.AndroidThreadSaveForegroundService
import com.valoser.futacha.shared.service.ThreadSaveAlreadyRunningException
import com.valoser.futacha.shared.service.runExclusiveUserThreadSave
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.ftutter.FtutterHtmlSaveDialog
import com.valoser.futacha.shared.util.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestName
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Drives the real HTML-to-ZIP dialog, pausing only an archive read. */
class FtutterHtmlSaveProgressInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = TestName()
    private val saveTitle get() = "audit-${testName.methodName.hashCode()}"
    private val db = "audit_m25_html.db"
    private lateinit var actual: FileSystem
    private lateinit var fs: BlockingZipFs
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var client: HttpClient
    private var shown by mutableStateOf(true)
    private val dismissed = AtomicInteger()
    private val control = Uri.parse("content://${SafRenameAuditProvider.AUTHORITY}")
    private val location = SaveLocation.TreeUri(
        DocumentsContract.buildTreeDocumentUri(SafRenameAuditProvider.AUTHORITY, "root").toString())

    @Before fun prepare() {
        val context = rule.activity
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        context.contentResolver.call(control, "grant", context.packageName, null)
        context.contentResolver.call(control, "seed", "0", null)
        context.deleteDatabase(db)
        actual = createFileSystem(context); fs = BlockingZipFs(actual)
        store = AndroidCompatibilityStore(context, databaseName = db)
        runBlocking { store.initialize() }
        client = HttpClient(MockEngine { respond("<html><body><blockquote>local thread</blockquote></body></html>",
            headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8")) })
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        if (fs.holdZip && fs.blocked.isCompleted) runBlocking { withTimeout(5_000) { fs.zipCleaned.await() } }
        client.close(); runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db)
        rule.activity.contentResolver.call(control, "seed", "0", null)
    }
    private fun open() {
        val preferences = mapOf(compatPreferenceStorageKey("storage", "dummyDownloadDir") to location.toRawString())
        val features = FutachaSharedFeatures(store, mutableStateOf(preferences), client,
            repository = null, fileSystem = fs, cookieRepository = null, appVersion = "test", openSettings = {})
        val page = ThreadPage("100", null, null, null, listOf(
            Post("100", author = null, subject = null, timestamp = "", messageHtml = "local thread", imageUrl = null, thumbnailUrl = null)))
        rule.setContent {
            MaterialTheme {
                if (shown) FtutterHtmlSaveDialog(features, page, "b", "board", "https://img.2chan.net/b/", saveTitle,
                    preferences, save = { _, _ -> }, onDismiss = { shown = false; dismissed.incrementAndGet() })
                else Text("closed")
            }
        }
        rule.onNodeWithText("保存").performClick()
    }
    @Test fun activeZipShowsAnUnfinishedPhaseAndCanBeCancelledWithoutLosingHtml() {
        fs.holdZip = true
        open()
        rule.waitUntil(15_000) { fs.blocked.isCompleted }
        rule.onNodeWithText("キャンセル").assertIsDisplayed()
        rule.onNodeWithText("100%").assertDoesNotExist()
        rule.onNodeWithText("ZIPを作成しています").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("ZIPの作成を中止しました（HTMLは保存済みです）", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val zip = requireNotNull(fs.zipPath.get())
        runBlocking {
            assertFalse(actual.exists(location, zip))
            assertTrue(actual.exists(location, "${zip.removeSuffix(".zip")}/metadata.json"))
        }
        assertEquals(0, dismissed.get())
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { dismissed.get() == 1 }
    }
    @Test fun completedZipProducesTheResultAndItsCloseButtonWorks() {
        open()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("ZIP:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val zip = requireNotNull(fs.zipPath.get())
        val bytes = runBlocking { actual.readBytes(location, zip).getOrThrow() }
        assertTrue(bytes.size > 100)
        assertArrayEquals(byteArrayOf(0x50, 0x4b, 3, 4), bytes.copyOf(4))
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { dismissed.get() == 1 }
        rule.onNodeWithText("closed").assertIsDisplayed()
    }
    @Test fun zipRetainsTheCrossModeSaveGateUntilCancellationAndThenReleasesIt() {
        fs.holdZip = true
        open()
        rule.waitUntil(15_000) { fs.blocked.isCompleted }
        val rejection = runBlocking { runCatching { runExclusiveUserThreadSave { "unexpected" } }.exceptionOrNull() }
        assertTrue("ZIP must still own the shared save gate", rejection is ThreadSaveAlreadyRunningException)
        val session = AndroidProtectedThreadSaveRegistry.sessions.value.values.single()
        assertTrue(session.progress.value!!.getOverallProgressPercentage() < 100)
        assertEquals("ZIPを作成しています", session.progress.value!!.currentItem)
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { AndroidProtectedThreadSaveRegistry.sessions.value.isEmpty() }
        assertEquals("available", runBlocking { runExclusiveUserThreadSave { "available" } })
    }
    @Test fun backgroundZipCanBeCancelledFromItsForegroundNotification() {
        fs.holdZip = true
        open()
        rule.waitUntil(15_000) { fs.blocked.isCompleted }
        val context = rule.activity.applicationContext
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val cancel = runBlocking {
            withTimeout(5_000) {
                var action: android.app.Notification.Action? = null
                while (action == null) {
                    action = notifications.activeNotifications
                        .filter { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() == saveTitle }
                        .flatMap { it.notification.actions?.toList().orEmpty() }
                        .firstOrNull { it.title.toString() == "キャンセル" }
                    if (action == null) delay(25)
                }
                action
            }
        }
        val services = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        assertTrue(services.getRunningServices(100).any {
            it.service.className == AndroidThreadSaveForegroundService::class.java.name && it.foreground
        })
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        try {
            assertEquals(1, AndroidProtectedThreadSaveRegistry.sessions.value.size)
            val delivery = CompletableDeferred<String>()
            cancel.actionIntent.send(context, 0, null, { _, intent, _, _, _ ->
                delivery.complete("${intent.action}: ${intent.getStringExtra(AndroidThreadSaveForegroundService.EXTRA_SESSION_ID)}")
            }, android.os.Handler(android.os.Looper.getMainLooper()))
            runBlocking { withTimeout(5_000) {
                val delivered = delivery.await()
                try {
                    withTimeout(3_000) {
                        fs.zipCleaned.await()
                        while (AndroidProtectedThreadSaveRegistry.sessions.value.isNotEmpty()) delay(25)
                    }
                } catch (failure: TimeoutCancellationException) {
                    throw AssertionError("Notification delivered $delivered; active=${AndroidProtectedThreadSaveRegistry.sessions.value.keys}; ZIP cleaned=${fs.zipCleaned.isCompleted}", failure)
                }
            } }
        } finally { rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("ZIPの作成を中止しました（HTMLは保存済みです）", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val zip = requireNotNull(fs.zipPath.get())
        runBlocking {
            assertFalse(actual.exists(location, zip))
            assertTrue(actual.exists(location, "${zip.removeSuffix(".zip")}/metadata.json"))
        }
    }
    /** L13: a ZIP that cannot be written keeps the saved HTML in the result and explains without the raw text. */
    @Test fun aZipFailureKeepsTheSavedHtmlInTheResult() {
        fs.failZip = true
        open()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("ZIPは作成できませんでした", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("ZIPは作成できませんでした：保存先を確認してください", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("File not found", substring = true).assertCountEquals(0)
        val zip = requireNotNull(fs.zipPath.get())
        runBlocking { assertTrue(actual.exists(location, "${zip.removeSuffix(".zip")}/metadata.json")) }
    }
    private class BlockingZipFs(private val delegate: FileSystem) : FileSystem by delegate {
        var holdZip = false
        var failZip = false
        val blocked = CompletableDeferred<Unit>()
        val zipCleaned = CompletableDeferred<Unit>()
        val zipPath = AtomicReference<String?>()
        private val writingZip = AtomicBoolean(false)
        override suspend fun writeByteStream(base: SaveLocation, relativePath: String, block: suspend (FileWriteSink) -> Unit): Result<Unit> {
            if (!relativePath.endsWith(".zip")) return delegate.writeByteStream(base, relativePath, block)
            zipPath.set(relativePath); writingZip.set(true)
            if (failZip) { writingZip.set(false); return Result.failure(java.io.FileNotFoundException("File not found: $relativePath")) }
            return try { delegate.writeByteStream(base, relativePath, block) }
            finally { writingZip.set(false) }
        }
        override suspend fun readBytes(base: SaveLocation, relativePath: String): Result<ByteArray> {
            if (holdZip && writingZip.get() && relativePath.endsWith("/metadata.json")) {
                blocked.complete(Unit); awaitCancellation()
            }
            return delegate.readBytes(base, relativePath)
        }
        override suspend fun delete(base: SaveLocation, relativePath: String): Result<Unit> {
            val result = delegate.delete(base, relativePath)
            if (relativePath == zipPath.get()) zipCleaned.complete(Unit)
            return result
        }
    }
}
