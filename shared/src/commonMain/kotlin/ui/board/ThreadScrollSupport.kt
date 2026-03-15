package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.lazy.LazyListState

internal data class ThreadScrollRestoreTarget(
    val index: Int,
    val offset: Int
)

internal fun resolveThreadScrollRestoreTarget(
    savedIndex: Int,
    savedOffset: Int,
    totalItems: Int
): ThreadScrollRestoreTarget? {
    if (totalItems <= 0) return null
    return ThreadScrollRestoreTarget(
        index = savedIndex.coerceIn(0, totalItems - 1),
        offset = savedOffset.coerceAtLeast(0)
    )
}

internal fun buildThreadScrollRestoreFailureMessage(
    index: Int,
    offset: Int,
    error: Throwable
): String {
    return "Failed to restore scroll position index=$index offset=$offset: ${error.message}"
}

internal suspend fun restoreThreadScrollPositionSafely(
    listState: LazyListState,
    savedIndex: Int,
    savedOffset: Int,
    totalItems: Int,
    onFailure: (String, Throwable) -> Unit = { _, _ -> }
) {
    val target = resolveThreadScrollRestoreTarget(
        savedIndex = savedIndex,
        savedOffset = savedOffset,
        totalItems = totalItems
    ) ?: return
    runCatching {
        listState.scrollToItem(target.index, target.offset)
    }.onFailure { error ->
        onFailure(buildThreadScrollRestoreFailureMessage(target.index, target.offset, error), error)
    }
}
