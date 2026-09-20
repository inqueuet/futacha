package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoAnalysisContract
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class VideoAnalysisFramesNativeTest {
    @Test fun sequentialDecoderKeepsEveryVfrFrameRotationAndExclusiveRange() = runBlocking {
        val fs = createFileSystem(); val root = "video_analysis_contract"
        try {
            for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated")) {
                fs.writeBytes("$root/$name.mp4", VideoEditFixtures.bytes(name)).getOrThrow()
            }
            VideoAnalysisContract.timingRotationAndRange { fs.resolveAbsolutePath("$root/$it.mp4") }
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }
    @Test fun cancellingConsumerReleasesNativeDecoderForReuse() = runBlocking {
        val fs = createFileSystem(); val root = "video_analysis_cancellation"
        try {
            val path = "$root/input.mp4"
            fs.writeBytes(path, VideoEditFixtures.bytes("variable")).getOrThrow()
            VideoAnalysisContract.cancellationReleasesDecoderAndNextReadStartsNormally(fs.resolveAbsolutePath(path))
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }
    @Test fun disablingFeatureCancelsReadingAndPreservesSelectedOriginal() = runBlocking {
        val fs = createFileSystem(); val root = "video_analysis_feature_off"
        try {
            fs.writeBytes("$root/selected.mp4", VideoEditFixtures.bytes("variable")).getOrThrow()
            VideoAnalysisContract.featureOffCancelsOwnedDecoderAndNeverRemovesDeviceOriginal(
                fs.resolveAbsolutePath("$root/selected.mp4"), fs.resolveAbsolutePath("$root/work"))
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }
    @Test fun malformedAndRemoteInputsFailWithoutPartialAnalysis() = runBlocking {
        val fs = createFileSystem(); val root = "video_analysis_invalid"
        try {
            val bytes = VideoEditFixtures.bytes("landscape")
            fs.writeBytes("$root/input.mp4", bytes).getOrThrow()
            fs.writeBytes("$root/broken.mp4", bytes.copyOf(24)).getOrThrow()
            VideoAnalysisContract.brokenAndRemoteInputsFailWithoutFrames(fs.resolveAbsolutePath("$root/input.mp4"), fs.resolveAbsolutePath("$root/broken.mp4"))
        } finally { fs.deleteRecursively(root).getOrThrow() }
    }
}
