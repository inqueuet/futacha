package com.valoser.futacha.shared.network

import com.valoser.futacha.shared.repository.InMemoryFileSystem
import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.*
import kotlin.test.*

class PersistentCookieCancellationTest {
    @Test fun cancellingDuringCommitWaitAlsoReleasesTransaction() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), "cookies.json")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val request = launch {
            storage.commitOnSuccess {
                storage.addCookie(Url("https://may.2chan.net/b/"), Cookie("staged", "value"))
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val guard = storage.javaClass.getDeclaredField("mutex").apply { isAccessible = true }.get(storage) as kotlinx.coroutines.sync.Mutex
        guard.lock()
        release.complete(Unit)
        repeat(5) { yield() }
        request.cancel()
        repeat(5) { yield() }
        guard.unlock()
        request.join()
        val coordinator = storage.javaClass.getDeclaredField("transactionCoordinator").apply { isAccessible = true }.get(storage)
        val retained = coordinator.javaClass.getDeclaredField("transactions").apply { isAccessible = true }.get(coordinator) as Map<*, *>
        assertTrue(retained.isEmpty())
    }

    @Test fun cancellingReadTransactionReleasesItsRetainedCookieSnapshots() = runBlocking {
        val storage = PersistentCookieStorage(InMemoryFileSystem(), "cookies.json")
        val entered = CompletableDeferred<Unit>()
        val request = launch {
            storage.commitOnSuccess {
                storage.addCookie(Url("https://may.2chan.net/b/"), Cookie("staged", "value"))
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        request.cancelAndJoin()
        assertTrue(storage.listCookies().isEmpty())
        // The failure affects retained memory, not cookie visibility; check the retained maps too.
        val coordinator = storage.javaClass.getDeclaredField("transactionCoordinator").apply { isAccessible = true }.get(storage)
        val retained = coordinator.javaClass.getDeclaredField("transactions").apply { isAccessible = true }.get(coordinator) as Map<*, *>
        assertTrue(retained.isEmpty(), "cancelled transactions must release both snapshots")
    }
}
