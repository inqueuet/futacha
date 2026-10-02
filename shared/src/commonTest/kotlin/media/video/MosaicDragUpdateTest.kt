package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import kotlin.test.*

/** B-10: drag steps arriving before a recomposition must each apply to the latest document. */
class MosaicDragUpdateTest {
    private val start = MosaicDocument(listOf(MosaicRegion("1", endUs = 1_000, keyframes = listOf(MosaicKeyframe(0, MosaicBounds(.5f, .5f, .8f, .8f))))))
    private fun MosaicDocument.mask() = regions.single().maskAt(0)!!
    private fun MosaicDocument.paint(fromX: Float, toX: Float) =
        paintRegionMask("1", 0, 100, 100, fromX, .5f, toX, .5f, .02f, erase = false)

    @Test fun consecutiveBrushStepsKeepEverySegment() {
        var latest = start
        // Two pointer events before the next recomposition, applied as owner-side updates.
        for (step in listOf<(MosaicDocument) -> MosaicDocument>({ it.paint(.2f, .4f) }, { it.paint(.4f, .6f) })) latest = step(latest)
        val mask = latest.mask()
        // Normalized to the region box (left edge .1, width .8): .3 -> .25, .55 -> .5625.
        assertTrue(mask.contains(.25f, .5f), "the first segment must remain painted")
        assertTrue(mask.contains(.5625f, .5f))
        // The bug: the second step built from the stale document dropped the first segment.
        assertFalse(start.paint(.4f, .6f).mask().contains(.25f, .5f))
    }

    @Test fun consecutiveMoveStepsAccumulate() {
        val moved = start.moveRegionBounds("1", 0, .03f, 0f).moveRegionBounds("1", 0, .04f, .02f)
        val bounds = moved.regions.single().boundsAt(0)
        assertEquals(.57f, bounds.centerX, 1e-5f)
        assertEquals(.52f, bounds.centerY, 1e-5f)
    }

    @Test fun inactiveOrMissingRegionsAreUnchanged() {
        assertSame(start, start.paintRegionMask("1", 2_000, 100, 100, .2f, .5f, .4f, .5f, .02f, false))
        assertSame(start, start.paintRegionMask("2", 0, 100, 100, .2f, .5f, .4f, .5f, .02f, false))
        assertSame(start, start.moveRegionBounds("1", 2_000, .1f, .1f))
        // Erasing an unmasked region starts from the full box, like the editor's erase tool.
        assertFalse(start.paintRegionMask("1", 0, 100, 100, .5f, .5f, .5f, .5f, .05f, true).mask().contains(.5f, .5f))
    }
    /** B4-1: box drags and slider copies must keep the empty-contour flags instead of re-testing every mask. */
    @Test fun regionEditsKeepEmptyMaskFlags() {
        val masked = start.update("1") { it.copy(masks = listOf(MosaicMaskKeyframe(0, MosaicMask.EMPTY), MosaicMaskKeyframe(500, MosaicMask.FULL))) }
        assertTrue(masked.regions.single().hasEmptyActiveMask())
        assertTrue(masked.regions.single().hasCachedEmptyMasks)
        val edited = masked.moveRegionBounds("1", 0, .01f, 0f)
            .update("1") { it.copy(maskMargin = 3) }
            .update("1") { it.copy(blockFraction = .05f, shape = MosaicShape.ELLIPSE) }
            .update("1") { it.withBounds(250, MosaicBounds(.4f, .4f, .2f, .2f)).withoutKeyframe(250) }
        val region = edited.regions.single()
        assertTrue(region.hasCachedEmptyMasks, "copy-based edits dropped the flags")
        assertTrue(region.hasEmptyActiveMask())
        // The empty contour is only active on [0, 500); starting after it clears the flag result.
        assertFalse(edited.update("1") { it.copy(startUs = 500) }.regions.single().hasEmptyActiveMask())
        // A replaced mask list must be re-tested, not inherit stale flags.
        val replaced = edited.update("1") { it.copy(masks = listOf(MosaicMaskKeyframe(0, MosaicMask.FULL))) }.regions.single()
        assertFalse(replaced.hasCachedEmptyMasks)
        assertFalse(replaced.hasEmptyActiveMask())
    }
}
