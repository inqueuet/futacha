package com.valoser.futacha.shared.util

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmFileSystemDeletionTest {
    @Test
    fun recursiveDeleteDoesNotFollowSymbolicLinks() = runBlocking {
        val fileSystem = createFileSystem()
        val suffix = System.nanoTime().toString()
        val tree = File(fileSystem.resolveAbsolutePath("delete-link-test-$suffix"))
        val external = File(System.getProperty("java.io.tmpdir"), "futacha-link-target-$suffix")
        val sentinel = File(external, "sentinel.txt")
        val link = File(tree, "outside")
        try {
            tree.mkdirs()
            external.mkdirs()
            sentinel.writeText("keep")
            runCatching { Files.createSymbolicLink(link.toPath(), external.toPath()) }
                .getOrElse { return@runBlocking }

            fileSystem.deleteRecursively("delete-link-test-$suffix").getOrThrow()

            assertFalse(tree.exists())
            assertTrue(sentinel.exists())
        } finally {
            Files.deleteIfExists(link.toPath())
            tree.deleteRecursively()
            external.deleteRecursively()
        }
    }
}
