package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals

/** A lookup rewrites a hit's use time only once it is stale, as on iOS (L84). */
class DesktopCompatibilityDatabaseImagePhashTest {
    @Test
    fun readsReturnEveryHitAndRefreshUseTimeOnlyWhenStale() {
        val root = Files.createTempDirectory("compat-phash-touch").toFile()
        val database = DesktopCompatibilityDatabase(JvmFileSystem(root))
        try {
            val stored = (0 until 300).associate { index ->
                "compat.imagePhash.key$index" to index.toString(16).padStart(16, '0')
            }
            database.writeImagePhashes(stored, 1_000L)

            val keys = stored.keys.toList() + listOf("compat.imagePhash.missing", "compat.imagePhash.key0")
            assertEquals(stored, database.readImagePhashes(keys, 1_000L + 60_000L))
            // A recent hit does not rewrite its row.
            assertEquals(1_000L, usedAt(root, "compat.imagePhash.key0"))

            val later = 1_000L + 2L * 60L * 60L * 1000L
            assertEquals(
                mapOf("compat.imagePhash.key1" to stored.getValue("compat.imagePhash.key1")),
                database.readImagePhashes(listOf("compat.imagePhash.key1"), later)
            )
            assertEquals(later, usedAt(root, "compat.imagePhash.key1"))
            assertEquals(1_000L, usedAt(root, "compat.imagePhash.key2"))

            // Profile commits still work after the touch.
            database.writePayload("{}", 5L)
            assertEquals("{}", database.readPayload())
        } finally { database.close(); root.deleteRecursively() }
    }

    private fun usedAt(root: File, key: String): Long? {
        val db = File(root, "compatibility/compatibility.db")
        return DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            connection.prepareStatement("SELECT used_at FROM compat_image_phash WHERE key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else null }
            }
        }
    }
}
