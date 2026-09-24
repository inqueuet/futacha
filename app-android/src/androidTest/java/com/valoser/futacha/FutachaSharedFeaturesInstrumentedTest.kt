@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class FutachaSharedFeaturesInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var store: AndroidCompatibilityStore? = null
    private var loader: ImageLoader? = null
    private val databaseName = "futacha_shared_feature_ui_test.db"
    private var themeMode by mutableStateOf(ThemeMode.Light)
    private var themePalette by mutableStateOf(ThemePalette.Current)
    private var hostColors: ColorScheme? = null
    private var sharedColors: ColorScheme? = null
    private var hostChrome: FutachaChromeColors? = null
    private var sharedChrome: FutachaChromeColors? = null

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store?.closeForTest() }
        rule.activity.deleteDatabase(databaseName)
        loader?.shutdown()
    }

    private fun openThread(
        preferences: Map<String, String> = emptyMap(),
        ngRules: List<CompatNgRule> = emptyList(),
        awaitContent: Boolean = true,
        ngRulesDelayMillis: Long = 0L
    ): AndroidCompatibilityStore {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        runBlocking {
            storage.initialize()
            preferences.forEach { (key, value) -> storage.savePreference(key, value) }
            ngRules.forEach { storage.upsertNgRule(it) }
        }
        val board = BoardSummary("shared-ui", "共有確認板", "test", "https://may.2chan.net/b/", "")
        val page = ThreadPage("123", board.name, null, null, listOf(
            Post("123", 0, null, null, "", messageHtml = "通常の投稿です", imageUrl = null, thumbnailUrl = null),
            Post("124", 1, null, null, "", messageHtml = "共通で隠れる投稿", imageUrl = null, thumbnailUrl = null)))
        val repository = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
            override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page)
        }
        val images = ImageLoader(rule.activity).also { loader = it }
        // A slow store delivers its NG rules late; screens must not show posts meanwhile.
        // Built outside composition so recomposing does not restart the delayed flow.
        val sharedStore: CompatibilityStore = if (ngRulesDelayMillis <= 0L) storage else object : CompatibilityStore by storage {
            // Real time: composition coroutines here run on the test's virtual clock.
            override val ngRules = storage.ngRules.onStart {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Thread.sleep(ngRulesDelayMillis) }
            }
        }
        rule.runOnUiThread { rule.activity.setContent {
            FutachaTheme(themeMode, themePalette) { CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                hostColors = MaterialTheme.colorScheme
                hostChrome = LocalFutachaChromeColors.current
                ProvideFutachaSharedFeatures(sharedStore, null, repository, null, null, "test") {
                    sharedColors = MaterialTheme.colorScheme
                    sharedChrome = LocalFutachaChromeColors.current
                    val screenPreferences = ScreenPreferencesState("test", themeMode = themeMode, themePalette = themePalette)
                    var settingsOpen by remember { mutableStateOf(false) }
                    Column {
                        TextButton(onClick = { settingsOpen = true }) { Text("共通設定を開く") }
                        Box(Modifier.weight(1f)) {
                            ThreadScreen(board = board, history = emptyList(), threadId = "123",
                                threadTitle = "共有確認スレッド", initialReplyCount = 1,
                                preferencesState = screenPreferences, repository = repository, onBack = {})
                        }
                    }
                    if (settingsOpen) GlobalSettingsScreen(onBack = { settingsOpen = false },
                        preferencesState = screenPreferences, historyEntries = emptyList(),
                        preferencesCallbacks = ScreenPreferencesCallbacks(
                            onThemeModeChanged = { themeMode = it }, onThemePaletteChanged = { themePalette = it }))
                }
            } }
        } }
        if (awaitContent) {
            rule.waitUntil(15_000) { rule.onAllNodesWithText("通常の投稿です").fetchSemanticsNodes().isNotEmpty() }
        }
        return storage
    }

    @Test fun toshiakiColorsDoNotOverrideFutachaThreadOrSharedSettings() {
        val legacyColors = mapOf(
            "compat.design.designTheme" to "black",
            "compat.design.designTextColor" to "白",
            "compat.design.designNavigationBar" to "ON"
        )
        val storage = openThread(legacyColors)
        fun assertTheme() {
            rule.waitForIdle()
            rule.runOnIdle {
                assertSame("The shared feature host replaced Futacha's color scheme", hostColors, sharedColors)
                assertEquals(hostChrome, sharedChrome)
            }
            val expected = resolveFutabaThreadColorScheme(themePalette,
                resolveFutachaColorScheme(themeMode == ThemeMode.Dark, themePalette))
            val pixels = rule.onNodeWithTag("futacha-post-header-123", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            assertEquals(expected.surface, pixels[0, 0])
        }
        assertTheme()
        rule.runOnIdle { themeMode = ThemeMode.Dark }
        assertTheme()
        rule.onNodeWithText("共通設定を開く").performClick()
        rule.onNodeWithText("表示", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("フォント・タブ一覧").performScrollTo().performClick()
        rule.onNodeWithText("カスタムフォント").assertExists()
        listOf("カラーテーマ", "文字色", "ナビゲーションバー背景色").forEach {
            rule.onNodeWithText(it).assertDoesNotExist()
        }
        val settingsPixels = rule.onNodeWithTag("compat-settings-list-design").captureToImage().toPixelMap()
        assertEquals(resolveFutachaColorScheme(true, ThemePalette.Current).background, settingsPixels[0, 0])
        saveScreenshot("futacha-theme-shared-settings-dark.png")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText(ThemePalette.FutabaClassic.label).performScrollTo().performClick()
        rule.onNodeWithText(ThemeMode.Light.label, substring = false).performScrollTo().performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertTheme()
        saveScreenshot("futacha-theme-classic-thread.png")
        runBlocking {
            val stored = storage.preferences.first()
            legacyColors.forEach { (key, value) -> assertEquals(value, stored[key]) }
            // A previous release used the empty value when returning to a Futacha theme.
            storage.savePreference("compat.design.designTheme", "")
        }
        assertTheme()
    }

    @Test fun historyTabsWatcherAndDialogActionsUseReadableTextInLightAndBlackDarkThemes() {
        openThread()
        for ((palette, mode) in listOf(ThemePalette.FutabaClassic to ThemeMode.Light,
            ThemePalette.FutabaBlack to ThemeMode.Dark)) {
            rule.runOnIdle { themePalette = palette; themeMode = mode }
            val colors = resolveFutabaThreadColorScheme(palette,
                resolveFutachaColorScheme(mode == ThemeMode.Dark, palette))
            rule.onNodeWithContentDescription("履歴を開く").performClick()
            assertReadableText("タブ一覧", colors.background)
            assertReadableText("巡回", colors.background)
            saveScreenshot("readable-history-$palette.png")
            rule.onNodeWithText("タブ一覧").performClick()
            rule.onNodeWithText("タブ一覧 (1)").assertIsDisplayed()
            assertReadableText("整理", colors.surface)
            assertReadableText("閉じる", colors.surfaceContainerHigh)
            saveScreenshot("readable-tabs-$palette.png")
            rule.onNodeWithText("閉じる").performClick()
            rule.onNodeWithText("巡回").performClick()
            assertReadableText("巡回管理", colors.surfaceContainerHigh)
            assertReadableText("保存済み結果を再読込", colors.surfaceContainerHigh)
            rule.onNodeWithText("巡回管理").performClick()
            rule.onNodeWithText("キーワード").performScrollTo()
            assertReadableText("キーワード", colors.surfaceVariant)
            rule.onNodeWithText("端末の通知を許可").performScrollTo()
            assertReadableText("端末の通知を許可", colors.surfaceContainerHigh)
            saveScreenshot("readable-watcher-$palette.png")
            // Back closes the watcher manager, the watcher results dialog and the
            // drawer in turn. Wait for each layer to leave before the next Back:
            // a Back sent while a dialog window is still being removed finds no
            // focused window and Espresso times out.
            pressBackUntil { gone("端末の通知を許可") && !gone("保存済み結果を再読込") }
            pressBackUntil { gone("保存済み結果を再読込") && rule.activity.hasWindowFocus() }
            pressBackUntil { rule.activity.hasWindowFocus() }
        }
    }

    private fun gone(label: String) = rule.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()

    private fun pressBackUntil(condition: () -> Boolean) {
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(10_000, condition)
    }

    private fun assertReadableText(label: String, background: Color) {
        val node = rule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.runOnIdle { node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts) }
        assertTrue("Missing text layout: $label", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val foreground = layout.layoutInput.style.color
            assertEquals("Translucent text: $label", 1f, foreground.alpha)
            val luminance = foreground.compositeOver(background).luminance()
            val contrast = (maxOf(luminance, background.luminance()) + 0.05f) /
                (minOf(luminance, background.luminance()) + 0.05f)
            assertTrue("Low contrast: $label ($contrast)", contrast >= 4.5f)
        }
    }

    private fun saveScreenshot(name: String) {
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test fun watcherSettingsAndSearchableHelpOpenFromFutachaSettings() {
        openThread()
        rule.onNodeWithText("共通設定を開く").performClick()
        rule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("バックグラウンド・通信"))
        rule.onNodeWithText("バックグラウンド・通信").performClick()
        rule.onNodeWithText("巡回管理").performScrollTo().performClick()
        rule.onNodeWithText("履歴・巡回のヘルプ").performScrollTo().performClick()
        rule.waitUntil(10_000) { helpDocumentScript("document.querySelectorAll('label').length > 0") == "true" }
        assertEquals("\"板一覧\"", helpDocumentScript("document.querySelector('label').textContent"))
        assertEquals("false", helpDocumentScript("document.getElementById('watcher-help').checked"))
        helpDocumentScript("document.querySelector('label[for=\"watcher-help\"]').click()")
        assertEquals("true", helpDocumentScript("document.getElementById('watcher-help').nextElementSibling.offsetHeight > 0"))
        helpDocumentScript("document.querySelector('label[for=\"watcher-help\"]').click()")
        assertEquals("false", helpDocumentScript("document.getElementById('watcher-help').checked"))
        rule.onNodeWithTag("help-search-field").performTextInput("にじろぐ")
        rule.onNodeWithTag("help-search-results").assertIsDisplayed()
        rule.waitUntil(10_000) { helpDocumentScript("document.querySelectorAll('mark').length > 0") == "true" }
        assertHelpSearchDocument("にじろぐ")
        helpDocumentScript("document.querySelector('label[for=\"watcher-help\"]').click()")
        assertEquals("false", helpDocumentScript("document.getElementById('watcher-help').checked"))
        rule.onNodeWithTag("help-search-field").performTextReplacement("強制停止")
        rule.waitUntil(10_000) { helpDocumentScript("document.querySelector('mark')?.textContent === '強制停止'") == "true" }
        assertHelpSearchDocument("強制停止")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        helpDocumentScript("document.querySelector('mark').scrollIntoView({block:'center'})")
        saveScreenshot("v11.4-help-search.png")
        rule.onNodeWithTag("help-search-field").performTextReplacement("no-such-help-word-114")
        rule.onNodeWithText("一致する項目がありません").assertIsDisplayed()
        rule.onNodeWithText("クリア").performClick()
        rule.onNodeWithTag("compat-help-content").assertIsDisplayed()
        rule.waitUntil(10_000) { helpDocumentScript("document.querySelectorAll('label').length > 0") == "true" }
        assertEquals("false", helpDocumentScript("document.getElementById('watcher-help').checked"))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onAllNodesWithContentDescription("戻る").onLast().performClick()
        rule.onNodeWithText("履歴・巡回のヘルプ").assertExists()
    }

    @Test fun commonSettingUpdatesTheSameStoredValueWhileFutachaRemainsOpen() {
        val storage = openThread()
        rule.onNodeWithText("共通設定を開く").performClick()
        rule.onNodeWithText("共通の詳細設定").assertDoesNotExist()
        rule.onNodeWithText("操作").performScrollTo().performClick()
        rule.onNodeWithText("コントロール").performScrollTo().performClick()
        rule.onNodeWithText("送信時の確認").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { storage.preferences.first()["compat.control.controlPostConfirm"] == "OFF" } }
        rule.onNodeWithText("送信時の確認").onChildren().filter(isToggleable()).onFirst().assertIsOff()
        rule.waitForIdle()
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, "futacha-shared-control-settings.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("通常の投稿です").assertExists()
    }

    @Test fun sharedNgChangeFiltersTheOpenFutachaThreadAndRegistersItsTab() {
        val storage = openThread()
        rule.onNodeWithText("共通で隠れる投稿").assertExists()
        runBlocking { storage.upsertNgRule(CompatNgRule("ui-global-ng", CompatNgKind.THREAD_IGNORE, "*",
            "共通で隠れる", System.currentTimeMillis())) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("共通で隠れる投稿").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("通常の投稿です").assertExists()
        rule.waitUntil(5_000) { runBlocking { storage.tabs.first().any { it.threadNo == "123" } } }
        assertEquals("共有確認スレッド", runBlocking { storage.tabs.first().single().title })
    }

    @Test fun lateSharedNgRulesNeverLetTheirPostShow() {
        openThread(ngRules = listOf(CompatNgRule("ui-late-ng", CompatNgKind.THREAD_IGNORE, "*",
            "共通で隠れる", System.currentTimeMillis())), awaitContent = false, ngRulesDelayMillis = 1_500L)
        val start = System.currentTimeMillis()
        var shown = false
        // Watch until the post is shown and the delayed rules have arrived, up to 20 s.
        while (System.currentTimeMillis() - start < 20_000L && !(shown && System.currentTimeMillis() - start > 3_000L)) {
            // Used to render with an empty rule list until the rules arrived.
            assertTrue(rule.onAllNodesWithText("共通で隠れる投稿").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty())
            shown = shown || rule.onAllNodesWithText("通常の投稿です").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            Thread.sleep(50)
        }
        assertTrue("thread never showed its visible post: " +
            rule.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes(false)
                .mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString() }
                .take(40), shown)
    }

    @Test fun existingSharedNgNeverShowsItsPostWhenTheThreadOpens() {
        rule.mainClock.autoAdvance = false
        openThread(ngRules = listOf(CompatNgRule("ui-initial-ng", CompatNgKind.THREAD_IGNORE, "*",
            "共通で隠れる", System.currentTimeMillis())), awaitContent = false)
        var shown = false
        repeat(600) {
            if (shown) return@repeat
            rule.mainClock.advanceTimeByFrame()
            // Used to render the post first and hide it once the rules arrived.
            assertTrue(rule.onAllNodesWithText("共通で隠れる投稿").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty())
            shown = rule.onAllNodesWithText("通常の投稿です").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
            if (!shown) Thread.sleep(5)
        }
        assertTrue("thread never showed its visible post", shown)
        rule.mainClock.autoAdvance = true
    }
}
