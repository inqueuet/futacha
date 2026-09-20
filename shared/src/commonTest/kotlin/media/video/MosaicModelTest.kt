package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import kotlin.test.*

class MosaicModelTest {
    @Test fun movingRegionInterpolatesOnlyWithinItsTimeRange() {
        val left=MosaicBounds(0.2f,0.3f,0.2f,0.2f)
        val right=MosaicBounds(0.8f,0.7f,0.3f,0.4f)
        val region=MosaicRegion("a",startUs=100,endUs=1000,keyframes=listOf(MosaicKeyframe(100,left),MosaicKeyframe(900,right)))
        assertFalse(region.activeAt(99)); assertTrue(region.activeAt(100)); assertTrue(region.activeAt(999)); assertFalse(region.activeAt(1000))
        assertEquals(0.5f,region.boundsAt(500).centerX,0.00001f)
        assertEquals(0.3f,region.boundsAt(500).height,0.00001f)
        assertEquals(left,region.boundsAt(0)); assertEquals(right,region.boundsAt(10000))
        assertEquals(left,region.copy(interpolate=false).boundsAt(899)); assertEquals(right,region.copy(interpolate=false).boundsAt(900))
    }
    @Test fun movingAndResizingCannotLeaveVideoBounds() {
        val bounds=MosaicBounds(-10f,8f,4f,-0.5f).constrained()
        assertEquals(1f,bounds.width,0f); assertEquals(0.5f,bounds.centerX,0f)
        assertTrue(bounds.centerY+bounds.height/2<=1)
        val ellipse=MosaicBounds(0.5f,0.5f,0.5f,0.5f)
        assertTrue(ellipse.contains(0.7f,0.7f,MosaicShape.RECTANGLE)); assertFalse(ellipse.contains(0.7f,0.7f,MosaicShape.ELLIPSE))
    }
    @Test fun undoRestoresWholeGestureAndNewActionClearsRedo() {
        val history=MosaicHistory()
        val first=MosaicDocument(listOf(MosaicRegion("a",endUs=1000)))
        history.commit(first)
        val moved=first.update("a") { it.withBounds(500,MosaicBounds(0.3f,0.3f,0.2f,0.2f)) }
        history.commit(moved)
        assertEquals(first,history.undo()); assertEquals(moved,history.redo())
        history.undo(); history.commit(first.update("a") { it.copy(shape=MosaicShape.ELLIPSE) }); assertFalse(history.canRedo)
    }
    @Test fun variableFrameRateSteppingUsesActualPresentationTimes() {
        val frames=VideoFrameIndex(longArrayOf(0,33333,66667,166667,233333),300000)
        assertEquals(66667L,frames.step(33333,true)); assertEquals(166667L,frames.step(66667,true))
        assertEquals(66667L,frames.step(166667,false)); assertEquals(233333L,frames.step(299999,true))
        assertEquals(300000L,frames.endAfter(233333)); assertEquals(0L,frames.step(0,false))
    }
    @Test fun deletingLastKeyframeKeepsRegionUsable() {
        val region=MosaicRegion("a",endUs=1000)
        assertEquals(region,region.withoutKeyframe(0))
        assertEquals(1,region.withBounds(500,MosaicBounds.DEFAULT).withoutKeyframe(0).keyframes.size)
    }
}
