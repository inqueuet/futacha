package com.valoser.futacha.shared.ai

import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopAiFileWriteTest {
    @Test fun replaceRetriesWhileAnotherProcessHoldsTheUsageFile() {
        val directory = Files.createTempDirectory("futacha-ai-write").toFile()
        try {
            val usage = File(directory, "openai-usage-v1.json").apply { writeText("old") }
            var moves = 0
            writeDesktopAiFile(usage, "new".encodeToByteArray(), move = { source, target, options ->
                // Windows: a scanner holds the file for the first two renames.
                if (++moves <= 2) throw AccessDeniedException(target.toString())
                Files.move(source, target, *options)
            })
            assertEquals(3, moves)
            assertEquals("new", usage.readText())
            assertTrue(directory.listFiles().orEmpty().all { it.name == usage.name }, "temporary file must be removed")
        } finally { directory.deleteRecursively() }
    }

    @Test fun persistentFailureKeepsThePreviousFileAndRemovesTheTemporary() {
        val directory = Files.createTempDirectory("futacha-ai-write-fail").toFile()
        try {
            val cache = File(directory, "openai-analysis-v1.json").apply { writeText("old") }
            assertFailsWith<AccessDeniedException> {
                writeDesktopAiFile(cache, "new".encodeToByteArray(), move = { _, target, _ -> throw AccessDeniedException(target.toString()) })
            }
            assertEquals("old", cache.readText())
            assertTrue(directory.listFiles().orEmpty().all { it.name == cache.name })
        } finally { directory.deleteRecursively() }
    }
}
