package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThreadAttachmentGallerySupportTest {
    @Test
    fun buildThreadAttachmentGalleryItems_assigns_expected_badges() {
        val items = buildThreadAttachmentGalleryItems(
            listOf(
                post(
                    id = "1",
                    imageUrl = "https://example.com/src/movie.webm",
                    thumbnailUrl = null
                ),
                post(
                    id = "2",
                    imageUrl = "https://example.com/src/loop.gif",
                    thumbnailUrl = "https://example.com/thumb/loops.jpg"
                ),
                post(
                    id = "3",
                    imageUrl = "https://example.com/src/picture.jpg",
                    thumbnailUrl = "https://example.com/thumb/pictures.jpg"
                )
            )
        )

        assertEquals(3, items.size)
        assertEquals("動画", items[0].badge?.label)
        assertEquals("GIF", items[1].badge?.label)
        assertNull(items[2].badge)
    }

    @Test
    fun buildThreadAttachmentGalleryItems_skips_posts_without_media() {
        val items = buildThreadAttachmentGalleryItems(
            listOf(
                post(id = "1", imageUrl = null, thumbnailUrl = null)
            )
        )

        assertEquals(emptyList(), items)
    }

    @Test
    fun buildThreadAttachmentGalleryItems_uses_file_name_from_target_url() {
        val item = buildThreadAttachmentGalleryItems(
            listOf(
                post(
                    id = "1",
                    imageUrl = "https://example.com/src/sample.gif?foo=1",
                    thumbnailUrl = null
                )
            )
        ).singleOrNull()

        assertNotNull(item)
        assertEquals("sample.gif", item.fileName)
        assertEquals("https://example.com/src/sample.gif?foo=1", item.targetUrl)
    }

    private fun post(
        id: String,
        imageUrl: String?,
        thumbnailUrl: String?
    ): Post {
        return Post(
            id = id,
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
