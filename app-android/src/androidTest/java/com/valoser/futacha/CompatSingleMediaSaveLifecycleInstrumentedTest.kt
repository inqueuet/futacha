@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.network.NetworkFetcher
import coil3.network.ktor3.asNetworkClient
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File

/** Uses the real workspace navigation, keeping the source request pending across Back. */
class CompatSingleMediaSaveLifecycleInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m28_save.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var client: HttpClient
    private lateinit var previewClient: HttpClient
    private lateinit var loader: ImageLoader
    private lateinit var folder: File
    private lateinit var bytes: ByteArray
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val cancelled = CompletableDeferred<Unit>()
    private var fail = false
    private val unlocked = mutableStateOf(true)
    private val appLock = FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }
    private val boardUrl = "https://img.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/123.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)

    @Before fun prepare() {
        folder = File(rule.activity.cacheDir, "audit-m28-save").apply { deleteRecursively(); mkdirs() }
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        bytes = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.BLUE); compress(Bitmap.CompressFormat.JPEG, 85, out); recycle()
            }
        }.toByteArray()
        previewClient = HttpClient(MockEngine { respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg")) })
        loader = ImageLoader.Builder(rule.activity).diskCache(null).components {
            add(NetworkFetcher.Factory(networkClient = { previewClient.asNetworkClient() }))
        }.build()
        client = HttpClient(MockEngine { request ->
            started.complete(Unit)
            try { release.await() } catch (error: CancellationException) { cancelled.complete(Unit); throw error }
            if (fail) respond("local failure", HttpStatusCode.InternalServerError)
            else if (request.url.encodedPath.endsWith(".htm")) respond("<html><body><blockquote>local post</blockquote></body></html>",
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=UTF-8"))
            else respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/jpeg"))
        })
        runBlocking {
            store.initialize()
            store.upsertBoard(CompatBoard(boardKey, "board", boardUrl, boardUrl, 0))
            store.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "board", "123", "Save audit",
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
            store.savePreference("compat.storage.dummyDownloadDir", folder.absolutePath)
            store.savePreference("compat.catalog.catalogThreadOpenWithReload", "OFF")
            store.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 1, 1, posts = listOf(
                CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "local post",
                    imageUrl = "${boardUrl}src/1.jpg", thumbnailUrl = "${boardUrl}thumb/1s.jpg"))))
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        rule.waitForIdle()
        release.complete(Unit)
        client.close(); loader.shutdown(); previewClient.close()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(db); folder.deleteRecursively()
    }
    @Test fun gallerySaveCompletesAfterReturningToTheThread() = observe(gallery = true, failure = false)
    @Test fun galleryFailureAfterReturningToTheThreadIsReported() = observe(gallery = true, failure = true)
    @Test fun threadImageSaveCompletesAfterReturningToTheCatalog() = observe(gallery = false, failure = false)
    @Test fun threadImageFailureAfterReturningToTheCatalogIsReported() = observe(gallery = false, failure = true)
    @Test fun gallerySaveWhileOpenKeepsItsLocalResultAndShareAction() = observe(gallery = true, failure = false, closeScreen = false)
    @Test fun threadImageSaveWhileOpenKeepsItsLocalResult() = observe(gallery = false, failure = false, closeScreen = false)
    @Test fun viewerResultAfterBackCanBeReadAndDismissed() = observe(false, false, viewer = true, holdNotice = true)
    @Test fun htmlResultCanBeReadAndDismissed() = observe(false, false, closeScreen = false, pageFormat = "HTML", holdNotice = true)
    @Test fun zipResultCanBeReadAndDismissed() = observe(false, false, closeScreen = false, pageFormat = "メディアのみ(ZIP)", holdNotice = true)
    @Test fun folderResultCanBeReadAndDismissed() = observe(false, false, closeScreen = false, pageFormat = "メディアのみ(フォルダ)", holdNotice = true)
    @Test fun savedResultSurvivesBackgroundAndTheAppLock() = observe(false, false, viewer = true, holdNotice = true, lockAndBackground = true)

    /** L74: a media-only folder save of a thread without media says so, not 「成功0件 / 失敗0件」. */
    @Test fun aFolderSaveOfAThreadWithoutMediaSaysThereIsNone() {
        runBlocking {
            store.saveThreadSnapshot(CompatThreadSnapshot(tabKey, 2, 2, posts = listOf(
                CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "text only post"))))
        }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader,
            LocalFutachaAppLockHolder provides appLock, LocalFutachaAppUnlocked provides unlocked.value) {
            CompatibilityApp(store, repository = null, httpClient = client, fileSystem = createFileSystem(rule.activity),
                imageLoader = loader, catalogImageLoader = loader, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("text only post", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-other").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onNodeWithText("ページを保存", substring = true).performClick()
        rule.onNodeWithText("メディアのみ(フォルダ)").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存するメディアがありません").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("成功0件", substring = true).fetchSemanticsNodes().isEmpty())
    }

    /** L48: a thread save goes on when its screen goes, and the thread shows its progress and 中止 again on return. */
    @Test fun aThreadSaveShowsItsProgressAgainWhenTheThreadComesBack() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader,
            LocalFutachaAppLockHolder provides appLock, LocalFutachaAppUnlocked provides unlocked.value) {
            CompatibilityApp(store, repository = null, httpClient = client, fileSystem = createFileSystem(rule.activity),
                imageLoader = loader, catalogImageLoader = loader, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("No.1の画像").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-other").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        rule.onNodeWithText("ページを保存", substring = true).performClick()
        rule.onNodeWithText("HTML").performClick()
        rule.waitUntil(10_000) { started.isCompleted }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-thread-save-progress-dialog").fetchSemanticsNodes().isNotEmpty() }

        // The thread's screen goes (as when another thread is opened from outside) while the save runs.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("No.1の画像").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("compat-thread-save-progress-dialog").assertDoesNotExist()

        // Back to the thread from the drawer's open threads.
        rule.onRoot().performTouchInput { swipe(Offset(2f, height * 0.5f), Offset(width * 0.6f, height * 0.5f), 300) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Save audit").fetchSemanticsNodes().any { it.positionInRoot.x >= 0f } }
        rule.onAllNodesWithText("Save audit").filter(hasClickAction()).onFirst().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("No.1の画像").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-thread-save-progress-dialog").fetchSemanticsNodes().isNotEmpty() }
        assertFalse("Leaving the thread did not cancel its save", cancelled.isCompleted)

        release.complete(Unit)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("保存しました", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-thread-save-progress-dialog").fetchSemanticsNodes().isEmpty() }
    }

    private fun observe(gallery: Boolean, failure: Boolean, closeScreen: Boolean = true,
        viewer: Boolean = false, pageFormat: String? = null, holdNotice: Boolean = false, lockAndBackground: Boolean = false) {
        fail = failure
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader,
            LocalFutachaAppLockHolder provides appLock, LocalFutachaAppUnlocked provides unlocked.value) {
            CompatibilityApp(store, repository = null, httpClient = client, fileSystem = createFileSystem(rule.activity),
                imageLoader = loader, catalogImageLoader = loader, initialThreadDeepLink = threadUrl, onExitApplication = {})
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithContentDescription("No.1の画像").fetchSemanticsNodes().isNotEmpty() }
        if (pageFormat != null) {
            rule.onNodeWithTag("compat-toolbar-command-other").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            rule.onNodeWithText("ページを保存", substring = true).performClick()
            rule.onNodeWithText(pageFormat).performClick()
        } else if (viewer) {
            rule.onNodeWithContentDescription("No.1の画像").performClick()
            rule.onNodeWithContentDescription("保存する").performClick()
        } else if (gallery) {
            rule.onNodeWithContentDescription("画像一覧").performClick()
            rule.onNodeWithTag("compat-gallery-item-1").performTouchInput { longClick() }
        } else rule.onNodeWithContentDescription("No.1の画像").performTouchInput { longClick() }
        if (pageFormat == null && !viewer) rule.onNodeWithText("画像を保存する").performClick()
        rule.waitUntil(10_000) { started.isCompleted }
        if (closeScreen) {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
            if (viewer) rule.onNodeWithTag("compat-viewer-image-page").assertDoesNotExist()
            else if (gallery) rule.onNodeWithTag("compat-gallery-item-1").assertDoesNotExist()
            else rule.onNodeWithContentDescription("No.1の画像").assertDoesNotExist()
        }
        release.complete(Unit)
        val text = if (failure) "保存できませんでした" else "保存しました"
        rule.waitUntil(10_000) {
            cancelled.isCompleted || rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse("Leaving the source screen must not cancel its save", cancelled.isCompleted)
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
        if (!closeScreen && gallery) rule.onNodeWithText("共有").assertIsDisplayed()
        val files = folder.listFiles().orEmpty().filter { it.isFile }
        if (failure) assertTrue(files.isEmpty())
        else if (pageFormat == null) assertArrayEquals(bytes, files.single().readBytes())
        else assertTrue(folder.walkTopDown().any { it.isFile })
        if (holdNotice) {
            rule.mainClock.advanceTimeBy(4_000)
            rule.onNodeWithText(text, substring = true).assertIsDisplayed()
            if (lockAndBackground) {
                rule.runOnUiThread { appLock.lockSession(); appLock.setContentVisible(false); unlocked.value = false }
                rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                try { rule.mainClock.advanceTimeBy(20_000) }
                finally {
                    rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                    rule.runOnUiThread { appLock.openSessionWithoutLock(); appLock.setContentVisible(true); unlocked.value = true }
                }
                rule.onNodeWithText(text, substring = true).assertIsDisplayed()
            }
            rule.onNodeWithTag("compat-app-notice").performClick()
            rule.onNodeWithText(text, substring = true).assertDoesNotExist()
        }
    }
}
