package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadMediaPreviewRelocationTest {
    private fun entry(url: String, type: MediaType = MediaType.Image) =
        MediaPreviewEntry(url = url, mediaType = type, postId = url, title = url)

    private fun collection(vararg entries: MediaPreviewEntry) =
        MediaPreviewCollection(entries.toList(), buildMediaPreviewIndexByKey(entries.toList()))

    @Test
    fun refreshedPostList_keepsTheViewerOnTheSameMedia() {
        val previous = listOf(entry("a.jpg"), entry("b.jpg"))
        // The refresh inserts new media before the shown one and appends more.
        val next = collection(entry("new.jpg"), entry("a.jpg"), entry("b.jpg"), entry("c.webm", MediaType.Video))

        val relocated = relocateThreadMediaPreviewState(ThreadMediaPreviewState(previewMediaIndex = 1), previous, next)

        assertEquals(2, relocated.previewMediaIndex)
    }

    @Test
    fun refreshedPostList_closesTheViewerOnlyWhenTheMediaDisappeared() {
        val previous = listOf(entry("a.jpg"), entry("b.jpg"))

        val removed = relocateThreadMediaPreviewState(
            ThreadMediaPreviewState(previewMediaIndex = 1), previous, collection(entry("a.jpg"))
        )
        val sameUrlOtherType = relocateThreadMediaPreviewState(
            ThreadMediaPreviewState(previewMediaIndex = 0), previous, collection(entry("a.jpg", MediaType.Video))
        )

        assertNull(removed.previewMediaIndex)
        assertNull(sameUrlOtherType.previewMediaIndex)
    }
}
