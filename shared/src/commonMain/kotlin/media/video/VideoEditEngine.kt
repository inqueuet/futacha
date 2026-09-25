package com.valoser.futacha.shared.media.video

import androidx.compose.ui.graphics.ImageBitmap
import com.valoser.futacha.shared.media.edit.EditRaster
import com.valoser.futacha.shared.media.edit.imageEditBitmap
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock

/** One decoded editor frame. Masks render onto it repeatedly; only a new time decodes again. */
internal interface VideoPreviewFrame {
    val timeUs: Long
    suspend fun render(document: MosaicDocument): ImageBitmap
    fun close()
}

/** Android/desktop keep the decoded ARGB pixels and apply the shared shader-equivalent renderer. */
internal class RasterVideoPreviewFrame(
    override val timeUs: Long, private val raster: EditRaster, private val renderTimeUs: Long = timeUs
) : VideoPreviewFrame {
    override suspend fun render(document: MosaicDocument): ImageBitmap =
        imageEditBitmap(renderVideoPreview(raster, document, renderTimeUs))
    override fun close() {}
}

/**
 * Decodes [timeUs] and hands the frame to [adopt] before returning, so a cancellation that
 * lands after the native decode can never drop (and leak) a frame that needs [close].
 */
internal expect suspend fun decodeDeviceVideoPreviewFrame(
    path: String, info: VideoEditInfo, timeUs: Long, adopt: (VideoPreviewFrame) -> Unit
)

internal suspend fun previewDeviceVideo(path: String, info: VideoEditInfo, timeUs: Long, document: MosaicDocument): ImageBitmap {
    var frame: VideoPreviewFrame? = null
    try {
        decodeDeviceVideoPreviewFrame(path, info, timeUs) { frame = it }
        return checkNotNull(frame) { "動画のコマを読み取れません" }.render(document)
    } finally { frame?.close() }
}

/**
 * The editor's last decoded frame. A mask/box change re-renders it without decoding again
 * (or taking the source file); only a new time decodes. Frames are used and released under
 * one lock, so a cancelled render still inside native code never loses its frame.
 */
internal class VideoPreviewFrameCache {
    private val mutex = Mutex()
    private var frame: VideoPreviewFrame? = null
    private var closed = false
    @kotlin.concurrent.Volatile private var decodedTimeUs: Long? = null

    /** Whether [render] at [timeUs] can skip decoding; a hint for the spinner only. */
    fun has(timeUs: Long): Boolean = decodedTimeUs == timeUs

    suspend fun render(
        timeUs: Long, document: MosaicDocument, decode: suspend (adopt: (VideoPreviewFrame) -> Unit) -> Unit
    ): ImageBitmap = mutex.withLock {
        check(!closed) { "プレビューは終了しました" }
        if (frame?.timeUs != timeUs) {
            decodedTimeUs = null
            frame?.close(); frame = null
            decode { adopted -> frame?.close(); frame = adopted }
        }
        val current = checkNotNull(frame?.takeIf { it.timeUs == timeUs }) { "動画のコマを読み取れません" }
        decodedTimeUs = timeUs
        withContext(Dispatchers.Default) { current.render(document) }
    }

    /** Safe from composition disposal: an in-flight render finishes before its frame is released. */
    fun close() {
        videoPreviewCleanup.launch {
            mutex.withLock { closed = true; decodedTimeUs = null; frame?.close(); frame = null }
        }
    }
}

private val videoPreviewCleanup = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal expect suspend fun exportDeviceVideo(context: Any?, path: String, info: VideoEditInfo,
    document: MosaicDocument, output: String, onProgress: (Float) -> Unit)

internal fun validateVideoEditDocument(document: MosaicDocument, info: VideoEditInfo) {
    require(document.regions.isNotEmpty()) { "モザイクか黒塗りの範囲を追加してください" }
    require(document.review?.confirmed != false) { "解析した区間の確認が必要です" }
    require(document.regions.none { it.hasEmptyActiveMask() }) { "空の輪郭があります" }
    require(document.regions.all { it.startUs < info.frames.durationUs && it.endUs <= info.frames.durationUs }) { "範囲の時刻が動画の外です" }
    require(!info.hdr) { "現在はSDR動画に対応しています。HDR動画は編集できません" }
}

/** Native exporters write only inside the owned session directory; failure never publishes a file. */
internal suspend fun VideoEditSource.export(
    context: Any?, fileSystem: FileSystem, info: VideoEditInfo, document: MosaicDocument, onProgress: (Float) -> Unit
): String {
    var output: String? = null
    try {
        return useFile { input ->
            validateVideoEditDocument(document, info)
            val target = input.substringBeforeLast('/') + "/edited-${Clock.System.now().toEpochMilliseconds()}-${Random.nextInt(0, Int.MAX_VALUE).toString(36)}.mp4"
            output = target
            exportDeviceVideo(context, input, info, document, target, onProgress)
            currentCoroutineContext().ensureActive(); checkActive()
            preserveEditedVideoMetadata(input, target, document.regions.size)
            currentCoroutineContext().ensureActive(); checkActive()
            verifyVideoEditTiming(info, inspectDeviceVideo(target))
            target
        }
    } catch (failure: Throwable) {
        try { withContext(NonCancellable) { output?.let { fileSystem.delete(it).getOrThrow() } } }
        catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
        throw failure
    }
}

internal suspend fun saveEditedVideo(source: VideoEditSource, fs: FileSystem, output: String, location: SaveLocation): String {
    var destination: String? = null
    try {
        return source.useFile { input ->
            require(output.substringBeforeLast('/') == input.substringBeforeLast('/'))
            val name = output.substringAfterLast('/')
            require(name.matches(Regex("edited-[0-9]+-[a-z0-9]+\\.mp4")))
            val path = "edited_videos/$name"
            destination = path
            fs.createDirectory(location, "edited_videos").getOrThrow()
            fs.readByteStream(output) { reader -> fs.writeByteStream(location, path) { sink ->
                val buffer = ByteArray(VIDEO_EDIT_COPY_BUFFER)
                while (true) {
                    currentCoroutineContext().ensureActive(); source.checkActive()
                    val count = reader.read(buffer)
                    if (count == -1) break
                    check(count in 1..buffer.size)
                    sink.write(buffer, length = count)
                }
            }.getOrThrow() }.getOrThrow()
            currentCoroutineContext().ensureActive(); source.checkActive()
            path
        }
    } catch (failure: Throwable) {
        // Include the source's final permit check in the transaction, not just the stream copy.
        try { withContext(NonCancellable) { destination?.let { fs.delete(location, it).getOrThrow() } } }
        catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
        throw failure
    }
}
