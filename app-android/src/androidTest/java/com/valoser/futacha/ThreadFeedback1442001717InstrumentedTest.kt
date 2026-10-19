@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.network.*
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BaseInMemoryPlatformStateStorage
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.privacy.*
import com.valoser.futacha.shared.util.AndroidFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Regression checks for the thread feedback fixes on production Compose screens. */
class ThreadFeedback1442001717InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val clients = mutableListOf<HttpClient>()
    private val db = "feedback1442001717_diagnostic.db"
    private val board = CompatBoard("audit-board", "検証板", "https://may.2chan.net/b/", "https://may.2chan.net/b/", 0)
    private val tab = CompatTab("audit-tab", "https://may.2chan.net/b/res/123.htm", "https://may.2chan.net/b/res/123.htm", board.key, board.name, "123", "検証タイトル", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val out get() = File(rule.activity.getExternalFilesDir(null), "feedback1442001717").apply { mkdirs() }

    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { store.initialize(); store.upsertBoard(board); store.openTab(tab) }
        loader = ImageLoader.Builder(rule.activity).build()
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        clients.forEach { it.close() }; loader.shutdown()
        runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun layout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test fun deleteKeyIsMaskedWhileItsOriginalValueIsKeptForTheDraft() {
        val secret = "TEST1234"
        runBlocking { store.saveDraft(CompatReplyDraft(tabKey = tab.key, comment = "本文", deleteKey = secret, updatedAtEpochMillis = 1)) }
        rule.setContent { CompatibilityProfileTheme("mono") { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatPostScreen(tab = tab, board = board, repository = null, store = store, preferences = emptyMap(),
                appVersion = "13.0", fileSystem = null, onToolbarEdit = {}, onBack = {})
        } } }
        val field = rule.onNodeWithTag("compat-post-delete-key")
        rule.waitUntil(5_000) { field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text.length == secret.length }
        val drawn = layout("compat-post-delete-key").layoutInput.text.text
        assertFalse(drawn.contains(secret)); assertEquals(secret.length, drawn.length)
        assertEquals(secret, runBlocking { store.loadDraft(tab.key) }?.deleteKey)
        capture("delete-key-masked")
    }

    @Test fun compatibilityCatalogSearchSurvivesAThreadRoundTrip() {
        val item = com.valoser.futacha.shared.model.CatalogItem(id = "9001", threadUrl = "${board.originalUrl}res/9001.htm", title = "SEARCH-KEEP", thumbnailUrl = null, fullImageUrl = null, replyCount = 1)
        runBlocking {
            store.closeTabs(setOf(tab.key), nowEpochMillis = 10)
            store.saveCatalogSnapshot(CompatCatalogSnapshot(board.key, CompatCatalogSort.CATALOG, 1, 1, listOf(item)))
        }
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by com.valoser.futacha.shared.repo.mock.FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
                settings: com.valoser.futacha.shared.model.CatalogFetchSettings): List<com.valoser.futacha.shared.model.CatalogItem> = listOf(item)
        }
        rule.setContent { CompositionLocalProvider(LocalFutachaImageLoader provides loader) { MaterialTheme {
            CompatibilityApp(store = store, repository = repository, onExitApplication = {})
        } } }
        rule.onNodeWithText(board.originalUrl).performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("スレッド検索").performClick()
        rule.onNodeWithText("検索文字").performTextInput("SEARCH")
        rule.onNodeWithTag("compat-catalog-item-9001").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-thread-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("SEARCH", substring = false).assertIsDisplayed()
        rule.onNodeWithText("1件").assertIsDisplayed()
        capture("compat-catalog-search-retained")
    }

    @Test fun composingTextIsReadableAcrossSixPalettes() {
        val theme = mutableStateOf("mono")
        rule.setContent { CompatibilityProfileTheme(theme.value) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                CompatPostScreen(tab = tab, board = board, repository = null, store = store,
                    preferences = emptyMap(), appVersion = "13.0", fileSystem = null, onToolbarEdit = {}, onBack = {})
            }
        } }
        val field = rule.onNodeWithTag("compat-post-comment-field")
        field.performClick(); rule.waitForIdle()
        rule.runOnUiThread {
            val view = requireNotNull(rule.activity.currentFocus)
            val connection = requireNotNull(view.onCreateInputConnection(EditorInfo())) { "Focused view has no IME connection: ${view.javaClass}" }
            assertTrue(connection.setComposingText("にほんごのへんかん", 1))
        }
        rule.waitUntil(5_000) { field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "にほんごのへんかん" }
        val rows = mutableListOf<String>()
        for (name in listOf("mono", "default", "futaba", "blue", "pink", "black")) {
            rule.runOnIdle { theme.value = name }; rule.waitForIdle()
            val result = layout("compat-post-comment-field")
            val decorated = result.layoutInput.text.spanStyles.firstOrNull { it.item.background != Color.Unspecified }
            assertNotNull("Actual composition decoration missing in $name", decorated)
            val fg = decorated!!.item.color.takeIf { it != Color.Unspecified } ?: result.layoutInput.style.color
            val bg = decorated!!.item.background
            val ratio = (maxOf(fg.luminance(), bg.luminance()) + .05f) / (minOf(fg.luminance(), bg.luminance()) + .05f)
            val normal = compatibilityPaletteFor(name).background
            rows += "$name foreground=${fg.toArgb().toUInt().toString(16)} background=${bg.toArgb().toUInt().toString(16)} contrast=$ratio normalBackground=${normal.toArgb().toUInt().toString(16)} range=${decorated.start}..${decorated.end}"
            assertTrue("Composition contrast must be at least 4.5 in $name", ratio >= 4.5f)
            assertNotEquals("Composition background must differ from ordinary background", normal, bg)
            capture("composition-$name")
        }
        File(out, "composition-observations.txt").writeText(rows.joinToString("\n"))
    }

    @Test fun imageOnlyPrivacyLeavesTextReadableAndCompatTitleCanShrink() {
        val appState = AppStateStore(BaseInMemoryPlatformStateStorage())
        try {
            rule.setContent { CompatibilityProfileTheme("mono") { CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                PrivacyModeHost(store, appState, compatibilityMode = true) {
                    var settings by remember { mutableStateOf(false) }
                    if (settings) {
                        PrivacySettingsExemption()
                        Column { Button(onClick = { settings = false }) { Text("検証画面へ戻る") }
                            CompatSettingsScreen("privacy", store, emptyMap(), null, onNavigate = {}, onBack = { settings = false }) }
                    } else {
                        Column {
                            CompatTopBar("検証タイトル", "検証用", {}, titleAppearance = LocalPrivacyMode.current?.options?.compatTitle ?: PrivateTitle.NORMAL)
                            Box(Modifier.fillMaxWidth().height(160.dp).background(Color.White).testTag("audit-body")) { Text("検証本文", color = Color.Black); Box(Modifier.size(60.dp).background(Color.White).privacyMediaFilter().testTag("audit-image")) }
                            Button(onClick = { settings = true }) { Text("設定を開く") }
                        }
                    }
                }
            } } }
            fun titleSize(): Float {
                val layouts = mutableListOf<TextLayoutResult>()
                rule.onNodeWithText("検証タイトル").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single().layoutInput.style.fontSize.value
            }
            assertEquals(20f, titleSize(), .01f); capture("privacy-before")
            rule.onNodeWithText("設定を開く").performClick()
            rule.onNodeWithText("画像のみ").assertExists()
            rule.onNodeWithTag("privacy-mode-toggle").performClick()
            rule.onNodeWithText("小さくする").performScrollTo().performClick()
            rule.waitUntil(5_000) { runBlocking { store.preferences.first()[PRIVACY_COMPAT_TITLE_KEY] == "SMALL" } }
            capture("privacy-settings-compat-title")
            rule.onNodeWithText("検証画面へ戻る").performClick()
            assertEquals(14f, titleSize(), .01f)
            val imageRoot = rule.onRoot().captureToImage().toPixelMap()
            val bodyBounds = rule.onNodeWithTag("audit-body").fetchSemanticsNode().boundsInRoot
            assertEquals(1f, imageRoot[(bodyBounds.right-20).toInt(), (bodyBounds.bottom-20).toInt()].red, .01f)
            val imageBounds = rule.onNodeWithTag("audit-image").fetchSemanticsNode().boundsInRoot
            assertTrue(imageRoot[imageBounds.center.x.toInt(), imageBounds.center.y.toInt()].red < .6f)
            capture("privacy-images-only-title-small")
            rule.onNodeWithText("設定を開く").performClick()
            rule.onNodeWithTag("privacy-scope-SCREEN").performScrollTo().performClick()
            rule.onNodeWithText("検証画面へ戻る").performClick()
            val root = rule.onRoot().captureToImage().toPixelMap()
            val bounds = rule.onNodeWithTag("audit-body").fetchSemanticsNode().boundsInRoot
            val pixel = root[bounds.center.x.toInt(), (bounds.bottom - 20).toInt()]
            assertTrue("Body background was not dimmed", pixel.red < .6f)
            capture("privacy-screen-title-small")
            File(out, "privacy-observations.txt").writeText("titleBefore=20sp titleAfterSmall=14sp bodyWhiteAfter=${pixel.red} imageOnlyChoice=true\n")
        } finally { appState.close() }
    }

    @Test fun postingPreparationRetriesOnlyReadsAndKeepsDraftOnFailure() {
        var gets = 0; var posts = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.parameters["mode"] == "catset") return@MockEngine respond("ok", HttpStatusCode.OK)
            if (request.method == HttpMethod.Post) { posts++; error("Unexpected real posting attempt") }
            gets++; throw java.io.IOException("unexpected end of stream")
        }) { installPostRedirectFollowingAndReadRetry { delayMillis { 1 } } }.also(clients::add)
        val repo = createRemoteBoardRepository(client)
        val preferences = mapOf("compat.control.controlPostConfirm" to "OFF")
        runBlocking { store.saveDraft(CompatReplyDraft(tabKey = tab.key, comment = "未送信の検証本文", deleteKey = "test-only", updatedAtEpochMillis = 1)) }
        rule.setContent { CompatibilityProfileTheme("mono") {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                CompatPostScreen(tab = tab, board = board, repository = repo, store = store,
                    preferences = preferences, appVersion = "13.0", fileSystem = null, onToolbarEdit = {}, onBack = {})
            }
        } }
        val field = rule.onNodeWithTag("compat-post-comment-field")
        rule.waitUntil(5_000) { field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text == "未送信の検証本文" }
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("投稿の準備ができないため", substring = true).fetchSemanticsNodes().isNotEmpty() }
        capture("posting-preparation-failure")
        assertEquals(0, posts)
        // The capability fetch also GETs before send. The error shown below is the real submit preparation.
        val firstGets = gets
        rule.onNodeWithText("OK").performClick()
        assertEquals("未送信の検証本文", field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("投稿の準備ができないため", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, gets - firstGets); assertEquals(0, posts)
        File(out, "posting-observations.txt").writeText("firstTotalGets=$firstGets manualSecondSendAddedGets=${gets-firstGets} posts=$posts draftKept=true\n")
    }

    @Test fun viewerSaveCompletesWhenItsScreenCloses() = observeViewerSave(closeScreen = true)
    @Test fun viewerSaveCompletesAfterHomeWithoutClosingItsScreen() = observeViewerSave(closeScreen = false)

    private fun observeViewerSave(closeScreen: Boolean) {
        val mode = if (closeScreen) "screen-close" else "home"
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
        val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8q8AAAAASUVORK5CYII=", android.util.Base64.DEFAULT)
        val client = HttpClient(MockEngine {
            started.complete(Unit)
            try { release.await() } catch (e: CancellationException) { cancelled.complete(Unit); throw e }
            respond(png, headers = headersOf(HttpHeaders.ContentType, "image/png"))
        }).also(clients::add)
        val folder = File(rule.activity.cacheDir, "feedback1442001717-$mode").apply { deleteRecursively(); mkdirs() }
        val prefs = mapOf("compat.storage.dummyDownloadDir" to folder.absolutePath, "compat.viewer.viewerPreloadMode" to "利用しない")
        val visible = mutableStateOf(true)
        val snapshot = CompatThreadSnapshot(tabKey = tab.key, revision = 1, fetchedAtEpochMillis = 1,
            posts = listOf(CompatPostSnapshot(position = 0, postNo = "123", timestamp = "now", messageHtml = "検証画像", imageUrl = "https://feedback.invalid/audit.png", thumbnailUrl = "https://feedback.invalid/audit.png")))
        rule.setContent { CompatibilityProfileTheme("mono") { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            val saveScope = rememberCoroutineScope()
            Column {
                Button(onClick = { visible.value = false }) { Text("検証用にビューアを閉じる") }
                if (visible.value) CompatViewerScreen(tab, 0, preparedSnapshot = snapshot, store = store, preferences = prefs,
                    ngRules = emptyList(), httpClient = client, fileSystem = AndroidFileSystem(rule.activity), saveCoroutineScope = saveScope,
                    onToolbarEdit = {}, onShowSourcePost = {}, onOpenGallery = { _, _ -> }, onOpenSettings = {}, onOpenCommonSettings = {}, onBack = { visible.value = false })
            }
        } } }
        rule.onNodeWithContentDescription("保存する").performClick()
        rule.waitUntil(10_000) { started.isCompleted }
        capture("save-$mode-started")
        if (closeScreen) {
            rule.onNodeWithText("検証用にビューアを閉じる").performClick()
            assertFalse(cancelled.isCompleted)
            release.complete(Unit)
            rule.waitUntil(10_000) { folder.walkTopDown().any { it.isFile && it.extension == "png" } }
            val saved = folder.walkTopDown().first { it.isFile && it.extension == "png" }
            assertArrayEquals(png, saved.readBytes())
            File(out, "save-$mode-observations.txt").writeText("cancelledOnScreenClose=false completedPng=true\n")
        } else {
            val pfd = instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME")
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }; pfd.close()
            android.os.SystemClock.sleep(500)
            val homeBitmap = instrumentation.uiAutomation.takeScreenshot()
            File(out, "save-home-background.png").outputStream().use { homeBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; homeBitmap.recycle()
            release.complete(Unit)
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (android.os.SystemClock.elapsedRealtime() < deadline && folder.walkTopDown().none { it.isFile && it.extension == "png" }) android.os.SystemClock.sleep(50)
            val saved = folder.walkTopDown().firstOrNull { it.isFile && it.extension == "png" }
            assertNotNull("Save did not finish while the app was behind Home", saved)
            assertArrayEquals(png, saved!!.readBytes())
            assertFalse(cancelled.isCompleted)
            File(out, "save-$mode-observations.txt").writeText("cancelledAfterHome=false completedPng=true bytes=${saved.length()}\n")
            rule.runOnUiThread { rule.activity.startActivity(android.content.Intent(rule.activity, ComponentActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        }
        folder.deleteRecursively()
    }
}
