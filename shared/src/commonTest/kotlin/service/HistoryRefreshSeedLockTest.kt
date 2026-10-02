package com.valoser.futacha.shared.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryRefreshSeedLockTest {
    private fun seedLockKey(storageId: String) =
        buildThreadStorageLockKey(storageId = storageId, baseDirectory = AUTO_SAVE_DIRECTORY)

    @Test
    fun seedReplacedBeforeItsLockIsTakenIsResolvedAgain() = runBlocking {
        // The index names gen1, which is replaced by gen2 (and deleted) before gen1 is locked.
        val answers = ArrayDeque(listOf("seed-test-gen1", "seed-test-gen2", "seed-test-gen2", "seed-test-gen2"))
        var lockedWhileSaving: Boolean? = null
        val seed = withIndexedSeedStorageLock(
            resolveSeedStorageId = { answers.removeFirst() },
            stableStorageId = "seed-test-stable"
        ) { seedStorageId ->
            lockedWhileSaving = ThreadStorageLockRegistry.withStorageLockOrNull(
                seedLockKey("seed-test-gen2"),
                waitTimeoutMillis = 10L
            ) { Unit } == null
            seedStorageId
        }
        assertEquals("seed-test-gen2", seed)
        assertEquals(true, lockedWhileSaving)
    }

    @Test
    fun seedThatKeepsChangingFallsBackToAnUnseededSave() = runBlocking {
        var generation = 0
        val seed = withIndexedSeedStorageLock(
            resolveSeedStorageId = { "seed-test-churn-${generation++}" },
            stableStorageId = "seed-test-stable"
        ) { it }
        assertNull(seed)
    }

    @Test
    fun stableOrMissingSeedRunsWithoutTakingAnotherLock() = runBlocking {
        assertEquals(
            "seed-test-stable",
            withIndexedSeedStorageLock({ "seed-test-stable" }, "seed-test-stable") { it }
        )
        assertNull(withIndexedSeedStorageLock({ null }, "seed-test-stable") { it })
        assertNull(withIndexedSeedStorageLock({ error("index unreadable") }, "seed-test-stable") { it })
    }
}
