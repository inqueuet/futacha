package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ThreadScreenDerivedRuntimeSnapshot(
    val postHighlightRanges: Map<String, List<IntRange>>,
    val firstVisibleSegmentIndex: Int
)

internal fun buildThreadScreenDerivedRuntimeSnapshot(
    derivedUiState: ThreadScreenDerivedUiState,
    isSearchActive: Boolean,
    searchMatches: List<ThreadSearchMatch>,
    readAloudSegments: List<ReadAloudSegment>,
    firstVisibleItemIndex: Int
): ThreadScreenDerivedRuntimeSnapshot {
    return ThreadScreenDerivedRuntimeSnapshot(
        postHighlightRanges = if (isSearchActive) {
            searchMatches.associate { it.postId to it.highlightRanges }
        } else {
            emptyMap()
        },
        firstVisibleSegmentIndex = if (
            !derivedUiState.shouldPrepareReadAloudSegments || readAloudSegments.isEmpty()
        ) {
            -1
        } else {
            findFirstVisibleReadAloudSegmentIndex(readAloudSegments, firstVisibleItemIndex)
        }
    )
}

internal data class ThreadScreenDerivedRuntimeState(
    val derivedUiState: ThreadScreenDerivedUiState,
    val mediaPreviewEntries: List<MediaPreviewEntry>,
    val mediaPreviewIndexByKey: Map<MediaPreviewKey, Int>,
    val searchMatches: List<ThreadSearchMatch>,
    val postHighlightRanges: Map<String, List<IntRange>>,
    val readAloudSegments: List<ReadAloudSegment>,
    val firstVisibleSegmentIndex: () -> Int
)

internal data class ThreadBackSwipeMetrics(
    val edgePx: Float,
    val triggerPx: Float
)

internal fun buildThreadBackSwipeMetrics(
    density: Density
): ThreadBackSwipeMetrics {
    return with(density) {
        ThreadBackSwipeMetrics(
            edgePx = 48.dp.toPx(),
            triggerPx = 96.dp.toPx()
        )
    }
}

@Composable
internal fun rememberThreadBackSwipeMetrics(
    density: Density
): ThreadBackSwipeMetrics {
    return remember(density) {
        buildThreadBackSwipeMetrics(density)
    }
}

@Composable
internal fun rememberThreadInitialScrollRestoreState(
    hasRestoredInitialScroll: Boolean,
    initialHistoryEntry: ThreadHistoryEntry?,
    totalItems: Int?
): ThreadInitialScrollRestoreState {
    return remember(
        hasRestoredInitialScroll,
        initialHistoryEntry?.lastReadItemIndex,
        initialHistoryEntry?.lastReadItemOffset,
        totalItems
    ) {
        resolveThreadInitialScrollRestoreState(
            hasRestoredInitialScroll = hasRestoredInitialScroll,
            entry = initialHistoryEntry,
            totalItems = totalItems
        )
    }
}

@Composable
internal fun rememberThreadScreenDerivedRuntimeState(
    currentState: ThreadUiState,
    initialReplyCount: Int?,
    threadTitle: String?,
    isReadAloudControlsVisible: Boolean,
    readAloudStatus: ReadAloudStatus,
    shouldPrepareReadAloudForCommand: Boolean = false,
    lazyListState: LazyListState,
    isSearchActive: Boolean,
    searchQuery: String
): ThreadScreenDerivedRuntimeState {
    val derivedUiState = remember(
        currentState,
        initialReplyCount,
        threadTitle,
        isReadAloudControlsVisible,
        readAloudStatus,
        shouldPrepareReadAloudForCommand
    ) {
        buildThreadScreenDerivedUiState(
            currentState = currentState,
            initialReplyCount = initialReplyCount,
            threadTitle = threadTitle,
            isReadAloudControlsVisible = isReadAloudControlsVisible,
            readAloudStatus = readAloudStatus,
            shouldPrepareReadAloudForCommand = shouldPrepareReadAloudForCommand
        )
    }
    val currentPage = derivedUiState.currentPage
    val currentPosts = derivedUiState.currentPosts
    val mediaPreviewCollection by produceState(
        initialValue = MediaPreviewCollection(
            entries = emptyList(),
            indexByKey = emptyMap()
        ),
        key1 = currentPosts
    ) {
        value = withContext(AppDispatchers.parsing) {
            buildMediaPreviewCollection(currentPosts)
        }
    }
    val searchTargets by produceState<List<ThreadSearchTarget>>(
        initialValue = emptyList(),
        key1 = isSearchActive,
        key2 = currentPage?.posts
    ) {
        if (!isSearchActive || currentPage == null) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchTargets(currentPage.posts)
        }
    }
    val normalizedSearchQuery = remember(searchQuery) { searchQuery.trim() }
    val searchMatches by produceState<List<ThreadSearchMatch>>(
        initialValue = emptyList(),
        key1 = isSearchActive,
        key2 = normalizedSearchQuery,
        key3 = searchTargets
    ) {
        if (!isSearchActive || normalizedSearchQuery.isBlank() || searchTargets.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        delay(THREAD_SEARCH_DEBOUNCE_MILLIS)
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchMatches(searchTargets, normalizedSearchQuery)
        }
    }
    val readAloudSegments by produceState<List<ReadAloudSegment>>(
        initialValue = emptyList(),
        key1 = currentPosts,
        key2 = derivedUiState.shouldPrepareReadAloudSegments
    ) {
        if (!derivedUiState.shouldPrepareReadAloudSegments || currentPosts.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildReadAloudSegments(currentPosts)
        }
    }
    val firstVisibleSegmentIndexState = remember(
        readAloudSegments,
        lazyListState,
        derivedUiState.shouldPrepareReadAloudSegments
    ) {
        derivedStateOf {
            buildThreadScreenDerivedRuntimeSnapshot(
                derivedUiState = derivedUiState,
                isSearchActive = isSearchActive,
                searchMatches = searchMatches,
                readAloudSegments = readAloudSegments,
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex
            ).firstVisibleSegmentIndex
        }
    }
    val firstVisibleSegmentIndex = remember(firstVisibleSegmentIndexState) {
        { firstVisibleSegmentIndexState.value }
    }
    val postHighlightRanges = remember(
        isSearchActive,
        searchMatches
    ) {
        if (isSearchActive) {
            searchMatches.associate { it.postId to it.highlightRanges }
        } else {
            emptyMap()
        }
    }
    return ThreadScreenDerivedRuntimeState(
        derivedUiState = derivedUiState,
        mediaPreviewEntries = mediaPreviewCollection.entries,
        mediaPreviewIndexByKey = mediaPreviewCollection.indexByKey,
        searchMatches = searchMatches,
        postHighlightRanges = postHighlightRanges,
        readAloudSegments = readAloudSegments,
        firstVisibleSegmentIndex = firstVisibleSegmentIndex
    )
}
