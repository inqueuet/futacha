package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class IosCompatibilityDatabaseImagePhashTest {
    @Test
    fun batchedReadsReturnEveryHitAndRefreshUseTimeOnlyWhenStale() = runBlocking {
        val fileSystem = createFileSystem()
        fileSystem.deleteRecursively("compatibility").getOrThrow()
        try {
            fileSystem.createDirectory("compatibility").getOrThrow()
            val database = IosCompatibilityDatabase(fileSystem)
            try {
                val stored = (0 until 300).associate { index ->
                    "compat.imagePhash.key$index" to index.toString(16).padStart(16, '0')
                }
                database.writeImagePhashes(stored, 1_000L)

                val keys = stored.keys.toList() + listOf("compat.imagePhash.missing", "compat.imagePhash.key0")
                assertEquals(stored, database.readImagePhashes(keys, 1_000L + 60_000L))
                // A recent hit does not rewrite its row.
                assertEquals(1_000L, database.imagePhashUsedAt("compat.imagePhash.key0"))

                val later = 1_000L + 2L * 60L * 60L * 1000L
                assertEquals(
                    mapOf("compat.imagePhash.key1" to stored.getValue("compat.imagePhash.key1")),
                    database.readImagePhashes(listOf("compat.imagePhash.key1"), later)
                )
                assertEquals(later, database.imagePhashUsedAt("compat.imagePhash.key1"))
                assertEquals(1_000L, database.imagePhashUsedAt("compat.imagePhash.key2"))

                // Profile commits still work after the relaxed hash transactions.
                database.writePayload("{}", 5L)
                assertEquals("{}", database.readPayload())
            } finally {
                database.close()
            }
        } finally {
            fileSystem.deleteRecursively("compatibility").getOrThrow()
        }
    }
}
