package com.valoser.futacha

import android.provider.Settings
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.geometry.Offset
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * L-B2「見た目の土台」：ふたばー風の画面の画素と寸法が futaber の土台どおりであることを、本番ホストで確かめる
 * （docs/futaber-evolution-look-2026-10-31.md §3.8）。下部バーは白い帯で高さ44dp・5等分、スレッドの本文は状態表示の
 * 下20dpの題名の行のすぐ後から始まり、題名は赤、ティール（2026-10-06の刷新の色）はどこにも無い。ダークテーマのバーは
 * futaberの濃い灰、タブ帯の選ばれたタブは他と同じ見た目で、タブに入れたスレでもパネルボタンの色は変わらない。
 * ナビゲーションの方式（3ボタン／ジェスチャー）はエミュレーターの設定のまま走り、画像の名前に方式を付ける。
 * 通信はチュートリアル板の同梱データだけを使う。
 */
class FutaberLookInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile

    private val futaberKeys = listOf(
        "compat.futaber.theme",
        "compat.futaber.lastBoardId",
        "compat.futaber.manageCategory",
        "compat.futaber.tabs",
        "compat.futaber.seen",
        "compat.futaber.catalog.displayStyle",
        "compat.futaber.catalog.sort",
        // L-B3: the rows, the header and the picture are measured with the defaults (another test can leave 「やや広め」,
        // 「題名と名前：常に」, 「画像を小さく」 or another font size on).
        "compat.futaber.catalog.rowWide",
        "compat.futaber.titleNameAlways",
        "compat.futaber.smallImages",
        "compat.futaber.fontSize"
    )

    private val gestureNavigation: Boolean
        get() = Settings.Secure.getInt(app.contentResolver, "navigation_mode", 0) == 2
    private val modeName get() = if (gestureNavigation) "gesture" else "three-button"

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null } + FUTABER_EXTENSIONS_OFF + ("compat.futaber.theme" to "light"))
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.compatibilityStore.savePreferences(futaberKeys.associateWith { null }) }
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

    private fun present(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { present(tag) }
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-board-address")
        waitForTag("futaber-catalog-item")
        rule.waitUntil(10_000) { !present("futaber-drawer") }
        settle()
    }

    private fun openFirstThread() {
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-thread-title")
        waitForTag("futaber-post")
        settle()
    }

    private fun settle() {
        rule.waitForIdle()
        Thread.sleep(800) // pictures and the drawer's slide
        rule.waitForIdle()
    }

    private val density get() = rule.density.density

    private fun statusTop(): Float {
        var top = 0
        rule.runOnUiThread {
            top = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        return top.toFloat()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun screen(): PixelMap = rule.onRoot().captureToImage().toPixelMap()

    private data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun distance(other: Rgb) = abs(r - other.r) + abs(g - other.g) + abs(b - other.b)
        override fun toString() = "#%02X%02X%02X".format(r, g, b)
    }

    private fun PixelMap.at(x: Float, y: Float): Rgb {
        val c = this[x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1)]
        return Rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }

    private fun rgb(hex: Long) = Rgb(((hex shr 16) and 0xFF).toInt(), ((hex shr 8) and 0xFF).toInt(), (hex and 0xFF).toInt())

    private fun assertColour(label: String, expected: Rgb, actual: Rgb, tolerance: Int = 9) {
        assertTrue("$label: expected $expected, was $actual", expected.distance(actual) <= tolerance)
    }

    /** The colour of a text drawn in [area]: the average of its pixels that differ most from the ground. */
    private fun PixelMap.inkIn(area: Rect, ground: Rgb): Rgb {
        val ink = mutableListOf<Rgb>()
        for (y in area.top.toInt() until area.bottom.toInt().coerceAtMost(height)) for (x in area.left.toInt() until area.right.toInt().coerceAtMost(width)) {
            val p = at(x.toFloat(), y.toFloat())
            if (p.distance(ground) > 300) ink += p
        }
        assertTrue("no ink in $area", ink.isNotEmpty())
        return Rgb(ink.sumOf { it.r } / ink.size, ink.sumOf { it.g } / ink.size, ink.sumOf { it.b } / ink.size)
    }

    /** Pixels of the 2026-10-06 redesign's teal in [area] (its light and dark values). */
    private fun PixelMap.tealIn(area: Rect): Int {
        val teal = listOf(rgb(0x0E7C73), rgb(0x5FD0C4))
        var count = 0
        for (y in area.top.toInt() until area.bottom.toInt().coerceAtMost(height) step 2) for (x in area.left.toInt() until area.right.toInt().coerceAtMost(width) step 2) {
            val p = at(x.toFloat(), y.toFloat())
            if (teal.any { it.distance(p) <= 60 }) count++
        }
        return count
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(500)
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-look-$modeName-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    private fun useTheme(value: String) {
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.theme", value) }
        rule.waitUntil(10_000) { runBlocking { app.compatibilityStore.loadPreference("compat.futaber.theme") } == value }
        settle()
    }

    /** Whether the status bar's and the navigation bar's glyphs are dark (for a light ground under them). */
    private fun systemBarGlyphsDark(): Pair<Boolean, Boolean> {
        var result = false to false
        rule.runOnUiThread {
            val window = rule.activity.window
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            result = controller.isAppearanceLightStatusBars to controller.isAppearanceLightNavigationBars
        }
        return result
    }

    /** V03: the catalog's bottom bar is a white band 44dp high with the five buttons in equal slots, down to the screen's edge. */
    @Test fun theCatalogBarsAreTheOriginalsWhiteBands() {
        openTutorialCatalog()
        val refresh = bounds("futaber-refresh")
        assertEquals("bar height", 44f * density, refresh.height, 1.5f * density)
        val image = screen()
        val white = rgb(0xFFFFFF)
        // Between two buttons (a slot boundary) the band itself shows: white, not a floating capsule.
        val slot = image.width / 5f
        listOf(slot, slot * 2, slot * 3, slot * 4).forEach { x ->
            assertColour("bottom bar at x=$x", white, image.at(x, refresh.center.y))
        }
        // Under the bar, the system's bottom area has the bar's colour.
        assertColour("under the bar", white, image.at(slot * 0.5f, refresh.bottom + 2f * density))
        // The navigation bar at the top: white with the red board address (futaber #FF3B30 made #D60B00).
        val address = bounds("futaber-board-address")
        assertEquals("navigation bar height", 44f * density, bounds("futaber-open-drawer").height, 1.5f * density)
        assertColour("navigation bar", white, image.at(address.left + 2f, address.top + 2f))
        val ink = image.inkIn(address, white)
        assertTrue("board address is red: $ink", ink.r > 150 && ink.g < 90 && ink.b < 90)
        // No teal anywhere on the bars.
        assertEquals("teal on the top", 0, image.tealIn(Rect(0f, 0f, image.width.toFloat(), address.bottom)))
        assertEquals("teal on the bottom", 0, image.tealIn(Rect(0f, refresh.top - 2f, image.width.toFloat(), image.height.toFloat())))
        // Dark glyphs on the white status area and the white bottom bar.
        assertEquals("dark glyphs on the white bars", true to true, systemBarGlyphsDark())
        shot("catalog-light")
    }

    /**
     * V05: no bar at the top of the thread. The title is one red line right under the status area and the posts start
     * 20dp (and the hairline) below it; "カタログへ戻る" is the title's accessibility action. V03 on the thread's bar too.
     */
    @Test fun theThreadStartsUnderItsTitleRow() {
        openTutorialCatalog()
        openFirstThread()
        val statusTop = statusTop()
        val title = bounds("futaber-thread-title")
        val body = bounds("futaber-thread-body")
        assertTrue("title under the status area: ${title.top} >= $statusTop", title.top >= statusTop - 1f)
        assertEquals("the posts start at status + 20dp", statusTop + 20.5f * density, body.top, 1.5f * density)
        val actions = rule.onNodeWithTag("futaber-thread-title").fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertTrue("「カタログへ戻る」 on the title: $actions", "カタログへ戻る" in actions)
        val image = screen()
        val white = rgb(0xFFFFFF)
        val ink = image.inkIn(title, white)
        assertTrue("thread title is red: $ink", ink.r > 150 && ink.g < 90 && ink.b < 90)
        val refresh = bounds("futaber-thread-refresh")
        assertEquals("bar height", 44f * density, refresh.height, 1.5f * density)
        assertColour("thread bar between buttons", white, image.at(image.width / 5f * 2, refresh.center.y))
        assertEquals("teal on the title", 0, image.tealIn(Rect(0f, 0f, image.width.toFloat(), body.top)))
        assertEquals("teal on the bar", 0, image.tealIn(Rect(0f, refresh.top - 2f, image.width.toFloat(), image.height.toFloat())))
        shot("thread-light")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        shot("menu-light")
    }

    /** V01・V02: ダークテーマ: futaber's dark grey bars (#1D1F21) and its cyan title. */
    @Test fun theDarkThemeBarsAreTheOriginalsDarkGrey() {
        openTutorialCatalog()
        useTheme("dark")
        val dark = rgb(0x1D1F21)
        val refresh = bounds("futaber-refresh")
        val address = bounds("futaber-board-address")
        var image = screen()
        assertColour("dark bottom bar", dark, image.at(image.width / 5f, refresh.center.y))
        assertColour("dark navigation bar", dark, image.at(address.left + 2f, address.top + 2f))
        val ink = image.inkIn(address, dark)
        assertTrue("board address is cyan: $ink", ink.r < 90 && ink.g > 150 && ink.b > 150)
        assertEquals("light glyphs on the dark bars", false to false, systemBarGlyphsDark())
        shot("catalog-dark")
        openFirstThread()
        image = screen()
        assertColour("dark thread bar", dark, image.at(image.width / 5f * 3, bounds("futaber-thread-refresh").center.y))
        shot("thread-dark")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        shot("menu-dark")
        // ふたば (provisional values): the red top takes light glyphs, the white bottom bar dark ones.
        androidx.test.espresso.Espresso.pressBack()
        useTheme("futaba")
        assertEquals("ふたば: light glyphs on the red top, dark ones on the white bottom", false to true, systemBarGlyphsDark())
        shot("thread-futaba")
    }

    /** V23・V36: the tab of the open thread looks like the others (only a screen reader hears it is selected), and the
     * panel button keeps its colour for a thread in the tabs. */
    @Test fun theTabStripAndThePanelButtonDoNotMarkTheOpenThread() {
        openTutorialCatalog()
        openFirstThread()
        val first = runBlocking { app.appStateStore.history.first().first() }
        runBlocking {
            app.appStateStore.updateHistory { list ->
                list + first.copy(threadId = "999002", title = "見た目の確認のスレッド", boardUrl = first.boardUrl.replace(first.threadId, "999002"))
            }
            app.compatibilityStore.savePreference(
                "compat.futaber.tabs", "[\"${first.boardId}\\n${first.threadId}\",\"${first.boardId}\\n999002\"]"
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 2 }
        settle()
        val tabs = rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes()
        val selected = tabs.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
        assertEquals("one tab is selected for a screen reader", 1, selected.size)
        val image = screen()
        val white = rgb(0xFFFFFF)
        tabs.forEach { tab ->
            // Right of the thumbnail, above the title: the tab's own ground.
            val b = tab.boundsInRoot
            assertColour("tab ground", white, image.at(b.left + 46f * density, b.top + 1.5f * density))
        }
        // The panel button of a thread in the tabs is drawn like the other icons (no accent).
        val manage = bounds("futaber-manage")
        val ink = image.inkIn(manage, white)
        assertTrue("panel button keeps the icon colour: $ink", abs(ink.r - ink.g) < 30 && abs(ink.g - ink.b) < 30)
        shot("tabs-light")
        runBlocking {
            app.appStateStore.updateHistory { list -> list.filterNot { it.threadId == "999002" } }
        }
    }

    /** C13: the theme page lists futaber's four themes in futaber's order under 「システムテーマ」, then this app's own. */
    @Test fun theThemePageListsTheOriginalsFourThenFollowTheDevice() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-theme").performClick()
        waitForTag("futaber-settings-row-theme-light")
        val order = listOf("light", "olddark", "dark", "futaba", "system").map { bounds("futaber-settings-row-theme-$it").top }
        assertEquals(order.sorted(), order)
        rule.onNodeWithTag("futaber-settings-row-theme-light").assert(androidx.compose.ui.test.hasText("デフォルト"))
        rule.onNodeWithTag("futaber-settings-row-theme-olddark").assert(androidx.compose.ui.test.hasText("ダークテーマ (旧)"))
        rule.onNodeWithTag("futaber-settings-row-theme-dark").assert(androidx.compose.ui.test.hasText("ダークテーマ"))
        rule.onNodeWithTag("futaber-settings-row-theme-futaba").assert(androidx.compose.ui.test.hasText("ふたば"))
        rule.onNodeWithTag("futaber-settings-row-theme-system").assert(androidx.compose.ui.test.hasText("端末の設定に合わせる"))
        rule.onNode(androidx.compose.ui.test.hasText("システムテーマ")).assertExists()
        shot("settings-theme")
    }

    // ---- L-B3「カタログとスレッドの行」(docs/futaber-evolution-look-2026-10-31.md §1.3) ----

    private fun useDisplayStyle(value: String) {
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.catalog.displayStyle", value) }
        rule.waitUntil(10_000) {
            runBlocking { app.compatibilityStore.loadPreference("compat.futaber.catalog.displayStyle") } == value
        }
    }

    private fun description(node: androidx.compose.ui.semantics.SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()

    /**
     * Runs [body]; when it fails, the screen is saved (/data/local/tmp/futaber-look-…-failure-<name>.png), and what it had
     * measured, the semantics tree and the stored ふたばー風 settings are added to the failure (the flaky catalog test).
     */
    private fun recordingFailure(name: String, body: (MutableList<String>) -> Unit) {
        val seen = mutableListOf<String>()
        try {
            body(seen)
        } catch (failure: Throwable) {
            runCatching { shot("failure-$name") }
            val tree = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 40) }.getOrElse { "tree: $it" }
            val stored = runCatching {
                runBlocking { app.compatibilityStore.preferences.first() }.filterKeys { it.startsWith("compat.futaber") }
            }.getOrElse { emptyMap() }
            val history = runCatching { runBlocking { app.appStateStore.history.first() }.take(5).map { it.boardId to it.threadId } }.getOrNull()
            val boards = runCatching { runBlocking { app.appStateStore.boards.first() }.map { it.id to it.url } }.getOrNull()
            val report = "seen=$seen\nstored=$stored\nhistory=$history\nboards=$boards\ntree=\n$tree"
            android.util.Log.e("FutaberLookFlaky", report)
            throw AssertionError("${failure.message}\n$report", failure)
        }
    }

    /** Back to the catalog from a thread, by the title's accessibility action (the ← is gone, V05). */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    private fun backToCatalog() {
        rule.onNodeWithTag("futaber-thread-title").performCustomAccessibilityActionWithLabel("カタログへ戻る")
        waitForTag("futaber-catalog-item")
        settle()
    }

    /**
     * V27・V12 on the real host: the tutorial board's row is 40dp with the title from 56dp and the count 10dp from the
     * right; once its thread is opened (written to the shared history) the row is grey (#EEEEEE) and says so to a screen
     * reader, and the grid's cell strip turns grey too. FutaberRowsInstrumentedTest checks more rows and the dark theme.
     */
    @Test fun theCatalogRowsAreFortyHighAndTheReadOnesGrey() = recordingFailure("catalog-rows") { seen ->
        useDisplayStyle("row1")
        openTutorialCatalog()
        waitForTag("futaber-catalog-list")
        // Opening the thread writes it to the history; back in the catalog its row is the read one.
        openFirstThread()
        backToCatalog()
        rule.waitUntil(10_000) { description(rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()[0]).endsWith("既読") }
        val row = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()[0].boundsInRoot
        // The title and the count are merged into their row for a screen reader; their own nodes are in the unmerged tree.
        val title = rule.onAllNodesWithTag("futaber-catalog-item-title", useUnmergedTree = true).fetchSemanticsNodes()[0].boundsInRoot
        val count = rule.onAllNodesWithTag("futaber-catalog-item-count", useUnmergedTree = true).fetchSemanticsNodes()[0].boundsInRoot
        seen += "row=$row title=$title count=$count density=$density"
        assertEquals("row height", 40f * density, row.height, 1.5f * density)
        assertEquals("title starts at 56dp", 56f * density, title.left - row.left, 1.5f * density)
        assertEquals("count ends 10dp from the right", 10f * density, row.right - count.right, 1.5f * density)
        val image = screen()
        val grey = rgb(0xEEEEEE)
        seen += "ground=${image.at(count.left - 4f * density, row.top + 2f * density)} below=${image.at(count.left - 4f * density, row.bottom + 10f * density)}"
        // The row's own ground, between the title and the count, above the letters.
        assertColour("read row ground", grey, image.at(count.left - 4f * density, row.top + 2f * density), tolerance = 6)
        // Under the row the list is white again.
        assertColour("below the rows", rgb(0xFFFFFF), image.at(count.left - 4f * density, row.bottom + 10f * density), tolerance = 6)
        val titleInk = image.inkIn(title, grey)
        assertTrue("title is black: $titleInk", titleInk.r < 90 && titleInk.g < 90 && titleInk.b < 90)
        val countInk = image.inkIn(count, grey)
        assertTrue("count is red: $countInk", countInk.r > 150 && countInk.g < 90 && countInk.b < 90)
        shot("catalog-rows-light")
        // The grid: the read cell's strip under the picture is grey.
        useDisplayStyle("grid4")
        waitForTag("futaber-catalog-grid")
        settle()
        // The grid is a new list: its read cell is waited for as the row's was (the check that failed with no message
        // after other classes in L-B6 read it at once).
        rule.waitUntil(10_000) { description(rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()[0]).endsWith("既読") }
        val cell = rule.onAllNodesWithTag("futaber-catalog-item").fetchSemanticsNodes()[0]
        seen += "cell=${cell.boundsInRoot} said=${description(cell)} strip=${screen().at(cell.boundsInRoot.center.x, cell.boundsInRoot.bottom - 1.5f * density)}"
        assertTrue("the grid's cell is read: ${description(cell)}", description(cell).endsWith("既読"))
        assertColour("read cell strip", grey, screen().at(cell.boundsInRoot.center.x, cell.boundsInRoot.bottom - 1.5f * density), tolerance = 6)
        shot("catalog-grid-light")
    }

    /**
     * V08・V10・V25・V11: a post's header is one text (the date, the IP or ID and "No." in one node, starting with the number,
     * the subject and the "[mail]"); the replies' mark is inside it and opens the bubble, also from a tap a little beside
     * it (the minimum touch size); the picture is 0.7 of its thumbnail with square corners.
     */
    @Test fun aPostsHeaderIsOneTextWithTheRepliesMarkInIt() {
        openTutorialCatalog()
        openFirstThread()
        // A thread read before opens where reading stopped: go to its first post.
        rule.onNodeWithTag("futaber-thread-list").performScrollToIndex(0)
        settle()
        val header = rule.onAllNodesWithTag("futaber-post-header").fetchSemanticsNodes()
            .first { description(it).endsWith("No.1364612020") }
        val said = description(header)
        assertTrue("the first post's header in futaber's order: $said", said.startsWith("0 無念 [id表示] 25/11/08(土)13:42:53"))
        assertTrue("…and its ID and No. in the same text: $said", said.contains("ID:Mua720AI") && said.endsWith("No.1364612020"))
        // V11: the tutorial's first picture is 250×251, shown at 0.7 with square corners.
        val picture = rule.onAllNodesWithTag("futaber-post-image").fetchSemanticsNodes().first().boundsInRoot
        assertEquals("picture width", 175f * density, picture.width, 1.5f * density)
        assertEquals("picture height", 175.7f * density, picture.height, 1.5f * density)
        rule.waitUntil(10_000) {
            val p = screen().at(picture.left + 1f, picture.top + 1f)
            p.distance(rgb(0xFFFFFF)) > 60 && p.distance(rgb(0xF2F2F2)) > 60
        }
        val img = screen()
        val inner = img.at(picture.left + 6f * density, picture.top + 6f * density)
        assertColour("the picture's very corner is the picture (no rounding)", inner, img.at(picture.left + 1f, picture.top + 1f), tolerance = 60)
        shot("thread-headers-light")
        // V10: the replies' mark lies inside a header and opens the replies' bubble.
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-reply-count"))
        settle()
        // Room under the mark for the tap below it: bring it up into the list when it sits at the bottom.
        val listBottom = bounds("futaber-thread-list").bottom
        repeat(4) {
            val y = rule.onAllNodesWithTag("futaber-reply-count").fetchSemanticsNodes().first().boundsInRoot.center.y
            if (y + 40f * density < listBottom) return@repeat
            rule.onNodeWithTag("futaber-thread-list").performTouchInput {
                swipe(Offset(centerX, centerY), Offset(centerX, centerY - 120f * density), durationMillis = 600)
            }
            settle()
        }
        val mark = rule.onAllNodesWithTag("futaber-reply-count").fetchSemanticsNodes().first()
        assertTrue("room under the mark", mark.boundsInRoot.center.y + 40f * density < listBottom)
        val owner = rule.onAllNodesWithTag("futaber-post-header").fetchSemanticsNodes().first { it.boundsInRoot.contains(mark.boundsInRoot.center) }
        assertTrue("the mark is inside a header text", owner.boundsInRoot.contains(mark.boundsInRoot.topLeft + Offset(1f, 1f)))
        assertTrue("the mark is small text, not a framed pill: ${mark.boundsInRoot.height / density}dp",
            mark.boundsInRoot.height < 22f * density)
        assertEquals("返信", description(mark).takeWhile { !it.isDigit() })
        // A tap 14dp below the mark's middle (on the header or the body's plain text) still opens it: the minimum
        // touch size is kept around the small mark (V10's 44).
        rule.onAllNodesWithTag("futaber-reply-count")[0].performTouchInput {
            click(Offset(width / 2f, height / 2f + 14f * density))
        }
        waitForTag("futaber-quote-card")
        shot("thread-reply-bubble-light")
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
    }

    /** F22: the thread ends with futaber's grey footer and "…頃消えます" at its right. */
    @Test fun theThreadEndsWithTheExpiryOnTheGreyFooter() {
        openTutorialCatalog()
        openFirstThread()
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-thread-expiry"))
        settle()
        rule.onNodeWithTag("futaber-thread-expiry").assert(hasText("19:54頃消えます"))
        val label = bounds("futaber-thread-expiry")
        val footer = bounds("futaber-thread-end")
        val image = screen()
        val ground = rgb(0xF2F2F2)
        assertColour("footer ground", ground, image.at(footer.left + 4f * density, footer.top + 2f * density), tolerance = 6)
        // Right-aligned: the letters are in the right half, ending 8dp from the edge.
        var rightmost = 0f
        var leftmost = Float.MAX_VALUE
        for (y in label.top.toInt() until label.bottom.toInt()) for (x in label.left.toInt() until label.right.toInt()) {
            if (image.at(x.toFloat(), y.toFloat()).distance(ground) > 150) { rightmost = maxOf(rightmost, x.toFloat()); leftmost = minOf(leftmost, x.toFloat()) }
        }
        assertTrue("the label is drawn", rightmost > 0f)
        assertTrue("right-aligned: starts at $leftmost of ${label.width}", leftmost > label.left + label.width / 2f)
        assertEquals("ends 8dp from the right", 8f * density, footer.right - rightmost, 3f * density)
        shot("thread-end-light")
    }

    /** F27: the tab strip and the panel's history show replies, the stored posts less the first. */
    @Test fun theTabsAndTheHistoryShowReplies() {
        openTutorialCatalog()
        openFirstThread()
        // The visit is written on opening, its post count once the page is read (the newest row is this thread).
        fun stored() = runBlocking { app.appStateStore.history.first() }.first()
        rule.waitUntil(10_000) { stored().replyCount > 1 }
        val key = stored()
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.tabs", "[\"${key.boardId}\\n${key.threadId}\"]") }
        waitForTag("futaber-tab")
        fun tab() = rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().single()
        // The tab follows the stored count (it can still move once the page is read); it shows one less.
        rule.waitUntil(10_000) { description(tab()).contains("、${stored().replyCount - 1}レス") }
        val entry = stored()
        val replies = entry.replyCount - 1
        rule.onNode(hasTestTag("futaber-tab") and hasText("$replies レス")).assertExists()
        shot("tabs-replies-light")
        rule.onNodeWithTag("futaber-manage").performClick()
        waitForTag("futaber-manage-category-History")
        rule.onNodeWithTag("futaber-manage-category-History").performClick()
        waitForTag("futaber-history-row")
        // The newest row is the thread just opened (other tests leave rows of the same tutorial thread on other boards).
        val newest = rule.onAllNodesWithTag("futaber-history-row")[0]
        newest.assert(hasText(entry.title.ifBlank { "(無題)" }))
        newest.assert(hasText("$replies"))
        assertTrue("not the stored count", runCatching { newest.assert(hasText("${entry.replyCount}")) }.isFailure)
        shot("history-replies-light")
    }

    /**
     * L-B3: pictures of the rows for the side-by-side with futaber (no assertions; runs the same on the build before the
     * change): the catalog as rows and as a grid with a read thread, a thread's headers and its end, in light and dark.
     */
    @Test fun captureTheRowsForReview() {
        listOf("light", "dark").forEach { theme ->
            useTheme(theme)
            useDisplayStyle("row1")
            if (!present("futaber-catalog-item") || present("futaber-drawer")) openTutorialCatalog()
            settle()
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
            waitForTag("futaber-post")
            settle()
            shot("review-$theme-thread-top")
            rule.onNodeWithTag("futaber-thread-bottom").performClick()
            settle()
            rule.onNodeWithTag("futaber-thread-list").performTouchInput {
                swipe(Offset(centerX, centerY), Offset(centerX, centerY - 300f * density), durationMillis = 600)
            }
            settle()
            shot("review-$theme-thread-end")
            androidx.test.espresso.Espresso.pressBack()
            waitForTag("futaber-catalog-item")
            settle()
            shot("review-$theme-catalog-rows")
            useDisplayStyle("grid4")
            settle()
            shot("review-$theme-catalog-grid")
        }
    }

    /**
     * L-B4: pictures of the sheets and the bubbles for the side-by-side with futaber (no assertions; it uses only tags
     * the build before the change has, so it runs on both): the catalog row's sheet, the post's, the picture's, nested
     * bubbles and the tab sheet, in light and dark. Nothing is sent (each sheet is closed with キャンセル).
     */
    @Test fun captureTheSheetsForReview() {
        listOf("light", "dark").forEach { theme ->
            useTheme(theme)
            useDisplayStyle("row1")
            if (!present("futaber-catalog-item") || present("futaber-drawer")) openTutorialCatalog()
            settle()
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
            waitForTag("futaber-post-actions")
            shot("sheets-$theme-catalog-row")
            rule.onNodeWithTag("futaber-action-cancel").performClick()
            rule.waitUntil(5_000) { !present("futaber-post-actions") }
            openFirstThread()
            rule.onAllNodesWithTag("futaber-post")[0].performTouchInput { longClick(Offset(width / 2f, height - 6f)) }
            waitForTag("futaber-post-actions")
            shot("sheets-$theme-post")
            rule.onNodeWithTag("futaber-action-cancel").performClick()
            rule.waitUntil(5_000) { !present("futaber-post-actions") }
            runCatching {
                rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-post-image"))
                rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick() }
                waitForTag("futaber-post-actions", 5_000)
                shot("sheets-$theme-picture")
                rule.onNodeWithTag("futaber-action-cancel").performClick()
                rule.waitUntil(5_000) { !present("futaber-post-actions") }
            }
            runCatching {
                rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-reply-count"))
                rule.onAllNodesWithTag("futaber-reply-count")[0].performClick()
                waitForTag("futaber-quote-card", 5_000)
                val inCard = hasTestTag("futaber-quote-line") and hasAnyAncestor(hasTestTag("futaber-quote-card"))
                rule.onAllNodes(inCard)[0].performClick()
                rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().isNotEmpty() }
                settle()
                shot("sheets-$theme-nested-bubbles")
            }
            if (present("futaber-quote-close-all")) rule.onNodeWithTag("futaber-quote-close-all").performClick()
            rule.waitUntil(5_000) { !present("futaber-quote-card") }
            // A tab of this thread, then its sheet (the panel button's long press registers it).
            rule.onNodeWithTag("futaber-manage").performTouchInput { longClick() }
            waitForTag("futaber-tab")
            rule.onAllNodesWithTag("futaber-tab")[0].performTouchInput { longClick() }
            waitForTag("futaber-post-actions")
            shot("sheets-$theme-tab")
            rule.onNodeWithTag("futaber-action-tab-remove").performClick()
            rule.waitUntil(5_000) { !present("futaber-tab") }
            androidx.test.espresso.Espresso.pressBack()
            waitForTag("futaber-catalog-item")
            // L-B5: the catalog row's sheet of a thread that has been read (「履歴から削除」 is shown only then).
            rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
            waitForTag("futaber-post-actions")
            shot("sheets-$theme-catalog-row-read")
            rule.onNodeWithTag("futaber-action-cancel").performClick()
            rule.waitUntil(5_000) { !present("futaber-post-actions") }
        }
    }
}
