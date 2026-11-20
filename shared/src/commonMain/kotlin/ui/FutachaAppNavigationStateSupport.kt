package com.valoser.futacha.shared.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.compat.canonicalizeSiteBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrlAsLegacy
import com.valoser.futacha.shared.ui.board.RegisteredThreadNavigation
import com.valoser.futacha.shared.mix.MixDefinition
import com.valoser.futacha.shared.mix.ResolvedMix
import com.valoser.futacha.shared.mix.resolveMixMembers

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
    val isSavedThreadsVisible: Boolean = false,
    /** The MIX (まとめカタログ) being shown while no board is selected. */
    val selectedMixId: String? = null
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
                    state.selectedBoardName,
                    state.selectedMixId
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
                    selectedBoardName = restored.getOrNull(7) as String?,
                    selectedMixId = restored.getOrNull(8) as? String
                )
            }
        )
    }
}

internal sealed interface FutachaThreadDeepLinkResolution {
    data class Open(val selection: FutachaThreadSelection) : FutachaThreadDeepLinkResolution
    data class UnregisteredBoard(val canonicalUrl: String) : FutachaThreadDeepLinkResolution
    data object Invalid : FutachaThreadDeepLinkResolution
}

internal fun resolveFutachaThreadDeepLink(
    rawUrl: String,
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>
): FutachaThreadDeepLinkResolution {
    val parsed = canonicalizeSiteThreadUrlAsLegacy(rawUrl) ?: return FutachaThreadDeepLinkResolution.Invalid
    val board = boards.firstOrNull { candidate ->
        canonicalizeSiteBoardUrl(candidate.url)?.canonicalUrl?.equals(parsed.canonicalBoardUrl, ignoreCase = true) == true
    } ?: return FutachaThreadDeepLinkResolution.UnregisteredBoard(parsed.canonicalUrl)
    val historyEntry = history.firstOrNull { entry ->
        entry.threadId == parsed.threadNo && entry.boardId == board.id
    }
    return FutachaThreadDeepLinkResolution.Open(
        FutachaThreadSelection(
            boardId = board.id,
            boardName = board.name,
            threadId = parsed.threadNo,
            threadTitle = historyEntry?.title,
            threadReplies = historyEntry?.replyCount,
            threadThumbnailUrl = historyEntry?.titleImageUrl,
            threadUrl = parsed.canonicalUrl,
            isSavedThreadsVisible = false
        )
    )
}

internal sealed interface FutachaDestination {
    data object SavedThreads : FutachaDestination
    data object BoardManagement : FutachaDestination
    data class MissingBoard(val missingBoardId: String) : FutachaDestination
    data class Catalog(val board: BoardSummary) : FutachaDestination
    data class Thread(val board: BoardSummary, val threadId: String) : FutachaDestination
    data class MixCatalog(val mix: ResolvedMix) : FutachaDestination
    /** The MIX is still being read, or was not found: the id is kept while loading (C-M13). */
    data class MissingMix(val mixId: String) : FutachaDestination
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
            isSavedThreadsVisible = false,
            selectedMixId = null
        ),
        clearBoardSelection = false
    )
}

internal fun selectFutachaMix(state: FutachaNavigationState, mixId: String) = FutachaNavigationState(selectedMixId = mixId)

/** Keep the selection while loading; discard it once a missing definition is confirmed. */
internal fun reconcileFutachaMixSelection(state: FutachaNavigationState,
    definitions: List<MixDefinition>?,
    mixEnabled: Boolean = com.valoser.futacha.shared.compat.MixAvailability.enabled): FutachaNavigationState {
    val id = state.selectedMixId ?: return state
    if (!mixEnabled) return state.copy(selectedMixId = null)
    return if (definitions != null && definitions.none { it.id == id }) state.copy(selectedMixId = null) else state
}

internal fun exitFutachaMix(state: FutachaNavigationState) = state.copy(selectedMixId = null)

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
        url = "https://${com.valoser.futacha.shared.model.SAVED_THREAD_PLACEHOLDER_HOST}/$urlSegment",
        description = "Temporary board context for saved thread playback",
        pinned = false
    )
}

internal fun resolveFutachaDestination(
    navigationState: FutachaNavigationState,
    boards: List<BoardSummary>,
    /** Null while the definitions are being read. */
    mixes: List<MixDefinition>? = emptyList(),
    resolveMix: (MixDefinition, List<BoardSummary>) -> ResolvedMix = { def, all ->
        resolveMixMembers(def, all, com.valoser.futacha.shared.compat.AimogeAvailability.sitesEnabled, { com.valoser.futacha.shared.service.SiteRefreshPause.sitePausedNow() },
            { url -> com.valoser.futacha.shared.service.SiteRefreshPause.boardPausedNow(com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(url) ?: url) })
    }
): FutachaDestination {
    if (navigationState.selectedBoardId == null) {
        if (!navigationState.isSavedThreadsVisible) navigationState.selectedMixId?.let { id ->
            if (!com.valoser.futacha.shared.compat.MixAvailability.enabled) return FutachaDestination.BoardManagement
            if (mixes == null) return FutachaDestination.MissingMix(id)
            val mix = mixes.find { it.id == id } ?: return FutachaDestination.BoardManagement
            return FutachaDestination.MixCatalog(resolveMix(mix, boards))
        }
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
