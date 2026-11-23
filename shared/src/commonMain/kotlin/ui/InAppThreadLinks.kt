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
        // The board's own `futaba.php?res=N` form opens the same page (its canonical link is /res/N.htm), so it is
        // read as that page (may 1449258130).
        val form = Regex("^(https?://[a-z0-9]+\\.2chan\\.net/[^/]+/)futaba\\.php\\?res=([0-9]+)(#.*)?$", RegexOption.IGNORE_CASE)
        form.matchEntire(withScheme)?.let { match -> return "${match.groupValues[1]}res/${match.groupValues[2]}.htm" }
        return withScheme.takeIf { futaba.matches(it) || aimoge.matches(it) }
    }

    /**
     * The first thread address in a shared text (a browser shares "title https://…"); null when there is none
     * (may 1449258130).
     */
    fun firstIn(text: String): String? {
        if (text.length > 16_384) return null
        return Regex("(?:https?://)?(?:[A-Za-z0-9-]+\\.)*(?:2chan\\.net|nijiurachan\\.net)/[^\\s<>\"'）」』】]+")
            .findAll(text)
            .firstNotNullOfOrNull { normalized(it.value.trimEnd('.', ',', ')', '、', '。')) }
    }

    /** False when [raw] is not a thread address. */
    fun open(raw: String): Boolean {
        val url = normalized(raw) ?: return false
        state.value = url
        return true
    }

    internal fun consume(url: String) { state.compareAndSet(url, null) }
}
