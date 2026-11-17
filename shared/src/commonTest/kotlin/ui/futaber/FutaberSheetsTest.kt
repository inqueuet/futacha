package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.Alignment
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.Post
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L-B4「シート・アラート・吹き出しの部品」(docs/futaber-evolution-look-2026-10-31.md §2.1・§2.11・§3.6): the action
 * sheet's heights (the original's items at their natural height and the extras inside, キャンセル always on screen, large
 * text and the keyboard), the bubbles' veils (F01), and the pure parts of the alert, the HUD, the toast, the popover and
 * the black callout menu.
 */
class FutaberSheetsTest {
    // iPhone SE 3 in pt (density 1 here): the status area 20, no bottom inset, the sheet's edge 8 at the top and the bottom.
    private val seAvailable = 667 - 20 - FUTABER_SHEET_EDGE_DP - 0 - FUTABER_SHEET_EDGE_DP
    private val row = FUTABER_SHEET_ROW_DP
    private val gap = FUTABER_SHEET_GAP_DP
    private val title = FUTABER_SHEET_TITLE_DP

    @Test fun theOwnRowsStopAtTheLastItemThatIsNotAnExtra() {
        // 7 of the original, then 3 extras: the group stops after the 7th.
        val heights = List(10) { row }
        val extras = List(7) { false } + List(3) { true }
        assertEquals(7 * row, futaberSheetOwnRowsHeight(heights, extras))
        // This app's own items among the original's (NG登録 and マーク, before そうだね) are shown with them (B5 moves them).
        val interleaved = List(4) { false } + listOf(false, false) + List(3) { false } + List(5) { true }
        assertEquals(9 * row, futaberSheetOwnRowsHeight(List(14) { row }, interleaved))
        // No extra, or no item of the original (a sheet of this app's own): every row.
        assertEquals(5 * row, futaberSheetOwnRowsHeight(List(5) { row }, List(5) { false }))
        assertEquals(5 * row, futaberSheetOwnRowsHeight(List(5) { row }, List(5) { true }))
        // A taller row (two lines) counts at its own height.
        assertEquals(row + 80, futaberSheetOwnRowsHeight(listOf(row, 80, row), listOf(false, false, true)))
        assertEquals(0, futaberSheetOwnRowsHeight(emptyList(), emptyList()))
    }

    /** M47: the original's 7 items, the heading and キャンセル on an SE, as futaber lays them out (511.5pt). */
    @Test fun theOriginalsSevenItemsAndCancelFitAnSE() {
        val heights = futaberSheetHeights(seAvailable, cancel = row, gap = gap, title = title, ownRows = 7 * row)
        assertEquals(title, heights.title)
        assertEquals(7 * row, heights.rows)
        assertEquals(row, heights.cancel)
        assertEquals(title + 7 * row + gap + row, heights.total)
        assertTrue(heights.total <= seAvailable)
        // The post sheet before L-B5 (the 7 with NG登録 and マーク among them: 9 rows) also fits without scrolling.
        val today = futaberSheetHeights(seAvailable, cancel = row, gap = gap, title = title, ownRows = 9 * row)
        assertEquals(9 * row, today.rows)
        assertTrue(today.total <= seAvailable, "${today.total} > $seAvailable")
    }

    /** §2.11 (4): with large text the rows and キャンセル grow; the group scrolls and キャンセル stays whole on screen. */
    @Test fun cancelStaysOnScreenWithLargeText() {
        listOf(1.3f, 2f, 3f).forEach { scale ->
            val rowHeight = (row * scale).toInt()
            val heights = futaberSheetHeights(seAvailable, cancel = rowHeight, gap = gap, title = (title * scale).toInt(), ownRows = 9 * rowHeight)
            assertEquals(rowHeight, heights.cancel, "scale $scale")
            assertTrue(heights.total <= seAvailable, "scale $scale: ${heights.total} > $seAvailable")
            assertTrue(heights.rows > 0, "scale $scale: no room for the rows")
        }
        // The Android window of the instrumented test (667dp, a 24dp status bar and a 48dp navigation bar).
        val android = 667 - 24 - 48 - 2 * FUTABER_SHEET_EDGE_DP
        val sheet = futaberSheetHeights(android, cancel = row, gap = gap, title = title, ownRows = 7 * row)
        assertEquals(7 * row, sheet.rows)
        // A window too short even for キャンセル gives it all the room.
        val tiny = futaberSheetHeights(40, cancel = row, gap = gap, title = title, ownRows = 7 * row)
        assertEquals(40, tiny.cancel)
        assertEquals(0, tiny.rows)
        assertEquals(40, tiny.total)
    }

    /** The sheet keeps above the keyboard: the writing screen's tools with the SE's keyboard (about 260pt) up. */
    @Test fun theToolsSheetFitsAboveTheKeyboard() {
        val withKeyboard = 667 - 20 - FUTABER_SHEET_EDGE_DP - 260 - FUTABER_SHEET_EDGE_DP
        val handwritingOnly = futaberSheetHeights(withKeyboard, cancel = row, gap = gap, title = title, ownRows = row)
        assertEquals(row, handwritingOnly.rows)
        assertTrue(handwritingOnly.total <= withKeyboard)
        val allTools = futaberSheetHeights(withKeyboard, cancel = row, gap = gap, title = title, ownRows = 6 * row)
        assertTrue(allTools.total <= withKeyboard)
        assertTrue(allTools.rows < 6 * row, "the tools scroll in what is left above the keyboard")
    }

    @Test fun aLongHeadingTakesAtMostItsShare() {
        val heights = futaberSheetHeights(seAvailable, cancel = row, gap = gap, title = 2000, ownRows = 7 * row)
        val groupMax = seAvailable - row - gap
        assertEquals((groupMax * FUTABER_SHEET_TITLE_MAX_FRACTION).toInt(), heights.title)
        assertEquals(groupMax - heights.title, heights.rows)
        assertTrue(heights.total <= seAvailable)
    }

    /** The extras are marked where they are made, so the sheets stop at the items the original has (§2.1). */
    @Test fun theExtensionsItemsAreExtrasAndTheOriginalsAreNot() {
        val post = Post(id = "1", author = null, subject = null, timestamp = "26/10/16(金)21:19:42 ID:abcd", messageHtml = "本文", imageUrl = null, thumbnailUrl = null)
        assertTrue(futaberPostSheetActions(post, {}, {}, {}, emptyList()).none { it.extra || it.destructive })
        val ops = FutaberPostDetailOps(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        val detail = futaberPostDetailActions(true, ops, post, listOf(post), {}, {}, { _, _, _ -> })
        assertTrue(detail.isNotEmpty() && detail.all { it.extra })
        // L-B5: the extension keeps save and share; its other three are the original's items of the picture sheet.
        val picture = futaberImageMenuExtraActions(true, "https://may.2chan.net/b/src/1.jpg", null)
        assertEquals(2, picture.size)
        assertTrue(picture.all { it.extra })
        val item = CatalogItem(id = "123", threadUrl = "https://may.2chan.net/b/res/123.htm", title = "t", thumbnailUrl = null,
            fullImageUrl = "https://f/1.jpg", replyCount = 1)
        assertTrue(assertNotNull(futaberCatalogImageNgAction(true, item, "key", true) {}).extra)
    }

    /** F01: one veil on the thread, and each bubble one veil (20%) darker per bubble over it. */
    @Test fun aBubbleUnderOthersIsOneVeilDarkerPerBubble() {
        assertEquals(0f, futaberBubbleVeilAlpha(0))
        assertTrue(abs(futaberBubbleVeilAlpha(1) - 0.2f) < 1e-4f)
        // White under two bubbles: 255 × 0.8 × 0.8 = 163.
        assertTrue(abs(futaberBubbleVeilAlpha(2) - 0.36f) < 1e-4f)
        assertTrue(futaberBubbleVeilAlpha(5) < 1f)
        // The kinds the later batches open (the search, many replies, from the viewer) are there with their names.
        assertEquals(listOf("検索結果", "返信が多いレス", "ビューアのレス"),
            listOf(FutaberQuoteKind.Search, FutaberQuoteKind.ManyReplies, FutaberQuoteKind.FromViewer).map { it.label })
    }

    @Test fun theScrollBarOfAPlainColumn() {
        assertNull(futaberScrollStateThumb(400f, 0f, 0f, 36f))
        val top = assertNotNull(futaberScrollStateThumb(400f, 400f, 0f, 36f))
        assertEquals(0f, top.top)
        assertEquals(200f, top.height)
        val end = assertNotNull(futaberScrollStateThumb(400f, 400f, 400f, 36f))
        assertEquals(200f, end.top)
        // A long content keeps the bar at least its least length.
        assertEquals(36f, assertNotNull(futaberScrollStateThumb(400f, 100_000f, 0f, 36f)).height)
    }

    // ---- The alert ----

    private fun button(id: String, style: FutaberAlertStyle = FutaberAlertStyle.Default, label: String = id) =
        FutaberAlertButton(id, label, style) {}

    @Test fun anAlertsButtonsGoSideBySideOrStackedWithCancelWhereIosPutsIt() {
        val two = listOf(button("ok", label = "OK"), button("cancel", FutaberAlertStyle.Cancel, "キャンセル"))
        assertEquals(listOf("cancel", "ok"), futaberAlertButtonOrder(two).map { it.id })
        assertFalse(futaberAlertButtonsStacked(two))
        val mht = listOf(button("thumbs", label = "サムネイルのみ"), button("cancel", FutaberAlertStyle.Cancel, "キャンセル"),
            button("all", label = "本体画像もダウンロードする"))
        assertEquals(listOf("thumbs", "all", "cancel"), futaberAlertButtonOrder(mht).map { it.id })
        assertTrue(futaberAlertButtonsStacked(mht))
        // Two buttons whose label does not fit half the alert are stacked too.
        assertTrue(futaberAlertButtonsStacked(listOf(button("cancel", FutaberAlertStyle.Cancel, "キャンセル"), button("all", label = "本体画像もダウンロードする"))))
        assertEquals(10, futaberAlertLabelWidth("キャンセル"))
        assertEquals(2, futaberAlertLabelWidth("OK"))
        // Back presses キャンセル; without one, nothing is pressed.
        assertEquals("cancel", futaberAlertBackButton(mht)?.id)
        assertNull(futaberAlertBackButton(listOf(button("ok"))))
    }

    @Test fun theHudAndTheToastSayWhatTheyShow() {
        assertEquals("読み込み中", futaberHudDescription(FUTABER_HUD_LABEL))
        assertEquals("保存しています", futaberHudDescription("保存しています"))
        assertEquals(104, FUTABER_HUD_WIDTH_DP)
        assertEquals(98, FUTABER_HUD_HEIGHT_DP)
        assertEquals("新着 0", futaberNewPostsToastText(0))
        assertEquals("新着 12", futaberNewPostsToastText(12))
        assertEquals("新着 0", futaberNewPostsToastText(-3))
        assertEquals(2_500L, FUTABER_TOAST_MILLIS)
        assertEquals(Alignment.BottomEnd, futaberToastAlignment(FutaberToastPosition.BottomEnd))
        assertEquals(Alignment.Center, futaberToastAlignment(FutaberToastPosition.Center))
        assertEquals(Alignment.TopCenter, futaberToastAlignment(FutaberToastPosition.Top))
    }

    // ---- The popover and the callout ----

    /** V29: the popover opens over the bottom bar's button and points at it; near a side it moves in, the arrow stays. */
    @Test fun aPopoverOpensOverItsButtonAndKeepsPointingAtIt() {
        val placed = futaberArrowPlacement(anchorX = 37f, anchorTop = 627f, anchorBottom = 667f, width = 140f, height = 218f,
            screenWidth = 375f, screenHeight = 667f, margin = 8f, arrow = 10f, arrowInset = 16f)
        assertTrue(placed.above)
        assertEquals(627f - 10f - 218f, placed.top)
        assertEquals(8f, placed.left)
        assertEquals(37f, placed.arrowX)
        // An anchor at the top opens it under the anchor.
        val under = futaberArrowPlacement(anchorX = 187f, anchorTop = 20f, anchorBottom = 64f, width = 140f, height = 218f,
            screenWidth = 375f, screenHeight = 667f, margin = 8f, arrow = 10f, arrowInset = 16f)
        assertFalse(under.above)
        assertEquals(74f, under.top)
        assertEquals(187f - 70f, under.left)
        // An anchor at the very edge: the arrow is kept off the corner.
        val edge = futaberArrowPlacement(anchorX = 2f, anchorTop = 627f, anchorBottom = 667f, width = 140f, height = 218f,
            screenWidth = 375f, screenHeight = 667f, margin = 8f, arrow = 10f, arrowInset = 16f)
        assertEquals(24f, edge.arrowX)
    }

    /** The camera's black menu: over the camera at the far left, 10 from the side, the arrow at the camera (90-08). */
    @Test fun theCalloutStartsTenFromTheSideOverTheCamera() {
        val placed = futaberCalloutPlacement(FutaberAnchor(x = 26f, top = 363f, bottom = 407f), width = 305f, height = 36f,
            screenWidth = 375f, screenHeight = 667f, density = 1f)
        assertTrue(placed.above)
        assertEquals(10f, placed.left)
        assertEquals(363f - FUTABER_CALLOUT_ARROW_DP - 36f, placed.top)
        assertEquals(26f, placed.arrowX)
        // Wider than the screen: it stays at the margin (it scrolls inside).
        val wide = futaberCalloutPlacement(FutaberAnchor(x = 200f, top = 363f, bottom = 407f), width = 500f, height = 36f,
            screenWidth = 375f, screenHeight = 667f, density = 1f)
        assertEquals(10f, wide.left)
    }
}
