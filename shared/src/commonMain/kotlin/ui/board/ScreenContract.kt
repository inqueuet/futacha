package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadHistoryEntry

data class ScreenContract(
    val history: List<ThreadHistoryEntry>,
    val historyCallbacks: ScreenHistoryCallbacks,
    val preferencesState: ScreenPreferencesState,
    val preferencesCallbacks: ScreenPreferencesCallbacks
)
