package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import kotlin.math.abs

internal data class ThreadScreenScaffoldBindings(
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
    val isDrawerOpen: Boolean,
    val backSwipeEdgePx: Float,
    val backSwipeTriggerPx: Float,
    val onDismissDrawerTap: () -> Unit,
    val onBackSwipe: () -> Unit,
    val actionInProgress: Boolean,
    val readAloudIndicatorSegment: ReadAloudSegment?,
    val appColorScheme: ColorScheme
)

@Composable
internal fun ThreadScreenScaffoldHost(
    bindings: ThreadScreenScaffoldBindings,
    content: @Composable BoxScope.() -> Unit
) {
    ModalNavigationDrawer(
        drawerState = bindings.drawerState,
        gesturesEnabled = true,
        drawerContent = {
            HistoryDrawerContent(
                history = bindings.history,
                onHistoryEntryDismissed = bindings.historyDrawerCallbacks.onHistoryEntryDismissed,
                onHistoryEntrySelected = bindings.historyDrawerCallbacks.onHistoryEntrySelected,
                onBoardClick = bindings.historyDrawerCallbacks.onBoardClick,
                onRefreshClick = bindings.historyDrawerCallbacks.onRefreshClick,
                onBatchDeleteClick = bindings.historyDrawerCallbacks.onBatchDeleteClick,
                onSettingsClick = bindings.historyDrawerCallbacks.onSettingsClick
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
                        searchQuery = bindings.searchQuery,
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
                        onMenuSettings = bindings.topBarCallbacks.onMenuSettings
                    )
                }
            },
            bottomBar = {
                MaterialTheme(
                    colorScheme = bindings.appColorScheme,
                    typography = MaterialTheme.typography,
                    shapes = MaterialTheme.shapes
                ) {
                    ThreadActionBar(
                        menuEntries = bindings.threadMenuEntries,
                        onAction = bindings.actionBarCallbacks.onAction
                    )
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(bindings.isDrawerOpen) {
                    if (!bindings.isDrawerOpen) return@pointerInput
                    awaitPointerEventScope {
                        awaitFirstDown()
                        bindings.onDismissDrawerTap()
                    }
                }
                .pointerInput(
                    bindings.isDrawerOpen,
                    bindings.backSwipeEdgePx,
                    bindings.backSwipeTriggerPx
                ) {
                    if (bindings.isDrawerOpen) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (down.position.x > bindings.backSwipeEdgePx) {
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        var totalDx = 0f
                        var totalDy = 0f
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: event.changes.firstOrNull()
                                ?: continue
                            if (event.changes.none { it.pressed } || !change.pressed) break
                            val delta = change.positionChange()
                            totalDx = (totalDx + delta.x).coerceAtLeast(0f)
                            totalDy += abs(delta.y)
                            if (totalDy > bindings.backSwipeTriggerPx && totalDy > totalDx) {
                                break
                            }
                            if (totalDx > bindings.backSwipeTriggerPx && totalDx > totalDy) {
                                change.consume()
                                bindings.onBackSwipe()
                                break
                            }
                        }
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
}
