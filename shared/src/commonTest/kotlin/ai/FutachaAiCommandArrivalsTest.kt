package com.valoser.futacha.shared.ai

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.TimeSource

class FutachaAiCommandArrivalsTest {
    @BeforeTest
    fun setUp() {
        FutachaAiCommandArrivals.clearForTest()
    }

    @AfterTest
    fun tearDown() {
        FutachaAiCommandArrivals.clearForTest()
    }

    @Test
    fun keepsFirstArrivalPerCommandInstance() {
        val first = FutachaAiCommand(FutachaAiAction.RefreshHistory, source = "ios")
        val equalButDistinct = FutachaAiCommand(FutachaAiAction.RefreshHistory, source = "ios")
        val early = TimeSource.Monotonic.markNow()
        FutachaAiCommandArrivals.record(first, early)
        FutachaAiCommandArrivals.record(first, TimeSource.Monotonic.markNow())

        assertEquals(early, FutachaAiCommandArrivals.arrivalOf(first))
        assertNull(FutachaAiCommandArrivals.arrivalOf(equalButDistinct))

        FutachaAiCommandArrivals.forget(first)
        assertNull(FutachaAiCommandArrivals.arrivalOf(first))
    }

    @Test
    fun dropsOldestBeyondBound() {
        val commands = List(65) { FutachaAiCommand(FutachaAiAction.RefreshHistory, source = "ios-$it") }
        commands.forEach { FutachaAiCommandArrivals.record(it) }

        assertNull(FutachaAiCommandArrivals.arrivalOf(commands.first()))
        assertEquals(true, FutachaAiCommandArrivals.arrivalOf(commands.last()) != null)
    }
}
