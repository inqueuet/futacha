package com.valoser.futacha.shared.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

@OptIn(ExperimentalTime::class)
class SavedThreadPurgeCutoffClockTest {
    private val purgedAt = 1_000_000L

    @Test
    fun cutoffIsUnchangedWhenTheClockRanNormally() {
        val source = TestTimeSource()
        val mark = source.markNow()
        source += 10.seconds
        assertEquals(purgedAt, effectivePurgeCutoffMillis(purgedAt, mark, purgedAt + 10_000L))
    }

    @Test
    fun cutoffFollowsAClockThatWasSetBackSoNewSavesAreNotDiscarded() {
        val source = TestTimeSource()
        val mark = source.markNow()
        source += 10.seconds
        // The purge was 10 s ago, then the clock was set back an hour.
        val now = purgedAt + 10_000L - 3_600_000L
        val effective = effectivePurgeCutoffMillis(purgedAt, mark, now)
        assertEquals(now - 10_000L, effective)
        // A save that starts now is stamped `now` and is later than the purge.
        assertEquals(true, now > effective)
    }

    @Test
    fun forwardClockStepsAndJitterKeepTheRecordedCutoff() {
        val source = TestTimeSource()
        val mark = source.markNow()
        source += 10.seconds
        assertEquals(purgedAt, effectivePurgeCutoffMillis(purgedAt, mark, purgedAt + 10_000L + 3_600_000L))
        assertEquals(purgedAt, effectivePurgeCutoffMillis(purgedAt, mark, purgedAt + 10_000L - 1_500L))
    }

    @Test
    fun cutoffsWithoutAMarkAreUsedAsRecorded() {
        assertEquals(purgedAt, effectivePurgeCutoffMillis(purgedAt, null, 5L))
        assertEquals(Long.MIN_VALUE, effectivePurgeCutoffMillis(Long.MIN_VALUE, TestTimeSource().markNow(), 5L))
    }
}
