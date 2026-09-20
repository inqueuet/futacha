package com.valoser.futacha.shared.ui.board

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
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.*
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal object DesktopVlc {
    private var configured = false
    @Synchronized fun factory(): MediaPlayerFactory {
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
        return MediaPlayerFactory("--intf=dummy", "--quiet", "--avcodec-hw=none", "--no-video-title-show", "--no-plugins-cache", "--no-lua", "--no-metadata-network-access", "--ignore-config")
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
                    val pixels = IntArray(width * height)
                    buffers[0].duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels)
                    for (i in pixels.indices) pixels[i] = pixels[i] or (0xff shl 24)
                    val image = imageEditBitmap(EditRaster(width, height, pixels))
                    hasFrame.set(true)
                    if (!closed.get()) frameChanged(image, width, height)
                } catch (_: Exception) { failed.set(true) }
            }
        }
        player.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
    }
    fun play(path: String) { withPlayer { check(it.media().play(path)) { "動画を開始できません" } } }
    override fun close() {
        synchronized(controlLock) { if (closed.compareAndSet(false, true)) {
            try { player.controls().stop(); player.release() } finally { factory.release() }
        } }
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
                    exportDeviceVideo(null, path, editing.info, editing.document, preview!!.absolutePath) {}
                    path = preview!!.absolutePath
                }
                ensureActive()
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
                owned?.close(); lease?.close(); pin?.close(); editPin?.close(); preview?.delete()
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
            Slider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { value -> session?.let { active -> scope.launch(Dispatchers.IO) { active.withPlayer { it.controls().setTime((duration * value).toLong()) } } } }, modifier = Modifier.weight(1f))
            Text("${position / 1000}/${duration / 1000}秒")
        }
    }
}
