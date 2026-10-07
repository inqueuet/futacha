package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.FileSystem
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

internal const val MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES = 32 * 1024 * 1024
internal fun Throwable.isRecoverableDesktopCompatibilityDatabaseCorruption(): Boolean =
    this is SQLException && errorCode in setOf(11, 26)

/** Same state envelope and overlay transactions as the iOS implementation, through JDBC. */
private const val IMAGE_PHASH_CACHE_MAX_ENTRIES = 8_192
private const val IMAGE_PHASH_CACHE_TRIM_TO = 6_144

internal class DesktopCompatibilityDatabase(private val fileSystem: FileSystem) {
    private var connection: Connection? = null
    private fun open(): Connection = connection ?: run {
        val file = File(fileSystem.resolveAbsolutePath("compatibility/compatibility.db"))
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").also { db ->
            try {
                db.createStatement().use { s ->
                    s.execute("PRAGMA journal_mode=WAL")
                    s.execute("PRAGMA synchronous=FULL")
                    s.execute("PRAGMA busy_timeout=5000")
                    s.execute("CREATE TABLE IF NOT EXISTS compat_state(id INTEGER PRIMARY KEY CHECK(id=1), payload TEXT NOT NULL, updated_at INTEGER NOT NULL)")
                    s.execute("CREATE TABLE IF NOT EXISTS compat_scroll_anchor(tab_key TEXT PRIMARY KEY, anchor_payload TEXT NOT NULL, updated_at INTEGER NOT NULL)")
                    s.execute("CREATE TABLE IF NOT EXISTS compat_snapshot_access(tab_key TEXT PRIMARY KEY, accessed_at INTEGER NOT NULL)")
                    s.execute("CREATE TABLE IF NOT EXISTS compat_image_phash(key TEXT PRIMARY KEY, phash TEXT NOT NULL, used_at INTEGER NOT NULL)")
                    // Refetchable thread/catalog caches, one row each, so a settings change
                    // does not rewrite up to 32 MB of them with the profile row.
                    s.execute("CREATE TABLE IF NOT EXISTS compat_cache_record(key TEXT PRIMARY KEY, payload TEXT NOT NULL)")
                }
                connection = db
            } catch (failure: Throwable) { db.close(); throw failure }
        }
    }
    fun readPayload(): String? = open().createStatement().use { s ->
        s.executeQuery("SELECT payload FROM compat_state WHERE id=1").use { r ->
            if (r.next()) bounded(r.getString(1), MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES) else null
        }
    }
    fun readPendingScrollAnchors(): Map<String, String> = open().createStatement().use { s ->
        s.executeQuery("SELECT tab_key,anchor_payload FROM compat_scroll_anchor ORDER BY updated_at").use { r ->
            buildMap { while (r.next()) { require(size < 20_000); put(bounded(r.getString(1), 4096), bounded(r.getString(2), 65536)) } }
        }
    }
    fun readPendingSnapshotAccess(): Map<String, Long> = open().createStatement().use { s ->
        s.executeQuery("SELECT tab_key,accessed_at FROM compat_snapshot_access").use { r ->
            buildMap { while (r.next()) { require(size < 20_000); put(bounded(r.getString(1), 4096), r.getLong(2)) } }
        }
    }
    fun writeScrollAnchor(tabKey: String, anchorPayload: String, updatedAtMillis: Long) {
        open().prepareStatement("INSERT OR REPLACE INTO compat_scroll_anchor VALUES(?,?,?)").use { s ->
            s.setString(1, bounded(tabKey, 4096)); s.setString(2, bounded(anchorPayload, 65536)); s.setLong(3, updatedAtMillis); s.executeUpdate()
        }
    }
    fun writeSnapshotAccess(tabKey: String, accessedAtMillis: Long) {
        open().prepareStatement("INSERT OR REPLACE INTO compat_snapshot_access VALUES(?,?)").use { s ->
            s.setString(1, bounded(tabKey, 4096)); s.setLong(2, accessedAtMillis); s.executeUpdate()
        }
    }
    /** Reads cached image hashes and marks the hits as recently used. */
    fun readImagePhashes(keys: Collection<String>, usedAtMillis: Long): Map<String, String> {
        val found = open().prepareStatement("SELECT phash FROM compat_image_phash WHERE key = ?").use { s ->
            buildMap {
                keys.distinct().forEach { key ->
                    s.setString(1, key)
                    s.executeQuery().use { rows -> if (rows.next()) put(key, rows.getString(1)) }
                }
            }
        }
        if (found.isNotEmpty()) writeImagePhashes(found, usedAtMillis)
        return found
    }

    /** Upserts hashes and trims the table to its bound, least recently used first. */
    fun writeImagePhashes(entries: Map<String, String>, usedAtMillis: Long) {
        if (entries.isEmpty()) return
        inTransaction { db ->
            db.prepareStatement("INSERT OR REPLACE INTO compat_image_phash VALUES(?,?,?)").use { s ->
                entries.forEach { (key, phash) ->
                    s.setString(1, bounded(key, 4096)); s.setString(2, bounded(phash, 64)); s.setLong(3, usedAtMillis)
                    s.executeUpdate()
                }
            }
            db.createStatement().use { s ->
                s.executeUpdate(
                    "DELETE FROM compat_image_phash WHERE (SELECT COUNT(*) FROM compat_image_phash) > $IMAGE_PHASH_CACHE_MAX_ENTRIES " +
                        "AND key IN (SELECT key FROM compat_image_phash ORDER BY used_at ASC " +
                        "LIMIT (SELECT COUNT(*) FROM compat_image_phash) - $IMAGE_PHASH_CACHE_TRIM_TO)"
                )
            }
        }
    }

    /** Cache rows (key -> payload) and the keys of rows that must not be trusted. */
    class CacheRecordsRead(val records: Map<String, String>, val rejectedKeys: List<String>)

    /** Reads the cache rows; an oversized row or one past the total budget is reported as rejected. */
    fun readCacheRecords(): CacheRecordsRead = open().createStatement().use { s ->
        s.executeQuery("SELECT key,payload FROM compat_cache_record").use { r ->
            val records = LinkedHashMap<String, String>()
            val rejected = mutableListOf<String>()
            var bytes = 0L
            while (r.next()) {
                val key = r.getString(1)
                val payload = r.getString(2)
                val size = payload.toByteArray(Charsets.UTF_8).size
                if (key.toByteArray(Charsets.UTF_8).size > 4096 || size > MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES ||
                    bytes + size > MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES
                ) {
                    rejected += key
                } else {
                    bytes += size
                    records[key] = payload
                }
            }
            CacheRecordsRead(records, rejected)
        }
    }

    fun deleteCacheRecords(keys: Collection<String>) {
        if (keys.isEmpty()) return
        inTransaction { db ->
            db.prepareStatement("DELETE FROM compat_cache_record WHERE key = ?").use { s ->
                keys.forEach { key -> s.setString(1, key); s.executeUpdate() }
            }
        }
    }

    fun clearCacheRecords() {
        open().createStatement().use { s -> s.executeUpdate("DELETE FROM compat_cache_record") }
    }

    /**
     * Writes the profile row, and in the same transaction the changed cache rows
     * ([cacheUpdates]: a null payload deletes the row). [replaceCacheRecords] first
     * removes every cache row, for a profile whose rows are not known to match memory.
     */
    fun writePayload(
        payload: String,
        updatedAtMillis: Long,
        cacheUpdates: Map<String, String?> = emptyMap(),
        replaceCacheRecords: Boolean = false
    ) {
        bounded(payload, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES)
        cacheUpdates.forEach { (key, value) ->
            bounded(key, 4096)
            if (value != null) bounded(value, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES)
        }
        inTransaction { db ->
            db.prepareStatement("INSERT OR REPLACE INTO compat_state VALUES(1,?,?)").use { s ->
                s.setString(1, payload); s.setLong(2, updatedAtMillis); s.executeUpdate()
            }
            if (replaceCacheRecords) {
                db.createStatement().use { s -> s.executeUpdate("DELETE FROM compat_cache_record") }
            }
            if (cacheUpdates.isNotEmpty()) {
                db.prepareStatement("INSERT OR REPLACE INTO compat_cache_record VALUES(?,?)").use { upsert ->
                    db.prepareStatement("DELETE FROM compat_cache_record WHERE key = ?").use { delete ->
                        cacheUpdates.forEach { (key, value) ->
                            if (value == null) {
                                delete.setString(1, key); delete.executeUpdate()
                            } else {
                                upsert.setString(1, key); upsert.setString(2, value); upsert.executeUpdate()
                            }
                        }
                    }
                }
            }
            db.createStatement().use { s ->
                s.executeUpdate("DELETE FROM compat_scroll_anchor"); s.executeUpdate("DELETE FROM compat_snapshot_access")
            }
        }
    }
    private inline fun inTransaction(block: (Connection) -> Unit) {
        val db = open()
        runDesktopDatabaseTransaction(db, discard = { if (connection === db) connection = null }, block = block)
    }
    fun close() { connection?.close(); connection = null }
    /**
     * Copies the database files next to it (relative path of the main copy, or null when there is
     * nothing to copy). Called before [deleteStorage] discards a database that could not be read:
     * a payload over the size limit or a damaged file is still the user's data, and a copy can
     * be recovered by hand. Throws when a copy cannot be written, so the caller keeps the original.
     */
    fun backupStorage(stampMillis: Long): String? {
        close()
        val main = File(fileSystem.resolveAbsolutePath("compatibility/compatibility.db"))
        if (!main.isFile) return null
        val backupBase = "unreadable_compatibility_db-$stampMillis.db"
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(main.path + suffix)
            if (!source.isFile) continue
            java.nio.file.Files.copy(
                source.toPath(),
                File(main.parentFile, backupBase + suffix).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
        return "compatibility/$backupBase"
    }
    suspend fun deleteStorage() {
        close()
        for (suffix in listOf("", "-wal", "-shm")) fileSystem.delete("compatibility/compatibility.db$suffix").getOrThrow()
    }
    private fun bounded(value: String, limit: Int): String {
        require(value.toByteArray(Charsets.UTF_8).size <= limit) { "Compatibility database value is too large" }
        return value
    }
}

/**
 * Commits [block] atomically. sqlite-jdbc commits the open transaction when
 * auto-commit is re-enabled, so after a failed rollback the connection is
 * closed instead (SQLite discards the uncommitted transaction on close) and
 * the original failure is rethrown with the rollback failure suppressed.
 */
internal inline fun runDesktopDatabaseTransaction(
    db: Connection,
    noinline discard: () -> Unit,
    block: (Connection) -> Unit
) {
    db.autoCommit = false
    try {
        block(db)
        db.commit()
    } catch (failure: Throwable) {
        try {
            db.rollback()
        } catch (rollbackFailure: Throwable) {
            failure.addSuppressed(rollbackFailure)
            discard()
            runCatching { db.close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
        restoreAutoCommitOrDiscard(db, discard)?.let(failure::addSuppressed)
        throw failure
    }
    restoreAutoCommitOrDiscard(db, discard)?.let { throw it }
}

/** A connection whose auto-commit cannot be restored may hold an open transaction; drop it. */
internal fun restoreAutoCommitOrDiscard(db: Connection, discard: () -> Unit): Throwable? {
    val failure = runCatching { db.autoCommit = true }.exceptionOrNull() ?: return null
    discard()
    runCatching { db.close() }.exceptionOrNull()?.let(failure::addSuppressed)
    return failure
}
