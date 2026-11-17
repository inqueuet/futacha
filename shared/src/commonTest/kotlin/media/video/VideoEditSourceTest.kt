package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.model.VideoFrameIndex
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.util.FileReadSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

class VideoEditSourceTest {
    @Test fun firstImportRemovesAbandonedSessionsButPreservesOriginalsAndActiveSessions() = runBlocking {
        val fs = InMemoryFileSystem()
        val root = "/virtual/editor-cleanup-${kotlin.random.Random.nextLong()}"
        val gate = MediaFeatureGate().apply { update(enabled) }
        val abandoned = "$root/device-video-123-abc/input.mp4"
        val original = "$root/my-video.mp4"
        fs.writeBytes(abandoned, header).getOrThrow()
        fs.writeBytes(original, header).getOrThrow()
        fs.createDirectory("$root/device-video-not-a-session").getOrThrow()
        val first = VideoEditSource.import(fs, root, "first", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
        try {
            assertFalse(fs.exists(abandoned))
            assertContentEquals(header, fs.readBytes(original).getOrThrow())
            assertTrue(fs.exists("$root/device-video-not-a-session"))
            val second = VideoEditSource.import(fs, root, "second", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            try { assertContentEquals(header, fs.readBytes(first.path).getOrThrow()) }
            finally { second.close() }
        } finally { first.close() }
        assertEquals(setOf("my-video.mp4", "device-video-not-a-session"), fs.listFiles(root).toSet())
    }

    @Test fun closeSurvivesDeletionFailureAndStillRejectsUse(): Unit = runBlocking {
        val fs = DeleteFailingFileSystem()
        val gate = MediaFeatureGate().apply { update(enabled) }
        val root = "/virtual/editor-delete-${kotlin.random.Random.nextLong()}"
        val source = VideoEditSource.import(fs, root, "v", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
        fs.failDeletes = true

        // Used to throw from the picker's finally block and crash the app.
        source.close()

        assertFailsWith<IllegalStateException> { source.useFile { } }
    }

    @Test fun firstImportContinuesWhenAnAbandonedSessionCannotBeDeleted(): Unit = runBlocking {
        val fs = DeleteFailingFileSystem()
        val gate = MediaFeatureGate().apply { update(enabled) }
        val root = "/virtual/editor-stuck-${kotlin.random.Random.nextLong()}"
        fs.writeBytes("$root/device-video-123-abc/input.mp4", header).getOrThrow()
        fs.failDeletes = true

        val source = VideoEditSource.import(fs, root, "v", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)

        assertContentEquals(header, fs.readBytes(source.path).getOrThrow())
        source.close()
    }

    private class DeleteFailingFileSystem(
        private val delegate: InMemoryFileSystem = InMemoryFileSystem()
    ) : com.valoser.futacha.shared.util.FileSystem by delegate {
        var failDeletes = false
        override suspend fun deleteRecursively(path: String): Result<Unit> =
            if (failDeletes) Result.failure(IllegalStateException("EIO")) else delegate.deleteRecursively(path)
    }

    private val enabled = MediaFeatureSettings(videoEditorEnabled = true)
    private val header = byteArrayOf(0, 0, 0, 24) + "ftypisom".encodeToByteArray()
    private class Reader(val bytes: ByteArray, val chunk: Int = Int.MAX_VALUE, val afterRead: (Int) -> Unit = {}) : FileReadSource {
        var position = 0
        var calls = 0
        var maxBuffer = 0
        override suspend fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            calls++; maxBuffer = maxOf(maxBuffer, bytes.size)
            if (position == this.bytes.size) return -1
            val n = minOf(length, chunk, this.bytes.size - position)
            this.bytes.copyInto(bytes, offset, position, position + n)
            position += n; afterRead(position)
            return n
        }
    }
    @Test fun selectedVideoIsCopiedOnceWithBoundedChunksAndSharedByLocalReaders(): Unit = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        val payload = header + ByteArray(700_000) { (it % 251).toByte() }
        val reader = Reader(payload, chunk = 140_000)
        val source = VideoEditSource.import(fs, "/virtual/editor", "phone.mov", reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
        try {
            assertEquals(payload.size, reader.position); assertEquals(payload.size.toLong(), source.byteSize)
            assertEquals(VIDEO_EDIT_COPY_BUFFER, reader.maxBuffer)
            val reads = reader.calls
            repeat(2) { source.useFile { assertContentEquals(payload, fs.readBytes(it).getOrThrow()) } }
            assertEquals(reads, reader.calls) // inspection and preview use the owned copy.
            gate.update(enabled.copy(promptDisplayEnabled = true, imageEditorEnabled = true))
            source.useFile { assertTrue(fs.exists(it)) }
        } finally { source.close() }
        assertFalse(fs.exists(source.path)); assertTrue(fs.listFiles("/virtual/editor").isEmpty())
        assertFailsWith<IllegalStateException> { source.useFile {} }
    }
    @Test fun offOrStalePermitDoesNotEvenReadTheDeviceResource() = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        val permit = gate.permit(MediaFeature.VIDEO_EDITOR)!!
        gate.update(MediaFeatureSettings.Disabled); gate.update(enabled)
        val reader = Reader(header)
        assertFailsWith<IllegalStateException> { VideoEditSource.import(fs, "/virtual/editor", "v", reader, gate, permit) }
        assertEquals(0, reader.calls); assertTrue(fs.listFiles("/virtual/editor").isEmpty())
        assertNull(MediaFeatureGate().permit(MediaFeature.VIDEO_EDITOR))
    }
    @Test fun cancellingOrTurningOffDuringImportDeletesPartialVideo() = runBlocking {
        for (cancel in listOf(false, true)) {
            val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
            val reader = Reader(header + ByteArray(100), 13) { position ->
                if (position > 12) {
                    if (cancel) throw CancellationException("cancel picker")
                    gate.update(MediaFeatureSettings.Disabled)
                }
            }
            try {
                VideoEditSource.import(fs, "/virtual/editor", "v", reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
                fail("must not publish a cancelled source")
            } catch (failure: Exception) {
                if (cancel) assertIs<CancellationException>(failure) else assertIs<IllegalStateException>(failure)
            }
            assertTrue(fs.listFiles("/virtual/editor").isEmpty())
        }
    }
    @Test fun invalidTruncatedAndOversizedInputsLeaveNoWorkFiles() = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        for (bytes in listOf(ByteArray(0), header.copyOf(8), ByteArray(100), header + ByteArray(30))) {
            assertFails { VideoEditSource.import(fs, "/virtual/editor", "v", Reader(bytes, 3), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!, maxBytes = 20) }
            assertTrue(fs.listFiles("/virtual/editor").isEmpty())
        }
        assertEquals(VideoContainer.WEBM, editableVideoContainer(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte()) + ByteArray(8)))
    }
    @Test fun closeWaitsForCodecReaderBeforeDeletingItsInput() = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        val source = VideoEditSource.import(fs, "/virtual/editor", "v", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val reader = launch { source.useFile { started.complete(Unit); finish.await(); assertTrue(fs.exists(it)) } }
        started.await()
        val closing = launch { source.close() }; yield()
        assertFalse(closing.isCompleted); assertTrue(fs.exists(source.path))
        finish.complete(Unit); reader.join(); closing.join()
        assertFalse(fs.exists(source.path))
    }
    @Test fun onlyVideoOffCancelsAnActiveCodecAndRejectsItsLateResult() = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        val source = VideoEditSource.import(fs, "/virtual/editor", "v", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
        val started = CompletableDeferred<Unit>()
        val reading = async {
            assertFailsWith<CancellationException> { source.useFile { started.complete(Unit); awaitCancellation() } }
        }
        started.await()
        gate.update(enabled.copy(promptDisplayEnabled = true, imageEditorEnabled = true)); yield()
        assertFalse(reading.isCompleted)
        gate.update(enabled.copy(videoEditorEnabled = false))
        withTimeout(5_000) { reading.await() }
        source.close(); assertTrue(fs.listFiles("/virtual/editor").isEmpty())
    }
    @Test fun cancellationDuringDispatcherHandoffDisposesTheCompletedCopy(): Unit = runBlocking {
        val fs = InMemoryFileSystem(); val gate = MediaFeatureGate().apply { update(enabled) }
        val queue = Channel<Runnable>(Channel.UNLIMITED)
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { check(queue.trySend(block).isSuccess) }
        }
        val importing = launch(dispatcher) {
            VideoEditSource.import(fs, "/virtual/editor", "v", Reader(header), gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            fail("handoff must be cancelled")
        }
        queue.receive().run() // start import; its copy runs on the I/O dispatcher.
        val delivery = withTimeout(5_000) { queue.receive() }
        assertEquals(1, fs.listFiles("/virtual/editor").size)
        importing.cancel(); delivery.run(); importing.join()
        assertTrue(fs.listFiles("/virtual/editor").isEmpty())
        queue.close()
    }
    @Test fun frameIndexOwnsItsArrayAndRejectsInvalidOrAmbiguousTimes() {
        val source = longArrayOf(0, 33_333, 100_000)
        val index = VideoFrameIndex(source, 150_000)
        source[1] = 5; index.timestampsUs[1] = 7
        assertEquals(33_333L, index.step(0, true))
        assertEquals(150_000L, index.endAfter(Long.MAX_VALUE))
        for (times in listOf(longArrayOf(), longArrayOf(-1), longArrayOf(0, 0), longArrayOf(1, 0), longArrayOf(150_000))) {
            assertFailsWith<IllegalArgumentException> { VideoFrameIndex(times, 150_000) }
        }
        assertFailsWith<IllegalArgumentException> { buildVideoFrameIndex(listOf(0, 0), 100) }
        assertFailsWith<IllegalArgumentException> { buildVideoFrameIndex(listOf(Long.MAX_VALUE), Long.MAX_VALUE) }
    }
    @Test fun decodeOrderingAndLastFrameDurationDoNotTurnVfrIntoCfr() {
        val frames = buildVideoFrameIndex(listOf(66_667, 0, 33_333, 166_667), 200_000)
        assertContentEquals(longArrayOf(0, 33_333, 66_667, 166_667), frames.timestampsUs)
        assertEquals(200_000L, frames.durationUs) // last gap is NOT necessarily the last frame's duration.
        assertEquals(166_667L, frames.step(66_667, true))
        assertEquals(200_000L, frames.endAfter(166_667))
    }
    @Test fun exportVerificationRejectsDroppedFramesAudioAndChangedSpacing() {
        fun info(times: List<Long>, end: Long = 200_000) = VideoEditInfo(320, 240, buildVideoFrameIndex(times, end), true, false, 0)
        val input = info(listOf(10_000, 43_333, 110_000), 210_000)
        val normalized = info(listOf(0, 33_333, 100_000))
        verifyVideoEditTiming(input, normalized)
        for (invalid in listOf(normalized.copy(width = 240), normalized.copy(hasAudio = false),
            info(listOf(0, 100_000)), info(listOf(0, 50_000, 100_000)), info(listOf(0, 33_333, 100_000), 205_000))) {
            assertFailsWith<IllegalArgumentException> { verifyVideoEditTiming(input, invalid) }
        }
    }
}
