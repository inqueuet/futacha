package com.valoser.futacha

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry

/**
 * ふたばー風モードの縦の導線（板一覧 → カタログ → スレッド）を本番ホストで操作する。
 * 通信はチュートリアル板の同梱データだけを使い、他のモードの設定には触れない。
 */
class FutaberModeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val futaberKeys = listOf(
        "compat.futaber.ext.tree",
        "compat.futaber.ext.extract",
        "compat.futaber.ext.idTap",
        "compat.futaber.ext.autoScroll",
        "compat.futaber.ext.history",
        "compat.futaber.ext.mailPresets",
        "compat.futaber.ext.volumeKeys",
        "compat.futaber.ext.tabs",
        "compat.futaber.ext.video",
        "compat.thread.autoScrollPixel",
        "compat.thread.autoScrollSpeed",
        "compat.futaber.catalog.displayStyle",
        "compat.futaber.catalog.sort",
        "compat.futaber.theme",
        "compat.futaber.lastBoardId",
        "compat.futaber.tabs",
        "compat.futaber.drafts",
        "compat.futaber.favorites",
        "compat.futaber.seen",
        "compat.futaber.post.name",
        "compat.futaber.post.email",
        "compat.futaber.post.confirm"
    )
    private var originalDeleteKey: String = ""

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
            originalDeleteKey = app.appStateStore.lastUsedDeleteKey.first()
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.url.contains("zip.2chan.net/1/") } }
            app.appStateStore.setNgWords(emptyList())
            app.appStateStore.setLastUsedDeleteKey(originalDeleteKey)
            app.appStateStore.updateHistory { list -> list.filterNot { it.threadId == "1234567890" || it.threadId == "999001" } }
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
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

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun openTutorialCatalog() {
        // 初回は板一覧が自動で開く。
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-board-address")
        waitForTag("futaber-catalog-item")
    }

    @Test fun drawerSelectionOpensTheCatalogAndRemembersTheBoard() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-board-address").assertIsDisplayed()
        assertEquals("t", preference("compat.futaber.lastBoardId"))
        // 既定は横4のグリッド。
        rule.onNodeWithTag("futaber-catalog-grid").assertIsDisplayed()

        // 再生成しても、板一覧を開き直さずにカタログへ戻る。
        rule.activityRule.scenario.recreate()
        waitForTag("futaber-board-address")
        assertTrue(rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isEmpty())
    }

    @Test fun displayStyleAndSortAreAppliedAndPersisted() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-display-style").performClick()
        rule.onNodeWithTag("futaber-style-row1").performClick()
        waitForTag("futaber-catalog-list")
        assertEquals("row1", preference("compat.futaber.catalog.displayStyle"))
        assertTrue(rule.onAllNodesWithTag("futaber-catalog-grid").fetchSemanticsNodes().isEmpty())

        rule.onNodeWithTag("futaber-display-style").performClick()
        rule.onNodeWithTag("futaber-style-grid8").performClick()
        waitForTag("futaber-catalog-grid")
        assertEquals("grid8", preference("compat.futaber.catalog.displayStyle"))

        rule.onNodeWithTag("futaber-sort").performClick()
        rule.onNodeWithTag("futaber-sort-Momentum").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.sort") == "Momentum" }
        waitForTag("futaber-catalog-item")
    }

    @Test fun searchFiltersTheCatalogAndCancelRestoresTheList() {
        openTutorialCatalog()
        val before = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size
        assertTrue("catalog should list threads", before > 0)
        rule.onNodeWithTag("futaber-search").performClick()
        rule.onNodeWithTag("futaber-search-field").performTextInput("存在しない語句zzzz")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("「存在しない語句zzzz」に一致するスレッドはありません").assertIsDisplayed()
        rule.onNodeWithTag("futaber-search-cancel").performClick()
        waitForTag("futaber-catalog-item")
        rule.onNodeWithTag("futaber-refresh").assertIsDisplayed()
    }

    @Test fun openingAThreadShowsItsPostsRecordsHistoryAndBackReturns() {
        openTutorialCatalog()
        val startedAt = System.currentTimeMillis()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-thread-title")
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-list").assertIsDisplayed()

        // 初めから入っているサンプル履歴ではなく、今開いた行が先頭に記録される。
        rule.waitUntil(10_000) {
            runBlocking { app.appStateStore.history.first() }.firstOrNull()?.lastVisitedEpochMillis ?: 0L >= startedAt
        }
        val opened = runBlocking { app.appStateStore.history.first() }.first()
        assertTrue("opened thread is the newest history row", opened.lastVisitedEpochMillis >= startedAt)
        assertTrue("history keeps the thread address", opened.boardUrl.contains("/res/"))
        assertEquals("t", opened.boardId)

        rule.onNodeWithTag("futaber-thread-bottom").performClick()
        rule.onNodeWithTag("futaber-thread-refresh").performClick()
        waitForTag("futaber-post")

        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")
        assertTrue(rule.onAllNodesWithTag("futaber-thread-title").fetchSemanticsNodes().isEmpty())
    }

    @Test fun settingsChangesTheThemeAndListsEveryModeOfThisBuild() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-theme").performClick()
        rule.onNodeWithTag("futaber-settings-row-theme-dark").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.theme") == "dark" }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        // デバッグ版では3モードすべてが選べる。
        rule.onNodeWithTag("futaber-settings-row-mode").performScrollTo().performClick()
        rule.onNodeWithText("ふたちゃモード").assertIsDisplayed()
        rule.onNodeWithText("としあき(仮)モード").assertIsDisplayed()
        rule.onNodeWithText("ふたばー風モード").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()
        rule.onNode(hasTestTag("futaber-board-address")).assertIsDisplayed()
    }

    @Test fun longPressOnACatalogRowOpensTheActionSheetThatCancelsAndOffersCopyAndHistoryRemoval() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-copy-url").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-remove-history").assertIsDisplayed()
        capture("34-catalog-action-sheet")
        // キャンセルでシートだけが閉じ、カタログは残る（スレッドは開かない）。
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }
        rule.onAllNodesWithTag("futaber-catalog-item")[0].assertIsDisplayed()
        rule.onAllNodesWithTag("futaber-post").assertCountEquals(0)
        // URLをコピー：シートが閉じる。
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-copy-url").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aCatalogNgWordAddedInTheSettingsHidesTheThreadAndRemovingItBringsItBack() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].assertIsDisplayed()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-ng").performClick()
        rule.onNodeWithTag("futaber-settings-row-ng-catalog-words").performClick()
        rule.onNodeWithTag("futaber-ng-empty").assertIsDisplayed()
        rule.onNodeWithTag("futaber-ng-input").performTextInput("チュートリアル")
        rule.onNodeWithTag("futaber-ng-add").performClick()
        rule.onNodeWithTag("futaber-ng-delete").assertIsDisplayed()
        // 件数が行に出る。
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-detail-ng-catalog-words", useUnmergedTree = true).assertTextContains("1")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()
        // 題名にその語句を含むスレッドがカタログから消える。
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isEmpty() }

        // 消すと戻る。
        rule.onNodeWithTag("futaber-open-settings").performClick()
        rule.onNodeWithTag("futaber-settings-row-ng").performClick()
        rule.onNodeWithTag("futaber-settings-row-ng-catalog-words").performClick()
        rule.onNodeWithTag("futaber-ng-delete").performClick()
        rule.onNodeWithTag("futaber-ng-empty").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun settingsScreenSavesTheRowStyleTheNameDisplayTheViewerSwipeAndThePatrolSwitch() {
        runBlocking {
            app.compatibilityStore.savePreferences(
                listOf("catalog.rowWide", "titleNameAlways", "viewerSwipeClose").associate { "compat.futaber.$it" to null } +
                    ("compat.watcher.enabled" to null)
            )
        }
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")

        // カタログの行表示：やや広め。
        rule.onNodeWithTag("futaber-settings-row-catalog").performClick()
        rule.onNodeWithTag("futaber-settings-row-catalog-row-wide").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.rowWide") == "ON" }
        rule.onNodeWithTag("futaber-settings-back").performClick()

        // スレッド：題名と名前を常に表示。
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        rule.onNodeWithTag("futaber-settings-row-thread-title-always").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.titleNameAlways") == "ON" }
        rule.onNodeWithTag("futaber-settings-back").performClick()

        // ジェスチャー：ビューアの下スワイプ。
        rule.onNodeWithTag("futaber-settings-row-gesture").performClick()
        rule.onNodeWithTag("futaber-settings-gesture-note").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-switch-gesture-viewer-swipe").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.viewerSwipeClose") == "ON" }
        rule.onNodeWithTag("futaber-settings-back").performClick()

        // 通知を有効にする：他モードと共有のキー。切ると OFF、戻すと ON。
        rule.onNodeWithTag("futaber-settings-switch-patrol-enabled").performClick()
        rule.waitUntil(10_000) { preference("compat.watcher.enabled") == "OFF" }
        rule.onNodeWithTag("futaber-settings-switch-patrol-enabled").performClick()
        rule.waitUntil(10_000) { preference("compat.watcher.enabled") == "ON" }
        capture("33-settings-new-items")
    }

    @Test fun settingsScreenChangesTheFontSizeTheRepliesThresholdTheImageSizeAndTheCatalogScroll() {
        runBlocking {
            app.compatibilityStore.savePreferences(
                listOf("fontSize", "manyReplies", "smallImages", "catalog.scrollTop").associate { "compat.futaber.$it" to null }
            )
        }
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        capture("30-settings-root")
        // フォントサイズ：＋で15、保存され、数字が変わる。上限・下限で止まる。
        rule.onNodeWithTag("futaber-settings-plus-font-size").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.fontSize") == "15" }
        rule.onNodeWithTag("futaber-settings-value-font-size").assertTextContains("フォントサイズ = 15")
        rule.onNodeWithTag("futaber-settings-minus-font-size").performClick()
        rule.onNodeWithTag("futaber-settings-minus-font-size").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.fontSize") == "13" }

        // スレッド：返信の多いレスの基準値と画像サイズ。
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        capture("31-settings-thread")
        rule.onNodeWithTag("futaber-settings-plus-many-replies").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.manyReplies") == "4" }
        rule.onNodeWithTag("futaber-settings-row-thread-image-small").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.smallImages") == "ON" }
        rule.onNodeWithTag("futaber-settings-back").performClick()

        // カタログ：更新時のスクロールを切る。
        rule.onNodeWithTag("futaber-settings-row-catalog").performClick()
        rule.onNodeWithTag("futaber-settings-switch-catalog-scroll-top").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.scrollTop") == "OFF" }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()

        // 反映：スレッドを開くと、画像は小さく始まる設定になっている（メニューの項目が「元の大きさにする」）。
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-small-images").assertIsDisplayed()
        rule.onNodeWithContentDescription("画像を元の大きさにする").assertIsDisplayed()
    }

    @Test fun settingsScreenOpensPostingNgViewerAndNotificationsFromTheList() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-posting").performClick()
        waitForTag("futaber-post-name")
        rule.onNodeWithText("閉じる").performClick()
        // NG：カタログNG／レスNGの2グループ。件数つきの行から、リストの編集画面へ入って戻れる。
        rule.onNodeWithTag("futaber-settings-row-ng").performClick()
        rule.onNodeWithTag("futaber-settings-row-ng-catalog-words").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-row-ng-res-words").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-row-ng-res-headers").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-row-ng-res-words").performClick()
        rule.onNodeWithTag("futaber-ng-input").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-viewer").performClick()
        waitForTag("futaber-viewer-settings")
        capture("32-settings-viewer")
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer-settings").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-settings-row-notifications").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("巡回管理").fetchSemanticsNodes().isNotEmpty() }
        Espresso.pressBack()
        rule.onNodeWithTag("futaber-settings-done").performClick()
    }

    @Test fun settingsScreenOffersTheSharedPagesHelpAndTheStoreSwitches() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        listOf(
            "storage", "network", "background", "backup", "image-search", "media", "privacy", "ai",
            "update-check", "background-refresh", "watch-alert", "lightweight", "help", "changelog", "license", "version"
        ).forEach { id ->
            val tag = if (id in setOf("update-check", "background-refresh", "watch-alert", "lightweight")) {
                "futaber-settings-switch-$id"
            } else "futaber-settings-row-$id"
            rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        capture("40-settings-more-rows")

        // 共有の設定ページ：ふたちゃ・としあき(仮)と同じ値を開き、戻るとこの画面に戻る。
        rule.onNodeWithTag("futaber-settings-row-storage").performScrollTo().performClick()
        waitForTag("futaber-viewer-settings")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("画像キャッシュ上限").fetchSemanticsNodes().isNotEmpty() }
        capture("41-settings-storage")
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer-settings").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-settings-row-network").performScrollTo().performClick()
        waitForTag("futaber-viewer-settings")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("通信の軽量化").fetchSemanticsNodes().isNotEmpty() }
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer-settings").fetchSemanticsNodes().isEmpty() }

        // ヘルプ → 更新情報（ヘルプの中から）→ 戻る、ライセンス。
        rule.onNodeWithTag("futaber-settings-row-help").performScrollTo().performClick()
        waitForTag("futaber-viewer-settings")
        capture("42-settings-help")
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer-settings").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-settings-row-license").performScrollTo().performClick()
        waitForTag("futaber-viewer-settings")
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-viewer-settings").fetchSemanticsNodes().isEmpty() }

        // バージョンは文字で出る（押せない）。
        rule.onNodeWithTag("futaber-settings-detail-version").performScrollTo().assertIsDisplayed()

        // ふたちゃと共有のスイッチは、実際の保存値を変える（確かめたら元に戻す）。
        // Turning it on goes through the app's permission flow (as in ふたちゃ): with the notification permission given it is stored at once.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            rule.activity.packageName, android.Manifest.permission.POST_NOTIFICATIONS
        )
        val before = runBlocking { app.appStateStore.isWatchAlertEnabled.first() }
        rule.onNodeWithTag("futaber-settings-switch-watch-alert").performScrollTo().performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.isWatchAlertEnabled.first() } != before }
        rule.onNodeWithTag("futaber-settings-switch-watch-alert").performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.isWatchAlertEnabled.first() } == before }
        rule.onNodeWithTag("futaber-settings-done").performClick()
    }

    @Test fun catalogNgThreadIsUnavailableOnTheTutorialBoardAndSharedRulesHideAndAreTakenBackInTheSettings() {
        openTutorialCatalog()
        val count = { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size }
        val before = count()
        assertTrue(before > 0)
        // チュートリアル板は共有ストアに板が無いので、「スレッドをNG」は押せない（実在板では登録できる）。
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-ng-thread").assertIsDisplayed().assertIsNotEnabled()
        capture("43-catalog-ng-thread-disabled")
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }

        // 他のモードが「全板」で登録したNGスレッド（ここでは直接入れる）はカタログに効き、設定から外せる。
        val firstThread = runBlocking {
            com.valoser.futacha.shared.repo.mock.FakeBoardRepository()
                .getCatalogPage("https://www.example.com/t/futaba.php", com.valoser.futacha.shared.model.CatalogMode.Catalog)
                .items.first()
        }
        val value = firstThread.threadUrl.trim().lowercase()
        val ruleId = com.valoser.futacha.shared.compat.compatNgRuleId(com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_REFUSE, "*", value)
        runBlocking {
            assertTrue(app.compatibilityStore.upsertNgRule(
                com.valoser.futacha.shared.compat.CompatNgRule(
                    id = ruleId, kind = com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_REFUSE, scopeKey = "*",
                    normalizedValue = value, createdAtEpochMillis = 1L, memo = "テスト"
                )
            ))
        }
        try {
            rule.waitUntil(15_000) { count() == before - 1 }
            rule.onNodeWithTag("futaber-open-settings").performClick()
            waitForTag("futaber-settings")
            rule.onNodeWithTag("futaber-settings-row-ng").performClick()
            rule.onNodeWithTag("futaber-settings-detail-ng-catalog-threads", useUnmergedTree = true).assertTextEquals("1")
            rule.onNodeWithTag("futaber-settings-row-ng-catalog-threads").performClick()
            waitForTag("futaber-ng-delete")
            assertTrue(rule.onAllNodesWithTag("futaber-ng-input").fetchSemanticsNodes().isEmpty())
            capture("44-catalog-ng-threads")
            rule.onNodeWithTag("futaber-ng-delete").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-ng-empty").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-done").performClick()
            rule.waitUntil(15_000) { count() == before }
        } finally {
            runBlocking { app.compatibilityStore.deleteNgRule(ruleId) }
        }
    }

    @Test fun refreshLongPressOffersGoingBackAndTheDroppedThreads() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-refresh").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        // 読み込みが1回だけなので、戻る先も消えたスレもまだ無い（押せない）。
        rule.onNodeWithTag("futaber-action-catalog-back").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithTag("futaber-action-catalog-dropped").assertIsDisplayed().assertIsNotEnabled()
        capture("45-refresh-long-press")
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }
        // 短く押すと、これまでどおり更新する（シートは出ない）。
        rule.onNodeWithTag("futaber-refresh").performClick()
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty())
    }

    @Test fun operationMenuHasTheUrlShareBrowserAndUndoReloadEntries() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-share-url").assertIsDisplayed()
        rule.onNodeWithTag("futaber-menu-open-browser").assertIsDisplayed()
        // 読み込み直しで内容が変わっていないので、戻る先は無い。
        rule.onNodeWithTag("futaber-menu-undo-reload").assertIsDisplayed().assertIsNotEnabled()
        capture("46-operation-menu-more")
    }

    @Test fun theExtensionsAreAbsentFromTheScreensUntilTheyAreTurnedOn() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        // 既定では、IDはただの文字で、操作メニューにも拡張の項目は無い。
        assertTrue(rule.onAllNodesWithTag("futaber-id-tap").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        assertTrue(rule.onAllNodesWithTag("futaber-menu-extract").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("futaber-menu-auto-scroll").fetchSemanticsNodes().isEmpty())
        capture("48-menu-without-extensions")
    }

    @Test fun settingsTurnOnTheExtensionsAndTheThreadGetsTheirEntries() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-ext-tree")
        for (id in listOf("ext-tree", "ext-extract", "ext-id-tap", "ext-volume-keys", "ext-auto-scroll", "ext-history", "ext-mail-presets", "ext-tabs", "ext-video")) {
            rule.onNodeWithTag("futaber-settings-switch-$id").performScrollTo()
            rule.onNodeWithTag("futaber-settings-switch-$id").performClick()
        }
        assertEquals("ON", preference("compat.futaber.ext.tree"))
        assertEquals("ON", preference("compat.futaber.ext.extract"))
        assertEquals("ON", preference("compat.futaber.ext.idTap"))
        assertEquals("ON", preference("compat.futaber.ext.autoScroll"))
        assertEquals("ON", preference("compat.futaber.ext.history"))
        assertEquals("ON", preference("compat.futaber.ext.mailPresets"))
        assertEquals("ON", preference("compat.futaber.ext.volumeKeys"))
        assertEquals("ON", preference("compat.futaber.ext.tabs"))
        // 動画ビューアは最初からオンなので、押すとオフになる。
        assertEquals("OFF", preference("compat.futaber.ext.video"))
        // オートスクロールを入れると、量と間隔の行が出る。
        waitForTag("futaber-settings-row-auto-scroll-pixel")
        rule.onNodeWithTag("futaber-settings-plus-auto-scroll-pixel").performClick()
        waitForTag("futaber-settings-value-auto-scroll-pixel")
        assertEquals("6", preference("compat.thread.autoScrollPixel"))
        capture("49-extension-settings")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        // 書き込み設定のメール欄に、プリセットのボタンが出る。
        rule.onNodeWithTag("futaber-settings-row-posting").performScrollTo().performClick()
        waitForTag("futaber-post-mail-preset-sage")
        rule.onNodeWithTag("futaber-post-mail-preset-sage").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.post.email") == "sage" }
        rule.onNodeWithTag("futaber-post-mail-preset-IP表示").assertIsDisplayed()
        capture("53-mail-presets")
        rule.onNodeWithText("閉じる").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()
        waitForTag("futaber-catalog-item")

        // 他の試験が立てたスレッド（IDの無いレス）が先頭にあることがあるので、IDのあるスレッドを探して開く。
        var opened = false
        for (index in 0 until rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().size.coerceAtMost(4)) {
            rule.onAllNodesWithTag("futaber-catalog-item")[index].performClick()
            waitForTag("futaber-post")
            // ツリー表示でもレスは並ぶ（引用が無いレスは字下げなし）。
            assertTrue(rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isNotEmpty())
            val hasIds = runCatching { rule.waitUntil(4_000) { rule.onAllNodesWithTag("futaber-id-tap").fetchSemanticsNodes().isNotEmpty() } }.isSuccess
            if (hasIds) { opened = true; break }
            rule.onNodeWithTag("futaber-thread-back").performClick()
            waitForTag("futaber-catalog-item")
        }
        assertTrue("A thread with poster IDs was not found", opened)

        // IDをタップすると、そのIDのレスだけに絞られ、チップで解除できる。
        waitForTag("futaber-id-tap")
        // 前回の読み位置まで動いた直後は、先頭のレスが上のバーに隠れかけていることがある。画面の中ほどのIDを押す。
        rule.waitForIdle()
        val listBounds = rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().boundsInRoot
        val fullyShown = rule.onAllNodesWithTag("futaber-id-tap").fetchSemanticsNodes().indexOfFirst {
            it.boundsInRoot.top >= listBounds.top + 8f && it.boundsInRoot.bottom <= listBounds.bottom - 8f
        }
        assertTrue("No poster ID is fully on screen", fullyShown >= 0)
        rule.onAllNodesWithTag("futaber-id-tap")[fullyShown].performClick()
        waitForTag("futaber-filter-chip")
        capture("50-same-id")
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        assertTrue(rule.onAllNodesWithTag("futaber-filter-chip").fetchSemanticsNodes().isEmpty())

        // 操作メニューの「レスを抽出」。そうだねが多いレスに絞って、解除する。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-extract")
        rule.onNodeWithTag("futaber-menu-extract").performClick()
        waitForTag("futaber-action-extract-saidane")
        capture("51-extract-sheet")
        rule.onNodeWithTag("futaber-action-extract-saidane").performClick()
        waitForTag("futaber-filter-chip")
        rule.onNodeWithText("そうだねが多いレス", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("futaber-filter-chip").performClick()

        // 管理パネルの履歴に、検索・並べ替え・一括更新の帯が出る。
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-history-band")
        rule.onNodeWithTag("futaber-history-refresh").assertIsDisplayed()
        rule.onNodeWithTag("futaber-history-sort").performClick()
        rule.onNodeWithTag("futaber-history-search").performTextInput("存在しないスレタイ")
        waitForTag("futaber-history-status")
        rule.onNodeWithText("条件に合う履歴はありません").assertIsDisplayed()
        capture("54-history-band")
        rule.onNodeWithTag("futaber-history-search").performTextClearance()
        waitForTag("futaber-history-row")
        rule.onNodeWithTag("futaber-manage").performClick()

        // オートスクロールは、開始するとチップが出て、チップのタップで止まる。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-menu-auto-scroll")
        rule.onNodeWithTag("futaber-menu-auto-scroll").performClick()
        waitForTag("futaber-auto-scroll-chip")
        capture("52-auto-scroll")
        rule.onNodeWithTag("futaber-auto-scroll-chip").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-auto-scroll-chip").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theBottomBarButtonsKeepTheirPlacesWithTheRoundedIcons() {
        openTutorialCatalog()
        // 丸みのあるアイコンでも、下部バーのボタンは同じ場所にあり、押せる。
        rule.onNodeWithTag("futaber-display-style").assertIsDisplayed()
        rule.onNodeWithTag("futaber-sort").assertIsDisplayed()
        rule.onNodeWithTag("futaber-search").assertIsDisplayed()
        rule.onNodeWithTag("futaber-manage").assertIsDisplayed()
        rule.onNodeWithTag("futaber-refresh").assertIsDisplayed()
        capture("65-catalog-rounded-icons")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-gallery").assertIsDisplayed()
        rule.onNodeWithTag("futaber-thread-bottom").assertIsDisplayed()
        rule.onNodeWithTag("futaber-thread-menu").assertIsDisplayed()
        rule.onNodeWithTag("futaber-thread-refresh").assertIsDisplayed()
        capture("66-thread-rounded-icons")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        capture("67-menu-rounded-icons")
    }

    @Test fun catalogSearchOffersTheArchiveSearchForTheTypedWord() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-search").performClick()
        waitForTag("futaber-search-field")
        // 語が空の間は、過去ログ検索の入口は出ない。
        assertTrue(rule.onAllNodesWithTag("futaber-archive-search").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("futaber-search-field").performTextInput("テスト")
        waitForTag("futaber-archive-search")
        rule.onNodeWithText("「テスト」を過去ログから検索").assertIsDisplayed()
        capture("61-archive-pill")
        rule.onNodeWithTag("futaber-archive-search").performClick()
        waitForTag("futaber-archive-sheet")
        // 結果は通信しだい（検索中・一覧・見つからない・失敗のどれか）。見出しと注意書きは必ず出る。
        rule.onNodeWithText("過去ログ検索「テスト」").assertIsDisplayed()
        capture("62-archive-sheet")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-archive-sheet").fetchSemanticsNodes().isEmpty() }
        // 閉じても検索の語は残り、入口がまた出る。
        waitForTag("futaber-archive-search")
    }

    @Test fun aClosedTabComesBackFromTheTabSheetWhenTheExtensionIsOn() {
        runBlocking {
            app.compatibilityStore.savePreferences(mapOf("compat.futaber.ext.tabs" to "ON", "compat.futaber.ext.history" to "ON"))
        }
        openTutorialCatalog()
        // チュートリアル板のスレッドは1つだけなので、2つ目は履歴に作り、2つをタブにする。
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val first = runBlocking { app.appStateStore.history.first().first() }
        runBlocking {
            app.appStateStore.updateHistory { list ->
                list + first.copy(threadId = "999001", title = "もう1つのスレッド", boardUrl = first.boardUrl.replace(first.threadId, "999001"))
            }
        }
        runBlocking {
            app.compatibilityStore.savePreference(
                "compat.futaber.tabs", "[\"${first.boardId}\\n${first.threadId}\",\"${first.boardId}\\n999001\"]"
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 2 }
        // 何も閉じていない間は、元に戻す項目は押せない。
        rule.onAllNodesWithTag("futaber-tab")[1].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-restore")
        rule.onNodeWithTag("futaber-action-tab-restore").assertIsNotEnabled()
        rule.onNodeWithTag("futaber-action-tab-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }
        // 1つ閉じると、残ったタブのシートで元に戻せる。
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-remove")
        rule.onNodeWithTag("futaber-action-tab-remove").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 1 }
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-tab-restore")
        rule.onNodeWithTag("futaber-action-tab-restore").assertIsEnabled()
        capture("59-tab-restore")
        rule.onNodeWithTag("futaber-action-tab-restore").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 2 }
    }

    @Test fun historyRowsCarryMarksWhenTheHistoryExtensionIsOn() {
        runBlocking { app.compatibilityStore.savePreferences(mapOf("compat.futaber.ext.history" to "ON")) }
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val thread = runBlocking { app.appStateStore.history.first().first() }
        runBlocking {
            app.appStateStore.updateHistory { list ->
                list.map { if (it.threadId == thread.threadId) it.copy(hasSelfPost = true, hasAutoSave = true) else it }
            }
        }
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-history-row")
        rule.onNodeWithText("書き込み済み・保存済み", substring = true).assertIsDisplayed()
        capture("60-history-marks")
        runBlocking { app.appStateStore.updateHistory { list -> list.map { it.copy(hasSelfPost = false, hasAutoSave = false) } } }
    }

    @Test fun aPicturesLongPressOpensItsOwnMenuWithImageSearchAndImageNg() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("futaber-post-image"))
        // 画像の長押しは、レスのメニューではなく、画像だけのメニューを開く。
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-image-search")
        rule.onNodeWithTag("futaber-action-image-ng").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("futaber-action-quote").fetchSemanticsNodes().isEmpty())
        capture("55-image-menu")
        // 画像検索：検索先を選ぶダイアログが開く。
        rule.onNodeWithTag("futaber-action-image-search").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("画像検索").fetchSemanticsNodes().isNotEmpty() }
        capture("56-image-search")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("画像検索").fetchSemanticsNodes().isEmpty() }
        // 画像NG：登録のダイアログが開く。
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-image-ng")
        rule.onNodeWithTag("futaber-action-image-ng").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("NG画像に登録").fetchSemanticsNodes().isNotEmpty() }
        capture("57-image-ng-register")
        androidx.test.espresso.Espresso.pressBack()
        // 画像以外の部分の長押しは、これまでどおりレスのメニュー。
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-action-quote")
        assertTrue(rule.onAllNodesWithTag("futaber-action-image-search").fetchSemanticsNodes().isEmpty())
    }

    @Test fun imageNgRulesAreListedInTheSettingsAndCanBeTakenBack() {
        openTutorialCatalog()
        val hash = "0123456789abcdef"
        val kind = com.valoser.futacha.shared.compat.CompatNgKind.THREAD_IMAGE_PHASH
        val ruleId = com.valoser.futacha.shared.compat.compatNgRuleId(kind, "*", hash)
        runBlocking {
            assertTrue(app.compatibilityStore.upsertNgRule(
                com.valoser.futacha.shared.compat.CompatNgRule(
                    id = ruleId, kind = kind, scopeKey = "*", normalizedValue = hash, createdAtEpochMillis = 1L,
                    imageUrl = "https://may.2chan.net/b/src/1234.jpg", memo = "テスト画像"
                )
            ))
        }
        try {
            rule.onNodeWithTag("futaber-open-settings").performClick()
            waitForTag("futaber-settings")
            rule.onNodeWithTag("futaber-settings-row-ng").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithTag("futaber-settings-detail-ng-res-images", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithTag("futaber-settings-detail-ng-res-images", useUnmergedTree = true).assertTextEquals("1")
            rule.onNodeWithTag("futaber-settings-row-ng-res-images").performClick()
            waitForTag("futaber-ng-delete")
            assertTrue(rule.onAllNodesWithTag("futaber-ng-input").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("テスト画像", substring = true).assertIsDisplayed()
            capture("58-image-ng-list")
            rule.onNodeWithTag("futaber-ng-delete").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-ng-empty").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-done").performClick()
        } finally {
            runBlocking { app.compatibilityStore.deleteNgRule(ruleId) }
        }
    }

    @Test fun postLongPressOffersNgRegisterThatOpensTheNgDialogWithThePosterReady() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-ng-register").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-ng-register").performClick()
        waitForTag("futaber-ng-input")
        // 名前・ID等の一覧が開き、そのレスのIDか名前が入力済み（既定の名前しか無ければ、ワードの一覧が空欄で開く）。
        val typed = rule.onNodeWithTag("futaber-ng-input").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text
        val headerSection = rule.onAllNodesWithTag("futaber-ng-section-Headers").fetchSemanticsNodes().isNotEmpty()
        assertTrue(headerSection)
        capture("47-ng-register-dialog:" + typed.length)
        rule.onNodeWithText("閉じる").performClick()
    }

    @Test fun boardsCanBeRenamedByTappingTheirNameWhileEditingAndOfferBulkAdd() {
        openTutorialCatalog()
        fun names() = runBlocking { app.appStateStore.boards.first().map { it.name } }
        val before = names()
        rule.onNodeWithTag("futaber-open-drawer").performClick()
        waitForTag("futaber-board-edit")
        rule.onNodeWithTag("futaber-board-edit").performClick()
        waitForTag("futaber-board-add")
        // 編集中に名前をタップすると、板は開かず改名のダイアログが出る。
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-rename-board-name")
        rule.onNodeWithTag("futaber-rename-board-name").performTextClearance()
        rule.onNodeWithTag("futaber-rename-board-name").performTextInput("改名した板")
        rule.onNodeWithTag("futaber-rename-board-submit").performClick()
        rule.waitUntil(15_000) { names().first() == "改名した板" }
        capture("48-board-renamed")
        // 元の名前へ戻す。
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-rename-board-name")
        rule.onNodeWithTag("futaber-rename-board-name").performTextClearance()
        rule.onNodeWithTag("futaber-rename-board-name").performTextInput(before.first())
        rule.onNodeWithTag("futaber-rename-board-submit").performClick()
        rule.waitUntil(15_000) { names() == before }

        // 追加ダイアログに「板一覧から一括追加」の切替がある（取得は行わない）。
        rule.onNodeWithTag("futaber-board-add").performClick()
        waitForTag("futaber-add-board-bulk-toggle")
        rule.onNodeWithTag("futaber-add-board-bulk-toggle").performClick()
        waitForTag("futaber-bulk-note")
        rule.onNodeWithTag("futaber-add-board-bulk-submit").assertIsDisplayed()
        capture("49-board-bulk-add")
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithTag("futaber-board-edit").performClick()
    }

    @Test fun settingsOfferTheStartUpLockCheckAndTheAppIconChoice() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        // 起動ロック：入力の検証だけを確かめる（実際には設定しない）。
        rule.onNodeWithTag("futaber-settings-row-app-lock").performScrollTo().performClick()
        waitForTag("futaber-lock-password")
        rule.onNodeWithTag("futaber-lock-password").performTextInput("abc")
        rule.onNodeWithTag("futaber-lock-confirmation").performTextInput("abc")
        rule.onNodeWithTag("futaber-lock-save").performClick()
        rule.onNodeWithText("4文字以上で入力してください。").assertIsDisplayed()
        rule.onNodeWithTag("futaber-lock-password").performTextInput("d")
        rule.onNodeWithTag("futaber-lock-confirmation").performTextInput("x")
        rule.onNodeWithTag("futaber-lock-save").performClick()
        rule.onNodeWithText("確認用パスワードが一致しません。").assertIsDisplayed()
        capture("50-app-lock-dialog")
        rule.onNodeWithText("キャンセル").performClick()
        assertTrue(runBlocking { app.appStateStore.appLockPasswordHash.first() }.isNullOrBlank())

        // アプリアイコン：選ぶと保存され、戻すと元へ戻る。
        val original = runBlocking { app.appStateStore.appIconVariant.first() }
        rule.onNodeWithTag("futaber-settings-row-app-icon").performScrollTo().performClick()
        try {
            rule.onNodeWithTag("futaber-settings-row-app-icon-Classic").performClick()
            rule.waitUntil(10_000) { runBlocking { app.appStateStore.appIconVariant.first() } == com.valoser.futacha.shared.model.AppIconVariant.Classic }
            capture("51-app-icon")
        } finally {
            runBlocking { app.appStateStore.setAppIconVariant(original) }
        }
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.appIconVariant.first() } == original }
    }

    @Test fun everyIconButtonHasANameForAssistiveTechnology() {
        openTutorialCatalog()
        listOf(
            "板一覧を開く", "設定", "カタログを検索", "カタログを更新"
        ).forEach { rule.onNode(hasContentDescriptionExactly(it)).assertIsDisplayed() }
    }

    @Test fun boardListCanBeEditedFromTheDrawer() {
        openTutorialCatalog()
        fun boardUrls() = runBlocking { app.appStateStore.boards.first().map { it.url } }
        val before = boardUrls()
        rule.onNodeWithTag("futaber-open-drawer").performClick()
        waitForTag("futaber-board-edit")
        rule.onNodeWithTag("futaber-board-edit").performClick()
        // 編集中は板を開かない。追加ダイアログは不正な入力を受け付けない。
        waitForTag("futaber-board-add")
        capture("10-drawer-edit")
        rule.onNodeWithTag("futaber-board-add").performClick()
        rule.onNodeWithTag("futaber-add-board-url").performTextInput("zip.2chan.net/1/")
        rule.onNodeWithText("http:// もしくは https:// から始まるURLを入力してください").assertIsDisplayed()
        capture("11-add-board-invalid")
        rule.onNodeWithTag("futaber-add-board-url").performTextClearance()
        rule.onNodeWithTag("futaber-add-board-url").performTextInput("https://zip.2chan.net/1/")
        rule.onNodeWithTag("futaber-add-board-submit").performClick()
        rule.waitUntil(15_000) { boardUrls().size == before.size + 1 }
        assertEquals("https://zip.2chan.net/1/futaba.php", boardUrls().last())

        // 上へ移動 → 削除（確認ダイアログあり）で元の一覧へ戻る。
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-board-up").fetchSemanticsNodes().size >= 2 }
        rule.onAllNodesWithTag("futaber-board-up")[1].performClick()
        rule.waitUntil(15_000) { boardUrls().first() == "https://zip.2chan.net/1/futaba.php" }
        rule.onAllNodesWithTag("futaber-board-delete")[0].performClick()
        rule.onNodeWithTag("futaber-board-delete-confirm").performClick()
        rule.waitUntil(15_000) { boardUrls() == before }
        rule.onNodeWithTag("futaber-board-edit").performClick()
    }

    @Test fun quoteAndReplyBubblesOpenNestAndCloseOneOrAll() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        // 同梱スレッドには引用と返信がある。返信数をタップすると返信先の吹き出しが開く。
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-reply-count"))
        rule.onAllNodesWithTag("futaber-reply-count")[0].performClick()
        capture("12-after-click")
        waitForTag("futaber-quote-card")
        capture("12-reply-bubble")
        // 開いている間、下部バーは「閉じる」だけになる。
        rule.onNodeWithTag("futaber-quote-close-all").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithTag("futaber-thread-refresh").fetchSemanticsNodes().isEmpty())
        // 吹き出しの中の引用から、さらに1枚開く。背面のスレッド本文ではなく、吹き出し内の行を選ぶ。
        val inCard = hasTestTag("futaber-quote-line") and hasAnyAncestor(hasTestTag("futaber-quote-card"))
        assertTrue("bubble has a quote line", rule.onAllNodes(inCard).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodes(inCard)[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().isNotEmpty() }
        capture("13-nested-bubble")
        // 1枚閉じると、元の吹き出しに戻る。
        rule.onNodeWithTag("futaber-quote-close-one").performClick()
        waitForTag("futaber-quote-card")
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().isEmpty() }
        waitForTag("futaber-thread-refresh")
        // 引用行のタップでも開き、外側のタップで1枚閉じる。
        runCatching { rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-quote-line")) }
        if (rule.onAllNodesWithTag("futaber-quote-line").fetchSemanticsNodes().isNotEmpty()) {
            rule.onAllNodesWithTag("futaber-quote-line")[0].performClick()
            waitForTag("futaber-quote-card")
            capture("14-source-bubble")
            rule.onNodeWithTag("futaber-quote-scrim").performClick()
        }
    }

    @Test fun tabsAreRegisteredByHandAndManagedFromThePanel() {
        openTutorialCatalog()
        // 開いただけではタブにならない。
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        assertTrue(rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().isEmpty())
        assertEquals(null, preference("compat.futaber.tabs")?.takeIf { it.isNotBlank() && it != "[]" })

        // パネルボタンの長押しで登録すると、帯が出る。メニューの項目でも外れる。
        rule.onNodeWithTag("futaber-manage").performTouchInput { longClick() }
        waitForTag("futaber-tab")
        capture("15-tab-strip")
        toggleTabFromMenu()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().isEmpty() }
        toggleTabFromMenu()
        waitForTag("futaber-tab")

        // カタログへ戻っても帯は残り、再生成後も残る。
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")
        waitForTag("futaber-tab")
        rule.activityRule.scenario.recreate()
        waitForTag("futaber-tab")

        // 管理画面：履歴とタブの一覧。
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-history-row")
        capture("16-manage-history")
        rule.onNodeWithTag("futaber-manage-category-Tabs").performClick()
        waitForTag("futaber-tab-row")
        capture("17-manage-tabs")
        rule.onNodeWithTag("futaber-tabs-hint").assertIsDisplayed()
        // 帯のタブからスレッドを開き直せる。
        rule.onNodeWithTag("futaber-manage-close").performClick()
        rule.onAllNodesWithTag("futaber-tab")[0].performClick()
        waitForTag("futaber-post")

        // 管理画面のタブは、行の右端の×で閉じる（編集モードは無い）。
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-Tabs").performClick()
        assertTrue(rule.onAllNodesWithTag("futaber-manage-edit").fetchSemanticsNodes().isEmpty())
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab-row").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-manage-close").performClick()
        assertTrue(rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().isEmpty())

        // 帯のタブの長押しはアクションシートを開く。キャンセルでは何も消えず、「タブを削除」で消える。
        rule.onNodeWithTag("futaber-manage").performTouchInput { longClick() }
        waitForTag("futaber-tab")
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-tab-remove-all").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-tab-cancel").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-tab-remove").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun aThreadIsStarredFromTheMenuListedInThePanelAndSurvivesClearingTheHistory() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val stored = { preference("compat.futaber.favorites")?.takeIf { it.isNotBlank() && it != "[]" } }
        assertEquals(null, stored())

        // メニューで追加 → ラベルが「外す」へ変わる。他モードのタブには何も作らない。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-favorite").performClick()
        rule.waitUntil(10_000) { stored() != null }
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithContentDescription("お気に入り解除").assertIsDisplayed()
        capture("22-favorite-menu")
        dismissOperationMenu()
        assertTrue(runBlocking { app.compatibilityStore.tabs.first() }.none { it.threadNo == "1364612020" })

        // 管理画面のお気に入り。履歴を消しても残る。
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        waitForTag("futaber-favorite-row")
        capture("23-manage-favorites")
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
        waitForTag("futaber-history-row")
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-history-row").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        waitForTag("futaber-favorite-row")

        // お気に入りから開き直せる。
        rule.onNodeWithTag("futaber-manage-close").performClick()
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        rule.onAllNodesWithTag("futaber-favorite-row")[0].performClick()
        waitForTag("futaber-post")

        // 編集で外すと、メニューの表示も「追加」へ戻る。
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-favorite-row").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-manage-close").performClick()
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithContentDescription("お気に入りに追加").assertIsDisplayed()
        assertEquals(null, stored())
    }

    @Test fun patrolResultsAreListedInTheNotificationsPanelOpenedAndRemoved() {
        val watcher = com.valoser.futacha.shared.compat.CompatWatcherRepository(app.compatibilityStore)
        val boardUrl = "https://www.example.com/t/futaba.php"
        val now = System.currentTimeMillis()
        runBlocking {
            watcher.deleteAll()
            watcher.recordAll(
                listOf(
                    com.valoser.futacha.shared.compat.CompatWatchMatch(
                        history = com.valoser.futacha.shared.compat.CompatHistoryEntry(
                            canonicalUrl = "https://www.example.com/t/res/1364612020.htm",
                            originalUrl = "https://www.example.com/t/res/1364612020.htm",
                            boardKey = com.valoser.futacha.shared.compat.compatBoardKey(boardUrl),
                            boardName = "チュートリアル＠ふたちゃ", threadNo = "1364612020", title = "巡回で見つけたスレ",
                            replyCount = 21, contentUpdatedAtEpochMillis = now
                        ),
                        isNew = true, keyword = "テスト"
                    )
                )
            )
        }
        try {
            openTutorialCatalog()
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-Notifications").performClick()
            waitForTag("futaber-notification-row")
            rule.onNodeWithText("巡回で見つけたスレ").assertIsDisplayed()
            rule.onNodeWithText("「テスト」", substring = true).assertIsDisplayed()
            capture("24-manage-notifications")

            // キーワード管理は共有の巡回管理で、結果の場所は「通知」と案内される。
            rule.onNodeWithTag("futaber-watch-manage").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("巡回管理").fetchSemanticsNodes().isNotEmpty() }
            // 説明文と手順の2か所にある。他モード向けの「ドロワー」案内は出ない。
            assertEquals(2, rule.onAllNodesWithText("管理画面の「通知」", substring = true).fetchSemanticsNodes().size)
            assertTrue(rule.onAllNodesWithText("ドロワー", substring = true).fetchSemanticsNodes().isEmpty())
            capture("25-watcher-manager")
            Espresso.pressBack()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("巡回管理").fetchSemanticsNodes().isEmpty() }

            // 開くとスレッドが表示される。
            rule.onAllNodesWithTag("futaber-notification-row")[0].performClick()
            waitForTag("futaber-post")

            // 編集で1件削除すると、保存済みの結果からも消える。
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-Notifications").performClick()
            rule.onNodeWithTag("futaber-manage-edit").performClick()
            rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-notification-row").fetchSemanticsNodes().isEmpty() }
            assertTrue(runBlocking { watcher.load(System.currentTimeMillis()) }.isEmpty())
        } finally {
            runBlocking { watcher.deleteAll() }
        }
    }

    @Test fun aThreadIsSavedListedInTheBoxOpenedAsACopyAndDeleted() {
        // 保存先は端末内のアプリ専用フォルダにして、フォルダ選択画面を出さない。
        val saveDir = java.io.File(app.getExternalFilesDir(null), "futaber-save-test").apply { deleteRecursively(); mkdirs() }
        runBlocking { app.compatibilityStore.savePreference("compat.storage.dummyDownloadDir", saveDir.absolutePath) }
        try {
            openTutorialCatalog()
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
            waitForTag("futaber-post")

            // 操作メニュー → スレッドを保存 → 保存形式「HTMLのみ」（通信しない）。
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            waitForTag("futaber-operation-menu")
            rule.onNodeWithTag("futaber-menu-save").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("保存形式").fetchSemanticsNodes().isNotEmpty() }
            capture("26-save-format")
            rule.onNodeWithText("HTMLのみ").performClick()
            rule.waitUntil(60_000) { rule.onAllNodesWithText("保存先:", substring = true).fetchSemanticsNodes().isNotEmpty() }
            capture("27-save-done")
            rule.onNodeWithText("閉じる").performClick()

            // 保存箱に並び、保存元の履歴を消しても残る。
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
            waitForTag("futaber-saved-row")
            capture("28-manage-saved-box")

            // 開くと保存したコピーとして表示され、履歴・タブは増えない。
            val historyBefore = runBlocking { app.appStateStore.history.first() }.size
            rule.onAllNodesWithTag("futaber-saved-row")[0].performClick()
            waitForTag("futaber-offline-notice")
            waitForTag("futaber-post")
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            waitForTag("futaber-operation-menu")
            rule.onNodeWithTag("futaber-menu-tab").assertIsNotEnabled()
            dismissOperationMenu()
            capture("29-saved-copy")
            assertEquals(historyBefore, runBlocking { app.appStateStore.history.first() }.size)
            rule.onNodeWithContentDescription("カタログへ戻る").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-offline-notice").fetchSemanticsNodes().isEmpty() }

            // 編集で削除すると、保存済みの索引からも消える。
            rule.onNodeWithTag("futaber-manage").performClick()
            rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
            waitForTag("futaber-saved-row")
            rule.onNodeWithTag("futaber-manage-edit").performClick()
            rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
            rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-saved-row").fetchSemanticsNodes().isEmpty() }
        } finally {
            runBlocking { app.compatibilityStore.savePreference("compat.storage.dummyDownloadDir", "") }
            saveDir.deleteRecursively()
        }
    }

    /** チュートリアル板（同梱の模擬データと同梱の画像）だけを使い、外部へは通信しない。 */
    @Test fun aThreadIsSavedAsMhtListedOpenedInTheAppAndDeleted() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")

        // 操作メニュー → MHTで保存 → 「サムネイルのみ」 → 完了の案内（共有／とじる）。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-mht-save").performClick()
        waitForTag("futaber-mht-choose-thumbs")
        rule.onNodeWithTag("futaber-mht-choose-full").assertIsDisplayed()
        rule.onNodeWithTag("futaber-mht-choose-cancel").assertIsDisplayed()
        capture("36-mht-choose")
        rule.onNodeWithTag("futaber-mht-choose-thumbs").performClick()
        waitForTag("futaber-mht-share-file")
        rule.onNodeWithTag("futaber-mht-close").assertIsDisplayed()
        capture("37-mht-done")
        rule.onNodeWithTag("futaber-mht-close").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-mht-dialog").fetchSemanticsNodes().isEmpty() }

        // 保存箱の先頭に「MHTファイル」として並び、開くと保存したコピーとして表示される。
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
        waitForTag("futaber-mht-row")
        rule.onNodeWithTag("futaber-mht-summary").assertTextContains("1項目", substring = true)
        capture("38-mht-box")
        val historyBefore = runBlocking { app.appStateStore.history.first() }.size
        rule.onAllNodesWithTag("futaber-mht-row")[0].performClick()
        waitForTag("futaber-offline-notice")
        waitForTag("futaber-post")
        capture("39-mht-opened")
        assertEquals(historyBefore, runBlocking { app.appStateStore.history.first() }.size)
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-offline-notice").fetchSemanticsNodes().isEmpty() }

        // 編集で削除すると一覧から消える。
        rule.onNodeWithTag("futaber-manage").performClick()
        rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
        waitForTag("futaber-mht-row")
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-mht-row").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun leftEdgeSwipeOpensTheBoardListFromAThreadOnly() {
        openTutorialCatalog()
        // カタログでは左端スワイプを奪わない（システムの戻るに任せる）。
        assertEquals(null, com.valoser.futacha.shared.compat.CompatBackGestureBus.captureDrawerRequest())
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        // スレッドでは、システムが左端のスワイプを確定した時点で板一覧が開く。
        rule.waitUntil(10_000) { com.valoser.futacha.shared.compat.CompatBackGestureBus.captureDrawerRequest() != null }
        val commit = com.valoser.futacha.shared.compat.CompatBackGestureBus.captureDrawerRequest()!!
        rule.runOnUiThread { assertTrue(commit()) }
        waitForTag("futaber-drawer")
        // 開いている間は登録が外れ、閉じるとスレッドへ戻る。
        assertEquals(null, com.valoser.futacha.shared.compat.CompatBackGestureBus.captureDrawerRequest())
        rule.onNodeWithTag("futaber-drawer-scrim").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-drawer").fetchSemanticsNodes().isEmpty() }
        waitForTag("futaber-post")
        // 吹き出しが開いている間も奪わない。
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-reply-count"))
        rule.onAllNodesWithTag("futaber-reply-count")[0].performClick()
        waitForTag("futaber-quote-card")
        assertEquals(null, com.valoser.futacha.shared.compat.CompatBackGestureBus.captureDrawerRequest())
    }

    @Test fun operationMenuSearchesFiltersCopiesTheUrlAndJumpsToNewPosts() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val fullCount = { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size }

        // 検索：一致する件数が出て、メニューを閉じても絞り込みの表示が残り、解除で全件へ戻る。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        capture("18-operation-menu")
        rule.onNodeWithTag("futaber-menu-search").performTextInput("テスト10_2")
        // The result is applied after a short wait for further typing.
        waitForTag("futaber-menu-result-count")
        rule.onNodeWithTag("futaber-menu-result-count").assertIsDisplayed()
        // The keyboard lifts the menu (it is not covered by it); the search key puts it away and shows the result.
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitForTag("futaber-filter-chip")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        rule.waitUntil(10_000) { fullCount() > 1 }
        assertTrue(rule.onAllNodesWithTag("futaber-filter-chip").fetchSemanticsNodes().isEmpty())

        // 返信が多いレスだけを表示する（トグル）。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-many-replies").performClick()
        waitForTag("futaber-filter-chip")
        rule.waitUntil(10_000) { fullCount() in 1..3 }
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        rule.waitUntil(10_000) { fullCount() > 3 }

        // URLをコピー：スレッド自身のアドレスがクリップボードに入る。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-copy-url").performClick()
        rule.waitUntil(10_000) {
            var text = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
                text = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }
            text.contains("/res/")
        }
    }

    @Test fun readingPositionIsRestoredAndNewPostsAreFoundSinceTheLastVisit() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        // 初回はまだ「新着」はなく、メニューの項目も押せない。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-new").assertIsNotEnabled()
        dismissOperationMenu()

        // 読み位置：途中までスクロールして戻ると、履歴に位置が保存される。
        rule.onNodeWithTag("futaber-thread-list").performScrollToIndex(8)
        rule.waitUntil(10_000) {
            (runBlocking { app.appStateStore.history.first() }.firstOrNull { it.boardId == "t" && it.threadId == opened() }
                ?.lastReadItemIndex ?: 0) >= 5
        }
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")

        // 前回見た件数を減らして開き直すと、増えた分が新着になり、OPには戻らず読み位置から始まる。
        // 「最後に見た件数」はこのモード専用の保存値（共有の履歴の件数は背景更新などで動くため使わない）。
        runBlocking {
            // 形式: JSON文字列配列で、各要素は「板ID\nスレID\n件数」。
            app.compatibilityStore.savePreference("compat.futaber.seen", "[\"t\\n${opened()}\\n6\"]")
        }
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        assertTrue(rule.onAllNodesWithText("ここは操作を試すサンプル板です。画像はアプリに同梱した見本です。").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-new").assertIsEnabled()
        rule.onNodeWithTag("futaber-menu-new").performClick()
        waitForTag("futaber-new-marker")
        capture("19-new-marker")
    }

    private fun opened(): String =
        runBlocking { app.appStateStore.history.first() }.first { it.boardId == "t" && it.lastVisitedEpochMillis > 1_000_000_000_000L }.threadId

    @Test fun ngWordsHideMatchingPostsAndCanBeSwitchedOffPerThread() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        list.performScrollToIndex(0)

        // NG編集：ふたちゃと共有のワード一覧へ追加すると、その語を含むレスが消える。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-ng-edit").performClick()
        waitForTag("futaber-ng-input")
        rule.onNodeWithTag("futaber-ng-input").performTextInput("テスト9")
        rule.onNodeWithTag("futaber-ng-add").performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.ngWords.first() } == listOf("テスト9") }
        capture("20-ng-dialog")
        rule.onNodeWithText("閉じる").performClick()
        assertTrue(
            "the NG'd post is gone from the list",
            runCatching { list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9")) }.isFailure
        )

        // このスレッドだけNGを無効にすると、再び見える。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-ng-toggle").assertIsEnabled()
        rule.onNodeWithTag("futaber-menu-ng-toggle").performClick()
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))

        // 登録の削除：削除すると、無効化を戻しても隠れない。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-ng-edit").performClick()
        waitForTag("futaber-ng-delete")
        rule.onAllNodesWithTag("futaber-ng-delete")[0].performClick()
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.ngWords.first() }.isEmpty() }
    }

    /** この端末（エミュレーター）に日本語の音声があるとは限らない。実発声は確認せず、開始・停止と失敗の通知だけを見る。 */
    @Test fun readAloudStartsStopsOrReportsWhyItCouldNot() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-read-aloud").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("futaber-reading-chip").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("futaber-reading-message").fetchSemanticsNodes().isNotEmpty()
        }
        capture("21-read-aloud")
        if (rule.onAllNodesWithTag("futaber-reading-chip").fetchSemanticsNodes().isNotEmpty()) {
            // 読み上げ中はメニューの項目が「停止」になり、止めると表示も消える。
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            rule.onNodeWithTag("futaber-menu-read-aloud").assertIsDisplayed()
            rule.onNodeWithTag("futaber-menu-read-aloud").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-reading-chip").fetchSemanticsNodes().isEmpty() }
        } else {
            // 声がない・準備に失敗した場合は、理由が通知として出て、押せば閉じる。
            rule.onNodeWithTag("futaber-reading-message").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-reading-message").fetchSemanticsNodes().isEmpty() }
        }
        // 戻ると読み上げは止まる（画面の破棄）。
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")
    }

    /** 板はチュートリアル（同梱の模擬データ）だけを使い、実際のサーバーへは送らない。 */
    @Test fun replyAndNewThreadAreWrittenFromTheScreensWithDraftAndConfirmation() {
        // 削除キーは共有の保存値。先に動いた試験が入れていると「削除キー無し」の確認にならないので空にする。
        runBlocking { app.appStateStore.setLastUsedDeleteKey("") }
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performClick()
        waitForTag("futaber-post-screen")
        rule.onNodeWithTag("futaber-post-title").assertIsDisplayed()
        capture("22-post-screen")

        // 削除キーが無いと送れず、理由が出て、入力は残る。
        rule.onNodeWithTag("futaber-post-comment").performTextInput("テスト投稿")
        rule.onNodeWithTag("futaber-post-send").performClick()
        waitForTag("futaber-post-error")
        rule.onNodeWithText("削除キー", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("futaber-post-error").performClick()
        rule.onNodeWithTag("futaber-post-counts").assertIsDisplayed()

        // 書き込み設定で削除キーを入れる。
        rule.onNodeWithTag("futaber-post-settings").performClick()
        waitForTag("futaber-post-deletekey")
        rule.onNodeWithTag("futaber-post-deletekey").performTextInput("pass1234")
        rule.onNodeWithTag("futaber-post-name").performTextInput("試験者")
        rule.waitUntil(10_000) { runBlocking { app.appStateStore.lastUsedDeleteKey.first() } == "pass1234" }
        rule.onNodeWithText("閉じる").performClick()

        // 閉じても下書きが残り、開き直すと戻る。
        rule.waitUntil(10_000) { preference("compat.futaber.drafts")?.contains("テスト投稿") == true }
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performClick()
        waitForTag("futaber-post-comment")
        rule.onNodeWithText("テスト投稿").assertIsDisplayed()

        // 送信：確認ダイアログを経て送られ、画面が閉じ、下書きは消える。
        rule.onNodeWithTag("futaber-post-send").performClick()
        waitForTag("futaber-post-confirm")
        rule.onNodeWithTag("futaber-post-confirm").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty() }
        waitForTag("futaber-post")
        rule.waitUntil(10_000) { preference("compat.futaber.drafts")?.contains("テスト投稿") != true }

        // スレ立て：カタログの＋から書き、送ると新しいスレッドが開く。
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-grid")
        rule.onNodeWithTag("futaber-create-thread").performClick()
        waitForTag("futaber-post-subject")
        rule.onNodeWithTag("futaber-post-subject").performTextInput("新しいスレ")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("立てた本文")
        capture("23-create-thread")
        rule.onNodeWithTag("futaber-post-send").performClick()
        rule.onNodeWithTag("futaber-post-confirm").performClick()
        waitForTag("futaber-thread-title")
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun longPressOnAPostQuotesItIntoTheWriteScreenOrCopiesIt() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))

        // コピー：本文がクリップボードに入る。
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        capture("24-post-actions")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(10_000) {
            var text = ""
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                text = app.getSystemService(android.content.ClipboardManager::class.java)
                    .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            }
            text.contains("テスト9")
        }
        assertTrue(rule.onAllNodesWithTag("futaber-post-actions").fetchSemanticsNodes().isEmpty())

        // 引用：本文の各行に「>」を付けて、書き込み画面の末尾へ入る。
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-quote").performClick()
        waitForTag("futaber-post-screen")
        rule.onNodeWithText(">テスト9", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("futaber-post-close").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty() }

        // No.引用：番号だけを引用する。下書きに続けて足される。
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-quote-number").performClick()
        waitForTag("futaber-post-screen")
        rule.onNodeWithText(">No.", substring = true).assertIsDisplayed()
    }

    /** チュートリアル板は同梱の模擬リポジトリで、そうだね・削除依頼も実サーバーへは送らない。 */
    @Test fun postSheetOffersSaidaneDeleteAndReportAndTheConfirmedOnesAskFirst() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-saidane").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-delete-own").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-report").assertIsDisplayed()
        capture("35-post-sheet-remote")

        // そうだね：確認なしで送り、結果が通知で出る。
        rule.onNodeWithTag("futaber-action-saidane").performClick()
        waitForTag("futaber-remote-notice")
        rule.onNodeWithTag("futaber-remote-notice").assertTextContains("そうだね", substring = true)
        rule.onNodeWithTag("futaber-remote-notice").performClick()

        // 通報：確認ダイアログが先に出て、キャンセルでは送らない。確定すると通知が出る。
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-report").performClick()
        waitForTag("futaber-remote-confirm")
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-remote-confirm").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.onAllNodesWithTag("futaber-remote-notice").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("テスト9").performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
        rule.onNodeWithTag("futaber-action-report").performClick()
        waitForTag("futaber-remote-confirm")
        rule.onNodeWithTag("futaber-remote-confirm").performClick()
        waitForTag("futaber-remote-notice")
        rule.onNodeWithTag("futaber-remote-notice").assertTextContains("削除依頼", substring = true)
    }

    @Test fun quoteModeLetsYouTapThreadLinesIntoTheDraftAndReturnToWriting() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performClick()
        waitForTag("futaber-post-comment")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("前置き")

        // 引用モードへ：スレッドが見え、下書きは小さなカードに残る。
        rule.onNodeWithTag("futaber-post-quote-mode").performClick()
        waitForTag("futaber-quote-strip")
        assertTrue(rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty())
        capture("25-quote-mode")
        // タップできるカードは中の文字を1つのノードにまとめるので、文字で探す。
        rule.onNodeWithText("前置き", substring = true).assertIsDisplayed()

        // 本文の行をタップすると「>」付きで下書きへ追記される。
        val list = rule.onNodeWithTag("futaber-thread-list")
        list.performScrollToNode(androidx.compose.ui.test.hasText("テスト9"))
        rule.onNodeWithText("テスト9").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText(">テスト9", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // カードをタップして書き込みに戻ると、追記が入っている。
        rule.onNodeWithTag("futaber-quote-strip").performClick()
        waitForTag("futaber-post-comment")
        rule.onNodeWithText("前置き", substring = true).assertIsDisplayed()
        rule.onNodeWithText(">テスト9", substring = true).assertIsDisplayed()
        rule.waitUntil(10_000) { preference("compat.futaber.drafts")?.contains(">テスト9") == true }
    }

    @Test fun anImageIsPastedPreviewedSentAndCanBeRemovedWhileATextClipboardIsRefused() {
        openTutorialCatalog()
        runBlocking { app.appStateStore.setLastUsedDeleteKey("pass1234") }
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-write").performClick()
        waitForTag("futaber-post-comment")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("画像つき")

        val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
        val oldClip = run {
            var clip: android.content.ClipData? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { clip = clipboard.primaryClip }
            clip
        }
        try {
            // 文字だけのクリップボードは画像として貼り付けず、理由を出し、本文は残る。
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", "https://example.com/a.png"))
            }
            // 貼り付けはクリップボードの更新がアプリに届いてから読む。
            rule.waitForIdle(); Thread.sleep(500)
            rule.onNodeWithTag("futaber-post-paste").performClick()
            waitForTag("futaber-post-error")
            assertTrue(rule.onAllNodesWithTag("futaber-post-attachment").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("画像つき").assertIsDisplayed()
            rule.onNodeWithTag("futaber-post-error").performClick()

            // 画像のクリップボードは添付され、プレビューが出る。
            val file = java.io.File(app.cacheDir, "compat_post_preview/futaber-test.png")
            file.parentFile!!.mkdirs()
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                clipboard.setPrimaryClip(android.content.ClipData.newUri(app.contentResolver, "test image", uri))
            }
            rule.waitForIdle(); Thread.sleep(500)
            rule.onNodeWithTag("futaber-post-paste").performClick()
            waitForTag("futaber-post-attachment")
            capture("26-attachment")

            // 外すと消え、もう一度貼って、そのまま送ると添付つきで送られて消える。
            rule.onNodeWithTag("futaber-post-attachment-remove").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-post-attachment").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("futaber-post-paste").performClick()
            waitForTag("futaber-post-attachment")
            // 引用モードを往復しても添付は残る。
            rule.onNodeWithTag("futaber-post-quote-mode").performClick()
            waitForTag("futaber-quote-strip")
            rule.onNodeWithTag("futaber-quote-strip").performClick()
            waitForTag("futaber-post-attachment")
            // 確認ダイアログは設定でONのまま。
            rule.onNodeWithTag("futaber-post-send").performClick()
            waitForTag("futaber-post-confirm")
            rule.onNodeWithTag("futaber-post-confirm").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post-screen").fetchSemanticsNodes().isEmpty() }
            // 送った後の書き込み画面には、添付が持ち越されない。
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            rule.onNodeWithTag("futaber-menu-write").performClick()
            waitForTag("futaber-post-comment")
            assertTrue(rule.onAllNodesWithTag("futaber-post-attachment").fetchSemanticsNodes().isEmpty())
            file.delete()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                oldClip?.let { clipboard.setPrimaryClip(it) } ?: clipboard.clearPrimaryClip()
            }
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(1_500) // 画像の読み込みとアニメーションの完了待ち
        // アンインストール後も残るよう、shellの領域へ保存する。
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    /** 操作メニューの外（上端）をタップして閉じる。中央は項目が増えるとメニュー本体に重なる。 */
    private fun dismissOperationMenu() {
        rule.onNodeWithTag("futaber-menu-scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
    }

    /** タブの登録・解除は操作メニューの項目から行う。 */
    private fun toggleTabFromMenu() {
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-tab").performClick()
    }

    private val galleryItem = SemanticsMatcher("gallery item") {
        it.config.getOrNull(SemanticsProperties.TestTag)== "futaber-gallery-item"
    }

    @Test fun galleryAndViewerOpenFromTheMenuAndReturnToTheThread() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")

        // 下部バーの画像一覧：添付のあるレスだけが並ぶ。
        rule.onNodeWithTag("futaber-thread-gallery").assertIsEnabled()
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        rule.waitUntil(20_000) { rule.onAllNodes(galleryItem).fetchSemanticsNodes().size >= 3 }
        capture("20-gallery")
        // ギアは共有のビューア設定を開く（画像ローダーが無いと落ちる経路）。
        rule.onNodeWithTag("futaber-gallery-settings").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("画像ビューア", substring = true).fetchSemanticsNodes().isNotEmpty() || rule.onAllNodes(hasTestTag("futaber-gallery-item")).fetchSemanticsNodes().isEmpty() }
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodes(galleryItem).fetchSemanticsNodes().isNotEmpty() }

        // 1枚タップ → ビューア。画面下部のバーと画像ページが出る。
        rule.onAllNodes(galleryItem)[0].performClick()
        waitForTag("futaber-viewer-info")
        capture("21-viewer")

        // 戻る → 一覧（スレッドへは戻らない）→ もう一度 → スレッド。
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodes(galleryItem).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-media").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-media").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-thread-list").assertIsDisplayed()
    }

    @Test fun viewerCanJumpBackToTheSourcePostWhichClearsASearchFilter() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        // 絞り込み中でも、元のレスへ戻ると絞り込みが解除されて表示される。
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-search").performTextInput("テスト10_2")
        rule.onNodeWithTag("futaber-menu-search").performImeAction()
        waitForTag("futaber-filter-chip")
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        rule.waitUntil(20_000) { rule.onAllNodes(galleryItem).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(galleryItem)[0].performClick()
        waitForTag("futaber-viewer-info")
        rule.onNodeWithContentDescription("レスに戻る").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-media").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-filter-chip").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("futaber-thread-list").assertIsDisplayed()
    }

    /** 4つのテーマ（既定の明・暗、ふたば、ダークテーマ（旧））のカタログとスレッドを保存する（目視用。判定はしない）。 */
    @Test fun captureThemesForReview() {
        openTutorialCatalog()
        listOf("light", "dark", "futaba", "olddark").forEach { theme ->
            rule.onNodeWithTag("futaber-open-settings").performClick()
            waitForTag("futaber-settings")
            rule.onNodeWithTag("futaber-settings-row-theme").performClick()
            rule.onNodeWithTag("futaber-settings-row-theme-$theme").performClick()
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-done").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes().isNotEmpty() }
            capture("theme-$theme-catalog")
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
            waitForTag("futaber-post")
            rule.onNodeWithTag("futaber-thread-list").performScrollToIndex(2)
            capture("theme-$theme-thread")
            rule.onNodeWithTag("futaber-thread-menu").performClick()
            waitForTag("futaber-operation-menu")
            capture("theme-$theme-menu")
            dismissOperationMenu()
            rule.onNodeWithContentDescription("カタログへ戻る").performClick()
            waitForTag("futaber-catalog-item")
        }
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.theme", "system") }
    }

    /** 目視確認用の画面を保存する（判定はしない）。 */
    @Test fun captureScreensForReview() {
        waitForTag("futaber-drawer")
        capture("1-drawer")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
        capture("2-catalog-grid4")
        rule.onNodeWithTag("futaber-display-style").performClick()
        capture("3-style-menu")
        rule.onNodeWithTag("futaber-style-row2").performClick()
        waitForTag("futaber-catalog-list")
        capture("4-catalog-row2")
        rule.onNodeWithTag("futaber-display-style").performClick()
        rule.onNodeWithTag("futaber-style-grid8").performClick()
        waitForTag("futaber-catalog-grid")
        capture("5-catalog-grid8")
        rule.onNodeWithTag("futaber-display-style").performClick()
        rule.onNodeWithTag("futaber-style-row1").performClick()
        waitForTag("futaber-catalog-list")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        capture("6-thread")
        rule.onNodeWithContentDescription("カタログへ戻る").performClick()
        waitForTag("futaber-catalog-list")
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        capture("7-settings")
        rule.onNodeWithTag("futaber-settings-row-theme").performClick()
        rule.onNodeWithTag("futaber-settings-row-theme-dark").performClick()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-done").performClick()
        capture("8-catalog-dark")
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        capture("9-thread-dark")
    }
}
