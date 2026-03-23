package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    private fun post(
        imageUrl: String?,
        thumbnailUrl: String?
    ): Post {
        return Post(
            id = "1",
            order = 0,
            author = "a",
            subject = "s",
            timestamp = "t",
            posterId = null,
            messageHtml = "body",
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            saidaneLabel = null,
            isDeleted = false,
            referencedCount = 0,
            quoteReferences = emptyList()
        )
    }
}
