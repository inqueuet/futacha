package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadBodyTextSize
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.ui.util.platformSystemGestureExclusion

private val THREAD_DRAWER_GESTURE_EXCLUSION_WIDTH = 48.dp

internal data class ThreadScreenScaffoldBindings(
    val modifier: Modifier,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val history: List<ThreadHistoryEntry>,
    val historyDrawerCallbacks: ThreadHistoryDrawerCallbacks,
    val historyDrawerTextSize: ThreadBodyTextSize,
    val isHistoryRefreshing: Boolean = false,
    val boardName: String,
    val resolvedThreadTitle: String,
    val resolvedReplyCount: Int?,
    val statusLabel: String?,
    val isSearchActive: Boolean,
    val searchQueryState: State<String>,
    val currentSearchResultIndex: Int,
    val totalSearchMatches: Int,
    val compactHeader: Boolean,
    val topBarCallbacks: ThreadTopBarCallbacks,
    val threadMenuEntries: List<ThreadMenuEntryConfig>,
    val actionBarCallbacks: ThreadActionBarCallbacks,
    val isDrawerOpen: Boolean,
    val onDismissDrawerTap: () -> Unit,
    val actionInProgress: Boolean,
    val readAloudIndicatorSegment: ReadAloudSegment?,
    val appColorScheme: ColorScheme
)

@Composable
internal fun ThreadScreenScaffoldHost(
    bindings: ThreadScreenScaffoldBindings,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = bindings.drawerState,
            gesturesEnabled = true,
            drawerContent = {
                HistoryDrawerContent(
                    history = bindings.history,
                    onHistoryEntryDismissed = bindings.historyDrawerCallbacks.onHistoryEntryDismissed,
                    onHistoryEntrySelected = bindings.historyDrawerCallbacks.onHistoryEntrySelected,
                    isHistoryRefreshing = bindings.isHistoryRefreshing,
                    onBoardClick = bindings.historyDrawerCallbacks.onBoardClick,
                    onRefreshClick = bindings.historyDrawerCallbacks.onRefreshClick,
                    onBatchDeleteClick = bindings.historyDrawerCallbacks.onBatchDeleteClick,
                    onExportClick = bindings.historyDrawerCallbacks.onExportClick,
                    onExportThenClearClick = bindings.historyDrawerCallbacks.onExportThenClearClick,
                    onExportSelectedClick = bindings.historyDrawerCallbacks.onExportSelectedClick,
                    onLoadImportPreview = bindings.historyDrawerCallbacks.onLoadImportPreview,
                    onImportClick = bindings.historyDrawerCallbacks.onImportClick,
                    onImportSelectedClick = bindings.historyDrawerCallbacks.onImportSelectedClick,
                    onSettingsClick = bindings.historyDrawerCallbacks.onSettingsClick,
                    bodyTextSize = bindings.historyDrawerTextSize
                )
            }
        ) {
            Scaffold(
                modifier = bindings.modifier,
                snackbarHost = { SnackbarHost(bindings.snackbarHostState) },
                topBar = {
                    MaterialTheme(
                        colorScheme = bindings.appColorScheme,
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes
                    ) {
                        ThreadTopBar(
                            boardName = bindings.boardName,
                            threadTitle = bindings.resolvedThreadTitle,
                            replyCount = bindings.resolvedReplyCount,
                            statusLabel = bindings.statusLabel,
                            isSearchActive = bindings.isSearchActive,
                            searchQueryState = bindings.searchQueryState,
                            currentSearchIndex = bindings.currentSearchResultIndex,
                            totalSearchMatches = bindings.totalSearchMatches,
                            onSearchQueryChange = bindings.topBarCallbacks.onSearchQueryChange,
                            onSearchPrev = bindings.topBarCallbacks.onSearchPrev,
                            onSearchNext = bindings.topBarCallbacks.onSearchNext,
                            onSearchSubmit = bindings.topBarCallbacks.onSearchSubmit,
                            onSearchClose = bindings.topBarCallbacks.onSearchClose,
                            onBack = bindings.topBarCallbacks.onBack,
                            onOpenHistory = bindings.topBarCallbacks.onOpenHistory,
                            onSearch = bindings.topBarCallbacks.onSearch,
                            onMenuSettings = bindings.topBarCallbacks.onMenuSettings,
                            compactHeader = bindings.compactHeader
                        )
                    }
                },
                bottomBar = {
                    MaterialTheme(
                        colorScheme = bindings.appColorScheme,
                        typography = MaterialTheme.typography,
                        shapes = MaterialTheme.shapes
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            ThreadActionBar(
                                menuEntries = bindings.threadMenuEntries,
                                onAction = bindings.actionBarCallbacks.onAction,
                                applyNavigationBarsPadding = false
                            )
                        }
                    }
                }
            ) { innerPadding ->
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("futacha-thread-content")
                    .pointerInput(bindings.isDrawerOpen) {
                        if (!bindings.isDrawerOpen) return@pointerInput
                        awaitPointerEventScope {
                            awaitFirstDown()
                            bindings.onDismissDrawerTap()
                        }
                    }
                    .background(MaterialTheme.colorScheme.background)

                Box(modifier = contentModifier) {
                    content()
                    if (bindings.actionInProgress) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                        )
                    }
                    bindings.readAloudIndicatorSegment?.let { segment ->
                        ReadAloudIndicator(
                            segment = segment,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp)
                        )
                    }
                }
            }
        }

        // The drawer and Android's system Back gesture both start at the physical left edge.
        // Reserve only the drawer's narrow start strip so a slow drawer drag cannot be delivered
        // as Back while the sheet is partially visible (issue #36). This is a no-op off Android.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(THREAD_DRAWER_GESTURE_EXCLUSION_WIDTH)
                .fillMaxHeight()
                .platformSystemGestureExclusion()
        )
    }
}
