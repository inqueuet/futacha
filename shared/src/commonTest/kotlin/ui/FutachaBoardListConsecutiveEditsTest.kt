package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.BoardManagementBoardInteractionInputs
import com.valoser.futacha.shared.ui.board.BoardManagementHistoryInteractionInputs
import com.valoser.futacha.shared.ui.board.BoardManagementOverlayInteractionInputs
import com.valoser.futacha.shared.ui.board.BoardManagementOverlayState
import com.valoser.futacha.shared.ui.board.BoardManagementStateInteractionInputs
import com.valoser.futacha.shared.ui.board.BoardManagementBoardListCallbacks
import com.valoser.futacha.shared.ui.board.buildBoardManagementInteractionBindingsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.test.Test
import kotlin.test.assertEquals

class FutachaBoardListConsecutiveEditsTest {
    private fun board(id: String, pinned: Boolean = false) =
        BoardSummary(id = id, name = id, category = "", url = "https://may.2chan.net/$id/futaba.php", description = "", pinned = pinned)

    /** The real board-list callbacks wired to the app callbacks over a store that takes time to save. */
    private class Harness(scope: CoroutineScope, initial: List<BoardSummary>) {
        var stored = initial
        private val mutex = Mutex()
        val callbacks: BoardManagementBoardListCallbacks

        init {
            val app = buildFutachaBoardScreenCallbacks(
                coroutineScope = scope,
                inputs = FutachaBoardScreenCallbackInputs(
                    currentNavigationState = { FutachaNavigationState() },
                    setNavigationState = {},
                    updateBoards = { transform -> mutex.withLock { delay(5); stored = transform(stored) } }
                )
            )
            callbacks = buildBoardManagementInteractionBindingsBundle(
                historyInputs = BoardManagementHistoryInteractionInputs(
                    coroutineScope = scope, closeDrawer = {}, openDrawer = {}, onExternalMenuAction = {},
                    onHistoryEntrySelected = {}, onHistoryRefresh = {}, onHistoryCleared = {}, showSnackbar = {}
                ),
                stateInputs = BoardManagementStateInteractionInputs(
                    currentIsDeleteMode = { false }, currentIsReorderMode = { true }, currentIsHistoryRefreshing = { false },
                    setIsDeleteMode = {}, setIsReorderMode = {}, setIsHistoryRefreshing = {},
                    currentIsMenuExpanded = { false }, setIsMenuExpanded = {}
                ),
                overlayInputs = BoardManagementOverlayInteractionInputs(
                    currentOverlayState = { BoardManagementOverlayState() }, setOverlayState = {}, hasCookieRepository = false
                ),
                boardInputs = BoardManagementBoardInteractionInputs(
                    onAddBoard = { _, _ -> }, onBoardDeleted = {}, onBoardSelected = {},
                    onBoardsReordered = app.onBoardsReordered
                )
            ).boardListCallbacks
        }
    }

    @Test
    fun rapidEditsOnTheShownListAllReachTheStore() = runBlocking {
        val shown = listOf(board("a"), board("b"), board("c"))
        val harness = Harness(this, shown)

        // Every tap arrives before the screen shows the previous result.
        harness.callbacks.onMoveUp(shown, 2)
        harness.callbacks.onPinClick(shown, 0)
        harness.callbacks.onRename(shown, "b", "B2")
        harness.callbacks.onMoveUp(shown, 2)
        delay(100)

        assertEquals(listOf("c", "a", "b"), harness.stored.map { it.id })
        assertEquals(true, harness.stored.first { it.id == "a" }.pinned)
        assertEquals("B2", harness.stored.first { it.id == "b" }.name)
    }

    @Test
    fun dragOrderKeepsConcurrentEditsAndBoardChanges() = runBlocking {
        val shown = listOf(board("a"), board("b"), board("c"))
        val harness = Harness(this, shown)

        harness.callbacks.onPinClick(shown, 1)
        // Meanwhile a board is added and another deleted (AI command, another screen).
        harness.stored = harness.stored.filterNot { it.id == "a" } + board("d")
        harness.callbacks.onReorder(listOf(board("c"), board("b"), board("a")))
        delay(100)

        assertEquals(listOf("c", "b", "d"), harness.stored.map { it.id })
        assertEquals(true, harness.stored.first { it.id == "b" }.pinned)
    }

    @Test
    fun aPlainListStillReplacesTheStoredOrder() = runBlocking {
        val harness = Harness(this, listOf(board("a"), board("b")))
        buildFutachaBoardScreenCallbacks(
            coroutineScope = this,
            inputs = FutachaBoardScreenCallbackInputs(
                currentNavigationState = { FutachaNavigationState() },
                setNavigationState = {},
                updateBoards = { transform -> harness.stored = transform(harness.stored) }
            )
        ).onBoardsReordered(listOf(board("b"), board("a")))
        delay(10)
        assertEquals(listOf("b", "a"), harness.stored.map { it.id })
    }
}
