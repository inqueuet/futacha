package com.valoser.futacha.shared.util

import java.nio.file.AccessDeniedException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmFileSystemReplaceRetryTest {
    @Test
    fun replaceRetriesWhileAnotherProcessHoldsTheDestination() {
        val root = Files.createTempDirectory("futacha-replace-retry")
        try {
            val from = root.resolve("tmp_1.tmp").also { Files.writeString(it, "new") }
            val to = root.resolve("index.json").also { Files.writeString(it, "old") }
            var calls = 0
            val sleeps = mutableListOf<Long>()
            moveReplacingWithRetry(from, to, move = { source, target, options ->
                calls += 1
                // Windows: the indexer/antivirus has index.json open for the first two tries.
                if (calls <= 2) throw AccessDeniedException(target.toString())
                Files.move(source, target, *options)
            }, sleep = { sleeps += it })

            assertEquals(3, calls)
            assertEquals(2, sleeps.size)
            assertEquals("new", Files.readString(to))
            assertTrue(Files.notExists(from))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun sharingViolationIsRetriedAndAtomicFallbackStillApplies() {
        val root = Files.createTempDirectory("futacha-replace-retry")
        try {
            val from = root.resolve("tmp_1.tmp").also { Files.writeString(it, "new") }
            val to = root.resolve("history.json")
            val options = mutableListOf<List<CopyOption>>()
            var calls = 0
            moveReplacingWithRetry(from, to, move = { source, target, opts ->
                calls += 1
                options += opts.toList()
                when {
                    calls == 1 -> throw FileSystemException(target.toString(), null, "being used by another process")
                    opts.contains(StandardCopyOption.ATOMIC_MOVE) -> throw AtomicMoveNotSupportedException(null, null, null)
                    else -> Files.move(source, target, *opts)
                }
            }, sleep = {})

            assertEquals("new", Files.readString(to))
            assertEquals(listOf(StandardCopyOption.REPLACE_EXISTING), options.last())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun persistentLockFailsAfterBoundedRetriesKeepingBothFiles() {
        val root = Files.createTempDirectory("futacha-replace-retry")
        try {
            val from = root.resolve("tmp_1.tmp").also { Files.writeString(it, "new") }
            val to = root.resolve("cookies.json").also { Files.writeString(it, "old") }
            var calls = 0
            var slept = 0L
            assertFailsWith<AccessDeniedException> {
                moveReplacingWithRetry(from, to, move = { _: Path, target: Path, _: Array<out CopyOption> ->
                    calls += 1
                    throw AccessDeniedException(target.toString())
                }, sleep = { slept += it })
            }

            assertEquals(7, calls)
            assertTrue(slept in 1_000L..2_000L)
            assertEquals("new", Files.readString(from))
            assertEquals("old", Files.readString(to))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun errorsThatCannotClearAreNotRetried() {
        val root = Files.createTempDirectory("futacha-replace-retry")
        try {
            var calls = 0
            assertFailsWith<NoSuchFileException> {
                moveReplacingWithRetry(root.resolve("missing.tmp"), root.resolve("index.json"), move = { source, _, _ ->
                    calls += 1
                    throw NoSuchFileException(source.toString())
                }, sleep = { error("must not wait") })
            }
            assertEquals(1, calls)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
