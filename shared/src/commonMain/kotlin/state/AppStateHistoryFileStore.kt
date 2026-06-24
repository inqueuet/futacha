package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val HISTORY_FILE_STORE_DIR = "private/history_store"
private const val HISTORY_FILE_STORE_ENTRIES_DIR = "$HISTORY_FILE_STORE_DIR/entries"
private const val HISTORY_FILE_STORE_MANIFEST_PATH = "$HISTORY_FILE_STORE_DIR/manifest.json"
private const val HISTORY_FILE_STORE_MANIFEST_BACKUP_PATH = "$HISTORY_FILE_STORE_DIR/manifest.json.backup"
private const val HISTORY_FILE_STORE_VERSION = 1
private const val HISTORY_FILE_KEY_PREFIX_MAX_LENGTH = 48

@Serializable
private data class AppStateHistoryFileManifest(
    val version: Int = HISTORY_FILE_STORE_VERSION,
    val revision: Long = 0L,
    val orderedEntries: List<AppStateHistoryFileManifestEntry> = emptyList()
)

@Serializable
private data class AppStateHistoryFileManifestEntry(
    val key: String,
    val identity: String
)

internal class AppStateHistoryFileStore(
    private val fileSystem: FileSystem,
    private val json: Json,
    private val tag: String
) {
    private val mutex = Mutex()
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes
    private val cachedEntryContentHashes = mutableMapOf<String, String>()

    suspend fun readHistorySnapshot(
        readLegacyHistoryJson: suspend () -> String?
    ): List<ThreadHistoryEntry> {
        return mutex.withLock {
            withContext(AppDispatchers.io) {
                val manifest = readManifestOrNull()
                if (manifest != null) {
                    return@withContext readHistoryFromManifest(manifest)
                }
                val legacyHistory = readLegacyHistory(readLegacyHistoryJson)
                if (legacyHistory.isNotEmpty()) {
                    runCatching {
                        persistHistorySnapshotLocked(legacyHistory)
                    }.onFailure { error ->
                        Logger.e(tag, "Failed to migrate legacy history JSON into split store", error)
                    }
                }
                legacyHistory
            }
        }
    }

    suspend fun persistHistorySnapshot(history: List<ThreadHistoryEntry>) {
        mutex.withLock {
            withContext(AppDispatchers.io) {
                persistHistorySnapshotLocked(history)
            }
        }
    }

    private suspend fun readLegacyHistory(
        readLegacyHistoryJson: suspend () -> String?
    ): List<ThreadHistoryEntry> {
        val raw = readLegacyHistoryJson() ?: return emptyList()
        return withContext(AppDispatchers.parsing) {
            decodeAppStateHistory(raw, json, tag)
        }
    }

    private suspend fun readHistoryFromManifest(
        manifest: AppStateHistoryFileManifest
    ): List<ThreadHistoryEntry> {
        if (manifest.orderedEntries.isEmpty()) return emptyList()
        return manifest.orderedEntries.mapNotNull { entry ->
            val path = historyEntryPath(entry.key)
            val raw = fileSystem.readString(path).getOrElse { error ->
                Logger.w(tag, "Missing split history entry '${entry.key}': ${error.message}")
                return@mapNotNull null
            }
            cachedEntryContentHashes[entry.key] = fnv1a64Hex(raw)
            runCatching {
                json.decodeFromString(ThreadHistoryEntry.serializer(), raw)
            }.onFailure { error ->
                Logger.e(tag, "Failed to decode split history entry '${entry.key}'", error)
            }.getOrNull()
        }
    }

    private suspend fun persistHistorySnapshotLocked(history: List<ThreadHistoryEntry>) {
        fileSystem.createDirectory(HISTORY_FILE_STORE_DIR).getOrThrow()
        fileSystem.createDirectory(HISTORY_FILE_STORE_ENTRIES_DIR).getOrThrow()

        val previousManifest = readManifestOrNull()
        val previousKeys = previousManifest?.orderedEntries.orEmpty().map { it.key }
        val manifestEntries = history
            .mapIndexedNotNull { index, entry ->
                val identity = historyEntryIdentity(entry).ifBlank {
                    buildFallbackHistoryIdentity(index, entry)
                }
                val key = historyFileKey(identity)
                val path = historyEntryPath(key)
                val encoded = json.encodeToString(ThreadHistoryEntry.serializer(), entry)
                val contentHash = fnv1a64Hex(encoded)
                if (cachedEntryContentHashes[key] != contentHash && readCurrentEntryHash(path) != contentHash) {
                    fileSystem.writeString(path, encoded).getOrThrow()
                }
                cachedEntryContentHashes[key] = contentHash
                AppStateHistoryFileManifestEntry(key = key, identity = identity)
            }
            .distinctBy { it.key }

        val nextKeys = manifestEntries.map { it.key }
        val shouldWriteManifest = previousManifest == null || previousKeys != nextKeys
        if (shouldWriteManifest) {
            val nextManifest = AppStateHistoryFileManifest(
                revision = (previousManifest?.revision ?: 0L) + 1L,
                orderedEntries = manifestEntries
            )
            val encodedManifest = json.encodeToString(AppStateHistoryFileManifest.serializer(), nextManifest)
            fileSystem.writeString(HISTORY_FILE_STORE_MANIFEST_PATH, encodedManifest).getOrThrow()
            fileSystem.writeString(HISTORY_FILE_STORE_MANIFEST_BACKUP_PATH, encodedManifest)
                .onFailure { error ->
                    Logger.w(tag, "Failed to update split history manifest backup: ${error.message}")
                }
        }

        deleteStaleHistoryEntries(
            previousKeys = previousKeys.toSet(),
            activeKeys = manifestEntries.mapTo(mutableSetOf()) { it.key }
        )
        _changes.value = _changes.value + 1L
    }

    private suspend fun readCurrentEntryHash(path: String): String? {
        val raw = fileSystem.readString(path).getOrNull() ?: return null
        return fnv1a64Hex(raw)
    }

    private suspend fun deleteStaleHistoryEntries(
        previousKeys: Set<String>,
        activeKeys: Set<String>
    ) {
        previousKeys
            .filterNot { it in activeKeys }
            .forEach { key ->
                fileSystem.delete(historyEntryPath(key)).onFailure { error ->
                    Logger.w(tag, "Failed to delete stale split history entry '$key': ${error.message}")
                }
                cachedEntryContentHashes.remove(key)
            }
    }

    private suspend fun readManifestOrNull(): AppStateHistoryFileManifest? {
        return readManifestFile(HISTORY_FILE_STORE_MANIFEST_PATH)
            ?: readManifestFile(HISTORY_FILE_STORE_MANIFEST_BACKUP_PATH)
    }

    private suspend fun readManifestFile(path: String): AppStateHistoryFileManifest? {
        if (!fileSystem.exists(path)) return null
        val raw = fileSystem.readString(path).getOrElse { error ->
            Logger.w(tag, "Failed to read split history manifest '$path': ${error.message}")
            return null
        }
        return runCatching {
            json.decodeFromString(AppStateHistoryFileManifest.serializer(), raw)
        }.onFailure { error ->
            Logger.e(tag, "Failed to decode split history manifest '$path'", error)
        }.getOrNull()
    }

    private fun historyEntryPath(key: String): String {
        return "$HISTORY_FILE_STORE_ENTRIES_DIR/$key.json"
    }

    private fun buildFallbackHistoryIdentity(index: Int, entry: ThreadHistoryEntry): String {
        val encoded = json.encodeToString(ThreadHistoryEntry.serializer(), entry)
        return "entry::$index::${fnv1a64Hex(encoded)}"
    }
}

private fun historyFileKey(identity: String): String {
    val prefix = identity
        .lowercase()
        .map { char ->
            when {
                char in 'a'..'z' -> char
                char in '0'..'9' -> char
                else -> '_'
            }
        }
        .joinToString("")
        .trim('_')
        .take(HISTORY_FILE_KEY_PREFIX_MAX_LENGTH)
        .ifBlank { "entry" }
    return "$prefix-${fnv1a64Hex(identity)}"
}

private fun fnv1a64Hex(value: String): String {
    var hash = -0x340d631b7bdddcdbL
    value.encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
