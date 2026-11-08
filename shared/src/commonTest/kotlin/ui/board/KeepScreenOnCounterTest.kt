package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

class KeepScreenOnCounterTest {
    @Test
    fun overlappingHoldersKeepTheScreenOnUntilTheLastLetsGoInAnyOrder() {
        val calls = mutableListOf<Boolean>()
        val counter = KeepScreenOnCounter { calls += it }
        counter.acquire() // the old page's video
        counter.acquire() // the new page's video, before the old one is disposed
        counter.release() // the old page goes away first
        assertEquals(listOf(true), calls, "the second video must keep the screen on")
        counter.release()
        assertEquals(listOf(true, false), calls)
        counter.release() // an extra release does not go below zero
        assertEquals(0, counter.holders)
        counter.acquire()
        assertEquals(listOf(true, false, true), calls)
    }
}
