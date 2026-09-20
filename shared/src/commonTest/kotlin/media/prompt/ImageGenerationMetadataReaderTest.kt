package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.*
import kotlin.test.*

class ImageGenerationMetadataReaderTest {
    private val positive = "  猫と犬 🐈\r\nsecond line  \t"
    private val negative = "ぼけ  "
    private val raw = "$positive\r\nNegative prompt: $negative\r\nSteps: 20, Sampler: Euler, Seed: 9007199254740993\r\n"

    @Test fun pillowEncodedJpegAndWebpReadNestedExifIfd(): Unit = runBlocking {
        for ((bytes, coverage) in listOf(GenerationImageFixtures.jpeg to MetadataCoverage.JPEG_METADATA,
            GenerationImageFixtures.webp to MetadataCoverage.WEBP_METADATA)) {
            val result = read(bytes)
            assertEquals(coverage, result.coverage)
            assertEquals(GenerationImageFixtures.positive, result.candidates.single().positive)
            assertEquals("blurry", result.candidates.single().negative)
        }
        val latin = "Ã©\nSteps: 1, Sampler: Euler, Seed: 0"
        assertEquals("Ã©", read(png("tEXt" to ("parameters\u0000$latin".map { it.code.toByte() }.toByteArray()))).candidates.single().positive)
    }

    @Test fun jpegExifUnicodePreservesTextAcrossTiffOrderAndBom(): Unit = runBlocking {
        for (tiffLittle in listOf(false, true)) for (textLittle in listOf(false, true)) for (bom in listOf(false, true)) {
            val comment = "UNICODE\u0000".encodeToByteArray() + utf16(raw + "\u0000", textLittle, bom)
            val result = read(jpeg(0xe1 to exif(tiff(0x9286 to comment, little = tiffLittle))))
            assertEquals(MetadataCoverage.JPEG_METADATA, result.coverage)
            val candidate = result.candidates.single()
            assertEquals(positive, candidate.positive)
            assertEquals(negative, candidate.negative)
            assertEquals(raw, candidate.raw)
            assertTrue(candidate.settings!!.contains("9007199254740993"))
            assertTrue(candidate.source.contains("JPEG EXIF UserComment"))
        }
    }

    @Test fun exifInlineXpCommentAndOrdinaryCameraFieldsDoNotCauseFalseLabels(): Unit = runBlocking {
        val ordinary = tiff(0x010e to "family photograph\u0000".encodeToByteArray(), 0x010f to "Cam\u0000".encodeToByteArray(),
            0x0110 to "prompt: meeting notes\u0000".encodeToByteArray(), 0x9286 to "ASCII\u0000\u0000\u0000ordinary caption".encodeToByteArray())
        assertTrue(read(jpeg(0xe1 to exif(ordinary), 0xfe to "cat".encodeToByteArray())).candidates.isEmpty())
        val xp = read(jpeg(0xe1 to exif(tiff(0x9c9c to utf16(raw + "\u0000", true, false)))))
        assertEquals(positive, xp.candidates.single().positive)
        val ascii = "cat  \nNegative prompt: bad\nSteps: 1, Sampler: Euler, Seed: 1"
        for ((tag, bytes) in listOf(0x9286 to ("ASCII\u0000\u0000\u0000$ascii\u0000".encodeToByteArray()), 0x010e to ascii.encodeToByteArray())) {
            assertEquals("cat  ", read(jpeg(0xe1 to exif(tiff(tag to bytes)))).candidates.single().positive)
        }
    }

    @Test fun comfyExifModelAndUtf8PngTextRecoverOnlyKnownGraphs(): Unit = runBlocking {
        val graph = """{"1":{"class_type":"CLIPTextEncode","inputs":{"text":"猫 🐈  "}},"2":{"class_type":"CLIPTextEncode","inputs":{"text":"bad"}},"3":{"class_type":"KSampler","inputs":{"positive":["1",0],"negative":["2",0],"seed":2}}}"""
        for (tag in listOf(0x010f, 0x0110)) {
            val result = read(webp("EXIF" to tiff(tag to "prompt:$graph\u0000".encodeToByteArray())))
            assertEquals("猫 🐈  ", result.candidates.single().positive)
            assertEquals("bad", result.candidates.single().negative)
            assertTrue(result.hasAiEvidence)
        }
        assertEquals("猫 🐈  ", read(png("tEXt" to "prompt\u0000$graph".encodeToByteArray())).candidates.single().positive)
        assertFalse(read(webp("EXIF" to tiff(0x0110 to "prompt:{\"prompt\":\"meeting\"}".encodeToByteArray()))).hasAiEvidence)
    }

    @Test fun pngExifAndXmpAreCollectedAlongsideExistingText(): Unit = runBlocking {
        val result = read(png("eXIf" to tiff(0x9286 to ("UNICODE\u0000".encodeToByteArray() + utf16(raw, false, false))),
            "iTXt" to ("XML:com.adobe.xmp".encodeToByteArray() + byteArrayOf(0, 0, 0, 0, 0) + xmp("<p:parameters>${xml(raw)}</p:parameters>").encodeToByteArray())))
        assertEquals(MetadataCoverage.PNG_METADATA, result.coverage)
        assertEquals(2, result.candidates.size)
        assertEquals(positive, result.candidates[0].positive)
        assertEquals(positive.replace("\r\n", "\n"), result.candidates[1].positive)
    }

    @Test fun webpSkipsLargeImagePayloadAndChecksRiffPaddingAndBounds(): Unit = runBlocking {
        val bytes = webp("VP8 " to ByteArray(1024 * 1024 + 1), "EXIF" to exif(tiff(0x9c9c to utf16(raw, true, true))))
        var reads = 0
        val result = ImageGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
            reads += count
            bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
        assertEquals(MetadataCoverage.WEBP_METADATA, result.coverage)
        assertEquals(positive, result.candidates.single().positive)
        assertTrue(reads < 4096, "Must seek past encoded pixels instead of duplicating the image in memory")
        val padding = webp("JUNK" to byteArrayOf(42)).also { it[it.lastIndex] = 1 }
        assertEquals(MetadataCoverage.MALFORMED, read(padding).coverage)
        assertEquals(MetadataCoverage.MALFORMED, read(bytes.copyOf(bytes.size - 1)).coverage)
        assertEquals(MetadataCoverage.MALFORMED, read("RIFF".encodeToByteArray() + le32(0xfffffff0L) + "WEBP".encodeToByteArray()).coverage)
        assertEquals(MetadataCoverage.WEBP_METADATA, read(webp("JUNK" to byteArrayOf(42)) + byteArrayOf(99)).coverage)
    }

    @Test fun xmpReadsAttributesElementsCdataAndSeparateLanguageAlternatives(): Unit = runBlocking {
        val value = "  cat & dog  \nSteps: 1, Sampler: Euler, Seed: 4"
        val variants = listOf(
            xmp("<p:parameters>${xml(value)}</p:parameters>"),
            xmp("", "p:parameters=\"${xml(value).replace("\n", "&#10;")}\""),
            xmp("<p:parameters><![CDATA[$value]]></p:parameters>"),
            xmp("<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">${xml(value)}</rdf:li><rdf:li xml:lang=\"ja\">${xml(value.replace("cat & dog", "猫と犬"))}</rdf:li></rdf:Alt></dc:description>")
        )
        for (packet in variants) {
            val result = read(jpeg(0xe1 to xmpApp1(packet)))
            assertEquals(MetadataCoverage.JPEG_METADATA, result.coverage)
            assertEquals("  cat & dog  ", result.candidates.first().positive)
            assertTrue(result.candidates.all { it.source.contains("XMP") })
        }
        assertEquals(2, read(webp("XMP " to variants.last().encodeToByteArray())).candidates.size)
    }

    @Test fun xmpAiEvidenceRequiresExactNamespaceAndValueAndPreservesEditingMeaning(): Unit = runBlocking {
        val uri = "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia"
        val editing = uri.replace("trainedAlgorithmicMedia", "compositeWithTrainedAlgorithmicMedia")
        val packet = xmp("<iptc:DigitalSourceType rdf:resource=\"$uri\"/>" +
            "<p:group xmlns:iptc=\"urn:unrelated\"><iptc:DigitalSourceType>$uri</iptc:DigitalSourceType></p:group>" +
            "<iptc:DigitalSourceType>$editing</iptc:DigitalSourceType>")
        val result = read(webp("XMP " to packet.encodeToByteArray()))
        assertEquals(2, result.candidates.size)
        assertTrue(result.candidates[0].source.endsWith("AI生成の申告"))
        assertTrue(result.candidates[1].source.endsWith("AI編集の申告"))
        assertEquals(AiDeclaration.GENERATED, result.candidates[0].declaration)
        assertEquals(AiDeclaration.EDITED, result.candidates[1].declaration)
        assertTrue(generationInlineText(result.candidates[1]).contains("AI編集の記録"))
        assertFalse(generationInlineText(result.candidates[0]).contains("http"))
        assertTrue(result.candidates.all { it.positive == null && it.isAi })
        for (attributes in listOf("p:DigitalSourceType=\"$uri\"", "DigitalSourceType=\"$uri\"", "iptc:DigitalSourceType=\"https://wrong.invalid/trainedAlgorithmicMedia\"")) {
            assertFalse(read(webp("XMP " to xmp("", attributes).encodeToByteArray())).hasAiEvidence)
        }
        assertFalse(read(webp("XMP " to xmp("<dc:description>family photo</dc:description><p:prompt>{\"prompt\":\"meeting\"}</p:prompt>").encodeToByteArray())).hasAiEvidence)
    }

    @Test fun xmpRejectsDtdExternalEntitiesAndMalformedPacketsWithoutLosingOtherSources(): Unit = runBlocking {
        val packets = listOf(
            "<!DOCTYPE x [<!ENTITY steal SYSTEM 'file:///unreadable'>]><x>&steal;</x>",
            "<!DOCTYPE x SYSTEM 'https://unreachable.invalid/a.dtd'><x/>",
            "<x><unclosed></x>",
            "<x>&unknown;</x>"
        )
        for (packet in packets) {
            val result = read(jpeg(0xe1 to xmpApp1(packet), 0xfe to raw.encodeToByteArray()))
            assertEquals(MetadataCoverage.MALFORMED, result.coverage)
            assertEquals(positive, result.candidates.single().positive)
        }
    }

    @Test fun malformedExifCyclesOffsetsAndSegmentsAreBounded(): Unit = runBlocking {
        val cyclic = tiff(0x010f to "Cam\u0000".encodeToByteArray()).also { put32(it, 22, 8, true) }
        val invalidOffset = tiff(0x010e to "long enough".encodeToByteArray()).also { put32(it, 18, 0xffffffffL, true) }
        for (data in listOf(cyclic, invalidOffset, byteArrayOf(73, 73, 42, 0))) {
            val result = read(jpeg(0xe1 to exif(data), 0xfe to raw.encodeToByteArray()))
            assertEquals(MetadataCoverage.MALFORMED, result.coverage)
            assertEquals(positive, result.candidates.single().positive)
        }
        assertEquals(MetadataCoverage.MALFORMED, read(byteArrayOf(-1, -40, -1, -31, 0, 1)).coverage)
        assertEquals(MetadataCoverage.MALFORMED, read(byteArrayOf(-1, -40, -1, -31, 127, -1)).coverage)
        val paddedJpeg = byteArrayOf(-1, -40, -1, -1, 1, -1, -48) + jpeg(0xfe to raw.encodeToByteArray()).drop(2).toByteArray()
        assertEquals(positive, read(paddedJpeg).candidates.single().positive)
    }

    @Test fun unsupportedEncodingsExtendedXmpAndComplexPropertiesAreExplicit(): Unit = runBlocking {
        val result = read(jpeg(0xe1 to exif(tiff(0x9286 to "JIS\u0000\u0000\u0000\u0000\u0000anything".encodeToByteArray())),
            0xe1 to xmpApp1(xmp("<p:parameters><p:nested>text</p:nested></p:parameters>", "note:HasExtendedXMP=\"0123456789abcdef\"")),
            0xfe to raw.encodeToByteArray()))
        assertEquals(setOf(MetadataLimitation.EXIF_ENCODING, MetadataLimitation.EXTENDED_XMP, MetadataLimitation.COMPLEX_XMP), result.limitations)
        assertEquals(positive, result.candidates.single().positive)
    }

    @Test fun metadataAllocationAndXmlDepthAreBounded(): Unit = runBlocking {
        val large = webp("EXIF" to ByteArray(MAX_METADATA_BYTES + 1))
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(large).coverage)
        val deep = xmp("<p:x>".repeat(40) + "a" + "</p:x>".repeat(40))
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(webp("XMP " to deep.encodeToByteArray())).coverage)
        val many = xmp("<p:x/>".repeat(1100))
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(webp("XMP " to many.encodeToByteArray())).coverage)
        val inflate = xmp("<p:parameters>" + "&#65;".repeat(60000) + "</p:parameters>")
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, read(webp("XMP " to inflate.encodeToByteArray())).coverage)
    }

    @Test fun xmpUtf16AndNumericNewlinesRemainValidAndBadSurrogatesFail(): Unit = runBlocking {
        val packet = xmp("<p:parameters>${xml(raw).replace("\r", "&#13;")}</p:parameters>")
        for (little in listOf(false, true)) {
            val candidate = read(webp("XMP " to utf16(packet, little, true))).candidates.single()
            assertEquals(positive, candidate.positive)
            assertEquals(negative, candidate.negative)
        }
        val invalid = "UNICODE\u0000".encodeToByteArray() + byteArrayOf(-2, -1, -40, 0)
        assertEquals(MetadataCoverage.MALFORMED, read(webp("EXIF" to tiff(0x9286 to invalid))).coverage)
    }

    @Test fun cancellationPropagatesThroughContainerAndXmlCallbacks(): Unit = runBlocking {
        assertFailsWith<CancellationException> { ImageGenerationMetadataReader().read(100) { _, _ -> throw CancellationException("stop") } }
        val bytes = webp("XMP " to xmp("<p:x/>".repeat(100)).encodeToByteArray())
        val job = Job()
        assertFailsWith<CancellationException> {
            withContext(job) {
                ImageGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
                    if (count > 100) job.cancel()
                    bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
                }
            }
        }
        var callbacks = 0
        val sink = XmpEventSink(MetadataReadBudget()) { if (++callbacks == 5) throw CancellationException("XML stop") }
        assertFailsWith<CancellationException> { parseXmpDocument(xmp("<p:x/>".repeat(10)).encodeToByteArray(), sink) }
    }

    private suspend fun read(bytes: ByteArray) = ImageGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
        bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
    }
    private fun exif(data: ByteArray) = "Exif\u0000\u0000".encodeToByteArray() + data
    private fun xmpApp1(xml: String) = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray() + xml.encodeToByteArray()
    private fun jpeg(vararg segments: Pair<Int, ByteArray>) = byteArrayOf(-1, -40) + segments.fold(byteArrayOf()) { acc, (marker, body) ->
        acc + byteArrayOf(-1, marker.toByte(), ((body.size + 2) shr 8).toByte(), (body.size + 2).toByte()) + body
    } + byteArrayOf(-1, -39)
    private fun webp(vararg chunks: Pair<String, ByteArray>): ByteArray {
        val payload = chunks.fold(byteArrayOf()) { acc, (kind, body) ->
            acc + kind.encodeToByteArray() + le32(body.size.toLong()) + body + if (body.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
        }
        return "RIFF".encodeToByteArray() + le32(payload.size + 4L) + "WEBP".encodeToByteArray() + payload
    }
    private fun png(vararg chunks: Pair<String, ByteArray>) = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) +
        (chunks.toList() + ("IEND" to byteArrayOf())).fold(byteArrayOf()) { acc, (kind, body) ->
            acc + le32(body.size.toLong()).reversedArray() + kind.encodeToByteArray() + body + le32(pngCrc(kind.encodeToByteArray() + body)).reversedArray()
        }
    private fun tiff(vararg tags: Pair<Int, ByteArray>, little: Boolean = true): ByteArray {
        val tableEnd = 8 + 2 + tags.size * 12 + 4
        val bytes = ByteArray(tableEnd + tags.sumOf { if (it.second.size > 4) it.second.size else 0 })
        bytes[0] = if (little) 73 else 77; bytes[1] = bytes[0]
        fun u16(p: Int, n: Int) {
            bytes[p] = (if (little) n else n shr 8).toByte()
            bytes[p + 1] = (if (little) n shr 8 else n).toByte()
        }
        u16(2, 42); put32(bytes, 4, 8, little); u16(8, tags.size)
        var payload = tableEnd
        tags.forEachIndexed { index, (tag, value) ->
            val p = 10 + index * 12
            u16(p, tag); u16(p + 2, if (tag == 0x9286) 7 else 2); put32(bytes, p + 4, value.size.toLong(), little)
            if (value.size <= 4) value.copyInto(bytes, p + 8)
            else { put32(bytes, p + 8, payload.toLong(), little); value.copyInto(bytes, payload); payload += value.size }
        }
        return bytes
    }
    private fun put32(bytes: ByteArray, p: Int, value: Long, little: Boolean) { (if (little) le32(value) else le32(value).reversedArray()).copyInto(bytes, p) }
    private fun le32(n: Long) = ByteArray(4) { (n shr (it * 8)).toByte() }
    private fun utf16(text: String, little: Boolean, bom: Boolean) =
        (if (bom) if (little) byteArrayOf(-1, -2) else byteArrayOf(-2, -1) else byteArrayOf()) +
            text.flatMap { c -> if (little) listOf(c.code.toByte(), (c.code shr 8).toByte()) else listOf((c.code shr 8).toByte(), c.code.toByte()) }.toByteArray()
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")
    private fun xmp(content: String, attributes: String = "") = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="$RDF_NS"><rdf:Description xmlns:p="urn:test:parameters" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:iptc="http://iptc.org/std/Iptc4xmpExt/2008-02-29/" xmlns:note="http://ns.adobe.com/xmp/note/" $attributes>$content</rdf:Description></rdf:RDF></x:xmpmeta>"""
}
