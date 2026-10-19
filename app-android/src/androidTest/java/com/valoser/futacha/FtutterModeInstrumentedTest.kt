package com.valoser.futacha

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.onAllNodesWithTag as allTagged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたったー風モード（開発中、デバッグ版だけで選べる）を本番ホストで開く。
 * 段階ごとに操作の試験を足す（docs/ftutter-mode-implementation-plan-2026-10-13.md）。
 */
class FtutterModeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    /** 試験で書く ふたったー風の設定。前後で消す。 */
    private val ftutterKeys = listOf(
        "compat.ftutter.previousOrderMode", "compat.ftutter.useKakudai", "compat.ftutter.hihyouji",
        "compat.ftutter.lastBoard", "compat.ftutter.showTab", "compat.ftutter.isPrivacyMode",
        "compat.ftutter.isTree", "compat.ftutter.seen", "compat.ftutter.tabs", "compat.ftutter.opening",
        "compat.ftutter.protected", "compat.ftutter.drawerContent", "compat.ftutter.NGList", "compat.ftutter.drafts",
        "compat.ftutter.deleteKey", "compat.manualPostMarks", "compat.ftutter.resuDOrderName", "compat.ftutter.resuDOrderBool",
        "compat.ftutter.Gesture12", "compat.ftutter.useTabletMode", "compat.ftutter.sureTBOrderName", "compat.ftutter.sureTBOrderBool",
        "compat.ftutter.gazouItiranRow", "compat.ftutter.showGalleryTab", "compat.ftutter.mainColor", "compat.ftutter.numOnThumb",
        "compat.ftutter.stats.opened", "compat.ftutter.drawerButtonOnTopLeft", "compat.ftutter.rirekiLimit",
        "compat.ftutter.useScrollbar", "compat.ftutter.alwaysShowScrollbar", "compat.ftutter.putScrollbarLeft",
        "compat.ftutter.scrollbarThickness", "compat.ftutter.saveHtmlWithThumb", "compat.ftutter.saveHtmlWithFile",
        "compat.ftutter.saveHtmlWithApusyou", "compat.ftutter.saveHtmlZip", "compat.ftutter.drawerLeftOrRight", "compat.ftutter.color.inTab"
    ) + listOf("commonNg", "imageMenu", "readAloud", "autoScroll", "mht", "ai", "watcher", "archiveSearch", "appLock", "historyTools")
        .map { "compat.ftutter.ext.$it" }
    /** 共通のNG（拡張「共通のNGを使う」の試験で書く）。前後で元に戻す。 */
    private var originalNg: Triple<List<String>, List<String>, List<String>>? = null
    private var originalNoticeShown: Boolean? = null
    /** スレッドを開くと履歴に書くので、試験の前の履歴に戻す。 */
    private var originalHistory: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>? = null
    private var originalFtutterPreferences: Map<String, String> = emptyMap()

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            originalFtutterPreferences = app.compatibilityStore.preferences.first()
                .filterKeys { it.startsWith("compat.ftutter.") || it == "compat.manualPostMarks" }
            app.compatibilityStore.savePreferences((originalFtutterPreferences.keys + ftutterKeys).associateWith { null })
            originalHistory = app.appStateStore.history.first()
            originalNoticeShown = app.appStateStore.hasShownPostingNotice.first()
            originalNg = Triple(app.appStateStore.ngWords.first(), app.appStateStore.ngHeaders.first(), app.appStateStore.catalogNgWords.first())
        }
        switchProfile(ExperienceProfile.FTUTTER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        // 画面側（rememberCoroutineScope）で始まった保存は、この試験の仕組みでは試験のスレッドが時計を進めた時にしか
        // 進まない。後片付けを試験のスレッドで待つと、その保存が鍵を持ったまま止まって互いに待つので、別のスレッドで行い、
        // 時計を進めながら終わりを待つ。
        val cleanup = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val currentKeys = app.compatibilityStore.preferences.first().keys.filter { it.startsWith("compat.ftutter.") }
            app.compatibilityStore.savePreferences((currentKeys + ftutterKeys).associateWith<String, String?> { null } + originalFtutterPreferences)
            originalHistory?.let { app.appStateStore.setHistory(it) }
            originalNoticeShown?.let { app.appStateStore.setHasShownPostingNotice(it) }
            originalNg?.let { (words, headers, catalog) ->
                app.appStateStore.setNgWords(words)
                app.appStateStore.setNgHeaders(headers)
                app.appStateStore.setCatalogNgWords(catalog)
            }
        }
        rule.waitUntil(30_000) { cleanup.isCompleted }
        switchProfile(originalProfile)
    }

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun openTutorialCatalog() {
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-row")
        rule.allTagged("ftutter-board-row")[0].performClick()
        waitForTag("ftutter-catalog")
        waitForTag("ftutter-catalog-item")
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    /** 目視用の画面を `Android/data/<id>/files/ftutter-shots/` に残す（試験の合否には使わない）。 */
    private fun saveShot(name: String) {
        rule.waitForIdle()
        val dir = java.io.File(app.getExternalFilesDir(null), "ftutter-shots").apply { mkdirs() }
        // ダイアログ・メニューは別のウィンドウなので、画面全体を撮る。画像の読み込みを少し待つ。
        Thread.sleep(1_500)
        rule.waitForIdle()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        runCatching { rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
            .onFailure { saveShot("timeout-$tag"); throw it }
    }

    @Test fun theModeOpensOnTheBoardListInTheOriginalColoursAndOffersTheModeSwitch() {
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-root")
        waitForTag("ftutter-board-list")
        // The default blue scheme: the original's toolbar is #EBEFF7 (measured on ふたったー 3.1.1).
        val pixels = rule.onNodeWithTag("ftutter-root").captureToImage().toPixelMap()
        val bar = pixels[pixels.width / 2, (pixels.height * 0.06f).toInt()]
        listOf(bar.red to 0xEB, bar.green to 0xEF, bar.blue to 0xF7).forEach { (actual, expected) ->
            assertTrue("toolbar channel ${(actual * 255).toInt()} vs $expected", abs((actual * 255).toInt() - expected) <= 3)
        }
        saveShot("01-board-list")
        rule.onNodeWithTag("ftutter-open-settings").performClick()
        waitForTag("ftutter-settings")
        saveShot("02-settings")
        // モードの切り替えは設定の一番下。
        rule.onNodeWithTag("ftutter-settings-list").performScrollToNode(androidx.compose.ui.test.hasText("モード"))
        assertTrue(rule.onAllNodesWithText("モード").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("ftutter-back").performClick()
        waitForTag("ftutter-board-list")
    }

    @Test fun settingsModeRowStaysAboveTheNavigationBarAndCanBeTapped() {
        openSettingsRoot()
        val list = rule.onNodeWithTag("ftutter-settings-list")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("settings-section-モード"))
        val section = rule.onNodeWithTag("settings-section-モード")
        val navigationBottom = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val safeBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom - navigationBottom
        assertTrue(
            "mode row must stay above navigation bar: ${section.fetchSemanticsNode().boundsInRoot.bottom} <= $safeBottom",
            section.fetchSemanticsNode().boundsInRoot.bottom <= safeBottom + 1f
        )
        // A semantics click would bypass the system navigation bar's hit area.
        section.performTouchInput { click(center) }
        list.performScrollToNode(androidx.compose.ui.test.hasText("ふたばー風モード"))
        rule.onNodeWithText("ふたばー風モード").performTouchInput { click(center) }
        rule.onNodeWithText("ふたばー風モードへ切り替えますか？").assertIsDisplayed()
        rule.onNodeWithText("キャンセル").performClick()
        assertEquals(ExperienceProfile.FTUTTER, app.experienceProfileStore.readActiveProfile())
    }

    @Test fun theBoardListOpensTheTutorialCatalogWhoseSortSearchAndZoomWork() {
        openTutorialCatalog()
        saveShot("03-catalog")
        val total = rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().size
        // The tutorial board has one thread.
        assertTrue("catalog items: $total", total >= 1)

        // The sort is one for every board and is remembered (previousOrderMode).
        rule.onNodeWithTag("ftutter-catalog-order").performClick()
        waitForTag("ftutter-order-NEW")
        rule.onNodeWithTag("ftutter-order-NEW").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.previousOrderMode") == "1" }
        waitForTag("ftutter-catalog-item")

        // The search narrows the tiles; closing it brings them back.
        rule.onNodeWithTag("ftutter-catalog-tool-search").performScrollTo().performClick()
        rule.onNodeWithTag("ftutter-search-input").performTextInput("zzzz-no-such-thread")
        rule.waitUntil(5_000) { rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("ftutter-search-close").performClick()
        waitForTag("ftutter-catalog-item")

        // The long-press zoom shows only while the finger stays down.
        rule.allTagged("ftutter-catalog-item")[0].performTouchInput { down(center); advanceEventTime(1_000); move() }
        waitForTag("ftutter-catalog-zoom")
        saveShot("04-zoom")
        rule.allTagged("ftutter-catalog-item")[0].performTouchInput { up() }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-catalog-zoom").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun withoutTheZoomTheLongPressDialogHidesAThreadAndTheHiddenListRestoresIt() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.useKakudai", "false") }
        openTutorialCatalog()
        val before = rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().size
        rule.allTagged("ftutter-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("ftutter-thumb-dialog")
        saveShot("05-thumb-dialog")
        rule.onNodeWithTag("ftutter-thumb-HIDE").performClick()
        rule.waitUntil(10_000) { !preference("compat.ftutter.hihyouji").isNullOrEmpty() }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().size == before - 1 }

        rule.onNodeWithTag("ftutter-catalog-tool-menu").performClick()
        waitForTag("ftutter-menu-hidden-ng")
        rule.onNodeWithTag("ftutter-menu-hidden-ng").performClick()
        waitForTag("ftutter-hidden-row")
        assertEquals(1, rule.allTagged("ftutter-hidden-row").fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-hidden-remove").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.hihyouji").isNullOrEmpty() }
        rule.onNodeWithTag("ftutter-back").performClick()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().size == before }
    }

    private fun openTutorialThread() {
        openTutorialCatalog()
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-thread")
        waitForTag("ftutter-post")
    }

    @Test fun importedScrollbarAndHtmlSaveChoicesHaveWorkingControls() {
        runBlocking { app.compatibilityStore.savePreferences(mapOf(
            "compat.ftutter.useScrollbar" to "true", "compat.ftutter.alwaysShowScrollbar" to "true",
            "compat.ftutter.putScrollbarLeft" to "true", "compat.ftutter.scrollbarThickness" to "12",
            "compat.ftutter.saveHtmlWithThumb" to "false", "compat.ftutter.saveHtmlWithFile" to "true",
            "compat.ftutter.saveHtmlWithApusyou" to "true", "compat.ftutter.saveHtmlZip" to "true"
        )) }
        openTutorialThread()
        waitForTag("compat-fast-scrollbar")
        val bar = rule.onNodeWithTag("compat-fast-scrollbar").fetchSemanticsNode().boundsInRoot
        assertTrue("scrollbar is on the left", bar.left < 10.dp.value * rule.density.density)
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.useScrollbar", "false") }
        rule.waitUntil(10_000) { rule.allTagged("compat-fast-scrollbar").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        rule.onNodeWithText("スレを保存").performClick()
        rule.onNodeWithText("サムネイルを含める").assertIsDisplayed()
        rule.onNodeWithText("元ファイルを含める").assertIsDisplayed()
        rule.onNodeWithText("あぷ小を含める").assertIsDisplayed()
        rule.onNodeWithText("ZIPで保存").assertIsDisplayed()
        // Checkbox state reflects the imported four independent choices.
        val checkboxes = rule.onAllNodes(androidx.compose.ui.test.isToggleable()).fetchSemanticsNodes()
        assertEquals(4, checkboxes.size)
        val states = checkboxes.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] }
        assertEquals(listOf(androidx.compose.ui.state.ToggleableState.Off,
            androidx.compose.ui.state.ToggleableState.On, androidx.compose.ui.state.ToggleableState.On,
            androidx.compose.ui.state.ToggleableState.On), states)
        saveShot("imported-scrollbar-html-options")
    }

    @Test fun anOpenTabsCatalogCellUsesItsImportedBackgroundColor() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.color.inTab", "4290435669") }
        openTutorialThread()
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-catalog-item")
        rule.waitUntil(10_000) { tutorialUrl in tabs() }
        rule.waitForIdle()
        saveShot("imported-open-tab-background")
        val pixels = rule.allTagged("ftutter-catalog-item")[0].captureToImage().toPixelMap()
        var matches = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (kotlin.math.abs(color.red - 186f / 255) < 0.01f &&
                kotlin.math.abs(color.green - 218f / 255) < 0.01f && kotlin.math.abs(color.blue - 85f / 255) < 0.01f) matches++
        }
        assertTrue("the open tab's catalogue background is painted", matches > 10)
        saveShot("imported-open-tab-background")
    }

    @Test fun importedHistoryLimitHidesOlderRowsAndKeepsProtectedRows() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking { app.compatibilityStore.savePreferences(mapOf(
            "compat.ftutter.rirekiLimit" to "1", "compat.ftutter.protected" to fakeTab3
        )) }
        openTutorialCatalog()
        rule.onNodeWithTag("ftutter-catalog-tool-drawer").performClick()
        waitForTag("ftutter-drawer-list")
        rule.waitUntil(10_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 2 }
        assertEquals(3, historyUrls().size)
        saveShot("imported-history-limit")
    }

    @Test fun theTutorialThreadShowsItsPostsAndTheQuotePopupsStackAndCloseOneByOne() {
        openTutorialThread()
        saveShot("06-thread")
        // 開いたスレッドは履歴の先頭に入る。
        rule.waitUntil(10_000) {
            runBlocking { app.appStateStore.history.first() }.firstOrNull()?.threadId == "1364612020"
        }
        // 前回の読み位置へ戻って開くので、先頭へ動かしてから見る。未読のスレの「ここまで読んだ」は先頭の前。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("kokomade")
        waitForTag("ftutter-kokomade")

        // 返信数のタップで返信の一覧、その中の返信数のタップでもう1枚重なる。Backで1枚ずつ閉じる。
        // 「テスト10」（通し番号10）には返信が3件ある。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p10")
        waitForTag("ftutter-reply-count")
        rule.allTagged("ftutter-reply-count")[0].performClick()
        waitForTag("ftutter-popup")
        saveShot("07-popup")
        assertEquals(1, rule.allTagged("ftutter-popup").fetchSemanticsNodes().size)
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-popup").fetchSemanticsNodes().isEmpty() }
        // スレッドの画面は残る（ポップアップのBackでは閉じない）。
        assertTrue(rule.allTagged("ftutter-thread").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun theTreeAndThePopularFilterReorderAndNarrowThePosts() {
        openTutorialThread()
        val all = rule.allTagged("ftutter-post").fetchSemanticsNodes().size
        // ツリー表示は全スレッド共通で保存する。
        rule.onNodeWithTag("ftutter-thread-tool-tree").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.isTree") == "true" }
        // 「テスト10」（10）への返信（11）は、一段字下げして親の下に並ぶ。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p10")
        rule.waitForIdle()
        saveShot("08-tree")
        val lefts = rule.allTagged("ftutter-post").fetchSemanticsNodes().map { it.boundsInRoot.left }
        assertTrue("tree indents: $lefts", lefts.any { it > lefts.minOrNull()!! + 10f })
        rule.onNodeWithTag("ftutter-thread-tool-tree").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.isTree") == "false" }
        // 人気レスの抽出はタブごとで保存しない。返信3件以上のレスだけになる。
        // チュートリアルでは「テスト10」（返信3件）だけが本体で、帯2本の間に1件だけ残る。
        rule.onNodeWithTag("ftutter-thread-tool-ninki").performClick()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-post").fetchSemanticsNodes().size == 1 }
        assertTrue(rule.onAllNodesWithText("テスト10_1", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(null, preference("compat.ftutter.ninki"))
        saveShot("09-popular")
        rule.onNodeWithTag("ftutter-thread-tool-ninki").performClick()
        rule.waitForIdle()
        // 解除すると全レスに戻る（OPは画像が大きく1画面に1件しか見えないことがあるので、末尾のレスまで動かして見る）。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p20")
        assertTrue(all > 0)
    }

    @Test fun leavingTheThreadSavesHowFarItWasRead() {
        openTutorialThread()
        // 「一番下へ」は既定でツールバーに無いので、一覧を末尾まで動かす。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("end")
        rule.waitForIdle()
        rule.onNodeWithTag("ftutter-back").performClick()
        waitForTag("ftutter-catalog")
        // 既読の件数（compat.ftutter.seen）と、履歴の読み位置が残る。
        rule.waitUntil(10_000) { preference("compat.ftutter.seen")?.contains("1364612020.htm") == true }
        rule.waitUntil(10_000) {
            runBlocking { app.appStateStore.history.first() }.firstOrNull { it.threadId == "1364612020" }?.lastReadItemIndex ?: 0 > 0
        }
        // もう一度開くと、「ここまで読んだ」は先頭ではなく既読の後ろに出る。
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        saveShot("10-reopened")
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p0")
        rule.waitForIdle()
        assertTrue(rule.allTagged("ftutter-kokomade").fetchSemanticsNodes().isEmpty())
    }

    private val tutorialUrl = "https://www.example.com/t/res/1364612020.htm"
    private val fakeTab2 = "https://www.example.com/t/res/1000000002.htm"
    private val fakeTab3 = "https://www.example.com/t/res/1000000003.htm"
    private fun tabs(): List<String> = preference("compat.ftutter.tabs").orEmpty().split("\n").filter { it.isNotBlank() }

    private fun openDrawerOnTabs() {
        rule.onNodeWithTag("ftutter-catalog-tool-drawer").performClick()
        waitForTag("ftutter-drawer")
        rule.onNodeWithTag("ftutter-drawer-content").performClick()
        waitForTag("ftutter-drawer-content-TABS")
        rule.onNodeWithTag("ftutter-drawer-content-TABS").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.drawerContent") == "1" }
    }

    @Test fun openingAThreadAddsItsTabFirstAndTheDrawerListsIt() {
        // 起動時は履歴に無いタブを閉じる（原作）ので、タブのスレッドは履歴にも入れておく。
        seedHistory(fakeTab2)
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.tabs", fakeTab2) }
        openTutorialThread()
        runCatching { rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab2) } }
        assertEquals(listOf(tutorialUrl, fakeTab2), tabs())
        assertEquals(tutorialUrl, preference("compat.ftutter.opening"))
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-catalog")
        openDrawerOnTabs()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 2 }
        saveShot("11-drawer-tabs")
        // 行のタップでそのスレッドを開く。
        rule.allTagged("ftutter-drawer-row")[0].performClick()
        waitForTag("ftutter-thread")
    }

    @Test fun swipingARowClosesItAndTheRestoreButtonPutsItBack() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2, fakeTab3).joinToString("\n")) }
        openTutorialCatalog()
        openDrawerOnTabs()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 3 }
        // 既定は右へスワイプで閉じる。
        rule.allTagged("ftutter-drawer-swipe")[1].performTouchInput { swipeRight() }
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab3) }
        waitForTag("ftutter-drawer-restore")
        rule.onNodeWithTag("ftutter-drawer-restore").performClick()
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab2, fakeTab3) }
        // 左へスワイプは保護の切り替えで、行は残る。
        rule.allTagged("ftutter-drawer-swipe")[2].performTouchInput { swipeLeft() }
        runCatching { rule.waitUntil(10_000) { preference("compat.ftutter.protected") == fakeTab3 } }
        assertEquals(fakeTab3, preference("compat.ftutter.protected"))
        assertEquals(3, tabs().size)
        saveShot("12-drawer-protected")
    }

    @Test fun tabUndoIsAbsentFromHistoryAndExplainsEachRestoredTabWithoutDeletingHistory() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2, fakeTab3).joinToString("\n")) }
        openTutorialCatalog()
        openDrawerOnTabs()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 3 }
        rule.onNodeWithTag("ftutter-drawer-restore").assertIsNotEnabled()
        rule.onNodeWithText("タブを戻す").assertIsDisplayed()
        val historyBefore = runBlocking { app.appStateStore.history.first() }
        rule.allTagged("ftutter-drawer-swipe")[1].performTouchInput { swipeRight() }
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab3) }
        rule.allTagged("ftutter-drawer-swipe")[1].performTouchInput { swipeRight() }
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl) }
        rule.onNodeWithText("残り2件").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-drawer-content").performClick()
        rule.onNodeWithTag("ftutter-drawer-content-HISTORY").performClick()
        rule.onNodeWithTag("ftutter-drawer-restore").assertDoesNotExist()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 3 }
        saveShot("history-without-tab-undo")
        rule.onNodeWithTag("ftutter-drawer-content").performClick()
        rule.onNodeWithTag("ftutter-drawer-content-TABS").performClick()
        rule.onNodeWithTag("ftutter-drawer-restore").performClick()
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab3) && preference("compat.ftutter.opening") == fakeTab3 }
        assertEquals(fakeTab3, preference("compat.ftutter.opening"))
        rule.onNodeWithText("残り1件").assertIsDisplayed()
        rule.onNodeWithTag("ftutter-drawer-restore-notice").assertIsDisplayed()
        saveShot("tab-undo-one-remaining")
        rule.onNodeWithTag("ftutter-drawer-content").performClick()
        rule.onNodeWithTag("ftutter-drawer-content-TABS_BY_ACCESS").performClick()
        rule.onNodeWithTag("ftutter-drawer-restore").performClick()
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab2, fakeTab3) && preference("compat.ftutter.opening") == fakeTab2 }
        assertEquals(fakeTab2, preference("compat.ftutter.opening"))
        rule.onNodeWithTag("ftutter-drawer-restore").assertIsNotEnabled()
        rule.onNodeWithText("残り0件").assertIsDisplayed()
        assertEquals(historyBefore, runBlocking { app.appStateStore.history.first() })
    }

    @Test fun closeAllKeepsProtectedTabsAndAProtectedTabAsksBeforeClosing() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2, fakeTab3).joinToString("\n"))
            app.compatibilityStore.savePreference("compat.ftutter.protected", fakeTab2)
        }
        openTutorialCatalog()
        openDrawerOnTabs()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 3 }
        rule.allTagged("ftutter-drawer-row")[0].performTouchInput { longClick() }
        waitForTag("ftutter-tab-dialog")
        saveShot("13-tab-dialog")
        rule.onNodeWithTag("ftutter-tab-CLOSE_ALL").performClick()
        rule.waitUntil(10_000) { tabs() == listOf(fakeTab2) }
        // 保護したタブを1つ閉じる時は確認が出る。
        rule.allTagged("ftutter-drawer-swipe")[0].performTouchInput { swipeRight() }
        waitForTag("ftutter-protected-ok")
        rule.onNodeWithTag("ftutter-protected-ok").performClick()
        rule.waitUntil(10_000) { tabs().isEmpty() }
        // 閉じると保護も外れる（原作どおり、戻しても保護は戻らない）。
        rule.waitUntil(10_000) { preference("compat.ftutter.protected").isNullOrEmpty() }
    }

    @Test fun closingTheLastTabFromTheTabStripGoesBackToTheCatalogOnce() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.showTab", "true") }
        openTutorialThread()
        waitForTag("ftutter-tab-strip")
        saveShot("14-tab-strip")
        rule.onNodeWithTag("ftutter-tab-selected").performTouchInput { longClick() }
        waitForTag("ftutter-tab-dialog")
        rule.onNodeWithTag("ftutter-tab-CLOSE_ALL").performClick()
        // 原作は板一覧まで戻ってしまう（二重の戻り）が、カタログへ1段だけ戻る。
        waitForTag("ftutter-catalog")
        rule.waitUntil(10_000) { tabs().isEmpty() }
        assertTrue(rule.allTagged("ftutter-board-list").fetchSemanticsNodes().isEmpty())
    }

    /** 通し番号 [index] のレスを長押しして、メニューを開く。 */
    private fun longPressPost(index: Int) {
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p$index")
        rule.waitForIdle()
        // 先頭へ動かしたので、一覧の上端に最も近いレスの行がそのレス。
        val listTop = rule.onNodeWithTag("ftutter-thread-list").fetchSemanticsNode().boundsInRoot.top
        val nodes = rule.allTagged("ftutter-post").fetchSemanticsNodes()
        val at = nodes.indices.filter { nodes[it].boundsInRoot.top >= listTop - 1f }.minByOrNull { nodes[it].boundsInRoot.top }!!
        // 中央は画像のことがある（画像の長押しは原作どおり何もしない）ので、見出しの所を押す。
        rule.allTagged("ftutter-post")[at].performTouchInput { longClick(androidx.compose.ui.geometry.Offset(centerX, top + 24f)) }
        runCatching { waitForTag("ftutter-post-menu-dialog", 8_000) }.onFailure { saveShot("debug-longpress-$index"); throw it }
    }

    @Test fun theLongPressMenuOffersTheNineItemsCopiesAndMarksAPost() {
        openTutorialThread()
        longPressPost(1)
        saveShot("15-post-menu")
        listOf("rep", "repNo", "repSelect", "soudane", "delete", "del", "mark", "copy", "ng").forEach {
            assertTrue("menu item $it", rule.allTagged("ftutter-post-menu-$it").fetchSemanticsNodes().isNotEmpty())
        }
        rule.onNodeWithTag("ftutter-post-menu-copy").performClick()
        waitForTag("ftutter-copy-dialog")
        rule.onNodeWithTag("ftutter-copy-No").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("をコピーしました", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // マークは他モードと共有の手動マーク（No.で保存）。
        longPressPost(1)
        rule.onNodeWithTag("ftutter-post-menu-mark").performClick()
        rule.waitUntil(10_000) { preference("compat.manualPostMarks")?.contains("1364612020") == true }
        longPressPost(1)
        assertTrue(rule.onAllNodesWithText("マーク解除").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun anNgWordFromTheMenuHidesThePostAndTheThreadMenuListsIt() {
        openTutorialThread()
        longPressPost(1)
        rule.onNodeWithTag("ftutter-post-menu-ng").performClick()
        // IDのあるレスは「NGワード／NGID・IP」を選ぶ。
        waitForTag("ftutter-choice-0")
        rule.onNodeWithTag("ftutter-choice-0").performClick()
        waitForTag("ftutter-ng-dialog")
        saveShot("16-ng-dialog")
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.NGList")?.contains("\"type\":1") == true }
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-ng")
        rule.onNodeWithTag("ftutter-thread-menu-ng").performClick()
        waitForTag("ftutter-popup")
        // 不正な正規表現は保存の前に断る（原作は本文が出なくなった）。
        androidx.test.espresso.Espresso.pressBack()
        longPressPost(2)
        rule.onNodeWithTag("ftutter-post-menu-ng").performClick()
        waitForTag("ftutter-choice-0")
        rule.onNodeWithTag("ftutter-choice-0").performClick()
        waitForTag("ftutter-ng-dialog")
        rule.onNodeWithTag("ftutter-ng-text").performTextReplacement("[")
        rule.onNodeWithTag("ftutter-ng-regex").performClick()
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        waitForTag("ftutter-ng-error")
    }

    /**
     * 再監査H1：Androidの正規表現は照合を途中で止められない。破滅的なパターンを登録しようとしても、画面は止まらず、
     * 少しの時間で「時間がかかりすぎます」と断る（以前はOKでメインスレッドが止まり、ANRになり得た）。
     */
    @Test fun aCatastrophicRegexIsRefusedWithoutFreezingOnAndroid() {
        openTutorialThread()
        longPressPost(2)
        rule.onNodeWithTag("ftutter-post-menu-ng").performClick()
        waitForTag("ftutter-choice-0")
        rule.onNodeWithTag("ftutter-choice-0").performClick()
        waitForTag("ftutter-ng-dialog")
        rule.onNodeWithTag("ftutter-ng-text").performTextReplacement("(q+){1,30}r")
        rule.onNodeWithTag("ftutter-ng-regex").performClick()
        val started = System.currentTimeMillis()
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        rule.waitUntil(15_000) { rule.onAllNodesWithText("時間がかかりすぎます", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 10_000)
        assertTrue(preference("compat.ftutter.NGList")?.contains("(q+)") != true)
    }

    /**
     * 再監査M1：NGタイトルがあっても、スレから戻った最初のコマでカタログが空にならない（以前は照合が終わるまで0件の一覧を
     * 描き、スクロール位置が先頭に戻った）。時計を止めて、戻った直後の1コマだけを見る。
     */
    @Test fun withATitleNgTheCatalogIsNotEmptyInTheFirstFrameAfterComingBack() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.NGList", "[{\"text\":\"存在しない題名\",\"type\":0}]") }
        openTutorialCatalog()
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            // Frame by frame until the catalog is there, then look at that very first frame (its NG matching has not come
            // back yet).
            var frames = 0
            while (rule.allTagged("ftutter-catalog").fetchSemanticsNodes().isEmpty() && frames < 120) {
                rule.mainClock.advanceTimeByFrame()
                frames++
            }
            assertTrue(rule.allTagged("ftutter-catalog").fetchSemanticsNodes().isNotEmpty())
            assertTrue("the catalog was drawn empty", rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().isNotEmpty())
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    /** 再監査M2：開いていないスレをカタログから「タブに追加」しても、このモードを開き直した時の整理で消えない。 */
    @Test fun aTabAddedFromTheCatalogSurvivesReopeningTheMode() {
        // 開いているタブ（履歴にある別のスレ）がある状態で、まだ開いていないスレを足す。
        seedHistory(fakeTab2)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", fakeTab2)
            app.compatibilityStore.savePreference("compat.ftutter.opening", fakeTab2)
            // 長押しで拡大でなくメニューを出す。
            app.compatibilityStore.savePreference("compat.ftutter.useKakudai", "false")
        }
        openTutorialCatalog()
        rule.allTagged("ftutter-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("ftutter-thumb-dialog")
        rule.onNodeWithTag("ftutter-thumb-ADD_TAB").performClick()
        rule.waitUntil(10_000) { tutorialUrl in tabs() }
        // 開き直す（起動時の整理が履歴に無いタブを閉じる）。
        openTutorialCatalog()
        rule.waitForIdle()
        Thread.sleep(1_000)
        assertTrue(tabs().toString(), tutorialUrl in tabs())
    }

    @Test fun catalogSearchSurvivesOpeningAThreadAndReturning() {
        openTutorialCatalog()
        rule.onNodeWithTag("ftutter-catalog-tool-search").performScrollTo().performClick()
        rule.onNodeWithTag("ftutter-search-input").performTextInput("チュートリアル")
        waitForTag("ftutter-catalog-item")
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-catalog")
        rule.onNodeWithTag("ftutter-search-input").assertTextContains("チュートリアル")
        waitForTag("ftutter-catalog-item")
        saveShot("feedback1442001717-search-return")
    }

    @Test fun gestureActionsCanBeRunFromTextMenusWithoutDrawingAGesture() {
        openTutorialCatalog()
        rule.onNodeWithTag("ftutter-catalog-tool-menu").performClick()
        rule.onNodeWithTag("ftutter-catalog-menu-gesture-actions").performClick()
        rule.onNodeWithTag("ftutter-gesture-action-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-gesture-action-15"))
        rule.onNodeWithTag("ftutter-gesture-action-15").performClick()
        waitForTag("ftutter-search-input")
        rule.onNodeWithTag("ftutter-search-close").performClick()
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        assertEquals(androidx.compose.ui.semantics.Role.Button,
            rule.onNodeWithTag("ftutter-thread-tool-edit").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Role])
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        rule.onNodeWithTag("ftutter-thread-menu-gesture-actions").performClick()
        rule.onNodeWithTag("ftutter-gesture-action-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-gesture-action-14"))
        rule.onNodeWithTag("ftutter-gesture-action-14").performClick()
        waitForTag("ftutter-post-dialog")
        saveShot("feedback-gesture-menu-opens-reply")
    }

    @Test fun theThreadMenuHasATextEntryToWriteAReply() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        rule.onNodeWithTag("ftutter-thread-menu-reply").performScrollTo().performClick()
        waitForTag("ftutter-post-dialog")
        rule.onNodeWithTag("ftutter-post-send").assertIsEnabled()
        saveShot("feedback1442001717-reply-text-entry")
    }

    @Test fun aQuoteReplyKeepsItsDraftAndSendingOnTheTutorialBoardClearsIt() {
        runBlocking { app.appStateStore.setHasShownPostingNotice(true) }
        openTutorialThread()
        longPressPost(1)
        rule.onNodeWithTag("ftutter-post-menu-repNo").performClick()
        waitForTag("ftutter-post-dialog")
        saveShot("17-post-dialog")
        assertTrue(rule.onAllNodesWithText(">No.", substring = true).fetchSemanticsNodes().isNotEmpty())
        // 閉じると下書きに残る（原作：閉じる時だけ保存）。
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(10_000) { preference("compat.ftutter.drafts")?.contains(">No.") == true }
        // 書き込みボタンで開くと下書きが戻る。チュートリアル板の模擬リポジトリへ送ると下書きが消える（実サーバーには送らない）。
        rule.onNodeWithTag("ftutter-thread-tool-edit").performClick()
        waitForTag("ftutter-post-dialog")
        assertTrue(rule.onAllNodesWithText(">No.", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("delete key generated", preference("compat.ftutter.deleteKey")?.length == 8)
        rule.onNodeWithTag("ftutter-post-send").performClick()
        rule.waitUntil(10_000) { rule.allTagged("ftutter-post-dialog").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(10_000) { preference("compat.ftutter.drafts")?.contains(">No.") != true }
    }

    /** 監査M5：閉じなくても、打っている間に下書きが残る（以前は閉じる時だけで、画面から外れる・アプリが終わると消えた）。 */
    @Test fun aDraftIsKeptWhileTypingWithoutClosingTheDialog() {
        runBlocking { app.appStateStore.setHasShownPostingNotice(true) }
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-edit").performClick()
        waitForTag("ftutter-post-dialog")
        rule.onNodeWithTag("ftutter-post-comment").performTextInput("閉じずに残す下書き")
        rule.waitUntil(10_000) { preference("compat.ftutter.drafts")?.contains("閉じずに残す下書き") == true }
        assertTrue(rule.allTagged("ftutter-post-dialog").fetchSemanticsNodes().isNotEmpty())
        // 画面を作り直しても（アプリが裏で終わった時と同じく、閉じる処理は通らない）下書きが戻る。
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-row")
        assertTrue(preference("compat.ftutter.drafts")?.contains("閉じずに残す下書き") == true)
    }

    /** 監査M12：他のアプリから共有された画像を、書き込み画面で添付できる（以前はボタンが無く、案内だけ出た）。 */
    @Test fun aSharedImageCanBeAttachedFromThePostDialog() {
        runBlocking { app.appStateStore.setHasShownPostingNotice(true) }
        val image = com.valoser.futacha.shared.util.ImageData(byteArrayOf(1, 2, 3), "incoming.jpg")
        try {
            com.valoser.futacha.shared.ui.board.IncomingSharedAttachment.offer(image)
            openTutorialThread()
            rule.onNodeWithTag("ftutter-thread-tool-edit").performClick()
            waitForTag("ftutter-post-dialog")
            rule.waitUntil(10_000) { rule.onAllNodesWithText("共有された画像を添付").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("共有された画像を添付").performClick()
            rule.waitUntil(10_000) { com.valoser.futacha.shared.ui.board.IncomingSharedAttachment.pending.value == null }
            assertTrue(rule.onAllNodesWithText("共有された画像を添付").fetchSemanticsNodes().isEmpty())
        } finally {
            com.valoser.futacha.shared.ui.board.IncomingSharedAttachment.consume(image)
        }
    }

    /** 指で折れ線を描く（[segments]は dp の移動。1区間を細かく分けて動かす）。[beforeUp]は離す前に呼ぶ。 */
    private fun drawGesture(tag: String, vararg segments: Pair<Float, Float>, beforeUp: () -> Unit = {}) {
        val node = rule.onNodeWithTag(tag)
        node.performTouchInput { down(center) }
        segments.forEach { (dx, dy) ->
            node.performTouchInput {
                repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(dx.dp.toPx() / 10, dy.dp.toPx() / 10), delayMillis = 16) }
            }
        }
        rule.waitForIdle()
        beforeUp()
        node.performTouchInput { up() }
    }

    @Test fun aRightUpStrokeShowsItsNameWhileDrawnAndOpensTheDrawer() {
        openTutorialThread()
        drawGesture("ftutter-thread-list", 60f to 0f, 0f to -60f) {
            waitForTag("ftutter-gesture-hint")
            assertTrue(rule.onAllNodesWithText("ドロワーを開く").fetchSemanticsNodes().isNotEmpty())
            saveShot("18-gesture-hint")
        }
        waitForTag("ftutter-drawer")
        rule.waitUntil(5_000) { rule.allTagged("ftutter-gesture-hint").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun previousOnTheFirstTabGoesBackToTheCatalogAndAFourSegmentStrokeDoesNothing() {
        openTutorialThread()
        // 4区間（←↑→↑）は表示も実行も無い。
        drawGesture("ftutter-thread-list", -60f to 0f, 0f to -60f, 60f to 0f, 0f to -60f) {
            assertTrue(rule.allTagged("ftutter-gesture-hint").fetchSemanticsNodes().isEmpty())
        }
        rule.waitForIdle()
        assertTrue(rule.allTagged("ftutter-thread").fetchSemanticsNodes().isNotEmpty())
        // 「→」＝前へ：先頭のタブなので、設定（既定ON）どおりカタログへ戻る。
        drawGesture("ftutter-thread-list", 80f to 0f)
        waitForTag("ftutter-catalog")
    }

    @Test fun theGestureSettingsChangeAndResetTheAssignments() {
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-list")
        rule.onNodeWithTag("ftutter-open-settings").performClick()
        waitForTag("ftutter-settings-gesture")
        rule.onNodeWithTag("ftutter-settings-gesture").performClick()
        waitForTag("ftutter-gesture-settings")
        saveShot("19-gesture-settings")
        rule.onNodeWithTag("ftutter-gesture-row-12").performClick()
        waitForTag("ftutter-choice-11")
        rule.onNodeWithTag("ftutter-choice-11").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.Gesture12") == "11" }
        rule.onNodeWithTag("ftutter-gesture-reset").performClick()
        rule.onNodeWithTag("ftutter-gesture-reset-ok").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.Gesture12").isNullOrEmpty() }
    }

    @Test fun theThreadToolbarEditorSavesTheOrderAndSwitches() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-copy-url")
        rule.onNodeWithText("ツールバー編集").performClick()
        waitForTag("ftutter-order-editor")
        saveShot("20-toolbar-editor")
        rule.onNodeWithTag("ftutter-order-save").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.sureTBOrderName")?.startsWith("drawer") == true }
        assertEquals(14, preference("compat.ftutter.sureTBOrderBool")?.split("\n")?.size)
    }

    @Test fun twoPaneShowsTheCatalogAndTheOpenedThreadSideBySide() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.useTabletMode", "true") }
        openTutorialCatalog()
        waitForTag("ftutter-two-pane")
        waitForTag("ftutter-two-pane-empty")
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        // スレッドを開いてもカタログは残る（画面を積まない）。
        assertTrue(rule.allTagged("ftutter-catalog").fetchSemanticsNodes().isNotEmpty())
        saveShot("21-two-pane")
        // 戻るは二画面全体を閉じて板一覧へ（原作）。
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-board-list")
    }

    @Test fun dragAfterALongPressReordersTheTabStrip() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.showTab", "true")
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2, fakeTab3).joinToString("\n"))
        }
        openTutorialCatalog()
        waitForTag("ftutter-tab-strip")
        // 2番目のタブを長押しのまま右へ1つ分動かすと、2番目と3番目が入れ替わる。
        rule.allTagged("ftutter-tab")[1].performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(10) { moveBy(androidx.compose.ui.geometry.Offset(60.dp.toPx() / 10, 0f), delayMillis = 16) }
            up()
        }
        rule.waitUntil(10_000) { tabs() == listOf(tutorialUrl, fakeTab3, fakeTab2) }
    }

    /**
     * 監査M8：ドラッグの途中でタブの並びが変わっても（ここでは別の操作で先頭にタブが足される）、並べ替えが最後まで効く。
     * 以前は位置を鍵にしていたので、並びが変わるとジェスチャーが取り消され、ずれた表示が残り、並べ替えも起きなかった。
     */
    @Test fun aTabDragSurvivesTheTabsChangingInTheMiddle() {
        val fakeTab4 = "https://www.example.com/t/res/1000000004.htm"
        seedHistory(tutorialUrl, fakeTab2, fakeTab3, fakeTab4)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.showTab", "true")
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2, fakeTab3).joinToString("\n"))
        }
        openTutorialCatalog()
        waitForTag("ftutter-tab-strip")
        val tab = rule.allTagged("ftutter-tab")[1]
        tab.performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(5) { moveBy(androidx.compose.ui.geometry.Offset(60.dp.toPx() / 10, 0f), delayMillis = 16) }
        }
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(fakeTab4, tutorialUrl, fakeTab2, fakeTab3).joinToString("\n"))
        }
        rule.waitUntil(10_000) { rule.allTagged("ftutter-tab").fetchSemanticsNodes().size + rule.allTagged("ftutter-tab-selected").fetchSemanticsNodes().size == 4 }
        tab.performTouchInput {
            repeat(5) { moveBy(androidx.compose.ui.geometry.Offset(60.dp.toPx() / 10, 0f), delayMillis = 16) }
            up()
        }
        rule.waitUntil(10_000) { tabs() == listOf(fakeTab4, tutorialUrl, fakeTab3, fakeTab2) }
    }

    /**
     * 再監査M3：スレ画面はスレッドごとに作り直すが、タブ帯の位置は残る。以前はタブ帯の位置がスレ画面と一緒に作り直され、
     * 奥のタブへ切り替えると帯が先頭に戻って、開いたタブが見えなくなった。
     */
    @Test fun theTabStripKeepsItsPositionWhenSwitchingToAFarTab() {
        val many = listOf(tutorialUrl) + (2..14).map { "https://www.example.com/t/res/10000000%02d.htm".format(it) }
        seedHistory(*many.toTypedArray())
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.showTab", "true")
            app.compatibilityStore.savePreference("compat.ftutter.tabs", many.joinToString("\n"))
        }
        openTutorialThread()
        waitForTag("ftutter-tab-strip")
        rule.onNodeWithTag("ftutter-tab-strip").performScrollToKey(many.last())
        rule.waitForIdle()
        val visible = rule.allTagged("ftutter-tab").fetchSemanticsNodes().size
        rule.allTagged("ftutter-tab")[visible - 1].performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.opening") == many.last() }
        rule.waitForIdle()
        // 開いたタブ（奥のタブ）がタブ帯に見えたまま。
        assertTrue(rule.allTagged("ftutter-tab-selected").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun aTappedImageOpensTheViewerWhoseStripCardGoesBackToThePost() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p0")
        // 画像はレスの行に統合されているので、統合前の木で探す。
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performClick()
        waitForTag("ftutter-viewer")
        waitForTag("ftutter-viewer-strip")
        saveShot("22-viewer")
        listOf("ftutter-viewer-save", "ftutter-viewer-strip-toggle", "ftutter-viewer-res", "ftutter-viewer-search", "ftutter-viewer-menu").forEach {
            assertTrue(it, rule.allTagged(it).fetchSemanticsNodes().isNotEmpty())
        }
        // レス表示：その画像のレスを含む会話。
        rule.onNodeWithTag("ftutter-viewer-res").performClick()
        waitForTag("ftutter-viewer-conversation")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-viewer-conversation").fetchSemanticsNodes().isEmpty() }
        // 帯の表示は保存する。
        rule.onNodeWithTag("ftutter-viewer-strip-toggle").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.showGalleryTab") == "false" }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-viewer-strip").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("ftutter-viewer-strip-toggle").performClick()
        waitForTag("ftutter-viewer-strip")
        // 帯の番号カード：ビューアを閉じて、スレのそのレスへ戻る。
        rule.onAllNodesWithTag("ftutter-index-card", useUnmergedTree = true)[1].performClick()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-viewer").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.allTagged("ftutter-thread").fetchSemanticsNodes().isNotEmpty())
        // もう一度開いて、縦のスワイプで閉じる。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p0")
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performClick()
        waitForTag("ftutter-viewer")
        rule.onNodeWithTag("ftutter-viewer").performTouchInput { swipeDown(startY = top + height * 0.2f, endY = top + height * 0.8f, durationMillis = 400) }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-viewer").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theGallerySavesItsColumnsAndALongPressStartsTheSelection() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-collection").performClick()
        waitForTag("ftutter-gallery")
        waitForTag("ftutter-gallery-cell")
        saveShot("23-gallery")
        assertTrue(rule.allTagged("ftutter-index-card").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("ftutter-gallery-plus").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.gazouItiranRow") == "5" }
        rule.onNodeWithTag("ftutter-gallery-minus").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.gazouItiranRow") == "4" }
        rule.allTagged("ftutter-gallery-cell")[0].performTouchInput { longClick() }
        waitForTag("ftutter-gallery-save")
        assertTrue(rule.onAllNodesWithText("選択保存").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("ftutter-gallery-select-all").performClick()
        rule.onNodeWithTag("ftutter-gallery-save").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("個のファイルを保存しますか？", substring = true).fetchSemanticsNodes().isNotEmpty() }
        saveShot("24-gallery-select")
        androidx.test.espresso.Espresso.pressBack()
        // 戻るは選択モードを解くだけ。
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-gallery-save").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.allTagged("ftutter-gallery").fetchSemanticsNodes().isNotEmpty())
        // セルのタップでビューア、閉じると一覧へ戻る。
        rule.allTagged("ftutter-gallery-cell")[0].performClick()
        waitForTag("ftutter-viewer")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-viewer").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.allTagged("ftutter-gallery").fetchSemanticsNodes().isNotEmpty())
    }

    private fun openSettingsRoot() {
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-list")
        rule.onNodeWithTag("ftutter-open-settings").performClick()
        waitForTag("ftutter-settings")
    }

    @Test fun theSettingsCategoriesOpenAndTheirChoicesAreSaved() {
        openSettingsRoot()
        saveShot("25-settings-root")
        listOf("design", "catalog", "thread", "tab-drawer", "media", "ng", "network", "backup", "stats").forEach {
            assertTrue("category $it", rule.allTagged("ftutter-settings-$it").fetchSemanticsNodes().isNotEmpty() ||
                run { rule.onNodeWithTag("ftutter-settings").performTouchInput { swipeUp() }; rule.allTagged("ftutter-settings-$it").fetchSemanticsNodes().isNotEmpty() })
        }
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-list")
        rule.onNodeWithTag("ftutter-open-settings").performClick()
        waitForTag("ftutter-settings-design")
        rule.onNodeWithTag("ftutter-settings-design").performClick()
        waitForTag("ftutter-settings-page-design")
        // ふたば配色（#800000）。
        rule.onNodeWithTag("ftutter-main-color-ff800000").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.mainColor") == (0xFF800000L).toString() }
        saveShot("26-settings-design")
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-settings-catalog")
        rule.onNodeWithTag("ftutter-settings-catalog").performClick()
        waitForTag("ftutter-settings-page-catalog")
        rule.onNodeWithTag("ftutter-setting-compat.ftutter.numOnThumb").performClick()
        waitForTag("ftutter-option-4")
        rule.onNodeWithTag("ftutter-option-4").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.numOnThumb") == "4" }
        androidx.test.espresso.Espresso.pressBack()
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-board-list")
    }

    @Test fun openingAThreadIsCountedInTheStatistics() {
        openTutorialThread()
        rule.waitUntil(10_000) { (preference("compat.ftutter.stats.opened")?.toIntOrNull() ?: 0) >= 1 }
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-catalog")
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-board-list")
        rule.onNodeWithTag("ftutter-open-settings").performClick()
        waitForTag("ftutter-settings")
        rule.onNodeWithTag("ftutter-settings").performTouchInput { swipeUp() }
        waitForTag("ftutter-settings-stats")
        rule.onNodeWithTag("ftutter-settings-stats").performClick()
        waitForTag("ftutter-settings-page-stats")
        saveShot("27-stats")
        assertTrue(rule.onAllNodesWithText("開いたスレ").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun theTopLeftDrawerButtonAppearsWhenSwitchedOn() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.drawerButtonOnTopLeft", "true") }
        openTutorialCatalog()
        waitForTag("ftutter-topbar-drawer")
        rule.onNodeWithTag("ftutter-topbar-drawer").performClick()
        waitForTag("ftutter-drawer")
    }

    @Test fun aRightStrokeClosesTheGallery() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-collection").performClick()
        waitForTag("ftutter-gallery-cell")
        drawGesture("ftutter-gallery", 80f to 0f)
        rule.waitUntil(5_000) { rule.allTagged("ftutter-gallery").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.allTagged("ftutter-thread").fetchSemanticsNodes().isNotEmpty())
    }

    // ---- 第9段階：拡張機能（既定OFF） ----

    private fun enableExtensions(vararg names: String) = runBlocking {
        app.compatibilityStore.savePreferences(names.associate { "compat.ftutter.ext.$it" to "true" })
    }

    private fun openExtrasPage() {
        openSettingsRoot()
        rule.onNodeWithTag("ftutter-settings-list").performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-settings-extras"))
        rule.onNodeWithTag("ftutter-settings-extras").performClick()
        waitForTag("ftutter-settings-page-extras")
    }

    @Test fun withTheExtrasOffTheThreadMenuAndTheImageLongPressStayAsTheOriginal() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-copy-url")
        listOf("ftutter-thread-menu-mht", "ftutter-thread-menu-read-aloud", "ftutter-thread-menu-auto-scroll").forEach {
            assertTrue(it, rule.allTagged(it).fetchSemanticsNodes().isEmpty())
        }
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p2")
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performTouchInput { longClick() }
        rule.waitForIdle()
        assertTrue(rule.allTagged("ftutter-image-menu").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.allTagged("ftutter-post-menu").fetchSemanticsNodes().isEmpty())
    }

    @Test fun theExtrasPageSavesItsSwitchesAndOpensTheSharedAppLock() {
        openExtrasPage()
        saveShot("30-extras")
        assertTrue(rule.allTagged("ftutter-ext-app-lock").fetchSemanticsNodes().isEmpty())
        val list = rule.onNodeWithTag("ftutter-settings-page-extras-list")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-setting-compat.ftutter.ext.appLock"))
        rule.onNodeWithTag("ftutter-setting-compat.ftutter.ext.appLock").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.ext.appLock") == "true" }
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-ext-app-lock"))
        rule.onNodeWithTag("ftutter-ext-app-lock").performClick()
        // 起動ロックは他モードと共有のダイアログ（パスワードは入れずに閉じる）。
        waitForTag("compat-lock-password")
        saveShot("31-extras-app-lock")
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("compat-lock-password").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag("ftutter-setting-compat.ftutter.ext.appLock").performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.ext.appLock") == "false" }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-ext-app-lock").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun theThreadExtrasAddTheirMenuItemsAndTheImageMenu() {
        enableExtensions("mht", "readAloud", "autoScroll", "imageMenu")
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-auto-scroll")
        saveShot("32-extras-thread-menu")
        assertTrue(rule.allTagged("ftutter-thread-menu-read-aloud").fetchSemanticsNodes().isNotEmpty())
        // オートスクロール：帯が出て、停止で消える。
        rule.onNodeWithTag("ftutter-thread-menu-auto-scroll").performClick()
        waitForTag("ftutter-auto-scroll")
        rule.onNodeWithTag("ftutter-auto-scroll-停止").performClick()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-auto-scroll").fetchSemanticsNodes().isEmpty() }
        // MHTで保存：形式を選ぶダイアログ（保存はしない）。
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-mht")
        rule.onNodeWithTag("ftutter-thread-menu-mht").performClick()
        waitForTag("ftutter-mht-choice")
        assertTrue(rule.allTagged("ftutter-mht-full").fetchSemanticsNodes().isNotEmpty())
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-mht-choice").fetchSemanticsNodes().isEmpty() }
        // 画像の長押し：画像検索・画像NGのメニュー（レスのメニューは開かない）。
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("p2")
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("ftutter-post-image", useUnmergedTree = true)[0].performTouchInput { longClick() }
        waitForTag("ftutter-image-menu-search")
        assertTrue(rule.allTagged("ftutter-image-menu-ng").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.allTagged("ftutter-post-menu").fetchSemanticsNodes().isEmpty())
        saveShot("33-extras-image-menu")
        rule.onNodeWithTag("ftutter-image-menu-ng").performClick()
        // 共有の画像NGの登録ダイアログ（登録はしない）。
        rule.waitUntil(10_000) { rule.onAllNodesWithText("NG画像に登録").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(rule.onAllNodesWithText("この板のみ").fetchSemanticsNodes().isNotEmpty())
        androidx.test.espresso.Espresso.pressBack()
    }

    @Test fun theCommonNgHidesSharedWordsOnlyWhileItsSwitchIsOn() {
        runBlocking {
            app.appStateStore.setNgWords(listOf("テスト3"))
            app.appStateStore.setCatalogNgWords(listOf("チュートリアル"))
        }
        // OFF：共有のNGは効かない。
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-copy-url")
        assertTrue(rule.allTagged("ftutter-thread-menu-ng").fetchSemanticsNodes().isEmpty())
        androidx.test.espresso.Espresso.pressBack()
        // ON：本文の語でレスが隠れ、カタログの題名の語でスレが消える。
        enableExtensions("commonNg")
        rule.onNodeWithTag("ftutter-thread-tool-menu").performClick()
        waitForTag("ftutter-thread-menu-ng")
        androidx.test.espresso.Espresso.pressBack()
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-row")
        rule.allTagged("ftutter-board-row")[0].performClick()
        waitForTag("ftutter-catalog")
        rule.waitUntil(20_000) { rule.allTagged("ftutter-catalog-item").fetchSemanticsNodes().isEmpty() }
        saveShot("34-extras-common-ng")
    }

    @Test fun thePatrolExtraOpensTheSharedResultsAndManager() {
        enableExtensions("watcher")
        openExtrasPage()
        val list = rule.onNodeWithTag("ftutter-settings-page-extras-list")
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("ftutter-ext-open-watcher"))
        rule.onNodeWithTag("ftutter-ext-open-watcher").performClick()
        waitForTag("ftutter-watcher")
        rule.onNodeWithTag("ftutter-watcher-manage").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("今すぐ巡回", substring = true).fetchSemanticsNodes().isNotEmpty() }
        saveShot("35-extras-watcher")
    }

    // ---- 履歴の削除（原作：「履歴」の行の長押し→「履歴から削除」→確認）と、拡張の選択削除・すべて削除 ----

    private val fakeTab4 = "https://www.example.com/t/res/1000000004.htm"

    private fun seedHistory(vararg urls: String) = runBlocking {
        val now = System.currentTimeMillis()
        app.appStateStore.setHistory(urls.mapIndexed { i, url ->
            com.valoser.futacha.shared.model.ThreadHistoryEntry(
                threadId = url.substringAfterLast("/").removeSuffix(".htm"), boardId = "", title = "履歴$i",
                titleImageUrl = "", boardName = "チュートリアル", boardUrl = url,
                lastVisitedEpochMillis = now - i * 1_000L, replyCount = 2
            )
        })
    }

    private fun historyUrls(): List<String> = runBlocking { app.appStateStore.history.first().map { it.boardUrl } }

    private fun openDrawerOnHistory(rows: Int) {
        rule.onNodeWithTag("ftutter-catalog-tool-drawer").performClick()
        waitForTag("ftutter-drawer")
        rule.waitUntil(10_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == rows }
    }

    @Test fun aHistoryRowLongPressOffersTheThumbMenuWhoseDeleteAsksAndAlsoClosesTheTab() {
        seedHistory(tutorialUrl, fakeTab2, fakeTab3)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", fakeTab2)
            app.compatibilityStore.savePreference("compat.ftutter.ext.historyTools", "false")
        }
        openTutorialCatalog()
        openDrawerOnHistory(3)
        // 拡張がOFFなら、選択のボタンは出ない。
        assertEquals(0, rule.allTagged("ftutter-drawer-history-select").fetchSemanticsNodes().size)
        // 履歴にだけある行：原作と同じメニュー。削除は確認の後。
        rule.allTagged("ftutter-drawer-row")[2].performTouchInput { longClick() }
        waitForTag("ftutter-thumb-dialog")
        saveShot("36-history-row-menu")
        rule.onNodeWithTag("ftutter-thumb-DELETE_HISTORY").performClick()
        waitForTag("ftutter-confirm-ok")
        saveShot("37-history-delete-confirm")
        assertEquals(listOf(tutorialUrl, fakeTab2, fakeTab3), historyUrls())
        rule.onNodeWithTag("ftutter-confirm-ok").performClick()
        rule.waitUntil(10_000) { historyUrls() == listOf(tutorialUrl, fakeTab2) }
        rule.waitUntil(5_000) { rule.allTagged("ftutter-drawer-row").fetchSemanticsNodes().size == 2 }
        // タブでもある行も、タブのメニューではなく同じメニュー。削除するとタブも閉じる。
        rule.allTagged("ftutter-drawer-row")[1].performTouchInput { longClick() }
        waitForTag("ftutter-thumb-dialog")
        assertEquals(0, rule.allTagged("ftutter-tab-dialog").fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-thumb-DELETE_HISTORY").performClick()
        waitForTag("ftutter-confirm-ok")
        rule.onNodeWithTag("ftutter-confirm-ok").performClick()
        rule.waitUntil(10_000) { historyUrls() == listOf(tutorialUrl) }
        rule.waitUntil(10_000) { tabs().isEmpty() }
    }

    @Test fun theCatalogDeleteFromHistoryAsksFirst() {
        seedHistory(tutorialUrl)
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.useKakudai", "false") }
        openTutorialCatalog()
        rule.allTagged("ftutter-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("ftutter-thumb-dialog")
        rule.onNodeWithTag("ftutter-thumb-DELETE_HISTORY").performClick()
        waitForTag("ftutter-confirm-ok")
        assertEquals(listOf(tutorialUrl), historyUrls())
        rule.onNodeWithTag("ftutter-confirm-ok").performClick()
        rule.waitUntil(10_000) { historyUrls().isEmpty() }
    }

    @Test fun theHistoryToolsExtraDeletesTheChosenRowsAndAllButProtectedOnes() {
        assertEquals(null, preference("compat.ftutter.ext.historyTools"))
        seedHistory(tutorialUrl, fakeTab2, fakeTab3, fakeTab4)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(tutorialUrl, fakeTab2).joinToString("\n"))
            app.compatibilityStore.savePreference("compat.ftutter.protected", fakeTab2)
        }
        openTutorialCatalog()
        openDrawerOnHistory(4)
        rule.onNodeWithTag("ftutter-drawer-history-select").performClick()
        waitForTag("ftutter-drawer-history-delete-selected")
        // 選んだ行の印を数える。行のタップは選択の切り替えで、開かない。
        fun checked() = rule.allTagged("ftutter-drawer-row-checked").fetchSemanticsNodes().size
        rule.onNodeWithText("履歴0").performClick()
        rule.onNodeWithText("履歴1").performClick()
        rule.waitUntil(5_000) { checked() == 2 }
        rule.onNodeWithText("履歴1").performClick()
        rule.waitUntil(5_000) { checked() == 1 }
        rule.onNodeWithText("履歴2").performClick()
        rule.waitUntil(5_000) { checked() == 2 }
        // 選んだのは tutorialUrl（履歴0）と fakeTab3（履歴2）。
        saveShot("38-history-select")
        assertEquals(0, rule.allTagged("ftutter-thread").fetchSemanticsNodes().size)
        rule.onNodeWithTag("ftutter-drawer-history-delete-selected").performClick()
        waitForTag("ftutter-confirm-ok")
        rule.onNodeWithTag("ftutter-confirm-ok").performClick()
        rule.waitUntil(10_000) { historyUrls() == listOf(fakeTab2, fakeTab4) }
        rule.waitUntil(10_000) { tabs() == listOf(fakeTab2) }
        // すべて削除：保護したスレッド（fakeTab2）は残す。
        rule.onNodeWithTag("ftutter-drawer-history-select").performClick()
        waitForTag("ftutter-drawer-history-delete-all")
        rule.onNodeWithTag("ftutter-drawer-history-delete-all").performClick()
        waitForTag("ftutter-confirm-ok")
        saveShot("39-history-delete-all-confirm")
        rule.onNodeWithTag("ftutter-confirm-ok").performClick()
        rule.waitUntil(10_000) { historyUrls() == listOf(fakeTab2) }
        assertEquals(listOf(fakeTab2), tabs())
    }

    // ---- 2026-10-14の監査（docs/code-audit-2026-10-14.md）A1・A2・A3・A8・A9 ----

    @Test fun startingTheModeClosesTabsMissingFromHistoryAndKeepsTheHistoryLimit() {
        // 原作：起動時に履歴に無いタブを閉じ、保護していないスレッドは「履歴の保存数」まで。保護したタブは数えない。
        seedHistory(fakeTab2, fakeTab3, tutorialUrl)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.tabs", listOf(fakeTab4, fakeTab3, fakeTab2, tutorialUrl).joinToString("\n"))
            app.compatibilityStore.savePreference("compat.ftutter.opening", fakeTab2)
            app.compatibilityStore.savePreference("compat.ftutter.protected", tutorialUrl)
            app.compatibilityStore.savePreference("compat.ftutter.rirekiLimit", "1")
        }
        openTutorialCatalog()
        runCatching { rule.waitUntil(10_000) { tabs() == listOf(fakeTab2, tutorialUrl) } }
        assertEquals(listOf(fakeTab2, tutorialUrl), tabs())
    }

    @Test fun switchingToAnotherTabSavesThePositionOfTheThreadLeft() {
        seedHistory(fakeTab2)
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.showTab", "true")
            app.compatibilityStore.savePreference("compat.ftutter.tabs", fakeTab2)
        }
        openTutorialThread()
        waitForTag("ftutter-tab-strip")
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("end")
        rule.waitForIdle()
        // タブ帯で別のスレッドへ。同じ画面のまま替わるので、以前は離れたスレッドの位置が保存されなかった。
        rule.allTagged("ftutter-tab")[0].performClick()
        rule.waitUntil(10_000) { preference("compat.ftutter.seen")?.contains("1364612020.htm") == true }
        rule.waitUntil(10_000) {
            runBlocking { app.appStateStore.history.first() }.firstOrNull { it.threadId == "1364612020" }?.lastReadItemIndex ?: 0 > 0
        }
    }

    @Test fun openingTheSettingsFromAThreadSavesItsPosition() {
        openTutorialThread()
        rule.onNodeWithTag("ftutter-thread-list").performScrollToKey("end")
        rule.waitForIdle()
        // 画面ごと外れる時も、読み位置の書き込みが取り消されない。
        rule.onNodeWithTag("ftutter-thread-settings").performClick()
        waitForTag("ftutter-settings")
        rule.waitUntil(10_000) {
            runBlocking { app.appStateStore.history.first() }.firstOrNull { it.threadId == "1364612020" }?.lastReadItemIndex ?: 0 > 0
        }
    }

    private fun drawerShown(): Boolean = rule.allTagged("ftutter-drawer").fetchSemanticsNodes().any { it.boundsInRoot.right > 1f }

    @Test fun backClosesTheOpenDrawerBeforeLeavingTheCatalogOrTheThread() {
        // スレッドのツールバーの「ドロワー」は既定で出ないので、試験では出しておく。
        runBlocking {
            app.compatibilityStore.savePreference("compat.ftutter.sureTBOrderName", "drawer")
            app.compatibilityStore.savePreference("compat.ftutter.sureTBOrderBool", "true")
        }
        openTutorialCatalog()
        rule.onNodeWithTag("ftutter-catalog-tool-drawer").performClick()
        rule.waitUntil(5_000) { drawerShown() }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { !drawerShown() }
        assertTrue(rule.allTagged("ftutter-catalog").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.allTagged("ftutter-board-list").fetchSemanticsNodes().isEmpty())
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        rule.onNodeWithTag("ftutter-thread-tool-drawer").performClick()
        rule.waitUntil(5_000) { drawerShown() }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { !drawerShown() }
        assertTrue(rule.allTagged("ftutter-thread").fetchSemanticsNodes().isNotEmpty())
        // もう一度戻るとカタログ、さらに板一覧。板一覧から入り直したカタログでは、ドロワーは閉じている。
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-catalog")
        rule.onNodeWithTag("ftutter-catalog-tool-drawer").performClick()
        rule.waitUntil(5_000) { drawerShown() }
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { !drawerShown() }
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-board-list")
        rule.allTagged("ftutter-board-row")[0].performClick()
        waitForTag("ftutter-catalog")
        rule.waitForIdle()
        assertTrue(!drawerShown())
    }

    @Test fun inTwoPaneBackClosesTheCatalogSearchBeforeLeaving() {
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.useTabletMode", "true") }
        openTutorialCatalog()
        waitForTag("ftutter-two-pane")
        rule.allTagged("ftutter-catalog-item")[0].performClick()
        waitForTag("ftutter-post")
        rule.onNodeWithTag("ftutter-catalog-tool-search").performScrollTo().performClick()
        waitForTag("ftutter-search-input")
        // 以前は後から置いたスレッド側の戻るが先に受け、二画面ごと板一覧へ抜けた。
        androidx.test.espresso.Espresso.pressBack()
        rule.waitUntil(5_000) { rule.allTagged("ftutter-search-input").fetchSemanticsNodes().isEmpty() }
        assertTrue(rule.allTagged("ftutter-two-pane").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.allTagged("ftutter-board-list").fetchSemanticsNodes().isEmpty())
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("ftutter-board-list")
    }

    @Test fun deletingAHistoryRowAlsoDeletesTheOtherModesCopySoItDoesNotComeBack() {
        // 他モードと共有する履歴は公式の板のスレだけなので、may の番号で作る（通信はしない）。
        val mayThread = "https://may.2chan.net/b/res/1000000002.htm"
        seedHistory(tutorialUrl, mayThread)
        val shared = runBlocking { app.appStateStore.history.first() }.first { it.boardUrl == mayThread }
        val entry = shared.toCompatHistoryEntry()!!
        // 他モードの履歴の行は板を参照する（外部キー）。他モードで may を開いた人と同じく、板を板一覧と共有ストアの両方に入れておく
        // （共有ストアだけだと、起動時の同期で板ごと履歴が消える）。チュートリアル板が先頭のまま、末尾に足す。
        val may = com.valoser.futacha.shared.model.BoardSummary("ftutter-test-may", "may/b", "", "https://may.2chan.net/b/futaba.php", "")
        val hadBoard = runBlocking { app.compatibilityStore.boards.first() }.any { it.key == entry.boardKey }
        runBlocking {
            if (!hadBoard) {
                app.appStateStore.updateBoards { boards -> boards + may }
                app.compatibilityStore.importModernBoards(listOf(may))
            }
            assertTrue(app.compatibilityStore.boards.first().any { it.key == entry.boardKey })
            app.compatibilityStore.recordHistoryVisit(entry)
        }
        fun compatHasRow() = runBlocking { app.compatibilityStore.history.first() }.any { it.threadNo == "1000000002" }
        try {
            assertTrue(compatHasRow())
            openTutorialCatalog()
            openDrawerOnHistory(2)
            rule.allTagged("ftutter-drawer-row")[1].performTouchInput { longClick() }
            waitForTag("ftutter-thumb-dialog")
            rule.onNodeWithTag("ftutter-thumb-DELETE_HISTORY").performClick()
            waitForTag("ftutter-confirm-ok")
            rule.onNodeWithTag("ftutter-confirm-ok").performClick()
            rule.waitUntil(10_000) { historyUrls() == listOf(tutorialUrl) }
            // 他モードの履歴（共有ストア）からも消えるので、次の起動の統合で戻らない。
            rule.waitUntil(10_000) { !compatHasRow() }
        } finally {
            runBlocking {
                app.compatibilityStore.deleteHistory(entry.canonicalUrl)
                if (!hadBoard) {
                    app.appStateStore.updateBoards { boards -> boards.filterNot { it.id == may.id } }
                    app.compatibilityStore.deleteBoard(entry.boardKey)
                }
            }
        }
    }

    @Test fun anNgRuleBeyondWhatCanBeStoredIsRefusedInTheDialog() {
        // 保存値の上限（2万字）の手前までNGを入れておく。以前は「登録しました」と出るのに保存されなかった。
        val full = buildString {
            append("[")
            var n = 0
            while (length < 19_900) {
                if (n > 0) append(",")
                append("{\"text\":\"既存のNG$n\",\"type\":1}")
                n++
            }
            append("]")
        }
        runBlocking { app.compatibilityStore.savePreference("compat.ftutter.NGList", full) }
        openTutorialThread()
        longPressPost(1)
        rule.onNodeWithTag("ftutter-post-menu-ng").performClick()
        waitForTag("ftutter-choice-0")
        rule.onNodeWithTag("ftutter-choice-0").performClick()
        waitForTag("ftutter-ng-dialog")
        rule.onNodeWithTag("ftutter-ng-text").performTextReplacement("とても長い新しいNGの言葉".repeat(10))
        rule.onNodeWithTag("ftutter-ng-save").performClick()
        waitForTag("ftutter-ng-error")
        assertEquals(full, preference("compat.ftutter.NGList"))
    }
}
