@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** Existing APIs only: real PNGs/drafts, deterministic file/DB gates, no server calls or audio. */
class CompatM46DrawingSaveInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m46_drawing.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/811.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var fs: FileSystem
    private lateinit var folder: File
    private lateinit var oldLocator: String
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var originalConfiguration = Configuration.ORIENTATION_PORTRAIT
    private val privateFiles = CopyOnWriteArrayList<String>()
    private val copies = AtomicInteger()
    private val saveAttempts = AtomicInteger()
    private val activeWrites = AtomicInteger()
    private val failDraft = AtomicBoolean(false)
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()

    @After fun cleanup() {
        release.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        rule.waitUntil(5_000) { activeWrites.get() == 0 }
        if (::storage.isInitialized) runBlocking { storage.closeForTest() }
        if (::fs.isInitialized) runBlocking {
            (privateFiles.toList() + if (::oldLocator.isInitialized) listOf(oldLocator) else emptyList()).distinct().forEach {
                deleteCompatPostAttachment(fs, it, deleteContainer = true)
            }
        }
        rule.activity.deleteDatabase(db)
        if (::folder.isInitialized) folder.deleteRecursively()
        val restored = if (originalConfiguration == Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        rule.runOnUiThread { rule.activity.requestedOrientation = restored }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == originalConfiguration }
        rule.runOnUiThread { rule.activity.requestedOrientation = originalOrientation }
    }

    /**
     * L39 (replacing M46's wait for the copy, by the decision): the reply's drawing is attached and the post screen
     * returns while the PNG copy is still held; the copy is written afterwards. The draft commit is still waited for
     * (the build test below).
     */
    @Test fun replyAttachesWithoutWaitingForThePngCopyAndWritesItAfterwards() {
        prepare(build = false, holdCopy = true)
        saveDrawing()
        rule.waitUntil(10_000) { started.isCompleted }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        assertTrue("The copy is still held", folder.listFiles().orEmpty().none { it.isFile })
        release.complete(Unit)
        rule.waitUntil(10_000) { folder.listFiles().orEmpty().any { it.isFile } }
        assertCompleted(build = false)
    }

    @Test fun buildWaitsForItsDraftCommitBeforeCopyingAndReturning() {
        prepare(build = true, holdDraft = true)
        saveDrawing()
        rule.waitUntil(10_000) { started.isCompleted }
        assertEquals("Do not create an external copy before the draft commit", 0, copies.get())
        assertBusyAndBackIgnored()
        assertEquals(1, saveAttempts.get())
        release.complete(Unit)
        assertCompleted(build = true)
    }

    @Test fun failedDraftCommitKeepsTheDrawingAndOldAttachmentAndRetryCreatesOneCopy() {
        failDraft.set(true)
        prepare(build = false)
        saveDrawing()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("手書き画像を保存できませんでした", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-drawing-canvas").assertIsDisplayed()
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()
        assertEquals(oldLocator, runBlocking { storage.loadDraft(tabKey) }?.attachmentUri)
        assertEquals(0, copies.get())
        assertTrue(runBlocking { fs.exists(oldLocator) })
        assertTrue(privateFiles.all { !runBlocking { fs.exists(it) } })
        failDraft.set(false)
        saveDrawing()
        assertCompleted(build = false)
    }

    private fun prepare(build: Boolean, holdCopy: Boolean = false, holdDraft: Boolean = false) {
        originalOrientation = rule.activity.requestedOrientation
        originalConfiguration = rule.activity.resources.configuration.orientation
        rule.runOnUiThread { rule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        rule.waitUntil(5_000) { rule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        folder = File(rule.activity.cacheDir, "audit-m46-copy").apply { mkdirs() }
        val realFs = createFileSystem(rule.activity)
        fs = object : FileSystem by realFs {
            override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> {
                if (path.startsWith("private/compat_post_attachments/")) privateFiles += path
                return realFs.writeBytes(path, bytes)
            }
            override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
                copies.incrementAndGet()
                activeWrites.incrementAndGet()
                return try {
                    if (holdCopy) { started.complete(Unit); release.await() }
                    realFs.writeBytes(base, relativePath, bytes)
                } finally { activeWrites.decrementAndGet() }
            }
        }
        rule.activity.deleteDatabase(db)
        // L69: a drawing another test left on the real files would be offered before anything could be drawn.
        clearCompatDrawingRecovery(rule.activity)
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking {
            storage.initialize()
            storage.savePreference("compat.commonUsedVersion", "9999.0")
            storage.savePreference(compatPreferenceStorageKey("storage", "dummyDrawingDir"), folder.absolutePath)
            storage.upsertBoard(CompatBoard(boardKey, "M46", boardUrl, boardUrl, 0))
            storage.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "M46", "811", "M46 thread",
                insertedAtEpochMillis = 811, contentUpdatedAtEpochMillis = 811, snapshotRevision = 811))
            storage.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 811, 811,
                posts = listOf(CompatPostSnapshot(0, "811", timestamp = "", messageHtml = "M46 post"))))
            val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
            val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
            bitmap.recycle()
            oldLocator = persistCompatPostAttachment(realFs, if (build) "build:$boardKey" else tabKey, ImageData(bytes, "old.png")).getOrThrow()
            if (build) storage.saveBuildDraft(CompatBuildDraft(boardKey, comment = "keep text", attachmentUri = oldLocator, updatedAtEpochMillis = 1))
            else storage.saveDraft(CompatReplyDraft(tabKey, comment = "keep text", attachmentUri = oldLocator, updatedAtEpochMillis = 1))
        }
        val wrapped = object : CompatibilityStore by storage {
            private suspend fun beforeSave(handwriting: Boolean) {
                if (!handwriting) return
                saveAttempts.incrementAndGet()
                activeWrites.incrementAndGet()
                try {
                    if (holdDraft) { started.complete(Unit); release.await() }
                    if (failDraft.get()) throw IOException("test draft disk full")
                } finally { activeWrites.decrementAndGet() }
            }
            override suspend fun saveDraft(draft: CompatReplyDraft) { beforeSave(draft.attachmentIsHandwriting); storage.saveDraft(draft) }
            override suspend fun saveBuildDraft(draft: CompatBuildDraft) { beforeSave(draft.attachmentIsHandwriting); storage.saveBuildDraft(draft) }
        }
        rule.setContent { MaterialTheme {
            CompatibilityApp(wrapped, repository = null, fileSystem = fs,
                initialBoardDeepLink = boardUrl.takeIf { build }, initialThreadDeepLink = threadUrl.takeUnless { build }, onExitApplication = {})
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-toolbar-command-post").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-post").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("手書き").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("keep text", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("手書き").performClick()
        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            swipe(Offset(width * 0.25f, height * 0.3f), Offset(width * 0.75f, height * 0.7f), 200)
        }
    }

    private fun saveDrawing() {
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("保存する").performClick()
    }
    private fun assertBusyAndBackIgnored() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onAllNodesWithText("画像が保存されていません", substring = true).assertCountEquals(0)
        rule.onNodeWithTag("compat-drawing-canvas").assertIsDisplayed()
        rule.onNodeWithContentDescription("その他").assertIsNotEnabled()
        rule.onNodeWithContentDescription("元に戻す").assertIsNotEnabled()
    }
    private fun assertCompleted(build: Boolean) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-drawing-canvas").fetchSemanticsNodes().isEmpty() }
        val (comment, locator, handwriting) = runBlocking {
            if (build) checkNotNull(storage.loadBuildDraft(boardKey)).let { Triple(it.comment, it.attachmentUri, it.attachmentIsHandwriting) }
            else checkNotNull(storage.loadDraft(tabKey)).let { Triple(it.comment, it.attachmentUri, it.attachmentIsHandwriting) }
        }
        assertEquals("keep text", comment)
        assertTrue(handwriting)
        assertNotEquals(oldLocator, locator)
        val bytes = runBlocking { fs.readBytes(checkNotNull(locator)).getOrThrow() }
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(344, bitmap.width); assertEquals(135, bitmap.height); bitmap.recycle()
        assertEquals(1, copies.get())
        val copiesOnDisk = folder.listFiles().orEmpty().filter { it.isFile }
        assertEquals(1, copiesOnDisk.size)
        assertTrue(copiesOnDisk.single().readBytes().contentEquals(bytes))
        assertFalse(runBlocking { fs.exists(oldLocator) })
    }
}
