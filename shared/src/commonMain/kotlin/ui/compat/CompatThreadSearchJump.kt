package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Decides when the thread search scrolls to its current hit.
 *
 * Matches are recomputed whenever the thread refreshes. Jumping on every
 * recomputation pulled the reader back to the hit after each update, so the
 * jump only happens for a new query, a restored screen (nothing shown yet) or
 * an index that had to be clamped because hits disappeared. The next/previous
 * buttons scroll by themselves; their index change is only recorded.
 *
 * [hitsQuery] is Compose state so that a new query whose hit list happens to
 * equal the previous one still restarts the jump effect (it is one of its keys).
 */
internal class CompatSearchJumpTracker {
    /** The query the current hit list was computed for; older lists are stale. */
    var hitsQuery: String? by mutableStateOf(null)
    private var lastQuery: String? = null

    fun reset() {
        lastQuery = null
    }

    fun shouldJump(query: String, requestedIndex: Int, coercedIndex: Int): Boolean {
        val jump = lastQuery != query || requestedIndex != coercedIndex
        lastQuery = query
        return jump
    }
}
