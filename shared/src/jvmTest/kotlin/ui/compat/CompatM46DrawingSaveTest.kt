package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class CompatM46DrawingSaveTest {
    private val drawing = ImageData(byteArrayOf(1, 2, 3, 4), "drawing.png", isHandwriting = true)

    @Test fun replyAndBuildKeepTheirTextAndPersistTheReferencedPayloadBeforeCopying() = runBlocking {
        for (build in listOf(false, true)) fixture(build) { store, fs, origin, old, root ->
            var copyObserved = false
            val tracked = object : FileSystem by fs {
                override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
                    val locator = checkNotNull(locator(store, origin))
                    assertNotEquals(old, locator)
                    assertContentEquals(drawing.bytes, fs.readBytes(locator).getOrThrow())
                    copyObserved = true
                    return fs.writeBytes(base, relativePath, bytes)
                }
            }
            saveCompatDrawingToDraft(store, tracked, origin, drawing, 9)
            coroutineScope {
                launchCompatDrawingCopy(this, tracked, SaveLocation.Path(root.resolve("copies").path), drawing, 9) { fail("copy failed") }
            }
            assertTrue(copyObserved)
            assertFalse(fs.exists(old))
            assertPreservedDraft(store, origin)
            val copied = root.resolve("copies").listFiles().orEmpty().single()
            assertContentEquals(drawing.bytes, copied.readBytes())
        }
    }

    @Test fun aDraftFailureRemovesOnlyTheUnreferencedNewPayloadAndDoesNotCreateACopy() = runBlocking<Unit> {
        for (build in listOf(false, true)) fixture(build) { store, fs, origin, old, root ->
            val written = mutableListOf<String>()
            val tracked = object : FileSystem by fs {
                override suspend fun writeBytes(path: String, bytes: ByteArray): Result<Unit> {
                    written += path
                    return fs.writeBytes(path, bytes)
                }
            }
            val failed = failingStore(store, afterCommit = false)
            assertFailsWith<IOException> {
                saveCompatDrawingToDraft(failed, tracked, origin, drawing, 9)
            }
            assertEquals(old, locator(store, origin))
            assertTrue(fs.exists(old))
            assertEquals(1, written.size)
            assertFalse(fs.exists(written.single()))
            assertFalse(root.resolve("copies").exists())
        }
    }

    @Test fun aFailureAfterCommitKeepsTheReferencedPngAndRetryStillCreatesOnlyOneCopy() = runBlocking<Unit> {
        for (build in listOf(false, true)) fixture(build) { store, fs, origin, _, root ->
            assertFailsWith<IOException> {
                saveCompatDrawingToDraft(failingStore(store, afterCommit = true), fs, origin, drawing, 9)
            }
            val committed = checkNotNull(locator(store, origin))
            assertContentEquals(drawing.bytes, fs.readBytes(committed).getOrThrow())
            assertFalse(root.resolve("copies").exists())
            saveCompatDrawingToDraft(store, fs, origin, drawing, 10)
            coroutineScope {
                launchCompatDrawingCopy(this, fs, SaveLocation.Path(root.resolve("copies").path), drawing, 10) { fail("copy failed") }
            }
            assertEquals(1, root.resolve("copies").listFiles().orEmpty().size)
            assertContentEquals(drawing.bytes, fs.readBytes(checkNotNull(locator(store, origin))).getOrThrow())
        }
    }

    @Test fun cancellationBeforeTheDraftCommitPropagatesAndPreservesThePreviousAttachment() = runBlocking<Unit> {
        for (build in listOf(false, true)) fixture(build) { store, fs, origin, old, root ->
            val cancelled = object : CompatibilityStore by store {
                override suspend fun saveDraft(draft: CompatReplyDraft): Unit = throw CancellationException("test cancellation")
                override suspend fun saveBuildDraft(draft: CompatBuildDraft): Unit = throw CancellationException("test cancellation")
            }
            assertFailsWith<CancellationException> {
                saveCompatDrawingToDraft(cancelled, fs, origin, drawing, 9)
            }
            assertEquals(old, locator(store, origin))
            assertTrue(fs.exists(old))
            assertFalse(root.resolve("copies").exists())
        }
    }

    @Test fun anOptionalCopyFailureReturnsAWarningAndKeepsTheSuccessfullySavedDraft() = runBlocking {
        fixture(false) { store, fs, origin, old, root ->
            val failedCopy = object : FileSystem by fs {
                override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> =
                    Result.failure(IOException("test copy failure"))
            }
            saveCompatDrawingToDraft(store, failedCopy, origin, drawing, 9)
            var failed = false
            coroutineScope {
                launchCompatDrawingCopy(this, failedCopy, SaveLocation.Path(root.resolve("copies").path), drawing, 9) { failed = true }
            }
            assertTrue(failed)
            assertContentEquals(drawing.bytes, fs.readBytes(checkNotNull(locator(store, origin))).getOrThrow())
            assertFalse(fs.exists(old))
            assertPreservedDraft(store, origin)
        }
    }

    /** L39: the attachment is saved without waiting for the person's copy, which is written afterwards. */
    @Test fun theAttachmentDoesNotWaitForTheCopy() = runBlocking {
        fixture(false) { store, fs, origin, _, root ->
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val delayed = object : FileSystem by fs {
                override suspend fun writeBytes(base: SaveLocation, relativePath: String, bytes: ByteArray): Result<Unit> {
                    started.complete(Unit)
                    release.await()
                    return fs.writeBytes(base, relativePath, bytes)
                }
            }
            withTimeout(5_000) { saveCompatDrawingToDraft(store, delayed, origin, drawing, 9) }
            assertContentEquals(drawing.bytes, fs.readBytes(checkNotNull(locator(store, origin))).getOrThrow())
            coroutineScope {
                val copy = checkNotNull(launchCompatDrawingCopy(this, delayed, SaveLocation.Path(root.resolve("copies").path),
                    drawing, 9) { fail("copy failed") })
                withTimeout(5_000) { started.await() }
                assertFalse(copy.isCompleted)
                release.complete(Unit)
            }
            assertEquals(1, root.resolve("copies").listFiles().orEmpty().size)
        }
    }

    private fun failingStore(store: CompatibilityStore, afterCommit: Boolean) = object : CompatibilityStore by store {
        override suspend fun saveDraft(draft: CompatReplyDraft) {
            if (afterCommit) store.saveDraft(draft)
            throw IOException("test draft failure")
        }
        override suspend fun saveBuildDraft(draft: CompatBuildDraft) {
            if (afterCommit) store.saveBuildDraft(draft)
            throw IOException("test draft failure")
        }
    }
    private suspend fun locator(store: CompatibilityStore, origin: CompatHost): String? = when (origin) {
        is CompatHost.Post -> store.loadDraft(origin.tabKey)?.attachmentUri
        is CompatHost.PostBuild -> store.loadBuildDraft(origin.boardKey)?.attachmentUri
        else -> null
    }
    private suspend fun assertPreservedDraft(store: CompatibilityStore, origin: CompatHost) {
        val fields = when (origin) {
            is CompatHost.Post -> checkNotNull(store.loadDraft(origin.tabKey)).let {
                assertTrue(it.attachmentIsHandwriting); listOf(it.name, it.email, it.subject, it.comment, it.deleteKey)
            }
            is CompatHost.PostBuild -> checkNotNull(store.loadBuildDraft(origin.boardKey)).let {
                assertTrue(it.attachmentIsHandwriting); listOf(it.name, it.email, it.subject, it.comment, it.deleteKey)
            }
            else -> error("invalid fixture origin")
        }
        assertEquals(listOf("name", "email", "subject", "comment", "key"), fields)
    }
    private suspend fun fixture(build: Boolean,
        block: suspend (DesktopCompatibilityStore, FileSystem, CompatHost, String, java.io.File) -> Unit) {
        val root = Files.createTempDirectory("futacha-m46-drawing").toFile()
        val fs = JvmFileSystem(root)
        val store = DesktopCompatibilityStore(fs)
        val url = "https://may.2chan.net/b/"
        val thread = "${url}res/811.htm"
        val board = compatBoardKey(url)
        val tab = compatTabKey(thread)
        try {
            store.initialize()
            store.upsertBoard(CompatBoard(board, "fixture", url, url, 0))
            store.openTab(CompatTab(tab, thread, thread, board, "fixture", "811", "fixture",
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
            val old = persistCompatPostAttachment(fs, if (build) "build:$board" else tab, ImageData(byteArrayOf(9), "old.png")).getOrThrow()
            if (build) store.saveBuildDraft(CompatBuildDraft(board, "name", "email", "subject", "comment", old, false, "key", 1))
            else store.saveDraft(CompatReplyDraft(tab, "name", "email", "subject", "comment", old, false, "key", 1))
            block(store, fs, if (build) CompatHost.PostBuild(board) else CompatHost.Post(tab), old, root)
        } finally { store.close(); root.deleteRecursively() }
    }
}
