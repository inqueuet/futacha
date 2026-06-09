package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun resolveSwipeNavigationAction_requires_horizontal_dominance_and_threshold() {
        assertEquals(
            SwipeNavigationAction.Next,
            resolveSwipeNavigationAction(
                totalDx = -96f,
                totalDy = 18f,
                thresholdPx = 56f
            )
        )
        assertEquals(
            SwipeNavigationAction.Previous,
            resolveSwipeNavigationAction(
                totalDx = 80f,
                totalDy = 12f,
                thresholdPx = 56f
            )
        )
        assertEquals(
            SwipeNavigationAction.None,
            resolveSwipeNavigationAction(
                totalDx = -40f,
                totalDy = 5f,
                thresholdPx = 56f
            )
        )
        assertEquals(
            SwipeNavigationAction.None,
            resolveSwipeNavigationAction(
                totalDx = -72f,
                totalDy = 90f,
                thresholdPx = 56f
            )
        )
    }

    @Test
    fun swipe_navigation_start_bounds_exclude_padded_controls_area() {
        val containerSize = IntSize(width = 400, height = 800)

        assertTrue(
            isSwipeNavigationStartWithinBounds(
                position = Offset(200f, 500f),
                containerSize = containerSize,
                startPaddingPx = 0f,
                topPaddingPx = 0f,
                endPaddingPx = 0f,
                bottomPaddingPx = 220f
            )
        )
        assertFalse(
            isSwipeNavigationStartWithinBounds(
                position = Offset(200f, 700f),
                containerSize = containerSize,
                startPaddingPx = 0f,
                topPaddingPx = 0f,
                endPaddingPx = 0f,
                bottomPaddingPx = 220f
            )
        )
    }
}
