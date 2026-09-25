@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.sqlite.SQLITE_DONE
import com.valoser.futacha.shared.sqlite.SQLITE_OK
import com.valoser.futacha.shared.sqlite.SQLITE_OPEN_CREATE
import com.valoser.futacha.shared.sqlite.SQLITE_OPEN_FULLMUTEX
import com.valoser.futacha.shared.sqlite.SQLITE_OPEN_READWRITE
import com.valoser.futacha.shared.sqlite.sqlite3_bind_int64
import com.valoser.futacha.shared.sqlite.sqlite3_bind_text
import com.valoser.futacha.shared.sqlite.sqlite3_close_v2
import com.valoser.futacha.shared.sqlite.sqlite3_column_text
import com.valoser.futacha.shared.sqlite.sqlite3_column_bytes
import com.valoser.futacha.shared.sqlite.sqlite3_column_int64
import com.valoser.futacha.shared.sqlite.sqlite3_exec
import com.valoser.futacha.shared.sqlite.sqlite3_finalize
import com.valoser.futacha.shared.sqlite.sqlite3_open_v2
import com.valoser.futacha.shared.sqlite.sqlite3_prepare_v2
import com.valoser.futacha.shared.sqlite.sqlite3_reset
import com.valoser.futacha.shared.sqlite.sqlite3_step
import com.valoser.futacha.shared.sqlite.SQLITE_ROW
import com.valoser.futacha.shared.util.FileSystem
import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value

internal const val MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES = 32 * 1024 * 1024
private const val MAX_COMPATIBILITY_DATABASE_OVERLAY_ROWS = 20_000
private const val MAX_COMPATIBILITY_DATABASE_STATE_RECORD_ROWS = 100_000
private const val MAX_COMPATIBILITY_DATABASE_STATE_KIND_BYTES = 64
private const val MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES = 4 * 1024
private const val MAX_COMPATIBILITY_DATABASE_ANCHOR_BYTES = 64 * 1024
private const val IMAGE_PHASH_CACHE_MAX_ENTRIES = 8_192
private const val IMAGE_PHASH_CACHE_TRIM_TO = 6_144
private const val IMAGE_PHASH_USE_REFRESH_MILLIS = 60L * 60L * 1000L
private const val SQLITE_CORRUPT = 11
private const val SQLITE_NOTADB = 26

internal class CompatibilityCacheRecordsRead(
    val records: Map<String, String>,
    val rejectedRowIds: List<Long>
)

/** Identifies one durable profile record kept outside the metadata payload. */
internal data class CompatibilityStateRecordKey(val kind: String, val key: String)

internal class CompatibilityStateRecord(val kind: String, val key: String, val payload: String)

/** State records in insertion (rowid) order, plus rows that exceeded their bounds. */
internal class CompatibilityStateRecordsRead(
    val records: List<CompatibilityStateRecord>,
    val rejectedRowIds: List<Long>
)

internal class IosCompatibilityDatabaseException(
    val sqliteCode: Int?,
    message: String
) : IllegalStateException(message)

internal fun Throwable.isRecoverableIosCompatibilityDatabaseCorruption(): Boolean =
    this is IosCompatibilityDatabaseException && sqliteCode in setOf(SQLITE_CORRUPT, SQLITE_NOTADB)

/**
 * SQLite profile metadata with independently updated thread and catalog cache records.
 *
 * CompatibilityStore already performs all model validation and publishes only
 * after a successful mutation.  Keeping its structured payload in a SQLite
 * transaction gives iOS durable commits, crash-safe replacement and future
 * schema migration without duplicating its well-tested domain rules in SQL.
 */
internal class IosCompatibilityDatabase(
    private val fileSystem: FileSystem,
    internal val storagePath: String = "compatibility/compatibility.db"
) {
    private var handle: CPointer<sqlite3>? = null
    // SQLITE_TRANSIENT is the documented sentinel which makes SQLite copy a
    // bound string before the Kotlin/Native C-string temporary can disappear.
    private val sqliteTransientDestructor = (-1L).toCPointer<CFunction<(COpaquePointer?) -> Unit>>()

    fun readPayload(): String? {
        val db = open()
        val statement = prepare(db, "SELECT payload FROM compat_state WHERE id = 1")
        return try {
            val stepCode = sqlite3_step(statement)
            when (stepCode) {
                SQLITE_ROW -> readBoundedColumnText(
                    statement,
                    0,
                    MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES,
                    "compatibility state"
                )
                SQLITE_DONE -> null
                else -> throw failure(db, "read compatibility state", stepCode)
            }
        } finally {
            sqlite3_finalize(statement)
        }
    }

    fun readPendingScrollAnchors(): Map<String, String> {
        val db = open()
        val statement = prepare(
            db,
            "SELECT tab_key, anchor_payload FROM compat_scroll_anchor ORDER BY updated_at"
        )
        return try {
            buildMap {
                var rowCount = 0
                while (true) {
                    val stepCode = sqlite3_step(statement)
                    when (stepCode) {
                        SQLITE_ROW -> {
                            rowCount += 1
                            require(rowCount <= MAX_COMPATIBILITY_DATABASE_OVERLAY_ROWS) {
                                "Compatibility scroll overlay contains too many rows"
                            }
                            val tabKey = readBoundedColumnText(
                                statement,
                                0,
                                MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES,
                                "compatibility scroll tab"
                            )
                                ?: continue
                            val payload = readBoundedColumnText(
                                statement,
                                1,
                                MAX_COMPATIBILITY_DATABASE_ANCHOR_BYTES,
                                "compatibility scroll anchor"
                            )
                                ?: continue
                            put(tabKey, payload)
                        }
                        SQLITE_DONE -> break
                        else -> throw failure(db, "read compatibility scroll anchors", stepCode)
                    }
                }
            }
        } finally {
            sqlite3_finalize(statement)
        }
    }

    fun writeScrollAnchor(tabKey: String, anchorPayload: String, updatedAtMillis: Long) {
        requireUtf8Size(tabKey, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "Compatibility scroll tab")
        requireUtf8Size(anchorPayload, MAX_COMPATIBILITY_DATABASE_ANCHOR_BYTES, "Compatibility scroll anchor")
        val db = open()
        val statement = prepare(
            db,
            "INSERT INTO compat_scroll_anchor(tab_key, anchor_payload, updated_at) VALUES(?, ?, ?) " +
                "ON CONFLICT(tab_key) DO UPDATE SET anchor_payload = excluded.anchor_payload, " +
                "updated_at = excluded.updated_at"
        )
        try {
            checkSqlite(
                sqlite3_bind_text(statement, 1, tabKey, -1, sqliteTransientDestructor),
                db,
                "bind compatibility scroll tab"
            )
            checkSqlite(
                sqlite3_bind_text(statement, 2, anchorPayload, -1, sqliteTransientDestructor),
                db,
                "bind compatibility scroll anchor"
            )
            checkSqlite(
                sqlite3_bind_int64(statement, 3, updatedAtMillis),
                db,
                "bind compatibility scroll timestamp"
            )
            checkSqlite(sqlite3_step(statement), db, "write compatibility scroll anchor", expected = SQLITE_DONE)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    fun readPendingSnapshotAccess(): Map<String, Long> {
        val db = open()
        val statement = prepare(
            db,
            "SELECT tab_key, accessed_at FROM compat_snapshot_access"
        )
        return try {
            buildMap {
                var rowCount = 0
                while (true) {
                    val stepCode = sqlite3_step(statement)
                    when (stepCode) {
                        SQLITE_ROW -> {
                            rowCount += 1
                            require(rowCount <= MAX_COMPATIBILITY_DATABASE_OVERLAY_ROWS) {
                                "Compatibility snapshot access overlay contains too many rows"
                            }
                            val tabKey = readBoundedColumnText(
                                statement,
                                0,
                                MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES,
                                "compatibility snapshot access tab"
                            )
                                ?: continue
                            put(tabKey, sqlite3_column_int64(statement, 1))
                        }
                        SQLITE_DONE -> break
                        else -> throw failure(db, "read compatibility snapshot access", stepCode)
                    }
                }
            }
        } finally {
            sqlite3_finalize(statement)
        }
    }

    fun writeSnapshotAccess(tabKey: String, accessedAtMillis: Long) {
        requireUtf8Size(tabKey, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "Compatibility snapshot access tab")
        val db = open()
        val statement = prepare(
            db,
            "INSERT INTO compat_snapshot_access(tab_key, accessed_at) VALUES(?, ?) " +
                "ON CONFLICT(tab_key) DO UPDATE SET accessed_at = excluded.accessed_at"
        )
        try {
            checkSqlite(
                sqlite3_bind_text(statement, 1, tabKey, -1, sqliteTransientDestructor),
                db,
                "bind compatibility snapshot access tab"
            )
            checkSqlite(
                sqlite3_bind_int64(statement, 2, accessedAtMillis),
                db,
                "bind compatibility snapshot access timestamp"
            )
            checkSqlite(sqlite3_step(statement), db, "write compatibility snapshot access", expected = SQLITE_DONE)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    /**
     * Reads cached image hashes and marks the hits as recently used.
     *
     * One statement serves every key, and a hit's use time is rewritten only
     * once it is [IMAGE_PHASH_USE_REFRESH_MILLIS] old: bumping every hit made
     * each scroll through a thread a durable write transaction.
     */
    fun readImagePhashes(keys: Collection<String>, usedAtMillis: Long): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        val db = open()
        val found = LinkedHashMap<String, String>()
        val stale = mutableListOf<String>()
        val statement = prepare(db, "SELECT phash, used_at FROM compat_image_phash WHERE key = ?")
        try {
            keys.distinct().forEach { key ->
                if (key.encodeToByteArray().size > MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES) return@forEach
                sqlite3_reset(statement)
                checkSqlite(
                    sqlite3_bind_text(statement, 1, key, -1, sqliteTransientDestructor),
                    db,
                    "bind compatibility image hash key"
                )
                when (val stepCode = sqlite3_step(statement)) {
                    SQLITE_ROW -> readBoundedColumnText(statement, 0, 64, "compatibility image hash")?.let { phash ->
                        found[key] = phash
                        if (usedAtMillis - sqlite3_column_int64(statement, 1) >= IMAGE_PHASH_USE_REFRESH_MILLIS) {
                            stale += key
                        }
                    }
                    SQLITE_DONE -> Unit
                    else -> throw failure(db, "read compatibility image hash", stepCode)
                }
            }
        } finally {
            sqlite3_finalize(statement)
        }
        if (stale.isNotEmpty()) touchImagePhashes(db, stale, usedAtMillis)
        return found
    }

    private fun touchImagePhashes(db: CPointer<sqlite3>, keys: List<String>, usedAtMillis: Long) {
        withRelaxedDurability(db) {
            execute(db, "BEGIN IMMEDIATE", "begin compatibility image hash touch")
            try {
                val statement = prepare(db, "UPDATE compat_image_phash SET used_at = ? WHERE key = ?")
                try {
                    keys.forEach { key ->
                        sqlite3_reset(statement)
                        checkSqlite(sqlite3_bind_int64(statement, 1, usedAtMillis), db, "bind image hash use time")
                        checkSqlite(sqlite3_bind_text(statement, 2, key, -1, sqliteTransientDestructor), db, "bind image hash key")
                        checkSqlite(sqlite3_step(statement), db, "touch compatibility image hash", expected = SQLITE_DONE)
                    }
                } finally {
                    sqlite3_finalize(statement)
                }
                execute(db, "COMMIT", "commit compatibility image hash touch")
            } catch (error: Throwable) {
                runCatching { execute(db, "ROLLBACK", "roll back compatibility image hash touch") }
                throw error
            }
        }
    }

    /** Upserts hashes and trims the table to its bound, least recently used first. */
    fun writeImagePhashes(entries: Map<String, String>, usedAtMillis: Long) {
        if (entries.isEmpty()) return
        entries.keys.forEach { key ->
            requireUtf8Size(key, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "Compatibility image hash key")
        }
        val db = open()
        withRelaxedDurability(db) {
            execute(db, "BEGIN IMMEDIATE", "begin compatibility image hash write")
            try {
                val statement = prepare(
                    db,
                    "INSERT INTO compat_image_phash(key, phash, used_at) VALUES(?, ?, ?) " +
                        "ON CONFLICT(key) DO UPDATE SET phash = excluded.phash, used_at = excluded.used_at"
                )
                try {
                    entries.forEach { (key, phash) ->
                        sqlite3_reset(statement)
                        checkSqlite(sqlite3_bind_text(statement, 1, key, -1, sqliteTransientDestructor), db, "bind image hash key")
                        checkSqlite(sqlite3_bind_text(statement, 2, phash, -1, sqliteTransientDestructor), db, "bind image hash")
                        checkSqlite(sqlite3_bind_int64(statement, 3, usedAtMillis), db, "bind image hash use time")
                        checkSqlite(sqlite3_step(statement), db, "write compatibility image hash", expected = SQLITE_DONE)
                    }
                } finally {
                    sqlite3_finalize(statement)
                }
                execute(
                    db,
                    "DELETE FROM compat_image_phash WHERE (SELECT COUNT(*) FROM compat_image_phash) > " +
                        "$IMAGE_PHASH_CACHE_MAX_ENTRIES AND key IN (SELECT key FROM compat_image_phash " +
                        "ORDER BY used_at ASC LIMIT (SELECT COUNT(*) FROM compat_image_phash) - $IMAGE_PHASH_CACHE_TRIM_TO)",
                    "trim compatibility image hash cache"
                )
                execute(db, "COMMIT", "commit compatibility image hash write")
            } catch (error: Throwable) {
                runCatching { execute(db, "ROLLBACK", "roll back compatibility image hash write") }
                throw error
            }
        }
    }

    /** Test hook: the stored use time of one image hash, or null. */
    internal fun imagePhashUsedAt(key: String): Long? {
        val db = open()
        val statement = prepare(db, "SELECT used_at FROM compat_image_phash WHERE key = ?")
        return try {
            checkSqlite(sqlite3_bind_text(statement, 1, key, -1, sqliteTransientDestructor), db, "bind image hash key")
            when (val stepCode = sqlite3_step(statement)) {
                SQLITE_ROW -> sqlite3_column_int64(statement, 0)
                SQLITE_DONE -> null
                else -> throw failure(db, "read compatibility image hash use time", stepCode)
            }
        } finally {
            sqlite3_finalize(statement)
        }
    }

    /**
     * Runs a transaction of recomputable image-hash rows with synchronous=NORMAL.
     * In WAL mode that still survives an app crash; only a power loss can undo
     * the last commits, which merely costs a recomputation. Profile commits
     * keep the connection's FULL durability.
     */
    private inline fun withRelaxedDurability(db: CPointer<sqlite3>, block: () -> Unit) {
        execute(db, "PRAGMA synchronous=NORMAL", "relax compatibility image hash durability")
        var succeeded = false
        try {
            block()
            succeeded = true
        } finally {
            val restored = sqlite3_exec(db, "PRAGMA synchronous=FULL", null, null, null)
            // Never leave later profile commits with relaxed durability: if
            // FULL cannot be restored, drop the connection so open() sets it.
            if (restored != SQLITE_OK) {
                close()
                if (succeeded) {
                    throw IosCompatibilityDatabaseException(restored, "iOS compatibility database failed to restore durable commits")
                }
            }
        }
    }

    /**
     * Commits one profile change atomically.
     *
     * @param payload the metadata row, or null when it did not change. Only a
     *   written payload clears the scroll/access overlays, because only then
     *   does the durable metadata already contain their latest values.
     * @param stateRecordUpdates durable profile records (preferences, archive
     *   outbox, dropped catalog items) to upsert, or delete when null.
     * @param replaceStateRecords deletes every state record first, for the
     *   first write after migrating them out of an old metadata payload.
     */
    fun writePayload(
        payload: String?,
        updatedAtMillis: Long,
        cacheUpdates: Map<String, String?> = emptyMap(),
        stateRecordUpdates: Map<CompatibilityStateRecordKey, String?> = emptyMap(),
        replaceStateRecords: Boolean = false
    ) {
        if (payload == null && cacheUpdates.isEmpty() && stateRecordUpdates.isEmpty() && !replaceStateRecords) return
        payload?.let { requireUtf8Size(it, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "Compatibility state") }
        stateRecordUpdates.forEach { (recordKey, value) ->
            requireUtf8Size(recordKey.kind, MAX_COMPATIBILITY_DATABASE_STATE_KIND_BYTES, "State record kind")
            requireUtf8Size(recordKey.key, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "State record key")
            value?.let { requireUtf8Size(it, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "State record") }
        }
        val db = open()
        execute(db, "BEGIN IMMEDIATE", "begin compatibility state transaction")
        try {
            cacheUpdates.forEach { (key, value) ->
                val cacheStatement = prepare(db, if (value == null)
                    "DELETE FROM compat_cache_record WHERE key = ?" else
                    "INSERT INTO compat_cache_record(key, payload) VALUES(?, ?) " +
                        "ON CONFLICT(key) DO UPDATE SET payload = excluded.payload")
                try {
                    requireUtf8Size(key, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "Cache key")
                    checkSqlite(sqlite3_bind_text(cacheStatement, 1, key, -1, sqliteTransientDestructor), db, "bind cache key")
                    if (value != null) {
                        requireUtf8Size(value, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "Cache record")
                        checkSqlite(sqlite3_bind_text(cacheStatement, 2, value, -1, sqliteTransientDestructor), db, "bind cache record")
                    }
                    checkSqlite(sqlite3_step(cacheStatement), db, "write cache record", expected = SQLITE_DONE)
                } finally {
                    sqlite3_finalize(cacheStatement)
                }
            }
            if (replaceStateRecords) {
                execute(db, "DELETE FROM compat_state_record", "replace compatibility state records")
            }
            if (stateRecordUpdates.isNotEmpty()) writeStateRecords(db, stateRecordUpdates)
            if (payload != null) {
                val statement = prepare(
                    db,
                    "INSERT INTO compat_state(id, schema_version, payload, updated_at) VALUES(1, 10, ?, ?) " +
                        "ON CONFLICT(id) DO UPDATE SET schema_version = excluded.schema_version, " +
                        "payload = excluded.payload, updated_at = excluded.updated_at"
                )
                try {
                    checkSqlite(
                        sqlite3_bind_text(statement, 1, payload, -1, sqliteTransientDestructor),
                        db,
                        "bind compatibility state"
                    )
                    checkSqlite(sqlite3_bind_int64(statement, 2, updatedAtMillis), db, "bind compatibility timestamp")
                    checkSqlite(sqlite3_step(statement), db, "write compatibility state", expected = SQLITE_DONE)
                } finally {
                    sqlite3_finalize(statement)
                }
                // The full payload now contains every latest anchor. Clear the
                // tiny write-ahead overlay in the same transaction so a crash can
                // never replay an older scroll position over a newer payload.
                execute(db, "DELETE FROM compat_scroll_anchor", "clear compatibility scroll overlay")
                execute(db, "DELETE FROM compat_snapshot_access", "clear compatibility snapshot access overlay")
            }
            execute(db, "COMMIT", "commit compatibility state transaction")
        } catch (error: Throwable) {
            runCatching { execute(db, "ROLLBACK", "rollback compatibility state transaction") }
            throw error
        }
    }

    private fun writeStateRecords(db: CPointer<sqlite3>, updates: Map<CompatibilityStateRecordKey, String?>) {
        val upsert = prepare(
            db,
            "INSERT INTO compat_state_record(kind, key, payload) VALUES(?, ?, ?) " +
                "ON CONFLICT(kind, key) DO UPDATE SET payload = excluded.payload"
        )
        try {
            val delete = prepare(db, "DELETE FROM compat_state_record WHERE kind = ? AND key = ?")
            try {
                updates.forEach { (recordKey, value) ->
                    val statement = if (value == null) delete else upsert
                    sqlite3_reset(statement)
                    checkSqlite(sqlite3_bind_text(statement, 1, recordKey.kind, -1, sqliteTransientDestructor), db, "bind state record kind")
                    checkSqlite(sqlite3_bind_text(statement, 2, recordKey.key, -1, sqliteTransientDestructor), db, "bind state record key")
                    if (value != null) {
                        checkSqlite(sqlite3_bind_text(statement, 3, value, -1, sqliteTransientDestructor), db, "bind state record")
                    }
                    checkSqlite(sqlite3_step(statement), db, "write state record", expected = SQLITE_DONE)
                }
            } finally {
                sqlite3_finalize(delete)
            }
        } finally {
            sqlite3_finalize(upsert)
        }
    }

    /**
     * Reads the durable profile records in insertion order. A row that is
     * oversized or exceeds the row/byte budget is reported in
     * [CompatibilityStateRecordsRead.rejectedRowIds] instead of being loaded.
     */
    fun readStateRecords(): CompatibilityStateRecordsRead {
        val db = open()
        val statement = prepare(db, "SELECT rowid, kind, key, payload FROM compat_state_record ORDER BY rowid")
        return try {
            val records = mutableListOf<CompatibilityStateRecord>()
            val rejectedRowIds = mutableListOf<Long>()
            var bytes = 0L
            while (true) {
                when (val code = sqlite3_step(statement)) {
                    SQLITE_DONE -> break
                    SQLITE_ROW -> {
                        val rowId = sqlite3_column_int64(statement, 0)
                        val kindBytes = sqlite3_column_bytes(statement, 1)
                        val keyBytes = sqlite3_column_bytes(statement, 2)
                        val payloadBytes = sqlite3_column_bytes(statement, 3)
                        val withinBudget = records.size < MAX_COMPATIBILITY_DATABASE_STATE_RECORD_ROWS &&
                            kindBytes in 0..MAX_COMPATIBILITY_DATABASE_STATE_KIND_BYTES &&
                            keyBytes in 0..MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES &&
                            payloadBytes in 0..MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES &&
                            bytes + payloadBytes <= MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES
                        val kind = if (withinBudget) {
                            readBoundedColumnText(statement, 1, MAX_COMPATIBILITY_DATABASE_STATE_KIND_BYTES, "State record kind")
                        } else null
                        val key = kind?.let {
                            readBoundedColumnText(statement, 2, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "State record key")
                        }
                        val value = key?.let {
                            readBoundedColumnText(statement, 3, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "State record")
                        }
                        if (kind == null || key == null || value == null) {
                            rejectedRowIds += rowId
                        } else {
                            bytes += payloadBytes
                            records += CompatibilityStateRecord(kind, key, value)
                        }
                    }
                    else -> throw failure(db, "read state records", code)
                }
            }
            CompatibilityStateRecordsRead(records, rejectedRowIds)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    /** Deletes state records by key and by rowid in one transaction. */
    fun deleteStateRecords(keys: Collection<CompatibilityStateRecordKey>, rowIds: Collection<Long> = emptyList()) {
        if (keys.isEmpty() && rowIds.isEmpty()) return
        val db = open()
        execute(db, "BEGIN IMMEDIATE", "begin compatibility state record cleanup")
        try {
            if (keys.isNotEmpty()) writeStateRecords(db, keys.associateWith { null })
            val statement = prepare(db, "DELETE FROM compat_state_record WHERE rowid = ?")
            try {
                rowIds.forEach { rowId ->
                    sqlite3_reset(statement)
                    checkSqlite(sqlite3_bind_int64(statement, 1, rowId), db, "bind state record rowid")
                    checkSqlite(sqlite3_step(statement), db, "delete state record row", expected = SQLITE_DONE)
                }
            } finally {
                sqlite3_finalize(statement)
            }
            execute(db, "COMMIT", "commit compatibility state record cleanup")
        } catch (error: Throwable) {
            runCatching { execute(db, "ROLLBACK", "roll back compatibility state record cleanup") }
            throw error
        }
    }

    /** Test hook: when the metadata row was last written, or null. */
    internal fun payloadUpdatedAt(): Long? {
        val db = open()
        val statement = prepare(db, "SELECT updated_at FROM compat_state WHERE id = 1")
        return try {
            when (val stepCode = sqlite3_step(statement)) {
                SQLITE_ROW -> sqlite3_column_int64(statement, 0)
                SQLITE_DONE -> null
                else -> throw failure(db, "read compatibility state time", stepCode)
            }
        } finally {
            sqlite3_finalize(statement)
        }
    }

    /**
     * Reads the refetchable cache rows. A row that is oversized or exceeds the
     * row/byte budget is reported in [CompatibilityCacheRecordsRead.rejectedRowIds]
     * instead of failing the whole profile, so the caller can drop it.
     */
    fun readCacheRecords(): CompatibilityCacheRecordsRead {
        val db = open()
        val statement = prepare(db, "SELECT rowid, key, payload FROM compat_cache_record")
        return try {
            val records = mutableMapOf<String, String>()
            val rejectedRowIds = mutableListOf<Long>()
            var bytes = 0L
            while (true) {
                when (val code = sqlite3_step(statement)) {
                    SQLITE_DONE -> break
                    SQLITE_ROW -> {
                        val rowId = sqlite3_column_int64(statement, 0)
                        val keyBytes = sqlite3_column_bytes(statement, 1)
                        val payloadBytes = sqlite3_column_bytes(statement, 2)
                        val withinBudget = records.size < MAX_COMPATIBILITY_DATABASE_OVERLAY_ROWS &&
                            keyBytes in 0..MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES &&
                            payloadBytes in 0..MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES &&
                            bytes + payloadBytes <= MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES
                        val key = if (withinBudget) readBoundedColumnText(
                            statement, 1, MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES, "Cache key"
                        ) else null
                        val value = key?.let {
                            readBoundedColumnText(statement, 2, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "Cache record")
                        }
                        if (key == null || value == null) {
                            rejectedRowIds += rowId
                        } else {
                            bytes += payloadBytes
                            records[key] = value
                        }
                    }
                    else -> throw failure(db, "read cache records", code)
                }
            }
            CompatibilityCacheRecordsRead(records, rejectedRowIds)
        } finally {
            sqlite3_finalize(statement)
        }
    }

    /** Deletes refetchable cache rows by key and by rowid in one transaction. */
    fun deleteCacheRecords(keys: Collection<String>, rowIds: Collection<Long> = emptyList()) {
        if (keys.isEmpty() && rowIds.isEmpty()) return
        val db = open()
        execute(db, "BEGIN IMMEDIATE", "begin compatibility cache cleanup")
        try {
            keys.forEach { key ->
                val statement = prepare(db, "DELETE FROM compat_cache_record WHERE key = ?")
                try {
                    checkSqlite(sqlite3_bind_text(statement, 1, key, -1, sqliteTransientDestructor), db, "bind cache key")
                    checkSqlite(sqlite3_step(statement), db, "delete cache record", expected = SQLITE_DONE)
                } finally {
                    sqlite3_finalize(statement)
                }
            }
            rowIds.forEach { rowId ->
                val statement = prepare(db, "DELETE FROM compat_cache_record WHERE rowid = ?")
                try {
                    checkSqlite(sqlite3_bind_int64(statement, 1, rowId), db, "bind cache rowid")
                    checkSqlite(sqlite3_step(statement), db, "delete cache row", expected = SQLITE_DONE)
                } finally {
                    sqlite3_finalize(statement)
                }
            }
            execute(db, "COMMIT", "commit compatibility cache cleanup")
        } catch (error: Throwable) {
            runCatching { execute(db, "ROLLBACK", "roll back compatibility cache cleanup") }
            throw error
        }
    }

    fun clearCacheRecords() {
        execute(open(), "DELETE FROM compat_cache_record", "clear compatibility cache records")
    }

    fun close() {
        handle?.let { sqlite3_close_v2(it) }
        handle = null
    }

    suspend fun deleteStorage() {
        close()
        listOf(storagePath, "$storagePath-wal", "$storagePath-shm").forEach { path ->
            fileSystem.delete(path).getOrThrow()
        }
    }

    private fun readBoundedColumnText(
        statement: CPointer<sqlite3_stmt>,
        column: Int,
        maxBytes: Int,
        label: String
    ): String? {
        val byteCount = sqlite3_column_bytes(statement, column)
        require(byteCount in 0..maxBytes) { "$label exceeds its permitted size" }
        return sqlite3_column_text(statement, column)
            ?.reinterpret<ByteVar>()
            ?.toKString()
    }

    private fun requireUtf8Size(value: String, maxBytes: Int, label: String) {
        require(value.encodeToByteArray().size <= maxBytes) { "$label exceeds its permitted size" }
    }

    private fun open(): CPointer<sqlite3> {
        handle?.let { return it }
        val path = fileSystem.resolveAbsolutePath(storagePath)
        val db: CPointer<sqlite3> = memScoped {
            val out = alloc<CPointerVarOf<CPointer<sqlite3>>>()
            val result = sqlite3_open_v2(
                path,
                out.ptr,
                SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_FULLMUTEX,
                null
            )
            if (result != SQLITE_OK || out.value == null) {
                out.value?.let { sqlite3_close_v2(it) }
                throw IosCompatibilityDatabaseException(
                    result,
                    "iOS compatibility database open failed (code=$result)"
                )
            }
            out.value!!
        }
        return try {
            execute(db, "PRAGMA foreign_keys=ON", "enable compatibility foreign keys")
            execute(db, "PRAGMA journal_mode=WAL", "enable compatibility write-ahead log")
            execute(db, "PRAGMA synchronous=FULL", "enable compatibility durable commits")
            execute(db, "PRAGMA secure_delete=ON", "enable compatibility secure delete")
            execute(
                db,
                "CREATE TABLE IF NOT EXISTS compat_state(" +
                    "id INTEGER PRIMARY KEY CHECK(id = 1), " +
                    "schema_version INTEGER NOT NULL, " +
                    "payload TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL)",
                "create compatibility state table"
            )
            execute(
                db,
                "CREATE TABLE IF NOT EXISTS compat_scroll_anchor(" +
                    "tab_key TEXT PRIMARY KEY, " +
                    "anchor_payload TEXT NOT NULL, " +
                    "updated_at INTEGER NOT NULL)",
                "create compatibility scroll overlay table"
            )
            execute(
                db,
                "CREATE TABLE IF NOT EXISTS compat_snapshot_access(" +
                    "tab_key TEXT PRIMARY KEY, " +
                    "accessed_at INTEGER NOT NULL)",
                "create compatibility snapshot access overlay table"
            )
            execute(
                db,
                "CREATE TABLE IF NOT EXISTS compat_image_phash(" +
                    "key TEXT PRIMARY KEY, " +
                    "phash TEXT NOT NULL, " +
                    "used_at INTEGER NOT NULL)",
                "create compatibility image hash cache table"
            )
            execute(db, "CREATE TABLE IF NOT EXISTS compat_cache_record(key TEXT PRIMARY KEY, payload TEXT NOT NULL)",
                "create compatibility cache records")
            execute(
                db,
                "CREATE TABLE IF NOT EXISTS compat_state_record(" +
                    "kind TEXT NOT NULL, key TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(kind, key))",
                "create compatibility state records"
            )
            execute(db, "PRAGMA user_version=10", "set compatibility schema version")
            handle = db
            db
        } catch (error: Throwable) {
            sqlite3_close_v2(db)
            throw error
        }
    }

    private fun prepare(db: CPointer<sqlite3>, sql: String) = memScoped {
        val out = alloc<CPointerVarOf<CPointer<sqlite3_stmt>>>()
        checkSqlite(sqlite3_prepare_v2(db, sql, -1, out.ptr, null), db, "prepare compatibility statement")
        out.value ?: throw failure(db, "prepare compatibility statement")
    }

    private fun execute(db: CPointer<sqlite3>, sql: String, operation: String) {
        checkSqlite(sqlite3_exec(db, sql, null, null, null), db, operation)
    }

    private fun checkSqlite(code: Int, db: CPointer<sqlite3>, operation: String, expected: Int = SQLITE_OK) {
        if (code != expected) throw failure(db, operation, code)
    }

    private fun failure(db: CPointer<sqlite3>, operation: String, code: Int? = null): IllegalStateException =
        IosCompatibilityDatabaseException(
            sqliteCode = code,
            message = "iOS compatibility database failed during $operation${code?.let { " (code=$it)" }.orEmpty()}"
        )
}
