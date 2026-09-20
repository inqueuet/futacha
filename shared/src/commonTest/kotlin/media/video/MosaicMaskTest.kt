package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import kotlin.test.*

class MosaicMaskTest {
    @Test fun freehandStrokesAreContinuousImmutableAndCanHaveHoles() {
        val drawn=MosaicMask.EMPTY.stroke(.2f,.5f,.8f,.5f,.1f,.1f,false)
        assertTrue(MosaicMask.EMPTY.isEmpty)
        assertTrue((3..7).all { drawn.contains(it/10f,.5f) })
        assertFalse(drawn.contains(.5f,.1f))
        val erased=drawn.stroke(.5f,.5f,.5f,.5f,.04f,.04f,true)
        assertFalse(erased.contains(.5f,.5f));assertTrue(drawn.contains(.5f,.5f));assertTrue(erased.contains(.3f,.5f))
        assertEquals(2048L,drawn.byteSize)
    }
    @Test fun dilationOnlyAddsCoverageAndRemainsWithinLocalBounds() {
        val original=MosaicMask.from { x,y -> x in 40..70 && y in 50..80 }
        val expanded=original.dilated(5)
        for(y in 0 until 128)for(x in 0 until 128)if(original.contains(x,y))assertTrue(expanded.contains(x,y))
        assertTrue(expanded.contains(36,60));assertFalse(expanded.contains(20,60))
        assertSame(expanded,original.dilated(5));assertSame(original,original.dilated(0))
        assertSame(MosaicMask.EMPTY,MosaicMask.EMPTY.dilated(5))
        assertFalse(expanded.contains(-.01f,.5f))
    }
    @Test fun drawingAcrossRectangleEdgesDoesNotWriteOutsideMask() {
        val line=MosaicMask.EMPTY.stroke(-1f,.5f,2f,.5f,.04f,.08f,false)
        assertTrue(line.contains(0f,.5f));assertTrue(line.contains(1f,.5f));assertFalse(line.contains(.5f,.1f))
        assertFailsWith<IllegalArgumentException> { line.stroke(Float.NaN,0f,1f,1f,.1f,.1f,false) }
    }
    @Test fun videoContoursUseSourceTimestampsAndCanRestoreRectangleOnFailure() {
        val a=MosaicMask.EMPTY.stroke(.2f,.5f,.4f,.5f,.1f,.1f,false)
        val b=MosaicMask.EMPTY.stroke(.6f,.5f,.8f,.5f,.1f,.1f,false)
        val region=MosaicRegion("a",endUs=1_000_000,masks=listOf(MosaicMaskKeyframe(33_333,a),MosaicMaskKeyframe(200_000,b),MosaicMaskKeyframe(400_000,null)))
        assertNull(region.maskAt(0));assertSame(a,region.maskAt(33_333));assertSame(a,region.maskAt(199_999))
        assertSame(b,region.maskAt(200_000));assertNull(region.maskAt(400_000));assertNull(region.maskAt(999_999))
        val edited=region.withMask(200_000,a)
        assertEquals(3,edited.masks.size);assertSame(a,edited.maskAt(200_000));assertSame(b,region.maskAt(200_000))
        val history=MosaicHistory(MosaicDocument(listOf(region)))
        history.commit(MosaicDocument(listOf(edited)));assertEquals(region,history.undo().regions.single())
    }
    @Test fun brushIsCircularInImagePixelsEvenInsideNonSquareBounds() {
        val bounds=MosaicBounds(.5f,.5f,.6f,.3f)
        val mask=MosaicMask.EMPTY.paintWithin(bounds,1000,1000,.5f,.5f,.5f,.5f,.1f,false)
        assertTrue(mask.contains(.54f,.5f));assertFalse(mask.contains(.6f,.5f))
        assertTrue(mask.contains(.5f,.59f));assertFalse(mask.contains(.5f,.65f))
    }
    @Test fun emptyMasksOutsideTheActivePeriodDoNotPreventExport() {
        val region=MosaicRegion("a",startUs=100,endUs=300,masks=listOf(MosaicMaskKeyframe(0,MosaicMask.EMPTY),MosaicMaskKeyframe(100,MosaicMask.FULL),MosaicMaskKeyframe(300,MosaicMask.EMPTY)))
        assertFalse(region.hasEmptyActiveMask())
        assertTrue(region.copy(startUs=99).hasEmptyActiveMask())
        assertTrue(region.copy(endUs=301).hasEmptyActiveMask())
    }
}
