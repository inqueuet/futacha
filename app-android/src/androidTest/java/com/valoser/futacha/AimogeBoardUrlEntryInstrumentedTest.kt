@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.ui.aimoge.AIMOGE_BOARD_URL_DISABLED_MESSAGE
import com.valoser.futacha.shared.ui.aimoge.rememberAimogeBoardUrlAdder
import com.valoser.futacha.shared.ui.board.AddBoardDialog
import org.junit.*
import org.junit.Assert.*

/** あいもげの板は「板を追加」の URL 欄から足す。通信はしない（一覧は差し替え、URL の判定だけを画面で見る）。 */
class AimogeBoardUrlEntryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val aUrl = "https://nijiurachan.net/b/aimg/"

    @After fun off() { AimogeAvailability.sitesEnabled = false }

    private fun showFutachaDialog(existing: List<BoardSummary> = emptyList(), added: MutableList<Pair<String, String>>) {
        rule.setContent {
            MaterialTheme {
                AddBoardDialog(existingBoards = existing, onDismiss = {}, onSubmit = { n, u -> added += n to u }, onBulkSubmit = {})
            }
        }
    }

    @Test fun whileOffTheReasonIsShownAndNothingIsAdded() {
        AimogeAvailability.sitesEnabled = false
        val added = mutableListOf<Pair<String, String>>()
        showFutachaDialog(added = added)
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("https://nijiurachan.net/b/aimg")
        rule.onNodeWithText(AIMOGE_BOARD_URL_DISABLED_MESSAGE).assertIsDisplayed()
        rule.onNodeWithText("追加").assertIsNotEnabled()
        rule.onAllNodesWithTag("aimoge-add-boards-entry").assertCountEquals(0)
        assertTrue(added.isEmpty())
    }

    @Test fun whileOnTheListEntryIsGoneAndADuplicateIsRefused() {
        AimogeAvailability.sitesEnabled = true
        val added = mutableListOf<Pair<String, String>>()
        showFutachaDialog(listOf(BoardSummary("aimoge-aimg", "二次元裏", "", aUrl, "")), added)
        rule.onAllNodesWithTag("aimoge-add-boards-entry").assertCountEquals(0)
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("http://nijiurachan.net/b/aimg/catalog")
        rule.onNodeWithText("同じURLの板が既に登録されています").assertIsDisplayed()
        rule.onNodeWithText("追加").assertIsNotEnabled()
    }

    @Test fun anR18BoardIsConfirmedThenAddedWithItsListName() {
        AimogeAvailability.sitesEnabled = true
        val added = mutableListOf<Pair<String, String>>()
        rule.setContent {
            MaterialTheme {
                val adder = rememberAimogeBoardUrlAdder(
                    onAdd = { n, u -> added += n to u },
                    loadBoards = { listOf(AimogeBoardInfo("aimg", "二次元裏", aUrl, r18 = true)) }
                )
                Button(onClick = { adder.start(aUrl) }, modifier = Modifier.testTag("go")) { Text("go") }
            }
        }
        rule.onNodeWithTag("go").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("aimoge-r18-cancel").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("aimoge-r18-cancel").performClick()
        rule.waitForIdle()
        assertTrue(added.isEmpty())
        rule.onNodeWithTag("go").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("aimoge-r18-confirm").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("aimoge-r18-confirm").performClick()
        rule.waitForIdle()
        assertEquals(listOf("二次元裏" to aUrl), added)
    }
}
