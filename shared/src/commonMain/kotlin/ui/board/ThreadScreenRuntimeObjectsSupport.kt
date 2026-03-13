package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.CoroutineScope

internal data class ThreadScreenRuntimeObjectSeed(
    val initialListIndex: Int,
    val initialListOffset: Int
)

internal data class ThreadScreenRuntimeObjectBundle(
    val snackbarHostState: SnackbarHostState,
    val coroutineScope: CoroutineScope,
    val drawerState: DrawerState,
    val isDrawerOpen: State<Boolean>,
    val lazyListState: LazyListState
)

internal fun buildThreadScreenRuntimeObjectSeed(
    initialHistoryEntry: ThreadHistoryEntry?
): ThreadScreenRuntimeObjectSeed {
    return ThreadScreenRuntimeObjectSeed(
        initialListIndex = initialHistoryEntry?.lastReadItemIndex ?: 0,
        initialListOffset = initialHistoryEntry?.lastReadItemOffset ?: 0
    )
}

@Composable
internal fun rememberThreadScreenRuntimeObjectBundle(
    threadId: String,
    initialHistoryEntry: ThreadHistoryEntry?
): ThreadScreenRuntimeObjectBundle {
    val seed = remember(initialHistoryEntry) {
        buildThreadScreenRuntimeObjectSeed(initialHistoryEntry)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val isDrawerOpen = remember(drawerState) {
        derivedStateOf {
            drawerState.currentValue == DrawerValue.Open ||
                drawerState.targetValue == DrawerValue.Open
        }
    }
    val lazyListState = remember(threadId) {
        LazyListState(
            seed.initialListIndex,
            seed.initialListOffset
        )
    }
    return ThreadScreenRuntimeObjectBundle(
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        drawerState = drawerState,
        isDrawerOpen = isDrawerOpen,
        lazyListState = lazyListState
    )
}
