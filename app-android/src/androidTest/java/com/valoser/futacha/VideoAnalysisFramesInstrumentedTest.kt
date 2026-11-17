@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.testing.video.VideoAnalysisContract
import com.valoser.futacha.testing.video.VideoEditFixtures
import com.valoser.futacha.testing.video.VideoPlaybackFixtures
import com.valoser.futacha.shared.media.video.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

class VideoAnalysisFramesInstrumentedTest {
    @Test fun sequentialDecoderKeepsEveryVfrFrameRotationAndExclusiveRange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "video-analysis-contract").apply { mkdirs() }
        try {
            for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated")) {
                File(root, "$name.mp4").writeBytes(VideoEditFixtures.bytes(name))
            }
            VideoAnalysisContract.timingRotationAndRange { File(root, "$it.mp4").absolutePath }
        } finally { root.deleteRecursively() }
    }
    @Test fun cancellingConsumerReleasesNativeDecoderForReuse() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "video-analysis-cancellation").apply { mkdirs() }
        try {
            val path = File(root, "input.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
            VideoAnalysisContract.cancellationReleasesDecoderAndNextReadStartsNormally(path.absolutePath)
        } finally { root.deleteRecursively() }
        Unit
    }
    @Test fun disablingFeatureCancelsReadingAndPreservesSelectedOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "video-analysis-feature-off").apply { mkdirs() }
        try {
            val input = File(root, "selected.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
            VideoAnalysisContract.featureOffCancelsOwnedDecoderAndNeverRemovesDeviceOriginal(input.absolutePath, File(root, "work").absolutePath, context)
        } finally { root.deleteRecursively() }
    }
    @Test fun malformedAndRemoteInputsFailWithoutPartialAnalysis() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "video-analysis-invalid").apply { mkdirs() }
        try {
            val bytes = VideoEditFixtures.bytes("landscape")
            val input = File(root, "input.mp4").apply { writeBytes(bytes) }
            val broken = File(root, "broken.mp4").apply { writeBytes(bytes.copyOf(24)) }
            VideoAnalysisContract.brokenAndRemoteInputsFailWithoutFrames(input.absolutePath, broken.absolutePath)
        } finally { root.deleteRecursively() }
    }
    @Test fun webmVp8AndVp9ProduceActualDecodedFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "video-analysis-webm").apply { mkdirs() }
        try {
            for ((name, bytes) in listOf("vp8" to VideoPlaybackFixtures.webmVp8(), "vp9" to VideoPlaybackFixtures.webm())) {
                check(bytes.size <= 8 * 1024 * 1024)
                val input = File(root, "$name.webm").apply { writeBytes(bytes) }
                val info = inspectDeviceVideo(input.absolutePath)
                var count = 0
                readVideoAnalysisFrames(input.absolutePath, info) { frame ->
                    check(frame.timeUs == info.frames.timeAt(count++))
                    check(frame.rgb.isNotEmpty() && frame.gray.isNotEmpty())
                }
                check(count == info.frames.size && count > 0)
            }
        } finally { root.deleteRecursively() }
    }
}
