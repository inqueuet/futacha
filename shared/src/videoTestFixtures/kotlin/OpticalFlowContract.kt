@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha.testing.video

import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.*
import kotlin.math.abs

/** The same reference texture/motion is run through both native OpenCV bindings. */
object OpticalFlowContract {
    suspend fun actualTimelineManualAnchorsAndCancellation(context: Any? = null) {
        val fs = createFileSystem(context)
        val root = "video_tracking_contract"
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        try {
            fs.writeBytes("$root/original.mp4", VideoEditFixtures.bytes("variable")).getOrThrow()
            val source = fs.readByteStream("$root/original.mp4") {
                VideoEditSource.import(fs, fs.resolveAbsolutePath("$root/work"), "selected.mp4", it, gate,
                    checkNotNull(gate.permit(MediaFeature.VIDEO_EDITOR)))
            }.getOrThrow()
            try {
                val info = source.useFile { inspectDeviceVideo(it) }
                val manualTime = info.frames.timeAt(10)
                val manual = MosaicBounds(.4f, .4f, .25f, .25f)
                val region = MosaicRegion("tracking", endUs = info.frames.durationUs,
                    keyframes = listOf(MosaicKeyframe(info.frames.timeAt(0), MosaicBounds.DEFAULT), MosaicKeyframe(manualTime, manual)))
                val original = MosaicDocument(listOf(region))
                val forward = trackVideoRegion(source, info, original, region.id, info.frames.timeAt(0), true) { _, _ -> }
                val backward = trackVideoRegion(source, info, forward, region.id, info.frames.timeAt(info.frames.size - 1), false) { _, _ -> }
                for (result in listOf(forward, backward)) {
                    val output = result.regions.single()
                    val trajectory = checkNotNull(output.trajectory)
                    check(trajectory.size == info.frames.size)
                    check((0 until trajectory.size).all { trajectory.timeAt(it) == info.frames.timeAt(it) })
                    check(output.keyframes == region.keyframes && output.boundsAt(manualTime) == manual)
                    check(trajectory.boundsAt(manualTime) == manual)
                    check(result.review?.confirmed == false)
                    check(runCatching { validateVideoEditDocument(result, info) }.isFailure)
                }
                coroutineScope {
                    val cancelled = launch {
                        trackVideoRegion(source, info, original, region.id, info.frames.timeAt(0), true) { _, _ ->
                            throw CancellationException("User cancelled tracking")
                        }
                        error("Cancelled tracking must not publish a document")
                    }
                    cancelled.join(); check(cancelled.isCancelled)
                }
                check(original.regions.single().trajectory == null && original.review == null)
                check(source.useFile { inspectDeviceVideo(it) }.frames.size == info.frames.size)
                check(fs.readBytes("$root/original.mp4").getOrThrow().contentEquals(VideoEditFixtures.bytes("variable")))
            } finally { source.close() }
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }

    fun translationLossAndExplicitReseed() {
        val initial = MosaicBounds(.4375f, .4791667f, .4f, .5f)
        OpticalFlowTracker().use { tracker ->
            tracker.seed(frame(0, 0, 0), initial)
            repeat(8) { n ->
                val result = tracker.step(frame((n + 1) * 2, n + 1, (n + 1) * 33_333L))
                check(!result.uncertain) { "Lost translated texture at frame $n" }
                check(abs(result.bounds.centerX - initial.centerX - (n + 1) * 2f / 320) < .012f)
                check(abs(result.bounds.centerY - initial.centerY - (n + 1) / 240f) < .012f)
                check(abs(result.bounds.width - initial.width) < .02f)
            }
            val lost = tracker.step(frame(0, 0, 300_000, flat = true))
            check(lost.uncertain)
            check(tracker.step(frame(20, 10, 333_333)) == lost) // No silent reacquisition.
            tracker.seed(frame(0, 0, 366_666), initial)
            check(!tracker.step(frame(2, 1, 400_000)).uncertain)
            check(tracker.step(frame(4, 2, 433_333), sceneCut = true).uncertain)
        }
        val closed = OpticalFlowTracker()
        closed.close(); closed.close()
        check(runCatching { closed.seed(frame(0, 0, 0), initial) }.isFailure)
    }

    private fun frame(dx: Int, dy: Int, time: Long, flat: Boolean = false): AnalysisFrame {
        val width = 320; val height = 240
        val gray = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val xx = x - dx; val yy = y - dy
            val cell = (xx / 7 * 73856093) xor (yy / 7 * 19349663)
            gray[y * width + x] = (if (flat || xx !in 70..210 || yy !in 50..180) 20 else 40 + (cell and 127)).toByte()
        }
        return AnalysisFrame(time, width, height, ByteArray(0), gray)
    }
}
