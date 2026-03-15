package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry

data class ScreenHistoryCallbacks(
    val onHistoryEntrySelected: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryEntryDismissed: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryEntryUpdated: (ThreadHistoryEntry) -> Unit = {},
    val onHistoryRefresh: suspend () -> Unit = {},
    val onHistoryCleared: () -> Unit = {}
)
