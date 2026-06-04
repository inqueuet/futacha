package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage

internal const val THREAD_AI_CACHE_MAX_ENTRIES = 12

internal data class ThreadAiCacheKey(
    val threadId: String,
    val postsFingerprint: ThreadPostListFingerprint,
    val providerLabel: String
)

internal fun <T> putThreadAiCacheEntry(
    cache: LinkedHashMap<ThreadAiCacheKey, T>,
    key: ThreadAiCacheKey,
    value: T
) {
    if (cache.size >= THREAD_AI_CACHE_MAX_ENTRIES && !cache.containsKey(key)) {
        val iterator = cache.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
    cache[key] = value
}

internal fun shouldComputeFullThreadPostFingerprint(
    shouldComputeForThreadFilters: Boolean,
    shouldShowThreadSummary: Boolean,
    shouldApplyAiPostFilter: Boolean
): Boolean {
    return shouldComputeForThreadFilters || shouldShowThreadSummary || shouldApplyAiPostFilter
}

internal fun resolveThreadAiSourcePosts(page: ThreadPage): List<Post> {
    return page.posts
}
