package com.valoser.futacha

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * L-B10: futaber's manage panel, board list and catalog search in the real app (the tutorial board only; nothing is
 * sent anywhere). The panel opens on the category chosen last, also after a recreate (S24); 「編集」／「選択」 with
 * 「すべて削除」 that asks first, grey ×s and rows that do not open (M29・M50); futaber's dates (V35); a history row's
 * long press does nothing (G25); a side swipe on the panel changes nothing (G31, kept). The board list's edit (red −,
 * the drag handle, 「完了」 and 「板の追加」, the add alert with 「https://」), the dimmed screen behind it (V31) and
 * Android's Back closing it (S27). The search lists nothing until a word is typed and cancel goes back to the top
 * (S25), the iOS-style field (V30), the buttons over the search (S28, kept), and the popovers with a tap outside that
 * only closes them (V29・M52).
 */
@OptIn(ExperimentalTestApi::class)
class FutaberPanelDrawerSearchInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private val keys = listOf(
        "compat.futaber.theme", "compat.futaber.lastBoardId", "compat.futaber.seen", "compat.futaber.tabs",
        "compat.futaber.favorites", "compat.futaber.manageCategory", "compat.futaber.catalog.displayStyle",
        "compat.futaber.catalog.sort", "compat.futaber.ext.history"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null } + ("compat.futaber.theme" to "light"))
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.updateBoards { boards -> boards.filterNot { it.url.contains("zip.2chan.net/1/") } }
            app.appStateStore.updateBoards { boards -> boards.map { if (it.name == "改名した板") it.copy(name = renamedFrom ?: it.name) else it } }
            app.compatibilityStore.savePreferences(keys.associateWith { null })
        }
        switchProfile(originalProfile)
    }

    private var renamedFrom: String? = null

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

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun dp(px: Float) = px / rule.activity.resources.displayMetrics.density

    /** The text of a node: its edit text (a field) or its texts, merged (a row's label is in its child). */
    private fun text(tag: String): String {
        val config = rule.onNodeWithTag(tag).fetchSemanticsNode().config
        if (SemanticsProperties.EditableText in config) return config[SemanticsProperties.EditableText].text
        return config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }
    }

    private fun openCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
        rule.waitUntil(10_000) { absent("futaber-drawer-scrim") }
    }

    private fun backToCatalog() {
        rule.onNodeWithTag("futaber-thread-title").performCustomAccessibilityActionWithLabel("カタログへ戻る")
        waitForTag("futaber-catalog-item")
    }

    /** Opens the first thread and comes back, so the history has a row. */
    private fun readAThread() {
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        backToCatalog()
    }

    private fun openPanel() {
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-manage-panel")
    }

    private fun closePanel() {
        rule.onNodeWithTag("futaber-manage-close").performClick()
        rule.waitUntil(10_000) { absent("futaber-manage-panel") }
    }

    private fun selectedCategory(): String =
        listOf("SavedBox", "Notifications", "Favorites", "History", "Tabs").single { name ->
            rule.onNodeWithTag("futaber-manage-category-$name").fetchSemanticsNode().config
                .getOrElse(SemanticsProperties.Selected) { false }
        }

    private data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun distance(other: Rgb) = abs(r - other.r) + abs(g - other.g) + abs(b - other.b)
        override fun toString() = "#%02X%02X%02X".format(r, g, b)
    }

    private fun PixelMap.at(x: Float, y: Float): Rgb {
        val c = this[x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1)]
        return Rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(800)
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-lb10-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    @Test fun thePanelOpensOnTheCategoryChosenLastAlsoAfterARecreate() {
        openCatalog()
        openPanel()
        // Nothing chosen yet: the history, as before.
        assertEquals("History", selectedCategory())
        assertEquals("スレッド履歴", text("futaber-manage-title"))
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.manageCategory") == "Favorites" }
        assertEquals("お気に入り", text("futaber-manage-title"))
        closePanel()
        openPanel()
        assertEquals("Favorites", selectedCategory())
        closePanel()
        rule.activityRule.scenario.recreate()
        waitForTag("futaber-catalog-item")
        openPanel()
        rule.onNodeWithTag("futaber-manage-category-Favorites").assertIsSelected()
        // The saved box is titled as futaber's, with its total line.
        rule.onNodeWithTag("futaber-manage-category-SavedBox").performClick()
        rule.waitUntil(10_000) { text("futaber-manage-title") == "MHTファイル" }
        waitForTag("futaber-mht-summary")
        assertTrue(text("futaber-mht-summary"), Regex("""\d+(\.\d)?(KB|MB) \d+項目の合計""").matches(text("futaber-mht-summary")))
        rule.onNodeWithTag("futaber-manage-edit").assertTextEquals("選択")
    }

    @Test fun theEditModesAskBeforeDeletingAllAndTheirRowsDoNotOpen() {
        openCatalog()
        readAThread()
        openPanel()
        // The history: 「編集」 → 「完了」 with 「すべて削除」 at the left and a grey × at each row's right; a row does not open.
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
        waitForTag("futaber-history-row")
        rule.onNodeWithTag("futaber-manage-edit").assertTextEquals("編集")
        assertTrue(absent("futaber-manage-clear"))
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onNodeWithTag("futaber-manage-edit").assertTextEquals("完了")
        waitForTag("futaber-manage-clear")
        val row = rule.onAllNodesWithTag("futaber-history-row")[0].fetchSemanticsNode().boundsInRoot // other tests leave history rows behind
        val delete = rule.onAllNodesWithTag("futaber-manage-delete")[0].fetchSemanticsNode().boundsInRoot
        assertTrue("the × is at the row's right ($delete in $row)", delete.left > row.left + row.width / 2)
        capture("panel-history-edit")
        rule.onAllNodesWithTag("futaber-history-row")[0].performClick()
        rule.waitForIdle()
        assertTrue("a row does not open while editing", absent("futaber-post"))
        // 「すべて削除」 asks first (the history's own dialog); キャンセル keeps every row.
        rule.onNodeWithTag("futaber-manage-clear").performClick()
        waitForTag("futaber-history-clear-dialog")
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(10_000) { absent("futaber-history-clear-dialog") }
        waitForTag("futaber-history-row")
        rule.onNodeWithTag("futaber-manage-edit").performClick()

        // The notices: 「編集」, nothing listed: 「すべて削除」 is there and cannot be pressed; the patrol's line and
        // 「キーワード管理」 are small rows after the list.
        rule.onNodeWithTag("futaber-manage-category-Notifications").performClick()
        rule.waitUntil(10_000) { text("futaber-manage-title") == "通知" }
        waitForTag("futaber-watch-manage")
        if (rule.onAllNodesWithTag("futaber-notification-row").fetchSemanticsNodes().isEmpty()) {
            assertTrue("an empty notices list is plain", rule.onAllNodesWithText("一致したスレッドはまだありません").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag("futaber-manage-edit").performClick()
            rule.onNodeWithTag("futaber-manage-clear").assertIsNotEnabled()
            rule.onNodeWithTag("futaber-manage-edit").performClick()
        }
        waitForTag("futaber-notifications-status")

        // The favourites: 「選択」; with one starred, 「すべて削除」 asks with futaber's alert, キャンセル keeps it, 削除 takes it.
        closePanel()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        rule.onNodeWithTag("futaber-menu-favorite").performClick()
        // L-B12 (M14): a thread with pictures asks first.
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-favorite-images-none").fetchSemanticsNodes().isNotEmpty() || preference("compat.futaber.favorites")?.let { it.isNotBlank() && it != "[]" } == true }
        if (rule.onAllNodesWithTag("futaber-favorite-images-none").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("futaber-favorite-images-none").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.favorites")?.let { it.isNotBlank() && it != "[]" } == true }
        rule.waitUntil(10_000) { absent("futaber-operation-menu") }
        openPanel()
        rule.onNodeWithTag("futaber-manage-category-Favorites").performClick()
        waitForTag("futaber-favorite-row")
        rule.onNodeWithTag("futaber-manage-edit").assertTextEquals("選択")
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onNodeWithTag("futaber-manage-clear").performClick()
        waitForTag("futaber-alert")
        capture("panel-favorites-clear-alert")
        rule.onNodeWithTag("futaber-alert-manage-clear-cancel").performClick()
        rule.waitUntil(10_000) { absent("futaber-alert") }
        waitForTag("futaber-favorite-row")
        rule.onNodeWithTag("futaber-manage-clear").performClick()
        waitForTag("futaber-alert")
        rule.onNodeWithTag("futaber-alert-manage-clear-confirm").performClick()
        rule.waitUntil(10_000) { absent("futaber-favorite-row") }
        rule.waitUntil(10_000) { preference("compat.futaber.favorites").let { it.isNullOrBlank() || it == "[]" } }
        assertTrue("an empty favourites list is plain", rule.onAllNodesWithText("お気に入りはありません", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun aHistoryRowHasFutabersDateAndItsLongPressDoesNothing() {
        openCatalog()
        readAThread()
        openPanel()
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
        waitForTag("futaber-history-row")
        val date = rule.onAllNodesWithTag("futaber-manage-row-date", useUnmergedTree = true)[0].fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString("") { it.text }
        assertTrue(date, Regex("""\d\d/\d\d/\d\d \d\d:\d\d""").matches(date))
        rule.onAllNodesWithTag("futaber-history-row")[0].performTouchInput { longClick() }
        rule.waitForIdle()
        Thread.sleep(600)
        rule.waitForIdle()
        assertTrue("a long press does not open the thread", absent("futaber-post"))
        waitForTag("futaber-manage-panel")
        // G31 (kept): a side swipe on the list changes nothing (no edit, no other category, the panel stays).
        rule.onNodeWithTag("futaber-history-list").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("futaber-history-list").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals("History", selectedCategory())
        rule.onNodeWithTag("futaber-manage-edit").assertTextEquals("編集")
        assertTrue(absent("futaber-post"))
        // A tap still opens it.
        rule.onAllNodesWithTag("futaber-history-row")[0].performClick()
        waitForTag("futaber-post")
    }

    @Test fun theBoardListEditIsFutabersAndItsAddAlertSaysWhyAnEmptyOkAddsNothing() {
        openCatalog()
        rule.onNodeWithTag("futaber-open-drawer").performClick()
        waitForTag("futaber-drawer-scrim")
        // V31: 「掲示板」 lower down (about 100pt from the top in futaber).
        val heading = bounds("futaber-drawer-heading")
        assertTrue("the heading's top ${dp(heading.top)}dp", dp(heading.top) >= 80f)
        rule.onNodeWithTag("futaber-board-edit").performClick()
        waitForTag("futaber-board-handle")
        // The heading stays 「掲示板」; futaber's red −, the handle, 「完了」 and 「板の追加」; no arrows.
        assertEquals("掲示板", text("futaber-drawer-heading"))
        waitForTag("futaber-board-delete")
        assertTrue(absent("futaber-board-up"))
        rule.onNodeWithTag("futaber-board-edit").assertIsDisplayed()
        rule.onNodeWithText("完了").assertIsDisplayed()
        rule.onNodeWithTag("futaber-board-add").assertIsDisplayed()
        val names = { runBlocking { app.appStateStore.boards.first() }.map { it.name } }
        val urls = { runBlocking { app.appStateStore.boards.first() }.map { it.url } }
        val before = urls()
        // A row's tap does nothing while editing.
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        rule.waitForIdle()
        waitForTag("futaber-board-handle")
        capture("drawer-edit")

        // 「板の追加」: the alert with 「https://」; OK so adds nothing and says why; an address is added at the end.
        rule.onNodeWithTag("futaber-board-add").performClick()
        waitForTag("futaber-add-board-url")
        assertEquals("https://", text("futaber-add-board-url"))
        capture("drawer-add-alert")
        rule.onNodeWithTag("futaber-add-board-submit").performClick()
        waitForTag("futaber-alert-message")
        assertEquals("板のアドレスを入力してください", text("futaber-alert-message"))
        assertEquals(before, urls())
        rule.onNodeWithTag("futaber-add-board-url").performTextReplacement("zip.2chan.net/1/")
        rule.onNodeWithTag("futaber-add-board-submit").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("http:// もしくは https:// から始まるURLを入力してください").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-add-board-url").performTextReplacement("https://zip.2chan.net/1/")
        rule.onNodeWithTag("futaber-add-board-submit").performClick()
        rule.waitUntil(15_000) { urls().size == before.size + 1 }
        assertEquals("https://zip.2chan.net/1/futaba.php", urls().last())
        rule.waitUntil(10_000) { absent("futaber-alert") }

        // The handle drags the new board up one row; the order is stored when the finger is lifted.
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-board-handle").fetchSemanticsNodes().size == before.size + 1 }
        val handles = rule.onAllNodesWithTag("futaber-board-handle")
        val rowHeight = rule.onAllNodesWithTag("futaber-drawer-board")[0].fetchSemanticsNode().boundsInRoot.height
        handles[before.size].performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(0f, -rowHeight * 1.1f / 12f)) }
            up()
        }
        rule.waitUntil(15_000) { urls().indexOf("https://zip.2chan.net/1/futaba.php") == before.size - 1 }
        // Audit B3: a second drag after the first was stored moves the board again (it moved an old list before).
        if (before.size >= 2) {
            rule.waitForIdle()
            rule.onAllNodesWithTag("futaber-board-handle")[before.size - 1].performTouchInput {
                down(center)
                repeat(12) { moveBy(Offset(0f, -rowHeight * 1.1f / 12f)) }
                up()
            }
            rule.waitUntil(15_000) { urls().indexOf("https://zip.2chan.net/1/futaba.php") == before.size - 2 }
        }

        // 「名前を変更」 after futaber's two: which board, then its name.
        renamedFrom = names().first()
        rule.onNodeWithTag("futaber-board-rename").performClick()
        waitForTag("futaber-post-actions")
        rule.onAllNodesWithTag("futaber-action-board-rename-${runBlocking { app.appStateStore.boards.first() }.first().id}")[0].performClick()
        waitForTag("futaber-rename-board-name")
        rule.onNodeWithTag("futaber-rename-board-name").performTextReplacement("改名した板")
        rule.onNodeWithTag("futaber-rename-board-submit").performClick()
        rule.waitUntil(15_000) { names().first() == "改名した板" }
        rule.onNodeWithTag("futaber-board-rename").performClick()
        waitForTag("futaber-post-actions")
        rule.onAllNodesWithTag("futaber-action-board-rename-${runBlocking { app.appStateStore.boards.first() }.first().id}")[0].performClick()
        waitForTag("futaber-rename-board-name")
        rule.onNodeWithTag("futaber-rename-board-name").performTextReplacement(renamedFrom!!)
        rule.onNodeWithTag("futaber-rename-board-submit").performClick()
        rule.waitUntil(15_000) { names().first() == renamedFrom }

        // The bulk add is offered after them (asked first; not pressed, nothing is fetched).
        if (rule.onAllNodesWithTag("futaber-board-bulk-add").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag("futaber-board-bulk-add").performClick()
            waitForTag("futaber-add-board-bulk-submit")
            rule.onNodeWithTag("futaber-alert-bulk-cancel").performClick()
            rule.waitUntil(10_000) { absent("futaber-alert") }
        }

        // The red − asks, then takes the board out.
        val zip = urls().indexOf("https://zip.2chan.net/1/futaba.php")
        rule.onAllNodesWithTag("futaber-board-delete")[zip].performClick()
        rule.onNodeWithTag("futaber-board-delete-confirm").performClick()
        rule.waitUntil(15_000) { urls() == before }
        rule.onNodeWithTag("futaber-board-edit").performClick()
        rule.waitUntil(10_000) { absent("futaber-board-handle") }
    }

    @Test fun theScreenBehindTheBoardListIsDimmedAndAndroidBackClosesIt() {
        openCatalog()
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val bar = bounds("futaber-board-address")
        // A point of the white navigation bar that is still on screen once the catalog is pushed aside.
        val x = root.width * 0.86f
        val y = bar.center.y
        val clear = rule.onRoot().captureToImage().toPixelMap().at(root.width * 0.15f, y)
        rule.onNodeWithTag("futaber-open-drawer").performClick()
        waitForTag("futaber-drawer-scrim")
        Thread.sleep(500)
        rule.waitForIdle()
        val dimmed = rule.onRoot().captureToImage().toPixelMap().at(x, y)
        assertTrue("the white bar is white before ($clear)", clear.distance(Rgb(255, 255, 255)) <= 6)
        // futaber's white 255 → 245 (about 4%).
        assertTrue("dimmed a little: $dimmed", dimmed.r in 238..250 && dimmed.g in 238..250 && dimmed.b in 238..250)
        capture("drawer-dimmed")
        Espresso.pressBack()
        rule.waitUntil(10_000) { absent("futaber-drawer-scrim") }
    }

    @Test fun theSearchListsNothingUntilAWordAndTheFieldIsIosStyle() {
        openCatalog()
        rule.onNodeWithTag("futaber-search").performClick()
        waitForTag("futaber-search-field")
        // S25: nothing typed, nothing listed; V30: 「検索ワード」 in the grey field, no clear button yet, a black キャンセル.
        waitForTag("futaber-catalog-search-blank")
        assertTrue(absent("futaber-catalog-item"))
        rule.onNodeWithText("検索ワード").assertIsDisplayed()
        assertTrue(absent("futaber-search-clear"))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage().toPixelMap()
        val field = bounds("futaber-search-field")
        val ground = image.at(field.right - 4f, field.center.y)
        assertTrue("the field is grey ($ground)", ground.distance(Rgb(0xF2, 0xF2, 0xF2)) <= 9)
        val cancel = bounds("futaber-search-cancel")
        val darkest = (cancel.left.toInt() until cancel.right.toInt()).flatMap { x ->
            (cancel.top.toInt() until cancel.bottom.toInt()).map { y -> image.at(x.toFloat(), y.toFloat()) }
        }.minBy { it.r + it.g + it.b }
        assertTrue("キャンセル is black ($darkest)", darkest.r < 60 && darkest.g < 60 && darkest.b < 60)
        capture("search-open")
        rule.onNodeWithTag("futaber-search-field").performTextInput("チュートリアル")
        waitForTag("futaber-catalog-item")
        waitForTag("futaber-search-clear")
        capture("search-typed")
        // The clear button empties the word: nothing listed again.
        rule.onNodeWithTag("futaber-search-clear").performClick()
        waitForTag("futaber-catalog-search-blank")
        assertEquals("", text("futaber-search-field"))
        // Cancel: every thread again.
        rule.onNodeWithTag("futaber-search-cancel").performClick()
        rule.waitUntil(10_000) { absent("futaber-search-field") }
        waitForTag("futaber-catalog-item")
    }

    @Test fun theTopButtonsStillWorkWhileSearchingAndTheSearchStays() {
        openCatalog()
        rule.onNodeWithTag("futaber-search").performClick()
        waitForTag("futaber-search-field")
        rule.onNodeWithTag("futaber-search-field").performTextInput("チュ")
        // S28 (kept): the board list's button opens the list over the search; closing it finds the search as it was.
        rule.onNodeWithTag("futaber-open-drawer").performClick()
        waitForTag("futaber-drawer-scrim")
        rule.onNodeWithTag("futaber-drawer-scrim").performClick()
        rule.waitUntil(10_000) { absent("futaber-drawer-scrim") }
        waitForTag("futaber-search-field")
        assertEquals("チュ", text("futaber-search-field"))
    }

    @Test fun thePopoversPointAtTheirButtonsAndATapOutsideOnlyClosesThem() {
        openCatalog()
        val button = bounds("futaber-display-style")
        rule.onNodeWithTag("futaber-display-style").performClick()
        waitForTag("futaber-popover")
        val popover = bounds("futaber-popover")
        assertTrue("the popover is over its button ($popover / $button)", popover.bottom <= button.top + 1f)
        assertEquals("表示形式", text("futaber-popover-title"))
        // Full-width digits in the labels (V29); the stored value stays "grid4".
        assertEquals("行１", text("futaber-style-row1"))
        assertEquals("横４", text("futaber-style-grid4"))
        rule.onNodeWithTag("futaber-style-grid4").assertIsSelected()
        capture("popover-style")
        // M52: a tap outside on a thread only closes the popover; the thread does not open.
        val item = rule.onAllNodesWithTag("futaber-catalog-item")[0].fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("futaber-popover-outside").performTouchInput { click(Offset(item.center.x, item.center.y)) }
        rule.waitUntil(10_000) { absent("futaber-popover") }
        rule.waitForIdle()
        Thread.sleep(500)
        assertTrue("the tap did not reach the thread", absent("futaber-post"))
        rule.onNodeWithTag("futaber-display-style").performClick()
        waitForTag("futaber-popover")
        rule.onNodeWithTag("futaber-style-grid3").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.displayStyle") == "grid3" }
        assertTrue(absent("futaber-popover"))
        // The sort popover.
        rule.onNodeWithTag("futaber-sort").performClick()
        waitForTag("futaber-popover")
        assertEquals("表示順序", text("futaber-popover-title"))
        capture("popover-sort")
        rule.onNodeWithTag("futaber-sort-Momentum").performClick()
        rule.waitUntil(10_000) { preference("compat.futaber.catalog.sort") == "Momentum" }
        // Android's Back closes a popover too.
        rule.onNodeWithTag("futaber-sort").performClick()
        waitForTag("futaber-popover")
        Espresso.pressBack()
        rule.waitUntil(10_000) { absent("futaber-popover") }
        waitForTag("futaber-catalog-item")
    }

    /** Pictures of the panel's categories, the board list's edit and the search, light and dark, for the look doc. */
    @Test fun captureThePanelDrawerAndSearchForReview() {
        listOf("light", "dark").forEach { theme ->
            runBlocking { app.compatibilityStore.savePreferences(mapOf("compat.futaber.theme" to theme, "compat.futaber.manageCategory" to null)) }
            rule.activityRule.scenario.recreate()
            if (theme == "light") openCatalog() else waitForTag("futaber-catalog-item")
            if (theme == "light") readAThread()
            openPanel()
            listOf("SavedBox", "Notifications", "Favorites", "History", "Tabs").forEach { category ->
                rule.onNodeWithTag("futaber-manage-category-$category").performClick()
                rule.waitForIdle()
                capture("$theme-panel-$category")
                if (category != "Tabs") {
                    rule.onNodeWithTag("futaber-manage-edit").performClick()
                    capture("$theme-panel-$category-edit")
                    rule.onNodeWithTag("futaber-manage-edit").performClick()
                }
            }
            closePanel()
            rule.onNodeWithTag("futaber-open-drawer").performClick()
            waitForTag("futaber-drawer-scrim")
            capture("$theme-drawer")
            rule.onNodeWithTag("futaber-board-edit").performClick()
            waitForTag("futaber-board-handle")
            capture("$theme-drawer-edit")
            rule.onNodeWithTag("futaber-board-add").performClick()
            waitForTag("futaber-add-board-url")
            capture("$theme-drawer-add")
            rule.onNodeWithTag("futaber-add-board-cancel").performClick()
            rule.waitUntil(10_000) { absent("futaber-alert") }
            rule.onNodeWithTag("futaber-board-edit").performClick()
            rule.onNodeWithTag("futaber-drawer-scrim").performClick()
            rule.waitUntil(10_000) { absent("futaber-drawer-scrim") }
            rule.onNodeWithTag("futaber-search").performClick()
            waitForTag("futaber-search-field")
            capture("$theme-search-open")
            rule.onNodeWithTag("futaber-search-field").performTextInput("チュ")
            capture("$theme-search-typed")
            rule.onNodeWithTag("futaber-search-cancel").performClick()
            rule.waitUntil(10_000) { absent("futaber-search-field") }
            rule.onNodeWithTag("futaber-display-style").performClick()
            waitForTag("futaber-popover")
            capture("$theme-popover-style")
            rule.onNodeWithTag("futaber-popover-outside").performClick()
            rule.waitUntil(10_000) { absent("futaber-popover") }
            rule.onNodeWithTag("futaber-sort").performClick()
            waitForTag("futaber-popover")
            capture("$theme-popover-sort")
            rule.onNodeWithTag("futaber-popover-outside").performClick()
            rule.waitUntil(10_000) { absent("futaber-popover") }
        }
        assertFalse(absent("futaber-catalog-item"))
    }
}
