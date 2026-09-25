package com.valoser.futacha.shared.ui.image

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SharedImageDiskCacheRegistryTest {
    private val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "futacha-shared-disk-cache-${Random.nextLong().toULong()}"

    @AfterTest
    fun cleanUp() {
        FileSystem.SYSTEM.deleteRecursively(directory)
    }

    @Test
    fun loadersOnTheSameDirectoryShareOneCacheUntilTheLastRelease() {
        val registry = SharedImageDiskCacheRegistry()
        val first = assertNotNull(registry.acquire(directory, 1_000_000L))
        // A replacement loader opened before the old one is shut down.
        val second = assertNotNull(registry.acquire("$directory/./".toPath(), 2_000_000L))
        assertSame(first, second)
        assertEquals(2, registry.holderCount(directory))

        registry.release(first)
        first.openEditor("still-open")?.abort()

        registry.release(second)
        assertEquals(0, registry.holderCount(directory))
        assertFailsWith<IllegalStateException> { first.openEditor("closed") }

        val reopened = assertNotNull(registry.acquire(directory, 2_000_000L))
        assertNotSame(first, reopened)
        assertEquals(2_000_000L, reopened.maxSize)
        registry.release(reopened)
    }
}
