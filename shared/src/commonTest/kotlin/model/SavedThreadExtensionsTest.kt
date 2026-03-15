package com.valoser.futacha.shared.model

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedThreadExtensionsTest {
    @Test
    fun toThreadPage_resolvesPathSaveLocationMediaPaths() = runBlocking {
        val metadata = savedMetadata(
            storageId = "b_123",
            imagePath = "b/src/123.jpg",
            thumbnailPath = "b/thumb/123s.jpg"
        )

        val page = metadata.toThreadPage(
            fileSystem = InMemoryFileSystem(),
            baseSaveLocation = SaveLocation.Path("/downloads/futacha")
        )

        assertEquals("/virtual/downloads/futacha/b_123/b/src/123.jpg", page.posts[0].imageUrl)
        assertEquals("/virtual/downloads/futacha/b_123/b/thumb/123s.jpg", page.posts[0].thumbnailUrl)
    }

    @Test
    fun toThreadPage_resolvesTreeUriMediaPaths() = runBlocking {
        val metadata = savedMetadata(
            storageId = "b_123",
            imagePath = "b/src/123.webm",
            thumbnailPath = "b/thumb/123s.jpg",
            originalImageUrl = "https://may.2chan.net/b/src/123.webm"
        )

        val page = metadata.toThreadPage(
            fileSystem = InMemoryFileSystem(),
            baseSaveLocation = SaveLocation.TreeUri("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        )

        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments%2Fb_123%2Fb%2Fsrc%2F123.webm",
            page.posts[0].imageUrl
        )
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments%2Fb_123%2Fb%2Fthumb%2F123s.jpg",
            page.posts[0].thumbnailUrl
        )
    }

    @Test
    fun toThreadPage_keepsAbsoluteAndContentUrisUntouched() = runBlocking {
        val metadata = savedMetadata(
            storageId = "b_123",
            imagePath = "content://media/external/images/1",
            thumbnailPath = "/already/absolute/thumb.jpg"
        )

        val page = metadata.toThreadPage(
            fileSystem = InMemoryFileSystem(),
            baseDirectory = "autosave"
        )

        assertEquals("content://media/external/images/1", page.posts[0].imageUrl)
        assertEquals("/already/absolute/thumb.jpg", page.posts[0].thumbnailUrl)
    }

    @Test
    fun toThreadPage_fallsBackToOriginalUrls_whenLocalPathsAreMissing() = runBlocking {
        val metadata = savedMetadata(
            storageId = null,
            imagePath = null,
            thumbnailPath = null,
            originalImageUrl = "https://may.2chan.net/b/src/123.jpg",
            originalThumbnailUrl = "https://may.2chan.net/b/thumb/123s.jpg"
        )

        val page = metadata.toThreadPage(
            fileSystem = InMemoryFileSystem(),
            baseDirectory = "autosave"
        )

        assertEquals("https://may.2chan.net/b/src/123.jpg", page.posts[0].imageUrl)
        assertEquals("https://may.2chan.net/b/thumb/123s.jpg", page.posts[0].thumbnailUrl)
    }

    private fun savedMetadata(
        storageId: String?,
        imagePath: String?,
        thumbnailPath: String?,
        originalImageUrl: String? = null,
        originalThumbnailUrl: String? = null
    ): SavedThreadMetadata {
        return SavedThreadMetadata(
            threadId = "123",
            boardId = "b",
            boardName = "may/b",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            title = "title",
            storageId = storageId,
            savedAt = 100L,
            expiresAtLabel = "soon",
            posts = listOf(
                SavedPost(
                    id = "1",
                    order = 1,
                    author = "author",
                    subject = "subject",
                    timestamp = "24/01/01(月)00:00:00",
                    messageHtml = "body",
                    originalImageUrl = originalImageUrl,
                    localImagePath = imagePath,
                    originalVideoUrl = null,
                    localVideoPath = null,
                    originalThumbnailUrl = originalThumbnailUrl,
                    localThumbnailPath = thumbnailPath
                )
            ),
            totalSize = 10L
        )
    }
}
