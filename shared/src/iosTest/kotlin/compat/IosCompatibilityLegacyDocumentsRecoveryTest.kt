package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.state.SettingsRecoveryNotices
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * L94: an old toshiaki database in Documents that cannot be read used to be left in place while the app started
 * from an empty profile without a word, and the next launch deleted it as an already migrated leftover.
 */
class IosCompatibilityLegacyDocumentsRecoveryTest {
    @Test
    fun anUnreadableOldDocumentsDatabaseIsKeptAndToldInsteadOfDeleted() = runBlocking {
        val fileSystem = createFileSystem()
        val legacyDirectory = "${fileSystem.getAppDataDirectory().trimEnd('/')}/compatibility"
        val legacyDatabase = "$legacyDirectory/compatibility.db"
        val kept = "compatibility/unreadable_compatibility_database-5000.db"
        // Not an SQLite file: the header is wrong, so every read reports SQLITE_NOTADB.
        val unreadable = ByteArray(8_192) { index -> (index * 31 + 7).toByte() }
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        fileSystem.deleteRecursively(legacyDirectory).getOrThrow()
        while (SettingsRecoveryNotices.notice.value != null) SettingsRecoveryNotices.acknowledge()
        try {
            fileSystem.writeBytes(legacyDatabase, unreadable).getOrThrow()

            IosCompatibilityStore(fileSystem, nowMillis = { 5_000L }).initialize()

            assertTrue(fileSystem.exists(kept), "The unreadable old database is kept in private storage")
            assertContentEquals(unreadable, fileSystem.readBytes(kept).getOrThrow())
            val notice = fileSystem.readString(UNREADABLE_COMPATIBILITY_NOTICE_PATH).getOrThrow()
            assertTrue(notice.contains("unreadable_compatibility_database-5000.db"), notice)
            assertEquals(UNREADABLE_COMPATIBILITY_NOTICE_TITLE, SettingsRecoveryNotices.notice.value?.title)

            // The next launch removes the Documents leftovers of a migration; the kept copy stays.
            IosCompatibilityStore(fileSystem, nowMillis = { 6_000L }).initialize()
            assertTrue(fileSystem.exists(kept), "The kept copy survives the next launch")
            assertContentEquals(unreadable, fileSystem.readBytes(kept).getOrThrow())
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
            fileSystem.deleteRecursively(legacyDirectory).getOrThrow()
            while (SettingsRecoveryNotices.notice.value != null) SettingsRecoveryNotices.acknowledge()
        }
    }

    @Test
    fun anOldDocumentsDatabaseThatCannotBeOpenedFailsTheStartInsteadOfStartingEmpty() = runBlocking {
        val fileSystem = createFileSystem()
        val legacyDirectory = "${fileSystem.getAppDataDirectory().trimEnd('/')}/compatibility"
        // A path SQLite cannot open (SQLITE_CANTOPEN), like a file the system keeps locked: not damage, so it
        // must be tried again later rather than replaced by an empty profile and removed on the next launch.
        val blocked = "$legacyDirectory/compatibility.db/kept.txt"
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        fileSystem.deleteRecursively(legacyDirectory).getOrThrow()
        try {
            fileSystem.writeString(blocked, "keep").getOrThrow()

            assertFails { IosCompatibilityStore(fileSystem, nowMillis = { 5_000L }).initialize() }
            assertFails { IosCompatibilityStore(fileSystem, nowMillis = { 6_000L }).initialize() }

            assertEquals("keep", fileSystem.readString(blocked).getOrThrow())
            assertTrue(!fileSystem.exists("compatibility/compatibility.db"), "No empty profile replaced it")
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
            fileSystem.deleteRecursively(legacyDirectory).getOrThrow()
            while (SettingsRecoveryNotices.notice.value != null) SettingsRecoveryNotices.acknowledge()
        }
    }
}
