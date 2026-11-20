package com.valoser.futacha.shared.ui.image

import coil3.disk.DiskCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path

/**
 * One Coil [DiskCache] per cache directory for the whole process.
 *
 * `ImageLoader.shutdown()` does not close its DiskCache, and a replacement loader
 * (mode switch, image-setting change, network-services switch) used to open a second
 * DiskCache on the same directory: two journals over one directory leak descriptors,
 * miss each other's entries and can corrupt the journal. Loaders now lease the shared
 * instance and the last lease closes it.
 */
internal class SharedImageDiskCacheRegistry(
    private val create: (directory: Path, maxBytes: Long) -> DiskCache? = { directory, maxBytes ->
        DiskCache.Builder().directory(directory).maxSizeBytes(maxBytes).build()
    }
) {
    private class Entry(val cache: DiskCache) {
        var holders = 1
        val closed = CompletableDeferred<Unit>()
    }

    private val lock = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun acquire(directory: Path, maxBytes: Long): DiskCache? {
        val key = directory.normalized().toString()
        while (true) {
            val closing = lock.withLock {
                val existing = entries[key]
                if (existing == null) {
                    // Coil opens the journal lazily on first use.
                    return create(directory, maxBytes)?.also { entries[key] = Entry(it) }
                }
                if (existing.holders > 0 && existing.cache.maxSize == maxBytes) {
                    existing.holders++
                    return existing.cache
                }
                existing.closed
            }
            closing.await()
        }
    }

    suspend fun release(cache: DiskCache) = withContext(NonCancellable) {
        val closing = lock.withLock {
            val match = entries.entries.firstOrNull { it.value.cache === cache }
            if (match != null) {
                if (match.value.holders == 0 || --match.value.holders > 0) return@withContext
                match.key to match.value
            } else null
        }
        try {
            cache.shutdown()
        } finally {
            if (closing != null) {
                val (key, entry) = closing
                lock.withLock {
                    entries.remove(key)
                    entry.closed.complete(Unit)
                }
            }
        }
    }

    internal suspend fun holderCount(directory: Path): Int =
        lock.withLock { entries[directory.normalized().toString()]?.holders ?: 0 }
}

internal val SharedImageDiskCaches = SharedImageDiskCacheRegistry()
