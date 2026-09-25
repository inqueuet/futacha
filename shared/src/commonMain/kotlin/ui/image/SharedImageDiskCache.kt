package com.valoser.futacha.shared.ui.image

import coil3.disk.DiskCache
import com.valoser.futacha.shared.util.Logger
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
@OptIn(ExperimentalAtomicApi::class)
internal class SharedImageDiskCacheRegistry(
    private val create: (directory: Path, maxBytes: Long) -> DiskCache? = { directory, maxBytes ->
        DiskCache.Builder().directory(directory).maxSizeBytes(maxBytes).build()
    }
) {
    private class Entry(val cache: DiskCache, val holders: Int)

    private val entries = AtomicReference<Map<String, Entry>>(emptyMap())

    fun acquire(directory: Path, maxBytes: Long): DiskCache? {
        val key = directory.normalized().toString()
        var created: DiskCache? = null
        while (true) {
            val current = entries.load()
            val existing = current[key]
            if (existing != null) {
                if (entries.compareAndSet(current, current + (key to Entry(existing.cache, existing.holders + 1)))) {
                    created?.shutdown()
                    if (existing.cache.maxSize != maxBytes) {
                        Logger.i(
                            TAG,
                            "Image disk cache in use; its size changes to $maxBytes bytes once every loader is released"
                        )
                    }
                    return existing.cache
                }
                continue
            }
            val candidate = created ?: create(directory, maxBytes)?.also { created = it } ?: return null
            if (entries.compareAndSet(current, current + (key to Entry(candidate, 1)))) {
                return candidate
            }
        }
    }

    /** Releases one lease; the last one closes the cache. Unknown caches are closed directly. */
    fun release(cache: DiskCache) {
        while (true) {
            val current = entries.load()
            val match = current.entries.firstOrNull { it.value.cache === cache }
            if (match == null) {
                cache.shutdown()
                return
            }
            val remaining = match.value.holders - 1
            val next = if (remaining <= 0) {
                current - match.key
            } else {
                current + (match.key to Entry(cache, remaining))
            }
            if (entries.compareAndSet(current, next)) {
                if (remaining <= 0) cache.shutdown()
                return
            }
        }
    }

    internal fun holderCount(directory: Path): Int =
        entries.load()[directory.normalized().toString()]?.holders ?: 0

    private companion object {
        const val TAG = "SharedImageDiskCache"
    }
}

internal val SharedImageDiskCaches = SharedImageDiskCacheRegistry()
