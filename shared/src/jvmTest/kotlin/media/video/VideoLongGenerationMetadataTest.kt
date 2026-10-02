package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/** B-7: a source whose long tags pass the pre-export check must also pass the post-export check. */
class VideoLongGenerationMetadataTest {
    @Test fun longGenerationMetadataThatPassesThePrecheckIsWrittenAndVerified(): Unit = runBlocking {
        withDirectory { directory ->
            val value = parameters(1_500_000)
            val input = File(directory, "input.mp4").apply { writeBytes(sourceWith(value)) }
            val output = File(directory, "output.mp4").apply { writeBytes(VideoEditFixtures.bytes("rotated")) }
            assertEquals(value, requirePreservableVideoMetadata(input.absolutePath).fields.single().value)
            // Previously failed here: the copied tag plus the record exceeded the 8MB read-back budget.
            preserveEditedVideoMetadata(input.absolutePath, output.absolutePath, 2)
            val bytes = output.readBytes()
            val saved = PreservedVideoMetadata.fromScan(VideoMetadataReader(16 * 1024 * 1024).read(bytes.size.toLong()) { offset, count ->
                bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            })
            assertEquals(listOf(PreservedVideoMetadata.Field("parameters", value)), saved.fields)
            assertEquals(2, saved.edits.single().regionCount)
        }
    }

    @Test fun recordThatCannotBeAppendedIsRejectedBeforeEncoding(): Unit = runBlocking {
        withDirectory { directory ->
            // The source scan accepts these tags, but their record exceeds the preservation limit.
            val input = File(directory, "input.mp4").apply { writeBytes(sourceWith(parameters(2_400_000))) }
            val failure = assertFailsWith<IllegalArgumentException> { requirePreservableVideoMetadata(input.absolutePath) }
            assertEquals("元動画の生成情報が保存上限を超えています", failure.message)
        }
    }

    @Test fun everySourceThePrecheckAcceptsCanBeWritten(): Unit = runBlocking {
        withDirectory { directory ->
            for (length in listOf(1_000_000, 1_300_000, 1_800_000, 1_950_000, 2_050_000)) {
                val input = File(directory, "input-$length.mp4").apply { writeBytes(sourceWith(parameters(length))) }
                val output = File(directory, "output-$length.mp4").apply { writeBytes(VideoEditFixtures.bytes("rotated")) }
                val accepted = try { requirePreservableVideoMetadata(input.absolutePath); true }
                catch (failure: IllegalArgumentException) {
                    assertEquals("元動画の生成情報が保存上限を超えています", failure.message); false
                }
                if (accepted) preserveEditedVideoMetadata(input.absolutePath, output.absolutePath, 16)
                input.delete(); output.delete()
            }
        }
    }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("video-long-metadata").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }
    /** ASCII text with quotes and line breaks, which JSON escaping makes longer in the record. */
    private fun parameters(length: Int): String {
        val unit = "masterpiece, \"best quality\", Steps: 20, Sampler: Euler a\n"
        return buildString(length) { while (this.length < length) append(unit) }.take(length)
    }
    private fun uint(value: Long) = ByteArray(4) { (value ushr ((3 - it) * 8)).toByte() }
    private fun box(type: String, payload: ByteArray) = uint(payload.size + 8L) + type.encodeToByteArray() + payload
    private fun sourceWith(value: String): ByteArray {
        val keys = uint(0) + uint(1) + box("mdta", "parameters".encodeToByteArray())
        val data = box("data", uint(1) + uint(0) + value.encodeToByteArray())
        val ilst = uint(data.size + 8L) + uint(1) + data
        val handler = box("hdlr", uint(0) + uint(0) + "mdta".encodeToByteArray() + ByteArray(13))
        val meta = box("meta", uint(0) + handler + box("keys", keys) + box("ilst", ilst))
        return box("ftyp", "isom".encodeToByteArray() + uint(0) + "isom".encodeToByteArray()) + box("moov", box("udta", meta))
    }
}
