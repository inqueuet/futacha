package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Use item identities, not list indices: headers, NG filtering and tree order change indices. */
internal fun moderationNearbyPostIds(
    orderedIds: List<String>, visibleIds: Set<String>, radius: Int = 8
): Set<String> {
    val selected = linkedSetOf<String>()
    orderedIds.forEachIndexed { index, id ->
        if (id in visibleIds) {
            for (nearby in (index - radius).coerceAtLeast(0)..(index + radius).coerceAtMost(orderedIds.lastIndex)) {
                selected += orderedIds[nearby]
            }
        }
    }
    return selected
}

@OptIn(FlowPreview::class)
@Composable
internal fun rememberModerationViewport(
    listState: LazyListState,
    orderedIds: List<String>,
    idsByItemKey: Map<String, String>,
    enabled: Boolean
): Set<String> {
    val target = remember(listState, orderedIds, idsByItemKey, enabled) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(target) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo.mapNotNull { idsByItemKey[it.key] }.toSet()
            moderationNearbyPostIds(orderedIds, visible)
        }.distinctUntilChanged().debounce(250).collect { target.value = it }
    }
    return target.value
}
