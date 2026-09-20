package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.*
import okio.Buffer
import okio.Deflater
import okio.DeflaterSink
import okio.buffer
import okio.use
import kotlin.test.*

class PngGenerationMetadataReaderTest {
    @Test fun a1111PreservesPositiveWhitespaceAndSeparatesNegativeAndSettings(): Unit = runBlocking {
        val positive = "  cat\r\nline two  \r\n"
        val negative = "blurry  \r\nsecond negative\t"
        val settings = "Steps: 20, Sampler: Euler a, CFG scale: 7, Seed: 42\r\n"
        val raw = "$positive\r\nNegative prompt: $negative\r\n$settings"
        val result = read(png("tEXt" to text("parameters", raw)))
        assertEquals(MetadataCoverage.PNG_METADATA, result.coverage)
        val candidate = result.candidates.single()
        assertEquals(positive, candidate.positive)
        assertEquals(negative, candidate.negative)
        assertEquals(settings, candidate.settings)
        assertEquals(raw, candidate.raw)
        assertTrue(result.hasAiEvidence)
    }

    @Test fun ordinaryCommentsAndUnknownPromptJsonNeverProduceAiLabels(): Unit = runBlocking {
        val result = read(png("tEXt" to text("Comment", "my family photo"),
            "tEXt" to text("Description", "Negative prompt: just a quotation"),
            "tEXt" to text("prompt", """{"prompt":"cat"}""")))
        assertTrue(result.candidates.isEmpty())
        assertFalse(result.hasAiEvidence)
        val uncertain = read(png("tEXt" to text("parameters", "cat\nNegative prompt: quote")))
        assertNull(uncertain.candidates.single().positive)
        assertFalse(uncertain.hasAiEvidence)
        assertEquals("cat\nNegative prompt: quote", uncertain.candidates.single().raw)
        val ambiguous = read(png("tEXt" to text("parameters",
            "cat\nNegative prompt: quoted heading\nNegative prompt: bad\nSteps: 1, Sampler: Euler, Seed: 0")))
        assertTrue(ambiguous.hasAiEvidence)
        assertNull(ambiguous.candidates.single().positive, "An ambiguous block must use explicit raw copy")
    }

    @Test fun compressedInternationalAndLatin1TextWorkOnEveryPlatform(): Unit = runBlocking {
        val value = "猫と犬 \nNegative prompt: ぼけ\nSteps: 12, Sampler: Euler, Seed: 4"
        val iText = "parameters".encodeToByteArray() + byteArrayOf(0, 1, 0, 0, 0) + compress(value.encodeToByteArray())
        val international = read(png("iTXt" to iText)).candidates.single()
        assertEquals("猫と犬 ", international.positive)
        assertEquals("ぼけ", international.negative)
        val latin = "caf\u00e9\nSteps: 1, Sampler: Euler, Seed: 0"
        val zText = "parameters".encodeToByteArray() + byteArrayOf(0, 0) + compress(latin.map { it.code.toByte() }.toByteArray())
        assertEquals("café", read(png("zTXt" to zText)).candidates.single().positive)
    }

    @Test fun novelAiRequiresSoftwareAndGenerationSchemaAndPreservesText(): Unit = runBlocking {
        val comment = """{"prompt":"  cat\r\n","uc":"bad  ","steps":20,"sampler":"k_euler"}"""
        assertTrue(read(png("tEXt" to text("Comment", comment))).candidates.isEmpty())
        val candidate = read(png("tEXt" to text("Software", "NovelAI"), "tEXt" to text("Comment", comment))).candidates.single()
        assertEquals("  cat\r\n", candidate.positive)
        assertEquals("bad  ", candidate.negative)
        assertFalse(candidate.settings!!.contains("prompt"))
        assertTrue(candidate.isAi)
    }

    @Test fun comfyKeepsSamplerCandidatesAndDoesNotGuessLinkedText(): Unit = runBlocking {
        val graph = """{"1":{"class_type":"CLIPTextEncode","inputs":{"text":"cat "}},"2":{"class_type":"CLIPTextEncode","inputs":{"text":"bad"}},"3":{"class_type":"KSampler","inputs":{"positive":["1",0],"negative":["2",0],"seed":4}},"4":{"class_type":"KSamplerAdvanced","inputs":{"positive":["4",0],"negative":["2",0]}}}"""
        val result = read(png("tEXt" to text("prompt", graph)))
        assertEquals(2, result.candidates.size)
        assertEquals("cat ", result.candidates[0].positive)
        assertEquals("bad", result.candidates[0].negative)
        assertNull(result.candidates[1].positive, "Cycles/unknown graph links must not be misreported as a prompt")
        assertEquals(graph, result.candidates[1].raw)
        assertTrue(result.hasAiEvidence)
    }

    @Test fun corruptCrcBadLengthsAndUnsupportedFormatsAreDistinct(): Unit = runBlocking {
        val valid = png("tEXt" to text("parameters", "cat\nSteps: 1, Sampler: Euler, Seed: 0"))
        val corrupt = valid.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertEquals(MetadataCoverage.MALFORMED, read(corrupt).coverage)
        assertEquals(MetadataCoverage.MALFORMED, read(valid.copyOf(20)).coverage)
        assertEquals(MetadataCoverage.UNSUPPORTED, read(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())).coverage)
        val invalidUtf8 = "parameters".encodeToByteArray() + byteArrayOf(0, 0, 0, 0, 0, 0xff.toByte())
        assertEquals(MetadataCoverage.MALFORMED, read(png("iTXt" to invalidUtf8)).coverage)
    }

    @Test fun decompressionBombAndDeepJsonAreBounded(): Unit = runBlocking {
        val data = "parameters".encodeToByteArray() + byteArrayOf(0, 0) + compress(ByteArray(1024 * 1024) { 65 })
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(png("zTXt" to data)).coverage)
        val deep = "{\"x\":".repeat(100) + "0" + "}".repeat(100)
        assertTrue(read(png("tEXt" to text("prompt", deep))).candidates.isEmpty())
    }

    @Test fun imagePayloadIsSkippedAndCancellationIsNotReportedAsNoMetadata(): Unit = runBlocking {
        val payload = ByteArray(1024 * 1024)
        val bytes = png("IDAT" to payload, "tEXt" to text("parameters", "cat\nSteps: 1, Sampler: Euler, Seed: 0"))
        var reads = 0
        val metadata = PngGenerationMetadataReader().read(bytes.size.toLong()) { offset, length ->
            reads += length
            bytes.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
        assertTrue(metadata.hasAiEvidence)
        assertTrue(reads < 1024, "Image pixels must not be read or re-encoded for text metadata")
        assertFailsWith<CancellationException> {
            PngGenerationMetadataReader().read(100) { _, _ -> throw CancellationException("cancelled") }
        }
    }

    @Test fun previewAndCopyBudgetsNeverChangeSourceText() {
        val original = "🐈".repeat(400) + "\r\n  tail  "
        val preview = promptPreview(original)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 601)
        assertEquals("🐈".repeat(400) + "\r\n  tail  ", original)
        assertTrue(canCopyPrompt(original))
        assertFalse(canCopyPrompt("猫".repeat(30_000)))
        assertTrue(canCopyPrompt("a".repeat(MAX_COPY_BYTES)))
        assertFalse(canSelectWholePrompt("\n".repeat(1000)), "A small clipboard payload can still create unbounded layout height")
        assertFalse(canSelectWholePrompt("a".repeat(2049)))
        assertTrue(canSelectWholePrompt(original))
    }

    private suspend fun read(bytes: ByteArray) = PngGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
        bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + count))
    }

    private fun text(key: String, value: String) = "$key\u0000$value".encodeToByteArray()
    private fun compress(bytes: ByteArray): ByteArray {
        val target = Buffer()
        DeflaterSink(target, Deflater()).buffer().use { it.write(bytes) }
        return target.readByteArray()
    }
    private fun png(vararg chunks: Pair<String, ByteArray>): ByteArray = Buffer().apply {
        write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        (chunks.toList() + ("IEND" to ByteArray(0))).forEach { (type, data) ->
            writeInt(data.size); writeUtf8(type); write(data); writeInt(pngCrc(type.encodeToByteArray() + data).toInt())
        }
    }.readByteArray()
}
