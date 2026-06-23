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
    fun putBoundedAiCacheEntryKeepsConfiguredLimit() {
        val cache = linkedMapOf<Int, String>()

        putBoundedAiCacheEntry(cache, 1, "one", maxEntries = 2)
        putBoundedAiCacheEntry(cache, 2, "two", maxEntries = 2)
        putBoundedAiCacheEntry(cache, 3, "three", maxEntries = 2)

        assertEquals(mapOf(2 to "two", 3 to "three"), cache)
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

    @Test
    fun resolveThreadAiPostModerationSourcePostsSkipsDuplicateIds() {
        val first = post("1")
        val duplicateA = post("2", body = "first duplicate")
        val duplicateB = post("2", body = "second duplicate")
        val last = post("3")

        assertEquals(
            listOf(first, last),
            resolveThreadAiPostModerationSourcePosts(
                listOf(first, duplicateA, duplicateB, last)
            )
        )
    }

    @Test
    fun buildThreadPostModerationCacheKeyChangesOnlyForPostContentOrProvider() {
        val original = post("1")
        val same = post("1")
        val changed = original.copy(messageHtml = "changed")

        assertEquals(
            buildThreadPostModerationCacheKey("100", original, "AI"),
            buildThreadPostModerationCacheKey("100", same, "AI")
        )
        assertFalse(
            buildThreadPostModerationCacheKey("100", original, "AI") ==
                buildThreadPostModerationCacheKey("100", changed, "AI")
        )
        assertFalse(
            buildThreadPostModerationCacheKey("100", original, "AI") ==
                buildThreadPostModerationCacheKey("100", original, "Other")
        )
    }

    @Test
    fun buildThreadSummaryCacheKeyIgnoresPostFingerprintForThreadLevelReuse() {
        assertEquals(
            buildThreadSummaryCacheKey("100", "AI"),
            buildThreadSummaryCacheKey("100", "AI")
        )
        assertFalse(buildThreadSummaryCacheKey("100", "AI") == buildThreadSummaryCacheKey("101", "AI"))
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

    private fun post(id: String, body: String = "body $id"): Post {
        return Post(
            id = id,
            author = null,
            subject = null,
            timestamp = "",
            messageHtml = body,
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}
