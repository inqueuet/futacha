package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatReplyPopupPositionTest {
    @Test
    fun popupClampsToToolbarButUsesTappedResponseAsTheBottomAnchor() {
        val provider = CompatReplyPopupPositionProvider(anchorY = 520, minimumTopY = 147)
        val position = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 0, 0),
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(1080, 300)
        )
        assertEquals(220, position.y)
        assertEquals(0, position.x)
    }

    @Test
    fun popupNeverMovesAboveTheMinimumTopWhenResponseIsNearToolbar() {
        val provider = CompatReplyPopupPositionProvider(anchorY = 180, minimumTopY = 147)
        val position = provider.calculatePosition(
            anchorBounds = IntRect(0, 0, 0, 0),
            windowSize = IntSize(1080, 2400),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(1080, 300)
        )
        assertEquals(147, position.y)
    }
}
