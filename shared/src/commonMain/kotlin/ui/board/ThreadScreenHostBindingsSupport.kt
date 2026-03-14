package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyListState
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem

internal data class ThreadScreenHostBindingsBundle(
    val scaffoldBindings: ThreadScreenScaffoldBindings,
    val contentBindings: ThreadScreenContentHostBindings,
    val overlayBindings: ThreadScreenOverlayHostBindings
)

internal data class ThreadScreenHostScaffoldInputs(
    val modifier: Modifier,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val history: List<ThreadHistoryEntry>,
    val historyDrawerCallbacks: ThreadHistoryDrawerCallbacks,
    val boardName: String,
    val resolvedThreadTitle: String,
    val resolvedReplyCount: Int?,
    val statusLabel: String?,
    val isSearchActive: Boolean,
    val searchQuery: String,
    val currentSearchResultIndex: Int,
    val totalSearchMatches: Int,
    val topBarCallbacks: ThreadTopBarCallbacks,
    val threadMenuEntries: List<ThreadMenuEntryConfig>,
    val actionBarCallbacks: ThreadActionBarCallbacks,
    val isAdsEnabled: Boolean,
    val isDrawerOpen: Boolean,
    val backSwipeEdgePx: Float,
    val backSwipeTriggerPx: Float,
    val onDismissDrawerTap: () -> Unit,
    val onBackSwipe: () -> Unit,
    val actionInProgress: Boolean,
    val readAloudIndicatorSegment: ReadAloudSegment?,
    val appColorScheme: ColorScheme
)

internal data class ThreadScreenHostContentInputs(
    val uiState: ThreadUiState,
    val refreshThread: () -> Unit,
    val threadFilterBinding: ThreadFilterUiStateBinding,
    val persistedSelfPostIdentifiers: List<String>,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val threadFilterCache: LinkedHashMap<ThreadFilterCacheKey, ThreadPage>,
    val lazyListState: LazyListState,
    val saidaneOverrides: Map<String, String>,
    val selfPostIdentifierSet: Set<String>,
    val postHighlightRanges: Map<String, List<IntRange>>,
    val postOverlayState: ThreadPostOverlayState,
    val setPostOverlayState: (ThreadPostOverlayState) -> Unit,
    val onSaidaneClick: (Post) -> Unit,
    val onMediaClick: ((String, MediaType) -> Unit)?,
    val onUrlClick: (String) -> Unit,
    val onRefresh: () -> Unit,
    val isRefreshing: Boolean
)

internal data class ThreadScreenHostOverlayInputs(
    val postOverlayState: ThreadPostOverlayState,
    val sheetOverlayState: ThreadSheetOverlayState,
    val modalOverlayState: ThreadModalOverlayState,
    val history: List<ThreadHistoryEntry>,
    val boardName: String,
    val resolvedThreadTitle: String,
    val replyDialogState: ThreadReplyDialogState,
    val mediaPreviewState: ThreadMediaPreviewState,
    val mediaPreviewEntries: List<MediaPreviewEntry>,
    val galleryPosts: List<Post>?,
    val isSingleMediaSaveInProgress: Boolean,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val ngFilteringEnabled: Boolean,
    val readAloudSegments: List<ReadAloudSegment>,
    val currentReadAloudIndex: Int,
    val firstVisibleSegmentIndex: Int,
    val readAloudStatus: ReadAloudStatus,
    val isPrivacyFilterEnabled: Boolean,
    val saveProgress: SaveProgress?,
    val preferencesState: ScreenPreferencesState,
    val uiBindings: ThreadScreenUiBindingsBundle,
    val filterUiState: ThreadFilterUiState,
    val fileSystem: FileSystem?,
    val autoSavedThreadRepository: SavedThreadRepository?,
    val cookieRepository: CookieRepository?,
    val appColorScheme: ColorScheme,
    val actionCallbacks: ThreadScreenOverlayActionCallbacks,
    val onQuoteFromActionSheet: (Post) -> Unit,
    val onNgRegisterFromActionSheet: (Post) -> Unit,
    val onSaidaneFromActionSheet: (Post) -> Unit,
    val onDelRequestFromActionSheet: (Post) -> Unit,
    val onDeleteFromActionSheet: (Post) -> Unit,
    val onReplySubmit: (() -> Unit)? = null
)

internal fun buildThreadScreenHostBindingsBundle(
    scaffoldInputs: ThreadScreenHostScaffoldInputs,
    contentInputs: ThreadScreenHostContentInputs,
    overlayInputs: ThreadScreenHostOverlayInputs
): ThreadScreenHostBindingsBundle {
    return ThreadScreenHostBindingsBundle(
        scaffoldBindings = ThreadScreenScaffoldBindings(
            modifier = scaffoldInputs.modifier,
            drawerState = scaffoldInputs.drawerState,
            snackbarHostState = scaffoldInputs.snackbarHostState,
            history = scaffoldInputs.history,
            historyDrawerCallbacks = scaffoldInputs.historyDrawerCallbacks,
            boardName = scaffoldInputs.boardName,
            resolvedThreadTitle = scaffoldInputs.resolvedThreadTitle,
            resolvedReplyCount = scaffoldInputs.resolvedReplyCount,
            statusLabel = scaffoldInputs.statusLabel,
            isSearchActive = scaffoldInputs.isSearchActive,
            searchQuery = scaffoldInputs.searchQuery,
            currentSearchResultIndex = scaffoldInputs.currentSearchResultIndex,
            totalSearchMatches = scaffoldInputs.totalSearchMatches,
            topBarCallbacks = scaffoldInputs.topBarCallbacks,
            threadMenuEntries = scaffoldInputs.threadMenuEntries,
            actionBarCallbacks = scaffoldInputs.actionBarCallbacks,
            isAdsEnabled = scaffoldInputs.isAdsEnabled,
            isDrawerOpen = scaffoldInputs.isDrawerOpen,
            backSwipeEdgePx = scaffoldInputs.backSwipeEdgePx,
            backSwipeTriggerPx = scaffoldInputs.backSwipeTriggerPx,
            onDismissDrawerTap = scaffoldInputs.onDismissDrawerTap,
            onBackSwipe = scaffoldInputs.onBackSwipe,
            actionInProgress = scaffoldInputs.actionInProgress,
            readAloudIndicatorSegment = scaffoldInputs.readAloudIndicatorSegment,
            appColorScheme = scaffoldInputs.appColorScheme
        ),
        contentBindings = ThreadScreenContentHostBindings(
            uiState = contentInputs.uiState,
            refreshThread = contentInputs.refreshThread,
            threadFilterBinding = contentInputs.threadFilterBinding,
            persistedSelfPostIdentifiers = contentInputs.persistedSelfPostIdentifiers,
            ngHeaders = contentInputs.ngHeaders,
            ngWords = contentInputs.ngWords,
            ngFilteringEnabled = contentInputs.ngFilteringEnabled,
            threadFilterCache = contentInputs.threadFilterCache,
            lazyListState = contentInputs.lazyListState,
            saidaneOverrides = contentInputs.saidaneOverrides,
            selfPostIdentifierSet = contentInputs.selfPostIdentifierSet,
            postHighlightRanges = contentInputs.postHighlightRanges,
            postOverlayState = contentInputs.postOverlayState,
            setPostOverlayState = contentInputs.setPostOverlayState,
            onSaidaneClick = contentInputs.onSaidaneClick,
            onMediaClick = contentInputs.onMediaClick,
            onUrlClick = contentInputs.onUrlClick,
            onRefresh = contentInputs.onRefresh,
            isRefreshing = contentInputs.isRefreshing
        ),
        overlayBindings = ThreadScreenOverlayHostBindings(
            postOverlayState = overlayInputs.postOverlayState,
            sheetOverlayState = overlayInputs.sheetOverlayState,
            modalOverlayState = overlayInputs.modalOverlayState,
            history = overlayInputs.history,
            boardName = overlayInputs.boardName,
            resolvedThreadTitle = overlayInputs.resolvedThreadTitle,
            replyDialogState = overlayInputs.replyDialogState,
            mediaPreviewState = overlayInputs.mediaPreviewState,
            mediaPreviewEntries = overlayInputs.mediaPreviewEntries,
            galleryPosts = overlayInputs.galleryPosts,
            isSingleMediaSaveInProgress = overlayInputs.isSingleMediaSaveInProgress,
            ngHeaders = overlayInputs.ngHeaders,
            ngWords = overlayInputs.ngWords,
            ngFilteringEnabled = overlayInputs.ngFilteringEnabled,
            readAloudSegments = overlayInputs.readAloudSegments,
            currentReadAloudIndex = overlayInputs.currentReadAloudIndex,
            firstVisibleSegmentIndex = overlayInputs.firstVisibleSegmentIndex,
            readAloudStatus = overlayInputs.readAloudStatus,
            isPrivacyFilterEnabled = overlayInputs.isPrivacyFilterEnabled,
            saveProgress = overlayInputs.saveProgress,
            preferencesState = overlayInputs.preferencesState,
            uiBindings = overlayInputs.uiBindings,
            filterUiState = overlayInputs.filterUiState,
            fileSystem = overlayInputs.fileSystem,
            autoSavedThreadRepository = overlayInputs.autoSavedThreadRepository,
            cookieRepository = overlayInputs.cookieRepository,
            appColorScheme = overlayInputs.appColorScheme,
            onDismissPostActionSheet = overlayInputs.actionCallbacks.onDismissPostActionSheet,
            isSaidaneEnabled = overlayInputs.actionCallbacks.isSaidaneEnabled,
            onQuoteFromActionSheet = overlayInputs.onQuoteFromActionSheet,
            onNgRegisterFromActionSheet = overlayInputs.onNgRegisterFromActionSheet,
            onSaidaneFromActionSheet = overlayInputs.onSaidaneFromActionSheet,
            onDelRequestFromActionSheet = overlayInputs.onDelRequestFromActionSheet,
            onDeleteFromActionSheet = overlayInputs.onDeleteFromActionSheet,
            onDeleteDialogPasswordChange = overlayInputs.actionCallbacks.onDeleteDialogPasswordChange,
            onDeleteDialogImageOnlyChange = overlayInputs.actionCallbacks.onDeleteDialogImageOnlyChange,
            onDeleteDialogDismiss = overlayInputs.actionCallbacks.onDeleteDialogDismiss,
            onDeleteDialogConfirm = overlayInputs.actionCallbacks.onDeleteDialogConfirm,
            onQuoteSelectionDismiss = overlayInputs.actionCallbacks.onQuoteSelectionDismiss,
            onReplySubmit = overlayInputs.onReplySubmit ?: overlayInputs.actionCallbacks.onReplySubmit
        )
    )
}
