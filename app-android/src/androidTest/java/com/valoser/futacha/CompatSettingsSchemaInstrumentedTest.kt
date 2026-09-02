package com.valoser.futacha

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Base64
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.CompatWorkspaceRecord
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.compat.normalizeCompatSearchText
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.compat.CompatThreadSaveProgressDialog
import com.valoser.futacha.shared.ui.compat.CompatPostAttachmentPreview
import com.valoser.futacha.shared.ui.compat.CompatPostDrawingScreen
import com.valoser.futacha.shared.ui.compat.CompatPostImageCompressConfirmation
import com.valoser.futacha.shared.ui.compat.CompatAscii2dRegistrationDialog
import com.valoser.futacha.shared.ui.compat.CompatUpsUploadDialog
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import com.valoser.futacha.shared.ui.compat.compatReverseSearchLongPressedLink
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.AndroidFileSystem
import com.valoser.futacha.shared.repo.BoardRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CompatSettingsSchemaInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_settings_schema_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var imageLoader: ImageLoader

    @Before
    fun prepare() {
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "1.0")
        }
        imageLoader = ImageLoader.Builder(context).build()
    }

    @Test
    fun issue78PersistedArchiveLabelsAreAbsentFromBodyAndQuoteOnDevice() {
        val boardUrl = "https://img.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/1463510009.htm"
        val tabKey = compatTabKey(threadUrl)
        val sourceUrl = "https://dec.2chan.net/up2/src/fu7190971.png"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "二次元裏", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "二次元裏",
                    threadNo = "1463510009",
                    title = "生成残量回復...15%！",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "1463510009",
                            timestamp = "26/08/30(日)12:09:25",
                            messageHtml =
                                "<a href=\"$sourceUrl\">fu7190971.png</a>" +
                                    "<span onclick=\"previewImg('body','$sourceUrl')\">[見る]</span><br>りんみ"
                        ),
                        CompatPostSnapshot(
                            position = 1,
                            postNo = "1463510029",
                            timestamp = "26/08/30(日)12:09:30",
                            messageHtml =
                                "&gt;<a href=\"$sourceUrl\">fu7190971.png</a>" +
                                    "<span onclick=\"previewImg('quote','$sourceUrl')\">[見る]</span><br>失恋はほむらもだろ…"
                        )
                    )
                )
            )
        }

        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-1463510029", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithText("りんみ", substring = true).assertIsDisplayed()
        rule.onNodeWithText("失恋はほむらもだろ…", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("[見る]", substring = true).assertCountEquals(0)

        val screenshot = rule.onRoot().captureToImage().asAndroidBitmap()
        context.openFileOutput("issue78-android-device.png", Context.MODE_PRIVATE).use { output ->
            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun futabaThemeUsesReferenceBrownStatusBarWithWhiteIcons() {
        runBlocking {
            store.savePreference("compat.design.designTheme", "ふたば")
        }
        val controller = WindowCompat.getInsetsController(
            rule.activity.window,
            rule.activity.window.decorView
        )
        controller.isAppearanceLightStatusBars = true
        rule.setContent {
            MaterialTheme {
                CompatibilityApp(store = store, repository = null, onExitApplication = {})
            }
        }

        // API 35+ enforces edge-to-edge and can report a transparent Window
        // color even though Compose paints the reference brown behind it. The
        // exact #542D24 token is common-tested; this platform test fixes the
        // user-visible white glyph mode after starting from dark glyphs.
        rule.waitUntil(5_000) { !controller.isAppearanceLightStatusBars }
        assertFalse(controller.isAppearanceLightStatusBars)
    }

    @Test
    fun allReferenceToolbarEditorsUseTwoBottomPreviewsAndPersistImmediately() {
        val initial = CompatToolbarSurface.entries.associateWith { surface ->
            compatToolbarMaster(surface).mapIndexed { index, item ->
                CompatToolbarItem(
                    key = item.key,
                    position = index,
                    active = if (surface == CompatToolbarSurface.POST) true else item.defaultActive
                )
            }
        }
        runBlocking {
            initial.forEach { (surface, items) -> store.saveToolbar(surface, items) }
        }
        val surfaceState = mutableStateOf(CompatToolbarSurface.CATALOG)
        val showEditorState = mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                if (showEditorState.value) {
                    CompatToolbarEditorScreen(
                        surface = surfaceState.value,
                        store = store,
                        onBack = { showEditorState.value = false }
                    )
                } else {
                    androidx.compose.material3.Text("editor closed")
                }
            }
        }

        CompatToolbarSurface.entries.forEach { surface ->
            rule.runOnIdle { surfaceState.value = surface }
            val items = initial.getValue(surface)
            val first = items.first()
            rule.waitUntil(5_000) {
                rule.onAllNodesWithTag(
                    "compat-toolbar-preview-${if (first.active) "active" else "inactive"}-${first.key}",
                    useUnmergedTree = true
                ).fetchSemanticsNodes().isNotEmpty()
            }

            val inactivePreview = rule.onNodeWithTag("compat-toolbar-preview-inactive")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val activePreview = rule.onNodeWithTag("compat-toolbar-preview-active")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val expectedHeight = with(rule.density) { 40.dp.toPx() }
            assertTrue(abs(inactivePreview.height - expectedHeight) <= 1f)
            assertTrue(abs(activePreview.height - expectedHeight) <= 1f)
            assertTrue(inactivePreview.bottom <= activePreview.top + 1f)
            val editorList = rule.onNodeWithTag("compat-toolbar-editor-list")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(editorList.bottom <= inactivePreview.top + 1f)
            rule.onAllNodesWithContentDescription("初期設定に戻す").assertCountEquals(0)

            val firstRow = rule.onNodeWithTag("compat-toolbar-editor-row-${first.key}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val firstCheckbox = rule.onNodeWithTag("compat-toolbar-checkbox-${first.key}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val firstIcon = rule.onNodeWithTag("compat-toolbar-editor-icon-${first.key}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val firstHandle = rule.onNodeWithTag("compat-toolbar-handle-${first.key}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertTrue(abs(firstRow.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
            assertTrue(firstCheckbox.left - firstRow.left >= with(rule.density) { 20.dp.toPx() } - 1f)
            assertTrue(abs(firstIcon.width - with(rule.density) { 30.dp.toPx() }) <= 1f)
            assertTrue(abs(firstHandle.width - with(rule.density) { 50.dp.toPx() }) <= 1f)

            val expectedMore = surface != CompatToolbarSurface.POST || items.any { !it.active }
            rule.onAllNodesWithTag("compat-toolbar-preview-active-more", useUnmergedTree = true)
                .assertCountEquals(if (expectedMore) 1 else 0)
            items.forEach { item ->
                rule.onNodeWithTag(
                    "compat-toolbar-preview-${if (item.active) "active" else "inactive"}-${item.key}",
                    useUnmergedTree = true
                ).assertExists()
            }

            rule.onNodeWithTag("compat-toolbar-toggle-${first.key}", useUnmergedTree = true).performClick()
            if (surface == CompatToolbarSurface.POST) {
                rule.onNodeWithContentDescription("戻る").performClick()
                rule.waitUntil(5_000) {
                    rule.onAllNodesWithText("editor closed").fetchSemanticsNodes().isNotEmpty()
                }
                rule.onNodeWithText("editor closed").assertIsDisplayed()
            }
            rule.waitUntil(5_000) {
                runBlocking {
                    store.loadToolbar(surface).first { it.key == first.key }.active == !first.active
                }
            }
            if (surface != CompatToolbarSurface.POST) {
                rule.onNodeWithTag(
                    "compat-toolbar-preview-${if (first.active) "inactive" else "active"}-${first.key}",
                    useUnmergedTree = true
                ).assertExists()
            }
        }
    }

    @Test
    fun toolbarEditorDragMovesExactlyOneRowWithoutOscillating() {
        val surface = CompatToolbarSurface.THREAD
        val initial = compatToolbarMaster(surface).mapIndexed { index, item ->
            CompatToolbarItem(item.key, index, item.defaultActive)
        }
        runBlocking { store.saveToolbar(surface, initial) }
        rule.setContent {
            MaterialTheme {
                CompatToolbarEditorScreen(surface = surface, store = store, onBack = {})
            }
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag(
                "compat-toolbar-handle-${initial.first().key}",
                useUnmergedTree = true
            ).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val rowHeight = with(rule.density) { 60.dp.toPx() }
        rule.onNodeWithTag(
            "compat-toolbar-handle-${initial.first().key}",
            useUnmergedTree = true
        ).performTouchInput {
            down(center)
            moveBy(Offset(0f, rowHeight * 1.2f), delayMillis = 600L)
            up()
        }
        rule.waitUntil(5_000) {
            runBlocking {
                store.loadToolbar(surface).take(2).map(CompatToolbarItem::key) ==
                    listOf(initial[1].key, initial[0].key)
            }
        }
        assertEquals(
            listOf(initial[1].key, initial[0].key),
            runBlocking { store.loadToolbar(surface).take(2).map(CompatToolbarItem::key) }
        )
    }

    @Test
    fun informationScreensUseFutachaHistoryAndCurrentLicenses() {
        runBlocking { store.savePreference("compat.commonUsedVersion", "8.8") }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        appVersion = "8.9",
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText("更新履歴").assertIsDisplayed()
        rule.onNodeWithTag("compat-change-log-content").assertIsDisplayed()
        val firstChangeBounds = rule.onNodeWithTag("compat-change-log-body-10.1-0")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val minimumReadableLineHeight = with(rule.density) { 24.dp.toPx() }
        assertTrue(
            "change-log body must use readable density-aware Compose text",
            firstChangeBounds.height >= minimumReadableLineHeight
        )
        rule.onNodeWithContentDescription("ストア").assertIsDisplayed()
        rule.onNodeWithContentDescription("ヘルプ").performClick()
        rule.onNodeWithText("ヘルプ").assertIsDisplayed()
        rule.onNodeWithTag("compat-help-content").assertIsDisplayed()
        rule.onNodeWithContentDescription("変更履歴").assertIsDisplayed()
        rule.onNodeWithContentDescription("ストア").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("compat-change-log-content").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.commonUsedVersion") } == "8.9"
        }

        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithContentDescription("更新情報").performClick()
        rule.onNodeWithTag("compat-change-log-content").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithTag("compat-settings-list-root")
            .performScrollToNode(hasTextExactly("ライセンス"))
        rule.onNodeWithText("ライセンス").performClick()
        rule.onNodeWithTag("compat-license-list").assertIsDisplayed()
        rule.onNodeWithTag("compat-license-futacha-open-source-notices").assertIsDisplayed()
        rule.onNodeWithTag("compat-license-list").performScrollToIndex(2)
        rule.onNodeWithTag("compat-license-apache-license-2.0").assertIsDisplayed()
    }

    @After
    fun cleanUp() {
        if (::imageLoader.isInitialized) imageLoader.shutdown()
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    /**
     * Compose exposes both the unmerged BasicText node and its merged clickable
     * parent for a post body.  The production UI therefore legitimately has
     * more than one semantics node for the same visible string.  Assertions
     * which require a single node are brittle across Compose versions; for
     * content-presence checks we only need at least one matching node.
     */
    private fun assertTextPresent(text: String, substring: Boolean = false) {
        waitForTextPresent(text, substring)
        assertTrue(
            "Expected text '$text' to be present",
            rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    private fun assertBottomBarClearsSystemNavigation(tag: String) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        fun visibleBar() = rule.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { node ->
                node.layoutInfo.isPlaced && node.boundsInRoot.right > 0f && node.boundsInRoot.left < root.right
            }
            .maxByOrNull { it.boundsInRoot.right - it.boundsInRoot.left }
            ?.boundsInRoot
        val navigationInset = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?: 0
        val actionHeight = with(rule.density) { 40.dp.toPx() }
        val safeBottom = root.bottom - navigationInset
        // IME dismissal animates independently of the Compose idling clock.
        // Check the settled navigation layout, not an intermediate IME frame.
        rule.waitUntil(5_000) { visibleBar()?.bottom?.let { it >= safeBottom - 1f } == true }
        val bar = visibleBar() ?: error("No visible $tag")
        assertTrue("$tag must stay inside the root", bar.bottom <= root.bottom + 1f)
        assertTrue(
            "$tag ($bar) must terminate at the navigation-safe edge ($safeBottom) or paint through the inset",
            bar.bottom >= safeBottom - 1f
        )
        assertTrue(
            "$tag must preserve its 40dp action row with a $navigationInset px system navigation inset",
            bar.height >= actionHeight - 1f
        )
    }

    private fun waitForTextPresent(text: String, substring: Boolean = false) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForTagDisplayed(tag: String) {
        rule.waitUntil(5_000) {
            runCatching {
                rule.onAllNodesWithTag(tag)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .any { it.layoutInfo.isPlaced && it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            }.getOrDefault(false)
        }
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun waitForTagPresent(tag: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun waitForContentDescriptionPresent(description: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun compatibilityStartupWaitsForPreferencesBeforePaintingSelectedPalette() {
        val delayedBoards = MutableSharedFlow<List<CompatBoard>>(replay = 1)
        val delayedPreferences = MutableSharedFlow<Map<String, String>>(replay = 1)
        val delayedStore = object : CompatibilityStore by store {
            override val boards = delayedBoards
            override val preferences = delayedPreferences
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                // Simulate a host/OS night palette. The reference application's
                // six manually selected themes must remain independent of it.
                MaterialTheme(colorScheme = darkColorScheme()) {
                    CompatibilityApp(store = delayedStore, repository = null, onExitApplication = {})
                }
            }
        }

        // Before the persisted palette is known, no default teal/gray frame
        // may be painted over the Android preview window (#53).
        rule.onNodeWithTag("compat-startup-loading").assertDoesNotExist()

        runBlocking {
            delayedPreferences.emit(
                mapOf(
                    "compat.design.designTheme" to "ふたば",
                    "compat.commonUsedVersion" to "1.0"
                )
            )
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-startup-loading")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        val futabaPixels = rule.onNodeWithTag("compat-startup-loading")
            .captureToImage()
            .toPixelMap()
        val futabaCorner = futabaPixels[2, 2]
        assertTrue(
            "The manual Futaba theme must stay light under a dark host theme",
            futabaCorner.red > 0.95f && futabaCorner.green > 0.95f && futabaCorner.blue > 0.85f
        )

        runBlocking {
            delayedPreferences.emit(
                mapOf(
                    "compat.design.designTheme" to "ブラック",
                    "compat.commonUsedVersion" to "1.0"
                )
            )
        }
        rule.waitUntil(5_000) {
            val pixels = rule.onNodeWithTag("compat-startup-loading").captureToImage().toPixelMap()
            val corner = pixels[2, 2]
            corner.red < 0.05f && corner.green < 0.05f && corner.blue < 0.05f
        }

        runBlocking { delayedBoards.emit(emptyList()) }
        rule.onNodeWithText("ふたば").assertIsDisplayed()
    }

    @Test
    fun compatibilityMainListCollapsesDuplicateBoardKeysBeforeLayout() {
        val boardUrl = "https://img.2chan.net/t/"
        val board = CompatBoard(
            key = compatBoardKey(boardUrl),
            name = "primary-board",
            canonicalUrl = boardUrl,
            originalUrl = boardUrl,
            sortOrder = 0
        )
        val duplicateBoards = MutableStateFlow(
            listOf(board, board.copy(name = "duplicate-board", sortOrder = 1))
        )
        val duplicateStore = object : CompatibilityStore by store {
            override val boards = duplicateBoards
        }

        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = duplicateStore, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("primary-board").assertIsDisplayed()
        rule.onAllNodesWithText("duplicate-board").assertCountEquals(0)
    }

    @Test
    fun compatibilityUpdateCheckSettingPersistsSharedStartupGate() {
        val sharedStore = (context as FutachaApplication).appStateStore
        val original = runBlocking { sharedStore.isUpdateCheckEnabled.first() }
        try {
            runBlocking { sharedStore.setUpdateCheckEnabled(true) }
            rule.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                    MaterialTheme {
                        CompatibilityApp(
                            store = store,
                            repository = null,
                            stateStore = sharedStore,
                            onExitApplication = {}
                        )
                    }
                }
            }

            rule.onNodeWithContentDescription("その他").performClick()
            rule.onNodeWithText("設定").performClick()
            rule.onNodeWithTag("compat-settings-list-root")
                .performScrollToNode(hasText("アップデート確認"))
            rule.onNodeWithText("ON・起動時に最新リリースを確認").assertIsDisplayed()
            rule.onNodeWithText("アップデート確認").performClick()
            rule.waitUntil(5_000) {
                runBlocking { !sharedStore.isUpdateCheckEnabled.first() }
            }
            rule.onNodeWithText("OFF・起動時の通信と通知を停止").assertIsDisplayed()
        } finally {
            runBlocking { sharedStore.setUpdateCheckEnabled(original) }
        }
    }

    @Test
    fun rootSettingsKeepReferenceRowsAndPutCurrentFeaturesInTheExtensionSection() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        val settingsList = rule.onNodeWithTag("compat-settings-list-root")

        listOf("基本設定", "表示オプション", "バックアップ", "その他").forEach { category ->
            settingsList.performScrollToNode(hasText(category))
            rule.onNodeWithText(category).assertIsDisplayed()
        }
        listOf("更新情報", "ライセンス", "Twitter", "バージョン").forEach { row ->
            settingsList.performScrollToNode(hasText(row))
            rule.onNodeWithText(row).assertIsDisplayed()
        }
        settingsList.performScrollToNode(hasText("Database v26", substring = true))
        rule.onNodeWithText("Database v26", substring = true).assertIsDisplayed()

        settingsList.performScrollToNode(hasText("ふたちゃ拡張"))
        rule.onNodeWithText("ふたちゃ拡張").assertIsDisplayed()
        listOf("アップデート確認", "保存済みスレッド").forEach { row ->
            settingsList.performScrollToNode(hasText(row))
            rule.onNodeWithText(row).assertIsDisplayed()
        }

        settingsList.performScrollToNode(hasText("バージョン"))
        rule.onNodeWithText("バージョン").performClick()
        val referenceMessages = listOf(
            "エンジョイ＆エキサイティング", "ペイパーキャノーーーン！", "肩が赤い",
            "完成してるの初めて見た", "こいつ、動くぞ・・・", "ツァ", "なんか寒くね！？",
            "念レス成功", "よしなに", "やよエな", "ねないこだれだ", "タキシードクイズ",
            "しもんきん", "ワグナス！"
        )
        assertEquals(
            "The reference version easter egg was not shown.",
            1,
            referenceMessages.sumOf { rule.onAllNodesWithText(it).fetchSemanticsNodes().size }
        )
    }

    @Test
    fun mainHelpMenuIsReachableAndDismissible() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("ヘルプ").performClick()
        rule.onNodeWithText("ヘルプ").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("ふたば").assertIsDisplayed()
    }

    @Test
    fun drawerSettingsClosesDrawerBeforeShowingSettings() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("設定").performClick()
        rule.onNodeWithText("基本設定").assertIsDisplayed()
        // The drawer is kept composed by Material while its close animation
        // runs, but it must no longer be visible or intercept settings taps.
        rule.onNodeWithText("履歴").assertIsNotDisplayed()

        // Settings owns the left edge. A rightward edge drag must not reopen
        // the compatibility drawer after the navigation transition (#43).
        rule.onNodeWithTag("compat-settings-list-root").performTouchInput {
            swipe(
                start = Offset(1f, visibleSize.height / 2f),
                end = Offset(visibleSize.width * 0.8f, visibleSize.height / 2f)
            )
        }
        rule.onNodeWithText("履歴").assertIsNotDisplayed()
    }

    @Test
    fun drawerUpdateCheckDoesNotInsertLayoutRowBelowToolbar() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        val settings = rule.onNodeWithContentDescription("設定")
        val toolbarBottomBefore = settings.fetchSemanticsNode().boundsInRoot.bottom
        rule.onNodeWithContentDescription("全タブ更新確認").performClick()
        rule.onNodeWithText("全タブの更新確認を開始しました").assertIsDisplayed()
        val toolbarBottomAfter = settings.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "update-check feedback must overlay the drawer instead of adding a row",
            abs(toolbarBottomAfter - toolbarBottomBefore) <= 2f
        )
    }

    @Test
    fun drawerTabContextMatchesReferenceListAndFavoriteProtection() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/761.htm"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = compatTabKey(threadUrl),
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "761",
                    title = "お気に入り済みのスレ",
                    favorite = true,
                    insertedAtEpochMillis = 761L,
                    contentUpdatedAtEpochMillis = 761L
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("開いているタブ").performClick()
        rule.onNodeWithText("お気に入り済みのスレ").performTouchInput { longClick() }
        rule.onNodeWithTag("compat-drawer-tab-context-menu").assertIsDisplayed()
        rule.onNodeWithText("お気に入り").assertIsDisplayed()
        rule.onAllNodesWithText("お気に入りを解除").assertCountEquals(0)
        rule.onNodeWithText("全て削除する").assertIsDisplayed()
        rule.onNodeWithText("お気に入りを保護する").assertIsDisplayed()
        rule.onNodeWithTag("compat-drawer-protect-favorites", useUnmergedTree = true).assertIsOn()

        // old.apk and 1.apk apply the checked protection to the single-row
        // delete command too. The former implementation bypassed it here.
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("お気に入り済みのスレ").assertIsDisplayed()

        // A newly-created reference dialog always starts checked, regardless
        // of the checkbox state from the previous opening.
        rule.onNodeWithText("お気に入り済みのスレ").performTouchInput { longClick() }
        val protect = rule.onNodeWithTag("compat-drawer-protect-favorites", useUnmergedTree = true)
        protect.assertIsOn().performClick().assertIsOff()
        pressBack()
        rule.onNodeWithText("お気に入り済みのスレ").performTouchInput { longClick() }
        rule.onNodeWithTag("compat-drawer-protect-favorites", useUnmergedTree = true).assertIsOn()
        rule.onNodeWithTag("compat-drawer-protect-favorites", useUnmergedTree = true).performClick().assertIsOff()
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("お気に入り済みのスレ")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .none { it.layoutInfo.isPlaced }
        }
    }

    @Test
    fun threadPostAndScrollDialogsMatchReferenceLabelsAndConfirmationFlow() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/811.htm"
        val tabKey = compatTabKey(threadUrl)
        val posts = (0..20).map { index ->
            CompatPostSnapshot(
                position = index,
                postNo = (811 + index).toString(),
                timestamp = "08/25 12:${index.toString().padStart(2, '0')}",
                messageHtml = "REFERENCE-CONTEXT-$index"
            )
        }
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "811",
                    title = "参照ダイアログ",
                    replyCount = posts.size,
                    checkedReplyCount = posts.size,
                    insertedAtEpochMillis = 811L,
                    contentUpdatedAtEpochMillis = 811L,
                    snapshotRevision = 811L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 811L,
                    fetchedAtEpochMillis = 811L,
                    posts = posts
                )
            )
            store.saveToolbar(
                CompatToolbarSurface.THREAD,
                compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                    CompatToolbarItem(item.key, index, item.key == "scroll")
                }
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-811", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-thread-post-811", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNode(hasTextExactly("web") and hasClickAction()).assertIsDisplayed()
        rule.onNode(hasTextExactly("del") and hasClickAction()).assertIsDisplayed()
        rule.onAllNodesWithText("WEB").assertCountEquals(0)
        rule.onAllNodesWithText("DEL").assertCountEquals(0)
        rule.onNode(hasTextExactly("del") and hasClickAction()).performClick()
        rule.onNodeWithText("削除依頼 No.811").assertIsDisplayed()
        rule.onNodeWithText("送信する").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithTag("compat-thread-post-811", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNode(hasTextExactly("削除") and hasClickAction()).performClick()
        rule.onNodeWithText("レス削除 No.811").assertIsDisplayed()
        rule.onNodeWithText("画像だけ消す").assertIsDisplayed()
        rule.onAllNodesWithText("画像だけ削除").assertCountEquals(0)
        rule.onAllNodesWithText("No.811の削除キーを入力してください").assertCountEquals(0)
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithContentDescription("スクロールバー").performClick()
        rule.onNodeWithTag("compat-thread-scroll-dialog").assertIsDisplayed()
        rule.onNodeWithText("トップ").assertIsDisplayed()
        rule.onNodeWithText("最新レス").assertIsDisplayed()
        rule.onAllNodesWithText("先頭").assertCountEquals(0)
        rule.onAllNodesWithText("最下部").assertCountEquals(0)
        rule.onAllNodesWithText("閉じる").assertCountEquals(0)
        rule.onNodeWithText("最新レス").performClick()
        rule.onAllNodesWithTag("compat-thread-scroll-dialog").assertCountEquals(0)

        rule.onNodeWithTag("compat-toolbar-command-other")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("読み上げ").performClick()
        rule.onNodeWithTag("compat-thread-speech-dialog").assertIsDisplayed()
        rule.onAllNodesWithText("読み上げプレーヤー").assertCountEquals(0)
        rule.onAllNodesWithText("再生").assertCountEquals(0)
        pressBack()
        rule.onAllNodesWithTag("compat-thread-speech-dialog").assertCountEquals(0)
    }

    @Test
    fun threadSaveProgressMatchesReferenceTitleBarsAndNonDismissibleFlow() {
        var canceled = false
        val cancelRequested = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                CompatThreadSaveProgressDialog(
                    progress = SaveProgress(
                        phase = SavePhase.DOWNLOADING,
                        current = 4,
                        total = 10,
                        currentItem = "画像を保存中"
                    ),
                    cancelRequested = cancelRequested.value,
                    onCancel = {
                        canceled = true
                        cancelRequested.value = true
                    }
                )
            }
        }

        rule.onNodeWithTag("compat-thread-save-progress-dialog").assertIsDisplayed()
        rule.onNodeWithText("スレッドを保存中").assertIsDisplayed()
        rule.onNodeWithText("画像を保存中").assertIsDisplayed()
        rule.onNodeWithText("全体の進行状況").assertIsDisplayed()
        rule.onNodeWithTag("compat-save-progress-overall").assertIsDisplayed()
        rule.onNodeWithTag("compat-save-progress-current").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("compat-thread-save-progress-dialog").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        assertTrue(canceled)
        rule.onNodeWithText("中断しています…").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").assertIsNotEnabled()
    }

    @Test
    fun imageFolderSaveUsesTheReferenceProgressAndCancellationFlow() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/829.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "829",
                    title = "画像フォルダ保存",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "829",
                            timestamp = "08/27 11:30",
                            messageHtml = "保存中断の確認",
                            imageUrl = "https://may.2chan.net/b/src/829.jpg",
                            thumbnailUrl = "https://may.2chan.net/b/thumb/829s.jpg"
                        )
                    )
                )
            )
        }
        val client = HttpClient(MockEngine { awaitCancellation() })
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        httpClient = client,
                        fileSystem = AndroidFileSystem(context),
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTagPresent("compat-thread-pager")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-829", useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithTag("compat-toolbar-command-other")
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithTag("compat-bottom-popup").assertIsDisplayed()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("ページを保存", substring = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithText("ページを保存", substring = true).performClick()
        rule.onNodeWithText("メディアのみ(フォルダ)").performClick()
        rule.onNodeWithTag("compat-thread-save-progress-dialog").assertIsDisplayed()
        rule.onNodeWithText("スレッドを保存中").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("キャンセルしました")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithText("キャンセルしました").assertIsDisplayed()
        client.close()
    }

    @Test
    fun postUpsUploadDialogMatchesReferenceCopyDefaultsAndNonDismissibleFlow() {
        var submitted = false
        var canceled = false
        rule.setContent {
            MaterialTheme {
                CompatUpsUploadDialog(
                    fileName = "reference-upload.png",
                    comment = "",
                    deleteKey = "248600",
                    onCommentChange = {},
                    onDeleteKeyChange = {},
                    onSubmit = { submitted = true },
                    onCancel = { canceled = true }
                )
            }
        }

        rule.onNodeWithTag("compat-ups-upload-dialog").assertIsDisplayed()
        rule.onNodeWithText("あぷ小アップロード").assertIsDisplayed()
        rule.onNodeWithText("アップロードファイル").assertIsDisplayed()
        rule.onNodeWithText("reference-upload.png").assertIsDisplayed()
        rule.onNodeWithText("コメント").assertIsDisplayed()
        rule.onNodeWithText("削除キー").assertIsDisplayed()
        rule.onNodeWithText("248600").assertIsDisplayed()
        rule.onNodeWithText("送信する").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").assertIsDisplayed()
        rule.onAllNodesWithText("あぷ小へアップロード").assertCountEquals(0)
        rule.onAllNodesWithText("アップロード").assertCountEquals(0)
        rule.onAllNodesWithText("3000KBまで。完了後に生成されたファイル名を本文末尾へ追記します。").assertCountEquals(0)

        pressBack()
        rule.onNodeWithTag("compat-ups-upload-dialog").assertIsDisplayed()
        rule.onNodeWithText("送信する").performClick()
        assertTrue(submitted)
        rule.onNodeWithText("キャンセル").performClick()
        assertTrue(canceled)
    }

    @Test
    fun postAttachmentPreviewMatchesReferenceOrderDimensionsAndVideoAffordance() {
        var imageOpened = false
        var videoOpened = false
        val landscapePng = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLZAAAAAElFTkSuQmCC",
            Base64.DEFAULT
        )
        rule.setContent {
            MaterialTheme {
                Column {
                    CompatPostAttachmentPreview(
                        attachment = ImageData(landscapePng, "landscape.png"),
                        onImagePreview = { imageOpened = true },
                        onVideoPreview = {}
                    )
                    CompatPostAttachmentPreview(
                        attachment = ImageData(byteArrayOf(1), "reference.webm"),
                        onImagePreview = {},
                        onVideoPreview = { videoOpened = true }
                    )
                }
            }
        }

        val thumbnails = rule.onAllNodesWithTag("compat-post-attachment-thumbnail")
            .fetchSemanticsNodes()
        val fileNames = rule.onAllNodesWithTag("compat-post-attachment-file-name")
            .fetchSemanticsNodes()
        assertEquals(2, thumbnails.size)
        assertEquals(2, fileNames.size)
        assertTrue(thumbnails[0].boundsInRoot.bottom <= fileNames[0].boundsInRoot.top)
        val expectedImageWidth = with(rule.density) { 150.dp.toPx() }
        val expectedImageHeight = with(rule.density) { 75.dp.toPx() }
        assertTrue(abs(thumbnails[0].boundsInRoot.width - expectedImageWidth) <= 1f)
        assertTrue(abs(thumbnails[0].boundsInRoot.height - expectedImageHeight) <= 1f)
        val expectedVideoSize = with(rule.density) { 100.dp.toPx() }
        assertTrue(abs(thumbnails[1].boundsInRoot.width - expectedVideoSize) <= 1f)
        assertTrue(abs(thumbnails[1].boundsInRoot.height - expectedVideoSize) <= 1f)

        rule.onNodeWithContentDescription("添付画像をプレビュー").performClick()
        rule.onNodeWithContentDescription("添付動画を開く").performClick()
        assertTrue(imageOpened)
        assertTrue(videoOpened)
    }

    @Test
    fun drawerRemembersLastSelectedPageAfterCloseAndReopen() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithContentDescription("巡回結果").performClick()
        rule.onNodeWithText("にじろぐ(仮) 未インストール").assertIsDisplayed()

        pressBack()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("ドロワー")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { it.layoutInfo.isPlaced }
        }
        rule.onNodeWithContentDescription("ドロワー").performClick()

        // Closing the drawer clears only its visibility. The selected page
        // must remain WATCHER instead of falling back to TABS/HISTORY (#41).
        rule.onNodeWithText("にじろぐ(仮) 未インストール").assertIsDisplayed()
    }

    @Test
    fun drawerRowsAndFiveToolbarImagesMatchOldAndFinalApk() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val firstUrl = "${boardUrl}res/5100.htm"
        val selectedUrl = "${boardUrl}res/5101.htm"
        val updatedAt = 1_720_368_960_000L
        val first = CompatTab(
            key = compatTabKey(firstUrl),
            canonicalUrl = firstUrl,
            originalUrl = firstUrl,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = "5100",
            title = "LIVE-NONSELECTED-DRAWER",
            replyCount = 105,
            checkedReplyCount = 100,
            insertedAtEpochMillis = updatedAt - 1_000L,
            contentUpdatedAtEpochMillis = updatedAt
        )
        val selected = CompatTab(
            key = compatTabKey(selectedUrl),
            canonicalUrl = selectedUrl,
            originalUrl = selectedUrl,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = "5101",
            title = "SELECTED-DRAWER",
            replyCount = 1,
            checkedReplyCount = 1,
            insertedAtEpochMillis = updatedAt,
            contentUpdatedAtEpochMillis = updatedAt
        )
        val history = CompatHistoryEntry(
            canonicalUrl = firstUrl,
            originalUrl = firstUrl,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = "5100",
            title = first.title,
            replyCount = 105,
            contentUpdatedAtEpochMillis = updatedAt
        )
        runBlocking {
            // A local watch word may add this entry to compatibility history,
            // but old.apk/1.apk's watcher page is provider-owned and must not
            // duplicate that built-in history entry.
            store.savePreference("compat.catalog.監視ワード", "LIVE-NONSELECTED")
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(first, history)
            store.openTab(selected)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithText(first.title).assertIsDisplayed()
        val expectedSubtitle = SimpleDateFormat("M/d HH:mm", Locale.JAPAN)
            .format(Date(updatedAt)) + " mayb"
        rule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
        rule.onNodeWithText("100").assertIsDisplayed()
        rule.onNodeWithText("+5").assertIsDisplayed()
        rule.onAllNodesWithText("+0").assertCountEquals(0)

        val expectedRowHeight = with(rule.density) { 50.dp.toPx() }
        val rowNode = rule.onNodeWithText(first.title)
        rule.waitUntil(5_000) {
            rowNode.fetchSemanticsNode().boundsInRoot.left >= 0f
        }
        val rowBounds = rowNode.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(abs(rowBounds.height - expectedRowHeight) <= 1f)

        val titlePixels = rule.onNodeWithText(first.title)
            .captureToImage().toPixelMap()
        assertTrue(
            "A live non-selected tab must not be painted as a dead gray tab",
            (0 until titlePixels.height).any { y ->
                (0 until titlePixels.width).any { x ->
                    val pixel = titlePixels[x, y]
                    pixel.alpha > 0.5f && pixel.red < 0.25f && pixel.green < 0.25f && pixel.blue < 0.25f
                }
            }
        )

        val toolbarKeys = listOf("tabs", "history", "watcher", "check_all", "settings")
        val iconSignatures = toolbarKeys.map { key ->
            val pixels = rule.onNodeWithTag("compat-toolbar-icon-$key", useUnmergedTree = true)
                .assertIsDisplayed().captureToImage().toPixelMap()
            assertEquals(with(rule.density) { 30.dp.roundToPx() }, pixels.width)
            assertEquals(with(rule.density) { 30.dp.roundToPx() }, pixels.height)
            buildList {
                for (y in 0 until pixels.height) {
                    for (x in 0 until pixels.width) add(pixels[x, y].toArgb())
                }
            }
        }
        assertEquals(5, iconSignatures.distinct().size)

        rule.onNodeWithContentDescription("履歴").performClick()
        rule.onNodeWithText("履歴").assertIsDisplayed()
        rule.onNodeWithText(first.title).assertIsDisplayed()
        rule.onNodeWithText(expectedSubtitle).assertIsDisplayed()
        rule.onNodeWithText("100").assertIsDisplayed()
        rule.onNodeWithText("+5").assertIsDisplayed()

        rule.onNodeWithContentDescription("巡回結果").performClick()
        rule.onNodeWithText("にじろぐ(仮) 未インストール").assertIsDisplayed()
        rule.onAllNodesWithText(first.title).assertCountEquals(0)
    }

    @Test
    fun drawerLeftSwipeClosesDrawerWithoutDeletingHistory() {
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "${boardUrl}res/4242.htm"
        val history = CompatHistoryEntry(
            canonicalUrl = threadUrl,
            originalUrl = threadUrl,
            boardKey = compatBoardKey(boardUrl),
            boardName = "mayb",
            threadNo = "4242",
            title = "SWIPE-SAFE-HISTORY",
            replyCount = 42,
            contentUpdatedAtEpochMillis = 1L
        )
        runBlocking {
            store.upsertBoard(
                CompatBoard(history.boardKey, history.boardName, boardUrl, boardUrl, 0)
            )
            store.upsertHistory(history)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithText(history.title).assertIsDisplayed().performTouchInput { swipeLeft() }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("ドロワー")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .any { it.layoutInfo.isPlaced }
        }

        // Leftward swipes belong to the drawer. Rightward dismissal remains
        // available separately, matching the reference APK (#42).
        assertEquals(listOf(history), runBlocking { store.history.first() })
    }

    @Test
    fun drawerRightSwipeDismissesHistoryWithoutClosingTheDrawer() {
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "${boardUrl}res/4343.htm"
        val history = CompatHistoryEntry(
            canonicalUrl = threadUrl,
            originalUrl = threadUrl,
            boardKey = compatBoardKey(boardUrl),
            boardName = "mayb",
            threadNo = "4343",
            title = "RIGHT-SWIPE-HISTORY",
            replyCount = 43,
            contentUpdatedAtEpochMillis = 1L
        )
        runBlocking {
            store.upsertBoard(
                CompatBoard(history.boardKey, history.boardName, boardUrl, boardUrl, 0)
            )
            store.upsertHistory(history)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.onNodeWithText(history.title).assertIsDisplayed().performTouchInput { swipeRight() }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(history.title)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .none { it.layoutInfo.isPlaced }
        }

        // The reference APK keeps rightward row dismissal while reserving a
        // leftward drag for closing the drawer (#42).
        assertTrue(runBlocking { store.history.first().isEmpty() })
    }

    @Test
    fun compatibilityThreadRendersLiteralAndNumericEmojiWithoutReplacement() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/456.htm"
        val tab = CompatTab(
            key = compatTabKey(threadUrl),
            canonicalUrl = threadUrl,
            originalUrl = threadUrl,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = "456",
            title = "絵文字テスト",
            replyCount = 1,
            insertedAtEpochMillis = 1L,
            contentUpdatedAtEpochMillis = 1L,
            snapshotRevision = 1L
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(tab)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tab.key,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    boardTitle = "mayb",
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "1",
                            author = "としあき",
                            mail = "sage",
                            timestamp = "08/06 12:00",
                            messageHtml = "本文 😀👍🏽<br>&#x1F680;<br>https://example.invalid/"
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("😀", substring = true)
        assertTextPresent("👍🏽", substring = true)
        assertTextPresent("🚀", substring = true)
        assertTextPresent("[sage]", substring = true)
        assertTextPresent("https://example.invalid/", substring = true)
        assertEquals(
            Role.Button,
            rule.onNodeWithContentDescription("リロード")
                .fetchSemanticsNode().config.let { config ->
                    runCatching { config[SemanticsProperties.Role] }.getOrNull()
                }
        )
        assertEquals(
            Role.Button,
            rule.onNodeWithContentDescription("ページ最上部へ")
                .fetchSemanticsNode().config.let { config ->
                    runCatching { config[SemanticsProperties.Role] }.getOrNull()
                }
        )
        val toolbarCell = rule.onNodeWithContentDescription("リロード").fetchSemanticsNode().boundsInRoot
        val minToolbarCell = with(rule.density) { 40.dp.toPx() }
        assertTrue("compat toolbar cell is smaller than the 40dp golden contract", toolbarCell.height >= minToolbarCell - 1f)
        assertBottomBarClearsSystemNavigation("compat-main-bottom-bar")
    }

    @Test
    fun replyFutabaThreadUrlSwitchesToTheRegisteredThreadInsideCompatibilityMode() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        fun tab(no: String, title: String) = CompatTab(
            key = compatTabKey("${boardUrl}res/$no.htm"),
            canonicalUrl = "${boardUrl}res/$no.htm",
            originalUrl = "${boardUrl}res/$no.htm",
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = no,
            title = title,
            replyCount = 1,
            insertedAtEpochMillis = no.toLong(),
            contentUpdatedAtEpochMillis = no.toLong(),
            snapshotRevision = no.toLong()
        )
        fun snapshot(tab: CompatTab, body: String) = CompatThreadSnapshot(
            tabKey = tab.key,
            revision = tab.snapshotRevision,
            fetchedAtEpochMillis = tab.snapshotRevision,
            boardTitle = "mayb",
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = tab.threadNo,
                    timestamp = "08/24 18:00",
                    messageHtml = body
                )
            )
        )
        val target = tab("789", "リンク先")
        val source = tab("456", "リンク元")
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(target)
            store.saveThreadSnapshot(snapshot(target, "TARGET-INTERNAL-BODY"))
            store.openTab(source)
            store.saveThreadSnapshot(snapshot(source, target.canonicalUrl))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = source.canonicalUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent(target.canonicalUrl)
        rule.onNode(
            hasTextExactly(target.canonicalUrl) and hasClickAction(),
            useUnmergedTree = true
        ).performClick()
        assertTextPresent("TARGET-INTERNAL-BODY")
        val persistedTabs = runBlocking { store.tabs.first() }
        assertEquals(2, persistedTabs.size)
        assertEquals("リンク先", persistedTabs.first { it.key == target.key }.title)
    }

    @Test
    fun galleryMatchesApkSquareGridSaveModeAndOverflowCommands() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/456.htm"
        val tab = CompatTab(
            key = compatTabKey(threadUrl),
            canonicalUrl = threadUrl,
            originalUrl = threadUrl,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = "456",
            title = "画像テスト",
            replyCount = 1,
            insertedAtEpochMillis = 1L,
            contentUpdatedAtEpochMillis = 1L,
            snapshotRevision = 1L
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference(
                "compat.image_search.engines",
                "google.file|google.url|lens.file|lens.url|ascii2d.url|tineye.url|" +
                    "iqdb.file|iqdb.url|saucenao.file|saucenao.url|yandex.file|yandex.url|bing.url"
            )
            store.openTab(tab)
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tab.key,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    boardTitle = "mayb",
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "1",
                            author = "としあき",
                            timestamp = "08/06 12:00",
                            messageHtml = "画像レス本文",
                            imageUrl = "https://may.2chan.net/b/src/1.gif",
                            thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg"
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("画像一覧").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("画像一覧").performClick()
        rule.onNodeWithText("画像一覧").assertIsDisplayed()
        rule.onNodeWithText("1枚").assertIsDisplayed()
        rule.onNodeWithText("GIF").assertIsDisplayed()
        val imageBounds = rule.onNodeWithTag(
            testTag = "compat-gallery-image-1",
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        assertTrue("gallery image is not square: $imageBounds", abs(imageBounds.width - imageBounds.height) <= 2f)

        rule.onNodeWithTag("compat-gallery-item-1").performTouchInput { longClick() }
        // Builder.setItems in sample/1.apk has no title/cancel row and remains
        // scrollable even when all 13 optional search targets are enabled.
        rule.onAllNodesWithText("キャンセル").assertCountEquals(0)
        rule.onNodeWithTag("compat-gallery-context-menu").performScrollToIndex(20)
        rule.waitForIdle()
        rule.onNodeWithText("Bing Visual Search (URL)").assertIsDisplayed()
        pressBack()

        rule.onNodeWithTag("compat-gallery-item-1").performClick()
        rule.onNodeWithTag("compat-viewer-image-page").performTouchInput { longClick() }
        rule.onNodeWithText("保存").assertIsDisplayed()
        rule.onNodeWithText("共有").assertIsDisplayed()
        rule.onNodeWithText("検索").assertIsDisplayed()
        rule.onAllNodesWithText("画像").assertCountEquals(0)
        rule.onAllNodesWithText("NG画像に登録").assertCountEquals(0)
        rule.onAllNodesWithText("閉じる").assertCountEquals(0)
        rule.onNodeWithText("検索").performClick()
        rule.onNodeWithTag("compat-viewer-image-search-menu").performScrollToIndex(12)
        rule.onNodeWithText("Bing Visual Search (URL)").assertIsDisplayed()
        rule.onAllNodesWithText("キャンセル").assertCountEquals(0)
        pressBack()
        rule.onNodeWithTag("compat-viewer-image-page").assertIsDisplayed()

        // ViewerActivity in both reference APKs uses two different result codes:
        // gallery keeps the current media index, while back jumps to its source post.
        rule.onNodeWithTag("compat-viewer-toolbar-icon-gallery", useUnmergedTree = true).performClick()
        rule.onNodeWithText("画像一覧").assertIsDisplayed()
        rule.onNodeWithTag("compat-gallery-item-1").performClick()
        rule.onNodeWithTag("compat-viewer-toolbar-icon-back", useUnmergedTree = true).performClick()
        assertTextPresent("画像レス本文")
        rule.onNodeWithContentDescription("画像一覧").performClick()

        rule.onNodeWithContentDescription("一括保存").performClick()
        rule.onNodeWithText("0/1件選択").assertIsDisplayed()
        rule.onNodeWithTag("compat-gallery-item-1").performClick()
        rule.onNodeWithText("1/1件選択").assertIsDisplayed()
        rule.onNodeWithTag("compat-gallery-selection-1", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("選択したメディアを保存").performClick()
        rule.onNodeWithText("一括保存").assertIsDisplayed()
        rule.onNodeWithText("選択した1件の画像・動画を保存します").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("表示オプション").assertIsDisplayed()
        rule.onNodeWithText("設定").assertIsDisplayed()
        rule.onNodeWithText("ヘルプ").assertIsDisplayed()
        rule.onNodeWithText("全解除").assertIsDisplayed()
    }

    @Test
    fun apuSmallThumbnailDisabledKeepsBodyUrlsWithoutAddingPreviewRows() {
        val boardUrl = "https://img.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/1458679789.htm"
        val tabKey = compatTabKey(threadUrl)
        val body = "fu7099123.jpg<br>fu7099124.png"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "img/b", boardUrl, boardUrl, 0))
            store.savePreference("compat.thread.threadUpsThumbMethod", "表示しない")
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "img/b",
                    threadNo = "1458679789",
                    title = "あぷ小設定テスト",
                    replyCount = 0,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "1",
                            timestamp = "08/15 12:00",
                            messageHtml = body
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("fu7099123.jpg", substring = true)
        assertTextPresent("fu7099124.png", substring = true)
        assertTrue(
            "disabled あぷ小 thumbnails must not add an Image row",
            rule.onAllNodesWithContentDescription("あぷ小画像を開く")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        )
    }

    @Test
    fun missingNgScopeIsIgnoredWithoutCrashing() {
        val saved = runBlocking {
            store.upsertNgRule(
                CompatNgRule(
                    id = compatNgRuleId(CompatNgKind.THREAD_IMAGE, "missing-tab", "https://example.invalid/image.jpg"),
                    kind = CompatNgKind.THREAD_IMAGE,
                    scopeKey = "missing-tab",
                    normalizedValue = "https://example.invalid/image.jpg",
                    createdAtEpochMillis = 1L
                )
            )
        }

        assertFalse(saved)
        assertEquals(emptyList<CompatNgRule>(), runBlocking { store.ngRules.first() })
    }

    @Test
    fun mainBoardHandleDragReordersAndPersistsOnDrop() {
        val boards = listOf("板A", "板B", "板C").mapIndexed { index, name ->
            val url = "https://may.2chan.net/${('a'.code + index).toChar()}/"
            CompatBoard(
                key = compatBoardKey(url),
                name = name,
                canonicalUrl = url,
                originalUrl = url,
                sortOrder = index
            )
        }
        runBlocking { boards.forEach { store.upsertBoard(it) } }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("並び替え").performClick()
        rule.onAllNodesWithContentDescription("ハンドル", useUnmergedTree = true)[0].performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 520f),
                durationMillis = 1_000L
            )
        }
        val persistedOrder = runBlocking {
            withTimeout(5_000L) {
                store.boards.first { current ->
                    current.map { it.name } == listOf("板B", "板C", "板A")
                }.map { it.name }
            }
        }
        assertEquals(listOf("板B", "板C", "板A"), persistedOrder)
    }

    @Test
    fun mainBoardDialogsMatchReferenceAndPersistAddEditDelete() {
        val existingUrl = "https://may.2chan.net/b/"
        val existing = CompatBoard(
            key = compatBoardKey(existingUrl),
            name = "mayb",
            canonicalUrl = existingUrl,
            originalUrl = existingUrl,
            sortOrder = 0
        )
        runBlocking { store.upsertBoard(existing) }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("板一覧").performClick()
        rule.onNodeWithText("板一覧の取得").assertIsDisplayed()
        rule.onNodeWithText("ふたばちゃんねるアドレス").assertIsDisplayed()
        rule.onNodeWithText("更新する").assertIsDisplayed()
        rule.onNodeWithText("更新する").performClick()
        rule.waitUntil(5_000) {
            runCatching {
                rule.onNodeWithText("アドレスを確認して下さい").assertIsDisplayed()
                rule.onNodeWithText("板一覧の取得").assertIsDisplayed()
            }.isSuccess
        }
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("新規追加").performClick()
        rule.onNodeWithText("新しい板の追加").assertIsDisplayed()
        rule.onNodeWithText("表示名").assertIsDisplayed()
        rule.onNodeWithText("URL").assertIsDisplayed()
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText(
            "表示名を入力して下さい\n" +
                "アドレスを入力して下さい\n" +
                "正しいURLを入力して下さい\nhttps://***.2chan.net/***/"
        ).assertIsDisplayed()
        rule.onNodeWithTag("compat-board-name-input", useUnmergedTree = true)
            .performTextReplacement(" 追 加　板 ")
        rule.onNodeWithTag("compat-board-url-input", useUnmergedTree = true)
            .performTextReplacement(" https://img.2chan.net/b/futaba.htm ")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("追加板を追加しました", substring = true).assertIsDisplayed()
        val addedUrl = "https://img.2chan.net/b/"
        rule.waitUntil(5_000) {
            runBlocking { store.boards.first().any { it.canonicalUrl == addedUrl && it.name == "追加板" } }
        }
        rule.onNodeWithText("追加板").assertIsDisplayed().performTouchInput { longClick() }
        rule.onNodeWithText("名前を変更").performClick()
        rule.onNodeWithText("名前の変更").assertIsDisplayed()
        rule.onNodeWithText("表示名").assertIsDisplayed()
        rule.onNodeWithText("更新する").assertIsDisplayed()
        rule.onNodeWithTag("compat-board-name-input", useUnmergedTree = true)
            .performTextReplacement("改名板")
        rule.onNodeWithText("更新する").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.boards.first().any { it.canonicalUrl == addedUrl && it.name == "改名板" } }
        }

        rule.onNodeWithText("改名板").assertIsDisplayed().performTouchInput { longClick() }
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("板の削除").assertIsDisplayed()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("改名板を削除しました", substring = true).assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking { store.boards.first().none { it.canonicalUrl == addedUrl } }
        }
    }

    @Test
    fun missingCatalogAndThreadRowsAreReachableAndPersistStableApkKeys() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        // The root settings list is a LazyColumn.  The shorter Samsung
        // viewport does not compose the display-options group until the list
        // is moved there; relying on the taller emulator made this test pass
        // for the wrong reason.
        rule.onNodeWithTag("compat-settings-list-root").performScrollToIndex(7)
        rule.onNodeWithText("カタログ画面").performClick()

        // The row is in the second group of the catalog LazyColumn.  Compose
        // only exposes it after that group has been composed on short devices.
        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(5)
        rule.onNodeWithText("低画質サムネイル").assertIsDisplayed()

        // 1.apk declares catalogAppendDropped's android:dependency on
        // catalogFindThreadDeleted. Keep the row disabled until its parent
        // switch is enabled, and prove both raw values persist independently.
        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(23)
        val findDropped = rule.onNodeWithTag("compat-setting-catalogFindThreadDeleted")
        val appendDropped = rule.onNodeWithTag("compat-setting-catalogAppendDropped")
        findDropped.assertIsEnabled()
        appendDropped.assertIsNotEnabled()
        findDropped.performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.catalog.catalogFindThreadDeleted") } == "ON"
        }
        appendDropped.assertIsEnabled().performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.catalog.catalogAppendDropped") } == "ON"
        }
        // Restore the reference defaults so repeated and full-suite runs do
        // not inherit this test's dependent switches.
        appendDropped.performClick()
        findDropped.performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                store.loadPreference("compat.catalog.catalogFindThreadDeleted") == "OFF" &&
                    store.loadPreference("compat.catalog.catalogAppendDropped") == "OFF"
            }
        }
        appendDropped.assertIsNotEnabled()

        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(13)
        rule.onNodeWithTag("compat-setting-catalogListViewTitleLength").assertIsDisplayed()
        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(9)
        rule.onNodeWithTag("compat-setting-catalogGridViewTitleLength").performScrollTo().performClick()
        rule.onNodeWithTag("compat-setting-options").performScrollToIndex(19)
        rule.onNodeWithText("19").assertIsDisplayed()
        rule.onNodeWithTag("compat-setting-options").performScrollToIndex(30)
        rule.onNodeWithText("30").assertIsDisplayed()
        rule.onNodeWithText("30").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.catalog.catalogGridViewTitleLength") } == "30"
        }

        // A long ListPreference leaves its dialog at index 30. The following
        // three-choice dialog must start from its own state instead of opening
        // as a blank sheet with an inherited out-of-range index.
        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(20)
        rule.onNodeWithTag("compat-setting-catalogTitleLength").performScrollTo().performClick()
        rule.onNodeWithText("10文字").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.catalog.catalogTitleLength") } == "10"
        }

        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("スレッド画面").performScrollTo().performClick()
        rule.onNodeWithTag("compat-settings-list-thread").performScrollToIndex(3)
        rule.onNodeWithText("オートスクロール量").assertIsDisplayed()
        rule.onNodeWithTag("compat-settings-list-thread").performScrollToIndex(6)
        rule.onNodeWithText("デフォルトの名前と題名を非表示").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.thread.threadHideDefaultNameAndSubject") } == "ON"
        }
    }

    @Test
    fun designSettingsPersistReferenceRawValuesAndShowExactRedrawAndFontDialogs() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("デザイン").performClick()
        rule.onNodeWithText("スタイル").assertIsDisplayed()
        rule.onNodeWithText("タブ一覧").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-designTheme").performClick()
        rule.mainClock.autoAdvance = false
        try {
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithText("ブラック").performClick()
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("compat-settings-transient-notice").assertIsDisplayed()
            rule.onNodeWithText("画面の再描画時に反映されます").assertIsDisplayed()
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.design.designTheme") } == "black"
        }
        rule.onNodeWithText("ブラック").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-designLoading").performClick()
        rule.onNodeWithText("アイコン").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.design.designLoading") } == "icon"
        }
        rule.onNodeWithText("アイコン").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-designTabSelectorLocation").performClick()
        rule.onNodeWithText("ツールバーの上に重ねる").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.design.designTabSelectorLocation") } == "over"
        }
        rule.onNodeWithText("ツールバーに重ねる").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-dummyCustomFont").performClick()
        rule.onAllNodesWithText("カスタムフォント").assertCountEquals(2)
        rule.onAllNodesWithText("*.ttf  *.otf").assertCountEquals(0)
        listOf("選択", "リセット", "キャンセル").forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithTag("compat-setting-dummyCustomFont").performClick()
        rule.onNodeWithText("リセット").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.design.dummyCustomFont") } == "デフォルト"
        }
        rule.onNodeWithText("アプリを再起動してください").assertIsDisplayed()
    }

    @Test
    fun threadDisplayOptionsMatchReferenceActivityPersistRawValuesAndReturnDirectly() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/821.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.thread.threadHeaderQuoteSimple", "ON")
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "821",
                    title = "スレッド設定参照テスト",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "821",
                            timestamp = "08/25 12:00",
                            messageHtml = "設定を反映する本文",
                            referencedCount = 2
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTagPresent("compat-thread-pager")
        rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()
        rule.onNodeWithContentDescription("返信数").assertIsDisplayed()
        fun openDisplayOptions() {
            rule.onAllNodesWithContentDescription("その他")[0].performClick()
            rule.onNodeWithText("表示オプション").performClick()
            rule.onNodeWithText("スレッド設定").assertIsDisplayed()
        }

        openDisplayOptions()
        rule.onNodeWithText("全般").assertIsDisplayed()
        rule.onNodeWithTag("compat-settings-list-thread").performScrollToNode(
            hasText("抽出する閾値")
        )
        rule.onNodeWithText("抽出する閾値").assertIsDisplayed()
        rule.onAllNodesWithText("ふたちゃ拡張").assertCountEquals(0)
        rule.onAllNodesWithText("画像NG類似度閾値").assertCountEquals(0)

        val uploaderPolicy = rule.onNodeWithTag("compat-setting-threadUpsThumbMethod")
            .performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val checkboxRow = rule.onNodeWithTag("compat-setting-threadAdminDeleteShow")
            .performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val listRow = rule.onNodeWithTag("compat-setting-threadHeaderSoudaneDisplay")
            .performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(uploaderPolicy.height - with(rule.density) { 73.dp.toPx() }) <= 1f)
        assertTrue(abs(checkboxRow.height - with(rule.density) { 54.dp.toPx() }) <= 1f)
        assertTrue(abs(listRow.height - with(rule.density) { 73.dp.toPx() }) <= 1f)
        rule.onNodeWithText("表示する").assertIsDisplayed()
        rule.onAllNodesWithText("表示しない").assertCountEquals(0)

        rule.onNodeWithTag("compat-setting-threadHeaderSoudaneDisplay").performClick()
        rule.onNodeWithText("シンプル(右寄せ)").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.thread.threadHeaderSoudaneDisplay") } == "simple|right"
        }
        rule.onNodeWithTag("compat-setting-threadHeaderSoudaneDisplay").performScrollTo()
        rule.onNodeWithText("シンプル(右寄せ)").assertIsDisplayed()

        // ThreadTabActivity starts ThreadSettingActivity directly. Its Back
        // finishes to the thread; it must not detour through AppSettingActivity.
        rule.onNodeWithContentDescription("戻る").performClick()
        waitForTagPresent("compat-thread-pager")
        rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()
        rule.onAllNodesWithText("設定").assertCountEquals(0)

        openDisplayOptions()
        rule.onNodeWithTag("compat-setting-threadHeaderSoudaneDisplay").performScrollTo()
        rule.onNodeWithText("シンプル(右寄せ)").assertIsDisplayed()
        pressBack()
        waitForTagPresent("compat-thread-pager")
        rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()
    }

    @Test
    fun controlSettingsMatchReferenceRowsPersistRawValuesAndIsolateExtensions() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithTag("compat-settings-list-root").performScrollToNode(hasText("コントロール"))
        rule.onNodeWithText("コントロール").performClick()
        rule.onNodeWithText("コントロール").assertIsDisplayed()

        val settingsList = rule.onNodeWithTag("compat-settings-list-control")
        listOf("カタログ画面", "スレッド画面", "ツールバー", "書き込み画面", "画面ビューア").forEach { category ->
            settingsList.performScrollToNode(hasText(category))
            rule.onNodeWithText(category).assertIsDisplayed()
        }

        // A LazyColumn does not compose every off-screen row on compact API 26
        // devices. Ask the list to materialize the row before addressing it.
        settingsList.performScrollToNode(hasTestTag("compat-setting-controlCatalogLongTap"))
        rule.onNodeWithTag("compat-setting-controlCatalogLongTap").performClick()
        rule.onNodeWithText("NGスレッドに登録").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.control.controlCatalogLongTap") } == "ng"
        }
        rule.onNodeWithTag("compat-setting-controlCatalogLongTap").performScrollTo()
        rule.onNodeWithText("NGスレッドに登録").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-controlThreadVolumeKey").performScrollTo().performClick()
        rule.onNodeWithText("1レス分スクロール").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.control.controlThreadVolumeKey") } == "response"
        }

        // Current-only controls remain available, but they must not alter the
        // five categories and nine rows exposed by AppControlSettingActivity.
        settingsList.performScrollToNode(hasText("ふたちゃ拡張"))
        rule.onNodeWithText("ふたちゃ拡張").assertIsDisplayed()
        rule.onNodeWithText("タブを閉じた時の通知").assertIsDisplayed()
        rule.onNodeWithText("板名の誤投稿確認").assertIsDisplayed()

        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("設定").assertIsDisplayed()
    }

    @Test
    fun imageSearchUsesTheReferenceDedicatedSettingsPageAndCheckboxRows() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("画像検索").assertIsDisplayed()
        rule.onNodeWithText("長押しメニューの整理").assertIsDisplayed()
        rule.onNodeWithText("画像検索").performClick()

        rule.onNodeWithText("長押しメニューに出す検索先").assertIsDisplayed()
        rule.onNodeWithText(
            "File方式は画像そのものを送り、結果をアプリ内蔵ブラウザで表示します。",
            substring = true
        ).assertIsDisplayed()
        rule.onAllNodesWithText("選択中").assertCountEquals(0)
        rule.onNodeWithText("Google画像検索 (File)").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.image_search.engines") }
                ?.contains("google.file") == true
        }

        rule.onNodeWithTag("compat-settings-list-image_search").performScrollToIndex(14)
        rule.onNodeWithText("Bing Visual Search (URL)").assertIsDisplayed()
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("長押しメニューの整理").assertIsDisplayed()
    }

    @Test
    fun ascii2dAndImageCompressionDialogsMatchBothReferenceApks() {
        val mode = mutableStateOf("ascii2d")
        val show = mutableStateOf(true)
        val endpoint = mutableStateOf("")
        val invalidCount = AtomicInteger(0)
        val registered = AtomicReference<String?>(null)
        val compressed = AtomicInteger(0)
        rule.setContent {
            MaterialTheme {
                when (mode.value) {
                    "ascii2d" -> if (show.value) {
                        CompatAscii2dRegistrationDialog(
                            initialEndpoint = endpoint.value,
                            onDismiss = { show.value = false },
                            onRegister = {
                                registered.set(it)
                                show.value = false
                            },
                            onInvalid = { invalidCount.incrementAndGet() }
                        )
                    }
                    else -> CompatPostImageCompressConfirmation(
                        onCompress = { compressed.incrementAndGet() },
                        onCancel = {}
                    )
                }
            }
        }

        rule.onNodeWithText("詳細画像検索の設定").assertIsDisplayed()
        rule.onNodeWithText("アドレス ※わかる人向け").assertIsDisplayed()
        rule.onNodeWithText("https://").assertIsDisplayed()
        rule.onNodeWithText("登録する").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { invalidCount.get() == 1 }
        rule.onAllNodesWithText("詳細画像検索の設定").assertCountEquals(0)

        rule.runOnIdle {
            endpoint.value = "https://ascii2d.net/search/url/"
            show.value = true
        }
        rule.onNodeWithText("登録する").performClick()
        rule.waitUntil(5_000) { registered.get() != null }
        assertEquals("https://ascii2d.net/search/url/", registered.get())

        rule.runOnIdle { mode.value = "compress" }
        rule.onNodeWithText("確認").assertIsDisplayed()
        rule.onNodeWithText("画像をリサイズしますか？").assertIsDisplayed()
        rule.onNodeWithText("圧縮する").assertIsDisplayed().performClick()
        assertEquals(1, compressed.get())
        rule.onNodeWithText("キャンセル").assertIsDisplayed()
    }

    @Test
    fun storageMatchesReferenceRowsDialogsRawValuesAndSeparateCacheUsage() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("ストレージ").performClick()
        val settingsList = rule.onNodeWithTag("compat-settings-list-storage")
        rule.onNodeWithText("保存先").assertIsDisplayed()
        rule.onNodeWithText("未設定時：標準フォルダに保存").assertIsDisplayed()
        rule.onNodeWithTag("compat-setting-dummyDownloadDir").performClick()
        rule.onNodeWithText("ダウンロード").assertIsDisplayed()
        rule.onNodeWithText("画像の保存などに利用します", substring = true).assertIsDisplayed()
        rule.onNodeWithText("フォルダ選択").assertIsDisplayed()
        rule.onNodeWithText("リセット").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.storage.dummyDownloadDir") } == ""
        }
        rule.onNodeWithText("未設定時：標準フォルダに保存").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-dummyDrawingDir").performClick()
        rule.onNodeWithText("手書き").assertIsDisplayed()
        rule.onNodeWithText("手書き画像の保存に利用します", substring = true).assertIsDisplayed()
        listOf("フォルダ選択", "リセット", "キャンセル").forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithTag("compat-setting-commonImageCache").performScrollTo().performClick()
        rule.onNodeWithText("1GB").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.storage.commonImageCache") } == "1024"
        }
        rule.onNodeWithTag("compat-setting-commonImageCache").performScrollTo()
        rule.onNodeWithText("1024MB").assertIsDisplayed()

        rule.onNodeWithTag("compat-setting-dummyCatalogImageCacheLocation").performScrollTo().performClick()
        rule.onNodeWithText("内部ストレージ").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.storage.dummyCatalogImageCacheLocation") } == "internal"
        }
        rule.onNodeWithTag("compat-setting-dummyCatalogImageCacheLocation").performScrollTo()
        rule.onNodeWithText("内部ストレージ").assertIsDisplayed()

        settingsList.performScrollToIndex(6)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("現在の使用量:画像 ", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("現在の使用量:画像 ", substring = true).assertIsDisplayed()
        rule.onNodeWithText(" / カタログ ", substring = true).assertIsDisplayed()
        settingsList.performScrollToNode(hasText("スレッドキャッシュのクリア"))
        rule.onNodeWithTag("compat-setting-dummyThreadCacheClear")
            .assert(hasText("現在の使用量:0.00MB"))
        settingsList.performScrollToNode(hasText("その他のクリア"))
        rule.onNodeWithTag("compat-setting-dummyAttachFileClear")
            .assert(hasText("現在の使用量:0.00MB"))
    }

    @Test
    fun settingPtmtEditDialogMatchesFinalApkFieldsActionsAndValidation() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        cookieRepository = (context.applicationContext as FutachaApplication).cookieRepository,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithTag("compat-settings-list-root")
            .performScrollToNode(hasText("ptmtクッキーの編集"))
        rule.onNodeWithTag("compat-setting-ptmtEditor").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-ptmt-change", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        rule.onAllNodesWithText("ptmtクッキーの編集").assertCountEquals(2)
        rule.onNodeWithTag("compat-ptmt-value", useUnmergedTree = true).fetchSemanticsNode()
        rule.onNodeWithTag("compat-ptmt-check", useUnmergedTree = true).fetchSemanticsNode()
        rule.onNodeWithText("誤操作防止の為", substring = true).assertIsDisplayed()
        listOf("リセット", "キャンセル", "変更する").forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }

        rule.onNodeWithTag("compat-ptmt-check", useUnmergedTree = true).performTextInput("誤字")
        rule.onNodeWithTag("compat-ptmt-change", useUnmergedTree = true).performClick()
        rule.onNodeWithText("決意に誤字があります").assertIsDisplayed()
        rule.onNodeWithTag("compat-ptmt-cancel", useUnmergedTree = true).performClick()
    }

    @Test
    fun reverseSearchLongPressAcceptsOnlyLinkHitTypes() {
        assertEquals(
            "https://example.com/result",
            compatReverseSearchLongPressedLink(
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                "  https://example.com/result  "
            )
        )
        assertEquals(
            "https://example.com/image",
            compatReverseSearchLongPressedLink(
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                "https://example.com/image"
            )
        )
        assertNull(
            compatReverseSearchLongPressedLink(
                WebView.HitTestResult.IMAGE_TYPE,
                "https://example.com/not-a-link"
            )
        )
    }

    @Test
    fun settingsRootScrollPositionSurvivesReturningFromChildPage() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        // Put the root at the display-options section, where the child route
        // is visible on both the emulator and the short Samsung viewport.
        rule.onNodeWithTag("compat-settings-list-root").performScrollToIndex(7)
        rule.onNodeWithText("カタログ画面").performClick()
        rule.onNodeWithTag("compat-settings-list-catalog").performScrollToIndex(13)
        rule.onNodeWithContentDescription("戻る").performClick()

        // If the root LazyColumn was recreated at index 0, the backup heading
        // is not composed on the short device. Its presence proves that the
        // root anchor survived the child-page round trip (#39).
        rule.onNodeWithTag("compat-settings-list-root").assertIsDisplayed()
        rule.onNodeWithText("バックアップ").assertIsDisplayed()
    }

    @Test
    fun archiveReportPrivacyControlsToggleExplainAndDeleteLocalOutbox() {
        runBlocking {
            store.enqueueArchiveReport("https://may.2chan.net/b/res/123.htm", 1_000L)
        }
        var lastEnabledCallback: Boolean? = null
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        onArchiveReportEnabledChanged = { lastEnabledCallback = it },
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        // "送信内容について" is the final row (index 30).  Index 29 is
        // enough to compose it on a tall emulator but not on the short
        // landscape-safe viewport of the Samsung device.
        rule.onNodeWithTag("compat-settings-list-root").performScrollToIndex(30)
        rule.onNodeWithText("閲覧スレ通知").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.archive_report.enabled") } == "OFF" &&
                lastEnabledCallback == false
        }
        assertFalse(lastEnabledCallback ?: true)

        rule.onNodeWithText("送信内容について").performScrollTo().performClick()
        rule.onNodeWithText("閲覧スレ通知について").assertIsDisplayed()
        rule.onNodeWithText("閉じる").performClick()

        rule.onNodeWithText("通知データを削除").performScrollTo().performClick()
        rule.onNodeWithText("未送信、再送待ち、受付済み、送信対象外の記録をすべて端末から削除します。この操作は元に戻せません。")
            .assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) { runBlocking { store.archiveReportOutboxStats().total == 0 } }
    }

    @Test
    fun successfulVisibleThreadNetworkLoadQueuesArchiveReport() {
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "https://may.2chan.net/b/res/123.htm"
        runBlocking {
            store.upsertBoard(
                CompatBoard(
                    key = compatBoardKey(boardUrl),
                    name = "二次元裏",
                    canonicalUrl = boardUrl,
                    originalUrl = boardUrl,
                    sortOrder = 0
                )
            )
        }
        var queuedCount = 0
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = FakeBoardRepository(),
                        initialThreadDeepLink = threadUrl,
                        onArchiveReportEnqueued = { queuedCount = it },
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            queuedCount == 1 && runBlocking { store.archiveReportOutboxStats().total == 1 }
        }
    }

    @Test
    fun backgroundMatchesReferenceRowsWarningsAndRawPolicyPersistence() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("バックグラウンド").performScrollTo().performClick()

        rule.onNodeWithText("スレッド関連").assertIsDisplayed()
        rule.onAllNodesWithText("スレッドの生存確認").assertCountEquals(1)
        rule.onAllNodesWithText("スレッドの更新確認").assertCountEquals(1)

        rule.onNodeWithText("スレッドの更新確認").performClick()
        rule.onNodeWithText("選択").assertIsDisplayed()
        rule.onNode(hasTextExactly("常に確認する")).performClick()
        rule.onNodeWithText("注意事項").assertIsDisplayed()
        rule.onNodeWithText(
            "カタログからレス数を取得して更新分を履歴やツールバーに反映させます\n" +
                "常に確認する場合は通信量などに十分注意してください"
        ).assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                store.loadPreference("compat.background.backgroundThreadUpdateCheck")
            } == "usually"
        }

        rule.onNodeWithText("スレッドの生存確認").performClick()
        rule.onNode(hasTextExactly("常に確認する")).performClick()
        rule.onNodeWithText("注意事項").assertIsDisplayed()
        rule.onNodeWithText(
            "しばらく更新されていないスレッドを確認して履歴に反映させます\n" +
                "落ちたスレを明確にしておけば履歴の管理や更新の確認に役立ちます\n" +
                "常に確認する場合は通信量などに十分注意してください"
        ).assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                store.loadPreference("compat.background.backgroundThreadExistCheck")
            } == "usually"
        }
    }

    @Test
    fun viewerPreloadPersistsFinalApkRawPolicies() {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        val rootSettings = rule.onNodeWithTag("compat-settings-list-root")
        rootSettings.performScrollToNode(hasText("画像ビューア"))
        rule.onNode(hasText("画像ビューア") and hasClickAction()).performClick()
        rule.onNodeWithText("画像ビューア設定").assertIsDisplayed()

        val preload = rule.onNodeWithTag("compat-setting-viewerPreloadMode")
        preload.assertIsDisplayed().performClick()
        rule.onNode(hasTextExactly("Wi-Fi回線のみ")).performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.viewer.viewerPreloadMode") } == "wifi"
        }

        preload.performClick()
        rule.onNode(hasTextExactly("利用しない")).performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.viewer.viewerPreloadMode") } == "none"
        }

        // Restore the 1.apk default while also proving all three raw values
        // can be selected repeatedly on the same screen.
        preload.performClick()
        rule.onNode(hasTextExactly("常に利用する")).performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.viewer.viewerPreloadMode") } == "usually"
        }
    }

    @Test
    fun backgroundReferenceTimerChecksImmediatelyAfterAllPersistedStateLoads() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/881.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "881",
                    title = "起動直後の確認",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 0L
                )
            )
            store.savePreference("compat.background.backgroundThreadUpdateCheck", "usually")
            store.savePreference("compat.background.backgroundThreadExistCheck", "usually")
        }
        val catalogChecks = AtomicInteger(0)
        val existenceChecks = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
                catalogChecks.incrementAndGet()
                return listOf(
                    CatalogItem(
                        id = "881",
                        threadUrl = threadUrl,
                        title = "起動直後の確認",
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = 3
                    )
                )
            }

            override suspend fun probeThreadGone(threadUrl: String): Boolean {
                existenceChecks.incrementAndGet()
                return false
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.waitUntil(5_000) {
            catalogChecks.get() == 1 && existenceChecks.get() == 1 && runBlocking {
                store.loadPreference(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE) != null &&
                    store.loadPreference(COMPAT_BACKGROUND_EXISTENCE_TIME_PREFERENCE) != null
            }
        }
        assertEquals(3, runBlocking { store.tabs.first().single().replyCount })
    }

    @Test
    fun networkMatchesReferenceRowsReadOnlyStatusWarningAndParallelRawValue() {
        runBlocking {
            store.savePreference(
                "compat.network.cache.status",
                "2026/08/25 19:00 - 稼働中"
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
        rule.onNodeWithText("ネットワーク").performScrollTo().performClick()

        rule.onNodeWithText("キャッシュサーバー機能").assertIsDisplayed()
        rule.onNodeWithText("通信の軽量化").assertIsDisplayed()
        rule.onNodeWithText("ステータス").assertIsDisplayed().performClick()
        rule.onNodeWithText("2026/08/25 19:00 - 稼働中").assertIsDisplayed()
        rule.onAllNodesWithText("確認中…").assertCountEquals(0)
        rule.onNodeWithText("画像の取得").assertIsDisplayed()
        rule.onNodeWithText("画像の同時取得数").assertIsDisplayed()
        rule.onNodeWithText(
            "減らすと1枚あたりの読み込みは速くなりますが、画面全体が出そろうまでは遅くなります。" +
                "回線が細い場合は少なめが有利なことがあります。"
        ).assertIsDisplayed()

        rule.onNodeWithText("通信の軽量化").performClick()
        rule.onNodeWithText("確認").assertIsDisplayed()
        rule.onNodeWithText(
            "本来のHTMLからタグを削除したり内容をコンパクトにした解析済みのデータを" +
                "サーバーから取得します\n詳しい仕様と注意点はヘルプを確認して下さい"
        ).assertIsDisplayed()
        rule.onAllNodesWithText("キャンセル").assertCountEquals(0)
        pressBack()
        rule.onNodeWithText("確認").assertIsDisplayed()
        rule.onNodeWithText("OK").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.network.cache.enabled") } == "ON"
        }

        rule.onNodeWithText("画像の同時取得数").performClick()
        rule.onAllNodesWithText("画像の同時取得数").assertCountEquals(2)
        rule.onNode(hasTextExactly("8本")).performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.network.networkImageParallel") } == "8"
        }
        rule.onNodeWithText("8本").assertIsDisplayed()
    }

    @Test
    fun networkStatusIsProbedOnReferenceHostStartupAndPersistedWithHour() {
        val requests = CopyOnWriteArrayList<String>()
        val httpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requests += request.url.encodedPath
                    when (request.url.encodedPath) {
                        "/health/search" -> respond(
                            content = "{\"ok\":true}",
                            status = HttpStatusCode.OK,
                            headers = headersOf()
                        )
                        else -> error("Unexpected cache status request: ${request.url}")
                    }
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        httpClient = httpClient,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(5_000) {
            runBlocking {
                store.loadPreference("compat.network.cache.available") == "ON" &&
                    store.loadPreference("compat.network.cache.status_date")
                        ?.matches(Regex("[0-9]{4}/[0-9]{2}/[0-9]{2} [0-9]{2}:00")) == true &&
                    store.loadPreference("compat.network.cache.status")?.contains(" - ") == true
            }
        }
        assertEquals(listOf("/health/search"), requests.toList())
        httpClient.close()
    }

    @Test
    fun catalogLightweightMenuTogglesImmediatelyWithoutSettingsWarning() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(
                        CatalogItem(
                            id = "25",
                            threadUrl = "${boardUrl}res/25.htm",
                            title = "NETWORK-TOGGLE",
                            thumbnailUrl = null,
                            fullImageUrl = null,
                            replyCount = 1
                        )
                    )
                )
            )
            store.savePreference("compat.network.cache.enabled", "OFF")
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = FakeBoardRepository(), onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onAllNodesWithContentDescription("その他")[1].performClick()
        rule.onNodeWithText("通信の軽量化").performClick()
        rule.onAllNodesWithText("確認").assertCountEquals(0)
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.network.cache.enabled") } == "ON"
        }
        rule.onNodeWithText("通信の軽量化オン").assertIsDisplayed()

        rule.onAllNodesWithContentDescription("その他")[1].performClick()
        rule.onNodeWithText("通信の軽量化").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.network.cache.enabled") } == "OFF"
        }
        rule.onNodeWithText("通信の軽量化オフ").assertIsDisplayed()
    }

    @Test
    fun catalogToolbarRendersAndSwapsTheReferenceStateArtwork() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/2600.htm"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(
                        CatalogItem(
                            id = "2600",
                            threadUrl = threadUrl,
                            title = "TOOLBAR-STATE",
                            thumbnailUrl = null,
                            fullImageUrl = null,
                            replyCount = 2
                        )
                    )
                )
            )
            store.openTab(
                CompatTab(
                    key = compatTabKey(threadUrl),
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "2600",
                    title = "更新あり",
                    replyCount = 2,
                    checkedReplyCount = 1,
                    insertedAtEpochMillis = 2_600L,
                    contentUpdatedAtEpochMillis = 2_600L
                )
            )
            store.saveToolbar(
                CompatToolbarSurface.CATALOG,
                compatToolbarMaster(CompatToolbarSurface.CATALOG).mapIndexed { index, item ->
                    CompatToolbarItem(item.key, index, active = true)
                }
            )
            store.savePreference("compat.network.cache.enabled", "OFF")
            store.savePreference("compat.catalog.NG機能", "ON")
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = FakeBoardRepository(), onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        waitForTagDisplayed("compat-catalog-grid")

        fun pixels(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)
            .assertIsDisplayed().captureToImage().toPixelMap()
        fun signature(pixelMap: androidx.compose.ui.graphics.PixelMap): List<Int> = buildList {
            for (y in 0 until pixelMap.height) {
                for (x in 0 until pixelMap.width) add(pixelMap[x, y].toArgb())
            }
        }

        val tabUpdate = pixels("compat-toolbar-icon-tab")
        assertTrue(
            "The final-APK tab-update artwork must retain its red update mark",
            (0 until tabUpdate.height).any { y ->
                (0 until tabUpdate.width).any { x ->
                    tabUpdate[x, y].red > 0.75f && tabUpdate[x, y].green < 0.45f
                }
            }
        )

        val bypassOn = signature(pixels("compat-toolbar-icon-bypass"))
        rule.onNodeWithContentDescription("通信の軽量化").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.network.cache.enabled") } == "ON"
        }
        val bypassOff = signature(pixels("compat-toolbar-icon-bypass"))
        assertTrue("Bypass ON/OFF must swap the final-APK artwork", bypassOn != bypassOff)

        val ngOn = signature(pixels("compat-toolbar-icon-quickng"))
        rule.onNodeWithContentDescription("NG").performClick()
        rule.waitUntil(5_000) {
            runBlocking { store.loadPreference("compat.catalog.NG機能") } == "OFF"
        }
        val ngOff = signature(pixels("compat-toolbar-icon-quickng"))
        assertTrue("Catalog quick-NG must swap ngon/ngoff immediately", ngOn != ngOff)
    }

    @Test
    fun catalogReplyPriorityMatchesReferencePartitionAndPersistsPerBoardFlags() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val items = listOf(
            "a" to 10,
            "b" to 2,
            "c" to 5,
            "d" to 8,
            "e" to 1
        ).map { (id, replies) ->
            CatalogItem(
                id = id,
                threadUrl = "${boardUrl}res/$id.htm",
                title = "PRIORITY-$id",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = replies
            )
        }
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.catalog.delayFewReplies", "5")
            store.savePreference("compat.catalog.監視ワード", "PRIORITY-b\nPRIORITY-c")
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = items
                )
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        waitForTagDisplayed("compat-catalog-grid")

        fun left(id: String): Float = rule.onNodeWithContentDescription("PRIORITY-$id")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.left
        assertTrue(left("c") < left("a"))
        assertTrue(left("a") < left("d"))
        assertTrue(left("d") < left("b"))
        assertTrue(left("b") < left("e"))

        fun openNgMenu() {
            val overflows = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
            val bottomOverflowIndex = overflows.indices.maxBy { index ->
                overflows[index].boundsInRoot.top
            }
            rule.onAllNodesWithContentDescription("その他")[bottomOverflowIndex].performClick()
            rule.onNodeWithText("NG管理", substring = true).performClick()
        }

        openNgMenu()
        rule.onNodeWithText("レス数優先を無効にする").assertIsDisplayed()
        rule.onNodeWithText("レス数非優先を隠す").performClick()
        rule.waitUntil(5_000) {
            runBlocking { !store.loadCatalogPreference(boardKey).showNonPriority }
        }
        rule.onAllNodesWithContentDescription("PRIORITY-b").assertCountEquals(0)
        rule.onAllNodesWithContentDescription("PRIORITY-e").assertCountEquals(0)
        assertTrue(left("c") < left("a"))
        assertTrue(left("a") < left("d"))

        openNgMenu()
        rule.onNodeWithText("レス数非優先を表示する").assertIsDisplayed()
        rule.onNodeWithText("レス数優先を無効にする").performClick()
        rule.waitUntil(5_000) {
            runBlocking { !store.loadCatalogPreference(boardKey).replyPriorityEnabled }
        }
        assertTrue(left("b") < left("c"))
        assertTrue(left("c") < left("a"))
        assertTrue(left("a") < left("d"))
        assertTrue(left("d") < left("e"))
    }

    @Test
    fun catalogSourceTitleLimitAppliesBeforeDisplayAndSearchLikeReferenceLoader() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val visiblePrefix = "1234567890"
        val discardedSuffix = "HIDDEN-BY-SOURCE-LIMIT"
        val item = CatalogItem(
            id = "9001",
            threadUrl = "${boardUrl}res/9001.htm",
            title = visiblePrefix + discardedSuffix,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 9
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.catalog.catalogTitleLength", "10")
            store.savePreference("compat.catalog.catalogGridViewTitleLength", "30")
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.onNodeWithContentDescription(visiblePrefix).assertIsDisplayed()
        rule.onAllNodesWithContentDescription(visiblePrefix + discardedSuffix).assertCountEquals(0)

        rule.onNodeWithContentDescription("スレッド検索").performClick()
        rule.onNodeWithText("検索文字").performTextInput(discardedSuffix)
        rule.onAllNodesWithContentDescription(visiblePrefix).assertCountEquals(0)
        rule.onNodeWithText("0件").assertIsDisplayed()

        rule.onNodeWithText(discardedSuffix).performTextReplacement(visiblePrefix)
        rule.onNodeWithContentDescription(visiblePrefix).assertIsDisplayed()
        rule.onNodeWithText("1件").assertIsDisplayed()
    }

    @Test
    fun catalogRendersPersistentCacheWithCenteredLoadingWhileNetworkIsBlocked() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val cachedItems = (1..100).map { number ->
            CatalogItem(
                id = number.toString(),
                threadUrl = "${boardUrl}res/$number.htm",
                title = "CACHE-$number",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = number
            )
        }
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 10L,
                    fetchedAtEpochMillis = 1_000L,
                    items = cachedItems
                )
            )
            store.savePreference("compat.catalog.catalogFastScroll", "ON")
            // This test verifies the cached-first rendering while an explicit
            // open-time refresh is in flight. The APK setting defaults to OFF;
            // opt in here so the blocked repository remains intentional.
            store.savePreference("compat.catalog.catalogOpenWithReload", "ON")
        }
        val networkStarted = AtomicBoolean(false)
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                networkStarted.set(true)
                awaitCancellation()
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) { networkStarted.get() }
        rule.onNodeWithContentDescription("CACHE-1").assertIsDisplayed()
        rule.onNodeWithTag("compat-catalog-blocking-loading").assertIsDisplayed()
        rule.onNodeWithTag("compat-fast-scrollbar").assertIsDisplayed()
    }

    @Test
    fun catalogDisplaySwitchShowsCenteredLoadingIndicatorUntilNewLayoutIsReady() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val otherBoardUrl = "https://img.2chan.net/b/"
        val otherBoardKey = compatBoardKey(otherBoardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.upsertBoard(CompatBoard(otherBoardKey, "imgb", otherBoardUrl, otherBoardUrl, 1))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(
                        CatalogItem(
                            id = "69",
                            threadUrl = "${boardUrl}res/69.htm",
                            title = "LAYOUT-SWITCH-69",
                            thumbnailUrl = null,
                            fullImageUrl = null,
                            replyCount = 1
                        )
                    )
                )
            )
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = otherBoardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(
                        CatalogItem(
                            id = "70",
                            threadUrl = "${otherBoardUrl}res/70.htm",
                            title = "GLOBAL-LAYOUT-70",
                            thumbnailUrl = null,
                            fullImageUrl = null,
                            replyCount = 1
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.onAllNodesWithContentDescription("その他")[1].performClick()
        rule.onNodeWithText("表示の切り替え").assertIsDisplayed()

        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("表示の切り替え").performClick()
            rule.mainClock.advanceTimeByFrame()

            rule.onNodeWithTag("compat-catalog-list").assertIsDisplayed()
            val catalogBounds = rule.onNodeWithTag("compat-catalog-list")
                .fetchSemanticsNode().boundsInRoot
            val loadingNode = rule.onNodeWithTag("compat-catalog-blocking-loading")
                .assertIsDisplayed()
            val loadingBounds = loadingNode.fetchSemanticsNode().boundsInRoot
            assertTrue(abs(loadingBounds.center.x - catalogBounds.center.x) <= 2f)
            assertTrue(abs(loadingBounds.center.y - catalogBounds.center.y) <= 2f)
            assertEquals(
                "デフォルト",
                loadingNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            )

            rule.mainClock.advanceTimeBy(350L)
            rule.waitForIdle()
            rule.onAllNodesWithTag("compat-catalog-blocking-loading").assertCountEquals(0)
        } finally {
            rule.mainClock.autoAdvance = true
        }

        assertEquals(
            "1",
            runBlocking { store.loadPreference("compat.catalog.catalogViewMode") }
        )
        pressBack()
        rule.onNodeWithText(otherBoardUrl).performClick()
        rule.onNodeWithTag("compat-catalog-list").assertIsDisplayed()
    }

    @Test
    fun catalogDisplaySwitchUsesConfiguredAnimatedFutabaLoadingIcon() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designLoading", "アイコン")
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(
                        CatalogItem(
                            id = "69",
                            threadUrl = "${boardUrl}res/69.htm",
                            title = "ICON-LOADING-69",
                            thumbnailUrl = null,
                            fullImageUrl = null,
                            replyCount = 1
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onAllNodesWithContentDescription("その他")[1].performClick()
        rule.onNodeWithText("表示の切り替え").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        try {
            rule.onNodeWithText("表示の切り替え").performClick()
            rule.mainClock.advanceTimeByFrame()
            val loadingNode = rule.onNodeWithTag("compat-catalog-blocking-loading")
                .assertIsDisplayed()
            assertEquals(
                "アイコン",
                loadingNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            )
            rule.mainClock.advanceTimeBy(180L)
            rule.waitForIdle()
            loadingNode.assertIsDisplayed()
            val artworkNode = rule.onNodeWithTag("compat-loading-artwork")
            fun loadingRotation(): Any? = artworkNode.fetchSemanticsNode().config
                .first { it.key.name == "CompatLoadingRotation" }.value
            val firstRotation = loadingRotation()

            rule.mainClock.advanceTimeBy(80L)
            rule.waitForIdle()
            val secondRotation = loadingRotation()
            assertTrue(
                "The futaba loading artwork must rotate ($firstRotation -> $secondRotation)",
                firstRotation != secondRotation
            )
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun threadInitialDefaultLoadingArtworkHasVisibleMotionWhileRequestIsBlocked() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/7654321.htm"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            // In both reference APKs the black theme uses a white progress
            // ring. A toolbar-black ring would animate but remain invisible.
            store.savePreference("compat.design.designTheme", "ブラック")
            store.savePreference("compat.design.designLoading", "デフォルト")
        }
        val requestStarted = AtomicBoolean(false)
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
                requestStarted.set(true)
                awaitCancellation()
            }
        }
        // Pause the clock before the indicator enters composition. An already
        // running infinite transition is not re-registered when autoAdvance is
        // toggled after the fact, which would leave its test phase at zero.
        rule.mainClock.autoAdvance = false
        try {
            rule.setContent {
                CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                    MaterialTheme {
                        CompatibilityApp(
                            store = store,
                            repository = blockedRepository,
                            initialThreadDeepLink = threadUrl,
                            onExitApplication = {}
                        )
                    }
                }
            }
            // CompatibilityApp first collects its persisted board/preferences
            // flows and then resolves the deep link. Drive those startup frames
            // explicitly while automatic advancement is paused.
            rule.mainClock.advanceTimeBy(5_000L)

            rule.waitUntil(5_000) {
                if (!requestStarted.get()) rule.mainClock.advanceTimeByFrame()
                requestStarted.get()
            }
            val loadingNode = rule.onNodeWithTag("compat-thread-initial-loading")
                .assertIsDisplayed()
            assertEquals(
                "デフォルト",
                loadingNode.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            )

            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("compat-loading-artwork").assertIsDisplayed()
            val firstFrame = loadingNode.captureToImage().toPixelMap()
            val frameWidth = firstFrame.width
            val frameHeight = firstFrame.height
            // PixelMap can be backed by the reusable capture buffer on Android.
            // Materialize the first frame before requesting the second one.
            val firstPixels = IntArray(frameWidth * frameHeight) { index ->
                firstFrame[index % frameWidth, index / frameWidth].toArgb()
            }
            val visibleArtworkPixels = firstPixels.count { pixel ->
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                red > 220 && green > 220 && blue > 220
            }
            assertTrue(
                "The reference black theme must paint a visible white progress ring",
                visibleArtworkPixels > 20
            )
            val artworkNode = rule.onNodeWithTag("compat-loading-artwork")
            fun loadingRotation(): Any? = artworkNode.fetchSemanticsNode().config
                .first { it.key.name == "CompatLoadingRotation" }.value
            val firstRotation = loadingRotation()
            rule.mainClock.advanceTimeBy(100L)
            rule.waitForIdle()
            val secondFrame = loadingNode.captureToImage().toPixelMap()
            val secondRotation = loadingRotation()
            var changedPixels = 0
            for (y in 0 until minOf(frameHeight, secondFrame.height)) {
                for (x in 0 until minOf(frameWidth, secondFrame.width)) {
                    if (
                        firstPixels[y * frameWidth + x] != secondFrame[x, y].toArgb()
                    ) changedPixels += 1
                }
            }
            val comparedPixels = minOf(frameWidth, secondFrame.width) *
                minOf(frameHeight, secondFrame.height)
            assertTrue(
                "The thread loading animation clock must advance " +
                    "($firstRotation -> $secondRotation)",
                firstRotation != secondRotation
            )
            assertTrue(
                "The default thread loading artwork must visibly rotate " +
                    "($changedPixels/$comparedPixels pixels changed)",
                changedPixels > comparedPixels / 25
            )
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun webpAndWebmThreadColdLoadRevealsPostsWithoutManualRefresh() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/7654322.htm"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val requestCount = AtomicInteger(0)
        val mediaRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
                requestCount.incrementAndGet()
                return ThreadPage(
                    threadId = "7654322",
                    boardTitle = "二次元裏＠ふたば",
                    expiresAtLabel = null,
                    deletedNotice = null,
                    posts = listOf(
                        Post(
                            id = "7654322",
                            order = 0,
                            author = null,
                            subject = null,
                            timestamp = "08/27 13:00",
                            messageHtml = "WEBPの添付",
                            imageUrl = "https://may.2chan.net/b/src/1787802957098.webp",
                            thumbnailUrl = "https://may.2chan.net/b/thumb/1787802957098s.webp"
                        ),
                        Post(
                            id = "7654323",
                            order = 1,
                            author = null,
                            subject = null,
                            timestamp = "08/27 13:01",
                            messageHtml = "WEBMの添付",
                            imageUrl = "https://may.2chan.net/b/src/1787754264981.webm",
                            thumbnailUrl = "https://may.2chan.net/b/thumb/1787754264981s.jpg"
                        )
                    )
                )
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = mediaRepository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        listOf("7654322", "7654323").forEach { postNo ->
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("compat-thread-post-$postNo", useUnmergedTree = true)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
        }
        rule.onAllNodesWithTag("compat-thread-initial-loading").assertCountEquals(0)
        assertEquals(1, requestCount.get())
    }

    @Test
    fun blackThreadRendersOwnPostSelfQuoteUrlAndQuoteWithReferenceColors() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/777.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTheme", "black")
            store.savePreference("compat.ownpost.$tabKey.100", "1")
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "777",
                    title = "ブラック配色",
                    replyCount = 2,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "100",
                            author = "としあき",
                            timestamp = "08/25 12:00",
                            messageHtml = "https://example.com/reference"
                        ),
                        CompatPostSnapshot(
                            position = 1,
                            postNo = "101",
                            timestamp = "08/25 12:01",
                            messageHtml = ">>100<br>自分宛の返信"
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        listOf("compat-thread-post-100", "compat-thread-post-101").forEach { tag ->
            rule.waitUntil(10_000) {
                runCatching {
                    rule.onNodeWithTag(tag).assertIsDisplayed()
                }.isSuccess
            }
        }

        fun pixelCountNear(tag: String, expected: Color, tolerance: Int = 24): Int {
            val pixels = rule.onNodeWithTag(tag).assertIsDisplayed().captureToImage().toPixelMap()
            val target = expected.toArgb()
            val tr = target shr 16 and 0xFF
            val tg = target shr 8 and 0xFF
            val tb = target and 0xFF
            var count = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val actual = pixels[x, y].toArgb()
                    val ar = actual shr 16 and 0xFF
                    val ag = actual shr 8 and 0xFF
                    val ab = actual and 0xFF
                    if (
                        abs(ar - tr) <= tolerance &&
                        abs(ag - tg) <= tolerance &&
                        abs(ab - tb) <= tolerance
                    ) count += 1
                }
            }
            return count
        }

        assertTrue(pixelCountNear("compat-thread-post-100", Color(0xFF008CE6)) > 2)
        assertTrue(pixelCountNear("compat-thread-post-100", Color(0xFF009688)) > 2)
        assertTrue(pixelCountNear("compat-thread-post-101", Color(0xFFF48FB1)) > 2)
        assertTrue(pixelCountNear("compat-thread-post-101", Color(0xFF789922)) > 2)
    }

    @Test
    fun postSendUsesNonCancelableThemeLoadingDialogWhileRequestIsBlocked() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTheme", "ブラック")
            store.savePreference("compat.design.designLoading", "デフォルト")
            store.savePreference("compat.control.controlPostConfirm", "OFF")
        }
        val requestStarted = AtomicBoolean(false)
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun createThread(
                board: String,
                name: String,
                email: String,
                subject: String,
                comment: String,
                password: String,
                imageFile: ByteArray?,
                imageFileName: String?,
                textOnly: Boolean
            ): String? {
                requestStarted.set(true)
                awaitCancellation()
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = blockedRepository,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("スレ立て").assertIsDisplayed().performClick()
        rule.onNodeWithText("コメント").performTextInput("送信待機表示の確認")
        rule.onNodeWithText("削除キー").performTextInput("1234")
        closeSoftKeyboard() // Never turn a slow IME transition into app Back.
        assertBottomBarClearsSystemNavigation("compat-post-bottom-bar")
        rule.onNodeWithContentDescription("送信する").performClick()

        rule.waitUntil(5_000) { requestStarted.get() }
        rule.onNodeWithTag("compat-post-waiting-dialog").assertIsDisplayed()
        val waitingArtwork = rule.onNodeWithTag("compat-loading-artwork", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val expectedArtworkSize = with(rule.density) { 50.dp.toPx() }
        assertTrue("post waiting artwork must retain the reference 50dp width", waitingArtwork.width >= expectedArtworkSize - 1f)
        assertTrue("post waiting artwork must retain the reference 50dp height", waitingArtwork.height >= expectedArtworkSize - 1f)
        pressBack()
        rule.onNodeWithTag("compat-post-waiting-dialog").assertIsDisplayed()
        rule.onNodeWithText("コメント").assertIsDisplayed()
    }

    @Test
    fun postOverflowUsesReferenceBottomPopupInsteadOfCenteredDialog() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveToolbar(
                CompatToolbarSurface.POST,
                compatToolbarMaster(CompatToolbarSurface.POST).mapIndexed { index, item ->
                    CompatToolbarItem(
                        key = item.key,
                        position = index,
                        active = item.key != "network_info"
                    )
                }
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = FakeBoardRepository(),
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        closeSoftKeyboard()
        val overflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        val bottomOverflowIndex = overflowNodes.indices.maxBy { index ->
            overflowNodes[index].boundsInRoot.top
        }
        rule.onAllNodesWithContentDescription("その他")[bottomOverflowIndex].performClick()

        val popup = rule.onNodeWithTag("compat-post-toolbar-overflow-popup")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val toolbar = rule.onNodeWithTag("compat-post-bottom-bar")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("post overflow must end above its toolbar", popup.bottom <= toolbar.top + 2f)
        assertTrue(
            "post overflow must remain a compact text menu",
            popup.height <= toolbar.height * 1.5f
        )
        rule.onNodeWithText("回線情報").assertIsDisplayed()
        rule.onAllNodesWithText("閉じる").assertCountEquals(0)
    }

    @Test
    fun postScreenAppliesAndRestoresReferenceImeResizePolicy() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val originalMode = rule.activity.window.attributes.softInputMode
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = FakeBoardRepository(),
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithText("コメント").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runCatching {
                rule.onNodeWithTag("compat-post-comment-field").assertIsFocused()
                true
            }.getOrDefault(false)
        }
        rule.onNodeWithTag("compat-post-comment-field").assertIsFocused()
        val hasEnabledIme = rule.activity
            .getSystemService(InputMethodManager::class.java)
            .enabledInputMethodList
            .isNotEmpty()
        val softwareImeIsAllowed =
            rule.activity.resources.configuration.keyboard == Configuration.KEYBOARD_NOKEYS ||
                Settings.Secure.getInt(
                    rule.activity.contentResolver,
                    "show_ime_with_hard_keyboard",
                    0
                ) == 1
        if (hasEnabledIme && softwareImeIsAllowed) {
            rule.waitUntil(5_000) {
                ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            }
        }
        rule.waitForIdle()
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            rule.activity.window.attributes.softInputMode and
                WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )

        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithText("コメント").assertIsNotDisplayed()
        rule.waitUntil(5_000) {
            rule.activity.window.attributes.softInputMode == originalMode
        }
        assertEquals(originalMode, rule.activity.window.attributes.softInputMode)
    }

    @Test
    fun postValidationMessagesMatchBothReferenceApks() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = FakeBoardRepository(),
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.onNodeWithText("投稿の確認").assertIsDisplayed()
        rule.onNodeWithText("送信する").performClick()
        rule.onNodeWithText("コメントが空白です").assertIsDisplayed()
        rule.onAllNodesWithText("本文または添付ファイルを入力してください").assertCountEquals(0)

        rule.onNodeWithText("コメント").performTextInput("本文")
        closeSoftKeyboard()
        rule.onNodeWithContentDescription("送信する").performClick()
        rule.onNodeWithText("投稿の確認").assertIsDisplayed()
        rule.onNodeWithText("送信する").performClick()
        rule.onNodeWithText("削除キーを入力して下さい").assertIsDisplayed()
        rule.onAllNodesWithText("削除キーを入力してください").assertCountEquals(0)

        rule.onNodeWithContentDescription("内容の破棄").performClick()
        rule.onNodeWithText("投稿内容の破棄").assertIsDisplayed()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("破棄する").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").assertIsDisplayed().performClick()
        rule.onNodeWithText("コメント").assertIsDisplayed()
    }

    @Test
    fun postAttachmentLongPressShowsTheReferenceRememberedPickerDialog() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = FakeBoardRepository(),
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("スレ立て").performClick()
        rule.onNodeWithContentDescription("添付画像").performTouchInput { longClick() }
        onView(withText("ファイル選択アプリを選択")).check(matches(isDisplayed()))
        pressBack()
        rule.onNodeWithText("コメント").assertIsDisplayed()
    }

    @Test
    fun postDrawingDialogsBrushesAndHelpMatchBothReferenceApks() {
        val returned = AtomicInteger(0)
        val savedDrawing = AtomicReference<ImageData?>(null)
        rule.setContent {
            MaterialTheme {
                CompatPostDrawingScreen(
                    onSaved = { savedDrawing.set(it) },
                    onBack = { returned.incrementAndGet() },
                    forceLandscape = false
                )
            }
        }

        rule.onNodeWithText("手書き").assertIsDisplayed()
        rule.onNodeWithContentDescription("パレット").performClick()
        rule.onNodeWithContentDescription("主筆").assertIsDisplayed()
        rule.onNodeWithContentDescription("副筆").assertIsDisplayed().performClick()
        assertTextPresent("24")
        assertTextPresent("240")
        assertTextPresent("224")
        assertTextPresent("214")
        rule.onNodeWithContentDescription("色見本").performClick()
        rule.onAllNodesWithTag("compat-drawing-preset").assertCountEquals(12)
        // Selecting a preset is the reference close path and avoids asking
        // Espresso to choose between two nested Compose Dialog windows.
        rule.onAllNodesWithTag("compat-drawing-preset")[0].performClick()
        rule.onNodeWithContentDescription("リセット").assertIsDisplayed().performClick()
        assertTextPresent("6")
        rule.onAllNodesWithText("foreground").assertCountEquals(0)
        rule.onAllNodesWithText("background").assertCountEquals(0)
        rule.onAllNodesWithText("適用").assertCountEquals(0)
        pressBack()

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("クリアー").performClick()
        rule.onNodeWithText("最初の状態に戻します\n本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("添付画像として保存します\n本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithTag("compat-drawing-canvas").performTouchInput {
            swipe(
                start = Offset(visibleSize.width * 0.25f, visibleSize.height * 0.5f),
                end = Offset(visibleSize.width * 0.75f, visibleSize.height * 0.5f),
                durationMillis = 300
            )
        }
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存する").performClick()
        rule.onNodeWithText("添付画像として保存します\n本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("保存する").performClick()
        rule.waitUntil(5_000) { savedDrawing.get() != null }
        val drawing = checkNotNull(savedDrawing.get())
        assertTrue(drawing.fileName.matches(Regex("drawing_\\d{8}_\\d{6}\\.png")))
        val bitmap = checkNotNull(BitmapFactory.decodeByteArray(drawing.bytes, 0, drawing.bytes.size))
        try {
            assertEquals(344, bitmap.width)
            assertEquals(135, bitmap.height)
            assertEquals(0xFFF0E0D6.toInt(), bitmap.getPixel(0, 0))
        } finally {
            bitmap.recycle()
        }

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("ヘルプ").performClick()
        rule.onNodeWithText("ヘルプ").assertIsDisplayed()
        pressBack()
        rule.onNodeWithText("手書き").assertIsDisplayed()
        rule.onNodeWithContentDescription("元に戻す").assertIsEnabled()

        pressBack()
        rule.onNodeWithText("画像が保存されていません\n本当によろしいですか？").assertIsDisplayed()
        assertEquals(0, returned.get())
        rule.onNodeWithText("送信画面に戻る").performClick()
        assertEquals(1, returned.get())
    }

    @Test
    fun catalogLeftEdgeSwipeOpensDrawerAfterShortTravel() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val items = (1..10).map { number ->
            CatalogItem(
                id = number.toString(),
                threadUrl = "${boardUrl}res/$number.htm",
                title = "EDGE-$number",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = number
            )
        }
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = items
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-catalog-grid").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(1f, y))
            // The device density is 1.875, so this is just over the 48dp
            // compatibility trigger while still inside the first grid column.
            moveTo(Offset(100f, y), delayMillis = 250)
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithText("履歴").assertIsDisplayed()
    }

    @Test
    fun catalogScrollPositionSurvivesOpeningAndClosingThread() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/80.htm"
        val tabKey = compatTabKey(threadUrl)
        val items = (1..100).map { number ->
            CatalogItem(
                id = number.toString(),
                threadUrl = "${boardUrl}res/$number.htm",
                title = "CACHE-$number",
                thumbnailUrl = null,
                fullImageUrl = null,
                replyCount = number
            )
        }
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 10L,
                    fetchedAtEpochMillis = 1_000L,
                    items = items
                )
            )
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "80",
                    title = "CACHE-80",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "80",
                            timestamp = "08/15 12:00",
                            messageHtml = "catalog-scroll-target"
                        )
                    )
                )
            )
            store.selectTab(null)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(10_000) {
            // The grid container is composed before the persisted catalog
            // snapshot has been read.  Waiting for the first cell proves the
            // lazy item provider has received the complete snapshot, so an
            // indexed scroll cannot race a temporary [0, 0) item range.
            rule.onAllNodesWithTag("compat-catalog-item-1", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-catalog-grid").performScrollToIndex(80)
        rule.onNodeWithContentDescription("CACHE-80").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("catalog-scroll-target", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        pressBack()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-catalog-grid").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("CACHE-80").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("CACHE-80").assertIsDisplayed()
    }

    @Test
    fun closingOnlyCatalogThreadReturnsToTheLaunchingCatalog() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/90.htm"
        val tabKey = compatTabKey(threadUrl)
        val item = CatalogItem(
            id = "90",
            threadUrl = threadUrl,
            title = "CLOSE-LAST-90",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 1
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "90",
                    title = item.title.orEmpty(),
                    replyCount = 1,
                    checkedReplyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "90",
                            timestamp = "08/19 12:00",
                            messageHtml = "CLOSE-LAST-BODY"
                        )
                    )
                )
            )
            store.selectTab(null)
            store.saveToolbar(
                CompatToolbarSurface.THREAD,
                compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, toolbarItem ->
                    CompatToolbarItem(toolbarItem.key, index, toolbarItem.key == "close")
                }
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("CLOSE-LAST-90")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("CLOSE-LAST-90").assertIsDisplayed().performClick()
        assertTextPresent("CLOSE-LAST-BODY")
        rule.onNodeWithContentDescription("スレを閉じる").performClick()

        rule.waitUntil(5_000) { runBlocking { store.tabs.first().isEmpty() } }
        waitForTagDisplayed("compat-catalog-grid")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("CLOSE-LAST-90")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("CLOSE-LAST-90").assertIsDisplayed()
        rule.onAllNodesWithText("板が登録されていません。右上のメニューから板を追加してください。")
            .assertCountEquals(0)
    }

    @Test
    fun reopeningCatalogThreadDoesNotDuplicateItsReplyCount() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/95.htm"
        val tabKey = compatTabKey(threadUrl)
        val item = CatalogItem(
            id = "95",
            threadUrl = threadUrl,
            title = "REPLY-COUNT-95",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 5
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "95",
                    title = item.title.orEmpty(),
                    replyCount = 5,
                    checkedReplyCount = 5,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L,
                    snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 1L,
                    fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "95",
                            timestamp = "08/19 12:00",
                            messageHtml = "REPLY-COUNT-BODY"
                        )
                    )
                )
            )
            store.selectTab(null)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        repeat(2) {
            waitForContentDescriptionPresent("REPLY-COUNT-95")
            rule.onNodeWithContentDescription("REPLY-COUNT-95").assertIsDisplayed().performClick()
            assertTextPresent("REPLY-COUNT-BODY")
            pressBack()
            waitForTagDisplayed("compat-catalog-grid")
            rule.onAllNodesWithText("+5").assertCountEquals(0)
        }
        val persisted = runBlocking { store.tabs.first().single() }
        assertEquals(5, persisted.replyCount)
        assertEquals(5, persisted.checkedReplyCount)
    }

    @Test
    fun catalogRefreshShowsReplyIncreaseFromThePreviousGeneration() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val previousItem = CatalogItem(
            id = "96",
            threadUrl = "${boardUrl}res/96.htm",
            title = "REPLY-DELTA-96",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 5
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(previousItem)
                )
            )
            store.savePreference(
                "compat.catalog.lastFetchThreadCount.$boardKey.${CompatCatalogSort.CATALOG.name}",
                "300"
            )
        }
        val requestCount = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                requestCount.incrementAndGet()
                return listOf(previousItem.copy(replyCount = 15))
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        rule.onNodeWithContentDescription("REPLY-DELTA-96").assertIsDisplayed()
        rule.onAllNodesWithText("+10").assertCountEquals(0)

        rule.onNodeWithContentDescription("リロード").performClick()
        rule.waitUntil(5_000) { requestCount.get() == 1 }
        rule.onNodeWithText("15").assertIsDisplayed()
        rule.onNodeWithText("+10").assertIsDisplayed()
    }

    @Test
    fun catalogTopPullGestureRunsExactlyOneAdditionalRefresh() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val requestCount = AtomicInteger(0)
        val countingRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                requestCount.incrementAndGet()
                return (1..100).map { number ->
                    CatalogItem(
                        id = number.toString(),
                        threadUrl = "${boardUrl}res/$number.htm",
                        title = "PULL-$number",
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = number
                    )
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = countingRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        rule.waitUntil(5_000) { requestCount.get() == 1 }
        rule.onNodeWithTag("compat-pull-refresh").performTouchInput { swipeDown() }
        rule.waitUntil(5_000) { requestCount.get() == 2 }
        assertEquals(2, requestCount.get())
    }

    @Test
    fun catalogToolbarRefreshKeepsPositionWhenReferenceSettingIsOff() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val requestCount = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                requestCount.incrementAndGet()
                return (1..100).map { number ->
                    CatalogItem(
                        id = number.toString(),
                        threadUrl = "https://may.2chan.net/b/res/$number.htm",
                        title = "RELOAD-$number",
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = number
                    )
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) { requestCount.get() == 1 }
        rule.onNodeWithTag("compat-catalog-grid").performScrollToIndex(80)
        rule.onNodeWithContentDescription("RELOAD-80").assertIsDisplayed()
        rule.onNodeWithContentDescription("リロード").performClick()
        rule.waitUntil(5_000) { requestCount.get() == 2 }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("RELOAD-80").assertIsDisplayed()
    }

    @Test
    fun catalogToolbarRefreshReturnsToFirstRowWhenReferenceSettingIsOn() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.catalog.catalogReloadScrollTop", "ON")
        }
        val requestCount = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                requestCount.incrementAndGet()
                return (1..100).map { number ->
                    CatalogItem(
                        id = number.toString(),
                        threadUrl = "$boardUrl/res/$number.htm",
                        title = "TOP-$number",
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = number
                    )
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }
        rule.onNodeWithText("mayb").performClick()
        rule.waitUntil(5_000) { requestCount.get() == 1 }
        rule.onNodeWithTag("compat-catalog-grid").performScrollToIndex(80)
        rule.onNodeWithContentDescription("TOP-80").assertIsDisplayed()
        rule.onNodeWithContentDescription("リロード").performClick()
        rule.waitUntil(5_000) { requestCount.get() == 2 }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("TOP-1").assertIsDisplayed()
    }

    @Test
    fun catalogThreadSizeChangeForcesARequestWithTheNewLegacySettings() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.catalog.catalogThreadSize", "200")
        }
        val requested = CopyOnWriteArrayList<CatalogFetchSettings>()
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                requested += settings
                return (1..200).map { number ->
                    CatalogItem(
                        id = number.toString(),
                        threadUrl = "$boardUrl/res/$number.htm",
                        title = "SIZE-$number",
                        thumbnailUrl = null,
                        fullImageUrl = null,
                        replyCount = number
                    )
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        rule.waitUntil(5_000) {
            requested.any { it.columns == 8 && it.rows == 25 }
        }
        runBlocking { store.savePreference("compat.catalog.catalogThreadSize", "800") }
        rule.waitUntil(5_000) {
            requested.any { it.columns == 32 && it.rows == 25 }
        }
        assertTrue(requested.any { it.columns == 32 && it.rows == 25 })
    }

    @Test
    fun threadTopAndBottomPullGesturesEachRunExactlyOneRefresh() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/123.htm"
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = (1..60).map { number ->
                Post(
                    id = number.toString(),
                    order = number - 1,
                    author = "としあき",
                    subject = null,
                    timestamp = "08/06 12:00",
                    messageHtml = "THREAD-PULL-$number",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            }
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val requestCount = AtomicInteger(0)
        var refreshGate: CompletableDeferred<Unit>? = null
        val countingRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage {
                val requestNumber = requestCount.incrementAndGet()
                refreshGate?.await()
                return if (requestNumber >= 3) {
                    page.copy(
                        posts = page.posts + Post(
                            id = "61",
                            order = 60,
                            author = "としあき",
                            subject = null,
                            timestamp = "08/06 12:01",
                            messageHtml = "THREAD-PULL-61",
                            imageUrl = null,
                            thumbnailUrl = null
                        )
                    )
                } else {
                    page
                }
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = countingRepository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(5_000) { requestCount.get() == 1 }
        rule.waitUntil(5_000) {
            runBlocking { store.loadThreadSnapshot(compatTabKey(threadUrl)) != null }
        }
        val initialRevision = runBlocking {
            store.loadThreadSnapshot(compatTabKey(threadUrl))!!.revision
        }
        val overflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[overflowNodes.lastIndex].performClick()
        val bottomPopup = rule.onNodeWithTag("compat-bottom-popup").fetchSemanticsNode().boundsInRoot
        val bottomToolbar = rule.onNodeWithContentDescription("リロード").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "bottom toolbar popup must end above the toolbar",
            bottomPopup.bottom <= bottomToolbar.top + 2f
        )
        rule.onNodeWithText("ページ最上部へ").performClick()
        refreshGate = CompletableDeferred()
        rule.onNodeWithTag("compat-thread-pull-refresh").performTouchInput { swipeDown() }
        rule.waitUntil(5_000) { requestCount.get() == 2 }
        rule.onNodeWithText("読み込み中…").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-pull-refresh-indicator").assertIsDisplayed()
        refreshGate.complete(Unit)
        refreshGate = null
        rule.waitUntil(5_000) {
            runBlocking {
                (store.loadThreadSnapshot(compatTabKey(threadUrl))?.revision ?: 0L) > initialRevision
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("新着なし").assertIsDisplayed()
        rule.onNodeWithText("画面を引っ張って…").assertDoesNotExist()
        rule.onNodeWithText("指を離して更新…").assertDoesNotExist()
        rule.onNodeWithText("読み込み中…").assertDoesNotExist()
        rule.onAllNodesWithTag("compat-thread-pull-refresh-indicator").assertCountEquals(0)
        val topPullRevision = runBlocking {
            store.loadThreadSnapshot(compatTabKey(threadUrl))!!.revision
        }
        rule.waitForIdle()

        rule.onNodeWithTag("compat-thread-list").performScrollToIndex(59)
        refreshGate = CompletableDeferred()
        rule.onNodeWithTag("compat-thread-pull-refresh").performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { requestCount.get() == 3 }
        rule.onNodeWithText("読み込み中…").assertIsDisplayed()
        refreshGate.complete(Unit)
        refreshGate = null
        rule.waitUntil(5_000) {
            runBlocking {
                (store.loadThreadSnapshot(compatTabKey(threadUrl))?.revision ?: 0L) > topPullRevision
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("compat-thread-list").performScrollToIndex(page.posts.size)
        rule.waitForIdle()
        rule.onNodeWithText("新着レス 1件").assertIsDisplayed()
        val newReplyDividerBounds = rule.onNodeWithTag(
            "compat-new-replies-divider",
            useUnmergedTree = true
        )
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val threadListBounds = rule.onNodeWithTag("compat-thread-list")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "new-reply divider must span the thread content width",
            newReplyDividerBounds.width >= threadListBounds.width - 2f
        )
        rule.onNodeWithText("画面を引っ張って…").assertDoesNotExist()
        rule.onNodeWithText("指を離して更新…").assertDoesNotExist()
        rule.onNodeWithText("読み込み中…").assertDoesNotExist()
        rule.onAllNodesWithTag("compat-thread-pull-refresh-indicator").assertCountEquals(0)
        assertEquals(3, requestCount.get())

        runBlocking { store.savePreference("compat.thread.threadPullToRefresh", "OFF") }
        rule.waitForIdle()
        rule.onNodeWithTag("compat-thread-pull-refresh").performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(1_000L)
        rule.waitForIdle()
        assertEquals(3, requestCount.get())

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.waitForIdle()
    }

    @Test
    fun threadExpirationFooterStaysAboveTheOverlaidTabSelector() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/165.htm"
        val expiration = "消滅：21:10頃（あと1時間10分15秒）3543人"
        val page = ThreadPage(
            threadId = "165",
            boardTitle = "mayb",
            expiresAtLabel = expiration,
            deletedNotice = null,
            posts = (1..15).map { number ->
                Post(
                    id = number.toString(),
                    order = number - 1,
                    author = "としあき",
                    subject = null,
                    timestamp = "08/19 21:00",
                    messageHtml = "EXPIRATION-$number",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            }
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            store.savePreference("compat.design.designTabSelectorLocation", "ツールバーの上に重ねる")
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTextPresent("EXPIRATION-1")
        waitForTagDisplayed("compat-tab-selector")
        rule.onNodeWithTag("compat-thread-list").performScrollToIndex(page.posts.size)
        rule.waitForIdle()

        val footerBounds = rule.onNodeWithTag("compat-thread-footer")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(expiration).assertIsDisplayed()
        val selectorBounds = rule.onNodeWithTag("compat-tab-selector")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "expiration footer must end above the overlaid tab selector",
            footerBounds.bottom <= selectorBounds.top + 2f
        )
        assertTrue(
            "expiration footer should remain a compact single row",
            footerBounds.height < selectorBounds.height
        )
    }

    @Test
    fun quoteReferencePopupIsAnchoredAboveTheTappedResponse() {
        val boardUrl = "https://may.2chan.net/b/"
        val threadUrl = "${boardUrl}res/789.htm"
        val boardKey = compatBoardKey(boardUrl)
        val page = ThreadPage(
            threadId = "789",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(id = "1", order = 0, author = "としあき", subject = null, timestamp = "08/07 12:00", messageHtml = "元レス", imageUrl = null, thumbnailUrl = null),
                Post(id = "2", order = 1, author = "としあき", subject = null, timestamp = "08/07 12:01", messageHtml = "&gt;&gt;1", imageUrl = null, thumbnailUrl = null)
            )
        )
        runBlocking { store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0)) }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTextPresent(">>1")
        rule.onNodeWithText(">>1").assertIsDisplayed()
        val referenceBounds = rule.onNodeWithText(">>1").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithText(">>1").performClick()
        rule.onNodeWithTag("compat-quote-popup").assertIsDisplayed()
        val popupNode = rule.onNodeWithTag("compat-quote-popup").fetchSemanticsNode()
        val popupBounds = popupNode.boundsInWindow
        val threadRootWidth = rule.onNodeWithTag("compat-thread-pager").fetchSemanticsNode().boundsInRoot.width
        assertTrue("quote popup must be full-width", popupBounds.width >= threadRootWidth * 0.9f)
        assertTrue("quote popup should be anchored near/above the tapped response", popupBounds.top < referenceBounds.bottom)
    }

    @Test
    fun threadSearchCountsPostsWrapsAndConsumesImeFocusAndCollapseBackStages() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/456.htm"
        val page = ThreadPage(
            threadId = "456",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post("1", 0, "としあき", null, "08/06 12:00", messageHtml = "aaa", imageUrl = null, thumbnailUrl = null),
                Post("2", 1, "としあき", null, "08/06 12:01", messageHtml = "本文", imageUrl = null, thumbnailUrl = null, mail = "aa@example.test"),
                Post("3", 2, "としあき", null, "08/06 12:02", messageHtml = "AAA", imageUrl = null, thumbnailUrl = null)
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        val restorationTester = StateRestorationTester(rule)
        restorationTester.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("aaa")
        rule.onNodeWithContentDescription("レス検索").performClick()
        rule.onNodeWithTag("compat-thread-search-field").performTextInput("aa")
        rule.onNodeWithText("1/2件").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-search-field").performImeAction()
        rule.onNodeWithText("2/2件").assertIsDisplayed()
        rule.onNodeWithContentDescription("次の検索結果").performClick()
        rule.onNodeWithText("1/2件").assertIsDisplayed()
        rule.onNodeWithContentDescription("次の検索結果").performClick()
        rule.onNodeWithText("2/2件").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("compat-thread-search-field").assertIsDisplayed()
        rule.onNodeWithText("2/2件").assertIsDisplayed()

        pressBack()
        rule.onNodeWithTag("compat-thread-search-field").assertIsDisplayed()
        pressBack()
        rule.onNodeWithTag("compat-thread-search-field").assertIsDisplayed()
        pressBack()
        rule.onAllNodesWithTag("compat-thread-search-field").assertCountEquals(0)
        assertTextPresent("aaa")

        rule.onNodeWithContentDescription("ドロワー").performClick()
        rule.waitForIdle()
    }

    @Test
    fun selectorUpdateCheckUsesAllTabProbeInsteadOfReloadingCatalog() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/780.htm"
        val tabKey = compatTabKey(threadUrl)
        val item = CatalogItem(
            id = "780",
            threadUrl = threadUrl,
            title = "更新確認対象",
            thumbnailUrl = "https://may.2chan.net/b/thumb/780s.jpg",
            fullImageUrl = null,
            replyCount = 2
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            // AppControlSettingActivity persists the ListPreference entryValue,
            // not its Japanese summary. The runtime must consume that raw value.
            store.savePreference("compat.control.controlTabSelectorLongTap", "check")
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "780",
                    title = "更新確認対象",
                    replyCount = 1,
                    insertedAtEpochMillis = 780L,
                    contentUpdatedAtEpochMillis = Long.MAX_VALUE
                )
            )
        }
        val updateChecks = AtomicInteger(0)
        val catalogReloads = AtomicInteger(0)
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
                updateChecks.incrementAndGet()
                return listOf(item)
            }

            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> {
                catalogReloads.incrementAndGet()
                return listOf(item)
            }

            override suspend fun probeThreadGone(threadUrl: String): Boolean = false
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        waitForTagDisplayed("compat-tab-selector")
        rule.waitUntil(5_000) { catalogReloads.get() == 1 }
        rule.onNodeWithTag("compat-catalog-item-780").performTouchInput { longClick() }
        rule.onNodeWithTag("compat-catalog-context-menu").assertIsDisplayed()
        rule.onNodeWithText("NGスレッドに登録").assertIsDisplayed()
        rule.onNodeWithText("NG画像に登録").assertIsDisplayed()
        rule.onNodeWithText("タブに追加する").assertIsDisplayed()
        rule.onAllNodesWithText("抽出ワードに登録").assertCountEquals(0)
        rule.onAllNodesWithText("拒否スレッドに登録").assertCountEquals(0)
        rule.onAllNodesWithText("NG画像(pHash)に登録").assertCountEquals(0)
        rule.onNodeWithText("NG画像に登録").performClick()
        rule.onNodeWithText("NG画像に登録").assertIsDisplayed()
        rule.onNodeWithText("この板のみ").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("compat-catalog-item-780").performTouchInput { longClick() }
        rule.onNodeWithText("delを送信する").performClick()
        rule.onNodeWithText("削除依頼 更新確認対象").assertIsDisplayed()
        rule.onAllNodesWithText("del確認").assertCountEquals(0)
        rule.onAllNodesWithText("スレNo.780にdelを送信しますか？", substring = true).assertCountEquals(0)
        rule.onNodeWithText("キャンセル").performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("compat-selector-tab-$tabKey").performTouchInput {
            down(center)
        }
        rule.mainClock.advanceTimeBy(650L)
        rule.onNodeWithTag("compat-selector-tab-$tabKey").performTouchInput {
            moveTo(Offset(center.x, center.y + 1f), delayMillis = 20L)
            up()
        }
        rule.mainClock.autoAdvance = true

        rule.waitUntil(5_000) { updateChecks.get() == 1 }
        assertEquals(1, catalogReloads.get())
        rule.waitUntil(5_000) {
            runBlocking { store.tabs.first().single().replyCount == 2 }
        }
    }

    @Test
    fun selectorHorizontalSwipeScrollsOverflowWithoutClosingTabs() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val tabs = (1..10).map { index ->
            val no = (800 + index).toString()
            val url = "${boardUrl}res/$no.htm"
            CompatTab(
                key = compatTabKey(url),
                canonicalUrl = url,
                originalUrl = url,
                boardKey = boardKey,
                boardName = "mayb",
                threadNo = no,
                title = "検証タブ$index",
                replyCount = 1,
                checkedReplyCount = 0,
                insertedAtEpochMillis = index.toLong(),
                contentUpdatedAtEpochMillis = index.toLong(),
                snapshotRevision = index.toLong()
            )
        }
        fun snapshot(tab: CompatTab) = CompatThreadSnapshot(
            tabKey = tab.key,
            revision = tab.snapshotRevision,
            fetchedAtEpochMillis = tab.snapshotRevision,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = tab.threadNo,
                    timestamp = "08/06 12:00",
                    messageHtml = tab.title
                )
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            tabs.take(4).forEach { tab ->
                store.openTab(tab)
                store.saveThreadSnapshot(snapshot(tab))
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = tabs[3].canonicalUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent(tabs[3].title)
        val selector = rule.onNodeWithTag("compat-tab-selector").assertIsDisplayed()

        // A horizontal gesture with content that fits must be a no-op.
        selector.performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(4, runBlocking { store.tabs.first().size })
        rule.onAllNodesWithText("スレッドを閉じました").assertCountEquals(0)

        // Once the row overflows, the same gesture must scroll the LazyRow
        // instead of closing whichever tab received the initial down event.
        runBlocking {
            tabs.drop(4).forEach { tab ->
                store.openTab(tab)
                store.saveThreadSnapshot(snapshot(tab))
            }
        }
        rule.waitUntil(5_000) { runBlocking { store.tabs.first().size == tabs.size } }
        val scrollBefore = rule.onNodeWithTag("compat-tab-selector")
            .fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        rule.onNodeWithTag("compat-tab-selector").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val scrollAfter = rule.onNodeWithTag("compat-tab-selector")
            .fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()

        assertEquals(tabs.size, runBlocking { store.tabs.first().size })
        assertTrue(scrollAfter > scrollBefore)
        rule.onAllNodesWithText("スレッドを閉じました").assertCountEquals(0)
    }

    @Test
    fun selectorLongHoldUpwardDropClosesOnlyAfterTargetAnimation() {
        // MainActivity always opts into edge-to-edge. API 35+ enforces that
        // behavior automatically, while API 26 test activities do not, so
        // mirror the production window contract explicitly on every API.
        rule.activity.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val firstUrl = "${boardUrl}res/701.htm"
        val secondUrl = "${boardUrl}res/702.htm"
        val firstKey = compatTabKey(firstUrl)
        val secondKey = compatTabKey(secondUrl)
        fun tab(key: String, url: String, no: String, title: String, inserted: Long) = CompatTab(
            key = key,
            canonicalUrl = url,
            originalUrl = url,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = no,
            title = title,
            replyCount = 1,
            checkedReplyCount = 0,
            insertedAtEpochMillis = inserted,
            contentUpdatedAtEpochMillis = inserted,
            snapshotRevision = inserted
        )
        fun snapshot(key: String, revision: Long, text: String) = CompatThreadSnapshot(
            tabKey = key,
            revision = revision,
            fetchedAtEpochMillis = revision,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = revision.toString(),
                    timestamp = "08/06 12:00",
                    messageHtml = text
                )
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            store.openTab(tab(firstKey, firstUrl, "701", "最初のスレ", 701L))
            store.saveThreadSnapshot(snapshot(firstKey, 701L, "最初の本文"))
            store.openTab(tab(secondKey, secondUrl, "702", "現在のスレ", 702L))
            store.saveThreadSnapshot(snapshot(secondKey, 702L, "現在の本文"))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = secondUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("現在の本文")
        rule.onNodeWithTag("compat-tab-selector").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        val firstSelectorTab = rule.onNodeWithTag("compat-selector-tab-$firstKey")
        firstSelectorTab.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(650L)
        firstSelectorTab.performTouchInput {
            moveTo(Offset(center.x, center.y - 100f), delayMillis = 80L)
            up()
        }
        rule.mainClock.advanceTimeBy(800L)
        assertEquals(2, runBlocking { store.tabs.first().size })

        firstSelectorTab.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(650L)
        firstSelectorTab.performTouchInput {
            moveTo(Offset(center.x, center.y - 600f), delayMillis = 80L)
        }
        rule.onNodeWithTag("compat-selector-drag-shadow").assertIsDisplayed()
        val shadowBounds = rule.onNodeWithTag("compat-selector-drag-shadow")
            .fetchSemanticsNode().boundsInRoot
        val previewBounds = rule.onNodeWithTag("compat-selector-drag-preview")
            .fetchSemanticsNode().boundsInRoot
        val windowWidth = rule.activity.window.decorView.width.toFloat()
        val windowHeight = rule.activity.window.decorView.height.toFloat()
        val navigationInsetBottom = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?.toFloat()
            ?: 0f
        val appContentBottom = windowHeight - navigationInsetBottom
        assertTrue(
            "drag shadow must start at the window top so the toolbar is dimmed",
            shadowBounds.top <= 2f
        )
        assertTrue(
            "drag shadow must cover app content down to the system navigation boundary " +
                "(shadow=$shadowBounds, windowHeight=$windowHeight, navigationInset=$navigationInsetBottom)",
            shadowBounds.bottom >= appContentBottom - 2f
        )
        assertTrue(shadowBounds.width >= windowWidth - 2f)
        assertTrue(shadowBounds.width > previewBounds.width * 5f)
        assertTrue(previewBounds.left >= shadowBounds.left)
        assertTrue(previewBounds.right <= shadowBounds.right)
        assertTrue(previewBounds.top >= shadowBounds.top)
        assertTrue(previewBounds.bottom <= shadowBounds.bottom)
        firstSelectorTab.performTouchInput { up() }
        assertEquals(2, runBlocking { store.tabs.first().size })
        rule.mainClock.advanceTimeBy(699L)
        assertEquals(2, runBlocking { store.tabs.first().size })
        rule.mainClock.advanceTimeBy(2L)
        rule.mainClock.autoAdvance = true
        rule.waitUntil(5_000) { runBlocking { store.tabs.first().size == 1 } }
        assertEquals(secondKey, runBlocking { store.tabs.first().single().key })
        // The reference APK closes a single tab silently. The recoverable
        // close record remains internal and only multi-close reports a count.
        rule.onAllNodesWithText("スレッドを閉じました").assertCountEquals(0)
        rule.onAllNodesWithText("元に戻す").assertCountEquals(0)
    }

    @Test
    fun closeToolbarRemainsUsableForConsecutiveTabsWhileUndoToastIsVisible() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        fun tab(no: String, inserted: Long): CompatTab {
            val url = "${boardUrl}res/$no.htm"
            return CompatTab(
                key = compatTabKey(url),
                canonicalUrl = url,
                originalUrl = url,
                boardKey = boardKey,
                boardName = "mayb",
                threadNo = no,
                title = "スレ$no",
                replyCount = 1,
                insertedAtEpochMillis = inserted,
                contentUpdatedAtEpochMillis = inserted,
                snapshotRevision = inserted
            )
        }
        fun snapshot(tab: CompatTab) = CompatThreadSnapshot(
            tabKey = tab.key,
            revision = tab.insertedAtEpochMillis,
            fetchedAtEpochMillis = tab.insertedAtEpochMillis,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = tab.threadNo,
                    timestamp = "08/06 12:00",
                    messageHtml = "本文${tab.threadNo}"
                )
            )
        )
        val tabs = listOf(tab("741", 741L), tab("742", 742L), tab("743", 743L))
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.control.controlThreadCloseBack", "OFF")
            store.saveToolbar(
                CompatToolbarSurface.THREAD,
                compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                    CompatToolbarItem(item.key, index, item.key == "close")
                }
            )
            tabs.forEach { tab ->
                store.openTab(tab)
                store.saveThreadSnapshot(snapshot(tab))
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = tabs.last().originalUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("スレを閉じる").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("スレを閉じる").performClick()
        rule.waitUntil(5_000) { runBlocking { store.tabs.first().size == 2 } }
        rule.onAllNodesWithText("スレッドを閉じました").assertCountEquals(0)
        rule.onNodeWithContentDescription("スレを閉じる").performClick()
        rule.waitUntil(5_000) { runBlocking { store.tabs.first().size == 1 } }
        assertEquals(tabs.first().key, runBlocking { store.tabs.first().single().key })
    }

    @Test
    fun threadPagerSwipeSelectsAdjacentTabAndRestoresEachTabScrollState() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val firstUrl = "${boardUrl}res/711.htm"
        val secondUrl = "${boardUrl}res/712.htm"
        val firstKey = compatTabKey(firstUrl)
        val secondKey = compatTabKey(secondUrl)
        fun tab(key: String, url: String, no: String, title: String, inserted: Long) = CompatTab(
            key = key,
            canonicalUrl = url,
            originalUrl = url,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = no,
            title = title,
            replyCount = 20,
            insertedAtEpochMillis = inserted,
            contentUpdatedAtEpochMillis = inserted,
            snapshotRevision = inserted
        )
        fun snapshot(key: String, revision: Long, prefix: String) = CompatThreadSnapshot(
            tabKey = key,
            revision = revision,
            fetchedAtEpochMillis = revision,
            posts = (0 until 20).map { index ->
                CompatPostSnapshot(
                    position = index,
                    postNo = "${revision}-${index}",
                    timestamp = "08/06 12:${index.toString().padStart(2, '0')}",
                    messageHtml = "$prefix-$index"
                )
            }
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            store.savePreference("compat.design.designTabSelectorLocation", "ツールバーの上に重ねる")
            store.openTab(tab(firstKey, firstUrl, "711", "前のスレ", 711L))
            store.saveThreadSnapshot(snapshot(firstKey, 711L, "前の本文"))
            store.openTab(tab(secondKey, secondUrl, "712", "現在のスレ", 712L))
            store.saveThreadSnapshot(snapshot(secondKey, 712L, "現在の本文"))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = secondUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("現在の本文-0")
        val selectorBefore = rule.onNodeWithTag("compat-tab-selector")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val pager = rule.onNodeWithTag("compat-thread-pager")
        pager.performTouchInput {
            down(center)
            moveTo(Offset(center.x * 0.3f, center.y), delayMillis = 250L)
        }
        val selectorDuringSwipe = rule.onNodeWithTag("compat-tab-selector")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(selectorBefore, selectorDuringSwipe)
        pager.performTouchInput { up() }
        assertTextPresent("前の本文-0")
        rule.onNodeWithTag("compat-thread-list").performScrollToIndex(15)
        rule.onNodeWithText("前の本文-15").assertIsDisplayed()

        rule.onNodeWithTag("compat-thread-pager").performTouchInput { swipeRight() }
        assertTextPresent("現在の本文-0")
        rule.onNodeWithTag("compat-thread-pager").performTouchInput { swipeLeft() }
        assertTextPresent("前の本文-15")
    }

    @Test
    fun leftEdgeSwipeOpensDrawerInsteadOfSelectingPreviousTab() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val firstUrl = "${boardUrl}res/731.htm"
        val secondUrl = "${boardUrl}res/732.htm"
        fun tab(key: String, url: String, no: String, title: String, inserted: Long) = CompatTab(
            key = key,
            canonicalUrl = url,
            originalUrl = url,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = no,
            title = title,
            replyCount = 1,
            insertedAtEpochMillis = inserted,
            contentUpdatedAtEpochMillis = inserted,
            snapshotRevision = inserted
        )
        fun snapshot(key: String, revision: Long, text: String) = CompatThreadSnapshot(
            tabKey = key,
            revision = revision,
            fetchedAtEpochMillis = revision,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = revision.toString(),
                    timestamp = "08/06 12:00",
                    messageHtml = text
                )
            )
        )
        val firstKey = compatTabKey(firstUrl)
        val secondKey = compatTabKey(secondUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(tab(firstKey, firstUrl, "731", "前のスレ", 731L))
            store.saveThreadSnapshot(snapshot(firstKey, 731L, "前の本文"))
            store.openTab(tab(secondKey, secondUrl, "732", "現在のスレ", 732L))
            store.saveThreadSnapshot(snapshot(secondKey, 732L, "現在の本文"))
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = secondUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("現在の本文")
        rule.onNodeWithTag("compat-thread-pager").performTouchInput {
            val y = visibleSize.height / 2f
            down(Offset(1f, y))
            moveTo(Offset(visibleSize.width * 0.8f, y), delayMillis = 500)
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithText("閲覧中のスレッド").assertIsDisplayed()
        rule.onAllNodesWithText("前の本文").assertCountEquals(0)
    }

    @Test
    fun catalogAndThreadSelectorOpenStateRemainIndependentForTheCurrentSession() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/721.htm"
        val tabKey = compatTabKey(threadUrl)
        val item = CatalogItem(
            id = "721",
            threadUrl = threadUrl,
            title = "対象スレ",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 1
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "721",
                    title = "対象スレ",
                    replyCount = 1,
                    insertedAtEpochMillis = 721L,
                    contentUpdatedAtEpochMillis = 721L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 721L,
                    fetchedAtEpochMillis = 721L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "721",
                            timestamp = "08/06 12:00",
                            messageHtml = "対象本文"
                        )
                    )
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("対象スレ")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription("対象スレ").assertIsDisplayed()
        rule.onNodeWithContentDescription("タブ").performClick()
        rule.onNodeWithTag("compat-tab-selector").assertIsDisplayed()
        assertFalse(runBlocking { store.workspace.first().catalogSelectorOpen })
        rule.onNodeWithContentDescription("対象スレ").performClick()
        assertTextPresent("対象本文")
        rule.onNodeWithTag("compat-tab-selector").assertDoesNotExist()
        assertFalse(runBlocking { store.workspace.first().threadSelectorOpen })
        rule.onNodeWithContentDescription("タブ一覧").performClick()
        rule.onNodeWithTag("compat-tab-selector").assertIsDisplayed()
        assertFalse(runBlocking { store.workspace.first().threadSelectorOpen })

        pressBack()
        rule.onNodeWithContentDescription("対象スレ").assertIsDisplayed()
        rule.onNodeWithTag("compat-tab-selector").assertIsDisplayed()
        assertFalse(runBlocking { store.workspace.first().catalogSelectorOpen })
        assertFalse(runBlocking { store.workspace.first().threadSelectorOpen })
    }

    @Test
    fun selectorStartupOffOverridesPreviouslyPersistedOpenStateOnCatalogAndThread() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/722.htm"
        val tabKey = compatTabKey(threadUrl)
        val item = CatalogItem(
            id = "722",
            threadUrl = threadUrl,
            title = "初期非表示対象スレ",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 1
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "OFF")
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "722",
                    title = "初期非表示対象スレ",
                    replyCount = 1,
                    insertedAtEpochMillis = 722L,
                    contentUpdatedAtEpochMillis = 722L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 722L,
                    fetchedAtEpochMillis = 722L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0,
                            postNo = "722",
                            timestamp = "08/19 15:37",
                            messageHtml = "初期非表示本文"
                        )
                    )
                )
            )
            store.updateWorkspace(
                CompatWorkspaceRecord(
                    activeTabKey = tabKey,
                    catalogHostBoardKey = boardKey,
                    catalogSelectorOpen = true,
                    threadSelectorOpen = true
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        waitForContentDescriptionPresent("初期非表示対象スレ")
        rule.onNodeWithContentDescription("初期非表示対象スレ").assertIsDisplayed()
        rule.onNodeWithTag("compat-tab-selector").assertDoesNotExist()

        // Reproduces #68: an old settings restore left selector flags in the
        // workspace database. After a restart, the first Back from Catalog
        // must navigate immediately instead of consuming that invisible flag.
        pressBack()
        rule.onNodeWithText("ふたば").assertIsDisplayed()

        rule.onNodeWithText(boardUrl).performClick()
        waitForContentDescriptionPresent("初期非表示対象スレ")
        rule.onNodeWithContentDescription("初期非表示対象スレ").assertIsDisplayed()
        rule.onNodeWithContentDescription("初期非表示対象スレ").performClick()
        rule.onNodeWithText("初期非表示本文").assertIsDisplayed()
        rule.onNodeWithTag("compat-tab-selector").assertDoesNotExist()

        pressBack()
        waitForContentDescriptionPresent("初期非表示対象スレ")
        rule.onNodeWithContentDescription("初期非表示対象スレ").assertIsDisplayed()
    }

    @Test
    fun boardListLastItemCanScrollAboveSystemNavigationBar() {
        val boardUrls = (1..30).map { index -> "https://may.2chan.net/test$index/" }
        runBlocking {
            boardUrls.forEachIndexed { index, url ->
                store.upsertBoard(
                    CompatBoard(
                        key = compatBoardKey(url),
                        name = "board-$index",
                        canonicalUrl = url,
                        originalUrl = url,
                        sortOrder = index
                    )
                )
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithTag("compat-board-list").performScrollToIndex(boardUrls.lastIndex)
        rule.onNodeWithText("board-29").assertIsDisplayed()
        val listBottom = rule.onNodeWithTag("compat-board-list").fetchSemanticsNode().boundsInRoot.bottom
        val rowBottom = rule.onNodeWithText("board-29").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("last board must remain inside the navigation-safe list viewport", rowBottom <= listBottom)
    }

    @Test
    fun catalogBottomBarClearsCurrentSystemNavigationMode() {
        // Run this same assertion with the emulator's gestural and three-button
        // overlays. MainActivity is edge-to-edge on every supported API, so the
        // isolated Compose host must mirror that production window contract.
        rule.activity.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val item = CatalogItem(
            id = "760",
            threadUrl = "${boardUrl}res/760.htm",
            title = "ナビゲーション境界確認",
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 1
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey = boardKey,
                    sort = CompatCatalogSort.CATALOG,
                    revision = 1L,
                    fetchedAtEpochMillis = 1_000L,
                    items = listOf(item)
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText(boardUrl).performClick()
        rule.onNodeWithContentDescription("ナビゲーション境界確認").assertIsDisplayed()
        assertBottomBarClearsSystemNavigation("compat-main-bottom-bar")
        rule.onNodeWithContentDescription("リロード").assertIsDisplayed().performClick()
    }

    @Test
    fun currentTabSelectorBottomJumpUsesFilteredLastRow() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/731.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTabSelectorOpened", "ON")
            store.openTab(
                CompatTab(
                    key = tabKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "731",
                    title = "絞り込みスレ",
                    replyCount = 5,
                    insertedAtEpochMillis = 731L,
                    contentUpdatedAtEpochMillis = 731L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey,
                    revision = 731L,
                    fetchedAtEpochMillis = 731L,
                    posts = (0 until 5).map { index ->
                        CompatPostSnapshot(
                            position = index,
                            postNo = "73$index",
                            timestamp = "08/06 12:0$index",
                            messageHtml = "絞り込み本文-$index"
                        )
                    }
                )
            )
            store.upsertNgRule(
                CompatNgRule(
                    id = compatNgRuleId(CompatNgKind.THREAD_POST_NO, tabKey, "734"),
                    kind = CompatNgKind.THREAD_POST_NO,
                    scopeKey = tabKey,
                    normalizedValue = "734",
                    createdAtEpochMillis = 731L
                )
            )
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = null,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        assertTextPresent("絞り込み本文-0")
        rule.onNodeWithTag("compat-selector-tab-$tabKey").performClick()
        rule.onNodeWithText("絞り込み本文-3").assertIsDisplayed()
    }

    @Test
    fun catalogDroppedCommandShowsTrackedThreadAndKeepsCurrentCatalog() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        fun item(id: String, title: String) = CatalogItem(
            id = id,
            threadUrl = "${boardUrl}res/$id.htm",
            title = title,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = id.toInt()
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.savePreference("compat.design.designTheme", "ふたば")
            store.savePreference("compat.catalog.catalogFindThreadDeleted", "ON")
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey,
                    CompatCatalogSort.CATALOG,
                    1L,
                    1_000L,
                    listOf(item("1", "消えた対象"), item("2", "現在の対象"))
                ),
                trackDropped = true
            )
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    boardKey,
                    CompatCatalogSort.CATALOG,
                    2L,
                    2_000L,
                    listOf(item("2", "現在の対象"))
                ),
                trackDropped = true,
                requestedThreadCount = 2
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        rule.onNodeWithContentDescription("現在の対象").assertIsDisplayed()
        val undoPixels = rule.onNodeWithTag("compat-toolbar-icon-undo", useUnmergedTree = true)
            .assertIsDisplayed().captureToImage().toPixelMap()
        val droppedPixels = rule.onNodeWithTag("compat-toolbar-icon-dropped", useUnmergedTree = true)
            .assertIsDisplayed().captureToImage().toPixelMap()
        fun whitePixelSignature(pixels: androidx.compose.ui.graphics.PixelMap): Set<Int> = buildSet {
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    if (color.red > 0.9f && color.green > 0.9f && color.blue > 0.9f) {
                        add(y * pixels.width + x)
                    }
                }
            }
        }
        val undoSignature = whitePixelSignature(undoPixels)
        val droppedSignature = whitePixelSignature(droppedPixels)
        assertTrue("The 1.apk rollback arrow was not rendered", undoSignature.isNotEmpty())
        assertTrue("The 1.apk dropped-thread clock was not rendered", droppedSignature.isNotEmpty())
        assertTrue(
            "リロード前に戻す and 消えたスレ must use different 1.apk artwork",
            undoSignature != droppedSignature
        )
        rule.onNodeWithContentDescription("消えたスレ").performClick()
        rule.onNodeWithText("mayb / 消えたスレ").assertIsDisplayed()
        rule.onNodeWithText("消えた対象").assertIsDisplayed()
        rule.onNodeWithText("落ち").assertIsDisplayed()
        rule.onNodeWithText("1res").assertIsDisplayed()
        val row = rule.onNodeWithTag("compat-dropped-row-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val thumb = rule.onNodeWithTag("compat-dropped-thumb-1", useUnmergedTree = true)
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(thumb.width - with(rule.density) { 60.dp.toPx() }) <= 1f)
        assertTrue(abs(thumb.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
        assertTrue(abs(row.height - with(rule.density) { 70.dp.toPx() }) <= 1f)

        val placeholderPixels = rule.onNodeWithTag("compat-dropped-thumb-1", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        assertTrue(
            "1.apk uses a blank white no-thumbnail bitmap",
            (0 until placeholderPixels.height).all { y ->
                (0 until placeholderPixels.width).all { x ->
                    placeholderPixels[x, y].toArgb() == Color.White.toArgb()
                }
            }
        )
        val futabaText = Color(0xFF800000).toArgb()
        val titlePixels = rule.onNodeWithTag("compat-dropped-title-1", useUnmergedTree = true)
            .captureToImage().toPixelMap()
        assertTrue(
            "the dropped-thread title must use FutabaTheme's #800000 text",
            (0 until titlePixels.height).any { y ->
                (0 until titlePixels.width).any { x -> titlePixels[x, y].toArgb() == futabaText }
            }
        )

        rule.onNodeWithText("消えた対象").performTouchInput {
            down(center)
            advanceEventTime(800L)
            up()
        }
        rule.onNodeWithText("落ちスレを履歴から削除").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadDroppedCatalogItems(boardKey).isEmpty() } }
        rule.onNodeWithText("消えたスレはありません").assertIsDisplayed()

        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithContentDescription("現在の対象").assertIsDisplayed()
    }

    @Test
    fun catalogExtractReferenceScreenUsesDirectActivityFlowAndExactDialogs() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val ruleId = "reference-catalog-extract"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.upsertNgRule(
                CompatNgRule(
                    id = ruleId,
                    kind = CompatNgKind.CATALOG_EXTRACT,
                    scopeKey = "*",
                    normalizedValue = "ＴＥＳＴ",
                    createdAtEpochMillis = 1L
                )
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().size >= 2
        }
        val catalogOverflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[catalogOverflowNodes.lastIndex].performClick()
        rule.onNodeWithText("監視ワード").performClick()
        rule.onNodeWithText("スレッド監視 1個").assertIsDisplayed()
        rule.onAllNodesWithText("抽出").assertCountEquals(0)
        val extractRow = rule.onNodeWithTag("compat-catalog-extract-row-$ruleId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(extractRow.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
        rule.onNodeWithContentDescription("全ての板").assertIsDisplayed()
        rule.onAllNodesWithText("全ての板 ・ 1970/01/01").assertCountEquals(0)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("compat-catalog-extract-search", useUnmergedTree = true).performTextInput("test")
        rule.onNodeWithText("ＴＥＳＴ").assertIsDisplayed()
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithText("スレッド監視 1個").assertIsDisplayed()

        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithText("単語").assertIsDisplayed()
        rule.onNodeWithText("全ての板").assertIsDisplayed()
        rule.onNodeWithText("・大文字と小文字を区別しません", substring = true).assertIsDisplayed()
        rule.onNodeWithText("追加する").assertIsDisplayed()
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("単語を入力して下さい").assertIsDisplayed()
        val addWord = rule.onNodeWithTag("compat-catalog-extract-add-word", useUnmergedTree = true)
        addWord.performTextInput("12345678901")
        addWord.assert(hasTextExactly("1234567890"))
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithTag("compat-catalog-extract-add-word", useUnmergedTree = true)
            .performTextInput("追加犬")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("スレッド監視 2個").assertIsDisplayed()
        rule.onNodeWithText("追加犬").performClick()
        rule.onNodeWithTag("compat-catalog-extract-edit-word", useUnmergedTree = true)
            .performTextReplacement("更新鳥")
        rule.onNodeWithText("更新する").performClick()
        rule.onNodeWithText("更新鳥").assertIsDisplayed()
        rule.onNodeWithText("更新鳥").performClick()
        rule.onNodeWithText("削除").performClick()
        rule.onNodeWithText("スレッド監視 1個").assertIsDisplayed()

        rule.onNodeWithText("ＴＥＳＴ").performClick()
        rule.onNodeWithTag("compat-catalog-extract-edit-word", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("更新する").assertIsDisplayed()
        rule.onNodeWithText("削除").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").assertIsDisplayed().performClick()
        rule.onNodeWithText("スレッド監視 1個").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("スレッド監視 0個").assertIsDisplayed()
        rule.onAllNodesWithText("ＴＥＳＴ").assertCountEquals(0)
    }

    @Test
    fun catalogIgnoreReferenceScreenUsesAliasesScopeIconsAndExactEditors() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val otherBoardUrl = "https://img.2chan.net/b/"
        val otherBoardKey = compatBoardKey(otherBoardUrl)
        val allBoardId = "reference-catalog-ignore-all"
        val legacyId = "reference-catalog-ignore-legacy"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.upsertBoard(CompatBoard(otherBoardKey, "imgb", otherBoardUrl, otherBoardUrl, 1))
            store.upsertNgRule(
                CompatNgRule(allBoardId, CompatNgKind.CATALOG_IGNORE, "*", "zebra", 1L, memo = "Ｚｅｂｒａ")
            )
            store.upsertNgRule(
                CompatNgRule(legacyId, CompatNgKind.CATALOG_WORD, boardKey, "apple", 2L, memo = "Apple")
            )
            store.upsertNgRule(
                CompatNgRule("other-board-ignore", CompatNgKind.CATALOG_IGNORE, otherBoardKey, "hidden", 3L)
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().size >= 2
        }
        val catalogOverflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[catalogOverflowNodes.lastIndex].performClick()
        rule.onNodeWithText("NG管理", substring = true).performClick()
        rule.onNodeWithText("NGワード").performClick()
        rule.onNodeWithText("ＮＧワード 2個").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("全ての板").assertCountEquals(1)
        rule.onAllNodesWithText("全ての板 ・ 1970/01/01", substring = true).assertCountEquals(0)
        val appleBounds = rule.onNodeWithTag("compat-catalog-ignore-row-$legacyId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val zebraBounds = rule.onNodeWithTag("compat-catalog-ignore-row-$allBoardId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(appleBounds.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
        assertTrue("NG words must use the reference ascending order", appleBounds.top < zebraBounds.top)

        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("compat-catalog-ignore-search", useUnmergedTree = true).performTextInput("ｚＥＢＲＡ")
        rule.onNodeWithText("Ｚｅｂｒａ").assertIsDisplayed()
        rule.onAllNodesWithText("Apple").assertCountEquals(0)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()

        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithText("単語").assertIsDisplayed()
        rule.onNodeWithText("全ての板").assertIsDisplayed()
        rule.onNodeWithText("・大文字と小文字を区別しません", substring = true).assertIsDisplayed()
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("単語を入力して下さい").assertIsDisplayed()
        val addWord = rule.onNodeWithTag("compat-catalog-ignore-add-word", useUnmergedTree = true)
        addWord.performTextInput("12345678901")
        addWord.assert(hasTextExactly("1234567890"))
        addWord.performTextReplacement("追加犬")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("ＮＧワード 3個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().any {
                    it.kind == CompatNgKind.CATALOG_IGNORE && it.memo == "追加犬"
                }
            }
        }

        rule.onNodeWithText("追加犬").performClick()
        rule.onNodeWithTag("compat-catalog-ignore-edit-word", useUnmergedTree = true)
            .performTextReplacement("更新鳥")
        rule.onNodeWithText("更新する").performClick()
        rule.onNodeWithText("更新鳥").assertIsDisplayed()
        rule.onNodeWithText("更新鳥").performClick()
        rule.onNodeWithText("削除").performClick()
        rule.onNodeWithText("ＮＧワード 2個").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧワード 0個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().none {
                    it.kind == CompatNgKind.CATALOG_IGNORE || it.kind == CompatNgKind.CATALOG_WORD
                }
            }
        }
    }

    @Test
    fun catalogRefuseReferenceScreenShowsTitleUrlAndConfirmsEveryDeletion() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val otherBoardUrl = "https://img.2chan.net/b/"
        val otherBoardKey = compatBoardKey(otherBoardUrl)
        val newestId = "reference-catalog-refuse-new"
        val oldAliasId = "reference-catalog-refuse-alias"
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.upsertBoard(CompatBoard(otherBoardKey, "imgb", otherBoardUrl, otherBoardUrl, 1))
            store.upsertNgRule(
                CompatNgRule(
                    newestId,
                    CompatNgKind.CATALOG_REFUSE,
                    boardKey,
                    "https://may.2chan.net/b/res/123.htm",
                    10L,
                    memo = "新題名"
                )
            )
            store.upsertNgRule(
                CompatNgRule(oldAliasId, CompatNgKind.CATALOG_THREAD, boardKey, "44", 1L, memo = "旧式")
            )
            store.upsertNgRule(
                CompatNgRule(
                    "other-board-refuse",
                    CompatNgKind.CATALOG_REFUSE,
                    otherBoardKey,
                    "https://img.2chan.net/b/res/999.htm",
                    20L,
                    memo = "他板"
                )
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().size >= 2
        }
        val catalogOverflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[catalogOverflowNodes.lastIndex].performClick()
        rule.onNodeWithText("NG管理", substring = true).performClick()
        rule.onNodeWithText("NGスレッド").performClick()
        rule.onNodeWithText("ＮＧスレッド 2個").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("新規追加").assertCountEquals(0)
        rule.onAllNodesWithText("全ての板 ・", substring = true).assertCountEquals(0)
        rule.onNodeWithText("新題名\nhttps://may.2chan.net/b/res/123.htm").assertIsDisplayed()
        val newBounds = rule.onNodeWithTag("compat-catalog-refuse-row-$newestId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val oldBounds = rule.onNodeWithTag("compat-catalog-refuse-row-$oldAliasId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(newBounds.height >= with(rule.density) { 60.dp.toPx() })
        assertTrue("NG threads must use newest-first reference order", newBounds.top < oldBounds.top)

        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("compat-catalog-refuse-search", useUnmergedTree = true).performTextInput("RES/123")
        rule.onNodeWithText("新題名\nhttps://may.2chan.net/b/res/123.htm").assertIsDisplayed()
        rule.onAllNodesWithText("旧式\n44").assertCountEquals(0)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()

        rule.onNodeWithText("新題名\nhttps://may.2chan.net/b/res/123.htm").performClick()
        rule.onNodeWithText("登録の削除").assertIsDisplayed()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("ＮＧスレッド 2個").assertIsDisplayed()
        rule.onNodeWithText("新題名\nhttps://may.2chan.net/b/res/123.htm").performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧスレッド 1個").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧスレッド 0個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().none {
                    it.kind == CompatNgKind.CATALOG_REFUSE || it.kind == CompatNgKind.CATALOG_THREAD
                }
            }
        }
    }

    @Test
    fun catalogContextRegistrationImmediatelyFeedsReferenceNgManagers() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/456.htm"
        val title = "長い題名の対象スレ"
        val item = CatalogItem(
            id = "456",
            threadUrl = threadUrl,
            title = title,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = 5
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = listOf(item)
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = repository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        rule.waitUntil(10_000) {
            runCatching {
                rule.onNodeWithTag("compat-catalog-item-456").assertIsDisplayed()
            }.isSuccess
        }
        rule.onNodeWithTag("compat-catalog-item-456").assertIsDisplayed()
        rule.onNodeWithTag("compat-catalog-item-456").performTouchInput { longClick() }
        rule.onNodeWithText("NGスレッドとNGワードに登録").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                val rules = store.ngRules.first()
                rules.any { it.kind == CompatNgKind.CATALOG_REFUSE } &&
                    rules.any { it.kind == CompatNgKind.CATALOG_IGNORE }
            }
        }
        val savedRules = runBlocking { store.ngRules.first() }
        val refuse = savedRules.single { it.kind == CompatNgKind.CATALOG_REFUSE }
        val ignore = savedRules.single { it.kind == CompatNgKind.CATALOG_IGNORE }
        assertEquals(threadUrl, refuse.normalizedValue)
        assertEquals("長い題名", refuse.memo)
        assertEquals(normalizeCompatSearchText(title), ignore.normalizedValue)
        assertEquals(title, ignore.memo)
        rule.onAllNodesWithTag("compat-catalog-item-456").assertCountEquals(0)
    }

    @Test
    fun imageNgManagerUnifiesUrlAndPhashRowsWithReferenceEditorThresholdAndClear() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val otherBoardKey = compatBoardKey("https://img.2chan.net/b/")
        val directId = "reference-image-ng-direct"
        val phashId = "reference-image-ng-phash"
        val phashCreatedAt = 1_700_000_000_000L
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.upsertBoard(
                CompatBoard(
                    otherBoardKey,
                    "imgb",
                    "https://img.2chan.net/b/",
                    "https://img.2chan.net/b/",
                    1
                )
            )
            store.upsertNgRule(
                CompatNgRule(
                    id = directId,
                    kind = CompatNgKind.CATALOG_IMAGE,
                    scopeKey = boardKey,
                    normalizedValue = "https://example.com/src/direct.jpg",
                    createdAtEpochMillis = 1_600_000_000_000L,
                    imageUrl = "https://example.com/src/direct.jpg"
                )
            )
            store.upsertNgRule(
                CompatNgRule(
                    id = phashId,
                    kind = CompatNgKind.CATALOG_IMAGE_PHASH,
                    scopeKey = "*",
                    normalizedValue = "0123456789abcdef",
                    createdAtEpochMillis = phashCreatedAt,
                    imageUrl = "https://example.com/src/global.jpg",
                    memo = "ＴＥＳＴ画像"
                )
            )
            store.upsertNgRule(
                CompatNgRule(
                    id = "reference-image-ng-other-board",
                    kind = CompatNgKind.CATALOG_IMAGE_PHASH,
                    scopeKey = otherBoardKey,
                    normalizedValue = "fedcba9876543210",
                    createdAtEpochMillis = 1_800_000_000_000L,
                    imageUrl = "https://example.com/src/other.jpg"
                )
            )
            store.upsertNgRule(
                CompatNgRule(
                    id = "reference-image-ng-thread-source",
                    kind = CompatNgKind.THREAD_IMAGE_PHASH,
                    scopeKey = boardKey,
                    normalizedValue = "aaaaaaaaaaaaaaaa",
                    createdAtEpochMillis = 1_900_000_000_000L,
                    imageUrl = "https://example.com/src/thread.jpg"
                )
            )
        }
        val blockedRepository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogWithSettings(
                board: String,
                mode: CatalogMode,
                settings: CatalogFetchSettings
            ): List<CatalogItem> = awaitCancellation()
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = blockedRepository, onExitApplication = {})
                }
            }
        }

        rule.onNodeWithText("mayb").performClick()
        waitForTagDisplayed("compat-catalog-grid")
        rule.waitUntil(5_000) {
            rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes().size >= 2
        }
        val catalogOverflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[catalogOverflowNodes.lastIndex].performClick()
        rule.onNodeWithText("NG管理", substring = true).performClick()
        rule.onNodeWithText("NG画像").performClick()

        rule.onNodeWithText("ＮＧ画像 2個").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("新規追加").assertCountEquals(0)
        rule.onNodeWithText("ＴＥＳＴ画像").assertIsDisplayed()
        rule.onNodeWithText("direct.jpg").assertIsDisplayed()
        rule.onNodeWithText("全ての板").assertIsDisplayed()
        rule.onNodeWithText("mayb").assertIsDisplayed()
        val newest = rule.onNodeWithTag("compat-image-ng-row-$phashId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val older = rule.onNodeWithTag("compat-image-ng-row-$directId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(newest.height - with(rule.density) { 68.dp.toPx() }) <= 1f)
        assertTrue("ImageNgDao orders newest registrations first", newest.top < older.top)

        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("compat-image-ng-search", useUnmergedTree = true).performTextInput("test")
        rule.onNodeWithText("ＴＥＳＴ画像").assertIsDisplayed()
        rule.onAllNodesWithText("direct.jpg").assertCountEquals(0)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()

        rule.onNodeWithText("ＴＥＳＴ画像").performClick()
        val editThumb = rule.onNodeWithTag("compat-image-ng-edit-thumb", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(abs(editThumb.width - with(rule.density) { 96.dp.toPx() }) <= 1f)
        assertTrue(abs(editThumb.height - with(rule.density) { 96.dp.toPx() }) <= 1f)
        rule.onNodeWithTag("compat-image-ng-edit-memo", useUnmergedTree = true)
            .performTextReplacement("更新メモ")
        rule.onNodeWithTag("compat-image-ng-edit-local-only", useUnmergedTree = true).performClick()
        rule.onNodeWithText("更新する").performClick()
        rule.onNodeWithText("更新メモ").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().any {
                    it.kind == CompatNgKind.CATALOG_IMAGE_PHASH &&
                        it.scopeKey == boardKey &&
                        it.memo == "更新メモ" &&
                        it.createdAtEpochMillis == phashCreatedAt
                }
            }
        }

        rule.onNodeWithText("direct.jpg").performClick()
        rule.onNodeWithText("NG画像").assertIsDisplayed()
        rule.onNodeWithText("削除").performClick()
        rule.onNodeWithText("ＮＧ画像 1個").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("類似判定のしきい値").performClick()
        rule.onNodeWithTag("compat-image-ng-threshold-slider", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("64bit pHashのハミング距離です。", substring = true).assertIsDisplayed()
        rule.onNodeWithText("初期値に戻す").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("ＮＧ画像 1個").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("登録済みのNG画像を全て削除します。よろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧ画像 0個").assertIsDisplayed()
        val remaining = runBlocking { store.ngRules.first() }
        assertTrue(remaining.any { it.id == "reference-image-ng-other-board" })
        assertTrue(remaining.any { it.id == "reference-image-ng-thread-source" })
        assertTrue(remaining.none { it.kind == CompatNgKind.CATALOG_IMAGE && it.scopeKey == boardKey })
        assertTrue(remaining.none { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH && it.scopeKey == boardKey })
    }

    @Test
    fun threadReferenceNgManagersMatchDedicatedActivitiesScopesEditorsAndGlobalClear() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/912.htm"
        val threadKey = compatTabKey(threadUrl)
        val otherThreadKey = compatTabKey("${boardUrl}res/913.htm")
        val headerGlobalId = "reference-thread-header-global"
        val headerLocalId = "reference-thread-header-local"
        val ignoreGlobalId = "reference-thread-ignore-global"
        val ignoreLocalId = "reference-thread-ignore-local"
        val page = ThreadPage(
            threadId = "912",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "912",
                    order = 0,
                    author = "としあき",
                    subject = null,
                    timestamp = "08/25 12:00",
                    messageHtml = "管理画面を開くレス",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = threadKey,
                    canonicalUrl = threadUrl,
                    originalUrl = threadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "912",
                    title = "管理画面を開くスレ",
                    replyCount = 1,
                    insertedAtEpochMillis = 1L,
                    contentUpdatedAtEpochMillis = 1L
                )
            )
            val otherThreadUrl = "${boardUrl}res/913.htm"
            store.openTab(
                CompatTab(
                    key = otherThreadKey,
                    canonicalUrl = otherThreadUrl,
                    originalUrl = otherThreadUrl,
                    boardKey = boardKey,
                    boardName = "mayb",
                    threadNo = "913",
                    title = "別のスレ",
                    insertedAtEpochMillis = 2L,
                    contentUpdatedAtEpochMillis = 2L
                )
            )
            store.upsertNgRule(
                CompatNgRule(headerGlobalId, CompatNgKind.THREAD_POST_NO, "*", "12", 1L)
            )
            store.upsertNgRule(
                CompatNgRule(headerLocalId, CompatNgKind.THREAD_REFUSE, threadKey, "zebra", 2L, memo = "Ｚｅｂｒａ")
            )
            store.upsertNgRule(
                CompatNgRule("reference-thread-header-other", CompatNgKind.THREAD_POSTER_ID, otherThreadKey, "ID:other", 3L)
            )
            store.upsertNgRule(
                CompatNgRule(ignoreGlobalId, CompatNgKind.THREAD_IGNORE, "*", "apple", 4L, memo = "Apple")
            )
            store.upsertNgRule(
                CompatNgRule(ignoreLocalId, CompatNgKind.THREAD_IGNORE, threadKey, "zebra", 5L, memo = "Ｚｅｂｒａ")
            )
            store.upsertNgRule(
                CompatNgRule("reference-thread-ignore-other", CompatNgKind.THREAD_IGNORE, otherThreadKey, "hidden", 6L)
            )
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTagDisplayed("compat-thread-pager")

        fun openThreadNg(label: String) {
            val overflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
            rule.onAllNodesWithContentDescription("その他")[overflowNodes.lastIndex].performClick()
            rule.onNodeWithText("NG管理", substring = true).performClick()
            rule.onNodeWithText(label).performClick()
        }

        openThreadNg("NGヘッダー")
        rule.onNodeWithText("ＮＧヘッダー 2個").assertIsDisplayed()
        rule.onNodeWithContentDescription("全てのスレッド").assertIsDisplayed()
        rule.onAllNodesWithText("全ての板 ・", substring = true).assertCountEquals(0)
        rule.onNodeWithText("No.12").assertIsDisplayed()
        rule.onNodeWithText("Ｚｅｂｒａ").assertIsDisplayed()
        val noBounds = rule.onNodeWithTag("compat-thread-refuse-row-$headerGlobalId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val zebraBounds = rule.onNodeWithTag("compat-thread-refuse-row-$headerLocalId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(noBounds.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
        assertTrue("ThreadRefuse rows must use displayed-word ascending order", noBounds.top < zebraBounds.top)

        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithText("ＮＧヘッダー").assertIsDisplayed()
        rule.onNodeWithText("このスレッドのみ").assertIsDisplayed()
        rule.onNodeWithText("・読み込みが長くなります", substring = true).assertIsDisplayed()
        val headerInput = rule.onNodeWithTag("compat-thread-refuse-add-word", useUnmergedTree = true)
        headerInput.performTextInput("123456789012345678901")
        headerInput.assert(hasTextExactly("12345678901234567890"))
        headerInput.performTextReplacement("無題")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("登録できない単語です").assertIsDisplayed()
        headerInput.performTextReplacement("追加犬")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("ＮＧヘッダー 3個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().any {
                    it.kind == CompatNgKind.THREAD_REFUSE &&
                        it.scopeKey == threadKey &&
                        it.memo == "追加犬"
                }
            }
        }
        rule.onNodeWithText("追加犬").performClick()
        rule.onNodeWithTag("compat-thread-refuse-edit-word", useUnmergedTree = true)
            .performTextReplacement("更新鳥")
        rule.onNodeWithText("更新する").performClick()
        rule.onNodeWithText("更新鳥").assertIsDisplayed()

        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("本当によろしいですか？").assertIsDisplayed()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧヘッダー 0個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().none {
                    it.kind in setOf(
                        CompatNgKind.THREAD_REFUSE,
                        CompatNgKind.THREAD_POST_NO,
                        CompatNgKind.THREAD_POSTER_ID
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("戻る").performClick()
        rule.onNodeWithTag("compat-thread-pager").assertIsDisplayed()

        openThreadNg("NGワード")
        rule.onNodeWithText("ＮＧワード 2個").assertIsDisplayed()
        rule.onNodeWithContentDescription("全てのスレッド").assertIsDisplayed()
        val appleBounds = rule.onNodeWithTag("compat-thread-ignore-row-$ignoreGlobalId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val ignoreZebraBounds = rule.onNodeWithTag("compat-thread-ignore-row-$ignoreLocalId", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(appleBounds.height - with(rule.density) { 60.dp.toPx() }) <= 1f)
        assertTrue(appleBounds.top < ignoreZebraBounds.top)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("compat-thread-ignore-search", useUnmergedTree = true).performTextInput("ａｐＰＬＥ")
        rule.onNodeWithText("Apple").assertIsDisplayed()
        rule.onAllNodesWithText("Ｚｅｂｒａ").assertCountEquals(0)
        rule.onNodeWithTag("compat-rule-management-search", useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription("新規追加").performClick()
        rule.onNodeWithText("ＮＧワード").assertIsDisplayed()
        rule.onNodeWithText("このスレッドのみ").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-ignore-add-word", useUnmergedTree = true)
            .performTextInput("本文追加")
        rule.onNodeWithText("追加する").performClick()
        rule.onNodeWithText("ＮＧワード 3個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().any {
                    it.kind == CompatNgKind.THREAD_IGNORE &&
                        it.scopeKey == threadKey &&
                        it.memo == "本文追加"
                }
            }
        }
        rule.onNodeWithTag("compat-rule-management-overflow", useUnmergedTree = true).performClick()
        rule.onNodeWithText("全て削除").performClick()
        rule.onNodeWithText("削除する").performClick()
        rule.onNodeWithText("ＮＧワード 0個").assertIsDisplayed()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().none { it.kind == CompatNgKind.THREAD_IGNORE }
            }
        }
    }

    @Test
    fun threadLongPressNgRegistrationUsesRawReferenceRowsAndFeedsHeaderManager() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/914.htm"
        val threadKey = compatTabKey(threadUrl)
        val page = ThreadPage(
            threadId = "914",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "914",
                    order = 0,
                    author = "登録作者",
                    subject = "登録題名",
                    timestamp = "08/25 12:00 ID:AbCd",
                    messageHtml = "登録本文",
                    imageUrl = null,
                    thumbnailUrl = null
                ),
                Post(
                    id = "915",
                    order = 1,
                    author = "としあき",
                    subject = null,
                    timestamp = "08/25 12:01",
                    messageHtml = "残るレス",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-914", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-thread-post-914", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("NG登録").performClick()
        rule.onAllNodesWithText("NG登録").assertCountEquals(0)
        rule.onNodeWithText("このスレッドのみ").assertIsDisplayed()
        rule.onNodeWithText("登録題名").assertIsDisplayed()
        rule.onNodeWithText("登録作者").assertIsDisplayed()
        rule.onNodeWithText("ID:AbCd").assertIsDisplayed()
        rule.onNodeWithText("No.914").assertIsDisplayed()
        val bodyCandidateNodes = rule.onAllNodesWithText("登録本文", useUnmergedTree = true)
            .fetchSemanticsNodes()
        rule.onAllNodesWithText("登録本文", useUnmergedTree = true)[bodyCandidateNodes.lastIndex]
            .assertIsDisplayed()
        rule.onAllNodesWithText("題名: 登録題名").assertCountEquals(0)
        rule.onAllNodesWithText("本文: 登録本文").assertCountEquals(0)
        rule.onNodeWithText("ID:AbCd").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                store.ngRules.first().any {
                    it.kind == CompatNgKind.THREAD_REFUSE &&
                        it.scopeKey == threadKey &&
                        it.memo == "ID:AbCd"
                }
            }
        }
        rule.onAllNodesWithTag("compat-thread-post-914").assertCountEquals(0)
        rule.onNodeWithTag("compat-thread-post-915").assertIsDisplayed()

        val overflowNodes = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        rule.onAllNodesWithContentDescription("その他")[overflowNodes.lastIndex].performClick()
        rule.onNodeWithText("NG管理", substring = true).performClick()
        rule.onNodeWithText("NGヘッダー").performClick()
        rule.onNodeWithText("ＮＧヘッダー 1個").assertIsDisplayed()
        rule.onNodeWithText("ID:AbCd").assertIsDisplayed()
    }

    @Test
    fun threadExtractionAndWebSearchDialogsMatchBothReferenceApks() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/916.htm"
        val page = ThreadPage(
            threadId = "916",
            boardTitle = "mayb",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                Post(
                    id = "916",
                    order = 0,
                    author = "としあき",
                    subject = null,
                    timestamp = "08/25 12:00",
                    messageHtml = "検索語A、検索語B",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = page
        }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store,
                        repository = repository,
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("compat-thread-post-916", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("compat-thread-post-916", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.onNodeWithText("web").performClick()
        rule.onNodeWithText("Google検索").assertIsDisplayed()
        rule.onAllNodesWithText("検索語A")[0].assertIsDisplayed()
        rule.onAllNodesWithText("検索語B")[0].assertIsDisplayed()
        rule.onAllNodesWithText("検索語").assertCountEquals(0)
        rule.onNodeWithText("検索する").assertIsEnabled()
        rule.onNodeWithText("キャンセル").performClick()

        rule.onNodeWithContentDescription("レス抽出").performClick()
        rule.onNodeWithText("キーワード").performClick()
        rule.onNodeWithText("キーワード").assertIsDisplayed()
        rule.onNodeWithTag("compat-thread-extraction-keyword", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("検索する").assertIsEnabled().performClick()
        rule.onAllNodesWithTag("compat-thread-extraction-keyword", useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
