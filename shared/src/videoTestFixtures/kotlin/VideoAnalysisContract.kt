@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha.testing.video

import com.valoser.futacha.shared.media.analysis.AnalysisFrame
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.MosaicDocument
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.*
import kotlin.math.abs

/** Runs the same real decoder assertions on Android and iOS. Synthetic videos only. */
object VideoAnalysisContract {
    suspend fun timingRotationAndRange(path: (String) -> String) {
        for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated")) {
            val input = path(name)
            val info = inspectDeviceVideo(input)
            val times = ArrayList<Long>()
            var referenceFrame: AnalysisFrame? = null
            diagnoseTimes("$name full") { readVideoAnalysisFrames(input, info, maximumEdge = 160) { frame ->
                check(frame.timeUs == info.frames.timeAt(times.size)) { "$name PTS ${frame.timeUs}" }
                times += frame.timeUs
                check(frame.width == if (info.width > info.height) 160 else 120)
                check(frame.height == if (info.width > info.height) 120 else 160)
                if (times.size == 1) referenceFrame = frame
            } }
            check(times.toLongArray().contentEquals(info.frames.timestampsUs)) { "$name missing frames" }
            val frame = checkNotNull(referenceFrame)
            // Independent native thumbnail API applies rotation. Sample well inside each quadrant.
            val preview = previewDeviceVideo(input, info, info.frames.timeAt(0), MosaicDocument())
            val pixels = IntArray(preview.width * preview.height).also { preview.readPixels(it) }
            for ((x, y) in listOf(.25 to .25, .75 to .25, .25 to .75, .75 to .75)) {
                val original = pixels[(y * preview.height).toInt() * preview.width + (x * preview.width).toInt()]
                val offset = ((y * frame.height).toInt() * frame.width + (x * frame.width).toInt()) * 3
                for (channel in 0..2) {
                    val delta = abs((original ushr (16 - channel * 8) and 255) - (frame.rgb[offset + channel].toInt() and 255))
                    check(delta < 35) { "$name upright raster $x/$y channel=$channel delta=$delta expected=${original.toUInt().toString(16)} actual=${frame.rgb.slice(offset until offset + 3).map { it.toInt() and 255 }}" }
                }
            }
            val first = info.frames.size / 2; val last = minOf(first + 3, info.frames.size - 1)
            val slice = ArrayList<Long>()
            diagnoseTimes("$name range") { readVideoAnalysisFrames(input, info, info.frames.timeAt(first) - 1, info.frames.timeAt(last), 80, false) {
                check(it.rgb.isEmpty() && it.gray.isNotEmpty() && maxOf(it.width, it.height) == 80)
                slice += it.timeUs
            } }
            check(slice == (first until last).map { info.frames.timeAt(it) }) { "$name half-open interval $slice" }
            var count = 0
            readVideoAnalysisFrames(input, info, info.frames.timeAt(0) + 1, info.frames.timeAt(1)) { count++ }
            check(count == 0)
        }
    }

    suspend fun cancellationReleasesDecoderAndNextReadStartsNormally(path: String) {
        val info = inspectDeviceVideo(path)
        var seen = 0
        try {
            diagnoseTimes("cancellation first read") { readVideoAnalysisFrames(path, info) { if (++seen == 2) throw CancellationException("cancel frame consumer") } }
            error("Cancellation must propagate")
        } catch (_: CancellationException) { check(seen == 2) }
        var reopened = 0
        diagnoseTimes("cancellation reopen") { readVideoAnalysisFrames(path, info, info.frames.timeAt(0), info.frames.timeAt(2)) { reopened++ } }
        check(reopened == 2)
    }

    suspend fun featureOffCancelsOwnedDecoderAndNeverRemovesDeviceOriginal(path: String, root: String, context: Any? = null) {
        val fs = createFileSystem(context)
        val enabled = MediaFeatureSettings(videoEditorEnabled = true)
        val gate = MediaFeatureGate().apply { update(enabled) }
        val original = fs.readBytes(path).getOrThrow()
        val source = fs.readByteStream(path) {
            VideoEditSource.import(fs, root, "selected.mp4", it, gate, checkNotNull(gate.permit(MediaFeature.VIDEO_EDITOR)))
        }.getOrThrow()
        try {
            val info = source.useFile { inspectDeviceVideo(it) }
            coroutineScope {
                val entered = CompletableDeferred<Unit>()
                val operation = async {
                    try {
                        source.analysisFrames(info) { entered.complete(Unit); awaitCancellation() }
                        error("Feature OFF must stop analysis")
                    } catch (_: CancellationException) { }
                }
                withTimeout(15_000) { entered.await() }
                gate.update(enabled.copy(promptDisplayEnabled = true, imageEditorEnabled = true))
                yield(); check(!operation.isCompleted && fs.exists(source.path))
                gate.update(MediaFeatureSettings.Disabled)
                withTimeout(5_000) { operation.await() }
            }
        } finally { source.close() }
        check(!fs.exists(source.path))
        check(fs.readBytes(path).getOrThrow().contentEquals(original))
    }

    suspend fun brokenAndRemoteInputsFailWithoutFrames(reference: String, broken: String) {
        val info = inspectDeviceVideo(reference)
        for (path in listOf(broken, "https://example.invalid/video.mp4")) {
            var failure: Exception? = null
            var frames = 0
            try { readVideoAnalysisFrames(path, info) { frames++ } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (e: Exception) { failure = e }
            check(failure != null && frames == 0) { "Invalid input must fail before delivering analysis frames" }
        }
    }

    private suspend fun diagnoseTimes(label: String, block: suspend () -> Unit) {
        try { block() } catch (failure: VideoAnalysisTimingException) {
            throw IllegalStateException("$label expected=${failure.expectedTimeUs} actual=${failure.actualTimeUs}", failure)
        }
    }
}
