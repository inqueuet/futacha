package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.mutableStateListOf

/**
 * The notices of としあき(仮), shown one at a time in the order they came (L143). A save result stays until the
 * person closes it (its path or reason stays readable); a general notice goes after its time. A notice arriving
 * while another is shown waits behind it instead of replacing it unread.
 */
internal class CompatSaveNotices {
    private class Entry(val message: String, val sticky: Boolean)

    private val waiting = mutableStateListOf<Entry>()

    /** The notice shown now, or null. */
    val current: String? get() = waiting.firstOrNull()?.message

    /** Whether the notice shown now stays until closed (a save result). */
    val currentSticky: Boolean get() = waiting.firstOrNull()?.sticky == true

    /** Changes whenever the notice shown changes, also to an equal text (for its timer). */
    val currentToken: Any? get() = waiting.firstOrNull()

    /** A save result: stays until closed. */
    fun offer(message: String) = add(Entry(message, sticky = true))

    /** A general notice: goes after its time. The same text right behind itself is not queued again. */
    fun offerTimed(message: String) {
        if (waiting.lastOrNull()?.let { !it.sticky && it.message == message } == true) return
        add(Entry(message, sticky = false))
    }

    fun dismiss() {
        if (waiting.isNotEmpty()) waiting.removeAt(0)
    }

    /** Ends the shown notice only if it is still [token] (its timer ran out). */
    fun dismiss(token: Any?) {
        if (token != null && waiting.firstOrNull() === token) waiting.removeAt(0)
    }

    private fun add(entry: Entry) {
        // Bounded: a run of notices never grows the list without end; the oldest unread go first.
        if (waiting.size >= MAX_WAITING) waiting.removeAt(1.coerceAtMost(waiting.lastIndex))
        waiting += entry
    }

    private companion object {
        const val MAX_WAITING = 20
    }
}
