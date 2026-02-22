package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 手動保存は共有Documents/futacha配下に置き、ユーザーが参照できるようにする
const val MANUAL_SAVE_DIRECTORY = "saved_threads"
const val DEFAULT_MANUAL_SAVE_ROOT = "Documents"

// 自動保存は FileSystem 側でアプリ専用の非公開ディレクトリへ解決される
const val AUTO_SAVE_DIRECTORY = "autosaved_threads"

private const val STORAGE_KEY_DELIMITER = "__"
private val INVALID_STORAGE_SEGMENT_REGEX = Regex("""[^A-Za-z0-9._-]""")
private const val STORAGE_SEGMENT_MAX_LENGTH = 80
private const val STORAGE_HASH_SUFFIX_LENGTH = 8

private data class StorageLockEntry(
    val mutex: Mutex,
    var holders: Int = 0
)

/**
 * Serializes per-thread storage mutations across save/delete flows.
 * This prevents concurrent writes and recursive deletions from corrupting saved data.
 */
object ThreadStorageLockRegistry {
    private val guard = Mutex()
    private val locks = mutableMapOf<String, StorageLockEntry>()

    suspend fun <T> withStorageLock(storageId: String, block: suspend () -> T): T {
        val key = storageId.trim().ifBlank { "thread" }
        val entry = guard.withLock {
            val current = locks.getOrPut(key) { StorageLockEntry(Mutex()) }
            current.holders += 1
            current
        }
        return try {
            entry.mutex.withLock {
                block()
            }
        } finally {
            guard.withLock {
                val current = locks[key]
                if (current === entry) {
                    current.holders -= 1
                    if (current.holders <= 0 && !current.mutex.isLocked) {
                        locks.remove(key)
                    }
                }
            }
        }
    }
}

fun buildThreadStorageLockKey(
    storageId: String,
    baseDirectory: String,
    baseSaveLocation: SaveLocation? = null
): String {
    val normalizedStorageId = storageId.trim().ifBlank { "thread" }
    val rootIdentity = when (baseSaveLocation) {
        null -> "path:${baseDirectory.trim()}"
        is SaveLocation.Path -> "path:${baseSaveLocation.path.trim()}"
        is SaveLocation.TreeUri -> "tree:${baseSaveLocation.uri.trim()}"
        is SaveLocation.Bookmark -> "bookmark:${baseSaveLocation.bookmarkData.trim()}"
    }
    val rootKey = shortStableHash(rootIdentity)
    return "root_${rootKey}${STORAGE_KEY_DELIMITER}$normalizedStorageId"
}

fun buildThreadStorageId(boardId: String?, threadId: String): String {
    val safeThread = sanitizeStorageSegment(threadId).ifBlank { "thread" }
    val safeBoard = sanitizeStorageSegment(boardId.orEmpty())
    return if (safeBoard.isBlank()) {
        safeThread
    } else {
        "$safeBoard$STORAGE_KEY_DELIMITER$safeThread"
    }
}

internal fun buildLegacyThreadStorageId(boardId: String?, threadId: String): String {
    val safeThread = sanitizeLegacyStorageSegment(threadId).ifBlank { "thread" }
    val safeBoard = sanitizeLegacyStorageSegment(boardId.orEmpty())
    return if (safeBoard.isBlank()) {
        safeThread
    } else {
        "$safeBoard$STORAGE_KEY_DELIMITER$safeThread"
    }
}

private fun sanitizeStorageSegment(value: String): String {
    val normalized = value
        .trim()
        .replace(INVALID_STORAGE_SEGMENT_REGEX, "_")
        .trim('_')
    if (normalized.isBlank()) return ""
    if (normalized.length <= STORAGE_SEGMENT_MAX_LENGTH) {
        return normalized
    }

    // Keep backward-compatible readable prefix but add stable hash to avoid collisions.
    val baseMaxLength = (STORAGE_SEGMENT_MAX_LENGTH - STORAGE_HASH_SUFFIX_LENGTH - 1).coerceAtLeast(1)
    val base = normalized.take(baseMaxLength).trimEnd('_').ifBlank { "segment" }
    val hash = shortStableHash(normalized)
    return "${base}_$hash"
}

private fun shortStableHash(value: String): String {
    // FNV-1a 64-bit, truncated to 8 hex chars for path-length safety.
    var hash = 1469598103934665603UL
    value.forEach { ch ->
        hash = hash xor ch.code.toULong()
        hash *= 1099511628211UL
    }
    val hex = hash.toString(16)
    return hex.takeLast(STORAGE_HASH_SUFFIX_LENGTH).padStart(STORAGE_HASH_SUFFIX_LENGTH, '0')
}

private fun sanitizeLegacyStorageSegment(value: String): String {
    return value
        .trim()
        .replace(INVALID_STORAGE_SEGMENT_REGEX, "_")
        .trim('_')
        .take(STORAGE_SEGMENT_MAX_LENGTH)
}
