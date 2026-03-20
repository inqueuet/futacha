package com.valoser.futacha.shared.ui

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
            inputs.setNavigationState(selectFutachaBoard(inputs.currentNavigationState(), board.id))
        },
        onAddBoard = { name, url ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                val normalizedUrl = normalizeBoardUrl(url)
                inputs.updateBoards { boards ->
                    if (boards.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                        boards
                    } else {
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
            if (action == BoardManagementMenuAction.SAVED_THREADS) {
                inputs.setNavigationState(
                    inputs.currentNavigationState().copy(isSavedThreadsVisible = true)
                )
            }
        },
        onBoardDeleted = { board ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards { boards ->
                    boards.filter { it.id != board.id }
                }
            }
        },
        onBoardsReordered = { reorderedBoards ->
            launchFutachaCallbackMutation(coroutineScope, start = CoroutineStart.UNDISPATCHED) {
                inputs.updateBoards {
                    reorderedBoards
                }
            }
        }
    )
}
