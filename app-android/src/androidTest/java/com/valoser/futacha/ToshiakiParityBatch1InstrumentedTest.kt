package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.compat.AndroidExperienceProfileStore
import com.valoser.futacha.compat.AndroidLauncherAliasReconciler
import com.valoser.futacha.compat.AndroidModeSwitchCoordinator
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.model.AppIconVariant
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * としあき(仮) feature-parity batch 1-3: the AI command switch, the board pin and the start-up lock / app icon settings.
 * The shared AppStateStore is the application's own, so each test puts back what it changed.
 */
class ToshiakiParityBatch1InstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val app = context as FutachaApplication
    private val sharedStore: AppStateStore get() = app.appStateStore
    private val databaseName = "toshiaki_batch1_${System.nanoTime()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var imageLoader: ImageLoader
    private val mayUrl = "https://may.2chan.net/b/"

    private var originalBoards: List<BoardSummary> = emptyList()
    private var originalAiCommand = false
    private var originalLockHash: String? = null
    private var originalIcon = AppIconVariant.Current

    @Before fun prepare() {
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "1.0")
            originalBoards = sharedStore.boards.first()
            originalAiCommand = sharedStore.isAiCommandEnabled.first()
            originalLockHash = sharedStore.appLockPasswordHash.first()
            originalIcon = sharedStore.appIconVariant.first()
        }
        imageLoader = ImageLoader.Builder(context).build()
    }

    @After fun restore() {
        runBlocking {
            sharedStore.setBoards(originalBoards)
            sharedStore.setAiCommandEnabled(originalAiCommand)
            if (originalLockHash.isNullOrBlank()) sharedStore.clearAppLockPassword()
            sharedStore.setAppIconVariant(originalIcon)
        }
    }

    private fun showApp(withStateStore: Boolean) {
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(store = store, repository = null, stateStore = if (withStateStore) sharedStore else null, onExitApplication = {})
                }
            }
        }
    }

    private fun openSettings() {
        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("設定").performClick()
    }

    @Test fun aiCommandSwitchIsOnTheAiSettingsPageAndPersistsTheSharedSetting() {
        runBlocking { sharedStore.setAiCommandEnabled(false) }
        showApp(withStateStore = true)
        openSettings()
        rule.onNodeWithTag("compat-settings-list-root").performScrollToNode(hasText("AI・補助機能"))
        rule.onNodeWithText("AI・補助機能").performClick()
        rule.onNodeWithTag("compat-ai-command-enabled").performScrollTo().assertIsOff()
        rule.onNodeWithTag("compat-ai-command-enabled").performClick()
        rule.waitUntil(5_000) { runBlocking { sharedStore.isAiCommandEnabled.first() } }
        rule.onNodeWithTag("compat-ai-command-enabled").assertIsOn()
        rule.onNodeWithText("現在はONです", substring = true).assertExists()
        rule.onNodeWithTag("compat-ai-command-enabled").performClick()
        rule.waitUntil(5_000) { runBlocking { !sharedStore.isAiCommandEnabled.first() } }
        rule.onNodeWithTag("compat-ai-command-enabled").assertIsOff()
        // The two older switches of this page are still there and still off.
        rule.onNodeWithTag("compat-ai-summary-enabled").performScrollTo().assertIsOff()
        rule.onNodeWithTag("compat-ai-moderation-enabled").performScrollTo().assertIsOff()
    }

    private fun seedMayBoard() {
        val board = CompatBoard(compatBoardKey(mayUrl), "mayb", mayUrl, mayUrl, 0)
        runBlocking {
            store.upsertBoard(board)
            sharedStore.setBoards(listOf(BoardSummary("may", "mayb", "cat", mayUrl, "d")))
        }
    }

    @Test fun boardLongPressPinsAndUnpinsInTheSharedBoardsAndMarksOnlyThePinnedBoard() {
        seedMayBoard()
        showApp(withStateStore = true)
        rule.onNodeWithText("mayb").assertIsDisplayed()
        rule.onAllNodesWithTag("compat-board-pin-mark", useUnmergedTree = true).assertCountEquals(0)

        rule.onNodeWithText("mayb").performTouchInput { longClick() }
        // The two existing choices stay, in their order, and the pin is added after them.
        rule.onNodeWithText("名前を変更").assertIsDisplayed()
        rule.onNodeWithText("削除する").assertIsDisplayed()
        rule.onAllNodesWithText("ピン留めを解除").assertCountEquals(0)
        rule.onNodeWithText("ピン留め").performClick()
        rule.waitUntil(5_000) { runBlocking { sharedStore.boards.first().single().pinned } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-board-pin-mark", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("mayb").assertIsDisplayed()

        // The menu now offers the way back.
        rule.onNodeWithText("mayb").performTouchInput { longClick() }
        rule.onNodeWithText("ピン留めを解除").assertIsDisplayed()
        rule.onNodeWithText("ピン留めを解除").performClick()
        rule.waitUntil(5_000) { runBlocking { !sharedStore.boards.first().single().pinned } }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-board-pin-mark", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun boardLongPressHasNoPinChoiceWithoutASharedStore() {
        seedMayBoard()
        showApp(withStateStore = false)
        rule.onNodeWithText("mayb").performTouchInput { longClick() }
        rule.onNodeWithText("名前を変更").assertIsDisplayed()
        rule.onAllNodesWithText("ピン留め").assertCountEquals(0)
        rule.onAllNodesWithText("ピン留めを解除").assertCountEquals(0)
    }

    @Test fun rootSettingsOfferLockAndIconOnlyWithASharedStore() {
        showApp(withStateStore = false)
        openSettings()
        rule.onNodeWithTag("compat-settings-list-root").performScrollToNode(hasText("ふたちゃ拡張"))
        rule.onAllNodesWithTag("compat-setting-appLock").assertCountEquals(0)
        rule.onAllNodesWithTag("compat-setting-appIcon").assertCountEquals(0)
    }

    @Test fun startUpLockCanBeSetChangedValidatedAndCleared() {
        runBlocking { sharedStore.clearAppLockPassword() }
        showApp(withStateStore = true)
        openSettings()
        val list = rule.onNodeWithTag("compat-settings-list-root")
        list.performScrollToNode(hasText("起動ロック"))
        rule.onNodeWithText("無効・パスワードを設定すると起動時にロック").assertIsDisplayed()
        rule.onNodeWithTag("compat-setting-appLock").performClick()

        rule.onNodeWithTag("compat-lock-password").performTextInput("abc")
        rule.onNodeWithTag("compat-lock-confirmation").performTextInput("abc")
        rule.onNodeWithTag("compat-lock-save").performClick()
        rule.onNodeWithText("4文字以上で入力してください。").assertIsDisplayed()
        assertTrue(runBlocking { sharedStore.appLockPasswordHash.first() }.isNullOrBlank())

        rule.onNodeWithTag("compat-lock-password").performTextInput("d")
        rule.onNodeWithTag("compat-lock-save").performClick()
        rule.onNodeWithText("確認用パスワードが一致しません。").assertIsDisplayed()

        rule.onNodeWithTag("compat-lock-confirmation").performTextInput("d")
        rule.onNodeWithTag("compat-lock-save").performClick()
        rule.waitUntil(5_000) { !runBlocking { sharedStore.appLockPasswordHash.first() }.isNullOrBlank() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-lock-save").fetchSemanticsNodes().isEmpty() }
        list.performScrollToNode(hasText("有効・起動時にパスワードを要求"))
        rule.onNodeWithText("有効・起動時にパスワードを要求").assertIsDisplayed()

        // Enabled: the same row offers a change and a clear.
        rule.onNodeWithTag("compat-setting-appLock").performClick()
        rule.onNodeWithText("有効です。アプリ起動時にパスワードを要求します。").assertIsDisplayed()
        rule.onNodeWithText("新しいパスワード").assertIsDisplayed()
        rule.onNodeWithTag("compat-lock-clear").performClick()
        rule.waitUntil(5_000) { runBlocking { sharedStore.appLockPasswordHash.first() }.isNullOrBlank() }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-lock-save").fetchSemanticsNodes().isEmpty() }
        list.performScrollToNode(hasText("無効・パスワードを設定すると起動時にロック"))
    }

    @Test fun appIconDialogOffersTheTwoIconsAndSavesTheChoiceInTheSharedSetting() {
        runBlocking { sharedStore.setAppIconVariant(AppIconVariant.Current) }
        showApp(withStateStore = true)
        openSettings()
        val list = rule.onNodeWithTag("compat-settings-list-root")
        list.performScrollToNode(hasText("アプリアイコン"))
        rule.onNodeWithTag("compat-setting-appIcon").performClick()
        rule.onNodeWithTag("compat-app-icon-Current").assertIsDisplayed()
        rule.onNodeWithTag("compat-app-icon-Classic").assertIsDisplayed()
        rule.onAllNodesWithTag("compat-app-icon-Midnight").assertCountEquals(0)
        rule.onNodeWithTag("compat-app-icon-Classic").performClick()
        rule.waitUntil(5_000) { runBlocking { sharedStore.appIconVariant.first() } == AppIconVariant.Classic }
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-app-icon-Classic").fetchSemanticsNodes().isEmpty() }
        list.performScrollToNode(hasText("クラシック"))
        rule.onNodeWithText("クラシック").assertIsDisplayed()
    }

    @Test fun leavingToshiakiKeepsTheIconChosenThere() {
        val preferencesName = "toshiaki_icon_${System.nanoTime()}"
        try {
            val profileStore = AndroidExperienceProfileStore(context, preferencesName)
            val reconciled = mutableListOf<Pair<ExperienceProfile, AppIconVariant>>()
            val coordinator = AndroidModeSwitchCoordinator(profileStore, AndroidLauncherAliasReconciler { profile, icon -> reconciled += profile to icon })
            runBlocking {
                coordinator.switchTo(ExperienceProfile.TOSHIAKI_COMPAT, AppIconVariant.Current).getOrThrow()
                // In としあき(仮) the user picks the classic icon, then goes back to ふたちゃ.
                coordinator.switchTo(ExperienceProfile.FUTACHA, AppIconVariant.Classic).getOrThrow()
            }
            assertEquals(AppIconVariant.Classic, profileStore.readPreferredFutachaIcon())
            assertEquals(ExperienceProfile.FUTACHA to AppIconVariant.Classic, reconciled.last())
        } finally {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
