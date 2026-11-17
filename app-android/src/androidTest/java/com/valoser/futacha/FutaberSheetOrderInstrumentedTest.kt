@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.test.espresso.Espresso
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.*
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.futaber.*
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.*
import org.junit.Assert.*

/**
 * L-B5「シートの並び」(docs/futaber-evolution-look-2026-10-31.md §1.3 M23・M24・G28) on Android with a local page: the
 * copy panel, the old instant copy behind its switch, the report asked with the original's sheet and the IP's long
 * press. Nothing reaches a server: the repository is a fake that records what would have been sent.
 */
class FutaberSheetOrderInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val board = BoardSummary("sheet-order", "シートの並び", "test", "https://may.2chan.net/27/futaba.php", "")
    private val ipA = "IP:106.73.*(enabler.ne.jp)"
    private val posts = listOf(
        Post("2001", author = "名無し", subject = "無題", timestamp = "26/10/16(金)21:19:42 $ipA",
            messageHtml = "最初の本文<br>二行目", imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx45"),
        Post("2002", author = "名無し", subject = "無題", timestamp = "26/10/16(金)22:37:55 IP:130.62.*(transix.jp)",
            messageHtml = "なんまんだぶ<br>お疲れさまでした", imageUrl = null, thumbnailUrl = null, saidaneLabel = "そうだねx28"),
        Post("2003", author = "名無し", subject = "無題", timestamp = "26/10/17(土)15:26:11 $ipA",
            messageHtml = "同じ人の本文", imageUrl = null, thumbnailUrl = null)
    ) + (2004..2030).map {
        Post("$it", author = "名無し", subject = "無題", timestamp = "26/10/18(日)00:00:00 IP:60.151.*(bbtec.net)",
            messageHtml = "body-$it", imageUrl = null, thumbnailUrl = null)
    }
    private val page = ThreadPage("2001", null, "28年5月頃消えます", null, posts)
    private val deletions = CopyOnWriteArrayList<String>()
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page)
        override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
            deletions += postId
        }
    }
    private val opened = CopyOnWriteArrayList<String>()
    private val headers = mutableStateListOf<String>()
    private var display by mutableStateOf<FutaberDisplaySettings>(FutaberDisplaySettings().extensionsOff())

    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle() }

    private fun present(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun clipboard(): String {
        var text = ""
        rule.runOnUiThread {
            text = rule.activity.getSystemService(android.content.ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }
        return text
    }

    private fun open() {
        rule.setContent {
            CompositionLocalProvider(LocalFutaberDisplaySettings provides display) {
                FutaberTheme(FutaberThemeMode.Light) {
                    Box(Modifier.fillMaxSize()) {
                        FutaberThreadScreen(board, FutaberThreadRef(board.id, "2001", "シートの並び", "", posts.size), repository,
                            isTabbed = false, onToggleTab = {}, onOpenManage = {}, onOpenPost = { opened += it }, refreshSignal = 0,
                            quoteMode = false, onQuoteLine = {}, onOpenDrawer = {}, drawerGestureEnabled = false,
                            onPageLoaded = {}, onScrollSettled = { _, _, _, _ -> }, ngWords = emptyList(), ngHeaders = headers.toList(),
                            ngRules = emptyList(), onChangeNgWords = {},
                            onChangeNgHeaders = { transform -> val next = transform(headers.toList()); headers.clear(); headers.addAll(next) },
                            onBack = {})
                    }
                }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodes(postLine("なんまんだぶ")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** A line of a post's body (the title row above the posts shows the first post's first line too). */
    private fun postLine(text: String) = hasText(text) and hasAnyAncestor(hasTestTag("futaber-post"))

    private fun longPressText(text: String) {
        rule.onNode(postLine(text)).performTouchInput { longClick() }
        rule.waitUntil(5_000) { present("futaber-post-actions") }
    }

    /** M23: コピー opens the original's copy panel, the body selected; its menu copies, quotes and adds to NG on the selection. */
    @Test fun copyOpensTheOriginalsCopyPanelWithTheBodySelected() {
        open()
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-panel") }
        assertFalse(present("futaber-post-actions"))
        val header = "無題 名無し 26/10/16(金)22:37:55 IP:130.62.*(transix.jp) No.2002 そうだねx28"
        val body = "なんまんだぶ\nお疲れさまでした"
        val field = rule.onNodeWithTag("futaber-copy-text").fetchSemanticsNode()
        assertEquals("$header\n$body", field.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(header.length + 1, header.length + 1 + body.length), field.config[SemanticsProperties.TextSelectionRange])
        // The panel is the lower half; × at the top left, すべてコピー at the top right.
        val panel = bounds("futaber-copy-panel")
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.height / 2f, panel.height, root.height * 0.06f)
        assertTrue(bounds("futaber-copy-close").left - panel.left < 4f)
        assertTrue(panel.right - bounds("futaber-copy-all").right < 4f)
        // コピー: the selection (the body); the panel stays.
        rule.onNodeWithTag("futaber-copy-menu-copy").assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { clipboard() == body }
        assertTrue(present("futaber-copy-panel"))
        // すべてコピー: the header and the body, and the panel closes.
        rule.onNodeWithTag("futaber-copy-all").performClick()
        rule.waitUntil(5_000) { clipboard() == "$header\n$body" && !present("futaber-copy-panel") }
        // 引用: the selection quoted into the writing screen.
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-menu-quote") }
        rule.onNodeWithTag("futaber-copy-menu-quote").performClick()
        rule.waitUntil(5_000) { opened.contains(">なんまんだぶ\n>お疲れさまでした") && !present("futaber-copy-panel") }
        // NGに追加: the NG dialog with the selection ready on the word list.
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-menu-ng") }
        rule.onNodeWithTag("futaber-copy-menu-ng").performClick()
        rule.waitUntil(5_000) { present("futaber-ng-input") }
        assertEquals(body, rule.onNodeWithTag("futaber-ng-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        rule.onNodeWithTag("futaber-ng-section-Words").assertIsSelected()
        rule.onNodeWithText("閉じる").performClick()
        rule.waitUntil(5_000) { !present("futaber-ng-input") }
        // × and the system's Back close it.
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-panel") }
        rule.onNodeWithTag("futaber-copy-close").performClick()
        rule.waitUntil(5_000) { !present("futaber-copy-panel") }
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-panel") }
        Espresso.pressBack()
        rule.waitUntil(5_000) { !present("futaber-copy-panel") }
    }

    /** M23: with 「レスの「コピー」で本文をすぐコピー」 on (設定→スレッドの末尾), コピー copies the body at once as before. */
    @Test fun theOldInstantCopyStaysBehindItsSwitch() {
        display = FutaberDisplaySettings(instantCopy = true)
        open()
        longPressText("同じ人の本文")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { clipboard() == "同じ人の本文" && !present("futaber-post-actions") }
        assertFalse(present("futaber-copy-panel"))
    }

    /** M24: 「不適切な投稿を通報」 asks with the sheet 「レス:N」 / 「delを送信」; キャンセル sends nothing, delを送信 sends del once. */
    @Test fun reportAsksWithTheOriginalsSheetAndSendsDelOnlyFromIt() {
        open()
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-report").performClick()
        rule.waitUntil(5_000) { present("futaber-action-del-send") }
        rule.onNode(hasText("レス:1") and hasAnyAncestor(hasTestTag("futaber-sheet-title"))).assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-del-send").assertTextEquals("delを送信")
        assertFalse(present("futaber-remote-confirm"))
        // One item and キャンセル (the extension's NG option is off).
        assertFalse(present("futaber-action-del-send-ng"))
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-action-del-send") }
        rule.waitForIdle()
        assertTrue(deletions.isEmpty())
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-report").performClick()
        rule.waitUntil(5_000) { present("futaber-action-del-send") }
        rule.onNodeWithTag("futaber-action-del-send").performClick()
        rule.waitUntil(5_000) { deletions.toList() == listOf("2002") }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("削除依頼を送信しました", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /**
     * G28: a long press on a header's IP opens 「IP:…」 / 「IPをNG」; IPをNG adds the IP to the shared 「NGネーム・ID等」 and
     * every post of that IP hides. A long press elsewhere on the post still opens the post's sheet.
     */
    @Test fun aLongPressOnTheIpOpensItsSheetAndIpNgHidesThatPoster() {
        open()
        // Elsewhere on the post: the post's sheet, not the IP's.
        longPressText("最初の本文")
        rule.onNodeWithTag("futaber-action-quote").assertIsDisplayed()
        assertFalse(present("futaber-action-ip-ng"))
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-post-actions") }
        // On the IP: the IP's sheet.
        rule.onAllNodesWithTag("futaber-ip-press", useUnmergedTree = true)[0].performTouchInput { longClick() }
        rule.waitUntil(5_000) { present("futaber-action-ip-ng") }
        rule.onNode(hasText(ipA) and hasAnyAncestor(hasTestTag("futaber-sheet-title"))).assertIsDisplayed()
        rule.onNodeWithTag("futaber-action-ip-ng").assertTextEquals("IPをNG")
        assertFalse(present("futaber-action-quote"))
        rule.onNodeWithTag("futaber-action-ip-ng").performClick()
        rule.waitUntil(5_000) { headers.toList() == listOf(ipA) }
        rule.waitUntil(10_000) {
            rule.onAllNodes(postLine("最初の本文")).fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodes(postLine("同じ人の本文")).fetchSemanticsNodes().isEmpty()
        }
        rule.onNode(postLine("なんまんだぶ")).assertIsDisplayed()
        rule.onNodeWithTag("futaber-remote-notice").assertTextContains(ipA, substring = true)
    }

    /** G28: the IP's long press does not swallow a tap: with the tap scroll on, a tap on an IP in the lower third scrolls. */
    @Test fun aTapOnTheIpStillReachesTheTapScroll() {
        display = FutaberDisplaySettings(tapScroll = true)
        open()
        val list = bounds("futaber-thread-list")
        val lowerThird = list.top + list.height * 2f / 3f
        val targets = rule.onAllNodesWithTag("futaber-ip-press", useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }.filter { it.center.y > lowerThird + 4f && it.bottom < list.bottom - 4f }
        assertTrue("an IP in the lower third of $list", targets.isNotEmpty())
        val before = rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        rule.onRoot().performTouchInput { click(Offset(targets[0].center.x, targets[0].center.y)) }
        rule.waitUntil(5_000) {
            rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before + 10f
        }
        assertFalse(present("futaber-action-ip-ng"))
    }

    /** Pictures of the copy panel, the report's sheet and the IP's sheet for the side-by-side (nothing is sent: each is closed). */
    @Test fun captureTheNewSheetsForReview() {
        fun shot(name: String) {
            rule.waitForIdle()
            val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/futaber-sheet-order-$name.png")
            java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
            pfd.close()
        }
        open()
        longPressText("なんまんだぶ")
        shot("post")
        rule.onNodeWithTag("futaber-action-copy").performClick()
        rule.waitUntil(5_000) { present("futaber-copy-menu") }
        shot("copy-panel")
        rule.onNodeWithTag("futaber-copy-close").performClick()
        rule.waitUntil(5_000) { !present("futaber-copy-panel") }
        longPressText("なんまんだぶ")
        rule.onNodeWithTag("futaber-action-report").performClick()
        rule.waitUntil(5_000) { present("futaber-action-del-send") }
        shot("report")
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-action-del-send") }
        rule.onAllNodesWithTag("futaber-ip-press", useUnmergedTree = true)[0].performTouchInput { longClick() }
        rule.waitUntil(5_000) { present("futaber-action-ip-ng") }
        shot("ip")
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { !present("futaber-action-ip-ng") }
        assertTrue(deletions.isEmpty())
    }
}
