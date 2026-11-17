package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * History deletions kept across restarts (plan 1-5 of docs/shared-history-tabs-plan-2026-11-09.md, audit F3).
 *
 * Kept outside the split store's folder and manifest so that rebuilding or downgrading the manifest cannot lose it,
 * and so its size never counts toward the manifest's limit. A full clear is [clearedAtMillis] alone.
 */
@Serializable
internal data class PersistedHistoryTombstones(
    val version: Int = 1,
    val clearedAtMillis: Long = 0L,
    val entries: List<PersistedHistoryTombstone> = emptyList()
)

@Serializable
internal data class PersistedHistoryTombstone(
    val key: String,
    val atMillis: Long,
    /** The deleted row's last visit: ties in [atMillis] keep the most recently read rows. */
    val lastVisitedEpochMillis: Long = 0L
)

internal interface AppStateHistoryTombstonePersistence {
    suspend fun read(): PersistedHistoryTombstones
    suspend fun write(tombstones: PersistedHistoryTombstones)
}

internal const val MAX_PERSISTED_HISTORY_TOMBSTONES = 1_024
internal const val PERSISTED_HISTORY_TOMBSTONE_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1_000

/**
 * The tombstones worth keeping: newer than [nowMillis] − 90 days and than the last clear (a clear already hides what
 * it removed), newest first (ties: most recently read row first), at most [MAX_PERSISTED_HISTORY_TOMBSTONES].
 */
internal fun selectPersistedHistoryTombstones(
    tombstones: Map<String, HistoryTombstone>,
    clearedAtMillis: Long,
    nowMillis: Long
): PersistedHistoryTombstones {
    val keepAfter = nowMillis - PERSISTED_HISTORY_TOMBSTONE_RETENTION_MILLIS
    val entries = tombstones.entries
        .filter { (key, value) -> key.isNotBlank() && value.atMillis > keepAfter && value.atMillis > clearedAtMillis }
        .sortedWith(
            compareByDescending<Map.Entry<String, HistoryTombstone>> { it.value.atMillis }
                .thenByDescending { it.value.entryLastVisitedEpochMillis }
                .thenBy { it.key }
        )
        .take(MAX_PERSISTED_HISTORY_TOMBSTONES)
        .map { (key, value) -> PersistedHistoryTombstone(key, value.atMillis, value.entryLastVisitedEpochMillis) }
    return PersistedHistoryTombstones(clearedAtMillis = clearedAtMillis.coerceAtLeast(0L), entries = entries)
}

/**
 * Loaded tombstones as the in-memory map: sequence 0 (the deletion counter restarts with the process, so a restarted
 * archive import is not refused), times in the future clamped to now (a clock set back must not hide rows forever).
 */
internal fun loadHistoryTombstones(
    persisted: PersistedHistoryTombstones,
    nowMillis: Long
): Pair<Map<String, HistoryTombstone>, Long> {
    val map = LinkedHashMap<String, HistoryTombstone>()
    persisted.entries.take(MAX_PERSISTED_HISTORY_TOMBSTONES).forEach { entry ->
        if (entry.key.isBlank() || entry.key.length > MAX_HISTORY_TOMBSTONE_KEY_LENGTH) return@forEach
        map[entry.key] = HistoryTombstone(
            atMillis = entry.atMillis.coerceAtMost(nowMillis),
            sequence = 0L,
            entryLastVisitedEpochMillis = entry.lastVisitedEpochMillis
        )
    }
    return map to persisted.clearedAtMillis.coerceIn(0L, nowMillis)
}

private const val MAX_HISTORY_TOMBSTONE_KEY_LENGTH = 8_192
private const val HISTORY_TOMBSTONE_PATH = "private/history_tombstones.json"
private const val HISTORY_TOMBSTONE_BACKUP_PATH = "private/history_tombstones.json.backup"
private const val HISTORY_TOMBSTONE_MAX_BYTES = 4L * 1024L * 1024L

internal class AppStateHistoryTombstoneFile(
    private val fileSystem: FileSystem,
    private val json: Json,
    private val tag: String
) : AppStateHistoryTombstonePersistence {
    private val mutex = Mutex()

    override suspend fun read(): PersistedHistoryTombstones = withContext(AppDispatchers.io) {
        mutex.withLock {
            readFile(HISTORY_TOMBSTONE_PATH) ?: readFile(HISTORY_TOMBSTONE_BACKUP_PATH) ?: PersistedHistoryTombstones()
        }
    }

    override suspend fun write(tombstones: PersistedHistoryTombstones) = withContext(AppDispatchers.io) {
        mutex.withLock {
            val encoded = json.encodeToString(PersistedHistoryTombstones.serializer(), tombstones)
            // The backup first: a write cut short leaves one readable copy.
            fileSystem.writeString(HISTORY_TOMBSTONE_BACKUP_PATH, encoded).getOrThrow()
            fileSystem.writeString(HISTORY_TOMBSTONE_PATH, encoded).getOrThrow()
        }
    }

    private suspend fun readFile(path: String): PersistedHistoryTombstones? {
        if (!fileSystem.exists(path)) return null
        return runSuspendCatchingPreservingCancellation {
            val size = fileSystem.getFileSize(path)
            require(size in 0L..HISTORY_TOMBSTONE_MAX_BYTES) { "Tombstone file exceeds its permitted size" }
            json.decodeFromString(PersistedHistoryTombstones.serializer(), fileSystem.readString(path).getOrThrow())
        }.onFailure { error -> Logger.w(tag, "Unreadable history tombstones at $path: ${error.message}") }.getOrNull()
    }
}

/** In-memory persistence for tests that restart an [AppStateStore] without a file system. */
internal class InMemoryHistoryTombstonePersistence : AppStateHistoryTombstonePersistence {
    var stored: PersistedHistoryTombstones = PersistedHistoryTombstones()
    var writes = 0
    override suspend fun read() = stored
    override suspend fun write(tombstones: PersistedHistoryTombstones) { stored = tombstones; writes++ }
}
