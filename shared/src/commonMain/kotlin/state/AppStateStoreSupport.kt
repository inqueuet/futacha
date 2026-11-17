package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.compatBoardKey
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
    val postId: String? = null,
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
 * Where one board's own settings are kept in AppState's board-keyed maps (the catalog modes and the board watch words),
 * D-B1 (docs/futaber-evolution-data-2026-10-31.md §2.1, §3.2). [key] is where they are written: an official board's
 * key in the compatibility store, the same on every device and in every mode, so a restored or re-added board finds them
 * again. [legacy] is what earlier versions wrote them under (the board's id in the AppState list, a slug that depends
 * on the order boards were added; the address when there is no id), read while [key] has nothing (read both, write one;
 * the migration stage `boardkeyed-maps-v1` moves them). A board that is not an official one keeps its id ([legacy] null).
 */
data class BoardSettingKeys(val key: String, val legacy: String? = null)

fun boardSettingKeys(boardId: String?, boardUrl: String?): BoardSettingKeys? {
    val legacy = boardId?.trim()?.takeIf { it.isNotEmpty() } ?: boardUrl?.trim()?.takeIf { it.isNotEmpty() }
    // An official board, or an あいもげ board while あいもげ is on (AM-B5), keeps its settings under its shared key.
    val shared = boardUrl?.let { com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf(it) }?.let { runCatching { compatBoardKey(it) }.getOrNull() }
    return when {
        shared != null -> BoardSettingKeys(shared, legacy?.takeIf { it != shared })
        legacy != null -> BoardSettingKeys(legacy)
        else -> null
    }
}

fun boardSettingKeys(board: BoardSummary): BoardSettingKeys? = boardSettingKeys(board.id, board.url)

/** The board's value: under its key, else under the key earlier versions used. */
fun <V> Map<String, V>.boardSetting(keys: BoardSettingKeys): V? = get(keys.key) ?: keys.legacy?.let { get(it) }

fun Map<String, *>.hasBoardSetting(keys: BoardSettingKeys): Boolean =
    containsKey(keys.key) || (keys.legacy != null && containsKey(keys.legacy))

/** Writes the board's value under its key, and takes away the copy under the earlier key. */
fun <V> Map<String, V>.withBoardSetting(keys: BoardSettingKeys, value: V): Map<String, V> =
    (if (keys.legacy != null) this - keys.legacy else this) + (keys.key to value)

fun <V> Map<String, V>.withoutBoardSetting(keys: BoardSettingKeys): Map<String, V> = this - setOfNotNull(keys.key, keys.legacy)

internal fun resolveEffectiveWatchWordsForBoard(
    globalWatchWords: List<String>,
    boardWatchWords: Map<String, List<String>>,
    boardId: String?
): List<String> {
    val normalizedBoardId = boardId?.trim().orEmpty()
    if (normalizedBoardId.isEmpty()) return globalWatchWords
    return resolveEffectiveWatchWordsForBoard(globalWatchWords, boardWatchWords, BoardSettingKeys(normalizedBoardId))
}

internal fun resolveEffectiveWatchWordsForBoard(
    globalWatchWords: List<String>,
    boardWatchWords: Map<String, List<String>>,
    keys: BoardSettingKeys?
): List<String> = if (keys != null && boardWatchWords.hasBoardSetting(keys)) {
    boardWatchWords.boardSetting(keys).orEmpty()
} else {
    globalWatchWords
}

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

    suspend fun remove(key: String): Job? {
        return mutex.withLock { map.remove(key) }
    }

    suspend fun cancelAndClear(): List<Job> {
        return mutex.withLock {
            val jobs = map.values.toList()
            map.clear()
            jobs
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
