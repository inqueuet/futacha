package com.valoser.futacha.shared.background

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundRefreshSubmitBookkeepingTest {
    private val all = BackgroundRefreshTaskKind.entries.toSet()

    @Test
    fun configureBeforeTheQueuedSubmitRunsStillSubmits() {
        val bookkeeping = BackgroundRefreshSubmitBookkeeping()
        val first = bookkeeping.kindsToSubmit(all, generation = 1L)
        bookkeeping.markQueued(first, generation = 1L)

        // A second configure(true) bumps the generation before the block runs;
        // the old block is then dropped as stale (H4-5).
        val second = bookkeeping.kindsToSubmit(all, generation = 2L)
        assertEquals(BackgroundRefreshTaskKind.entries, second)
        bookkeeping.markQueued(second, generation = 2L)
        bookkeeping.finishQueued(first, generation = 1L)

        // The newer generation's queued submit still counts.
        assertEquals(emptyList(), bookkeeping.kindsToSubmit(all, generation = 2L))
        bookkeeping.finishQueued(second, generation = 2L)
        second.forEach(bookkeeping::markSubmitted)
        assertEquals(emptyList(), bookkeeping.kindsToSubmit(all, generation = 2L))
    }

    @Test
    fun queuedKindIsNotSubmittedTwiceInOneGeneration() {
        val bookkeeping = BackgroundRefreshSubmitBookkeeping()
        bookkeeping.markQueued(bookkeeping.kindsToSubmit(all, 1L), 1L)

        assertEquals(emptyList(), bookkeeping.kindsToSubmit(all, 1L))
    }

    @Test
    fun failedSubmitAndStartedTaskAreSubmittedAgain() {
        val bookkeeping = BackgroundRefreshSubmitBookkeeping()
        val kinds = bookkeeping.kindsToSubmit(all, 1L)
        bookkeeping.markQueued(kinds, 1L)
        bookkeeping.finishQueued(kinds, 1L)
        // Only APP_REFRESH was accepted.
        bookkeeping.markSubmitted(BackgroundRefreshTaskKind.APP_REFRESH)
        assertEquals(listOf(BackgroundRefreshTaskKind.PROCESSING), bookkeeping.kindsToSubmit(all, 1L))

        bookkeeping.markSubmitted(BackgroundRefreshTaskKind.PROCESSING)
        bookkeeping.markStarted(BackgroundRefreshTaskKind.APP_REFRESH)
        assertEquals(listOf(BackgroundRefreshTaskKind.APP_REFRESH), bookkeeping.kindsToSubmit(all, 1L))

        bookkeeping.clear()
        assertEquals(BackgroundRefreshTaskKind.entries, bookkeeping.kindsToSubmit(all, 1L))
    }
}
