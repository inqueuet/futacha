package com.valoser.futacha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HistoryRefreshRunGateTest {
    private val gate = BackgroundRunGate<String>()

    private suspend fun run(key: String, release: CompletableDeferred<Unit>? = null, result: String = "ok", runs: MutableList<String>) =
        gate.runExclusive(
            key = key,
            maxWaitMillis = 5_000L,
            isSuccess = { it == "ok" },
            onSkipped = { "skipped" }
        ) {
            runs += key
            release?.await()
            result
        }

    @Test
    fun overlappingRunOfTheSameGenerationIsSkippedAfterTheFirstFinishes() = runBlocking {
        val runs = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val periodic = async { run("futacha:3", release, runs = runs) }
        yield()
        val oneTime = async { run("futacha:3", runs = runs) }
        yield()
        assertEquals(listOf("futacha:3"), runs)

        release.complete(Unit)

        assertEquals("ok", periodic.await())
        assertEquals("skipped", oneTime.await())
        assertEquals(listOf("futacha:3"), runs)
    }

    @Test
    fun waitingRunOfANewGenerationRunsAfterTheOldOne() = runBlocking {
        val runs = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val old = async { run("futacha:3", release, runs = runs) }
        yield()
        val new = async { run("futacha:5", runs = runs) }
        yield()
        release.complete(Unit)

        assertEquals("ok", old.await())
        assertEquals("ok", new.await())
        assertEquals(listOf("futacha:3", "futacha:5"), runs)
    }

    @Test
    fun failedRunDoesNotCoverTheWaitingRun() = runBlocking {
        val runs = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val failed = async { run("futacha:3", release, result = "retry", runs = runs) }
        yield()
        val next = async { run("futacha:3", runs = runs) }
        yield()
        release.complete(Unit)

        assertEquals("retry", failed.await())
        assertEquals("ok", next.await())
        assertEquals(2, runs.size)
    }

    @Test
    fun laterRunAfterAFinishedRunIsNotSkipped() = runBlocking {
        val runs = mutableListOf<String>()
        assertEquals("ok", run("futacha:3", runs = runs))
        assertEquals("ok", run("futacha:3", runs = runs))
        assertEquals(2, runs.size)
    }

    @Test
    fun waitLimitSkipsWithoutLosingTheGate() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()
        val holder = async { run("futacha:3", release, runs = runs) }
        yield()
        val timedOut = gate.runExclusive(
            key = "futacha:5",
            maxWaitMillis = 50L,
            isSuccess = { true },
            onSkipped = { "skipped" }
        ) { "ran" }
        assertEquals("skipped", timedOut)
        release.complete(Unit)
        holder.await()
        assertEquals("ok", run("futacha:5", runs = runs))
    }

    @Test
    fun ledgerIsReadAndWrittenUnderOneLockSoAMatchIsDeliveredOnce() {
        val notified = mutableSetOf<String>()
        val deliveries = AtomicInteger()
        val lock = Any()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        repeat(8) {
            pool.execute {
                start.await()
                deliverNewMatchesOnce(
                    lock = lock,
                    matches = listOf("thread-1"),
                    filterNew = { matches -> matches.filterNot { it in notified } },
                    markDelivered = { notified += it },
                    deliver = {
                        deliveries.incrementAndGet()
                        Thread.sleep(20)
                        true
                    }
                )
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(1, deliveries.get())
    }

    @Test
    fun undeliveredMatchesStayNewForTheNextRun() {
        val notified = mutableSetOf<String>()
        var deliveries = 0
        repeat(2) {
            deliverNewMatchesOnce(
                lock = Any(),
                matches = listOf("thread-1"),
                filterNew = { matches -> matches.filterNot { it in notified } },
                markDelivered = { notified += it },
                deliver = { deliveries += 1; false }
            )
        }
        assertEquals(2, deliveries)
    }

    @Test
    fun runThatSkippedABusyStepDoesNotCoverTheWaitingRun() = runBlocking {
        val runs = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val coverage = BackgroundRunCoverage()
        // A manual history refresh held the refresher: the worker skipped only that step (H4-4).
        val busy = async {
            gate.runExclusive(
                key = "futacha:3",
                maxWaitMillis = 5_000L,
                isSuccess = { it == "ok" && coverage.isComplete },
                onSkipped = { "skipped" }
            ) {
                runs += "busy"
                release.await()
                coverage.runSkippingIfBusy(isBusy = { it is IllegalStateException }) {
                    throw IllegalStateException("History refresh is already running")
                }
                runs += "later steps"
                "ok"
            }
        }
        yield()
        val waiting = async { run("futacha:3", runs = runs) }
        yield()
        release.complete(Unit)

        assertEquals("ok", busy.await())
        assertEquals("ok", waiting.await())
        assertEquals(listOf("busy", "later steps", "futacha:3"), runs)
    }

    @Test
    fun runSkippingIfBusyRethrowsOtherFailures() = runBlocking {
        val coverage = BackgroundRunCoverage()
        val thrown = runCatching {
            coverage.runSkippingIfBusy(isBusy = { it is IllegalStateException }) {
                throw java.io.IOException("network")
            }
        }.exceptionOrNull()

        assertTrue(thrown is java.io.IOException)
        assertTrue(coverage.isComplete)
        assertEquals("done", coverage.runSkippingIfBusy(isBusy = { true }) { "done" })
    }

    @Test
    fun workWaitsForTheSwitchThatEnqueuedItToClearItsJournal() = runBlocking {
        val journalGeneration = java.util.concurrent.atomic.AtomicReference<Long?>(7L)
        val clearer = async(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.delay(100L)
            journalGeneration.set(null)
        }

        // Before M4-3 the work saw the journal and finished without refreshing.
        assertTrue(awaitModeSwitchJournalCleared(7L, { journalGeneration.get() }, maxWaitMillis = 5_000L, pollMillis = 10L))
        clearer.await()
    }

    @Test
    fun workGivesUpWaitingOnlyForItsOwnStuckJournal() = runBlocking {
        assertEquals(false, awaitModeSwitchJournalCleared(7L, { 7L }, maxWaitMillis = 50L, pollMillis = 10L))
        // Another generation's journal is left to the work's own generation check.
        assertTrue(awaitModeSwitchJournalCleared(7L, { 9L }, maxWaitMillis = 50L, pollMillis = 10L))
        assertTrue(awaitModeSwitchJournalCleared(7L, { null }, maxWaitMillis = 50L, pollMillis = 10L))
        assertTrue(awaitModeSwitchJournalCleared(-1L, { -1L }, maxWaitMillis = 50L, pollMillis = 10L))
    }
}
