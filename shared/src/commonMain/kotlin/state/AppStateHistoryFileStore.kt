package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
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
private const val HISTORY_FILE_STORE_MAX_ENTRIES = 20_000
private const val HISTORY_FILE_STORE_MAX_MANIFEST_BYTES = 8L * 1024L * 1024L
private const val HISTORY_FILE_STORE_MAX_ENTRY_BYTES = 2L * 1024L * 1024L
private const val HISTORY_FILE_STORE_MAX_KEY_LENGTH = 96
private const val HISTORY_FILE_STORE_MAX_IDENTITY_LENGTH = 8_192

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
    private var cachedSnapshot: List<ThreadHistoryEntry>? = null

    suspend fun readHistorySnapshot(
        readLegacyHistoryJson: suspend () -> String?
    ): List<ThreadHistoryEntry> {
        return mutex.withLock {
            cachedSnapshot?.let { return@withLock it }
            withContext(AppDispatchers.io) {
                val manifest = readManifestOrNull()
                if (manifest != null) {
                    return@withContext readHistoryFromManifest(manifest).also { cachedSnapshot = it }
                }
                val legacyHistory = readLegacyHistory(readLegacyHistoryJson)
                    // No manifest and no legacy preference means initialization has not
                    // necessarily finished yet. Do not cache this provisional empty value:
                    // seedIfEmpty may publish the legacy JSON immediately afterwards.
                    ?: return@withContext emptyList()
                if (legacyHistory.isNotEmpty()) {
                    runSuspendCatchingPreservingCancellation {
                        persistHistorySnapshotLocked(legacyHistory)
                    }.onFailure { error ->
                        Logger.e(tag, "Failed to migrate legacy history JSON into split store", error)
                    }
                }
                legacyHistory.also { cachedSnapshot = it }
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
    ): List<ThreadHistoryEntry>? {
        val raw = readLegacyHistoryJson() ?: return null
        return withContext(AppDispatchers.parsing) {
            decodeAppStateHistory(raw, json, tag)
        }
    }

    private suspend fun readHistoryFromManifest(
        manifest: AppStateHistoryFileManifest
    ): List<ThreadHistoryEntry> {
        if (manifest.orderedEntries.isEmpty()) return emptyList()
        if (manifest.orderedEntries.size > HISTORY_FILE_STORE_MAX_ENTRIES) {
            Logger.e(tag, "Split history manifest contains too many entries", null)
            return emptyList()
        }
        return manifest.orderedEntries.mapNotNull { entry ->
            val path = historyEntryPath(entry.key)
            val raw = readBoundedString(path, HISTORY_FILE_STORE_MAX_ENTRY_BYTES).getOrElse { error ->
                Logger.w(tag, "Invalid split history entry '${entry.key}': ${error.message}")
                return@mapNotNull null
            }
            cachedEntryContentHashes[entry.key] = fnv1a64Hex(raw)
            withContext(AppDispatchers.parsing) {
                runCatching {
                    json.decodeFromString(ThreadHistoryEntry.serializer(), raw)
                }
            }.onFailure { error ->
                Logger.e(tag, "Failed to decode split history entry '${entry.key}'", error)
            }.getOrNull()
        }
    }

    private suspend fun persistHistorySnapshotLocked(history: List<ThreadHistoryEntry>) {
        require(history.size <= HISTORY_FILE_STORE_MAX_ENTRIES) {
            "History contains too many entries"
        }
        fileSystem.createDirectory(HISTORY_FILE_STORE_DIR).getOrThrow()
        fileSystem.createDirectory(HISTORY_FILE_STORE_ENTRIES_DIR).getOrThrow()

        val previousManifest = readManifestOrNull()
        val previousKeys = previousManifest?.orderedEntries.orEmpty().map { it.key }
        val previousEntriesByKey = cachedSnapshot.orEmpty().mapIndexedNotNull { index, entry ->
            val identity = historyEntryIdentity(entry).ifBlank {
                buildFallbackHistoryIdentity(index, entry)
            }
            historyFileKey(identity) to entry
        }.toMap()
        val manifestEntries = history
            .mapIndexedNotNull { index, entry ->
                val identity = historyEntryIdentity(entry).ifBlank {
                    buildFallbackHistoryIdentity(index, entry)
                }
                val key = historyFileKey(identity)
                val path = historyEntryPath(key)
                // The common scroll path changes one history row at a time.
                // Reuse known-equal entries instead of serializing the entire
                // history list merely to rediscover that all other hashes are
                // unchanged.
                if (previousEntriesByKey[key] != entry || cachedEntryContentHashes[key] == null) {
                    val encoded = json.encodeToString(ThreadHistoryEntry.serializer(), entry)
                    require(encoded.encodeToByteArray().size.toLong() <= HISTORY_FILE_STORE_MAX_ENTRY_BYTES) {
                        "History entry is too large"
                    }
                    val contentHash = fnv1a64Hex(encoded)
                    if (cachedEntryContentHashes[key] != contentHash && readCurrentEntryHash(path) != contentHash) {
                        fileSystem.writeString(path, encoded).getOrThrow()
                    }
                    cachedEntryContentHashes[key] = contentHash
                }
                AppStateHistoryFileManifestEntry(key = key, identity = identity)
            }
            .distinctBy { it.key }

        val nextKeys = manifestEntries.map { it.key }
        val shouldWriteManifest = history.isEmpty() || previousManifest == null || previousKeys != nextKeys
        if (shouldWriteManifest) {
            val nextManifest = AppStateHistoryFileManifest(
                revision = (previousManifest?.revision ?: 0L) + 1L,
                orderedEntries = manifestEntries
            )
            val encodedManifest = json.encodeToString(AppStateHistoryFileManifest.serializer(), nextManifest)
            require(encodedManifest.encodeToByteArray().size.toLong() <= HISTORY_FILE_STORE_MAX_MANIFEST_BYTES) {
                "History manifest is too large"
            }
            fileSystem.writeString(HISTORY_FILE_STORE_MANIFEST_PATH, encodedManifest).getOrThrow()
            val backupWrite = fileSystem.writeString(HISTORY_FILE_STORE_MANIFEST_BACKUP_PATH, encodedManifest)
            if (history.isEmpty()) {
                // A stale backup is a recovery source. Clearing is not durable
                // until both manifests explicitly point at the empty set.
                backupWrite.getOrThrow()
            } else {
                backupWrite.onFailure { error ->
                    Logger.w(tag, "Failed to update split history manifest backup: ${error.message}")
                }
            }
        }

        if (history.isEmpty()) {
            deleteAllHistoryEntryFiles()
        } else {
            deleteStaleHistoryEntries(
                previousKeys = previousKeys.toSet(),
                activeKeys = manifestEntries.mapTo(mutableSetOf()) { it.key }
            )
        }
        cachedSnapshot = history.toList()
        _changes.value = _changes.value + 1L
    }

    private suspend fun readCurrentEntryHash(path: String): String? {
        val raw = readBoundedString(path, HISTORY_FILE_STORE_MAX_ENTRY_BYTES).getOrNull() ?: return null
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

    private suspend fun deleteAllHistoryEntryFiles() {
        val recursiveDelete = fileSystem.deleteRecursively(HISTORY_FILE_STORE_ENTRIES_DIR)
        if (recursiveDelete.isSuccess) {
            cachedEntryContentHashes.clear()
            return
        }
        recursiveDelete.exceptionOrNull()?.let { error ->
            Logger.w(tag, "Failed to recursively delete split history entries: ${error.message}")
        }
        // Best-effort fallback for providers that cannot remove a directory
        // recursively but still support deleting its direct children.
        val fileNames = runSuspendCatchingPreservingCancellation {
            fileSystem.listFiles(HISTORY_FILE_STORE_ENTRIES_DIR)
        }.onFailure { error ->
            Logger.w(tag, "Failed to enumerate split history entries during clear: ${error.message}")
        }.getOrDefault(emptyList())
        fileNames.asSequence().take(HISTORY_FILE_STORE_MAX_ENTRIES * 2).forEach { fileName ->
            fileSystem.delete("$HISTORY_FILE_STORE_ENTRIES_DIR/$fileName")
                .onFailure { error ->
                    Logger.w(tag, "Failed to delete split history orphan '$fileName': ${error.message}")
                }
        }
        cachedEntryContentHashes.clear()
    }

    private suspend fun readManifestOrNull(): AppStateHistoryFileManifest? {
        return readManifestFile(HISTORY_FILE_STORE_MANIFEST_PATH)
            ?: readManifestFile(HISTORY_FILE_STORE_MANIFEST_BACKUP_PATH)
    }

    private suspend fun readManifestFile(path: String): AppStateHistoryFileManifest? {
        if (!fileSystem.exists(path)) return null
        val raw = readBoundedString(path, HISTORY_FILE_STORE_MAX_MANIFEST_BYTES).getOrElse { error ->
            Logger.w(tag, "Failed to read split history manifest '$path': ${error.message}")
            return null
        }
        return withContext(AppDispatchers.parsing) {
            runCatching {
                json.decodeFromString(AppStateHistoryFileManifest.serializer(), raw)
            }
        }.onFailure { error ->
            Logger.e(tag, "Failed to decode split history manifest '$path'", error)
        }.getOrNull()?.takeIf { manifest ->
            val entries = manifest.orderedEntries
            entries.size <= HISTORY_FILE_STORE_MAX_ENTRIES &&
                entries.all { entry ->
                    entry.key.length in 1..HISTORY_FILE_STORE_MAX_KEY_LENGTH &&
                        '/' !in entry.key && '\\' !in entry.key &&
                        entry.identity.length <= HISTORY_FILE_STORE_MAX_IDENTITY_LENGTH
                } &&
                entries.mapTo(hashSetOf()) { it.key }.size == entries.size
        }
    }

    private suspend fun readBoundedString(path: String, maxBytes: Long): Result<String> {
        return runSuspendCatchingPreservingCancellation {
            val size = fileSystem.getFileSize(path)
            require(size in 0L..maxBytes) { "File exceeds its permitted size" }
            val raw = fileSystem.readString(path).getOrThrow()
            require(raw.encodeToByteArray().size.toLong() <= maxBytes) {
                "File grew beyond its permitted size while reading"
            }
            raw
        }
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
