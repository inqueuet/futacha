package com.valoser.futacha

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import androidx.test.espresso.Espresso
import com.valoser.futacha.shared.compat.CompatNgKind
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
 * ふたばー風モードの機能そろえ B7b（NGの詳細管理、レスの詳細操作、関連レス・同じID、画像一覧の選択保存、読み上げの一時停止・位置）。
 * 同梱の模擬データ（チュートリアル板と、may板のアドレスで模擬リポジトリを読む板）だけを使い、実サーバー・外部サービスへは
 * 何も送らない（Google検索は語句の選択まで。閲覧スレ通知は試験中オフにする。保存は行わない）。
 */
private const val B7B_TUTORIAL_URL = "https://www.example.com/t/futaba.php"
/** may's shared key (official address), read through the bundled mock repository (the address names example.com). */
private const val B7B_MAY_LIKE_URL = "https://may.2chan.net/b/futaba.php?mock=example.com"
private const val B7B_MAY_LIKE_ID = "zz-b7bmay"
private const val B7B_MAY_LIKE_NAME = "B7b模擬may"
private const val B7B_NG_WORD = "ぴよぴよ七七"
private const val ARCHIVE_REPORT_KEY = "compat.archive_report.enabled"

class FutaberParityB7bInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalNgWords = emptyList<String>()
    private var originalArchiveReport: String? = null
    private var originalDrafts: String? = null

    private val keys = listOf(
        "compat.futaber.ext.ngDetail",
        "compat.futaber.ext.postDetailOps",
        "compat.futaber.ext.gallerySelectSave",
        "compat.futaber.ext.readAloudPanel",
        "compat.futaber.catalog.sort",
        "compat.futaber.lastBoardId",
        "compat.futaber.tabs",
        "compat.futaber.seen"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            originalNgWords = app.appStateStore.ngWords.first()
            app.appStateStore.setNgWords(emptyList())
            originalDrafts = app.compatibilityStore.loadPreference("compat.futaber.drafts")
            // Visiting the may-like board's thread must not queue its address for the external archive.
            originalArchiveReport = app.compatibilityStore.loadPreference(ARCHIVE_REPORT_KEY)
            app.compatibilityStore.savePreference(ARCHIVE_REPORT_KEY, "OFF")
            app.appStateStore.updateBoards { boards ->
                val withTutorial = if (boards.none { it.url == B7B_TUTORIAL_URL }) {
                    listOf(BoardSummary("t", "チュートリアル＠ふたちゃ", "チュートリアル", B7B_TUTORIAL_URL, "チュートリアル")) + boards
                } else boards
                withTutorial.filterNot { it.id == B7B_MAY_LIKE_ID }
            }
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            // Only the AppState board is removed; the shared store's board is left (it is may's, which others may use).
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.id == B7B_MAY_LIKE_ID } }
            app.appStateStore.updateHistory { list -> list.filterNot { it.boardId == B7B_MAY_LIKE_ID } }
            app.appStateStore.setNgWords(originalNgWords)
            val made = app.compatibilityStore.ngRules.first().filter { it.normalizedValue.contains(B7B_NG_WORD) }
            if (made.isNotEmpty()) app.compatibilityStore.deleteNgRules(made.map { it.id })
            app.compatibilityStore.savePreferences(
                keys.associateWith { null } + mapOf(ARCHIVE_REPORT_KEY to originalArchiveReport, "compat.futaber.drafts" to originalDrafts)
            )
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

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun turnOn(vararg extensionKeys: String) = runBlocking {
        app.compatibilityStore.savePreferences(extensionKeys.associate { it to "ON" })
    }

    private fun openBoard(name: String?) {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        if (name == null) {
            rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        } else {
            rule.waitUntil(20_000) {
                rule.onAllNodes(hasTestTag("futaber-drawer-board") and hasText(name, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(hasTestTag("futaber-drawer-board") and hasText(name, substring = true)).performScrollTo().performClick()
        }
        waitForTag("futaber-catalog-item")
    }

    private fun openThread(boardName: String? = null) {
        openBoard(boardName)
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
    }

    private fun longPressText(text: String) {
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasText(text))
        rule.onNodeWithText(text).performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
    }

    private fun clickAction(id: String) {
        waitForTag("futaber-action-$id")
        rule.onNodeWithTag("futaber-action-$id").performScrollTo().performClick()
    }

    private fun closeSheet() {
        rule.onNodeWithTag("futaber-action-cancel").performScrollTo().performClick()
        rule.waitUntil(5_000) { absent("futaber-post-actions") }
    }

    private fun openMenu() {
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
    }

    private fun clickMenu(id: String) {
        waitForTag("futaber-menu-$id")
        runCatching { rule.onNodeWithTag("futaber-menu-$id").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-$id").performClick()
    }

    private fun textOf(tag: String): String = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun clipboardText(): String? {
        var text: String? = null
        rule.runOnUiThread {
            val manager = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(app)?.toString()
        }
        return text
    }

    @Test fun theExtensionsAddNothingWhileTheyAreOff() {
        openThread()
        longPressText("テスト10")
        listOf("select-copy", "select-search", "quick-quote", "related", "same-id", "same-ip").forEach { id ->
            assertTrue(id, absent("futaber-action-$id"))
        }
        closeSheet()
        openMenu()
        assertTrue(absent("futaber-menu-ng-detail"))
        Espresso.pressBack()
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }
        // The picture list is this mode's own one.
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        waitForTag("futaber-gallery-grid")
        assertTrue(absent("futaber-select-save-gallery"))
        Espresso.pressBack()
        rule.waitUntil(10_000) { absent("futaber-media") }
        // The reading chip's state label is not a button (when the emulator has a voice at all).
        openMenu()
        clickMenu("read-aloud")
        rule.waitUntil(20_000) { !absent("futaber-reading-chip") || !absent("futaber-reading-message") }
        assertTrue(absent("futaber-reading-state"))
        if (!absent("futaber-reading-stop")) rule.onNodeWithTag("futaber-reading-stop").performClick()
        // The catalog's refresh sheet has no "NGの詳細管理".
        Espresso.pressBack()
        waitForTag("futaber-refresh")
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        assertTrue(absent("futaber-action-ng-detail"))
        closeSheet()
    }

    @Test fun thePostDetailOperationsCopyQuoteAndShowRelatedAndSameIdPosts() {
        turnOn("compat.futaber.ext.postDetailOps")
        openThread()
        longPressText("テスト10")
        listOf("select-copy", "select-search", "quick-quote", "related", "same-id").forEach { waitForTag("futaber-action-$it") }
        assertTrue(absent("futaber-action-same-ip")) // the bundled thread shows IDs, not IPs
        // 項目を選んでコピー: the number only.
        clickAction("select-copy")
        waitForTag("futaber-post-item-dialog")
        rule.onNodeWithTag("futaber-post-item-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("futaber-post-item-0").performClick()
        rule.onNodeWithTag("futaber-post-item-confirm").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { absent("futaber-post-item-dialog") }
        waitForTag("futaber-remote-notice")
        rule.onNodeWithTag("futaber-remote-notice").assertTextContains("クリップボードにコピーしました")
        assertEquals("No.1364612475", clipboardText())
        rule.onNodeWithTag("futaber-remote-notice").performClick()
        // 語句を選んでGoogle検索: the words are offered (nothing is opened here).
        longPressText("テスト10")
        clickAction("select-search")
        waitForTag("futaber-post-item-dialog")
        waitForTag("futaber-post-item-0")
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { absent("futaber-post-item-dialog") }
        // 関連レス: the post and its replies in a bubble.
        longPressText("テスト10")
        clickAction("related")
        waitForTag("futaber-quote-card")
        rule.onNode(hasTestTag("futaber-quote-card") and hasContentDescription("関連レス", substring = true)).assertExists()
        assertTrue(rule.onAllNodesWithText("テスト10_2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { absent("futaber-quote-card") }
        // 同じIDのレス: テスト6 and No.1364612824 share an ID.
        longPressText("テスト6")
        clickAction("same-id")
        waitForTag("futaber-quote-card")
        rule.onNode(hasTestTag("futaber-quote-card") and hasContentDescription("同じIDのレス、2件")).assertExists()
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { absent("futaber-quote-card") }
        // クイック引用返信: the write screen opens with every line quoted.
        longPressText("テスト10")
        clickAction("quick-quote")
        waitForTag("futaber-post-comment")
        rule.onNodeWithTag("futaber-post-comment").assertTextContains(">テスト10\n>テスト10_1", substring = true)
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(5_000) { absent("futaber-post-screen") }
    }

    @Test fun theDetailedNgIsDisabledOnABoardWithoutASharedKey() {
        turnOn("compat.futaber.ext.ngDetail")
        openThread()
        openMenu()
        waitForTag("futaber-menu-ng-detail")
        runCatching { rule.onNodeWithTag("futaber-menu-ng-detail").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-ng-detail").assertIsNotEnabled()
        Espresso.pressBack()
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }
        Espresso.pressBack()
        waitForTag("futaber-refresh")
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-action-ng-detail")
        rule.onNodeWithTag("futaber-action-ng-detail").assertIsNotEnabled()
        closeSheet()
    }

    @Test fun theDetailedNgListsTheSharedRulesAndMakesThreadRulesForEveryThreadOnly() {
        turnOn("compat.futaber.ext.ngDetail")
        runBlocking {
            app.appStateStore.updateBoards { boards -> listOf(BoardSummary(B7B_MAY_LIKE_ID, B7B_MAY_LIKE_NAME, "", B7B_MAY_LIKE_URL, "")) + boards }
        }
        // The catalog's refresh sheet: the board's catalog rules.
        openBoard(B7B_MAY_LIKE_NAME)
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-action-ng-detail")
        rule.onNodeWithTag("futaber-action-ng-detail").assertIsEnabled()
        clickAction("ng-detail")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("NG・監視の詳細管理").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("NGスレッド").assertExists()
        rule.onNodeWithText("監視ワード").assertExists()
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("NG・監視の詳細管理").fetchSemanticsNodes().isEmpty() }
        // The thread menu: the thread rules.
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        openMenu()
        clickMenu("ng-detail")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("NG・監視の詳細管理").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("NGヘッダー").assertExists()
        rule.onNodeWithText("NGワード").performClick()
        rule.onNodeWithContentDescription("新規追加").performClick()
        waitForTag("compat-thread-ignore-add-word")
        // This mode keeps no tab of its own: the rule is for every thread, and the dialog does not offer one thread.
        assertTrue(rule.onAllNodesWithText("このスレッドのみ").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("compat-thread-ignore-add-word").performTextInput(B7B_NG_WORD)
        rule.onNodeWithText("追加する").performClick()
        rule.waitUntil(10_000) {
            runBlocking { app.compatibilityStore.ngRules.first() }.any { it.normalizedValue.contains(B7B_NG_WORD) }
        }
        val made = runBlocking { app.compatibilityStore.ngRules.first() }.filter { it.normalizedValue.contains(B7B_NG_WORD) }
        assertEquals(1, made.size)
        assertEquals(CompatNgKind.THREAD_IGNORE, made.single().kind)
        assertEquals("*", made.single().scopeKey)
    }

    @Test fun theSelectionSaveListLeavesOutNgHiddenPostsAndOpensTheViewerOnTheSamePicture() {
        turnOn("compat.futaber.ext.gallerySelectSave")
        runBlocking { app.appStateStore.setNgWords(listOf("テスト8")) }
        openThread()
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        waitForTag("futaber-select-save-gallery")
        assertTrue(absent("futaber-gallery-grid"))
        waitForTag("compat-gallery-item-1364612713")
        // The NG word hides テスト8's post; its picture is not listed (as in the viewer).
        assertTrue(absent("compat-gallery-item-1364612347"))
        rule.onNodeWithTag("compat-gallery-item-1364612713").performScrollTo().performClick()
        waitForTag("futaber-viewer")
        rule.waitUntil(10_000) { textOf("futaber-viewer-counter") == "5 of 8" }
        rule.onNodeWithTag("futaber-viewer-close").performClick()
        rule.waitUntil(10_000) { absent("futaber-media") }
    }

    @Test fun theReadAloudPanelPausesResumesAndShowsThePosition() {
        turnOn("compat.futaber.ext.readAloudPanel")
        openThread()
        openMenu()
        clickMenu("read-aloud")
        rule.waitUntil(20_000) { !absent("futaber-reading-chip") || !absent("futaber-reading-message") }
        if (absent("futaber-reading-chip")) {
            // No voice on this emulator: the reason is shown, and that is as far as it can be checked here.
            waitForTag("futaber-reading-message")
            android.util.Log.i("FutaberB7bTest", "read-aloud: no voice, message only: " + textOf("futaber-reading-message"))
            return
        }
        rule.onNodeWithTag("futaber-reading-state", useUnmergedTree = true).performClick()
        waitForTag("futaber-reading-panel")
        // Once the first post is reached the position reads "n / N".
        rule.waitUntil(20_000) {
            absent("futaber-reading-chip") || !textOf("futaber-reading-position").startsWith("-")
        }
        if (absent("futaber-reading-chip")) {
            // The run ended (a failing engine); its message was shown.
            android.util.Log.i("FutaberB7bTest", "read-aloud: ended before the first post")
            return
        }
        android.util.Log.i("FutaberB7bTest", "read-aloud: position " + textOf("futaber-reading-position"))
        assertTrue(textOf("futaber-reading-position").matches(Regex("\\d+ / \\d+")))
        rule.onNodeWithTag("futaber-reading-pause").performClick()
        rule.waitUntil(5_000) { textOf("futaber-reading-state") == "一時停止中" }
        android.util.Log.i("FutaberB7bTest", "read-aloud: paused at " + textOf("futaber-reading-position"))
        // Paused: the chip stays and the button resumes.
        rule.onNodeWithTag("futaber-reading-chip").assertExists()
        rule.onNodeWithTag("futaber-reading-pause").assertTextContains("再開")
        rule.onNodeWithTag("futaber-reading-pause").performClick()
        rule.waitUntil(5_000) { absent("futaber-reading-chip") || textOf("futaber-reading-state") == "読み上げ中" }
        if (!absent("futaber-reading-stop")) rule.onNodeWithTag("futaber-reading-stop").performClick()
        rule.waitUntil(5_000) { absent("futaber-reading-chip") }
    }
}
