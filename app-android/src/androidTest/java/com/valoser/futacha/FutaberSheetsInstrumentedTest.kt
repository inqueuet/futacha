@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.*
import kotlin.math.abs
import org.junit.*
import org.junit.Assert.*

/**
 * L-B4「シート・アラート・吹き出しの部品」(docs/futaber-evolution-look-2026-10-31.md §2.1・§2.11・§3.6) on Android, with
 * local pages and no posting (そうだね・レス削除・通報 are never pressed). The sheets are measured in a 375×667dp window
 * (an iPhone SE's height, as `FutaberQuoteViewportInstrumentedTest` composes its part alone).
 */
class FutaberSheetsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("sheets", "シート", "test", "https://may.2chan.net/27/futaba.php", "")

    private val posts = listOf(
        Post("1001", author = null, subject = null, timestamp = "26/10/16(金)21:19:42", messageHtml = "元の本文<br>二行目",
            imageUrl = null, thumbnailUrl = null),
        Post("1002", author = null, subject = null, timestamp = "26/10/16(金)22:37:55", messageHtml = "&gt;元の本文<br>返信その一",
            imageUrl = null, thumbnailUrl = null, quoteReferences = listOf(QuoteReference(">元の本文", listOf("1001")))),
        Post("1003", author = null, subject = null, timestamp = "26/10/16(金)23:00:00", messageHtml = "&gt;返信その一<br>返信その二",
            imageUrl = null, thumbnailUrl = null, quoteReferences = listOf(QuoteReference(">返信その一", listOf("1002"))))
    ) + (1100..1120).map { Post("$it", author = null, subject = null, timestamp = "26/10/26(月)00:00:00", messageHtml = "body-$it", imageUrl = null, thumbnailUrl = null) }
    private val page = ThreadPage("1001", null, "28年5月頃消えます", null, posts)
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
    }

    /** The original's post sheet (60-01): 7 items, then this app's extras. */
    private val ran = mutableStateListOf<String>()
    private fun originalItems() = listOf("quote" to "引用", "quote-number" to "No.引用", "quote-last" to "最終行引用", "copy" to "コピー",
        "delete-own" to "レス削除", "report" to "不適切な投稿を通報", "saidane" to "そうだね")
        .map { (id, label) -> FutaberPostAction(id, label) { ran += id } }
    private fun extras() = listOf("select-copy" to "項目を選んでコピー", "select-search" to "語句を選んでGoogle検索", "quick-quote" to "クイック引用返信")
        .map { (id, label) -> FutaberPostAction(id, label, extra = true) { ran += id } }

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }

    private val density get() = rule.density.density
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun present(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private var fontScale by mutableStateOf(1f)
    private var shown by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var theme by mutableStateOf(FutaberThemeMode.Light)
    private var display by mutableStateOf(FutaberDisplaySettings())
    private var started = false
    /** The navigation bar's height as the composition reads it (px). */
    private var navigationBottom = 0

    /** [content] in a 375×667dp window at the top left (the system's insets apply as on a phone of that height). */
    private fun window(content: @Composable () -> Unit) {
        if (!started) {
            started = true
            rule.setContent {
                val base = LocalDensity.current
                navigationBottom = WindowInsets.navigationBars.getBottom(base)
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale), LocalFutaberDisplaySettings provides display) {
                    FutaberTheme(theme) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                            Box(Modifier.requiredSize(375.dp, 667.dp).testTag("test-window")) { shown?.invoke() }
                        }
                    }
                }
            }
        }
        rule.runOnUiThread { shown = content }
        rule.waitForIdle()
    }

    private fun assertInside(tag: String, window: Rect) {
        val b = bounds(tag)
        assertTrue("$tag $b is not inside $window", b.top >= window.top - 1f && b.bottom <= window.bottom + 1f && b.left >= window.left - 1f && b.right <= window.right + 1f)
    }

    // ---- M47 / §2.11 ----

    /** The original's 7 items, the heading and キャンセル all show on a 667dp screen; the extras are inside the group. */
    @Test fun theOriginalsItemsAndCancelAreAllOnA667dpScreen() {
        window { FutaberActionSheet("レス:0", originalItems() + extras(), onDismiss = { ran += "dismiss" }) }
        val window = bounds("test-window")
        listOf("quote", "quote-number", "quote-last", "copy", "delete-own", "report", "saidane").forEach { id ->
            rule.onNodeWithTag("futaber-action-$id").assertIsDisplayed()
            assertInside("futaber-action-$id", window)
        }
        rule.onNodeWithText("レス:0").assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-cancel").assertIsDisplayed()
        assertInside("futaber-action-cancel", window)
        // iOS's measures: rows 57, キャンセル 57, 8 between, 8 from the sides, 8 over the navigation bar.
        val saidane = bounds("futaber-action-saidane")
        val cancel = bounds("futaber-action-cancel")
        assertEquals(57f, saidane.height / density, 1f)
        assertEquals(57f, cancel.height / density, 1f)
        assertEquals(8f, (cancel.top - saidane.bottom) / density, 1f)
        assertEquals(8f, (cancel.left - window.left) / density, 1f)
        assertEquals(8f, (window.bottom - navigationBottom - cancel.bottom) / density, 1f)
        // F-B2: the extras are behind the row 「その他の機能」 after そうだね; it opens them as the second step.
        extras().forEach { rule.onNodeWithTag("futaber-action-${it.id}").assertDoesNotExist() }
        rule.onNodeWithTag("futaber-action-more").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithTag("futaber-action-quick-quote").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf("quick-quote"), ran.toList()) }
    }

    /**
     * L-B5 (M21): the thread's own post sheet, in the 375×667dp window with the navigation bar: the original's seven items
     * show in its order without scrolling (NG登録 and マーク, which sat among them, now come after そうだね inside the group).
     */
    @Test fun theThreadsPostSheetHoldsTheOriginalsSevenItemsWithoutScrolling() {
        window {
            FutaberThreadScreen(board, FutaberThreadRef(board.id, "1001", "シート", "", posts.size), repository,
                isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {})
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("futaber-post")[1].performTouchInput { longClick(Offset(width / 2f, height - 4f)) }
        rule.waitUntil(5_000) { present("futaber-post-actions") }
        val window = bounds("test-window")
        val ids = listOf("quote", "quote-number", "quote-last", "copy", "delete-own", "report", "saidane")
        val tops = ids.map { id ->
            rule.onNodeWithTag("futaber-action-$id").assertIsDisplayed()
            assertInside("futaber-action-$id", window)
            bounds("futaber-action-$id").top
        }
        assertEquals(tops.sorted(), tops)
        rule.onNodeWithText("レス:1").assertIsDisplayed()
        // This app's own items are under そうだね, inside the group: they show when it is scrolled.
        rule.onNodeWithTag("futaber-action-ng-register").assertDoesNotExist()
        rule.onNodeWithTag("futaber-action-mark").assertDoesNotExist()
        rule.onNodeWithTag("futaber-action-more").assertIsNotDisplayed()
        rule.onNodeWithTag("futaber-action-more").performScrollTo().assertIsDisplayed()
        assertTrue(bounds("futaber-action-more").top >= bounds("futaber-action-saidane").bottom - 1f)
        rule.onNodeWithTag("futaber-action-more").performClick()
        rule.onNodeWithTag("futaber-action-ng-register").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-post-actions") }
    }

    /** §2.11 (4): font scale 1.3 and the largest font of the app's settings: キャンセル stays whole on the 667dp screen. */
    @Test fun cancelStaysOnScreenWithLargeTextOnThePostSheet() {
        rule.runOnUiThread { fontScale = 1.3f; display = FutaberDisplaySettings(fontSize = FUTABER_FONT_SIZE_MAX) }
        window {
            FutaberThreadScreen(board, FutaberThreadRef(board.id, "1001", "シート", "", posts.size), repository,
                isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {})
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("futaber-post")[1].performTouchInput { longClick(Offset(width / 2f, height - 4f)) }
        rule.waitUntil(5_000) { present("futaber-post-actions") }
        val window = bounds("test-window")
        rule.onNodeWithTag("futaber-action-cancel").assertIsDisplayed()
        assertInside("futaber-action-cancel", window)
        rule.onNodeWithText("レス:1").assertIsDisplayed()
        // Every item of the original can be reached (the ones the group cannot hold scroll inside it).
        listOf("quote", "quote-number", "quote-last", "copy", "delete-own", "report", "saidane").forEach { id ->
            rule.onNodeWithTag("futaber-action-$id").performScrollTo().assertIsDisplayed()
            assertInside("futaber-action-$id", window)
        }
        // M47 (1): the veil covers the bottom bar too; a tap there closes the sheet and does not reach the bar.
        val scrim = bounds("futaber-action-scrim")
        val refresh = bounds("futaber-thread-refresh")
        assertTrue("the veil $scrim does not cover the bar $refresh", scrim.bottom >= refresh.bottom - 1f && scrim.top <= refresh.top)
        rule.onRoot().performTouchInput { click(Offset(refresh.center.x, refresh.center.y)) }
        rule.waitUntil(5_000) { !present("futaber-post-actions") }
        // Android's Back closes it too.
        rule.onAllNodesWithTag("futaber-post")[1].performTouchInput { longClick(Offset(width / 2f, height - 4f)) }
        rule.waitUntil(5_000) { present("futaber-post-actions") }
        Espresso.pressBack()
        rule.waitUntil(5_000) { !present("futaber-post-actions") }
    }

    /** G33: a finger put down on one item, slid onto another and lifted runs nothing; one lifted where it went down runs it. */
    @Test fun liftingOnAnotherItemSelectsNothing() {
        window { FutaberActionSheet("レス:0", originalItems(), onDismiss = { ran += "dismiss" }) }
        val from = bounds("futaber-action-copy")
        val to = bounds("futaber-action-delete-own")
        rule.onRoot().performTouchInput {
            down(from.center)
            moveTo(Offset(from.center.x, (from.center.y + to.center.y) / 2f))
            moveTo(to.center)
            up()
        }
        rule.waitForIdle()
        assertTrue("nothing runs: $ran", ran.isEmpty())
        rule.onNodeWithTag("futaber-post-actions").assertExists()
        rule.onRoot().performTouchInput { down(from.center); up() }
        rule.runOnIdle { assertEquals(listOf("copy"), ran.toList()) }
    }

    /**
     * The writing screen's tools with the keyboard up (L-B4, the tools sheet; L-B8 M37: the camera's black menu that took
     * its place): the menu is over the keyboard and the keyboard stays, so 「手書き」 can be pressed while typing.
     */
    @Test fun theToolsSheetIsAboveTheKeyboard() {
        // In the composition's own measures (this test window is not placed like the app's; the screen itself is checked in
        // FutaberSystemNavigationInstrumentedTest#theCameraMenuOpensAboveTheKeyboard).
        var imeBottom = 0
        var text by mutableStateOf("")
        rule.setContent {
            FutaberTheme(FutaberThemeMode.Light) {
                val density = LocalDensity.current
                imeBottom = WindowInsets.ime.getBottom(density)
                navigationBottom = WindowInsets.navigationBars.getBottom(density)
                val state = rememberFutaberPostToolsState()
                var anchor by androidx.compose.runtime.remember { mutableStateOf(FutaberAnchor(0f, 0f, 0f)) }
                Box(Modifier.fillMaxSize().imePadding()) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        BasicTextField(text, { text = it }, Modifier.testTag("test-field"))
                        // The camera button sits on the tool row over the keyboard (the writing screen's imePadding).
                        TextButton(
                            onClick = { state.cameraMenu = anchor },
                            modifier = Modifier.testTag("test-open").onGloballyPositioned {
                                val b = it.boundsInRoot()
                                anchor = FutaberAnchor(b.left + b.width / 2f, b.top, b.bottom)
                            }
                        ) { Text("添付を開く") }
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    FutaberPostToolsHost(state, FutaberPostTools(), true, "本文", {}, "TEST", {}, {})
                }
            }
        }
        rule.onNodeWithTag("test-field").performClick()
        rule.waitUntil(10_000) { imeBottom > navigationBottom }
        // The keyboard slides in: wait until the system's own keyboard inset stops changing, and measure from it (not
        // from the inset the composition read, so a menu that lags behind the keyboard is caught).
        fun platformIme(): Int {
            var bottom = 0
            rule.runOnUiThread {
                bottom = androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
            }
            return bottom
        }
        var last = -1
        var steady = 0
        rule.waitUntil(10_000) {
            val now = platformIme()
            if (now == last && now > 0) steady++ else steady = 0
            last = now
            Thread.sleep(150)
            steady >= 4
        }
        rule.waitForIdle()
        rule.onNodeWithTag("test-open").performClick()
        rule.waitUntil(5_000) { present("futaber-callout-tool-drawing") }
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val keyboardTop = root.bottom - platformIme()
        val menu = bounds("futaber-callout")
        assertTrue("the menu $menu is under the keyboard (top $keyboardTop, the composition read $imeBottom)", menu.bottom <= keyboardTop + 1f)
        assertTrue("the menu $menu is not over the button", menu.bottom <= bounds("test-open").top + 1f)
        assertTrue("the keyboard went away", platformIme() > 0)
        rule.onNodeWithTag("futaber-callout-outside").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 40f)) }
        rule.waitUntil(5_000) { !present("futaber-callout") }
    }

    // ---- F01 ----

    /** Nested bubbles: both cards are drawn, the lower one darker; only the top takes the tap outside. */
    @Test fun nestedBubblesShowBothCardsTheLowerOneDarker() {
        window {
            FutaberThreadScreen(board, FutaberThreadRef(board.id, "1001", "シート", "", posts.size), repository,
                isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = {}, refreshSignal = 0,
                quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = emptyList(),
                ngRules = emptyList(), onChangeNgWords = {}, onChangeNgHeaders = {}, onBack = {})
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("futaber-reply-count").fetchSemanticsNodes().isNotEmpty() }
        // The replies of the first post (1002), then the quote in 1002 back to the first post.
        rule.onAllNodesWithTag("futaber-reply-count")[0].performClick()
        rule.waitUntil(5_000) { present("futaber-quote-card") }
        val inCard = hasTestTag("futaber-quote-line") and hasAnyAncestor(hasTestTag("futaber-quote-card"))
        rule.waitUntil(5_000) { rule.onAllNodes(inCard).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(inCard)[0].performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().size == 2 }
        rule.waitForIdle()
        Thread.sleep(300) // the bubble is drawn once measured
        assertEquals(1, rule.onAllNodesWithTag("futaber-quote-scrim").fetchSemanticsNodes().size)
        val cards = rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().map { it.boundsInRoot }
        // The lower card stays in sight under its veil: its frame is darker than the top card's frame.
        val image = rule.onRoot().captureToImage().toPixelMap()
        val lower = cards[0]
        val upper = cards[1]
        val lowerPoint = framePoint(lower, upper)
        if (lowerPoint != null) {
            val upperFrame = image.grey(upper.left + 2f * density, upper.top + upper.height / 2f)
            val lowerFrame = image.grey(lowerPoint.x, lowerPoint.y)
            assertTrue("the lower bubble ($lowerFrame) is darker than the top one ($upperFrame)", lowerFrame < upperFrame - 20)
        }
        shotRaw("futaber-sheets-nested-bubbles")
        // A tap outside closes the top one only.
        rule.onNodeWithTag("futaber-quote-scrim").performTouchInput { click(Offset(4f, 4f)) }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-quote-card").fetchSemanticsNodes().size == 1 }
        rule.onNodeWithTag("futaber-quote-close-all").performClick()
        rule.waitUntil(5_000) { !present("futaber-quote-card") }
    }

    /** A point on the frame of [lower] that [upper] does not cover (its left frame band), or null. */
    private fun framePoint(lower: Rect, upper: Rect): Offset? {
        val x = lower.left + 2f * density
        var y = lower.top + 6f * density
        while (y < lower.bottom - 6f * density) {
            if (!upper.contains(Offset(x, y))) return Offset(x, y)
            y += 4f * density
        }
        return null
    }

    private fun PixelMap.grey(x: Float, y: Float): Int {
        val c = this[x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1)]
        return ((c.red + c.green + c.blue) / 3f * 255).toInt()
    }

    // ---- The new parts (§3.6) ----

    @Test fun anAlertsTwoButtonsSitSideBySideAndBackPressesCancel() {
        var field by mutableStateOf("https://")
        window {
            FutaberAlert(
                title = "板の追加", message = "板のURLを入力してください",
                buttons = listOf(FutaberAlertButton("ok", "OK") { ran += "ok" },
                    FutaberAlertButton("cancel", "キャンセル", FutaberAlertStyle.Cancel) { ran += "cancel" }),
                field = FutaberAlertField(field, { field = it })
            )
        }
        // The field takes the focus, and the keyboard's slide moves the alert up over it: measure once it stays put (the two
        // buttons were read in different frames of the slide and looked one above the other).
        var last = Float.NaN
        var steady = 0
        rule.waitUntil(10_000) {
            val top = bounds("futaber-alert-ok").top
            if (top == last) steady++ else steady = 0
            last = top
            Thread.sleep(100)
            steady >= 5
        }
        rule.waitForIdle()
        val ok = bounds("futaber-alert-ok")
        val cancel = bounds("futaber-alert-cancel")
        assertEquals("side by side", ok.top, cancel.top, 1f)
        assertTrue("キャンセル on the left", cancel.right <= ok.left + 1f)
        // A tap outside does nothing (a choice is asked for).
        rule.onNodeWithTag("futaber-alert-scrim").performTouchInput { click(Offset(4f, 4f)) }
        rule.onNodeWithTag("futaber-alert").assertIsDisplayed()
        rule.onNodeWithTag("futaber-alert-field").performTextReplacement("https://may.2chan.net/b/")
        rule.runOnIdle { assertEquals("https://may.2chan.net/b/", field) }
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        rule.runOnIdle { assertEquals(listOf("cancel"), ran.toList()) }
    }

    @Test fun anAlertsThreeButtonsAreStackedWithCancelLast() {
        window {
            FutaberAlert(
                title = "MHT保存", message = "サムネイルのみダウンロードしますか？",
                buttons = listOf(FutaberAlertButton("cancel", "キャンセル", FutaberAlertStyle.Cancel) { ran += "cancel" },
                    FutaberAlertButton("thumbs", "サムネイルのみ") { ran += "thumbs" },
                    FutaberAlertButton("all", "本体画像もダウンロードする") { ran += "all" })
            )
        }
        val thumbs = bounds("futaber-alert-thumbs")
        val all = bounds("futaber-alert-all")
        val cancel = bounds("futaber-alert-cancel")
        assertTrue(thumbs.bottom <= all.top + 1f && all.bottom <= cancel.top + 1f)
        assertEquals(thumbs.left, cancel.left, 1f)
        rule.onNodeWithTag("futaber-alert-all").performClick()
        rule.runOnIdle { assertEquals(listOf("all"), ran.toList()) }
    }

    @Test fun theHudShowsLoadingAndLetsTapsThrough() {
        window {
            Box(Modifier.fillMaxSize()) {
                TextButton(onClick = { ran += "under" }, Modifier.fillMaxSize().testTag("test-under")) { Text("下") }
                FutaberHud()
            }
        }
        rule.onNodeWithTag("futaber-hud").assertIsDisplayed()
        rule.onNodeWithContentDescription("読み込み中").assertExists()
        val hud = bounds("futaber-hud")
        assertTrue(hud.width / density >= FUTABER_HUD_WIDTH_DP - 1 && hud.height / density >= FUTABER_HUD_HEIGHT_DP - 1)
        rule.onRoot().performTouchInput { click(hud.center) }
        rule.runOnIdle { assertEquals(listOf("under"), ran.toList()) }
    }

    @Test fun theToastShowsTheNewPostsAndHides() {
        var message by mutableStateOf<String?>(null)
        window { FutaberToast(message, onHidden = { message = null }, bottomOffset = 88.dp) }
        rule.runOnUiThread { message = futaberNewPostsToastText(3) }
        rule.onNodeWithText("新着 3").assertIsDisplayed()
        val window = bounds("test-window")
        val toast = bounds("futaber-toast")
        assertTrue("at the right", window.right - toast.right < 16f * density)
        assertEquals(96f, (window.bottom - toast.bottom) / density, 1f)
        rule.waitUntil(6_000) { rule.onAllNodesWithText("新着 3").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun thePopoverPointsAtItsButtonAndAnOutsideTapOnlyClosesIt() {
        var open by mutableStateOf(true)
        window {
            Box(Modifier.fillMaxSize()) {
                TextButton(onClick = { ran += "under" }, Modifier.fillMaxSize().testTag("test-under")) { Text("下") }
                if (open) FutaberPopover(
                    FutaberAnchor(x = 37f * density, top = 600f * density, bottom = 640f * density), "表示形式",
                    listOf("row1" to "行１", "row2" to "行２", "grid3" to "横３").map { (id, label) ->
                        FutaberPopoverOption(id, label, selected = id == "row1") { ran += id; open = false }
                    }
                ) { open = false }
            }
        }
        rule.waitForIdle()
        val popover = bounds("futaber-popover")
        assertTrue("over its button", popover.bottom <= 600f * density)
        assertEquals(8f, popover.left / density, 1f)
        rule.onNodeWithTag("futaber-popover-row1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithTag("futaber-popover-row2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        // Outside: it closes and the button under it is not pressed (M52).
        rule.onRoot().performTouchInput { click(Offset(300f * density, 100f * density)) }
        rule.waitUntil(5_000) { !present("futaber-popover") }
        rule.runOnIdle { assertTrue(ran.isEmpty()) }
        rule.runOnUiThread { open = true }
        rule.onNodeWithTag("futaber-popover-row2").performClick()
        rule.runOnIdle { assertEquals(listOf("row2"), ran.toList()) }
    }

    @Test fun theCalloutOpensOverItsAnchorAndAnOutsideTapOnlyClosesIt() {
        var open by mutableStateOf(true)
        window {
            Box(Modifier.fillMaxSize()) {
                TextButton(onClick = { ran += "under" }, Modifier.fillMaxSize().testTag("test-under")) { Text("下") }
                if (open) FutaberCalloutMenu(
                    FutaberAnchor(x = 26f * density, top = 363f * density, bottom = 407f * density),
                    listOf("recent" to "最近の画像", "file" to "ファイル", "library" to "ライブラリ").map { (id, label) ->
                        FutaberCalloutItem(id, label) { ran += id; open = false }
                    }
                ) { open = false }
            }
        }
        rule.waitForIdle()
        val menu = bounds("futaber-callout")
        assertTrue("over the anchor", menu.bottom <= 363f * density)
        assertEquals(FUTABER_CALLOUT_MARGIN_DP.toFloat(), menu.left / density, 1f)
        rule.onRoot().performTouchInput { click(Offset(300f * density, 100f * density)) }
        rule.waitUntil(5_000) { !present("futaber-callout") }
        rule.runOnIdle { assertTrue(ran.isEmpty()) }
        rule.runOnUiThread { open = true }
        rule.onNodeWithTag("futaber-callout-file").performClick()
        rule.runOnIdle { assertEquals(listOf("file"), ran.toList()) }
    }

    /** Pictures of the new parts in both themes for the review (build/l-b4/). */
    @Test fun captureTheComponentsForReview() {
        listOf(FutaberThemeMode.Light to "light", FutaberThemeMode.Dark to "dark").forEach { (mode, name) ->
            rule.runOnUiThread { theme = mode }
            window { FutaberActionSheet("レス:0", originalItems() + extras(), onDismiss = {}) }
            shotRaw("futaber-sheets-$name-post-sheet-667")
            window {
                FutaberAlert("MHT保存", "サムネイルのみダウンロードしますか？", listOf(
                    FutaberAlertButton("thumbs", "サムネイルのみ") {}, FutaberAlertButton("all", "本体画像もダウンロードする") {},
                    FutaberAlertButton("cancel", "キャンセル", FutaberAlertStyle.Cancel) {}))
            }
            shotRaw("futaber-sheets-$name-alert")
            window { Box(Modifier.fillMaxSize()) { FutaberHud(); FutaberToast("新着 3", onHidden = {}, bottomOffset = 88.dp, durationMillis = 60_000) } }
            shotRaw("futaber-sheets-$name-hud-toast")
            window {
                Box(Modifier.fillMaxSize()) {
                    FutaberPopover(FutaberAnchor(37f * density, 600f * density, 640f * density), "表示形式",
                        listOf("行１", "行２", "横３", "横４", "横６", "横８").mapIndexed { i, l -> FutaberPopoverOption("o$i", l, i == 0) {} }) {}
                    FutaberCalloutMenu(FutaberAnchor(26f * density, 363f * density, 407f * density),
                        listOf("最近の画像", "ファイル", "ライブラリ").mapIndexed { i, l -> FutaberCalloutItem("c$i", l) {} }) {}
                }
            }
            shotRaw("futaber-sheets-$name-popover-callout")
        }
    }

    private fun shotRaw(name: String) {
        rule.waitForIdle()
        Thread.sleep(600) // the flash of the scroll bar and the first frame of the boxes
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }
}
