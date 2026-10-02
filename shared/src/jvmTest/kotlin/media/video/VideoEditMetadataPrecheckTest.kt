package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.desktop.DesktopEnvironment
import com.valoser.futacha.shared.media.*
import com.valoser.futacha.shared.media.prompt.PreservedVideoMetadata
import com.valoser.futacha.shared.media.video.model.*
import com.valoser.futacha.shared.util.createFileSystem
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class VideoEditMetadataPrecheckTest {
    /** B-6: an unpreservable source used to be rejected only after the whole encode. */
    @Test fun unknownLengthWebmIsRejectedBeforeTheEncoderStarts() = runBlocking {
        val root = Files.createTempDirectory("futacha-video-precheck").toFile()
        val environment = DesktopEnvironment(root, File(root, "cache"))
        val fs = createFileSystem(environment)
        val gate = MediaFeatureGate().apply { update(MediaFeatureSettings(videoEditorEnabled = true)) }
        // A browser recorder writes its Cluster with an unknown length, so later tags cannot be proven absent.
        val input = File(root, "recorded.webm").apply {
            writeBytes(webm(uint(0x1f43b675) + byteArrayOf(0xff.toByte()) + ByteArray(64)))
        }
        var source: VideoEditSource? = null
        try {
            source = fs.readByteStream(input.absolutePath) { reader ->
                VideoEditSource.import(fs, "video_edit_sessions", input.name, reader, gate, gate.permit(MediaFeature.VIDEO_EDITOR)!!)
            }.getOrThrow()
            val info = VideoEditInfo(100, 100, buildVideoFrameIndex(listOf(0, 100), 200), false, false, 0)
            val document = MosaicDocument(listOf(MosaicRegion("mask", endUs = 200, style = MosaicStyle.BLACK)))
            var progressed = false
            val failure = assertFailsWith<IllegalArgumentException> {
                source.export(null, fs, info, document) { progressed = true }
            }
            assertEquals("元動画の生成情報を最後まで読み取れませんでした。情報を保持して保存するため、別の元ファイルを選んでください", failure.message)
            assertFalse(progressed)
            val sessionFiles = File(source.path).parentFile.list().orEmpty().toList()
            assertEquals(listOf("input.webm"), sessionFiles)
        } finally { source?.close(); environment.closeAndAwait(); root.deleteRecursively() }
    }

    @Test fun preservableSourceReturnsTheSameRecordTheWriterUses() = runBlocking {
        val root = Files.createTempDirectory("futacha-video-precheck-ok").toFile()
        try {
            val input = File(root, "input.mp4").apply { writeBytes(VideoEditFixtures.bytes("variable")) }
            val record: PreservedVideoMetadata = requirePreservableVideoMetadata(input.absolutePath)
            assertTrue(record.fields.any { it.key == "prompt" && it.value.contains("9007199254740993") })
            assertContentEquals(VideoEditFixtures.bytes("variable"), input.readBytes())
        } finally { root.deleteRecursively() }
    }

    private fun uint(value: Long, count: Int = 4) = ByteArray(count) { (value ushr ((count - it - 1) * 8)).toByte() }
    private fun ebml(id: Long, payload: ByteArray): ByteArray {
        val idSize = (1..4).first { id ushr (it * 8) == 0L }
        val sizeLength = (1..8).first { payload.size < (1L shl (7 * it)) - 1 }
        return uint(id, idSize) + uint(payload.size.toLong() or (1L shl (sizeLength * 7)), sizeLength) + payload
    }
    private fun webm(body: ByteArray): ByteArray =
        ebml(0x1a45dfa3, ebml(0x4282, "webm".encodeToByteArray())) + ebml(0x18538067, body)
}
