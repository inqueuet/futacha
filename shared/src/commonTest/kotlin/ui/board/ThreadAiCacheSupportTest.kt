package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadAiCacheSupportTest {
    @Test
    fun moderationDecisionSurvivesVotesAndMediaUrlChangesButNotBodyEdits() {
        val post = Post("123", author = "a", subject = null, timestamp = "", messageHtml = "body",
            imageUrl = "https://original/123.jpg", thumbnailUrl = null, saidaneLabel = "そうだねx1")
        fun key(value: Post) = buildThreadPostModerationCacheKey("thread", value, "config", "context")
        assertEquals(key(post), key(post.copy(saidaneLabel = "そうだねx2", imageUrl = "/saved/123.jpg")))
        kotlin.test.assertNotEquals(key(post), key(post.copy(messageHtml = "changed body")))
    }

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
    fun putBoundedAiCacheEntryClearsCacheForNonPositiveLimit() {
        val cache = linkedMapOf(1 to "one")

        putBoundedAiCacheEntry(cache, 2, "two", maxEntries = 0)

        assertTrue(cache.isEmpty())
    }

    @Test
    fun putBoundedAiCacheEntryRefreshesRecencyWhenUpdatingExistingKey() {
        val cache = linkedMapOf(1 to "one", 2 to "two")

        putBoundedAiCacheEntry(cache, 1, "updated", maxEntries = 2)
        putBoundedAiCacheEntry(cache, 3, "three", maxEntries = 2)

        assertEquals(mapOf(1 to "updated", 3 to "three"), cache)
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
    fun unclassifiableHeadDoesNotBlockLaterModerationTargets() {
        val skipped = (1..40).map { post("$it").copy(isDeleted = true) } +
            post("41", "&gt;quoted only") + post("42", "https://example.com") + post("invalid")
        val target = post("43", "判定する本文")
        assertEquals(listOf(target), resolveThreadAiPostModerationSourcePosts(skipped + target))
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

    // Round 3 A-2: the whole futacha moderation pass is keyed on this; before, そうだね, images and
    // thumbnails changed the key and restarted it on every vote or refresh.
    @Test
    fun moderationPassKeyIgnoresVotesAndMediaButFollowsJudgedInput() {
        val longBody = "a".repeat(600) + "x"
        val posts = listOf(post("1"), post("2", body = longBody).copy(saidaneLabel = "そうだねx1"), post("3"))
        fun key(page: List<Post>, provider: String = "both:device:openai") = buildThreadAiModerationCacheKey(
            "may/100", resolveThreadAiPostModerationSourcePosts(page), provider)
        val base = key(posts)
        val voted = posts.map { if (it.id == "2") it.copy(saidaneLabel = "そうだねx5") else it }
        val withMedia = posts.map {
            if (it.id == "1") it.copy(imageUrl = "https://may/src/1.jpg", thumbnailUrl = "https://may/thumb/1s.jpg") else it
        }
        val refreshedCopy = posts.map { it.copy(referencedCount = 4) }
        assertEquals(base, key(voted))
        assertEquals(base, key(withMedia))
        assertEquals(base, key(refreshedCopy))
        // An edit past the first 512 characters with the same length must re-judge.
        val edited = posts.map { if (it.id == "2") it.copy(messageHtml = "a".repeat(600) + "y") else it }
        kotlin.test.assertNotEquals(base, key(edited))
        kotlin.test.assertNotEquals(base, key(posts + post("4")))
        kotlin.test.assertNotEquals(base, key(posts.map { if (it.id == "3") it.copy(isDeleted = true) else it }))
        kotlin.test.assertNotEquals(base, key(posts.reversed()))
        kotlin.test.assertNotEquals(base, key(posts, provider = "device"))
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
