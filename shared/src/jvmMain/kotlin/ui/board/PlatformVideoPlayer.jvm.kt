package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.sun.jna.NativeLibrary
import com.valoser.futacha.shared.desktop.*
import com.valoser.futacha.shared.media.source.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.edit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.*
import uk.co.caprica.vlcj.player.base.State as VlcState
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.*
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val DESKTOP_VLC_ACTIVE_STATES = setOf(VlcState.OPENING, VlcState.BUFFERING, VlcState.PLAYING)
private const val DESKTOP_VLC_PAUSE_WAIT_NANOS = 1_000_000_000L
private const val DESKTOP_VLC_RELEASE_TIMEOUT_MILLIS = 3_000L

/**
 * One libVLC instance per process: creating and releasing an instance per video
 * reloaded all ~337 plugins (libvlccore keeps its module bank only while an
 * instance lives, and `--no-plugins-cache` leaves no cache to read).
 */
internal object DesktopVlc {
    private var configured = false
    private var shared: MediaPlayerFactory? = null

    @Synchronized fun factory(): MediaPlayerFactory {
        configure()
        return shared ?: MediaPlayerFactory("--intf=dummy", "--quiet", "--avcodec-hw=none", "--no-video-title-show",
            "--no-plugins-cache", "--no-lua", "--no-metadata-network-access", "--ignore-config").also { shared = it }
    }

    /**
     * A player VLC never stopped may hold locks of its instance, so later videos
     * get a fresh one and the old instance is left to the stuck player.
     */
    @Synchronized fun abandon(factory: MediaPlayerFactory) {
        if (shared === factory) shared = null
    }

    /** At app shutdown. Each player keeps its own reference, so this only frees the instance once they are gone. */
    fun release() {
        val factory = synchronized(this) { shared.also { shared = null } } ?: return
        val released = java.util.concurrent.CountDownLatch(1)
        Thread({ try { factory.release() } finally { released.countDown() } }, "futacha-vlc-shutdown")
            .apply { isDaemon = true }.start()
        released.await(DESKTOP_VLC_RELEASE_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun configure() {
        if (!configured) {
            val root = desktopResource("vlc")
            DesktopPlatform.setEnvironment("VLC_PLUGIN_PATH", File(root, "plugins").absolutePath)
            val libraryDirectory = if (DesktopPlatform.isWindows) root else File(root, "lib")
            for (name in listOf("vlc", "vlccore", "libvlc", "libvlccore")) {
                NativeLibrary.addSearchPath(name, libraryDirectory.absolutePath)
            }
            NativeLibrary.getInstance(File(libraryDirectory,
                if (DesktopPlatform.isWindows) "libvlccore.dll" else "libvlccore.dylib").absolutePath,
                if (DesktopPlatform.isWindows) mapOf(com.sun.jna.Library.OPTION_OPEN_FLAGS to 0x00000008) else emptyMap())
            // libVLC finds its plugins relative to libvlccore in the bundled tree.
            configured = true
        }
    }
}

/**
 * Files a player has open. Deleting one of them (an attachment preview, an
 * edit preview) waits until VLC released it: Windows cannot delete open files.
 */
internal object DesktopVideoFiles {
    private val openCounts = HashMap<String, Int>()
    private val deleteWhenClosed = HashSet<String>()

    @Synchronized fun opened(path: String) { openCounts[path] = (openCounts[path] ?: 0) + 1 }

    @Synchronized fun closed(path: String) {
        val remaining = (openCounts[path] ?: return) - 1
        if (remaining > 0) { openCounts[path] = remaining; return }
        openCounts.remove(path)
        if (deleteWhenClosed.remove(path)) deleteOrDeferToExit(File(path))
    }

    @Synchronized fun delete(file: File) {
        val path = file.absolutePath
        if (path in openCounts) deleteWhenClosed += path else deleteOrDeferToExit(file)
    }

    private fun deleteOrDeferToExit(file: File) {
        if (file.exists() && !file.delete()) file.deleteOnExit()
    }
}

internal class DesktopVideoSession(private val frameChanged: (ImageBitmap, Int, Int) -> Unit) : AutoCloseable {
    private val factory = DesktopVlc.factory()
    val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
    val failed = AtomicBoolean(false)
    val ended = AtomicBoolean(false)
    val hasFrame = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val controlLock = Any()
    private val frameConverter = DesktopVideoFrameConverter()
    fun <T> withPlayer(block: (uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer) -> T): T? =
        synchronized(controlLock) { if (closed.get()) null else block(player) }
    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun error(mediaPlayer: MediaPlayer) { failed.set(true) }
            override fun finished(mediaPlayer: MediaPlayer) { ended.set(true); if (!hasFrame.get()) failed.set(true) }
        })
        val format = object : BufferFormatCallbackAdapter() {
            override fun getBufferFormat(width: Int, height: Int): BufferFormat {
                if (width <= 0 || height <= 0 || width.toLong() * height > 33_554_432) { failed.set(true); return RV32BufferFormat(1, 1) }
                val scale = minOf(1.0, 1920.0 / maxOf(width, height))
                return RV32BufferFormat(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            }
        }
        val render = object : RenderCallback {
            override fun lock(player: MediaPlayer) = Unit
            override fun unlock(player: MediaPlayer) = Unit
            override fun display(player: MediaPlayer, buffers: Array<ByteBuffer>, format: BufferFormat, displayWidth: Int, displayHeight: Int) {
                if (closed.get()) return
                try {
                    val width = format.width; val height = format.height
                    val image = frameConverter.convert(buffers[0], width, height)
                    hasFrame.set(true)
                    if (!closed.get()) frameChanged(image, width, height)
                } catch (_: Exception) { failed.set(true) }
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
    }
    fun play(path: String) { withPlayer { check(it.media().play(path)) { "動画を開始できません" } } }
    override fun close() = close(afterRelease = {})

    /**
     * [afterRelease] runs once VLC has released the player, so files VLC may
     * still have open (the source lease, a preview file) are not deleted under
     * it. If VLC stays stuck past the timeout it runs then instead, exactly once:
     * an unreturned lease would keep the original media store from shutting down.
     * Files deleted through [DesktopVideoFiles] fall back to deletion at exit.
     */
    fun close(afterRelease: () -> Unit) {
        // Marking closed under the lock waits for in-flight controls; later ones see closed and skip the player.
        if (!synchronized(controlLock) { closed.compareAndSet(false, true) }) {
            afterRelease()
            return
        }
        // libVLC 3.0.23's macOS audio output (auhal) waits without a deadline in its play/drain loops and only leaves
        // them early once paused, so stopping a playing input can block forever in input_Close. Pausing first fixes
        // closing during playback. Near the end of the audio the decoder thread that would apply that pause is
        // itself parked in the drain loop, so release on a daemon thread and abandon a player that stays stuck
        // instead of hanging the viewer, the IO pool or app shutdown.
        val released = java.util.concurrent.CountDownLatch(1)
        val cleanedUp = AtomicBoolean(false)
        fun cleanUp() {
            if (!cleanedUp.compareAndSet(false, true)) return
            try { afterRelease() } catch (failure: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("DesktopVideoSession", "Cleanup after VLC release failed", failure)
            }
        }
        Thread({
            try {
                if (player.status().state() in DESKTOP_VLC_ACTIVE_STATES) {
                    player.controls().setPause(true)
                    val deadline = System.nanoTime() + DESKTOP_VLC_PAUSE_WAIT_NANOS
                    while (player.status().state() in DESKTOP_VLC_ACTIVE_STATES && System.nanoTime() < deadline) {
                        Thread.sleep(10)
                    }
                }
                player.controls().stop(); player.release()
            } catch (failure: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("DesktopVideoSession", "Failed to release the VLC player", failure)
            } finally {
                // The shared instance stays alive for the next video (released at app shutdown).
                released.countDown()
                cleanUp()
            }
        }, "futacha-vlc-release").apply { isDaemon = true }.start()
        if (!released.await(DESKTOP_VLC_RELEASE_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            com.valoser.futacha.shared.util.Logger.w(
                "DesktopVideoSession",
                "VLC did not stop within ${DESKTOP_VLC_RELEASE_TIMEOUT_MILLIS}ms; abandoning the native player"
            )
            DesktopVlc.abandon(factory)
            cleanUp()
        }
    }
}

@Composable
internal actual fun NativePlatformVideoPlayer(videoUrl: String, playback: OriginalMediaPlayback?, modifier: Modifier,
    onStateChanged: (VideoPlayerState) -> Unit, onVideoSizeKnown: (Int, Int) -> Unit, areControlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit, volume: Float, isMuted: Boolean,
    onMediaInfoKnown: (VideoMediaInfo) -> Unit, onPlaybackError: (VideoPlaybackError) -> Unit, editing: VideoEditPlayback?) {
    val frameFlow = remember(videoUrl, playback, editing) { MutableStateFlow<Triple<ImageBitmap, Int, Int>?>(null) }
    val frame by frameFlow.collectAsState()
    var session by remember(videoUrl, playback, editing) { mutableStateOf<DesktopVideoSession?>(null) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    // While the thumb is dragged only the label follows; one seek runs on release.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val state by rememberUpdatedState(onStateChanged)
    val error by rememberUpdatedState(onPlaybackError)
    val size by rememberUpdatedState(onVideoSizeKnown)
    val info by rememberUpdatedState(onMediaInfoKnown)
    val currentVolume by rememberUpdatedState(volume)
    val currentMuted by rememberUpdatedState(isMuted)
    LaunchedEffect(frame?.second, frame?.third) { frame?.let { size(it.second, it.third); state(VideoPlayerState.Ready) } }
    LaunchedEffect(videoUrl, playback, editing) {
        var lease: OriginalMediaStore.Lease? = null
        var pin: OriginalMediaPlayback? = null
        var editPin: AutoCloseable? = null
        var owned: DesktopVideoSession? = null
        var preview: File? = null
        var openedPath: String? = null
        state(VideoPlayerState.Buffering)
        try {
            withContext(Dispatchers.IO) {
                pin = playback?.retain()
                lease = pin?.complete()
                var path = lease?.file?.toString() ?: videoUrl
                if (path.startsWith("file:/", true) || com.valoser.futacha.shared.util.isAbsoluteLocalMediaPath(path)) {
                    path = desktopLocalFile(path).absolutePath
                }
                editPin = editing?.retain()
                if (editing != null) {
                    preview = File.createTempFile("preview-", ".mp4", desktopLocalFile(path).parentFile)
                        .also { it.deleteOnExit() } // in case VLC never releases it
                    exportDeviceVideo(null, path, editing.info, editing.document, preview!!.absolutePath) {}
                    path = preview!!.absolutePath
                }
                ensureActive()
                openedPath = File(path).absolutePath.also(DesktopVideoFiles::opened)
                owned = DesktopVideoSession { image, w, h -> frameFlow.value = Triple(image, w, h) }
                owned!!.play(path)
                session = owned
            }
            val active = requireNotNull(owned)
            val deadline = System.nanoTime() + 20_000_000_000L
            var announcedDuration = -1L
            var seeked = editing == null
            editing?.pausePlayer = { scope.launch(Dispatchers.IO) { active.withPlayer { it.controls().setPause(true) } } }
            while (isActive) {
                withContext(Dispatchers.IO) {
                    check(!active.failed.get()) { "動画を再生できませんでした" }
                    check(active.hasFrame.get() || System.nanoTime() < deadline) { "動画の読み込みがタイムアウトしました" }
                    active.withPlayer { player ->
                    duration = player.status().length().coerceAtLeast(0)
                    position = player.status().time().coerceAtLeast(0)
                    playing = player.status().isPlaying
                    player.audio().setVolume((currentVolume.coerceIn(0f, 1f) * 100).toInt())
                    player.audio().setMute(currentMuted)
                    if (!seeked && duration > 0) { player.controls().setTime(editing!!.startUs / 1000); seeked = true }
                    }
                }
                if (duration > 0 && duration != announcedDuration) {
                    announcedDuration = duration
                    info(VideoMediaInfo(width = frame?.second, height = frame?.third, durationMillis = duration))
                }
                editing?.position(position * 1000)
                if (active.ended.get()) { editing?.ended(); playing = false }
                delay(80)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) {
            error(VideoPlaybackError("desktop_video", failure.message ?: "動画を再生できませんでした")); state(VideoPlayerState.Error)
        } finally {
            session = null; editing?.pausePlayer = null; frameFlow.value = null
            withContext(NonCancellable + Dispatchers.IO) {
                val releasedFiles = {
                    openedPath?.let(DesktopVideoFiles::closed)
                    lease?.close(); pin?.close(); editPin?.close(); preview?.let(DesktopVideoFiles::delete); Unit
                }
                // Release the source and delete the preview only after VLC let go of them.
                owned?.close(afterRelease = releasedFiles) ?: releasedFiles()
            }
        }
    }
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth().clickable { onControlsVisibilityChanged(!areControlsVisible) }, contentAlignment = Alignment.Center) {
            frame?.let { Image(it.first, "動画", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        if (areControlsVisible && editing == null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { session?.let { active -> scope.launch(Dispatchers.IO) {
                active.withPlayer { player ->
                    if (active.ended.getAndSet(false)) { player.controls().setTime(0); player.controls().play() }
                    else player.controls().setPause(playing)
                }
            } } }) { Text(if (playing) "一時停止" else "再生") }
            Slider(value = dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    val target = dragFraction?.let { (duration * it).toLong() }
                    dragFraction = null
                    val active = session
                    if (target != null && active != null) {
                        position = target
                        scope.launch(Dispatchers.IO) { active.withPlayer { it.controls().setTime(target) } }
                    }
                }, modifier = Modifier.weight(1f))
            Text("${(dragFraction?.let { (duration * it).toLong() } ?: position) / 1000}/${duration / 1000}秒")
        }
    }
}

/**
 * Turns VLC's RV32 frames into Compose bitmaps. RV32 is B, G, R, X in memory,
 * which Skia reads as BGRA once X is set to 255. The byte buffer is reused while the frame size stays the same: allocating an
 * IntArray and a ByteArray per frame cost about 0.5 GB/s at 1080p30.
 * Called from VLC's single display thread.
 */
internal class DesktopVideoFrameConverter {
    private var bytes = ByteArray(0)

    /** The reusable frame buffer (exposed for tests). */
    internal val buffer: ByteArray get() = bytes

    fun convert(frame: ByteBuffer, width: Int, height: Int): androidx.compose.ui.graphics.ImageBitmap {
        val size = width * height * 4
        if (bytes.size != size) bytes = ByteArray(size)
        frame.duplicate().get(bytes, 0, size)
        // X is undefined (often 0); Skia keeps it as alpha, so make it opaque in place.
        var alpha = 3
        while (alpha < size) { bytes[alpha] = -1; alpha += 4 }
        val info = org.jetbrains.skia.ImageInfo(width, height, org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.OPAQUE)
        val bitmap = org.jetbrains.skia.Bitmap()
        check(bitmap.allocPixels(info)) { "フレーム用のメモリを確保できません" }
        check(bitmap.installPixels(bytes)) { "フレームを変換できません" }
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    }
}
