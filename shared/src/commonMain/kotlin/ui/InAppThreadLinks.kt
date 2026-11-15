package com.valoser.futacha.shared.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A thread URL typed in the app (may 1447453911: a thread found elsewhere, e.g. an isolated one, could only be opened
 * from a link handed in by the OS). The app's root takes it the way of an OS link, so every mode opens it as it opens
 * those links (and asks before adding a board that is not registered).
 */
object InAppThreadLinks {
    private val state = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = state.asStateFlow()

    /** Null when [raw] is not a Futaba or あいもげ thread address; the trimmed address otherwise. */
    fun normalized(raw: String): String? {
        val text = raw.trim()
        if (text.length > 2048) return null
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        val futaba = Regex("^https?://[a-z0-9]+\\.2chan\\.net/[^/]+/res/[0-9]+\\.htm/?(#.*)?$", RegexOption.IGNORE_CASE)
        val aimoge = Regex("^https?://nijiurachan\\.net/b/[^/]+/thread/[0-9a-f-]+/?(#.*)?$", RegexOption.IGNORE_CASE)
        return withScheme.takeIf { futaba.matches(it) || aimoge.matches(it) }
    }

    /** False when [raw] is not a thread address. */
    fun open(raw: String): Boolean {
        val url = normalized(raw) ?: return false
        state.value = url
        return true
    }

    internal fun consume(url: String) { state.compareAndSet(url, null) }
}
