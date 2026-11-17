package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.*
import kotlin.test.*

class MosaicReviewIndexTest {
    private val frames = VideoFrameIndex(longArrayOf(0, 33_333, 66_667, 100_000, 200_000, 400_000, 700_000, 900_000), 1_000_000)
    private val document = MosaicDocument(listOf(
        MosaicRegion("manual", startUs=0, endUs=1_000_000),
        MosaicRegion("a", startUs=33_333, endUs=100_000, label="男性器候補"),
        MosaicRegion("b", startUs=66_667, endUs=200_000, label="女性器候補"),
        MosaicRegion("ear", startUs=700_000, endUs=900_000, label="女性器候補")
    ))

    @Test fun detectedFilterMergesOverlapsWithoutChangingMasksOrDocument() {
        val index=MosaicReviewIndex.create(document,MosaicReviewFilter.DETECTED,null,frames)
        assertEquals(listOf(MosaicReviewInterval(33_333,200_000),MosaicReviewInterval(700_000,900_000)),index.intervals)
        assertEquals(listOf("a","b","ear"),index.regions.map { it.id })
        assertEquals(4,document.regions.size)
        assertEquals(listOf(MosaicReviewInterval(0,1_000_000)),MosaicReviewIndex.create(document,MosaicReviewFilter.ALL,null,frames).intervals)
    }

    @Test fun selectedAndDeletedRegionsProduceOnlyTheirOwnReviewIntervals() {
        assertEquals(listOf(MosaicReviewInterval(700_000,900_000)),MosaicReviewIndex.create(document,MosaicReviewFilter.SELECTED,"ear",frames).intervals)
        val withoutEar=document.copy(regions=document.regions.filterNot { it.id=="ear" })
        assertTrue(MosaicReviewIndex.create(withoutEar,MosaicReviewFilter.SELECTED,"ear",frames).intervals.isEmpty())
        assertEquals(listOf(MosaicReviewInterval(33_333,200_000)),MosaicReviewIndex.create(withoutEar,MosaicReviewFilter.DETECTED,null,frames).intervals)
        assertEquals(document.regions.take(3),withoutEar.regions)
    }

    @Test fun navigationSkipsGapsHonoursExclusiveEndsAndWrapsOnlyBetweenIntervals() {
        val index=MosaicReviewIndex.create(document,MosaicReviewFilter.DETECTED,null,frames)
        assertEquals(33_333L,index.atOrAfter(0))
        assertEquals(66_667L,index.atOrAfter(66_667))
        assertEquals(700_000L,index.atOrAfter(200_000))
        assertNull(index.atOrAfter(900_000))
        assertEquals(100_000L,index.atOrBefore(400_000,frames))
        assertNull(index.atOrBefore(0,frames))
        assertEquals(700_000L,index.nextStart(33_333))
        assertEquals(33_333L,index.nextStart(900_000))
        assertEquals(33_333L,index.previousStart(100_000))
        assertEquals(700_000L,index.previousStart(33_333))
    }

    @Test fun nonFrameAlignedRangeNeverIncludesThePrecedingFrame() {
        val doc=MosaicDocument(listOf(MosaicRegion("a",startUs=40_000,endUs=80_000)))
        val index=MosaicReviewIndex.create(doc,MosaicReviewFilter.ALL,null,frames)
        assertEquals(listOf(MosaicReviewInterval(66_667,80_000)),index.intervals)
        assertEquals(66_667L,index.atOrBefore(100_000,frames))
        val empty=doc.update("a") { it.copy(startUs=70_000) }
        assertTrue(MosaicReviewIndex.create(empty,MosaicReviewFilter.ALL,null,frames).intervals.isEmpty())
    }

    @Test fun regroupingOlderInterleavedHintsPreservesGaps() {
        val groups=MosaicIssueGroup.from(listOf(
            MosaicAnalysisIssue(0,100,"候補なし"),MosaicAnalysisIssue(0,100,"弱い候補"),
            MosaicAnalysisIssue(100,200,"候補なし"),MosaicAnalysisIssue(100,200,"弱い候補"),
            MosaicAnalysisIssue(400,500,"候補なし")
        ))
        assertEquals(2,groups.size)
        assertEquals(listOf(MosaicReviewInterval(0,200)),groups.first().intervals)
        assertEquals(2,groups.last().intervals.size)
    }
}
