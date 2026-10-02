package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompatSavedThreadSnapshotChoiceTest {
    @Test
    fun fresherCacheKeepsTextButUsesSavedLocalMedia() {
        val cached = snapshot(
            revision = 200L,
            fetchedAt = 200L,
            post("1", "https://may.2chan.net/b/src/1.jpg", "https://may.2chan.net/b/thumb/1s.jpg", "cached text"),
            post("2", null, null, "deleted image")
        )
        val saved = snapshot(
            revision = 100L,
            fetchedAt = 100L,
            post("1", "/data/saved/images/1.jpg", "/data/saved/thumbs/1s.jpg", "saved text"),
            post("2", "/data/saved/images/2.jpg", "/data/saved/thumbs/2s.jpg", "saved text")
        )

        val choice = chooseCompatSavedThreadSnapshot(cached, saved)

        assertTrue(choice.needsSave)
        assertEquals(201L, choice.snapshot.revision)
        assertEquals(200L, choice.snapshot.fetchedAtEpochMillis)
        val first = choice.snapshot.posts[0]
        assertEquals("cached text", first.messageHtml)
        assertEquals("/data/saved/images/1.jpg", first.imageUrl)
        assertEquals("/data/saved/thumbs/1s.jpg", first.thumbnailUrl)
        // A board-side image deletion is not undone by the saved copy.
        assertNull(choice.snapshot.posts[1].imageUrl)
        assertNull(choice.snapshot.posts[1].thumbnailUrl)
    }

    @Test
    fun fullerCacheWithAdditionalRepliesKeepsRemoteMediaForUnsavedPosts() {
        val cached = snapshot(
            revision = 50L,
            fetchedAt = 50L,
            post("1", "https://may.2chan.net/b/src/1.jpg", "https://may.2chan.net/b/thumb/1s.jpg"),
            post("2", "https://may.2chan.net/b/src/2.jpg", "https://may.2chan.net/b/thumb/2s.jpg")
        )
        val saved = snapshot(
            revision = 100L,
            fetchedAt = 100L,
            post("1", "content://tree/document/1.jpg", "https://may.2chan.net/b/thumb/1s.jpg")
        )

        val choice = chooseCompatSavedThreadSnapshot(cached, saved)

        assertTrue(choice.needsSave)
        assertEquals("content://tree/document/1.jpg", choice.snapshot.posts[0].imageUrl)
        // The saved copy had no local thumbnail; keep the cached one.
        assertEquals("https://may.2chan.net/b/thumb/1s.jpg", choice.snapshot.posts[0].thumbnailUrl)
        assertEquals("https://may.2chan.net/b/src/2.jpg", choice.snapshot.posts[1].imageUrl)
    }

    @Test
    fun cacheWithoutLocalReplacementsIsNotRewritten() {
        val cached = snapshot(
            revision = 200L,
            fetchedAt = 200L,
            post("1", "https://may.2chan.net/b/src/1.jpg", null)
        )
        val saved = snapshot(
            revision = 100L,
            fetchedAt = 100L,
            post("1", "https://may.2chan.net/b/src/1.jpg", null)
        )

        val choice = chooseCompatSavedThreadSnapshot(cached, saved)

        assertFalse(choice.needsSave)
        assertSame(cached, choice.snapshot)
    }

    @Test
    fun olderCacheIsReplacedBySavedCopy() {
        val cached = snapshot(revision = 50L, fetchedAt = 50L, post("1", "https://x/src/1.jpg", null))
        val saved = snapshot(revision = 100L, fetchedAt = 100L, post("1", "/saved/1.jpg", null))

        val choice = chooseCompatSavedThreadSnapshot(cached, saved)
        assertTrue(choice.needsSave)
        assertSame(saved, choice.snapshot)

        val withoutCache = chooseCompatSavedThreadSnapshot(null, saved)
        assertTrue(withoutCache.needsSave)
        assertSame(saved, withoutCache.snapshot)
    }

    private fun post(
        postNo: String,
        imageUrl: String?,
        thumbnailUrl: String?,
        message: String = "body"
    ) = CompatPostSnapshot(
        position = postNo.toInt() - 1,
        postNo = postNo,
        timestamp = "26/10/02(金)00:00:00",
        messageHtml = message,
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl
    )

    private fun snapshot(revision: Long, fetchedAt: Long, vararg posts: CompatPostSnapshot) =
        CompatThreadSnapshot(
            tabKey = "tab",
            revision = revision,
            fetchedAtEpochMillis = fetchedAt,
            posts = posts.toList()
        )
}
