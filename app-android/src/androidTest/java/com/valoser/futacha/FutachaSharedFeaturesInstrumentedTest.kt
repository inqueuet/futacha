@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
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

    private fun openThread(preferences: Map<String, String> = emptyMap()): AndroidCompatibilityStore {
        val storage = AndroidCompatibilityStore(rule.activity, databaseName = databaseName).also { store = it }
        runBlocking {
            storage.initialize()
            preferences.forEach { (key, value) -> storage.savePreference(key, value) }
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
        rule.runOnUiThread { rule.activity.setContent {
            FutachaTheme(themeMode, themePalette) { CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                hostColors = MaterialTheme.colorScheme
                hostChrome = LocalFutachaChromeColors.current
                ProvideFutachaSharedFeatures(storage, null, repository, null, null, "test") {
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
        rule.waitUntil(15_000) { rule.onAllNodesWithText("通常の投稿です").fetchSemanticsNodes().isNotEmpty() }
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

    private fun saveScreenshot(name: String) {
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(rule.activity.filesDir, name).outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
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
}
