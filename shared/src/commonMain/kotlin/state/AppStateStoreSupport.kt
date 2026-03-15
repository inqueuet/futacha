package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuConfig
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.defaultThreadSettingsMenuConfig
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
 * Value object for debounced history scroll-position updates.
 * This keeps call sites explicit and avoids long positional parameter lists.
 */
data class AppStateHistoryScrollUpdateRequest(
    val threadId: String,
    val index: Int,
    val offset: Int,
    val boardId: String,
    val title: String,
    val titleImageUrl: String,
    val boardName: String,
    val boardUrl: String,
    val replyCount: Int,
    val forcePersist: Boolean = false
)

/**
 * Value object for initial app-state seeding.
 * This keeps startup call sites explicit without exposing a long positional API.
 */
data class AppStateSeedDefaults(
    val boards: List<BoardSummary>,
    val history: List<ThreadHistoryEntry>,
    val ngHeaders: List<String> = emptyList(),
    val ngWords: List<String> = emptyList(),
    val catalogNgWords: List<String> = emptyList(),
    val watchWords: List<String> = emptyList(),
    val selfPostIdentifierMap: Map<String, List<String>> = emptyMap(),
    val catalogModeMap: Map<String, CatalogMode> = emptyMap(),
    val threadMenuConfig: List<ThreadMenuItemConfig> = defaultThreadMenuConfig(),
    val threadSettingsMenuConfig: List<ThreadSettingsMenuItemConfig> =
        defaultThreadSettingsMenuConfig(),
    val threadMenuEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries(),
    val catalogNavEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries(),
    val lastUsedDeleteKey: String = ""
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
