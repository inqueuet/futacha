package com.valoser.futacha.shared.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-save cache of folder listings for document trees (Android SAF), where
 * looking a name up otherwise lists the folder and queries every child.
 * Each folder is listed once; entries the save creates or deletes are updated
 * in place. Safe for the parallel media downloads of one save.
 */
internal class TreeChildIndex<T : Any> {
    private val mutex = Mutex()
    private val listings = mutableMapOf<String, MutableMap<String, T>>()

    suspend fun child(folderKey: String, name: String, list: suspend () -> Map<String, T>): T? = mutex.withLock {
        val listing = listings[folderKey] ?: list().toMutableMap().also { listings[folderKey] = it }
        listing[name]
    }

    suspend fun record(folderKey: String, name: String, child: T) = mutex.withLock {
        listings[folderKey]?.put(name, child)
        Unit
    }

    suspend fun forget(folderKey: String, name: String) = mutex.withLock {
        listings[folderKey]?.remove(name)
        Unit
    }

    /** Drops every listing, e.g. after the tree changed outside the save. */
    suspend fun clear() = mutex.withLock { listings.clear() }
}
