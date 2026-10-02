package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** C5: the auto-scroll reload of a dead thread whose archive was not found. */
class FutachaAutoReloadGoneTest {
    private fun state(label: String) = ThreadUiState.Success(
        ThreadPage(threadId = "1", boardTitle = label, expiresAtLabel = null, deletedNotice = null, posts = emptyList())
    )

    private class Run(val reloads: Int, val probes: Int, val stoppedOn: ThreadUiState?)

    /** Mirrors the end-of-thread branch of the auto-scroll loop in FutachaThreadFeatureHost. */
    private suspend fun runEndOfThreadCycles(
        initial: ThreadUiState,
        cycles: Int,
        probe: suspend () -> Boolean,
        reload: (ThreadUiState) -> ThreadUiState
    ): Run {
        var shown = initial
        var reloadedFrom: ThreadUiState? = null
        var reloads = 0
        var probes = 0
        repeat(cycles) {
            if (futachaAutoReloadConfirmsGone(reloadedFrom, shown) { probes++; probe() }) {
                return Run(reloads, probes, shown)
            }
            reloadedFrom = shown
            reloads++
            shown = reload(shown)
        }
        return Run(reloads, probes, null)
    }

    @Test
    fun deadThreadWithoutArchiveStopsAfterTheFailedReload() = runBlocking {
        val page = state("dead")
        // A failed refresh (404/410, no archive) keeps the page on screen.
        val run = runEndOfThreadCycles(page, cycles = 20, probe = { true }) { it }
        assertEquals(1, run.reloads)
        assertEquals(1, run.probes)
        assertTrue(isFutachaThreadConfirmedGone(run.stoppedOn, page))
    }

    @Test
    fun reloadThatBringsANewPageNeverProbes() = runBlocking {
        var generation = 0
        val run = runEndOfThreadCycles(state("0"), cycles = 10, probe = { true }) { state("${++generation}") }
        assertEquals(10, run.reloads)
        assertEquals(0, run.probes)
        assertEquals(null, run.stoppedOn)
    }

    @Test
    fun liveOrUnknownProbeKeepsReloading() = runBlocking {
        val alive = runEndOfThreadCycles(state("alive"), cycles = 5, probe = { false }) { it }
        assertEquals(5, alive.reloads)
        assertEquals(null, alive.stoppedOn)
        val failing = runEndOfThreadCycles(state("offline"), cycles = 5, probe = { error("network") }) { it }
        assertEquals(5, failing.reloads)
        assertEquals(null, failing.stoppedOn)
    }

    @Test
    fun firstReloadIsNeverReplacedByAProbe() = runBlocking {
        var probed = false
        assertFalse(futachaAutoReloadConfirmsGone(null, state("first")) { probed = true; true })
        assertFalse(probed)
    }

    @Test
    fun aNewerPageFromAManualRetryClearsTheVerdict() {
        val dead = state("dead")
        assertTrue(isFutachaThreadConfirmedGone(dead, dead))
        assertFalse(isFutachaThreadConfirmedGone(dead, state("dead")))
        assertFalse(isFutachaThreadConfirmedGone(null, dead))
    }
}
