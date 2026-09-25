package com.valoser.futacha.shared.compat

import kotlinx.coroutines.sync.Mutex

/**
 * Bounded least-recently-used cache for pure text derivations of post HTML.
 *
 * Thread post rows are recreated whenever they re-enter the viewport, and
 * each row derived the plain text (several regular expressions) and inline
 * links from the same HTML several times, on the main thread. The lock is only
 * ever tried, never awaited: under contention the value is computed without
 * the cache, so a background bulk pass can never stall composition.
 */
internal class CompatTextDerivationCache<V : Any>(
    private val maxEntries: Int,
    private val maxChars: Long,
    private val sizeOf: (key: String, value: V) -> Long
) {
    private val entries = LinkedHashMap<String, V>()
    private var estimatedChars = 0L
    private val mutex = Mutex()

    fun getOrBuild(key: String, build: (String) -> V): V {
        if (mutex.tryLock()) {
            try {
                // Move a hit to the end so eviction stays least-recently-used.
                entries.remove(key)?.let { hit ->
                    entries[key] = hit
                    return hit
                }
            } finally {
                mutex.unlock()
            }
        }
        val value = build(key)
        val size = sizeOf(key, value)
        if (size <= maxChars && mutex.tryLock()) {
            try {
                entries.remove(key)?.let { estimatedChars -= sizeOf(key, it) }
                entries[key] = value
                estimatedChars += size
                while (entries.size > maxEntries || estimatedChars > maxChars) {
                    val iterator = entries.entries.iterator()
                    if (!iterator.hasNext()) break
                    // Read the entry before removing it: Kotlin/Native entries
                    // throw ConcurrentModificationException once removed.
                    val eldest = iterator.next()
                    val eldestSize = sizeOf(eldest.key, eldest.value)
                    iterator.remove()
                    estimatedChars -= eldestSize
                }
            } finally {
                mutex.unlock()
            }
        }
        return value
    }

    internal val size: Int get() = entries.size
}

private const val COMPAT_TEXT_CACHE_MAX_ENTRIES = 768
private const val COMPAT_TEXT_CACHE_MAX_CHARS = 1_500_000L

private val compatPlainTextCache = CompatTextDerivationCache<String>(
    maxEntries = COMPAT_TEXT_CACHE_MAX_ENTRIES,
    maxChars = COMPAT_TEXT_CACHE_MAX_CHARS,
    sizeOf = { key, value -> key.length.toLong() + value.length.toLong() }
)

private val compatInlineLinksCache = CompatTextDerivationCache<List<CompatInlineLink>>(
    maxEntries = COMPAT_TEXT_CACHE_MAX_ENTRIES,
    maxChars = COMPAT_TEXT_CACHE_MAX_CHARS,
    sizeOf = { key, value ->
        key.length.toLong() + value.sumOf { it.url.length.toLong() + 8L }
    }
)

/** [toCompatPlainText] backed by a bounded LRU cache; the result is identical. */
fun String.toCompatPlainTextCached(): String =
    compatPlainTextCache.getOrBuild(this) { it.toCompatPlainText() }

/** [compatInlineLinks] backed by a bounded LRU cache; the result is identical. */
fun compatInlineLinksCached(messageHtml: String): List<CompatInlineLink> =
    compatInlineLinksCache.getOrBuild(messageHtml, ::compatInlineLinks)
