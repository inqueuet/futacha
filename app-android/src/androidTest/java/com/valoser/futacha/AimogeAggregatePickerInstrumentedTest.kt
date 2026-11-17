@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.ui.aimoge.AimogeAddBoardsDialog
import com.valoser.futacha.shared.ui.board.AddBoardDialog
import com.valoser.futacha.shared.ui.compat.CompatBoardEditDialog
import com.valoser.futacha.shared.ui.ftutter.FtutterAddBoardDialog
import com.valoser.futacha.shared.ui.futaber.FutaberAddBoardAlert
import org.junit.*
import org.junit.Assert.*

/** Local /boards fixtures only; nothing is posted and no real board list is changed. */
class AimogeAggregatePickerInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val first = AimogeBoardInfo("one", "板一", "https://nijiurachan.net/b/one/")
    private val second = AimogeBoardInfo("two", "板二", "https://nijiurachan.net/b/two/", defaultEnabled = false)
    private val adult = AimogeBoardInfo("adult", "成人の板", "https://nijiurachan.net/b/adult/", r18 = true, defaultEnabled = false)
    private val hidden = AimogeBoardInfo("hidden", "非表示の板", "https://nijiurachan.net/b/hidden/", defaultVisible = false)
    private val sample = listOf(first, second, adult, hidden)
    private val loader: suspend () -> List<AimogeBoardInfo> = { sample }
    private val added = mutableListOf<String>()
    @Before fun on() { AimogeAvailability.sitesEnabled = true }
    @After fun clean() {
        rule.runOnUiThread { rule.activity.setContent {} }
        AimogeAvailability.sitesEnabled = false
    }

    private fun waitForPicker() {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("aimoge-board-one").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("aimoge-board-one").assertIsOn()
        rule.onNodeWithTag("aimoge-board-two").assertIsOff()
        rule.onAllNodesWithTag("aimoge-board-hidden").assertCountEquals(0)
        assertTrue(added.isEmpty())
    }
    private fun chooseTwoAndConfirm() {
        waitForPicker()
        rule.onNodeWithTag("aimoge-board-two").performClick().assertIsOn()
        rule.onNodeWithTag("aimoge-add-confirm").performClick()
        rule.runOnIdle { assertEquals(listOf(first.url, second.url), added) }
    }

    @Test fun futachaMixUrlOpensPickerAndAddsOnlyConfirmedBoards() {
        rule.setContent { MaterialTheme {
            AddBoardDialog(emptyList(), onDismiss = {}, onSubmit = { _, url -> added += url },
                onBulkSubmit = { boards -> added += boards.map { it.second } }, loadAimogeBoards = loader)
        } }
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("https://nijiurachan.net/mix/")
        rule.onNodeWithText("追加", useUnmergedTree = true).performClick()
        chooseTwoAndConfirm()
    }

    @Test fun toshiakiOverallUrlOpensPicker() {
        rule.setContent { MaterialTheme {
            CompatBoardEditDialog("新しい板の追加", "", "", true, emptyList(), onDismiss = {},
                onConfirm = { _, url, _ -> added += url }, onConfirmBoards = { boards -> added += boards.map { it.url } },
                loadAimogeBoards = loader)
        } }
        rule.onNodeWithTag("compat-board-url-input").performTextInput("https://nijiurachan.net/b/")
        rule.onNodeWithText("追加する").performClick()
        chooseTwoAndConfirm()
    }

    @Test fun futaberTopUrlOpensPicker() {
        rule.setContent { MaterialTheme {
            FutaberAddBoardAlert(emptyList(), onAddBoard = { _, url -> added += url }, onDismiss = {}, loadAimogeBoards = loader)
        } }
        rule.onNodeWithTag("futaber-add-board-url").performTextReplacement("https://nijiurachan.net/")
        rule.onNodeWithTag("futaber-add-board-submit").performClick()
        chooseTwoAndConfirm()
    }

    @Test fun ftutterMixUrlOpensPicker() {
        rule.setContent { MaterialTheme {
            FtutterAddBoardDialog(emptyList(), null, onDismiss = {}, onAdd = { added += it.url },
                onBulkAdd = null, onNotice = {}, loadAimogeBoards = loader)
        } }
        rule.onNodeWithTag("ftutter-add-board-input").performTextInput("https://nijiurachan.net/mix")
        rule.onNodeWithTag("ftutter-add-board-ok").performClick()
        chooseTwoAndConfirm()
    }

    @Test fun registeredBoardsAreExcludedAndR18RequiresConfirmation() {
        rule.setContent { MaterialTheme {
            AimogeAddBoardsDialog(setOf(first.url), onAdd = { boards -> added += boards.map { it.url } }, onDismiss = {}, loadBoards = loader)
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("aimoge-board-adult").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("aimoge-board-one").assertCountEquals(0)
        rule.onNodeWithTag("aimoge-add-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("aimoge-board-adult").performClick()
        rule.onNodeWithTag("aimoge-add-confirm").performClick()
        rule.onNodeWithTag("aimoge-r18-confirm").assertIsDisplayed()
        assertTrue(added.isEmpty())
        rule.onNodeWithTag("aimoge-r18-cancel").performClick()
        assertTrue(added.isEmpty())
        rule.onNodeWithTag("aimoge-add-confirm").performClick()
        rule.onNodeWithTag("aimoge-r18-confirm").performClick()
        rule.runOnIdle { assertEquals(listOf(adult.url), added) }
    }

    @Test fun failedLoadingCanRetryAndCancellingAddsNothing() {
        var attempts = 0
        var shown by mutableStateOf(true)
        val retryLoader: suspend () -> List<AimogeBoardInfo> = { attempts++; if (attempts == 1) error("offline") else sample }
        rule.setContent { MaterialTheme {
            if (shown) AimogeAddBoardsDialog(emptySet(), onAdd = { boards -> added += boards.map { it.url } },
                onDismiss = { shown = false }, loadBoards = retryLoader)
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("再読み込み").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("再読み込み").performClick()
        waitForPicker()
        rule.onNodeWithText("キャンセル").performClick()
        rule.onAllNodesWithTag("aimoge-add-boards").assertCountEquals(0)
        rule.runOnIdle { assertTrue(added.isEmpty()); assertEquals(2, attempts) }
    }
}
