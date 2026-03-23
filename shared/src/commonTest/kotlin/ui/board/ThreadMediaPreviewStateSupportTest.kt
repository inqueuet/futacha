package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadMediaPreviewStateSupportTest {
    private val entries = listOf(
        MediaPreviewEntry(
            url = "https://example.com/src/100.jpg",
            mediaType = MediaType.Image,
            postId = "100",
            title = "first"
        ),
        MediaPreviewEntry(
            url = "https://example.com/src/101.webm",
            mediaType = MediaType.Video,
            postId = "101",
            title = "second"
        )
    )

    @Test
    fun open_and_navigate_preview_updates_index() {
        val opened = openThreadMediaPreview(
            currentState = emptyThreadMediaPreviewState(),
            entries = entries,
            url = "https://example.com/src/101.webm",
            mediaType = MediaType.Video
        )
        assertEquals(1, opened.previewMediaIndex)

        val next = moveToNextThreadMediaPreview(opened, totalCount = entries.size)
        assertEquals(0, next.previewMediaIndex)

        val previous = moveToPreviousThreadMediaPreview(next, totalCount = entries.size)
        assertEquals(1, previous.previewMediaIndex)
    }

    @Test
    fun normalization_and_dialog_state_follow_bounds() {
        val normalized = normalizeThreadMediaPreviewState(
            currentState = ThreadMediaPreviewState(previewMediaIndex = 5),
            totalCount = entries.size
        )
        assertNull(normalized.previewMediaIndex)

        val dialogState = resolveThreadMediaPreviewDialogState(
            state = ThreadMediaPreviewState(previewMediaIndex = 0),
            entries = entries,
            isSaveInProgress = false
        )
        requireNotNull(dialogState)
        assertEquals("https://example.com/src/100.jpg", dialogState.entry.url)
        assertEquals(0, dialogState.currentIndex)
        assertEquals(2, dialogState.totalCount)
        assertEquals(true, dialogState.isSaveEnabled)
    }
}
