package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.ftutter.ftutterStableVisibleCount
import kotlin.test.Test
import kotlin.test.assertEquals

class ScrollSizeMemoryTest {
    @Test
    fun theAverageDoesNotChangeWhenScrollingBackOverMeasuredItems() {
        val memory = ScrollSizeMemory()
        memory.record(10, listOf(0 to 100, 1 to 300))
        memory.record(10, listOf(2 to 200))
        val settled = memory.averageSize()
        assertEquals(200f, settled)
        // Back at the top: the screen only shows the short post, the average stays.
        memory.record(10, listOf(0 to 100))
        assertEquals(settled, memory.averageSize())
    }

    @Test
    fun aShorterListForgetsTheOldSizes() {
        val memory = ScrollSizeMemory()
        memory.record(10, listOf(0 to 500))
        memory.record(3, listOf(0 to 100))
        assertEquals(100f, memory.averageSize())
    }

    @Test
    fun theFtutterCountComesFromTheAverage() {
        assertEquals(5, ftutterStableVisibleCount(1000, 200f, 2))
        assertEquals(2, ftutterStableVisibleCount(1000, 0f, 2))
    }
}
