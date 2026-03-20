package com.valoser.futacha.shared.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation

internal data class FutachaThreadSelection(
    val boardId: String,
    val boardName: String? = null,
    val threadId: String,
    val threadTitle: String?,
    val threadReplies: Int?,
    val threadThumbnailUrl: String?,
    val threadUrl: String?,
    val isSavedThreadsVisible: Boolean = false
)

internal data class FutachaNavigationState(
    val selectedBoardId: String? = null,
    val selectedBoardName: String? = null,
    val selectedThreadId: String? = null,
    val selectedThreadTitle: String? = null,
    val selectedThreadReplies: Int? = null,
    val selectedThreadThumbnailUrl: String? = null,
    val selectedThreadUrl: String? = null,
    val isSavedThreadsVisible: Boolean = false
) {
    companion object {
        val Saver: Saver<FutachaNavigationState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selectedBoardId,
                    state.selectedThreadId,
                    state.selectedThreadTitle,
                    state.selectedThreadReplies,
                    state.selectedThreadThumbnailUrl,
                    state.selectedThreadUrl,
                    state.isSavedThreadsVisible,
                    state.selectedBoardName
                )
            },
            restore = { restored ->
                FutachaNavigationState(
                    selectedBoardId = restored.getOrNull(0) as String?,
                    selectedThreadId = restored.getOrNull(1) as String?,
                    selectedThreadTitle = restored.getOrNull(2) as String?,
                    selectedThreadReplies = restored.getOrNull(3) as Int?,
                    selectedThreadThumbnailUrl = restored.getOrNull(4) as String?,
                    selectedThreadUrl = restored.getOrNull(5) as String?,
                    isSavedThreadsVisible = restored.getOrNull(6) as? Boolean ?: false,
                    selectedBoardName = restored.getOrNull(7) as String?
                )
            }
        )
    }
}

internal sealed interface FutachaDestination {
    data object SavedThreads : FutachaDestination
    data object BoardManagement : FutachaDestination
    data class MissingBoard(val missingBoardId: String) : FutachaDestination
    data class Catalog(val board: BoardSummary) : FutachaDestination
    data class Thread(val board: BoardSummary, val threadId: String) : FutachaDestination
}

internal fun clearFutachaThreadSelection(
    state: FutachaNavigationState,
    clearBoardSelection: Boolean = false
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = if (clearBoardSelection) null else state.selectedBoardId,
        selectedBoardName = if (clearBoardSelection) null else state.selectedBoardName,
        selectedThreadId = null,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = null
    )
}

internal fun selectFutachaBoard(
    state: FutachaNavigationState,
    boardId: String
): FutachaNavigationState {
    return clearFutachaThreadSelection(
        state = state.copy(
            selectedBoardId = boardId,
            selectedBoardName = null,
            isSavedThreadsVisible = false
        ),
        clearBoardSelection = false
    )
}

internal fun applyFutachaThreadSelection(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = selection.boardId,
        selectedBoardName = selection.boardName,
        selectedThreadId = selection.threadId,
        selectedThreadTitle = selection.threadTitle,
        selectedThreadReplies = selection.threadReplies,
        selectedThreadThumbnailUrl = selection.threadThumbnailUrl,
        selectedThreadUrl = selection.threadUrl,
        isSavedThreadsVisible = selection.isSavedThreadsVisible
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return applyFutachaThreadSelection(
        state = state.copy(isSavedThreadsVisible = false),
        selection = selection.copy(isSavedThreadsVisible = false)
    )
}

internal fun selectSavedThread(
    state: FutachaNavigationState,
    selection: FutachaThreadSelection
): FutachaNavigationState {
    return applyFutachaThreadSelection(
        state = state.copy(isSavedThreadsVisible = true),
        selection = selection.copy(isSavedThreadsVisible = true)
    )
}

internal fun selectCatalogThread(
    state: FutachaNavigationState,
    threadId: String,
    title: String?,
    replies: Int?,
    thumbnailUrl: String?,
    threadUrl: String?
): FutachaNavigationState {
    return state.copy(
        selectedBoardName = state.selectedBoardName,
        selectedThreadId = threadId,
        selectedThreadTitle = title,
        selectedThreadReplies = replies,
        selectedThreadThumbnailUrl = thumbnailUrl,
        selectedThreadUrl = threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun applyRegisteredThreadNavigation(
    state: FutachaNavigationState,
    target: RegisteredThreadNavigation
): FutachaNavigationState {
    return state.copy(
        selectedBoardId = target.board.id,
        selectedBoardName = target.board.name,
        selectedThreadId = target.threadId,
        selectedThreadTitle = null,
        selectedThreadReplies = null,
        selectedThreadThumbnailUrl = null,
        selectedThreadUrl = target.threadUrl,
        isSavedThreadsVisible = false
    )
}

internal fun dismissSavedThreads(state: FutachaNavigationState): FutachaNavigationState {
    return state.copy(isSavedThreadsVisible = false)
}

private val savedThreadFallbackBoardSegmentRegex = Regex("[^a-zA-Z0-9._-]+")

internal fun buildSavedThreadFallbackBoard(
    boardId: String,
    boardName: String?
): BoardSummary {
    val normalizedId = boardId.trim().ifBlank { "saved-thread" }
    val normalizedName = boardName?.trim().takeUnless { it.isNullOrBlank() } ?: normalizedId
    val urlSegment = savedThreadFallbackBoardSegmentRegex
        .replace(normalizedId.lowercase(), "_")
        .trim('_')
        .ifBlank { "saved-thread" }
    return BoardSummary(
        id = normalizedId,
        name = normalizedName,
        category = "",
        url = "https://saved.invalid/$urlSegment",
        description = "Temporary board context for saved thread playback",
        pinned = false
    )
}

internal fun resolveFutachaDestination(
    navigationState: FutachaNavigationState,
    boards: List<BoardSummary>
): FutachaDestination {
    if (navigationState.selectedBoardId == null) {
        return if (navigationState.isSavedThreadsVisible) {
            FutachaDestination.SavedThreads
        } else {
            FutachaDestination.BoardManagement
        }
    }
    val selectedBoard = boards.firstOrNull { it.id == navigationState.selectedBoardId }
    if (selectedBoard == null) {
        if (
            navigationState.selectedThreadId != null &&
            navigationState.isSavedThreadsVisible
        ) {
            return FutachaDestination.Thread(
                board = buildSavedThreadFallbackBoard(
                    boardId = navigationState.selectedBoardId,
                    boardName = navigationState.selectedBoardName
                ),
                threadId = navigationState.selectedThreadId
            )
        }
        return FutachaDestination.MissingBoard(navigationState.selectedBoardId)
    }
    return if (navigationState.selectedThreadId == null) {
        FutachaDestination.Catalog(selectedBoard)
    } else {
        FutachaDestination.Thread(
            board = selectedBoard,
            threadId = navigationState.selectedThreadId
        )
    }
}
