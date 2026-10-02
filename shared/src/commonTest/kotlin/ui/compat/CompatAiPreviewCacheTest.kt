package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatAiPreviewCacheTest {
    @Test fun hiddenDecisionsAreScopedToTheirThreadTextAndService() {
        val cache = CompatAiPreviewCache()
        val owner = Any()
        val source = CompatThreadSnapshot("thread1", 1, 1, posts = listOf(
            CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "hidden body")))
        cache.update(owner, source, setOf("1"))
        assertEquals(setOf("1"), cache.hidden(source.copy(revision = 2, fetchedAtEpochMillis = 2)))
        assertEquals(emptySet(), cache.hidden(source.copy(tabKey = "thread2")))
        assertEquals(emptySet(), cache.hidden(source.copy(posts = listOf(source.posts.single().copy(messageHtml = "edited")))))
        cache.update(Any(), source.copy(tabKey = "thread2"), emptySet())
        assertEquals(emptySet(), cache.hidden(source))
    }

    // Round 3 E9: each tab composes its own service, so entries follow the configuration.
    @Test fun hiddenDecisionsSurviveTabSwitchesWithTheSameConfiguration() {
        val cache = CompatAiPreviewCache()
        val a = CompatThreadSnapshot("threadA", 1, 1, posts = listOf(
            CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "hidden A"),
            CompatPostSnapshot(1, "2", timestamp = "", messageHtml = "kept A")))
        val b = a.copy(tabKey = "threadB")
        cache.update("DEVICE:BOTH:3", a, setOf("1"), setOf("1", "2"))
        // Switching to B: a new service instance with the same configuration key.
        cache.update(buildString { append("DEVICE:BOTH:"); append(3) }, b, emptySet())
        assertEquals(setOf("1"), cache.hidden(a), "B's neighbour preview still hides A's post")
        // Back on A, before the new session has re-checked anything: the entry is kept.
        cache.update("DEVICE:BOTH:3", a, emptySet(), emptySet())
        assertEquals(setOf("1"), cache.hidden(a))
        // An incomplete re-check (no decided set) does not drop it either; an explicit KEEP does.
        cache.update("DEVICE:BOTH:3", a, emptySet(), setOf("2"))
        assertEquals(setOf("1"), cache.hidden(a))
        cache.update("DEVICE:BOTH:3", a, emptySet(), setOf("1"))
        assertEquals(emptySet(), cache.hidden(a))
        // A changed body is never matched by an old entry.
        cache.update("DEVICE:BOTH:3", a, setOf("1"))
        val edited = a.copy(posts = listOf(a.posts[0].copy(messageHtml = "edited"), a.posts[1]))
        cache.update("DEVICE:BOTH:3", edited, emptySet())
        assertEquals(emptySet(), cache.hidden(a))
        // A different configuration clears everything.
        cache.update("DEVICE:BOTH:3", a, setOf("1"))
        cache.update("DEVICE:BOTH:4", b, emptySet())
        assertEquals(emptySet(), cache.hidden(a))
    }

    @Test fun leastRecentlyUpdatedThreadIsEvicted() {
        val cache = CompatAiPreviewCache(maxThreads = 2)
        fun thread(key: String) = CompatThreadSnapshot(key, 1, 1, posts = listOf(
            CompatPostSnapshot(0, "1", timestamp = "", messageHtml = "hidden")))
        cache.update("cfg", thread("t1"), setOf("1"))
        cache.update("cfg", thread("t2"), setOf("1"))
        cache.update("cfg", thread("t1"), setOf("1"))
        cache.update("cfg", thread("t3"), setOf("1"))
        assertEquals(setOf("1"), cache.hidden(thread("t1")))
        assertEquals(emptySet(), cache.hidden(thread("t2")))
        assertEquals(setOf("1"), cache.hidden(thread("t3")))
    }
}
