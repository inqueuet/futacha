package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatibilityToolbarDragTest {
    @Test
    fun placementUsesThePointerDownOrderWithoutBoundaryOscillation() {
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 2, visualOffsetPx = 29f),
            compatToolbarDragPlacement(2, 8, 29f, 60f)
        )
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 3, visualOffsetPx = -29f),
            compatToolbarDragPlacement(2, 8, 31f, 60f)
        )
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 4, visualOffsetPx = 29f),
            compatToolbarDragPlacement(2, 8, 149f, 60f)
        )
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 1, visualOffsetPx = 29f),
            compatToolbarDragPlacement(2, 8, -31f, 60f)
        )
    }

    @Test
    fun placementClampsAtBothEndsAndBoundsTheVisualOffset() {
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 4, visualOffsetPx = 60f),
            compatToolbarDragPlacement(2, 5, 10_000f, 60f)
        )
        assertEquals(
            CompatToolbarDragPlacement(targetIndex = 0, visualOffsetPx = -60f),
            compatToolbarDragPlacement(2, 5, -10_000f, 60f)
        )
    }
}
