package com.valoser.futacha.shared.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WatchReadAloudStatusStore {
    private val mutableStatus = MutableStateFlow<WatchReadAloudStatus?>(null)

    val status: StateFlow<WatchReadAloudStatus?> = mutableStatus.asStateFlow()

    fun update(status: WatchReadAloudStatus) {
        mutableStatus.value = status
    }

    fun clearIfMatches(
        boardId: String,
        boardUrl: String,
        threadId: String
    ) {
        val current = mutableStatus.value ?: return
        if (current.boardId == boardId &&
            current.boardUrl == boardUrl &&
            current.threadId == threadId
        ) {
            mutableStatus.value = null
        }
    }
}
