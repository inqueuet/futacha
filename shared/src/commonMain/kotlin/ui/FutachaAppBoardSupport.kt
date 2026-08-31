package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.BoardManagementMenuAction
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart

internal data class FutachaBoardScreenCallbackInputs(
    val currentNavigationState: () -> FutachaNavigationState,
    val setNavigationState: (FutachaNavigationState) -> Unit,
    val updateBoards: suspend ((List<BoardSummary>) -> List<BoardSummary>) -> Unit
)

internal data class FutachaBoardScreenCallbacks(
    val onBoardSelected: (BoardSummary) -> Unit,
    val onAddBoard: (String, String) -> Unit,
    val onMenuAction: (BoardManagementMenuAction) -> Unit,
    val onBoardDeleted: (BoardSummary) -> Unit,
    val onBoardsReordered: (List<BoardSummary>) -> Unit
)

internal fun buildFutachaBoardScreenCallbacks(
    coroutineScope: CoroutineScope,
    inputs: FutachaBoardScreenCallbackInputs
): FutachaBoardScreenCallbacks {
    return FutachaBoardScreenCallbacks(
        onBoardSelected = { board ->
            AnalyticsTracker.event(
                "board_selected",
                mapOf(
                    "board_kind" to analyticsBoardKind(board.url),
                    "board_context" to analyticsSessionContextId("board", board.id, board.url)
                )
            )
            inputs.setNavigationState(selectFutachaBoard(inputs.currentNavigationState(), board.id))
        },
        onAddBoard = { name, url ->
            AnalyticsTracker.event("board_add_submitted")
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                val normalizedUrl = normalizeBoardUrl(url)
                inputs.updateBoards { boards ->
                    if (boards.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                        AnalyticsTracker.event("board_add_result", mapOf("result" to "duplicate"))
                        boards
                    } else {
                        AnalyticsTracker.event(
                            "board_add_result",
                            mapOf(
                                "result" to "success",
                                "board_count_bucket" to analyticsCountBucket(boards.size + 1)
                            )
                        )
                        boards + createCustomBoardSummary(
                            name = name,
                            url = normalizedUrl,
                            existingBoards = boards
                        )
                    }
                }
            }
        },
        onMenuAction = { action ->
            AnalyticsTracker.event("board_menu_action", mapOf("action" to action.name.lowercase()))
            if (action == BoardManagementMenuAction.SAVED_THREADS) {
                inputs.setNavigationState(
                    inputs.currentNavigationState().copy(isSavedThreadsVisible = true)
                )
            }
        },
        onBoardDeleted = { board ->
            AnalyticsTracker.event(
                "board_deleted",
                mapOf(
                    "board_kind" to analyticsBoardKind(board.url),
                    "board_context" to analyticsSessionContextId("board", board.id, board.url)
                )
            )
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards { boards ->
                    boards.filter { it.id != board.id }
                }
            }
        },
        onBoardsReordered = { reorderedBoards ->
            AnalyticsTracker.event(
                "boards_reordered",
                mapOf("board_count_bucket" to analyticsCountBucket(reorderedBoards.size))
            )
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards {
                    reorderedBoards
                }
            }
        }
    )
}
