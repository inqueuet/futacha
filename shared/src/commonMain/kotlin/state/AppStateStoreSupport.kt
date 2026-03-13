package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight error envelope exposed by AppStateStore when persistence fails.
 */
data class StorageError(
    val operation: String,
    val message: String,
    val timestamp: Long
)

/**
 * Thread-safe Job registry used for debounced scroll-position writes.
 */
internal class AtomicJobMap {
    private val mutex = Mutex()
    private val map = mutableMapOf<String, Job>()

    suspend fun putAndCancelOld(key: String, newJob: Job): Job? {
        return mutex.withLock {
            map.put(key, newJob)
        }
    }

    suspend fun removeIfSame(key: String, job: Job?) {
        mutex.withLock {
            if (map[key] == job) {
                map.remove(key)
            }
        }
    }
}

/**
 * Snapshot used to persist and roll back history mutations safely.
 */
internal data class HistoryMutation<out T>(
    val revision: Long,
    val updatedHistory: List<ThreadHistoryEntry>,
    val previousRevision: Long,
    val previousHistory: List<ThreadHistoryEntry>?,
    val metadata: T
)
