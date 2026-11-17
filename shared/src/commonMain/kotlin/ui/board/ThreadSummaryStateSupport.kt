package com.valoser.futacha.shared.ui.board

internal fun resolveInitialThreadSummaryUiState(
    shouldShowThreadSummary: Boolean
): ThreadSummaryUiState? {
    return if (shouldShowThreadSummary) {
        ThreadSummaryUiState.Loading
    } else {
        null
    }
}
