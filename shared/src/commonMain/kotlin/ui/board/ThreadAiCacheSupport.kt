package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage

internal const val THREAD_AI_CACHE_MAX_ENTRIES = 12
internal const val THREAD_AI_POST_MODERATION_CACHE_MAX_ENTRIES = 600

internal data class ThreadAiCacheKey(
    val threadId: String,
    val postsFingerprint: ThreadPostListFingerprint,
    val providerLabel: String
)

internal data class ThreadSummaryCacheKey(
    val threadId: String,
    val providerLabel: String
)

internal data class ThreadPostModerationCacheKey(
    val threadId: String,
    val postId: String,
    val postFingerprint: Long,
    val providerLabel: String
)

internal fun <T> putThreadAiCacheEntry(
    cache: LinkedHashMap<ThreadAiCacheKey, T>,
    key: ThreadAiCacheKey,
    value: T,
    maxEntries: Int = THREAD_AI_CACHE_MAX_ENTRIES
) {
    if (cache.size >= maxEntries && !cache.containsKey(key)) {
        val iterator = cache.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
    cache[key] = value
}

internal fun <K, T> putBoundedAiCacheEntry(
    cache: LinkedHashMap<K, T>,
    key: K,
    value: T,
    maxEntries: Int
) {
    if (cache.size >= maxEntries && !cache.containsKey(key)) {
        val iterator = cache.entries.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
    cache[key] = value
}

internal fun buildThreadSummaryCacheKey(
    threadId: String,
    providerLabel: String
): ThreadSummaryCacheKey {
    return ThreadSummaryCacheKey(
        threadId = threadId,
        providerLabel = providerLabel
    )
}

internal fun buildThreadPostModerationCacheKey(
    threadId: String,
    post: Post,
    providerLabel: String
): ThreadPostModerationCacheKey {
    return ThreadPostModerationCacheKey(
        threadId = threadId,
        postId = post.id,
        postFingerprint = buildThreadPostAiFingerprint(post),
        providerLabel = providerLabel
    )
}

internal fun buildThreadPostAiFingerprint(post: Post): Long {
    var hash = 1_469_598_103_934_665_603L
    hash = mixThreadPostAiFingerprint(hash, post.id)
    hash = mixThreadPostAiFingerprint(hash, post.author)
    hash = mixThreadPostAiFingerprint(hash, post.subject)
    hash = mixThreadPostAiFingerprint(hash, post.posterId)
    hash = mixThreadPostAiFingerprint(hash, post.messageHtml)
    hash = mixThreadPostAiFingerprint(hash, post.imageUrl)
    hash = mixThreadPostAiFingerprint(hash, post.thumbnailUrl)
    hash = mixThreadPostAiFingerprint(hash, post.saidaneLabel)
    hash = mixThreadPostAiFingerprint(hash, if (post.isDeleted) 1 else 0)
    return hash
}

private fun mixThreadPostAiFingerprint(current: Long, value: String?): Long {
    return (current * 1_099_511_628_211L) xor (value?.hashCode()?.toLong() ?: 0L)
}

private fun mixThreadPostAiFingerprint(current: Long, value: Int): Long {
    return (current * 1_099_511_628_211L) xor value.toLong()
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

internal fun resolveThreadAiPostModerationSourcePosts(posts: List<Post>): List<Post> {
    val duplicatePostIds = findDuplicatePostIds(posts)
    if (duplicatePostIds.isEmpty()) return posts
    return posts.filterNot { it.id in duplicatePostIds }
}
