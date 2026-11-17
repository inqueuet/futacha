package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import com.valoser.futacha.shared.compat.CompatThreadSnapshot

/**
 * Only hidden decisions are retained, and only while their source text is unchanged. Entries are
 * per thread and survive tab switches (each tab composes its own AI service); they are dropped
 * when the AI configuration changes, when the post is explicitly decided again without hiding,
 * or when the least recently updated thread falls out of the bounded set.
 */
internal class CompatAiPreviewCache(private val maxThreads: Int = 64) {
    private data class Source(val body: String, val posterId: String?, val deleted: Boolean)
    private val entries = mutableStateMapOf<String, Map<String, Source>>()
    // The state map does not keep insertion order; this does (least recently updated first).
    private val order = linkedSetOf<String>()
    private var configuration: Any? = null
    /** Number of [update] calls; each one walks every post of the thread. */
    internal var updateCount = 0
        private set

    /**
     * [owner] identifies the AI configuration (equal values share entries). [decided] are posts
     * whose current result is complete: only those may drop a retained hidden entry, so an
     * unfinished re-check on a revisited tab does not erase it.
     */
    fun update(owner: Any, snapshot: CompatThreadSnapshot, hidden: Set<String>, decided: Set<String> = emptySet()) {
        updateCount++
        if (configuration != owner) { entries.clear(); order.clear(); configuration = owner }
        val previous = entries[snapshot.tabKey].orEmpty()
        val sources = HashMap<String, Source>()
        snapshot.posts.forEach {
            val source = Source(it.messageHtml, it.posterId, it.isDeleted)
            if (it.postNo in hidden || (it.postNo !in decided && previous[it.postNo] == source)) sources[it.postNo] = source
        }
        if (entries[snapshot.tabKey] != sources) entries[snapshot.tabKey] = sources
        order.remove(snapshot.tabKey)
        order.add(snapshot.tabKey)
        while (order.size > maxThreads) order.first().let { order.remove(it); entries.remove(it) }
    }

    fun hidden(snapshot: CompatThreadSnapshot?): Set<String> {
        snapshot ?: return emptySet()
        val retained = entries[snapshot.tabKey] ?: return emptySet()
        return snapshot.posts.filter {
            retained[it.postNo] == Source(it.messageHtml, it.posterId, it.isDeleted)
        }.mapTo(HashSet()) { it.postNo }
    }
}

internal val LocalCompatAiPreviewCache = compositionLocalOf<CompatAiPreviewCache?> { null }
