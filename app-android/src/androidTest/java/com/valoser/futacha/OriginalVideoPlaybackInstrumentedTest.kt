@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.net.Uri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.platform.app.InstrumentationRegistry
import coil3.disk.DiskCache
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okio.BufferedSink
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@UnstableApi
class OriginalVideoPlaybackInstrumentedTest {
    private val request = OriginalMediaRequest("https://may.2chan.net/b/src/video.mp4")
    private val uri = Uri.parse("futacha-original://media/asset.mp4")

    private suspend fun fixture(bytes: ByteArray, knownSize: Boolean, block: suspend (OriginalMediaStore, CompletableDeferred<Unit>, AtomicInteger) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "original-video-test-${System.nanoTime()}")
        val finish = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val split = minOf(bytes.size / 2, 512)
        val store = OriginalMediaStore("android-player", {
            DiskCache.Builder().directory(directory.path.toPath()).maxSizeBytes(1024 * 1024).build()
        }, object : OriginalMediaDownloader {
            override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink) = download(request, sink) {}
            override suspend fun download(request: OriginalMediaRequest, sink: BufferedSink, onHeaders: (OriginalMediaInfo) -> Unit): OriginalMediaInfo {
                calls.incrementAndGet()
                val info = OriginalMediaInfo("video/mp4", if (knownSize) bytes.size.toLong() else -1, resolvedUrl = request.url)
                onHeaders(info); sink.write(bytes, 0, split).emit()
                finish.await(); sink.write(bytes, split, bytes.size - split)
                return info.copy(sizeBytes = bytes.size.toLong())
            }
        })
        try { withTimeout(30_000) { block(store, finish, calls) } }
        finally { finish.complete(Unit); withTimeout(5_000) { store.closeAndAwait() }; directory.deleteRecursively() }
    }

    @Test fun dataSourceReadsTheGrowingPrefixSeeksAndReportsVerifiedEof(): Unit = runBlocking {
        for (known in listOf(true, false)) fixture("prefixsuffix".encodeToByteArray(), known) { store, finish, calls ->
            store.acquireForPlayback(request).use { original ->
                val reader = AndroidOriginalMediaDataSource(original)
                try {
                    assertEquals(if (known) 12L else -1L, reader.open(DataSpec(uri)))
                    val buffer = ByteArray(20)
                    assertEquals(6, reader.read(buffer, 0, 20))
                    assertEquals("prefix", buffer.copyOf(6).decodeToString())
                    val tail = async(Dispatchers.IO) { reader.read(buffer, 0, 20) }
                    finish.complete(Unit)
                    assertEquals(6, tail.await())
                    assertEquals("suffix", buffer.copyOf(6).decodeToString())
                    assertEquals(C.RESULT_END_OF_INPUT, reader.read(buffer, 0, 20))
                    reader.close()
                    // A second Media3 range is a read of the same file, never another GET.
                    reader.open(DataSpec.Builder().setUri(uri).setPosition(3).setLength(7).build())
                    assertEquals(7, reader.read(buffer, 1, 10))
                    assertEquals("fixsuff", buffer.copyOfRange(1, 8).decodeToString())
                    assertEquals(C.RESULT_END_OF_INPUT, reader.read(buffer, 0, 20))
                    assertEquals(1, calls.get())
                } finally { reader.close() }
            }
        }
    }

    @Test fun exoPlayerPromptAndExportShareOneTransferForKnownAndUnknownLength(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (known in listOf(true, false)) fixture(VideoEditFixtures.bytes("portrait"), known) { store, finish, calls ->
            val source = PromptMediaSource(store, MediaFeatureGate().apply { update(MediaFeatureSettings(promptDisplayEnabled = true)) })
            try {
                source.acquireForPlayback(request).use { original ->
                    val ready = CompletableDeferred<Unit>()
                    val player = withContext(Dispatchers.Main) {
                        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
                            .setMediaCodecSelector(MediaCodecSelector.PREFER_SOFTWARE)
                        ExoPlayer.Builder(context, renderers)
                            .setMediaSourceFactory(DefaultMediaSourceFactory(DataSource.Factory { AndroidOriginalMediaDataSource(original) }))
                            .build().apply {
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.complete(Unit) }
                                    override fun onPlayerError(error: PlaybackException) { ready.completeExceptionally(error) }
                                })
                                setMediaItem(MediaItem.fromUri(uri)); prepare()
                            }
                    }
                    try {
                        assertEquals("ftyp", original.readAt(4, 4).decodeToString())
                        finish.complete(Unit)
                        original.complete().close()
                        withTimeout(15_000) { ready.await() }
                        withContext(Dispatchers.Main) {
                            assertEquals(240, player.videoFormat?.width)
                            assertEquals(320, player.videoFormat?.height)
                            assertTrue(player.duration > 0)
                        }
                        source.changes.first { source.metadata(request.url) != null }
                        assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", source.metadata(request.url)!!.candidates.single().positive)
                        source.acquireForExport(request).use { assertArrayEquals(VideoEditFixtures.bytes("portrait"), it.readAt(0, it.info.sizeBytes.toInt())) }
                        assertEquals(1, calls.get())
                    } finally { withContext(NonCancellable + Dispatchers.Main) { player.release() } }
                }
            } finally { source.close() }
        }
    }
}
