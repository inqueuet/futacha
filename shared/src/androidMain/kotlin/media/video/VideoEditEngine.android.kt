@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.valoser.futacha.shared.media.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.metrics.LogSessionId
import android.net.Uri
import android.os.Build
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.effect.ScaleAndRotateTransformation
import com.valoser.futacha.shared.media.edit.*
import com.valoser.futacha.shared.media.video.model.MosaicDocument
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

internal actual suspend fun decodeDeviceVideoPreviewFrame(
    path: String, info: VideoEditInfo, timeUs: Long, adopt: (VideoPreviewFrame) -> Unit
): Unit = withContext(AppDispatchers.io) {
    val reader = MediaMetadataRetriever()
    var bitmap: Bitmap? = null
    try {
        reader.setDataSource(path)
        val scale = minOf(1f, 960f / maxOf(info.width, info.height))
        val w = (info.width * scale).roundToInt().coerceAtLeast(1); val h = (info.height * scale).roundToInt().coerceAtLeast(1)
        val at = (timeUs + retrieverTimelineOffsetUs(path, info)).coerceAtLeast(0)
        val raw = requireNotNull(if (Build.VERSION.SDK_INT >= 27) reader.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST, w, h)
            else reader.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST)) { "動画のコマを読み取れません" }
        bitmap = raw
        if (raw.width != w || raw.height != h) bitmap = Bitmap.createScaledBitmap(raw, w, h, true).also { if (it !== raw) raw.recycle() }
        val frame = requireNotNull(bitmap)
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        adopt(RasterVideoPreviewFrame(timeUs, EditRaster(frame.width, frame.height, pixels)))
    } finally { bitmap?.recycle(); reader.release() }
}

/**
 * Before Android 10 the platform MP4 extractor (shared by MediaMetadataRetriever) ignores a video
 * edit list's media time, while [VideoEditInfo.frames] follow Media3/Transformer's edit-list
 * timeline. Seeking the retriever by those times showed a frame 1-2 frames off. The constant
 * difference between the two timelines, measured on the first samples, maps one onto the other.
 */
private fun retrieverTimelineOffsetUs(path: String, info: VideoEditInfo): Long {
    if (Build.VERSION.SDK_INT >= 29) return 0L
    val file = File(path)
    val key = "$path|${file.length()}|${file.lastModified()}|${info.frames.timeAt(0)}"
    synchronized(retrieverOffsets) { retrieverOffsets[key]?.let { return it } }
    val extractor = MediaExtractor()
    val offset = try {
        extractor.setDataSource(path)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        if (track == null) 0L else {
            extractor.selectTrack(track)
            val times = ArrayList<Long>()
            while (times.size < RETRIEVER_OFFSET_SAMPLES && extractor.sampleTime >= 0) {
                times += extractor.sampleTime
                if (!extractor.advance()) break
            }
            platformTimelineOffsetUs(times, info.frames.timeAt(0))
        }
    } catch (_: Exception) { 0L } finally { extractor.release() }
    synchronized(retrieverOffsets) {
        retrieverOffsets[key] = offset
        while (retrieverOffsets.size > 8) retrieverOffsets.remove(retrieverOffsets.keys.first())
    }
    return offset
}

private const val RETRIEVER_OFFSET_SAMPLES = 64
private val retrieverOffsets = LinkedHashMap<String, Long>()

/** The earliest presentation time among the platform's first (decode-order) samples, relative to Media3's first frame. */
internal fun platformTimelineOffsetUs(platformSampleTimesUs: List<Long>, firstFrameUs: Long): Long =
    platformSampleTimesUs.minOrNull()?.let { it - firstFrameUs } ?: 0L

internal actual suspend fun exportDeviceVideo(context: Any?, path: String, info: VideoEditInfo,
    document: MosaicDocument, output: String, onProgress: (Float) -> Unit) {
    validateVideoEditDocument(document, info)
    val appContext = requireNotNull(context as? Context)
    // Transformer must be used from one looper thread. Running it on the main looper made
    // Transformer.cancel() (which waits for its internal threads to release the codecs)
    // freeze the UI whenever the user cancelled an export.
    val thread = android.os.HandlerThread("futacha-video-export").apply { start() }
    val looper = thread.looper
    try {
        val handler = android.os.Handler(looper)
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                // After quitSafely the looper drops posts; finish the block instead of losing it.
                if (!handler.post(block)) Dispatchers.IO.dispatch(context, block)
            }
        }
        withContext(dispatcher) {
            try {
                exportWithDecoder(appContext, looper, path, info, document, output, MediaCodecSelector.DEFAULT, onProgress)
            } catch (failure: ExportException) {
                if (failure.errorCode !in setOf(ExportException.ERROR_CODE_DECODER_INIT_FAILED, ExportException.ERROR_CODE_DECODING_FAILED)) throw failure
                currentCoroutineContext().ensureActive()
                // A vendor decoder can fail after initialization. Retry once with software decoding only.
                check(!File(output).exists() || File(output).delete()) { "失敗した書き出しを削除できません" }
                val software = MediaCodecSelector { mime, secure, tunneling -> MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).filter { it.softwareOnly } }
                exportWithDecoder(appContext, looper, path, info, document, output, software, onProgress)
            }
        }
    } finally {
        thread.quitSafely()
    }
}

private suspend fun exportWithDecoder(context: Context, looper: android.os.Looper, path: String, info: VideoEditInfo, document: MosaicDocument,
    output: String, decoder: MediaCodecSelector, onProgress: (Float) -> Unit) {
    val done = CompletableDeferred<Unit>()
    val transformer = Transformer.Builder(context)
        .setLooper(looper)
        .setAssetLoaderFactory(unrotatedVideoLoader(context, decoder))
        .setMuxerFactory(InAppMp4Muxer.Factory().setVideoDurationUs(info.frames.durationUs - info.frames.timeAt(0)))
        .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
        .setEncoderFactory(DefaultEncoderFactory.Builder(context).setEnableFallback(false).build())
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) { done.complete(Unit) }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { done.completeExceptionally(exportException) }
        }).build()
    val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(path))))
        .setEffects(Effects(emptyList(), buildList {
            // Apply rotation before user masks, without relying on codec-specific Surface rotation.
            if (info.rotationDegrees != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees((360 - info.rotationDegrees).toFloat()).build())
            add(MosaicEffect(AtomicReference(document), info.frames.timeAt(0)))
        })).build()
    val composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build()).build()
    try {
        transformer.start(composition, output)
        val progress = ProgressHolder()
        while (!done.isCompleted) {
            if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progress.progress / 100f)
            delay(100)
        }
        done.await(); onProgress(1f)
    } finally { transformer.cancel() }
}

/** Some older software decoders ignore the rotation hint when rendering to a Surface. */
private fun unrotatedVideoLoader(context: Context, selector: MediaCodecSelector): AssetLoader.Factory {
    val delegate = DefaultDecoderFactory.Builder(context).setMediaCodecSelector(selector).setEnableDecoderFallback(true).build()
    val decoder = object : Codec.DecoderFactory by delegate {
        override fun createForVideoDecoding(format: Format, outputSurface: Surface, requestSdrToneMapping: Boolean, logSessionId: LogSessionId?): Codec =
            delegate.createForVideoDecoding(format.buildUpon().setRotationDegrees(0).build(), outputSurface, requestSdrToneMapping, logSessionId)
    }
    val factory = ExoPlayerAssetLoader.Factory(context, decoder, androidx.media3.common.util.Clock.DEFAULT)
    return AssetLoader.Factory { item, looper, listener, settings ->
        factory.createAssetLoader(item, looper, object : AssetLoader.Listener by listener {
            override fun onTrackAdded(inputFormat: Format, supportedOutputTypes: Int): Boolean =
                listener.onTrackAdded(inputFormat.buildUpon().setRotationDegrees(0).build(), supportedOutputTypes)
            override fun onOutputFormat(format: Format): SampleConsumer? =
                listener.onOutputFormat(format.buildUpon().setRotationDegrees(0).build())
        }, settings)
    }
}
