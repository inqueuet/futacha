package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadInlineApuViewerTest {
    private val post = Post(
        id = "100",
        author = null, subject = null, timestamp = "", posterId = null,
        messageHtml = "fu1000001.jpg<br>fu1000002.png<br>&gt;fu1000009.jpg<br>fu1000003.mp4",
        imageUrl = "https://dec.2chan.net/up2/src/fu1000001.jpg",
        thumbnailUrl = null
    )

    @Test
    fun everyUploaderFileInTheBodyGetsAViewerPageAndTheQuotedOneDoesNot() = runBlocking {
        val collection = buildMediaPreviewCollection(listOf(post))
        val names = collection.entries.map { it.url.substringAfterLast('/') }
        assertEquals(listOf("fu1000001.jpg", "fu1000002.png", "fu1000003.mp4"), names)
        val second = collection.entries[1]
        val state = openThreadMediaPreview(ThreadMediaPreviewState(), collection.entries, collection.indexByKey, second.url, second.mediaType)
        assertEquals(1, state.previewMediaIndex)
        assertEquals(MediaType.Video, collection.entries[2].mediaType)
    }

    @Test
    fun theGalleryListsThemTooAndAPostWithOneFileStaysOneItem() {
        assertEquals(3, buildThreadAttachmentGalleryItems(listOf(post)).size)
        val single = post.copy(messageHtml = "fu1000001.jpg")
        assertEquals(1, buildThreadAttachmentGalleryItems(listOf(single)).size)
        assertTrue(threadInlineApuCopies(single).isEmpty())
    }
}
