package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadMediaSupportTest {
    @Test
    fun resolvePostMediaUrls_prioritize_image_for_target_and_thumbnail_for_display() {
        val post = post(
            imageUrl = "https://example.com/src/sample.webm",
            thumbnailUrl = "https://example.com/thumb/sample.jpg"
        )

        assertEquals("https://example.com/thumb/sample.jpg", resolvePostDisplayMediaUrl(post))
        assertEquals("https://example.com/src/sample.webm", resolvePostTargetMediaUrl(post))
        assertEquals(MediaType.Video, resolvePostTargetMediaType(post))
    }

    @Test
    fun resolvePostMediaUrls_fall_back_when_one_side_is_missing() {
        val imageOnlyPost = post(
            imageUrl = "https://example.com/src/sample.gif",
            thumbnailUrl = null
        )
        assertEquals("https://example.com/src/sample.gif", resolvePostDisplayMediaUrl(imageOnlyPost))
        assertEquals("https://example.com/src/sample.gif", resolvePostTargetMediaUrl(imageOnlyPost))
        assertEquals(MediaType.Image, resolvePostTargetMediaType(imageOnlyPost))

        val thumbnailOnlyPost = post(
            imageUrl = null,
            thumbnailUrl = "https://example.com/thumb/sample.jpg"
        )
        assertEquals("https://example.com/thumb/sample.jpg", resolvePostDisplayMediaUrl(thumbnailOnlyPost))
        assertEquals("https://example.com/thumb/sample.jpg", resolvePostTargetMediaUrl(thumbnailOnlyPost))
    }

    @Test
    fun resolvePostTargetMediaUrl_prefers_explicit_url() {
        val post = post(
            imageUrl = "https://example.com/src/sample.jpg",
            thumbnailUrl = "https://example.com/thumb/sample.jpg"
        )

        assertEquals(
            "https://example.com/custom/preview.jpg",
            resolvePostTargetMediaUrl(post, preferredUrl = "https://example.com/custom/preview.jpg")
        )
        assertEquals(
            MediaType.Image,
            resolvePostTargetMediaType(
                post = post,
                preferredUrl = "https://example.com/custom/preview.jpg",
                preferredMediaType = MediaType.Image
            )
        )
    }

    @Test
    fun resolvePostMediaUrls_return_null_when_post_has_no_media() {
        val post = post(imageUrl = null, thumbnailUrl = null)

        assertNull(resolvePostDisplayMediaUrl(post))
        assertNull(resolvePostTargetMediaUrl(post))
    }

    @Test
    fun isolatedPostRetainsItsRowButDoesNotExposeAttachmentMedia() {
        val isolated = post(
            imageUrl = "https://example.com/src/isolated.jpg",
            thumbnailUrl = "https://example.com/thumb/isolated.jpg"
        ).copy(isIsolated = true)

        assertNull(resolvePostDisplayMediaUrl(isolated))
        assertNull(resolvePostTargetMediaUrl(isolated))
        assertNull(buildMediaPreviewEntry(isolated))
    }

    @Test
    fun buildMediaPreviewEntry_uses_lightweight_fallback_title() {
        val post = post(
            imageUrl = "https://example.com/src/sample.jpg",
            thumbnailUrl = null,
            subject = "件名",
            messageHtml = "本文1行目<br>本文2行目"
        )

        val entry = buildMediaPreviewEntry(post)

        assertEquals("件名", entry?.title)
        assertEquals("本文1行目<br>本文2行目", entry?.messageHtml)
    }

    @Test
    fun resolveMediaPreviewDisplayTitle_uses_message_first_line_lazily() {
        val post = post(
            imageUrl = "https://example.com/src/sample.jpg",
            thumbnailUrl = null,
            subject = "件名",
            messageHtml = "本文1行目<br>本文2行目"
        )
        val entry = buildMediaPreviewEntry(post)

        assertEquals("本文1行目", entry?.let(::resolveMediaPreviewDisplayTitle))
    }

    @Test
    fun resolveMediaPreviewDisplayTitle_falls_back_to_entry_title() {
        val post = post(
            imageUrl = "https://example.com/src/sample.jpg",
            thumbnailUrl = null,
            subject = "件名",
            messageHtml = ""
        )
        val entry = buildMediaPreviewEntry(post)

        assertEquals("件名", entry?.let(::resolveMediaPreviewDisplayTitle))
    }

    @Test
    fun buildMediaPreviewIndexByKey_preserves_first_index_for_duplicate_keys() {
        val entries = listOf(
            MediaPreviewEntry(
                url = "https://example.com/src/sample.jpg",
                mediaType = MediaType.Image,
                postId = "1",
                title = "first"
            ),
            MediaPreviewEntry(
                url = "https://example.com/src/sample.jpg",
                mediaType = MediaType.Image,
                postId = "2",
                title = "second"
            ),
            MediaPreviewEntry(
                url = "https://example.com/src/sample.webm",
                mediaType = MediaType.Video,
                postId = "3",
                title = "video"
            )
        )

        val indexByKey = buildMediaPreviewIndexByKey(entries)

        assertEquals(
            0,
            indexByKey[MediaPreviewKey("https://example.com/src/sample.jpg", MediaType.Image)]
        )
        assertEquals(
            2,
            indexByKey[MediaPreviewKey("https://example.com/src/sample.webm", MediaType.Video)]
        )
    }

    @Test
    fun buildStableMediaPreviewCollection_retries_when_posts_change_during_build() = runBlocking {
        val firstPosts = listOf(
            post(
                id = "1",
                imageUrl = "https://example.com/src/first.jpg",
                thumbnailUrl = null
            )
        )
        val refreshedPosts = listOf(
            post(
                id = "2",
                imageUrl = "https://example.com/src/refreshed.jpg",
                thumbnailUrl = null
            )
        )
        var currentPosts = firstPosts
        val builtSnapshots = mutableListOf<List<Post>>()

        val stable = buildStableMediaPreviewCollection(
            currentPosts = { currentPosts },
            buildCollection = { posts ->
                builtSnapshots += posts
                if (posts === firstPosts) {
                    currentPosts = refreshedPosts
                }
                MediaPreviewCollection(
                    entries = posts.mapNotNull(::buildMediaPreviewEntry),
                    indexByKey = buildMediaPreviewIndexByKey(posts.mapNotNull(::buildMediaPreviewEntry))
                )
            }
        )

        assertEquals(listOf(firstPosts, refreshedPosts), builtSnapshots)
        assertSame(refreshedPosts, stable.posts)
        assertEquals(
            listOf("https://example.com/src/refreshed.jpg"),
            stable.collection.entries.map(MediaPreviewEntry::url)
        )
    }

    @Test
    fun buildStableMediaPreviewCollection_stopsAfterBoundedRetries() = runBlocking {
        var sourceCalls = 0
        var buildCalls = 0
        val stable = buildStableMediaPreviewCollection(
            currentPosts = {
                sourceCalls++
                listOf(post(id = sourceCalls.toString(), imageUrl = null, thumbnailUrl = null))
            },
            buildCollection = { posts ->
                buildCalls++
                MediaPreviewCollection(
                    entries = posts.mapNotNull(::buildMediaPreviewEntry),
                    indexByKey = emptyMap<MediaPreviewKey, Int>()
                )
            }
        )

        assertEquals(4, buildCalls)
        assertTrue(stable.posts.isNotEmpty())
    }

    private fun post(
        id: String = "1",
        imageUrl: String?,
        thumbnailUrl: String?,
        subject: String? = "s",
        messageHtml: String = "body"
    ): Post {
        return Post(
            id = id,
            order = 0,
            author = "a",
            subject = subject,
            timestamp = "t",
            posterId = null,
            messageHtml = messageHtml,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            saidaneLabel = null,
            isDeleted = false,
            referencedCount = 0,
            quoteReferences = emptyList()
        )
    }
}
