package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.*
import okio.Buffer
import kotlin.test.*

class PreservedImageMetadataTest {
    private val raw = "  猫 🐈\r\nsecond line  \r\nNegative prompt: bad  \r\nSteps: 20, Sampler: Euler, Seed: 9007199254740993\r\n"
    private val emptyJpeg = byteArrayOf(-1, -40, -1, -39)

    @Test fun rawScanDoesNotInterpretPromptsAndEditedJpegRetainsExactTags(): Unit = runBlocking {
        val workflow = """{"unknown":{"seed":9007199254740993,"widgets_values":["猫\r\n"]}}"""
        val input = png("parameters" to raw, "workflow" to workflow, "Comment" to "ordinary family photo")
        val collector = GenerationFieldCollector(preserveOnly = true)
        val result = ImageGenerationMetadataReader().readFields(input.size.toLong(), { start, count ->
            input.copyOfRange(start.toInt(), start.toInt() + count)
        }, collector, MetadataReadBudget())
        assertTrue(result.candidates.isEmpty())
        assertEquals(listOf(raw, workflow, "ordinary family photo"), collector.fields.map { it.value })
        val metadata = PreservedImageMetadata.scan(input)
        assertFalse(metadata.partial)
        val jpeg = preserveEditedImageMetadata(emptyJpeg, metadata, 48, 80)
        assertEquals(metadata, PreservedImageMetadata.scan(jpeg))
        val candidate = read(jpeg).candidates.single()
        assertEquals(raw, candidate.raw)
        assertEquals("  猫 🐈\r\nsecond line  ", candidate.positive)
        assertEquals("bad  ", candidate.negative)
        assertTrue(candidate.settings!!.contains("9007199254740993"))
        // Interoperable UserComment remains readable without Futacha's APP15 record.
        val standardOnly = jpeg(segments(jpeg).filter { it.first != 0xef })
        assertEquals(raw, read(standardOnly).candidates.single().raw)
    }

    @Test fun jpegAndWebpExifRoundTripWithoutCopyingSourceImageBlocks(): Unit = runBlocking {
        for (source in listOf(GenerationImageFixtures.jpeg, GenerationImageFixtures.webp, GenerationImageFixtures.jpegXmp)) {
            val metadata = PreservedImageMetadata.scan(source)
            val output = preserveEditedImageMetadata(emptyJpeg, metadata, 8, 8)
            assertEquals(metadata, PreservedImageMetadata.scan(output))
            assertEquals(read(source).candidates.map { it.raw }, read(output).candidates.map { it.raw })
            assertEquals(setOf(0xe1, 0xef), segments(output).map { it.first }.toSet())
        }
    }

    @Test fun freshExifHasOutputDimensionsAndNoThumbnailOrOldOrientation(): Unit = runBlocking {
        val thumbnailSecret = "UNEDITED_THUMBNAIL_CONTENT"
        // IFD0 points to a thumbnail IFD containing an ImageDescription and extra image data.
        val tiff = Buffer().writeUtf8("MM").writeShort(42).writeInt(8).writeShort(1)
            .writeShort(0x0112).writeShort(3).writeInt(1).writeInt(6 shl 16).writeInt(26)
            .writeShort(1).writeShort(0x010e).writeShort(2).writeInt(thumbnailSecret.length + 1).writeInt(44)
            .writeInt(0).writeUtf8(thumbnailSecret).writeByte(0).readByteArray()
        val input = jpeg(listOf(0xe1 to ("Exif\u0000\u0000".encodeToByteArray() + tiff)))
        val metadata = PreservedImageMetadata.scan(input)
        assertTrue(metadata.fields.isEmpty())
        val output = preserveEditedImageMetadata(emptyJpeg, metadata, 48, 80)
        assertFalse(output.decodeToString().contains(thumbnailSecret))
        val exif = segments(output).single().second.copyOfRange(6, segments(output).single().second.size)
        assertEquals(4, metadataUint(exif, 8, 2).toInt())
        assertEquals(48, metadataUint(exif, 18, 4).toInt())
        assertEquals(80, metadataUint(exif, 30, 4).toInt())
        assertEquals(1, metadataUint(exif, 42, 2).toInt())
        assertEquals(0, metadataUint(exif, 58, 4).toInt(), "No next/thumbnail IFD")
    }

    @Test fun xmpKeepsGenerationTextAndDeclarationsButNotEmbeddedPreviews(): Unit = runBlocking {
        val uri = "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"
        val xml = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="$RDF_NS"><rdf:Description xmlns:p="urn:test" xmlns:i="http://iptc.org/std/Iptc4xmpExt/2008-02-29/"><p:parameters>cat
Steps: 1, Sampler: Euler, Seed: 4</p:parameters><i:DigitalSourceType rdf:resource="$uri"/><p:Thumbnails><p:image>UNEDITED_PREVIEW</p:image></p:Thumbnails></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val input = jpeg(listOf(0xe1 to ("http://ns.adobe.com/xap/1.0/\u0000$xml".encodeToByteArray())))
        val metadata = PreservedImageMetadata.scan(input)
        assertEquals(2, metadata.fields.size)
        assertFalse(metadata.encode().decodeToString().contains("UNEDITED_PREVIEW"))
        val output = preserveEditedImageMetadata(emptyJpeg, metadata, 8, 8)
        assertEquals(2, read(output).candidates.size)
        assertEquals(AiDeclaration.GENERATED, read(output).candidates.last().declaration)
    }

    @Test fun largeFieldsUseMultipleSegmentsAndRepeatedEditingDoesNotNestRecords(): Unit = runBlocking {
        val value = "猫".repeat(50_000) + "\r\n9007199254740993"
        val metadata = PreservedImageMetadata(fields = listOf(PreservedImageMetadata.Field("workflow", value, "PNG iTXt")))
        var output = preserveEditedImageMetadata(emptyJpeg, metadata, 32, 32)
        assertTrue(segments(output).count { it.first == 0xef } > 1)
        val length = output.size
        repeat(3) {
            assertEquals(metadata, PreservedImageMetadata.scan(output))
            assertTrue(read(output).candidates.isEmpty(), "Unknown workflow is preserved, not classified as AI")
            output = preserveEditedImageMetadata(emptyJpeg, PreservedImageMetadata.scan(output), 32, 32)
            assertEquals(length, output.size)
        }
    }

    @Test fun missingDuplicatedReorderedAndCorruptRecordSegmentsNeverProducePartialPrompt(): Unit = runBlocking {
        val metadata = PreservedImageMetadata(fields = listOf(PreservedImageMetadata.Field("parameters", "cat".repeat(30_000) + "\nSteps: 1, Sampler: Euler, Seed: 1", "PNG iTXt")))
        val parts = segments(preserveEditedImageMetadata(emptyJpeg, metadata, 8, 8))
        val fragments = parts.filter { it.first == 0xef }
        val corrupt = fragments.mapIndexed { index, pair -> if (index == 0) pair.first to pair.second.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        } else pair }
        for (broken in listOf(fragments.dropLast(1), fragments + fragments.last(), fragments.reversed(), corrupt)) {
            val result = read(jpeg(broken))
            assertEquals(MetadataCoverage.MALFORMED, result.coverage)
            assertTrue(result.candidates.isEmpty())
        }
        val excessive = fragments.first().second.copyOf().also {
            val p = IMAGE_METADATA_SIGNATURE.size
            it[p + 2] = 127; it[p + 3] = -1
        }
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(jpeg(listOf(0xef to excessive))).coverage)
    }

    @Test fun preservationLimitsKeepWholeFieldsAndSignalOmissions(): Unit = runBlocking {
        val input = png("parameters" to raw, "workflow" to "a".repeat(MAX_IMAGE_METADATA_BYTES + 1))
        val metadata = PreservedImageMetadata.scan(input)
        assertTrue(metadata.partial)
        assertEquals(listOf(raw), metadata.fields.map { it.value })
        val output = preserveEditedImageMetadata(emptyJpeg, metadata, 8, 8)
        assertTrue(PreservedImageMetadata.scan(output).partial)
        assertEquals(raw, read(output).candidates.single().raw)
        assertFailsWith<InvalidMetadata> { PreservedImageMetadata.decode("{\"version\":2,\"fields\":[]}".encodeToByteArray()) }
        assertFailsWith<InvalidMetadata> { PreservedImageMetadata.decode(("[".repeat(12) + "]".repeat(12)).encodeToByteArray()) }
    }

    @Test fun imagePixelsAreNotMetadataAndCancelledPreservationDoesNotReturnOutput(): Unit = runBlocking {
        val input = png().let { it.copyOfRange(0, 8) + chunk("IDAT", "ALPHA_HIDDEN_PROMPT".encodeToByteArray()) + it.copyOfRange(8, it.size) }
        assertTrue(PreservedImageMetadata.scan(input).fields.isEmpty())
        assertFailsWith<CancellationException> {
            withContext(Job().apply { cancel() }) { PreservedImageMetadata.scan(input) }
        }
        assertFailsWith<CancellationException> {
            withContext(Job().apply { cancel() }) { preserveEditedImageMetadata(emptyJpeg, PreservedImageMetadata(), 8, 8) }
        }
    }

    private suspend fun read(bytes: ByteArray) = ImageGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
        bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
    }
    private fun segments(bytes: ByteArray): List<Pair<Int, ByteArray>> = buildList {
        var p = 2
        while (p < bytes.size && bytes[p + 1] != 0xd9.toByte()) {
            val n = metadataUint(bytes, p + 2, 2).toInt()
            add((bytes[p + 1].toInt() and 255) to bytes.copyOfRange(p + 4, p + 2 + n)); p += n + 2
        }
    }
    private fun jpeg(parts: List<Pair<Int, ByteArray>>) = Buffer().writeShort(0xffd8).apply {
        parts.forEach { (code, data) -> writeShort(0xff00 or code).writeShort(data.size + 2).write(data) }
    }.writeShort(0xffd9).readByteArray()
    private fun png(vararg fields: Pair<String, String>) = Buffer().write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)).apply {
        fields.forEach { (key, value) -> write(chunk("iTXt", key.encodeToByteArray() + byteArrayOf(0, 0, 0, 0, 0) + value.encodeToByteArray())) }
    }.write(chunk("IEND", byteArrayOf())).readByteArray()
    private fun chunk(type: String, data: ByteArray) = Buffer().writeInt(data.size).writeUtf8(type).write(data)
        .writeInt(pngCrc(type.encodeToByteArray() + data).toInt()).readByteArray()
}
