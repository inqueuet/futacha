package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.compat.compatFastScrollbarTarget
import com.valoser.futacha.shared.ui.compat.compatFastScrollbarDragTarget
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatFastScrollbarTest {
    @Test
    fun tapAndDragMapTheTrackToTheList() {
        assertEquals(0, compatFastScrollbarTarget(100, 1_000, 0f))
        assertEquals(50, compatFastScrollbarTarget(100, 1_000, 500f))
        assertEquals(99, compatFastScrollbarTarget(100, 1_000, 1_500f))
    }

    @Test
    fun thumbDragIsRelativeAndDoesNotJumpOnPointerDown() {
        assertEquals(
            400,
            compatFastScrollbarDragTarget(400, 1_000, 100, 1_000, dragDeltaY = 0f)
        )
        assertEquals(
            500,
            compatFastScrollbarDragTarget(400, 1_000, 100, 1_000, dragDeltaY = 100f)
        )
        assertEquals(
            300,
            compatFastScrollbarDragTarget(400, 1_000, 100, 1_000, dragDeltaY = -100f)
        )
    }
}
