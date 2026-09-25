package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class ThreadScreenDerivedRuntimeSnapshot(
    val postHighlightRanges: Map<Post, List<IntRange>>,
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
            searchMatches.associate { it.post to it.highlightRanges }
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
    val searchMatches: List<ThreadSearchMatch>,
    val postHighlightRanges: Map<Post, List<IntRange>>,
    val readAloudSegments: List<ReadAloudSegment>,
    val firstVisibleSegmentIndex: () -> Int
)

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
    searchQuery: String,
    searchPosts: List<Post>? = null,
    postTextCache: ThreadPostTextCache? = null,
    // What the list actually shows (NG removed, tree order, leading cards
    // counted). Read-aloud follows it so it skips hidden posts and scrolls to
    // the right row; the raw page is only used before the list reports.
    displayedPostsLayout: ThreadDisplayedPostsLayout? = null
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
    val effectiveSearchPosts = searchPosts ?: currentPage?.posts
    val searchTargets by produceState<List<ThreadSearchTarget>>(
        initialValue = emptyList(),
        key1 = isSearchActive,
        key2 = effectiveSearchPosts
    ) {
        if (!isSearchActive || effectiveSearchPosts == null) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchTargets(effectiveSearchPosts, postTextCache)
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
        value = withContext(AppDispatchers.parsing) {
            buildThreadSearchMatches(searchTargets, normalizedSearchQuery)
        }
    }
    val reportedLayout = displayedPostsLayout?.takeIf { it.posts.isNotEmpty() }
    val readAloudPosts = reportedLayout?.posts ?: currentPosts
    val readAloudItemsBeforePosts = reportedLayout?.itemsBeforePosts ?: 0
    val readAloudSkippedPostIds = reportedLayout?.collapsedPostIds.orEmpty()
    val readAloudSegments by produceState<List<ReadAloudSegment>>(
        initialValue = emptyList(),
        key1 = readAloudPosts,
        key2 = derivedUiState.shouldPrepareReadAloudSegments,
        key3 = readAloudSkippedPostIds
    ) {
        if (!derivedUiState.shouldPrepareReadAloudSegments || readAloudPosts.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        value = withContext(AppDispatchers.parsing) {
            buildReadAloudSegments(readAloudPosts, postTextCache, readAloudSkippedPostIds)
        }
    }
    val firstVisibleSegmentIndexState = remember(
        readAloudSegments,
        readAloudItemsBeforePosts,
        lazyListState,
        derivedUiState.shouldPrepareReadAloudSegments
    ) {
        derivedStateOf {
            buildThreadScreenDerivedRuntimeSnapshot(
                derivedUiState = derivedUiState,
                isSearchActive = isSearchActive,
                searchMatches = searchMatches,
                readAloudSegments = readAloudSegments,
                // Segment indices count displayed posts, not list rows.
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex - readAloudItemsBeforePosts
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
            searchMatches.associate { it.post to it.highlightRanges }
        } else {
            emptyMap()
        }
    }
    return ThreadScreenDerivedRuntimeState(
        derivedUiState = derivedUiState,
        searchMatches = searchMatches,
        postHighlightRanges = postHighlightRanges,
        readAloudSegments = readAloudSegments,
        firstVisibleSegmentIndex = firstVisibleSegmentIndex
    )
}
