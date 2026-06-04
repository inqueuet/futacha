package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadAiCacheSupportTest {
    @Test
    fun putThreadAiCacheEntryKeepsCacheWithinLimitByEvictingOldestEntry() {
        val cache = linkedMapOf<ThreadAiCacheKey, String>()
        val firstKey = cacheKey(index = 0)

        repeat(THREAD_AI_CACHE_MAX_ENTRIES) { index ->
            putThreadAiCacheEntry(cache, cacheKey(index), "value-$index")
        }

        putThreadAiCacheEntry(cache, cacheKey(THREAD_AI_CACHE_MAX_ENTRIES), "new")

        assertEquals(THREAD_AI_CACHE_MAX_ENTRIES, cache.size)
        assertFalse(cache.containsKey(firstKey))
        assertTrue(cache.containsKey(cacheKey(THREAD_AI_CACHE_MAX_ENTRIES)))
    }

    @Test
    fun putThreadAiCacheEntryUpdatesExistingKeyWithoutEviction() {
        val cache = linkedMapOf<ThreadAiCacheKey, String>()
        val firstKey = cacheKey(index = 0)
        repeat(THREAD_AI_CACHE_MAX_ENTRIES) { index ->
            putThreadAiCacheEntry(cache, cacheKey(index), "value-$index")
        }

        putThreadAiCacheEntry(cache, firstKey, "updated")

        assertEquals(THREAD_AI_CACHE_MAX_ENTRIES, cache.size)
        assertEquals("updated", cache[firstKey])
        assertTrue(cache.containsKey(cacheKey(index = 1)))
    }

    @Test
    fun shouldComputeFullFingerprintWhenAiFeaturesNeedFreshContent() {
        assertFalse(
            shouldComputeFullThreadPostFingerprint(
                shouldComputeForThreadFilters = false,
                shouldShowThreadSummary = false,
                shouldApplyAiPostFilter = false
            )
        )
        assertTrue(
            shouldComputeFullThreadPostFingerprint(
                shouldComputeForThreadFilters = false,
                shouldShowThreadSummary = true,
                shouldApplyAiPostFilter = false
            )
        )
        assertTrue(
            shouldComputeFullThreadPostFingerprint(
                shouldComputeForThreadFilters = false,
                shouldShowThreadSummary = false,
                shouldApplyAiPostFilter = true
            )
        )
    }

    @Test
    fun resolveThreadAiSourcePostsUsesOriginalThreadPagePosts() {
        val posts = listOf(post("1"), post("2"))
        val page = ThreadPage(
            threadId = "100",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = posts
        )

        assertEquals(posts, resolveThreadAiSourcePosts(page))
    }

    private fun cacheKey(index: Int): ThreadAiCacheKey {
        return ThreadAiCacheKey(
            threadId = "thread-$index",
            postsFingerprint = ThreadPostListFingerprint(
                size = index,
                firstPostId = "first-$index",
                lastPostId = "last-$index",
                rollingHash = index.toLong()
            ),
            providerLabel = "端末AI"
        )
    }

    private fun post(id: String): Post {
        return Post(
            id = id,
            author = null,
            subject = null,
            timestamp = "",
            messageHtml = "body $id",
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}
