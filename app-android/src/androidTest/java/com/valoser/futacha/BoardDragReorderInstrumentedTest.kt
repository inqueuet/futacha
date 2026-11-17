@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.*
import org.junit.*
import org.junit.Assert.*

/** 板一覧の並び替えのドラッグ（スレ1447418590の「ドラッグした時固まる」）。 */
class BoardDragReorderInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val names = (1..8).map { "BOARD-%02d".format(it) }

    @Test fun toshiakiDragFollowsTheFingerEvenWhenTheBoardsAreSentAgain() {
        var boards by mutableStateOf(names.map { CompatBoard(compatBoardKey("https://may.2chan.net/$it/"), it, "https://may.2chan.net/$it/", "https://may.2chan.net/$it/", 0) })
        var saved: List<String>? = null
        rule.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().height(700.dp)) { CompatibilityProfileTheme(null) {
                CompatMainScreen(boards, onOpenDrawer = {}, onCloseDrawer = {}, onOpenSettings = {}, onOpenHelp = {}, onOpenSavedThreads = {},
                    onUpdateBoards = {}, onBoardSelected = {}, onBoardUpsert = {}, onBoardDelete = {},
                    onBoardsReordered = { saved = it.map { b -> b.name } })
            } } }
        }
        rule.onNodeWithContentDescription("その他").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("並び替え").performClick()
        rule.waitForIdle()
        val handle = rule.onAllNodesWithContentDescription("ハンドル", useUnmergedTree = true)[0]
        val rowH = handle.fetchSemanticsNode().boundsInRoot.height
        handle.performTouchInput {
            down(center)
            for (i in 1..40) {
                moveBy(androidx.compose.ui.geometry.Offset(0f, rowH * 3.2f / 40f))
                // 保存などで同じ中身の板一覧がもう一度届く。
                if (i == 20) rule.runOnUiThread { boards = boards.toList() }
            }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("BOARD-02", "BOARD-03", "BOARD-04", "BOARD-01") + names.drop(4), saved)
    }

    @Test fun toshiakiLongPressOnTheNamePicksTheBoardUpToo() {
        val boards = names.map { CompatBoard(compatBoardKey("https://may.2chan.net/$it/"), it, "https://may.2chan.net/$it/", "https://may.2chan.net/$it/", 0) }
        var saved: List<String>? = null
        rule.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().height(700.dp)) { CompatibilityProfileTheme(null) {
                CompatMainScreen(boards, onOpenDrawer = {}, onCloseDrawer = {}, onOpenSettings = {}, onOpenHelp = {}, onOpenSavedThreads = {},
                    onUpdateBoards = {}, onBoardSelected = {}, onBoardUpsert = {}, onBoardDelete = {},
                    onBoardsReordered = { saved = it.map { b -> b.name } })
            } } }
        }
        rule.onNodeWithContentDescription("その他").performClick(); rule.waitForIdle()
        rule.onNodeWithText("並び替え").performClick(); rule.waitForIdle()
        val first = rule.onNodeWithText("BOARD-01", useUnmergedTree = true)
        val distance = rule.onNodeWithText("BOARD-04", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top -
            first.fetchSemanticsNode().boundsInRoot.top
        first.performTouchInput {
            down(center); advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            for (i in 1..40) moveBy(androidx.compose.ui.geometry.Offset(0f, distance * 1.07f / 40f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("BOARD-02", "BOARD-03", "BOARD-04", "BOARD-01") + names.drop(4), saved)
    }

    @Test fun toshiakiDeleteModeDeletesTheChosenBoardsAtOnce() {
        val boards = names.map { CompatBoard(compatBoardKey("https://may.2chan.net/$it/"), it, "https://may.2chan.net/$it/", "https://may.2chan.net/$it/", 0) }
        val deleted = mutableListOf<String>()
        rule.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().height(700.dp)) { CompatibilityProfileTheme(null) {
                CompatMainScreen(boards, onOpenDrawer = {}, onCloseDrawer = {}, onOpenSettings = {}, onOpenHelp = {}, onOpenSavedThreads = {},
                    onUpdateBoards = {}, onBoardSelected = {}, onBoardUpsert = {}, onBoardDelete = { deleted += it.name },
                    onBoardsReordered = {})
            } } }
        }
        rule.onNodeWithContentDescription("その他").performClick(); rule.waitForIdle()
        rule.onNodeWithText("削除").performClick(); rule.waitForIdle()
        rule.onNodeWithText("BOARD-02", useUnmergedTree = true).performClick()
        rule.onNodeWithText("BOARD-05", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("compat-board-delete-selected").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("compat-board-delete-selected-confirm").performClick(); rule.waitForIdle()
        assertEquals(listOf("BOARD-02", "BOARD-05"), deleted)
    }

    @Test fun futachaLongPressDragMovesSeveralRowsInOneGesture() {
        var boards by mutableStateOf(names.mapIndexed { i, n -> BoardSummary("b$i", n, "", "https://may.2chan.net/$n/", "") })
        rule.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().height(700.dp)) {
                BoardManagementBoardList(boards, false, true, false, {},
                    BoardManagementBoardListCallbacks({}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, onReorder = { boards = it }),
                    modifier = Modifier.fillMaxSize().testTag("board-list"))
            } }
        }
        val first = rule.onNodeWithText("BOARD-01", useUnmergedTree = true)
        // Three rows down: from BOARD-01's place to just past BOARD-04's.
        val distance = rule.onNodeWithText("BOARD-04", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top -
            first.fetchSemanticsNode().boundsInRoot.top
        first.performTouchInput {
            down(center); advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            for (i in 1..40) moveBy(androidx.compose.ui.geometry.Offset(0f, distance * 1.07f / 40f))
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("BOARD-02", "BOARD-03", "BOARD-04", "BOARD-01"), boards.take(4).map { it.name })
    }
}
