package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Total size the history auto-save folder may use before its oldest saves are removed. */
internal const val AUTO_SAVE_MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L

/**
 * Saves newer than this are never evicted for size. A displayed thread is re-saved every
 * 60 s (a run is capped at 180 s), so this also covers the thread currently on screen.
 */
internal const val AUTO_SAVE_EVICTION_RECENT_GRACE_MILLIS = 10L * 60L * 1000L

private const val TAG = "AutoSaveStorageMaintenance"

internal fun autoSaveRetentionKey(threadId: String, boardId: String?): String =
    "${boardId?.trim()?.lowercase().orEmpty()}\u0000${threadId.trim()}"

/**
 * Threads whose auto-save is being written right now (screen or background). The size
 * eviction never removes them, so a running save cannot lose its seed folder.
 */
internal object AutoSaveRetentionRegistry {
    private val mutex = Mutex()
    private val holders = mutableMapOf<String, Int>()

    suspend fun <T> retain(threadId: String, boardId: String?, block: suspend () -> T): T {
        val key = autoSaveRetentionKey(threadId, boardId)
        mutex.withLock { holders[key] = (holders[key] ?: 0) + 1 }
        try {
            return block()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    val remaining = (holders[key] ?: 1) - 1
                    if (remaining <= 0) holders.remove(key) else holders[key] = remaining
                }
            }
        }
    }

    suspend fun snapshot(): Set<String> = mutex.withLock { holders.keys.toSet() }
}

/**
 * Deletes the auto-saves of history entries dropped by the history size limit;
 * without this their folders stay on disk with nothing referencing them.
 */
internal fun buildTrimmedHistoryAutoSavePurger(
    fileSystem: FileSystem
): suspend (List<ThreadHistoryEntry>) -> Unit {
    // A separate instance is safe: purge cutoffs are shared by every instance of the
    // root, so a save running elsewhere is discarded instead of re-indexing the thread.
    val repository by lazy { SavedThreadRepository(fileSystem, baseDirectory = AUTO_SAVE_DIRECTORY) }
    return { entries -> purgeAutoSavesOfTrimmedHistory(repository, entries) }
}

internal suspend fun purgeAutoSavesOfTrimmedHistory(
    repository: SavedThreadRepository,
    entries: List<ThreadHistoryEntry>
) {
    fun identityOf(entry: ThreadHistoryEntry): Pair<String, String?> {
        val boardId = entry.boardId.trim().ifBlank {
            runCatching { BoardUrlResolver.resolveBoardSlug(entry.boardUrl) }.getOrDefault("")
        }.ifBlank { null }
        return entry.threadId to boardId
    }
    // One index rewrite per chunk rather than per entry. A chunk that fails as a whole is
    // retried entry by entry, so one bad folder does not keep the others from being deleted.
    entries.chunked(TRIMMED_HISTORY_PURGE_CHUNK_SIZE).forEach { chunk ->
        val identities = chunk.map(::identityOf)
        repository.purgeIndexedThreadsStorage(identities).onFailure { batchError ->
            Logger.w(TAG, "Failed to delete ${identities.size} trimmed auto-saves together, retrying one by one: ${batchError.message}")
            identities.forEach { (threadId, boardId) ->
                repository.purgeIndexedThreadStorage(threadId, boardId).onFailure { error ->
                    Logger.w(TAG, "Failed to delete auto-save of trimmed history $threadId: ${error.message}")
                }
            }
        }
    }
}

private const val TRIMMED_HISTORY_PURGE_CHUNK_SIZE = 200
