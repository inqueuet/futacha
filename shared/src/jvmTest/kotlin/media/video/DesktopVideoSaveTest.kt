package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.desktop.DesktopEnvironment
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopVideoSaveTest {
    @Test fun ownedSessionExportsMetadataAndSavesToUnicodeDirectory() = runBlocking {
        val root = Files.createTempDirectory("futacha-video-save").toFile()
        val environment = DesktopEnvironment(root, File(root, "cache"))
        val fs = createFileSystem(environment)
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        val input = File(root, "日本語 #50%.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
        var source: VideoEditSource? = null
        try {
            source = fs.readByteStream(input.absolutePath) { reader ->
                VideoEditSource.import(fs, "video_edit_sessions", input.name, reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            }.getOrThrow()
            val before = inspectDeviceVideo(source.path)
            val document = MosaicDocument(listOf(MosaicRegion("mask", endUs = before.frames.durationUs, style = MosaicStyle.BLACK)))
            val output = source.export(null, fs, before, document) {}
            val destination = SaveLocation.Path(File(root, "保存先 #50%").absolutePath)
            val saved = saveEditedVideo(source, fs, output, destination)
            val file = File(destination.path, saved)
            assertTrue(file.isFile)
            verifyVideoEditTiming(before, inspectDeviceVideo(file.absolutePath))
            readVideoAnalysisFrames(file.absolutePath, before, maximumEdge = 64) { frame ->
                val center = (frame.height / 2 * frame.width + frame.width / 2) * 3
                assertTrue((0..2).all { (frame.rgb[center + it].toInt() and 255) < 25 })
            }
            assertContentEquals(VideoEditFixtures.bytes("variable"), input.readBytes())
        } finally { source?.close(); environment.closeAndAwait(); root.deleteRecursively() }
    }
}
