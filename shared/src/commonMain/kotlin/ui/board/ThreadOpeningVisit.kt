package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One actual thread opening, retained by its host while a child screen is displayed. */
internal class ThreadOpeningVisit {
    var tracker: ThreadOpeningSummaryTracker? by mutableStateOf(null)
        private set

    fun initialize(postCount: Int?, lastPostId: String? = null) {
        if (tracker == null) tracker = ThreadOpeningSummaryTracker(postCount, lastPostId)
    }
}
