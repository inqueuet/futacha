package com.valoser.futacha

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B8（AIスレ要約・AIによる荒らし非表示、AIからのアプリ操作と確認が必要な操作）。
 * 同梱のチュートリアル板（模擬データ）だけを使い、実サーバー・OpenAIへは何も送らない（エミュレーターに端末AIは無い想定で、
 * 要約・非表示は「利用できない」状態の表示まで）。命令は Android のブリッジ（Siri・ショートカット等と同じ入口）から入れる。
 */
private const val B8_TUTORIAL_URL = "https://www.example.com/t/futaba.php"
private const val B8_TUTORIAL_NAME = "チュートリアル＠ふたちゃ"
private const val B8_NG_WORD = "びーはちえーあい"
private const val B8_ARCHIVE_REPORT_KEY = "compat.archive_report.enabled"

class FutaberParityB8AiInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalNgWords = emptyList<String>()
    private var originalAiCommand = false
    private var originalSummary = false
    private var originalModeration = false
    private var originalArchiveReport: String? = null

    private val keys = listOf("compat.futaber.lastBoardId", "compat.futaber.tabs", "compat.futaber.seen")

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            originalArchiveReport = app.compatibilityStore.loadPreference(B8_ARCHIVE_REPORT_KEY)
            app.compatibilityStore.savePreference(B8_ARCHIVE_REPORT_KEY, "OFF")
            originalNgWords = app.appStateStore.ngWords.first()
            app.appStateStore.setNgWords(emptyList())
            originalAiCommand = app.appStateStore.isAiCommandEnabled.first()
            originalSummary = app.appStateStore.isThreadSummaryModeEnabled.first()
            originalModeration = app.appStateStore.isAiPostFilterEnabled.first()
            app.appStateStore.setAiCommandEnabled(false)
            app.appStateStore.setThreadSummaryModeEnabled(false)
            app.appStateStore.setAiPostFilterEnabled(false)
            app.appStateStore.updateBoards { boards ->
                if (boards.none { it.url == B8_TUTORIAL_URL }) {
                    listOf(BoardSummary("t", B8_TUTORIAL_NAME, "チュートリアル", B8_TUTORIAL_URL, "チュートリアル")) + boards
                } else boards
            }
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.setNgWords(originalNgWords)
            app.appStateStore.setAiCommandEnabled(originalAiCommand)
            app.appStateStore.setThreadSummaryModeEnabled(originalSummary)
            app.appStateStore.setAiPostFilterEnabled(originalModeration)
            app.compatibilityStore.savePreferences(keys.associateWith { null } + mapOf(B8_ARCHIVE_REPORT_KEY to originalArchiveReport))
        }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun pressSystemBack() {
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("input keyevent KEYCODE_BACK").use { command ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        }
        rule.waitForIdle()
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun send(action: FutachaAiAction, vararg parameters: Pair<String, String>) {
        assertTrue(FutachaAiCommandBridge.enqueue(FutachaAiCommand(action, parameters.toMap(), source = "test")))
    }

    /** Waits until the notice at the bottom says [text] (each notice replaces the previous one). */
    private fun waitForNotice(text: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodes(hasTestTag("futaber-app-notice") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        rule.waitUntil(20_000) {
            rule.onAllNodes(hasTestTag("futaber-drawer-board") and hasText(B8_TUTORIAL_NAME, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasTestTag("futaber-drawer-board") and hasText(B8_TUTORIAL_NAME, substring = true)).performScrollTo().performClick()
        waitForTag("futaber-catalog-item")
    }

    private fun openTutorialThread() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
    }

    private fun listScroll(): Float = rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: -1f

    private fun setAiCommands(enabled: Boolean) = runBlocking { app.appStateStore.setAiCommandEnabled(enabled) }

    @Test fun withTheSharedSwitchOffCommandsAreRefusedExceptTheSettings() {
        openTutorialCatalog()
        send(FutachaAiAction.OpenBoardList)
        waitForNotice("AIアプリ操作は設定でOFFです")
        assertTrue("the board list stays closed", absent("futaber-drawer"))
        // Opening the settings is allowed while the switch is off (as in the other modes).
        send(FutachaAiAction.OpenGlobalSettings)
        waitForTag("futaber-settings")
    }

    @Test fun theAiSettingsPageHasEachSwitchOnce() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-ai").performScrollTo().performClick()
        waitForTag("compat-ai-command-enabled")
        listOf("compat-ai-summary-enabled", "compat-ai-moderation-enabled", "compat-ai-command-enabled").forEach { tag ->
            assertEquals(tag, 1, rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size)
        }
        assertEquals(1, rule.onAllNodesWithText("AIアプリ操作（アルファ版）").fetchSemanticsNodes().size)
    }

    @Test fun safeCommandsRunOnThisModesScreens() {
        setAiCommands(true)
        openTutorialCatalog()
        // 板一覧を開く → this mode's board list.
        send(FutachaAiAction.OpenBoardList)
        waitForTag("futaber-drawer")
        waitForNotice("板一覧を開きました")
        // 板を開く (by its name) closes the list and shows the catalog.
        send(FutachaAiAction.OpenBoard, "board" to B8_TUTORIAL_NAME)
        rule.waitUntil(10_000) { absent("futaber-drawer") }
        waitForTag("futaber-catalog-item")
        // カタログ検索 opens the catalog's search bar with the word.
        send(FutachaAiAction.SearchCatalog, "query" to "スレ")
        waitForNotice("カタログを「スレ」で検索しました")

        // A thread opened by hand, then the thread screen's commands.
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val threadId = runBlocking { app.appStateStore.history.first().first().threadId }
        send(FutachaAiAction.ScrollThreadToBottom)
        waitForNotice("スレの末尾へ移動しました")
        rule.waitUntil(10_000) { listScroll() > 0f }
        send(FutachaAiAction.ScrollThreadToTop)
        waitForNotice("スレの先頭へ移動しました")
        rule.waitUntil(10_000) { listScroll() == 0f }
        send(FutachaAiAction.SearchThread, "query" to "テスト")
        waitForTag("futaber-filter-chip")
        // Read-aloud: started (the emulator may have no voice; the reader then says so) and stopped.
        send(FutachaAiAction.StartThreadReadAloud)
        waitForNotice("読み上げを開始しました")
        rule.waitUntil(20_000) { !absent("futaber-reading-chip") || !absent("futaber-reading-message") }
        send(FutachaAiAction.StopThreadReadAloud)
        waitForNotice("読み上げ")

        // 板を開く from the thread goes back to the catalog; スレを開く (number and board) opens the thread again.
        send(FutachaAiAction.OpenBoard, "board" to B8_TUTORIAL_NAME)
        rule.waitUntil(10_000) { absent("futaber-post") }
        send(FutachaAiAction.OpenThread, "thread" to threadId, "board" to B8_TUTORIAL_NAME)
        waitForTag("futaber-post")
        waitForNotice("スレ $threadId を開きました")

        // 履歴を開く → the history panel.
        send(FutachaAiAction.OpenHistoryDrawer)
        waitForNotice("履歴を開きました")
        rule.onNodeWithTag("futaber-manage-close").performClick()
        // NGワードを追加 → the basic NG list shared with ふたちゃ.
        send(FutachaAiAction.AddNgWord, "word" to B8_NG_WORD)
        waitForNotice("NGワードに「$B8_NG_WORD」を追加しました")
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.ngWords.first() }.contains(B8_NG_WORD) }
    }

    @Test fun aCommandOutsideTheSubsetIsAnsweredAsNotSupported() {
        setAiCommands(true)
        openTutorialCatalog()
        send(FutachaAiAction.SetCatalogMode, "mode" to "new")
        waitForNotice("ふたばー風モードでは使えません")
        // A thread of an official board that is not registered asks to add it first (and nothing is added on Back).
        send(FutachaAiAction.OpenThread, "url" to "https://dat.2chan.net/b/res/1.htm")
        waitForTag("futaber-board-add-question")
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { absent("futaber-board-add-question") }
        // A thread command with no thread on screen says so.
        send(FutachaAiAction.ScrollThreadToBottom)
        waitForNotice("先に対象スレを開いてください")
    }

    @Test fun savingIsAskedFirstAndRunsOnlyAfterOk() {
        setAiCommands(true)
        openTutorialThread()
        send(FutachaAiAction.SaveCurrentThread)
        waitForTag("futaber-ai-confirm-message")
        rule.onNodeWithTag("futaber-ai-confirm-message").assertTextContains("「現在のスレを保存」を実行します", substring = true)
        rule.onNodeWithTag("futaber-ai-cancel").performClick()
        waitForNotice("AI操作をキャンセルしました")
        assertTrue("nothing was opened", rule.onAllNodesWithText("HTMLのみ").fetchSemanticsNodes().isEmpty())
        // Asked again; OK opens the save screen of the thread (its format choice), nothing is saved here.
        send(FutachaAiAction.SaveCurrentThread)
        waitForTag("futaber-ai-confirm")
        rule.onNodeWithTag("futaber-ai-confirm").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("HTMLのみ").fetchSemanticsNodes().isNotEmpty() }
        // The save screen is its own dialog window. Espresso.pressBack() first waits for "the" focused
        // root and, in the full run, timed out on the activity window while the dialog held the focus
        // (RootViewWithoutFocusException). Send the system Back key instead, as a user would.
        pressSystemBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("HTMLのみ").fetchSemanticsNodes().isEmpty() }
        // Deleting a board is asked too; cancelling keeps it.
        send(FutachaAiAction.DeleteBoard, "board" to B8_TUTORIAL_NAME)
        waitForTag("futaber-ai-confirm-message")
        rule.onNodeWithTag("futaber-ai-confirm-message").assertTextContains("「$B8_TUTORIAL_NAME」", substring = true)
        rule.onNodeWithTag("futaber-ai-cancel").performClick()
        waitForNotice("AI操作をキャンセルしました")
        assertTrue(runBlocking { app.appStateStore.boards.first() }.any { it.url == B8_TUTORIAL_URL })
    }

    @Test fun summaryAndHidingChangeNothingWhileOffAndShowTheirStateWhenOn() {
        openTutorialThread()
        assertTrue("no summary panel while the switch is off", absent("compat-thread-ai-panel"))
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-ng-toggle")
        rule.onNodeWithTag("futaber-menu-ng-toggle").assertIsNotEnabled()
        Espresso.pressBack()
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }

        // The shared switches (the same ones every mode reads) on: the panel appears with the AI's state.
        runBlocking {
            app.appStateStore.setThreadSummaryModeEnabled(true)
            app.appStateStore.setAiPostFilterEnabled(true)
        }
        waitForTag("compat-thread-ai-panel")
        // No device AI on the emulator: the summary ends with a reason (or the extracted text), not "processing" forever.
        rule.waitUntil(60_000) { rule.onAllNodesWithText("スレ要約を処理中…").fetchSemanticsNodes().isEmpty() }
        // The panel folds.
        rule.onNodeWithText("スレ要約・閉じる").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("スレ要約・開く").fetchSemanticsNodes().isNotEmpty() }
        // Nothing judged, so nothing hidden: "NGを無効にする" stays off.
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-ng-toggle")
        rule.onNodeWithTag("futaber-menu-ng-toggle").assertIsNotEnabled()
        Espresso.pressBack()
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }

        // Off again: the panel goes.
        runBlocking { app.appStateStore.setThreadSummaryModeEnabled(false) }
        rule.waitUntil(10_000) { absent("compat-thread-ai-panel") }
    }
}
