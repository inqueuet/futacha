package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ai.buildPostModerationBody
import com.valoser.futacha.shared.ai.LocalModerationContext
import com.valoser.futacha.shared.ai.PostModerationResult
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
    val providerLabel: String,
    val contextText: String = ""
)

internal fun <T> putThreadAiCacheEntry(
    cache: LinkedHashMap<ThreadAiCacheKey, T>,
    key: ThreadAiCacheKey,
    value: T,
    maxEntries: Int = THREAD_AI_CACHE_MAX_ENTRIES
) {
    if (maxEntries <= 0) {
        cache.clear()
        return
    }
    cache.remove(key)
    cache[key] = value
    trimAiCache(cache, maxEntries)
}

internal fun <K, T> putBoundedAiCacheEntry(
    cache: LinkedHashMap<K, T>,
    key: K,
    value: T,
    maxEntries: Int
) {
    if (maxEntries <= 0) {
        cache.clear()
        return
    }
    cache.remove(key)
    cache[key] = value
    trimAiCache(cache, maxEntries)
}

private fun <K, T> trimAiCache(cache: LinkedHashMap<K, T>, maxEntries: Int) {
    while (cache.size > maxEntries) {
        val iterator = cache.entries.iterator()
        if (!iterator.hasNext()) break
        iterator.next()
        iterator.remove()
    }
}

/**
 * Key of one moderation pass. Only the judged input is included: ids, order and full bodies of the
 * moderation source posts (deleted and empty posts are already excluded). Saidane counts, images
 * and thumbnails are not judged, so a そうだね or a refresh that changes only them must not restart
 * the pass; an edited body (even past the first 512 characters) or a new post must.
 */
internal fun buildThreadAiModerationCacheKey(
    threadId: String,
    moderationSourcePosts: List<Post>,
    providerLabel: String
): ThreadAiCacheKey {
    var hash = 1_469_598_103_934_665_603L
    moderationSourcePosts.forEach { post ->
        hash = mixThreadPostAiFingerprint(hash, post.id)
        hash = mixThreadPostAiFingerprint(hash, post.messageHtml)
        hash = mixThreadPostAiFingerprint(hash, post.messageHtml.length)
    }
    return ThreadAiCacheKey(
        threadId = threadId,
        postsFingerprint = ThreadPostListFingerprint(
            size = moderationSourcePosts.size,
            firstPostId = moderationSourcePosts.firstOrNull()?.id,
            lastPostId = moderationSourcePosts.lastOrNull()?.id,
            rollingHash = hash
        ),
        providerLabel = providerLabel
    )
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
    providerLabel: String,
    contextText: String = ""
): ThreadPostModerationCacheKey {
    return ThreadPostModerationCacheKey(
        threadId = threadId,
        postId = post.id,
        postFingerprint = buildThreadPostAiFingerprint(post, includePresentation = false),
        providerLabel = providerLabel,
        contextText = contextText
    )
}

internal fun buildThreadPostAiFingerprint(post: Post, includePresentation: Boolean = true): Long {
    var hash = 1_469_598_103_934_665_603L
    hash = mixThreadPostAiFingerprint(hash, post.id)
    hash = mixThreadPostAiFingerprint(hash, post.author)
    hash = mixThreadPostAiFingerprint(hash, post.subject)
    hash = mixThreadPostAiFingerprint(hash, post.posterId)
    hash = mixThreadPostAiFingerprint(hash, post.messageHtml)
    if (includePresentation) {
        hash = mixThreadPostAiFingerprint(hash, post.imageUrl)
        hash = mixThreadPostAiFingerprint(hash, post.thumbnailUrl)
        hash = mixThreadPostAiFingerprint(hash, post.saidaneLabel)
    }
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
    return posts.filter { post ->
        post.id !in duplicatePostIds && !post.isDeleted &&
            post.id.isNotEmpty() && post.id.all(Char::isDigit) &&
            buildPostModerationBody(post.messageHtml).isNotBlank()
    }
}

/** Per-post moderation context and cache lookups for a whole thread, built once per run off the main thread. */
internal class ThreadAiModerationPreparation(
    val context: LocalModerationContext,
    val cacheKeys: Map<String, ThreadPostModerationCacheKey>,
    val externalBodies: Map<String, String>,
    val results: LinkedHashMap<String, PostModerationResult>
)

internal fun prepareThreadAiModeration(
    cacheThreadId: String,
    title: String?,
    sourcePosts: List<Post>,
    external: Boolean,
    hybrid: Boolean,
    providerLabel: String,
    localCache: LinkedHashMap<ThreadPostModerationCacheKey, PostModerationResult>,
    externalCache: LinkedHashMap<String, Pair<String, PostModerationResult>>
): ThreadAiModerationPreparation {
    val context = LocalModerationContext(title, sourcePosts)
    val results = linkedMapOf<String, PostModerationResult>()
    if (external) {
        // Preserve offscreen decisions on refresh; discard edited or removed bodies.
        val bodies = sourcePosts.associate { post ->
            post.id to (post.messageHtml + if (hybrid) "\n" + context.forPosts(listOf(post)) else "")
        }
        externalCache.keys.toList().forEach { id ->
            if (externalCache[id]?.first != bodies[id]) externalCache.remove(id)
        }
        externalCache.forEach { (id, entry) -> results[id] = entry.second }
        return ThreadAiModerationPreparation(context, emptyMap(), bodies, results)
    }
    val keys = sourcePosts.associate { post ->
        post.id to buildThreadPostModerationCacheKey(
            threadId = cacheThreadId,
            post = post,
            providerLabel = providerLabel,
            contextText = context.forPosts(listOf(post))
        )
    }
    sourcePosts.forEach { post -> keys[post.id]?.let(localCache::get)?.let { results[post.id] = it } }
    return ThreadAiModerationPreparation(context, keys, emptyMap(), results)
}

internal fun commitThreadAiModeration(
    prepared: ThreadAiModerationPreparation,
    accepted: Map<String, PostModerationResult>,
    sourcePostCount: Int,
    localCache: LinkedHashMap<ThreadPostModerationCacheKey, PostModerationResult>,
    externalCache: LinkedHashMap<String, Pair<String, PostModerationResult>>
) {
    accepted.forEach { (id, result) ->
        prepared.externalBodies[id]?.let { body -> externalCache[id] = body to result }
        prepared.cacheKeys[id]?.let { key ->
            putBoundedAiCacheEntry(localCache, key, result,
                maxOf(THREAD_AI_POST_MODERATION_CACHE_MAX_ENTRIES, sourcePostCount))
        }
    }
}
