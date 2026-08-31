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

private const val MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES = 32 * 1024 * 1024
private const val MAX_COMPATIBILITY_DATABASE_OVERLAY_ROWS = 20_000
private const val MAX_COMPATIBILITY_DATABASE_TAB_KEY_BYTES = 4 * 1024
private const val MAX_COMPATIBILITY_DATABASE_ANCHOR_BYTES = 64 * 1024
private const val SQLITE_CORRUPT = 11
private const val SQLITE_NOTADB = 26

internal class IosCompatibilityDatabaseException(
    val sqliteCode: Int?,
    message: String
) : IllegalStateException(message)

internal fun Throwable.isRecoverableIosCompatibilityDatabaseCorruption(): Boolean =
    this is IosCompatibilityDatabaseException && sqliteCode in setOf(SQLITE_CORRUPT, SQLITE_NOTADB)

/**
 * Single-row SQLite envelope for the compatibility profile state.
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

    fun writePayload(payload: String, updatedAtMillis: Long) {
        requireUtf8Size(payload, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES, "Compatibility state")
        val db = open()
        execute(db, "BEGIN IMMEDIATE", "begin compatibility state transaction")
        try {
            val statement = prepare(
                db,
                "INSERT INTO compat_state(id, schema_version, payload, updated_at) VALUES(1, 8, ?, ?) " +
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
            execute(db, "COMMIT", "commit compatibility state transaction")
        } catch (error: Throwable) {
            runCatching { execute(db, "ROLLBACK", "rollback compatibility state transaction") }
            throw error
        }
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
            execute(db, "PRAGMA user_version=8", "set compatibility schema version")
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
