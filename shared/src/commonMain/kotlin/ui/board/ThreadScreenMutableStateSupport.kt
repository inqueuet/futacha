package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.Job

internal data class ThreadScreenMutableStateDefaults(
    val resolvedThreadUrlOverride: String? = null,
    val uiState: ThreadUiState = ThreadUiState.Loading,
    val readAloudStatus: ReadAloudStatus = ReadAloudStatus.Idle,
    val sheetOverlayState: ThreadSheetOverlayState = emptyThreadSheetOverlayState(),
    val currentReadAloudIndex: Int = 0,
    val readAloudCancelRequestedByUser: Boolean = false,
    val isManualSaveInProgress: Boolean = false,
    val isSingleMediaSaveInProgress: Boolean = false,
    val lastAutoSaveTimestamp: Long = 0L,
    val isShowingOfflineCopy: Boolean = false,
    val actionInProgress: Boolean = false,
    val lastBusyActionNoticeAtMillis: Long = 0L,
    val postOverlayState: ThreadPostOverlayState = emptyThreadPostOverlayState(),
    val isReplyDialogVisible: Boolean = false,
    val selectedThreadFilterOptions: Set<ThreadFilterOption> = emptySet(),
    val selectedThreadSortOption: ThreadFilterSortOption? = null,
    val threadFilterKeyword: String = "",
    val modalOverlayState: ThreadModalOverlayState = emptyThreadModalOverlayState(),
    val ngFilteringEnabled: Boolean = true,
    val replyName: String = "",
    val replyEmail: String = "",
    val replySubject: String = "",
    val replyComment: String = "",
    val replyPassword: String = "",
    val replyImageData: ImageData? = null,
    val isRefreshing: Boolean = false,
    val manualRefreshGeneration: Long = 0L,
    val isHistoryRefreshing: Boolean = false,
    val saveProgress: SaveProgress? = null,
    val hasRestoredInitialScroll: Boolean = false,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val currentSearchResultIndex: Int = 0,
    val mediaPreviewState: ThreadMediaPreviewState = emptyThreadMediaPreviewState()
)

internal fun buildThreadScreenMutableStateDefaults(): ThreadScreenMutableStateDefaults {
    return ThreadScreenMutableStateDefaults()
}

internal fun buildThreadScreenMutableStateDefaults(
    threadUrlOverride: String?
): ThreadScreenMutableStateDefaults {
    return ThreadScreenMutableStateDefaults(
        resolvedThreadUrlOverride = threadUrlOverride
    )
}

internal data class ThreadScreenMutableStateBundle(
    val resolvedThreadUrlOverride: MutableState<String?>,
    val uiState: MutableState<ThreadUiState>,
    val readAloudJob: MutableState<Job?>,
    val readAloudStatus: MutableState<ReadAloudStatus>,
    val sheetOverlayState: MutableState<ThreadSheetOverlayState>,
    val currentReadAloudIndex: MutableState<Int>,
    val readAloudCancelRequestedByUser: MutableState<Boolean>,
    val autoSaveJob: MutableState<Job?>,
    val manualSaveJob: MutableState<Job?>,
    val singleMediaSaveJob: MutableState<Job?>,
    val refreshThreadJob: MutableState<Job?>,
    val isManualSaveInProgress: MutableState<Boolean>,
    val isSingleMediaSaveInProgress: MutableState<Boolean>,
    val lastAutoSaveTimestamp: MutableState<Long>,
    val isShowingOfflineCopy: MutableState<Boolean>,
    val actionInProgress: MutableState<Boolean>,
    val lastBusyActionNoticeAtMillis: MutableState<Long>,
    val saidaneOverrides: MutableMap<String, String>,
    val postOverlayState: MutableState<ThreadPostOverlayState>,
    val isReplyDialogVisible: MutableState<Boolean>,
    val selectedThreadFilterOptions: MutableState<Set<ThreadFilterOption>>,
    val selectedThreadSortOption: MutableState<ThreadFilterSortOption?>,
    val threadFilterKeyword: MutableState<String>,
    val threadFilterCache: LinkedHashMap<ThreadFilterCacheKey, ThreadPage>,
    val modalOverlayState: MutableState<ThreadModalOverlayState>,
    val ngFilteringEnabled: MutableState<Boolean>,
    val replyName: MutableState<String>,
    val replyEmail: MutableState<String>,
    val replySubject: MutableState<String>,
    val replyComment: MutableState<String>,
    val replyPassword: MutableState<String>,
    val replyImageData: MutableState<ImageData?>,
    val isRefreshing: MutableState<Boolean>,
    val manualRefreshGeneration: MutableState<Long>,
    val isHistoryRefreshing: MutableState<Boolean>,
    val saveProgress: MutableState<SaveProgress?>,
    val hasRestoredInitialScroll: MutableState<Boolean>,
    val isSearchActive: MutableState<Boolean>,
    val searchQuery: MutableState<String>,
    val currentSearchResultIndex: MutableState<Int>,
    val mediaPreviewState: MutableState<ThreadMediaPreviewState>
)

@Composable
internal fun rememberThreadScreenMutableStateBundle(
    boardId: String,
    threadId: String,
    threadUrlOverride: String?
): ThreadScreenMutableStateBundle {
    val defaults = remember(threadUrlOverride) {
        buildThreadScreenMutableStateDefaults(threadUrlOverride)
    }
    return ThreadScreenMutableStateBundle(
        resolvedThreadUrlOverride = rememberSaveable(boardId, threadId) {
            mutableStateOf(defaults.resolvedThreadUrlOverride)
        },
        uiState = remember(boardId, threadId) { mutableStateOf(defaults.uiState) },
        readAloudJob = remember { mutableStateOf(null as Job?) },
        readAloudStatus = remember { mutableStateOf(defaults.readAloudStatus) },
        sheetOverlayState = remember { mutableStateOf(defaults.sheetOverlayState) },
        currentReadAloudIndex = rememberSaveable(threadId) { mutableStateOf(defaults.currentReadAloudIndex) },
        readAloudCancelRequestedByUser = remember { mutableStateOf(defaults.readAloudCancelRequestedByUser) },
        autoSaveJob = remember { mutableStateOf(null as Job?) },
        manualSaveJob = remember { mutableStateOf(null as Job?) },
        singleMediaSaveJob = remember { mutableStateOf(null as Job?) },
        refreshThreadJob = remember { mutableStateOf(null as Job?) },
        isManualSaveInProgress = remember { mutableStateOf(defaults.isManualSaveInProgress) },
        isSingleMediaSaveInProgress = remember { mutableStateOf(defaults.isSingleMediaSaveInProgress) },
        lastAutoSaveTimestamp = rememberSaveable(threadId) { mutableStateOf(defaults.lastAutoSaveTimestamp) },
        isShowingOfflineCopy = rememberSaveable(threadId) { mutableStateOf(defaults.isShowingOfflineCopy) },
        actionInProgress = remember { mutableStateOf(defaults.actionInProgress) },
        lastBusyActionNoticeAtMillis = remember { mutableStateOf(defaults.lastBusyActionNoticeAtMillis) },
        saidaneOverrides = remember(threadId) { mutableStateMapOf<String, String>() },
        postOverlayState = remember(boardId, threadId) { mutableStateOf(defaults.postOverlayState) },
        isReplyDialogVisible = remember { mutableStateOf(defaults.isReplyDialogVisible) },
        selectedThreadFilterOptions = remember { mutableStateOf(defaults.selectedThreadFilterOptions) },
        selectedThreadSortOption = rememberSaveable { mutableStateOf(defaults.selectedThreadSortOption) },
        threadFilterKeyword = rememberSaveable { mutableStateOf(defaults.threadFilterKeyword) },
        threadFilterCache = remember(threadId) { linkedMapOf() },
        modalOverlayState = remember { mutableStateOf(defaults.modalOverlayState) },
        ngFilteringEnabled = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.ngFilteringEnabled) },
        replyName = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.replyName) },
        replyEmail = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.replyEmail) },
        replySubject = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.replySubject) },
        replyComment = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.replyComment) },
        replyPassword = rememberSaveable(boardId, threadId) { mutableStateOf(defaults.replyPassword) },
        replyImageData = remember { mutableStateOf(defaults.replyImageData) },
        isRefreshing = remember { mutableStateOf(defaults.isRefreshing) },
        manualRefreshGeneration = remember { mutableStateOf(defaults.manualRefreshGeneration) },
        isHistoryRefreshing = remember { mutableStateOf(defaults.isHistoryRefreshing) },
        saveProgress = remember { mutableStateOf(defaults.saveProgress) },
        hasRestoredInitialScroll = remember(threadId) { mutableStateOf(defaults.hasRestoredInitialScroll) },
        isSearchActive = rememberSaveable(threadId) { mutableStateOf(defaults.isSearchActive) },
        searchQuery = rememberSaveable(threadId) { mutableStateOf(defaults.searchQuery) },
        currentSearchResultIndex = remember(threadId) { mutableStateOf(defaults.currentSearchResultIndex) },
        mediaPreviewState = remember { mutableStateOf(defaults.mediaPreviewState) }
    )
}
