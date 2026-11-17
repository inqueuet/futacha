package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.shared.AIMOGE_BOARDS_STAGE_ID
import com.valoser.futacha.shared.compat.shared.SharedDataMigrations
import com.valoser.futacha.shared.compat.shared.planAimogeBoardsMove
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.state.boardSettingKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AM-B5 `aimoge-boards-v1`: earlier-form あいもげ boards move to the canonical address and id, with what points at them. */
class AimogeBoardsMigrationTest {
    private val canonical = "https://nijiurachan.net/b/aimoge/"
    private val old = BoardSummary("nijiurachan", "あいもげ", "", "https://nijiurachan.net/b/aimoge/futaba.php", "", pinned = true)
    private val oldHttp = BoardSummary("nijiurachan2", "あいもげ2", "", "http://nijiurachan.net/b/aimoge/futaba.php", "")
    private val cannotMove = BoardSummary("x", "x", "", "https://nijiurachan.net/other/futaba.php", "")
    private val futaba = BoardSummary("b", "may", "", "https://may.2chan.net/b/futaba.php", "")
    private fun history(boardId: String, boardUrl: String) = ThreadHistoryEntry(
        threadId = "1", boardId = boardId, title = "t", titleImageUrl = "", boardName = "n", boardUrl = boardUrl,
        lastVisitedEpochMillis = 1L, replyCount = 0
    )

    @Test
    fun boardsMergeIntoOneCanonicalBoardAndTheRestStays() {
        val move = planAimogeBoardsMove(listOf(futaba, old, oldHttp, cannotMove), listOf(history("nijiurachan2", oldHttp.url), history("b", futaba.url)))
        assertEquals(listOf(futaba, BoardSummary("aimoge-aimoge", "あいもげ", "", canonical, "", pinned = true), cannotMove), move.boards)
        assertEquals("aimoge-aimoge", move.history[0].boardId)
        assertEquals(canonical, move.history[0].boardUrl)
        assertEquals(history("b", futaba.url), move.history[1])
        assertEquals(compatBoardKey(canonical), move.settingMoves["nijiurachan"])
        // Running again changes nothing.
        val again = planAimogeBoardsMove(move.boards, move.history)
        assertEquals(move.boards, again.boards)
        assertEquals(move.history, again.history)
        assertTrue(again.settingMoves.isEmpty())
    }

    @Test
    fun theStageIsRegisteredOnlyWhileAimogeIsOn() {
        val store = AppStateStore(FakePlatformStateStorage())
        withAimoge(enabled = false) { assertTrue(SharedDataMigrations.stagesFor(store).none { it.id == AIMOGE_BOARDS_STAGE_ID }) }
        withAimoge(enabled = true) { assertTrue(SharedDataMigrations.stagesFor(store).any { it.id == AIMOGE_BOARDS_STAGE_ID }) }
    }

    @Test
    fun theStageMovesTheStoreAndItsSettings() = runTest {
        val store = AppStateStore(FakePlatformStateStorage())
        store.setBoards(listOf(futaba, old))
        store.setHistory(listOf(history("nijiurachan", old.url)))
        store.setCatalogMode(boardSettingKeys(old)!!, CatalogMode.New)
        withAimoge(enabled = true) {
            val stage = SharedDataMigrations.stagesFor(store).first { it.id == AIMOGE_BOARDS_STAGE_ID }
            val legacy = stage.readLegacy()
            assertTrue(legacy.isNotEmpty())
            stage.migrate(legacy)
            assertEquals("", stage.readLegacy())
            val boards = store.boards.first()
            assertEquals(listOf("b", "aimoge-aimoge"), boards.map { it.id })
            assertEquals("aimoge-aimoge", store.history.first().single().boardId)
            assertEquals(CatalogMode.New, store.catalogModes.first()[compatBoardKey(canonical)])
        }
    }
}
