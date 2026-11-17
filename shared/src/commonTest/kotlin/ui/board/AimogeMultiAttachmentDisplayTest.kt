package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.PostAttachment
import com.valoser.futacha.shared.ui.image.postExtraAttachmentPreviewUrl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AimogeMultiAttachmentDisplayTest {
    private fun post(id: String, attachments: List<PostAttachment> = emptyList()) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = "",
        imageUrl = attachments.firstOrNull()?.url ?: "https://may.2chan.net/b/src/$id.jpg",
        thumbnailUrl = attachments.firstOrNull()?.thumbnailUrl ?: "https://may.2chan.net/b/thumb/${id}s.jpg",
        attachments = attachments
    )

    private val three = listOf(
        PostAttachment(url = "https://cdn.example/a1.png", thumbnailUrl = "https://cdn.example/a1s.jpg"),
        PostAttachment(url = "https://cdn.example/a2.webp", thumbnailUrl = "https://cdn.example/a2s.jpg"),
        PostAttachment(url = "https://cdn.example/a3.jpg", thumbnailUrl = null)
    )

    @Test
    fun theFutachaViewerHasAPageForEveryFileOfAPost() = runTest {
        // Before: one page per post, so the 2nd and 3rd files could not be opened from the post.
        val collection = buildMediaPreviewCollection(listOf(post("1"), post("2", three), post("3")))

        assertEquals(
            listOf(
                "https://may.2chan.net/b/src/1.jpg",
                "https://cdn.example/a1.png",
                "https://cdn.example/a2.webp",
                "https://cdn.example/a3.jpg",
                "https://may.2chan.net/b/src/3.jpg"
            ),
            collection.entries.map { it.url }
        )
        assertEquals(listOf("1", "2", "2", "2", "3"), collection.entries.map { it.postId })
    }

    @Test
    fun anExtraFileShowsItsThumbnailOrItselfWhenItIsAStillImage() {
        assertEquals("https://cdn.example/a2s.jpg", postExtraAttachmentPreviewUrl(three[1]))
        assertEquals("https://cdn.example/a3.jpg", postExtraAttachmentPreviewUrl(three[2]))
        assertNull(postExtraAttachmentPreviewUrl(PostAttachment(url = "https://cdn.example/v.mp4", mimeType = "video/mp4")))
    }
}
