@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.media.source

import coil3.disk.DiskCache
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import com.valoser.futacha.testing.video.VideoPlaybackFixtures
import kotlinx.cinterop.useContents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import okio.BufferedSink
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import kotlin.random.Random
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

class IosOriginalMediaAssetTest {
    @Test fun savedLocalWebmKeepsTheSourceAndCleansOnlyItsPlayerDirectory() = nativeTest {
        val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("saved-local-${Random.nextLong()}.webm")
        val bytes = VideoPlaybackFixtures.webm()
        FileSystem.SYSTEM.write(file) { write(bytes) }
        try {
            val document = IosLocalVideoDocument.createLocal(file.toString(), "webm")
            val pin = document.retain()
            try {
                val source = NSFileManager.defaultManager.attributesOfItemAtPath(file.toString(), null)
                val linked = NSFileManager.defaultManager.attributesOfItemAtPath(document.readAccessUrl.path + "/video.webm", null)
                assertNotNull(source?.get(NSFileSystemFileNumber))
                assertEquals(source?.get(NSFileSystemFileNumber), linked?.get(NSFileSystemFileNumber))
                val html = FileSystem.SYSTEM.read(requireNotNull(document.url.path).toPath()) { readUtf8() }
                assertTrue(html.contains("charset=\"utf-8\""))
                assertNotNull(document.webmInfo)
                document.close()
                assertFalse(document.cleanup.isCompleted)
            } finally { pin.close(); document.close(); withTimeout(5_000) { document.cleanup.await() } }
            assertContentEquals(bytes, FileSystem.SYSTEM.read(file) { readByteArray() })
            assertFalse(NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(document.readAccessUrl.path)))
        } finally { FileSystem.SYSTEM.delete(file, mustExist = false) }
    }

    @Test fun localWebmDocumentUsesAHardLinkAndLastViewPinOwnsCleanup() = nativeTest {
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("webkit-file-${Random.nextLong()}")
        val bytes = VideoPlaybackFixtures.webm()
        var calls = 0
        val store = OriginalMediaStore("webkit-file", {
            DiskCache.Builder().directory(directory).maxSizeBytes(1024 * 1024).build()
        }, { request, sink ->
            calls++; sink.write(bytes)
            OriginalMediaInfo("video/webm", bytes.size.toLong(), resolvedUrl = request.url)
        })
        try {
            store.acquireForPlayback(OriginalMediaRequest("https://may.2chan.net/b/src/test.webm")).use { original ->
                val root = original.complete().use { requireNotNull(it.file.parent) }
                val abandoned = root.resolve("web-player-123456789")
                FileSystem.SYSTEM.createDirectories(abandoned)
                FileSystem.SYSTEM.write(abandoned.resolve("player.html")) { writeUtf8("abandoned") }
                val unrelated = root.resolve("saved-video.webm")
                FileSystem.SYSTEM.write(unrelated) { write(bytes) }
                val document = IosLocalVideoDocument.create(original, "webm")
                val viewPin = document.retain()
                try {
                    assertFalse(FileSystem.SYSTEM.exists(abandoned))
                    assertContentEquals(bytes, FileSystem.SYSTEM.read(unrelated) { readByteArray() })
                    val second = IosLocalVideoDocument.create(original, "webm")
                    try { assertTrue(NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(document.url.path))) }
                    finally { second.close(); withTimeout(5_000) { second.cleanup.await() } }
                    original.complete().use { lease ->
                        val linked = requireNotNull(document.readAccessUrl.path) + "/video.webm"
                        val first = requireNotNull(NSFileManager.defaultManager.attributesOfItemAtPath(lease.file.toString(), null))
                        val second = NSFileManager.defaultManager.attributesOfItemAtPath(linked, null)
                        assertNotNull(first[NSFileSystemFileNumber])
                        assertEquals(first[NSFileSystemFileNumber], second?.get(NSFileSystemFileNumber))
                        assertEquals(first[NSFileSystemNumber], second?.get(NSFileSystemNumber))
                    }
                    document.close(); store.clear()
                    assertTrue(NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(viewPin.url.path)))
                    assertFalse(document.cleanup.isCompleted)
                    original.complete().use { assertContentEquals(bytes, it.readAt(0, bytes.size)) }
                    assertEquals(1, calls)
                } finally {
                    viewPin.close(); document.close()
                    withTimeout(5_000) { document.cleanup.await() }
                }
                assertFalse(NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(document.readAccessUrl.path)))
            }
        } finally { store.closeAndAwait(); FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false) }
    }

    @Test fun nativePlayerAndPromptUseOneOriginalIncludingUnknownLengthAndTailMoov() = nativeTest {
        for (knownLength in listOf(true, false)) {
            val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY.resolve("native-player-${Random.nextLong()}")
            val bytes = VideoEditFixtures.bytes("portrait")
            val calls = MutableStateFlow(0)
            val prefix = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val store = OriginalMediaStore("native-player", {
                DiskCache.Builder().directory(directory).maxSizeBytes(1024 * 1024).build()
            }, object : OriginalMediaDownloader {
                override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink) = download(request, sink) {}
                override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
                    calls.update { it + 1 }
                    val info = OriginalMediaInfo("video/mp4", if (knownLength) bytes.size.toLong() else -1, resolvedUrl = request.url)
                    onHeaders(info)
                    sink.write(bytes, 0, 512).emit(); prefix.complete(Unit)
                    finish.await()
                    sink.write(bytes, 512, bytes.size - 512)
                    return info.copy(sizeBytes = bytes.size.toLong())
                }
            })
            val source = PromptMediaSource(store, MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) })
            val request = OriginalMediaRequest("https://may.2chan.net/b/src/test.mp4")
            try {
                source.acquireForPlayback(request).use { original ->
                    prefix.await()
                    val pinned = IosOriginalMediaAsset(original, "mp4")
                    val item = AVPlayerItem(asset = pinned.asset)
                    val player = AVPlayer.playerWithPlayerItem(item)
                    try {
                        assertEquals("futacha-original", pinned.asset.URL.scheme)
                        assertEquals("ftyp", original.readAt(4, 4).decodeToString())
                        finish.complete(Unit)
                        original.complete().close()
                        withTimeout(15_000) {
                            while (item.status != AVPlayerItemStatusReadyToPlay) {
                                assertNotEquals(AVPlayerItemStatusFailed, item.status, item.error?.localizedDescription)
                                delay(20)
                            }
                        }
                        val size = item.presentationSize.useContents { width to height }
                        assertEquals(240.0 to 320.0, size)
                        assertTrue(CMTimeGetSeconds(item.duration) > 0)
                        source.changes.first { source.metadata(request.url) != null }
                        assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", source.metadata(request.url)!!.candidates.single().positive)
                        source.acquireForExport(request).use { assertContentEquals(bytes, it.readAt(0, bytes.size)) }
                        assertEquals(1, calls.value)
                    } finally {
                        player.pause(); player.replaceCurrentItemWithPlayerItem(null); pinned.close()
                    }
                }
            } finally {
                finish.complete(Unit); source.close(); store.closeAndAwait()
                FileSystem.SYSTEM.deleteRecursively(directory, mustExist = false)
            }
        }
    }

    /** The test runner owns the main thread; service AVFoundation's delegate queue while suspended. */
    private fun nativeTest(block: suspend () -> Unit) {
        assertTrue(NSThread.isMainThread)
        val result = MutableStateFlow<Result<Unit>?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val job = scope.launch { result.value = runCatching { withTimeout(30_000) { block() } } }
        val start = TimeSource.Monotonic.markNow()
        try {
            while (!job.isCompleted && start.elapsedNow() < 40.seconds) {
                NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            }
            assertTrue(job.isCompleted, "Native player test timed out")
            requireNotNull(result.value).getOrThrow()
        } finally { scope.cancel() }
    }
}
