package com.valoser.futacha.shared.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadStorageLockRegistryTest {
    @Test
    fun withStorageLock_serializesAccessForSameStorageId() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            ThreadStorageLockRegistry.withStorageLock("same") {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }

        firstEntered.await()

        val second = async {
            ThreadStorageLockRegistry.withStorageLock("same") {
                events += "second-start"
                secondEntered.complete(Unit)
            }
        }

        delay(20)
        assertTrue(!secondEntered.isCompleted)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second-start"), events)
    }

    @Test
    fun withStorageLockOrNull_returnsNullWhenWaitTimeoutExpires() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            ThreadStorageLockRegistry.withStorageLock("same-timeout") {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }

        firstEntered.await()

        val timedOut = ThreadStorageLockRegistry.withStorageLockOrNull(
            storageId = "same-timeout",
            waitTimeoutMillis = 20
        ) {
            "acquired"
        }

        assertNull(timedOut)

        releaseFirst.complete(Unit)
        first.await()

        val acquiredAfterRelease = ThreadStorageLockRegistry.withStorageLockOrNull(
            storageId = "same-timeout",
            waitTimeoutMillis = 200
        ) {
            "acquired"
        }

        assertEquals("acquired", acquiredAfterRelease)
    }

    @Test
    fun withStorageLock_allowsDifferentStorageIdsToProceedConcurrently() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val releaseBoth = CompletableDeferred<Unit>()

        val first = async {
            ThreadStorageLockRegistry.withStorageLock("storage-a") {
                firstEntered.complete(Unit)
                secondEntered.await()
                releaseBoth.await()
                "a"
            }
        }

        val second = async {
            ThreadStorageLockRegistry.withStorageLock("storage-b") {
                secondEntered.complete(Unit)
                firstEntered.await()
                releaseBoth.await()
                "b"
            }
        }

        withTimeout(500) {
            firstEntered.await()
            secondEntered.await()
        }

        releaseBoth.complete(Unit)

        assertEquals("a", first.await())
        assertEquals("b", second.await())
    }
}
