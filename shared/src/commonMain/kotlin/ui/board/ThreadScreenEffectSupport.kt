package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class ThreadAutoSaveEffectState(
    val availability: ThreadAutoSaveAvailability,
    val page: ThreadPage? = null
)

internal fun resolveThreadAutoSaveEffectState(
    page: ThreadPage?,
    expectedThreadId: String,
    isShowingOfflineCopy: Boolean,
    hasAutoSaveRepository: Boolean,
    hasHttpClient: Boolean,
    hasFileSystem: Boolean,
    isAutoSaveInProgress: Boolean,
    lastAutoSaveTimestampMillis: Long,
    nowMillis: Long,
    minIntervalMillis: Long
): ThreadAutoSaveEffectState {
    val availability = resolveThreadAutoSaveAvailability(
        pageThreadId = page?.threadId,
        expectedThreadId = expectedThreadId,
        isShowingOfflineCopy = isShowingOfflineCopy,
        hasAutoSaveRepository = hasAutoSaveRepository,
        hasHttpClient = hasHttpClient,
        hasFileSystem = hasFileSystem,
        isAutoSaveInProgress = isAutoSaveInProgress,
        lastAutoSaveTimestampMillis = lastAutoSaveTimestampMillis,
        nowMillis = nowMillis,
        minIntervalMillis = minIntervalMillis
    )
    return ThreadAutoSaveEffectState(
        availability = availability,
        page = page.takeIf { availability == ThreadAutoSaveAvailability.Ready }
    )
}

internal data class ThreadInitialScrollRestoreState(
    val shouldRestore: Boolean,
    val savedIndex: Int = 0,
    val savedOffset: Int = 0,
    val totalItems: Int = 0
)

internal fun resolveThreadUrlOverrideSyncState(
    currentResolvedThreadUrlOverride: String?,
    incomingThreadUrlOverride: String?
): String? {
    return incomingThreadUrlOverride
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it != currentResolvedThreadUrlOverride }
}

internal fun resolveThreadInitialScrollRestoreState(
    hasRestoredInitialScroll: Boolean,
    entry: ThreadHistoryEntry?,
    totalItems: Int?
): ThreadInitialScrollRestoreState {
    if (hasRestoredInitialScroll || entry == null || totalItems == null) {
        return ThreadInitialScrollRestoreState(shouldRestore = false)
    }
    return ThreadInitialScrollRestoreState(
        shouldRestore = totalItems > 0,
        savedIndex = entry.lastReadItemIndex,
        savedOffset = entry.lastReadItemOffset,
        totalItems = totalItems
    )
}

internal fun resolveThreadReadAloudIndexUpdate(
    currentIndex: Int,
    segmentCount: Int
): Int? {
    val normalizedIndex = normalizeReadAloudCurrentIndex(
        currentIndex = currentIndex,
        segmentCount = segmentCount
    )
    return normalizedIndex.takeIf { it != currentIndex }
}

internal fun resolveThreadSearchResultIndexUpdate(
    currentIndex: Int,
    matchCount: Int
): Int? {
    val normalizedIndex = normalizeThreadSearchResultIndex(
        currentIndex = currentIndex,
        matchCount = matchCount
    )
    return normalizedIndex.takeIf { it != currentIndex }
}

internal fun resolveThreadSearchQueryResetIndex(
    currentIndex: Int
): Int? {
    return 0.takeIf { currentIndex != 0 }
}

internal fun resolveThreadReplyDialogAutofillState(
    isReplyDialogVisible: Boolean,
    currentState: ThreadReplyDialogState,
    lastUsedDeleteKey: String
): ThreadReplyDialogState? {
    if (!isReplyDialogVisible) return null
    val updatedState = openThreadReplyDialog(
        state = currentState,
        lastUsedDeleteKey = lastUsedDeleteKey
    )
    return updatedState.takeIf { it != currentState }
}

@OptIn(FlowPreview::class)
internal suspend fun collectThreadScrollPositionPersistence(
    listState: LazyListState,
    threadId: String,
    debounceMillis: Long = 1_000L,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit
) {
    snapshotFlow {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
        .distinctUntilChanged()
        .debounce(debounceMillis)
        .collect { (index, offset) ->
            onScrollPositionPersist(threadId, index, offset)
        }
}
