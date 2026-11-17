package com.valoser.futacha.shared.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThreadSavePlatformProtectionTest {
    @Test
    fun secondUserSaveIsRejectedAndTheGateReopensAfterCompletion() = runBlocking {
        coroutineScope {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            // Kotlin/Native runBlocking does not pump a separately dispatched
            // child until the main test thread yields. Start immediately so the
            // gate is definitely held before this test awaits the signal.
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                runExclusiveUserThreadSave {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    "first-complete"
                }
            }

            firstStarted.await()
            val rejection = assertFailsWith<ThreadSaveAlreadyRunningException> {
                runExclusiveUserThreadSave { "should-not-run" }
            }
            assertEquals("別の保存を実行中です", rejection.message)

            releaseFirst.complete(Unit)
            assertEquals("first-complete", first.await())
            assertEquals(
                "third-complete",
                runExclusiveUserThreadSave { "third-complete" }
            )
        }
    }

    @Test
    fun backgroundTaskEndsOnceAfterNormalCompletion() = runBlocking {
        val ended = mutableListOf<Int>()
        val result = withBackgroundTaskLease(
            mainDispatcher = Dispatchers.Unconfined,
            begin = { 7 },
            end = { ended += it },
            block = { "saved" }
        )
        assertEquals("saved", result)
        assertEquals(listOf(7), ended)
    }

    @Test
    fun backgroundTaskEndsOnceWhenTheSaveIsCancelled() = runBlocking {
        val ended = mutableListOf<Int>()
        val started = CompletableDeferred<Unit>()
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            withBackgroundTaskLease(
                mainDispatcher = Dispatchers.Unconfined,
                begin = { 11 },
                end = { ended += it },
                block = {
                    started.complete(Unit)
                    awaitCancellation()
                }
            )
        }
        started.await()
        save.cancelAndJoin()
        assertEquals(listOf(11), ended)
    }

    @Test
    fun expirationCancelsTheSaveAndEndsTheTaskOnlyOnce() = runBlocking {
        val ended = mutableListOf<Int>()
        var expire: (() -> Unit)? = null
        val started = CompletableDeferred<Unit>()
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            withBackgroundTaskLease(
                mainDispatcher = Dispatchers.Unconfined,
                begin = { onExpired -> expire = onExpired; 13 },
                end = { ended += it },
                block = {
                    started.complete(Unit)
                    awaitCancellation()
                }
            )
        }
        started.await()
        assertNotNull(expire).invoke()
        // Expiration must end the task synchronously, before the save unwinds.
        assertEquals(listOf(13), ended)
        save.join()
        assertTrue(save.isCancelled)
        assertEquals(listOf(13), ended)
    }

    @Test
    fun cancellationWhileTheTaskBeginsStillEndsTheTask() = runBlocking {
        val ended = mutableListOf<Int>()
        var blockRan = false
        val save = launch(start = CoroutineStart.UNDISPATCHED) {
            val self = coroutineContext[Job]
            withBackgroundTaskLease(
                mainDispatcher = Dispatchers.Unconfined,
                begin = {
                    // Cancellation arrives after the platform created the task
                    // but before the identifier reaches the caller.
                    self?.cancel()
                    17
                },
                end = { ended += it },
                block = { blockRan = true }
            )
        }
        save.join()
        assertTrue(save.isCancelled)
        assertFalse(blockRan)
        assertEquals(listOf(17), ended)
    }

    @Test
    fun unavailableBackgroundTaskStillRunsTheSave() = runBlocking {
        val ended = mutableListOf<Int>()
        val result = withBackgroundTaskLease<String, Int>(
            mainDispatcher = Dispatchers.Unconfined,
            begin = { null },
            end = { ended += it },
            block = { "saved" }
        )
        assertEquals("saved", result)
        assertTrue(ended.isEmpty())
    }
}
