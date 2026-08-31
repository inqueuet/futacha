package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.analyticsBoardKind
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsPresentValue
import com.valoser.futacha.shared.analytics.analyticsSessionContextId
import com.valoser.futacha.shared.analytics.analyticsTextHasUrl
import com.valoser.futacha.shared.analytics.analyticsTextLengthBucket
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.ui.board.resolveRegisteredThreadNavigation

internal data class FutachaNavigationCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit,
    val onSavedThreadSelected: (SavedThread) -> Unit,
    val onCatalogThreadSelected: (
        threadId: String,
        title: String?,
        replies: Int?,
        thumbnailUrl: String?,
        threadUrl: String?
    ) -> Unit,
    val onSavedThreadsDismissed: () -> Unit,
    val onBoardSelectionCleared: () -> Unit,
    val onThreadDismissed: () -> Unit,
    val onRegisteredThreadUrlClick: (String) -> Boolean
)

internal fun buildFutachaNavigationCallbacks(
    currentBoards: () -> List<BoardSummary>,
    currentNavigationState: () -> FutachaNavigationState,
    setNavigationState: (FutachaNavigationState) -> Unit
): FutachaNavigationCallbacks {
    return FutachaNavigationCallbacks(
        onHistoryEntrySelected = { entry ->
            AnalyticsTracker.event(
                "history_entry_selected",
                mapOf(
                    "has_board_id" to analyticsPresentValue(entry.boardId.takeIf { it.isNotBlank() }),
                    "reply_count_bucket" to analyticsCountBucket(entry.replyCount),
                    "board_context" to analyticsSessionContextId("board", entry.boardId, entry.boardUrl),
                    "thread_context" to analyticsSessionContextId("thread", entry.boardUrl, entry.threadId),
                    "title_length_bucket" to analyticsTextLengthBucket(entry.title),
                    "title_has_url" to analyticsTextHasUrl(entry.title)
                )
            )
            resolveHistoryEntrySelection(entry, currentBoards())?.let { selection ->
                setNavigationState(
                    applyFutachaThreadSelection(currentNavigationState(), selection)
                )
            }
        },
        onSavedThreadSelected = { thread ->
            AnalyticsTracker.event(
                "saved_thread_selected",
                mapOf("post_count_bucket" to analyticsCountBucket(thread.postCount))
            )
            resolveSavedThreadSelection(thread, currentBoards())?.let { selection ->
                setNavigationState(
                    selectSavedThread(currentNavigationState(), selection)
                )
            }
        },
        onCatalogThreadSelected = { threadId, title, replies, thumbnailUrl, threadUrl ->
            val selectedBoard = currentBoards().firstOrNull {
                it.id == currentNavigationState().selectedBoardId
            }
            AnalyticsTracker.event(
                "catalog_thread_selected",
                mapOf(
                    "reply_count_bucket" to analyticsCountBucket(replies ?: 0),
                    "has_thumbnail" to analyticsPresentValue(thumbnailUrl),
                    "has_thread_url" to analyticsPresentValue(threadUrl),
                    "board_context" to analyticsSessionContextId(
                        "board",
                        selectedBoard?.id,
                        selectedBoard?.url
                    ),
                    "thread_context" to analyticsSessionContextId(
                        "thread",
                        selectedBoard?.url,
                        threadId
                    ),
                    "title_length_bucket" to analyticsTextLengthBucket(title),
                    "title_has_url" to analyticsTextHasUrl(title)
                )
            )
            setNavigationState(
                selectCatalogThread(
                    state = currentNavigationState(),
                    threadId = threadId,
                    title = title,
                    replies = replies,
                    thumbnailUrl = thumbnailUrl,
                    threadUrl = threadUrl
                )
            )
        },
        onSavedThreadsDismissed = {
            AnalyticsTracker.event("saved_threads_dismissed")
            setNavigationState(dismissSavedThreads(currentNavigationState()))
        },
        onBoardSelectionCleared = {
            AnalyticsTracker.event("board_selection_cleared")
            setNavigationState(
                clearFutachaThreadSelection(
                    state = currentNavigationState(),
                    clearBoardSelection = true
                )
            )
        },
        onThreadDismissed = {
            AnalyticsTracker.event(
                "thread_dismissed",
                mapOf("from_saved_threads" to currentNavigationState().isSavedThreadsVisible.toString())
            )
            val state = currentNavigationState()
            setNavigationState(
                clearFutachaThreadSelection(
                    state = state,
                    clearBoardSelection = state.isSavedThreadsVisible
                )
            )
        },
        onRegisteredThreadUrlClick = { url ->
            val target = resolveRegisteredThreadNavigation(url, currentBoards())
            if (target == null) {
                AnalyticsTracker.event("registered_thread_url_click", mapOf("result" to "unresolved"))
                false
            } else {
                AnalyticsTracker.event(
                    "registered_thread_url_click",
                    mapOf(
                        "result" to "resolved",
                        "board_kind" to analyticsBoardKind(target.board.url)
                    )
                )
                val navigationState = currentNavigationState()
                if (
                    shouldApplyRegisteredThreadNavigation(
                        currentBoardId = navigationState.selectedBoardId,
                        currentThreadId = navigationState.selectedThreadId,
                        currentThreadUrl = navigationState.selectedThreadUrl,
                        target = target
                    )
                ) {
                    setNavigationState(applyRegisteredThreadNavigation(navigationState, target))
                }
                true
            }
        }
    )
}
