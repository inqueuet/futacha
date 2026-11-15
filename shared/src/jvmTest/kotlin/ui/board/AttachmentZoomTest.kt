package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentZoomTest {
    private val box = IntSize(400, 800)

    @Test
    fun aDoubleTapZoomsInAndASecondOneGoesBack() {
        val zoomed = AttachmentZoom().doubleTapped(Offset(200f, 400f), box)
        assertEquals(ATTACHMENT_PREVIEW_DOUBLE_TAP_SCALE, zoomed.scale)
        assertEquals(Offset.Zero, zoomed.offset)
        assertEquals(AttachmentZoom(), zoomed.doubleTapped(Offset(10f, 10f), box))
    }

    @Test
    fun theScaleStaysBetweenOneAndTheMaximumAndThePictureCannotBeMovedOff() {
        val big = AttachmentZoom().transformed(Offset(200f, 400f), Offset.Zero, 100f, box)
        assertEquals(ATTACHMENT_PREVIEW_MAX_SCALE, big.scale)
        val dragged = big.transformed(Offset(200f, 400f), Offset(99999f, -99999f), 1f, box)
        assertEquals(Offset(400f * 4f / 2f, -800f * 4f / 2f), dragged.offset)
        assertEquals(AttachmentZoom(), big.transformed(Offset(200f, 400f), Offset.Zero, 0.01f, box))
    }
}

class CompatVideoZoomStepsTest {
    @Test
    fun theZoomButtonStepsOneTwoThreeAndBack() {
        val box = IntSize(300, 600)
        val two = com.valoser.futacha.shared.ui.compat.compatNextVideoZoom(AttachmentZoom(), box)
        assertEquals(2f, two.scale)
        assertEquals("2倍", com.valoser.futacha.shared.ui.compat.compatVideoZoomLabel(two))
        val three = com.valoser.futacha.shared.ui.compat.compatNextVideoZoom(two, box)
        assertEquals(3f, three.scale)
        assertEquals(AttachmentZoom(), com.valoser.futacha.shared.ui.compat.compatNextVideoZoom(three, box))
    }
}
