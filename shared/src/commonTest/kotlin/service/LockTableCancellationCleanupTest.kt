package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.network.HttpBoardApiPostingConfig
import com.valoser.futacha.shared.network.HttpBoardApiPostingConfigLockEntry
import com.valoser.futacha.shared.network.HttpBoardApiThreadSafeLruCache
import com.valoser.futacha.shared.network.fallbackHttpBoardApiPostingConfig
import com.valoser.futacha.shared.network.getOrLoadHttpBoardApiPostingConfig
import com.valoser.futacha.shared.repo.DefaultBoardRepositoryBoardInitLock
import com.valoser.futacha.shared.repo.initializeDefaultBoardRepositoryCookies
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A caller cancelled while the lock-table guard is busy must still release its
 * entry; otherwise per-key entries accumulate for the rest of the process.
 */
class LockTableCancellationCleanupTest {
    private suspend fun cancelWhileGuardIsHeld(
        guard: Mutex,
        blockEntered: CompletableDeferred<Unit>,
        start: suspend (CompletableDeferred<Unit>) -> Unit
    ) = coroutineScope {
        val release = CompletableDeferred<Unit>()
        val job = async(start = CoroutineStart.UNDISPATCHED) { start(release) }
        blockEntered.await()
        guard.lock()
        job.cancel()
        // Let the cancelled caller reach its cleanup and wait on the busy guard.
        repeat(5) { yield() }
        guard.unlock()
        runCatching { job.await() }
    }

    @Test
    fun mediaWriteLockEntryIsRemovedWhenCancelledWhileGuardIsBusy() = runBlocking {
        val guard = Mutex()
        val locks = mutableMapOf<String, ThreadSaveMediaPathLock>()
        val entered = CompletableDeferred<Unit>()
        cancelWhileGuardIsHeld(guard, entered) { release ->
            withThreadSaveMediaWriteLock("images/1.jpg", guard, locks) {
                entered.complete(Unit)
                release.await()
            }
        }
        assertTrue(locks.isEmpty(), "leaked entries: ${locks.keys}")
    }

    @Test
    fun postingConfigLockEntryIsRemovedWhenCancelledWhileGuardIsBusy() = runBlocking {
        val guard = Mutex()
        val locks = mutableMapOf<String, HttpBoardApiPostingConfigLockEntry>()
        val cache = HttpBoardApiThreadSafeLruCache<String, HttpBoardApiPostingConfig>(4)
        val entered = CompletableDeferred<Unit>()
        cancelWhileGuardIsHeld(guard, entered) { release ->
            getOrLoadHttpBoardApiPostingConfig(
                board = "https://may.2chan.net/b/",
                cache = cache,
                locksGuard = guard,
                locks = locks,
                fallbackChrencValue = "文字",
                logTag = "LockTableCancellationCleanupTest"
            ) {
                entered.complete(Unit)
                release.await()
                fallbackHttpBoardApiPostingConfig("文字")
            }
        }
        assertTrue(locks.isEmpty(), "leaked entries: ${locks.keys}")
    }

    @Test
    fun boardInitLockEntryIsRemovedWhenCancelledWhileGuardIsBusy() = runBlocking {
        val guard = Mutex()
        val locks = mutableMapOf<String, DefaultBoardRepositoryBoardInitLock>()
        val entered = CompletableDeferred<Unit>()
        cancelWhileGuardIsHeld(guard, entered) { release ->
            initializeDefaultBoardRepositoryCookies(
                board = "https://may.2chan.net/b/",
                logTag = "LockTableCancellationCleanupTest",
                initializedBoards = mutableSetOf(),
                cookieRepository = null,
                boardInitMutex = guard,
                boardInitializationMutexes = locks,
                requireSetup = {
                    entered.complete(Unit)
                    release.await()
                    true
                },
                fetchCatalogSetup = {}
            )
        }
        assertTrue(locks.isEmpty(), "leaked entries: ${locks.keys}")
    }
}
