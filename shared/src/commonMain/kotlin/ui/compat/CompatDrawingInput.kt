package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

/** Drawing starts on contact, without the touch slop used for scrolling and dragging. */
internal suspend fun PointerInputScope.detectCompatDrawingStrokes(
    onStart: (Offset) -> Unit,
    onPoint: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        var finished = false
        try {
            onStart(down.position)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                    ?: break
                if (change.isConsumed) break
                // Batched samples matter for small, fast curves between display frames.
                for (sample in change.historical) onPoint(sample.position)
                onPoint(change.position)
                change.consume()
                if (!change.pressed) {
                    onEnd()
                    finished = true
                    break
                }
            }
        } finally {
            // Also discard the unfinished stroke when the canvas leaves composition.
            if (!finished) onCancel()
        }
        // awaitEachGesture waits for all fingers to lift. Never join another finger
        // onto this stroke or use its release position as the pen's endpoint.
    }
}
