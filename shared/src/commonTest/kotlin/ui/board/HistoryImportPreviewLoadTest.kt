package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** U4-2: closing the import loading dialog must abandon the archive load. */
class HistoryImportPreviewLoadTest {
    @Test
    fun cancelledLoadIsStoppedAndItsResultDropped() = runBlocking {
        coroutineScope {
            val load = LatestOnlyLoad()
            val gate = CompletableDeferred<String>()
            val results = mutableListOf<String>()
            var cancelled = false
            load.start(this, load = {
                try {
                    gate.await()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled = true
                    throw e
                }
            }, onResult = { results += it })
            yield()
            load.cancel()
            gate.complete("late preview")
            yield()
            yield()
            assertTrue(cancelled, "the load must be cancelled")
            assertEquals(emptyList(), results)
        }
    }

    @Test
    fun resultOfALoadThatIgnoresCancellationIsStillDropped() = runBlocking {
        coroutineScope {
            val load = LatestOnlyLoad()
            val gate = CompletableDeferred<String>()
            val results = mutableListOf<String>()
            // A load that finishes without reaching a cancellation point.
            load.start(this, load = { withContext(NonCancellable) { gate.await() } }, onResult = { results += it })
            yield()
            load.cancel()
            gate.complete("late error")
            yield()
            yield()
            assertEquals(emptyList(), results)
        }
    }

    @Test
    fun onlyTheLatestLoadDeliversItsResult() = runBlocking {
        coroutineScope {
            val load = LatestOnlyLoad()
            val first = CompletableDeferred<String>()
            val second = CompletableDeferred<String>()
            val results = mutableListOf<String>()
            load.start(this, load = { withContext(NonCancellable) { first.await() } }, onResult = { results += it })
            yield()
            load.start(this, load = { second.await() }, onResult = { results += it })
            yield()
            first.complete("stale")
            second.complete("current")
            yield()
            yield()
            assertEquals(listOf("current"), results)
        }
    }
}
