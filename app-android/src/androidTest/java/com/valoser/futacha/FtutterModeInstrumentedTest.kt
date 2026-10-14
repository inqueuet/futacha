package com.valoser.futacha

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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
        "compat.ftutter.stats.opened", "compat.ftutter.drawerButtonOnTopLeft"
    ) + listOf("commonNg", "imageMenu", "readAloud", "autoScroll", "mht", "ai", "watcher", "archiveSearch", "appLock")
        .map { "compat.ftutter.ext.$it" }
    /** 共通のNG（拡張「共通のNGを使う」の試験で書く）。前後で元に戻す。 */
    private var originalNg: Triple<List<String>, List<String>, List<String>>? = null
    private var originalNoticeShown: Boolean? = null
    /** スレッドを開くと履歴に書くので、試験の前の履歴に戻す。 */
    private var originalHistory: List<com.valoser.futacha.shared.model.ThreadHistoryEntry>? = null

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(ftutterKeys.associateWith { null })
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
            app.compatibilityStore.savePreferences(ftutterKeys.associateWith { null })
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
        rule.onNodeWithTag("ftutter-catalog-tool-search").performClick()
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

    @Test fun closeAllKeepsProtectedTabsAndAProtectedTabAsksBeforeClosing() {
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
}
