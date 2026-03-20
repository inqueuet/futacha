package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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

@Composable
internal fun rememberThreadAutoSaveEffectState(
    currentPageForAutoSave: ThreadPage?,
    threadId: String,
    isShowingOfflineCopy: Boolean,
    autoSaveRepository: SavedThreadRepository?,
    httpClient: HttpClient?,
    fileSystem: com.valoser.futacha.shared.util.FileSystem?,
    autoSaveJob: Job?,
    lastAutoSaveTimestampMillis: Long
): ThreadAutoSaveEffectState {
    return remember(
        currentPageForAutoSave,
        threadId,
        isShowingOfflineCopy,
        autoSaveRepository,
        httpClient,
        fileSystem,
        autoSaveJob,
        lastAutoSaveTimestampMillis
    ) {
        resolveThreadAutoSaveEffectState(
            page = currentPageForAutoSave,
            expectedThreadId = threadId,
            isShowingOfflineCopy = isShowingOfflineCopy,
            hasAutoSaveRepository = autoSaveRepository != null,
            hasHttpClient = httpClient != null,
            hasFileSystem = fileSystem != null,
            isAutoSaveInProgress = autoSaveJob?.isActive == true,
            lastAutoSaveTimestampMillis = lastAutoSaveTimestampMillis,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS
        )
    }
}

@Composable
internal fun ThreadUrlOverrideSyncEffect(
    threadUrlOverride: String?,
    resolvedThreadUrlOverride: String?,
    onResolvedThreadUrlOverrideChanged: (String) -> Unit
) {
    LaunchedEffect(threadUrlOverride) {
        resolveThreadUrlOverrideSyncState(
            currentResolvedThreadUrlOverride = resolvedThreadUrlOverride,
            incomingThreadUrlOverride = threadUrlOverride
        )?.let(onResolvedThreadUrlOverrideChanged)
    }
}

@Composable
internal fun ThreadReplyDialogAutofillEffect(
    isReplyDialogVisible: Boolean,
    lastUsedDeleteKey: String,
    replyDialogBinding: ThreadReplyDialogStateBinding
) {
    LaunchedEffect(isReplyDialogVisible, lastUsedDeleteKey) {
        resolveThreadReplyDialogAutofillState(
            isReplyDialogVisible = isReplyDialogVisible,
            currentState = replyDialogBinding.currentState(),
            lastUsedDeleteKey = lastUsedDeleteKey
        )?.let(replyDialogBinding::setState)
    }
}

@Composable
internal fun ThreadReadAloudIndexEffect(
    segmentCount: Int,
    currentReadAloudIndex: Int,
    onCurrentReadAloudIndexChanged: (Int) -> Unit
) {
    LaunchedEffect(segmentCount) {
        resolveThreadReadAloudIndexUpdate(
            currentIndex = currentReadAloudIndex,
            segmentCount = segmentCount
        )?.let(onCurrentReadAloudIndexChanged)
    }
}

@Composable
internal fun ThreadInitialScrollRestoreEffect(
    threadId: String,
    restoreState: ThreadInitialScrollRestoreState,
    lazyListState: LazyListState,
    onRestoreCompleted: () -> Unit,
    onRestoreFailed: (String, Throwable?) -> Unit
) {
    LaunchedEffect(threadId, restoreState) {
        if (!restoreState.shouldRestore) return@LaunchedEffect
        restoreThreadScrollPositionSafely(
            listState = lazyListState,
            savedIndex = restoreState.savedIndex,
            savedOffset = restoreState.savedOffset,
            totalItems = restoreState.totalItems,
            onFailure = onRestoreFailed
        )
        onRestoreCompleted()
    }
}

@Composable
internal fun ThreadSearchIndexEffects(
    searchQuery: String,
    threadId: String,
    searchMatches: List<ThreadSearchMatch>,
    currentSearchResultIndex: Int,
    onCurrentSearchResultIndexChanged: (Int) -> Unit
) {
    LaunchedEffect(searchQuery, threadId) {
        resolveThreadSearchQueryResetIndex(
            currentIndex = currentSearchResultIndex
        )?.let(onCurrentSearchResultIndexChanged)
    }
    LaunchedEffect(searchMatches) {
        resolveThreadSearchResultIndexUpdate(
            currentIndex = currentSearchResultIndex,
            matchCount = searchMatches.size
        )?.let(onCurrentSearchResultIndexChanged)
    }
}

@Composable
internal fun ThreadScrollPersistenceEffect(
    threadId: String,
    lazyListState: LazyListState,
    onScrollPositionPersist: (threadId: String, index: Int, offset: Int) -> Unit
) {
    LaunchedEffect(threadId, lazyListState) {
        collectThreadScrollPositionPersistence(
            listState = lazyListState,
            threadId = threadId,
            onScrollPositionPersist = onScrollPositionPersist
        )
    }
}

@Composable
internal fun ThreadAutoSaveLaunchEffect(
    threadId: String,
    currentPageForAutoSave: ThreadPage?,
    isShowingOfflineCopy: Boolean,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    autoSaveEffectState: ThreadAutoSaveEffectState,
    onStartAutoSave: (ThreadPage) -> Unit
) {
    LaunchedEffect(
        threadId,
        currentPageForAutoSave,
        isShowingOfflineCopy,
        httpClient,
        fileSystem
    ) {
        autoSaveEffectState.page?.let(onStartAutoSave)
    }
}
