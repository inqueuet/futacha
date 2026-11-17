@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha.shared.media.analysis

import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.*
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath

/** Native decoding, ONNX inference, tracking and temporal masks run together on both platforms. */
object VideoAutomaticEditContract {
    suspend fun detectionContoursReviewAndIntervalPreservation(context: Any? = null) = withVideo(context) { source, info, store, gate, permit ->
        val first = info.frames.timeAt(2)
        val end = info.frames.timeAt(8)
        val manual = MosaicRegion("manual", endUs = info.frames.durationUs,
            masks = listOf(MosaicMaskKeyframe(info.frames.timeAt(0), MosaicMask.FULL)))
        val original = MosaicDocument(listOf(manual))
        val result = VideoSensitiveAnalyser.detect(source, info, original, first, end, DetectionSettings(contours = true),
            store, gate, permit) { _, _ -> }
        check(result.regions.first() === manual)
        val detected = result.regions.drop(1)
        check(detected.map { it.label }.toSet() == setOf("男性器候補", "女性器候補"))
        for (i in 2 until 8) for (label in setOf("男性器候補", "女性器候補")) {
            val time = info.frames.timeAt(i)
            val region = detected.single { it.label == label && it.activeAt(time) }
            check(region.trajectory!!.let { trajectory -> (0 until trajectory.size).any { trajectory.timeAt(it) == time } })
            check(region.masks.any { it.timeUs == time })
            check(region.maskAt(time)?.let { !it.isEmpty && it.coverage in .1f.. .5f } == true)
        }
        check(result.review?.confirmed == false)
        check(runCatching { validateVideoEditDocument(result, info) }.isFailure)
        val confirmed = result.copy(review = result.review!!.copy(confirmed = true))
        validateVideoEditDocument(confirmed, info)
        check(confirmed.update(manual.id) { it.copy(style = MosaicStyle.BLACK) }.review?.confirmed == false)
        val segmented = VideoSensitiveAnalyser.contours(source, info, confirmed, setOf(manual.id), first, end, store, gate, permit) { _, _ -> }
        val corrected = segmented.regions.first()
        check(corrected.maskAt(info.frames.timeAt(1)) === MosaicMask.FULL)
        check(corrected.maskAt(first)?.let { it.coverage in .1f.. .5f } == true)
        check(corrected.maskAt(end) === MosaicMask.FULL)
        check(corrected.masks.map { it.timeUs } == listOf(info.frames.timeAt(0)) + (2 until 8).map { info.frames.timeAt(it) } + end)
        check(segmented.review?.confirmed == false)
        check(original.regions.single() === manual && original.review == null)
    }

    suspend fun cancellationAndVideoOffNeverPublishPartialAnalysis(context: Any? = null) = withVideo(context) { source, info, store, gate, permit ->
        val original = MosaicDocument(listOf(MosaicRegion("manual", endUs = info.frames.durationUs)))
        var published: MosaicDocument? = null
        coroutineScope {
            val task = launch {
                published = VideoSensitiveAnalyser.detect(source, info, original, info.frames.timeAt(0), info.frames.timeAt(4),
                    DetectionSettings(contours = true), store, gate, permit) { message, _ ->
                    if (message.startsWith("輪郭を")) throw CancellationException("Cancel after detection during contour extraction")
                }
            }
            task.join(); check(task.isCancelled)
        }
        check(published == null && original.review == null && original.regions.single().masks.isEmpty())
        check(source.useFile { inspectDeviceVideo(it) }.frames.size == info.frames.size)
        coroutineScope {
            val task = launch {
                published = VideoSensitiveAnalyser.detect(source, info, original, info.frames.timeAt(0), info.frames.timeAt(4),
                    DetectionSettings(), store, gate, permit) { _, progress ->
                    if (progress != null) gate.update(MediaFeatureSettings(imageEditorEnabled = true, promptDisplayEnabled = true))
                }
            }
            task.join(); check(task.isCancelled)
        }
        check(published == null && original.regions.size == 1 && original.review == null)
    }

    private suspend fun withVideo(context: Any?, block: suspend (VideoEditSource, VideoEditInfo, ModelStore, MediaFeatureGate, MediaFeaturePermit) -> Unit) {
        val fs = createFileSystem(context)
        val root = "video_automatic_edit_contract"
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val permit = checkNotNull(gate.permit(MediaFeature.VIDEO_EDITOR))
        val bytes = mapOf(AnalysisModel.NUDE_NET to InferenceFixtures.detector(),
            AnalysisModel.MOBILE_SAM_ENCODER to InferenceFixtures.samEncoder(), AnalysisModel.MOBILE_SAM_DECODER to InferenceFixtures.samDecoder())
        val specs = bytes.map { (id, data) ->
            val hash = data.toByteString().sha256().hex()
            AnalysisModelSpec(id, "fixture", "fixture", "https://model.test", data.size.toLong(), hash,
                ModelDistribution("https://model.test/$id.onnx", data.size.toLong(), hash))
        }
        val store = ModelStore(gate, { fs.resolveAbsolutePath("$root/models").toPath() },
            downloader = { ModelDownloader { _, _ -> error("Automatic editing must not download models") } }, models = specs)
        try {
            fs.writeBytes("$root/original.mp4", VideoEditFixtures.bytes("variable")).getOrThrow()
            val source = fs.readByteStream("$root/original.mp4") {
                VideoEditSource.import(fs, fs.resolveAbsolutePath("$root/work"), "selected.mp4", it, gate, permit)
            }.getOrThrow()
            try {
                val info = source.useFile { inspectDeviceVideo(it) }
                check(runCatching { VideoSensitiveAnalyser.detect(source, info, MosaicDocument(), info.frames.timeAt(0), info.frames.timeAt(4),
                    DetectionSettings(), store, gate, permit) { _, _ -> } }.isFailure)
                for ((id, data) in bytes) store.import(id, permit) { Buffer().write(data) }
                block(source, info, store, gate, permit)
                check(fs.readBytes("$root/original.mp4").getOrThrow().contentEquals(VideoEditFixtures.bytes("variable")))
            } finally { source.close() }
        } finally { store.closeAndAwait(); fs.deleteRecursively(root).getOrThrow() }
    }
}
