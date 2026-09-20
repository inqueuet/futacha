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
    fun writePayload(payload: String, updatedAtMillis: Long) {
        bounded(payload, MAX_COMPATIBILITY_DATABASE_PAYLOAD_BYTES)
        val db = open(); db.autoCommit = false
        try {
            db.prepareStatement("INSERT OR REPLACE INTO compat_state VALUES(1,?,?)").use { s ->
                s.setString(1, payload); s.setLong(2, updatedAtMillis); s.executeUpdate()
            }
            db.createStatement().use { s ->
                s.executeUpdate("DELETE FROM compat_scroll_anchor"); s.executeUpdate("DELETE FROM compat_snapshot_access")
            }
            db.commit()
        } catch (failure: Throwable) { db.rollback(); throw failure }
        finally { db.autoCommit = true }
    }
    fun close() { connection?.close(); connection = null }
    suspend fun deleteStorage() {
        close()
        for (suffix in listOf("", "-wal", "-shm")) fileSystem.delete("compatibility/compatibility.db$suffix").getOrThrow()
    }
    private fun bounded(value: String, limit: Int): String {
        require(value.toByteArray(Charsets.UTF_8).size <= limit) { "Compatibility database value is too large" }
        return value
    }
}
