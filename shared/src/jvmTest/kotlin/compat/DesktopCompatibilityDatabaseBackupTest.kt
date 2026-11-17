package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An unreadable database (too large a payload, a damaged file) is copied aside before it is replaced. */
class DesktopCompatibilityDatabaseBackupTest {
    private val directory = Files.createTempDirectory("compat-db-backup").toFile()
    private val fs = JvmFileSystem(directory)

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun backupKeepsTheDatabaseAndItsPayloadReadable() = runBlocking {
        DesktopCompatibilityDatabase(fs).apply {
            writePayload("""{"boards":[]}""", 1L)
            close()
        }
        val database = DesktopCompatibilityDatabase(fs)
        val backup = database.backupStorage(stampMillis = 42L)
        assertEquals("compatibility/unreadable_compatibility_db-42.db", backup)
        val copy = File(directory, backup!!)
        assertTrue(copy.isFile && copy.length() > 0L)

        // Replacing the storage leaves the copy behind, and the copy still holds the payload.
        database.deleteStorage()
        assertFalse(File(directory, "compatibility/compatibility.db").exists())
        assertTrue(copy.isFile)
        val restored = File(directory, "compatibility/restored.db")
        copy.copyTo(restored)
        val fromCopy = java.sql.DriverManager.getConnection("jdbc:sqlite:${restored.absolutePath}").use { db ->
            db.createStatement().use { s ->
                s.executeQuery("SELECT payload FROM compat_state WHERE id=1").use { r -> if (r.next()) r.getString(1) else null }
            }
        }
        assertEquals("""{"boards":[]}""", fromCopy)
    }

    @Test
    fun nothingToBackUpWhenThereIsNoDatabaseFile() {
        assertNull(DesktopCompatibilityDatabase(fs).backupStorage(stampMillis = 1L))
    }
}
