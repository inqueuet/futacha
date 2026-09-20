package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.state.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.media.video.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.ui.board.DesktopVideoSession
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.shared.media.analysis.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import java.io.File
import kotlin.test.*

class DesktopRuntimeTest {
    @Test fun modeSwitchKeepsTutorialAndDoesNotResurrectDeletedBoards() = runBlocking {
        val directory = Files.createTempDirectory("futacha-profile-test").toFile()
        val environment = DesktopEnvironment(directory, File(directory, "cache"))
        environment.install()
        val graph = DesktopAppGraph(environment)
        try {
            graph.initialize()
            val tutorial = graph.stateStore.boards.first()
            val real = BoardSummary("b", "二次元裏", "", "https://may.2chan.net/b/", "")
            graph.stateStore.setBoards(tutorial + real)
            graph.synchronizeBoards(ExperienceProfile.FUTACHA)
            graph.switchTo(ExperienceProfile.TOSHIAKI_COMPAT)
            assertEquals(2, graph.compatibility.boards.first().size)
            assertTrue(graph.compatibility.boards.first().any { "example.com" in it.originalUrl })
            graph.switchTo(ExperienceProfile.FUTACHA)
            graph.stateStore.setBoards(tutorial)
            graph.switchTo(ExperienceProfile.TOSHIAKI_COMPAT)
            assertEquals(1, graph.compatibility.boards.first().size)
            graph.compatibility.deleteBoard(graph.compatibility.boards.first().single().key)
            graph.switchTo(ExperienceProfile.FUTACHA)
            assertTrue(graph.stateStore.boards.first().isEmpty())
        } finally { graph.close(); directory.deleteRecursively() }
    }

    @Test fun automaticDetectionContoursTrackingAndCancellationUseNativeRuntime() = runBlocking {
        System.setProperty("futacha.resourcesDir", File("../app-desktop/resources/${DesktopPlatform.resourceDirectory}").canonicalPath)
        val directory = Files.createTempDirectory("futacha-analysis-test").toFile()
        val environment = DesktopEnvironment(directory, File(directory, "cache"))
        try {
            com.valoser.futacha.testing.video.OpticalFlowContract.translationLossAndExplicitReseed()
            com.valoser.futacha.testing.video.OpticalFlowContract.actualTimelineManualAnchorsAndCancellation(environment)
            com.valoser.futacha.shared.media.analysis.VideoAutomaticEditContract.detectionContoursReviewAndIntervalPreservation(environment)
            com.valoser.futacha.shared.media.analysis.VideoAutomaticEditContract.cancellationAndVideoOffNeverPublishPartialAnalysis(environment)
        } finally { environment.closeAndAwait(); directory.deleteRecursively() }
    }

    @Test fun persistentSettingsAndCompatibilitySurviveReopen() = runBlocking {
        val directory = Files.createTempDirectory("futacha-store-test").toFile()
        val environment = DesktopEnvironment(directory, File(directory, "cache"))
        try {
            val fs = createFileSystem(environment)
            val state = createAppStateStore(environment, fs)
            state.setNgWords(listOf("日本語NG"))
            state.setManualSaveDirectory("/tmp/保存テスト")
            val secondState = createAppStateStore(environment, fs)
            assertEquals(listOf("日本語NG"), secondState.ngWords.first())
            assertEquals("/tmp/保存テスト", secondState.manualSaveDirectory.first())
            val store = DesktopCompatibilityStore(fs)
            store.initialize()
            val board = BoardSummary("b", "二次元裏", "", "https://may.2chan.net/b/", "")
            store.bootstrapBoardsIfNeeded(listOf(board))
            val compatBoard = store.boards.first().single()
            val url = "https://may.2chan.net/b/res/123.htm"
            val tab = CompatTab(compatTabKey(url), url, url, compatBoard.key, compatBoard.name, "123", "日本語スレッド", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1)
            store.openTab(tab, null)
            store.savePreference("compat.commonUsedVersion", "11.1")
            store.updateScrollAnchor(tab.key, ScrollAnchor(postNo = "123", offsetPx = 25, fallbackIndex = 2))
            store.saveDraft(CompatReplyDraft(tab.key, comment = "下書き", updatedAtEpochMillis = 2))
            store.close()
            val reopened = DesktopCompatibilityStore(fs)
            reopened.initialize()
            assertEquals("日本語スレッド", reopened.tabs.first().single().title)
            assertEquals(25, reopened.tabs.first().single().scrollAnchor.offsetPx)
            assertEquals("下書き", reopened.loadDraft(tab.key)?.comment)
            assertEquals("11.1", reopened.loadPreference("compat.commonUsedVersion"))
            reopened.close()
        } finally { environment.closeAndAwait(); directory.deleteRecursively() }
    }

    @Test fun editedVideoKeepsVfrTimelineAudioAndRotation() = runBlocking {
        val directory = Files.createTempDirectory("futacha-video-test").toFile()
        try {
            for (name in listOf("landscape", "portrait", "variable", "rotated", "asymmetric-rotated", "webm")) {
                val input = File(directory, "$name.mp4").apply { writeBytes(if (name == "webm") com.valoser.futacha.testing.video.VideoPlaybackFixtures.webm() else VideoEditFixtures.bytes(name)) }
                val info = inspectDeviceVideo(input.absolutePath)
                val document = MosaicDocument(listOf(MosaicRegion("mask", endUs = info.frames.durationUs,
                    style = MosaicStyle.BLACK, keyframes = listOf(MosaicKeyframe(0, MosaicBounds(.5f, .5f, .5f, .5f))))))
                val output = File(directory, "$name-edited.mp4")
                exportDeviceVideo(null, input.absolutePath, info, document, output.absolutePath) {}
                val edited = inspectDeviceVideo(output.absolutePath)
                verifyVideoEditTiming(info, edited)
                assertEquals(0, edited.rotationDegrees)
                if (info.hasAudio) assertEquals(audioSignature(input), audioSignature(output), "$name decoded audio samples and timestamps")
                var count = 0
                readVideoAnalysisFrames(output.absolutePath, edited, maximumEdge = 64) { frame ->
                    val i = (frame.height / 2 * frame.width + frame.width / 2) * 3
                    assertTrue((0..2).all { (frame.rgb[i + it].toInt() and 255) < 25 }, "$name center should be black")
                    count++
                }
                assertEquals(info.frames.size, count)
                println("DESKTOP_VIDEO_EDIT $name frames=$count audio=${edited.hasAudio} duration=${edited.frames.durationUs}")
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun playerDecodesVideoAndRejectsBrokenInput() = runBlocking {
        val directory = Files.createTempDirectory("futacha-player-test").toFile()
        System.setProperty("futacha.resourcesDir", File("../app-desktop/resources/${DesktopPlatform.resourceDirectory}").canonicalPath)
        try {
            for (name in listOf("landscape", "webm", "portrait")) {
                val input = File(directory, "$name.mp4").apply { writeBytes(if (name == "webm") com.valoser.futacha.testing.video.VideoPlaybackFixtures.webm() else VideoEditFixtures.bytes(name)) }
                var width = 0
                val player = DesktopVideoSession { _, w, _ -> width = w }
                try {
                    player.play(input.absolutePath)
                    println("PLAYER_STARTED $name")
                    withTimeout(10_000) { while (!player.hasFrame.get() && !player.failed.get()) delay(30) }
                    assertFalse(player.failed.get(), name)
                    assertTrue(width > 0, name)
                } finally { player.close() }
            }
            val bad = File(directory, "bad.mp4").apply { writeText("broken-video") }
            val player = DesktopVideoSession { _, _, _ -> fail("Broken input produced a frame") }
            try {
                player.play(bad.absolutePath)
                withTimeout(10_000) { while (!player.failed.get()) delay(30) }
                assertFalse(player.hasFrame.get())
            } finally { player.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun webmWithVorbisAndOpusKeepsAudibleTimingWhenExported() = runBlocking {
        val directory = Files.createTempDirectory("futacha-webm-audio-test").toFile()
        try {
            val original = File(directory, "source.mp4").apply { writeBytes(VideoEditFixtures.bytes("landscape")) }
            val executable = org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.ffmpeg::class.java)
            for (codec in listOf("vorbis", "libopus")) {
                val input = File(directory, "$codec.webm")
                val log = File(directory, "$codec.log")
                val process = ProcessBuilder(executable, "-nostdin", "-v", "error", "-y", "-i", original.absolutePath,
                    "-t", "0.6", "-vf", "scale=160:-2", "-c:v", "libvpx", "-deadline", "realtime", "-cpu-used", "8",
                    "-ac", "2", "-c:a", codec, "-strict", "-2", input.absolutePath).redirectErrorStream(true).redirectOutput(log).start()
                try {
                    withTimeout(30_000) { while (process.isAlive) delay(50) }
                    assertEquals(0, process.exitValue(), log.readText())
                } finally { process.destroyForcibly(); process.waitFor() }
                val info = inspectDeviceVideo(input.absolutePath)
                val output = File(directory, "$codec-edited.mp4")
                exportDeviceVideo(null, input.absolutePath, info, MosaicDocument(), output.absolutePath) {}
                val edited = inspectDeviceVideo(output.absolutePath)
                verifyVideoEditTiming(info, edited)
                assertTrue(edited.hasAudio)
                val before = audioSignature(input).first
                val after = audioSignature(output).first
                assertTrue(before.isNotEmpty() && after.isNotEmpty())
                assertTrue(kotlin.math.abs((before.first() - info.frames.timeAt(0)) - after.first()) <= 50_000, "$codec audio start")
                assertTrue(kotlin.math.abs((before.last() - info.frames.timeAt(0)) - after.last()) <= 50_000, "$codec audio end")
                val power = audioPower(input)
                assertTrue(power > 0.000001, "$codec fixture must be audible")
                assertTrue(kotlin.math.abs(audioPower(output) / power - 1) < .15, "$codec audio waveform power")
            }
        } finally { directory.deleteRecursively() }
    }

    private fun audioPower(file: File): Double {
        var sum = 0.0
        var count = 0L
        org.bytedeco.javacv.FFmpegFrameGrabber(file).use { grabber ->
            grabber.sampleFormat = org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT
            grabber.start()
            while (true) {
                val frame = grabber.grabSamples() ?: break
                frame.samples.forEach { sample ->
                    val buffer = (sample as java.nio.FloatBuffer).duplicate()
                    while (buffer.hasRemaining()) { val value = buffer.get().toDouble(); sum += value * value; count++ }
                }
            }
        }
        return sum / count.coerceAtLeast(1)
    }
    private fun audioSignature(file: File): Pair<List<Long>, String> {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        val times = mutableListOf<Long>()
        org.bytedeco.javacv.FFmpegFrameGrabber(file).use { grabber ->
            grabber.sampleFormat = org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLT
            grabber.start()
            while (true) {
                val frame = grabber.grabSamples() ?: break
                times += frame.timestamp
                frame.samples.forEach { sample ->
                    val buffer = (sample as java.nio.FloatBuffer).duplicate()
                    while (buffer.hasRemaining()) {
                        val bits = buffer.get().toRawBits()
                        for (shift in intArrayOf(0, 8, 16, 24)) hash.update((bits ushr shift).toByte())
                    }
                }
            }
        }
        return times to hash.digest().joinToString("") { "%02x".format(it) }
    }

}
