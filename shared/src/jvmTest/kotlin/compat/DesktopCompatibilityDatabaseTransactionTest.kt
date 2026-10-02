package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopCompatibilityDatabaseTransactionTest {
    /** Records calls and fails rollback, like a busy or already-aborted SQLite transaction. */
    private class RecordingConnection(val rollbackFails: Boolean) {
        val calls = mutableListOf<String>()
        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setAutoCommit" -> { calls += "setAutoCommit(${args!![0]})"; null }
                "commit" -> { calls += "commit"; null }
                "rollback" -> {
                    calls += "rollback"
                    if (rollbackFails) throw SQLException("cannot rollback")
                    null
                }
                "close" -> { calls += "close"; null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Connection
    }

    @Test
    fun failedRollbackClosesTheConnectionInsteadOfCommittingThePartialWrite() {
        val recording = RecordingConnection(rollbackFails = true)
        var discarded = false
        val failure = IllegalStateException("write failed")
        val thrown = assertFailsWith<IllegalStateException> {
            runDesktopDatabaseTransaction(recording.connection, discard = { discarded = true }) {
                throw failure
            }
        }
        assertSame(failure, thrown)
        assertTrue(thrown.suppressed.any { it is SQLException })
        assertTrue(discarded)
        // Re-enabling auto-commit (sqlite-jdbc runs "commit;") must never happen here.
        assertEquals(listOf("setAutoCommit(false)", "rollback", "close"), recording.calls)
    }

    @Test
    fun successfulRollbackRestoresAutoCommitAndKeepsTheConnection() {
        val recording = RecordingConnection(rollbackFails = false)
        var discarded = false
        assertFailsWith<IllegalStateException> {
            runDesktopDatabaseTransaction(recording.connection, discard = { discarded = true }) {
                error("write failed")
            }
        }
        assertFalse(discarded)
        assertEquals(listOf("setAutoCommit(false)", "rollback", "setAutoCommit(true)"), recording.calls)
    }

    @Test
    fun successfulBlockCommitsBeforeRestoringAutoCommit() {
        val recording = RecordingConnection(rollbackFails = false)
        runDesktopDatabaseTransaction(recording.connection, discard = {}) { }
        assertEquals(listOf("setAutoCommit(false)", "commit", "setAutoCommit(true)"), recording.calls)
    }

    @Test
    fun aFailureInTheMiddleOfABatchLeavesNoRowsBehind() {
        val directory = Files.createTempDirectory("compat-db-tx").toFile()
        try {
            val database = DesktopCompatibilityDatabase(JvmFileSystem(directory))
            database.writeImagePhashes(mapOf("kept" to "0123456789abcdef"), usedAtMillis = 1L)
            // The second hash exceeds the column bound after the first row was inserted.
            assertFailsWith<IllegalArgumentException> {
                database.writeImagePhashes(
                    linkedMapOf("partial" to "fedcba9876543210", "oversized" to "x".repeat(65)),
                    usedAtMillis = 2L
                )
            }
            database.close()
            val file = directory.walkTopDown().first { it.name == "compatibility.db" }
            DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
                db.createStatement().use { statement ->
                    statement.executeQuery("SELECT key FROM compat_image_phash ORDER BY key").use { rows ->
                        val keys = buildList { while (rows.next()) add(rows.getString(1)) }
                        assertEquals(listOf("kept"), keys)
                    }
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
