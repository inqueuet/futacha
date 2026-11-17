package com.valoser.futacha.shared.state

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal suspend fun <T> readAppStateCachedSnapshot(
    mutex: Mutex,
    currentCachedValue: () -> T?,
    readStorageSnapshot: suspend () -> T,
    setCachedValue: (T) -> Unit,
    onReadFailure: (Throwable) -> T,
    rethrowIfCancellation: (Throwable) -> Unit
): T {
    val cached = mutex.withLock { currentCachedValue() }
    if (cached != null) {
        return cached
    }
    val decoded = try {
        readStorageSnapshot()
    } catch (e: Exception) {
        rethrowIfCancellation(e)
        return onReadFailure(e)
    }
    return mutex.withLock {
        val latest = currentCachedValue()
        if (latest != null) {
            latest
        } else {
            setCachedValue(decoded)
            decoded
        }
    }
}

internal suspend fun <T> mutateAppStateCachedSnapshot(
    mutex: Mutex,
    loadedSnapshot: T,
    currentCachedValue: () -> T?,
    setCachedValue: (T) -> Unit,
    transform: (T) -> T,
    persistUpdatedValue: suspend (T) -> Unit,
    onWriteFailure: (Throwable) -> Unit,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    mutex.withLock {
        val current = currentCachedValue() ?: loadedSnapshot
        val updated = transform(current)
        if (updated === current || updated == current) {
            return@withLock
        }
        try {
            persistUpdatedValue(updated)
            setCachedValue(updated)
        } catch (e: Exception) {
            setCachedValue(current)
            rethrowIfCancellation(e)
            onWriteFailure(e)
        }
    }
}
