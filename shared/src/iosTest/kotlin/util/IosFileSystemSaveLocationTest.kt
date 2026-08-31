@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.SaveLocation
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkCreationWithSecurityScope
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * iOSの保存先はAndroid SAFではなくDocuments配下のPathまたはsecurity-scoped
 * bookmarkで扱う。ここでは通常保存・互換保存が共有するPath経路を、実際の
 * Simulator filesystemに対して検証する。
 */
class IosFileSystemSaveLocationTest {
    @Test
    fun pathSaveLocationSupportsStreamingRoundTripAndRejectsTraversal() = runBlocking {
        val fileSystem = createFileSystem()
        val base = SaveLocation.Path("ios_save_location_test")
        try {
            fileSystem.createDirectory(base).getOrThrow()
            fileSystem.createDirectory(base, "threads").getOrThrow()

            fileSystem.writeByteStream(base, "threads/chunks.bin") { sink ->
                sink.write(byteArrayOf(9, 1, 2, 9), offset = 1, length = 2)
                sink.write(byteArrayOf(3))
            }.getOrThrow()
            fileSystem.appendBytes(base, "threads/chunks.bin", byteArrayOf(4, 5)).getOrThrow()

            assertContentEquals(
                byteArrayOf(1, 2, 3, 4, 5),
                fileSystem.readBytes(base, "threads/chunks.bin").getOrThrow()
            )
            assertTrue(fileSystem.exists(base, "threads/chunks.bin"))
            assertTrue("chunks.bin" in fileSystem.listFiles(base, "threads"))
            assertTrue(fileSystem.getFileSize(base, "threads/chunks.bin") == 5L)

            val traversal = fileSystem.writeString(base, "../outside.txt", "must not escape")
            assertTrue(traversal.isFailure, "SaveLocation must reject a path outside its base directory.")
            assertFalse(fileSystem.exists(base, "../outside.txt"))
            val absoluteChild = fileSystem.writeString(base, "/tmp/futacha-outside.txt", "must not escape")
            assertTrue(absoluteChild.isFailure, "SaveLocation must reject an absolute child path.")
            assertFalse(fileSystem.exists(base, "/tmp/futacha-outside.txt"))

            fileSystem.delete(base, "threads/chunks.bin").getOrThrow()
            assertFalse(fileSystem.exists(base, "threads/chunks.bin"))
        } finally {
            fileSystem.delete(base).getOrThrow()
        }
    }

    @Test
    fun securityScopedBookmarkSaveLocationRoundTripsWithinTheSelectedDirectory() = runBlocking {
        val fileSystem = createFileSystem()
        val basePath = fileSystem.resolveAbsolutePath("ios_bookmark_save_location_test")
        fileSystem.createDirectory(SaveLocation.Path(basePath)).getOrThrow()
        try {
            val bookmark = securityScopedBookmark(basePath)
            fileSystem.createDirectory(bookmark, "thread").getOrThrow()
            fileSystem.writeString(bookmark, "thread/metadata.json", "{\"saved\":true}").getOrThrow()

            // Recreate the iOS file-system facade before resolving the persisted
            // bookmark. This mirrors the next app launch rather than relying on
            // the instance that created the file.
            val reopenedFileSystem = createFileSystem()
            assertTrue(reopenedFileSystem.exists(bookmark, "thread/metadata.json"))
            assertTrue(
                reopenedFileSystem.readString(bookmark, "thread/metadata.json").getOrThrow().contains("\"saved\":true")
            )
            assertTrue(reopenedFileSystem.delete(bookmark, "thread/metadata.json").isSuccess)
            assertFalse(reopenedFileSystem.exists(bookmark, "thread/metadata.json"))
        } finally {
            fileSystem.delete(SaveLocation.Path(basePath)).getOrThrow()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun securityScopedBookmark(path: String): SaveLocation.Bookmark = memScoped {
        val error = alloc<ObjCObjectVar<platform.Foundation.NSError?>>()
        val data = checkNotNull(
            NSURL.fileURLWithPath(path).bookmarkDataWithOptions(
                options = NSURLBookmarkCreationWithSecurityScope,
                includingResourceValuesForKeys = null,
                relativeToURL = null,
                error = error.ptr
            )
        ) { "Failed to create a Simulator security-scoped bookmark: ${error.value?.localizedDescription}" }
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        SaveLocation.Bookmark(Base64.encode(bytes))
    }
}
