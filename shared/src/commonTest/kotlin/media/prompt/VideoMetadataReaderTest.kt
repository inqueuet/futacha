package com.valoser.futacha.shared.media.prompt

import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.*
import kotlin.test.*

class VideoMetadataReaderTest {
    @Test fun realVp9WebmFixtureReadsItsGenerationComment(): Unit = verifyWebmComment(com.valoser.futacha.testing.video.VideoPlaybackFixtures.webm())
    @Test fun realVp8WebmFixtureReadsItsGenerationComment(): Unit = verifyWebmComment(com.valoser.futacha.testing.video.VideoPlaybackFixtures.webmVp8())

    private fun verifyWebmComment(bytes: ByteArray): Unit = runBlocking {
        val metadata = MediaGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
            bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
        assertEquals(MetadataCoverage.WEBM_METADATA, metadata.coverage)
        assertEquals("青い鳥", metadata.candidates.single().positive)
        assertEquals("blur, low quality", metadata.candidates.single().negative)
        assertContains(metadata.candidates.single().settings!!, "9007199254740993")
    }
    private val positive = "  猫 🐈\r\n日本語と \"quotes\" & <tag>  "
    private val raw = "$positive\r\nNegative prompt: ぼけ  \r\nSteps: 20, Sampler: Euler, Seed: 9007199254740993\r\n"

    @Test fun realMp4FixturesReadTailMetadataWithoutReadingEncodedFrames(): Unit = runBlocking {
        for (name in listOf("portrait", "landscape", "variable")) {
            val bytes = VideoEditFixtures.bytes(name)
            var readBytes = 0
            val metadata = MediaGenerationMetadataReader().read(bytes.size.toLong()) { offset, count ->
                readBytes += count
                bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            }
            assertEquals(MetadataCoverage.MP4_METADATA, metadata.coverage, name)
            val candidate = metadata.candidates.single()
            assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", candidate.positive)
            assertEquals("blur, low quality", candidate.negative)
            assertContains(candidate.settings!!, "9007199254740993")
            assertTrue(readBytes < bytes.size / 2, "$name: Metadata must seek past mdat")
        }
    }

    @Test fun mp4KeysAndFreeformValuesPreserveUnicodeWhitespaceAndFullSeed(): Unit = runBlocking {
        for (utf16 in listOf(false, true)) for (headerless in listOf(false, true)) {
            val bytes = mp4(mdta("parameters" to data(raw, utf16), headerless = headerless))
            val result = scan(bytes).generation()
            assertEquals(MetadataCoverage.MP4_METADATA, result.coverage)
            assertEquals(positive, result.candidates.single().positive)
            assertEquals("ぼけ  ", result.candidates.single().negative)
            assertEquals(raw, result.candidates.single().raw)
        }
        val freeform = atom("----", atom("mean", uint(0) + "com.apple.iTunes".encodeToByteArray()) +
            data(raw) + atom("name", uint(0) + "parameters".encodeToByteArray()))
        val result = scan(mp4(atom("meta", uint(0) + atom("ilst", freeform)))).generation()
        assertEquals(positive, result.candidates.single().positive, "Freeform name may follow data")
    }

    @Test fun quicktimeAndItunesCommentsRequireGenerationSchema(): Unit = runBlocking {
        for (text in listOf(raw, "This video has a prompt: meeting notes")) {
            val encoded = text.encodeToByteArray()
            for (tag in listOf("©cmt", "©des")) {
                val variants = listOf(
                    mp4(atom(tag, uint(encoded.size.toLong(), 2) + uint(0, 2) + encoded)),
                    mp4(atom("meta", uint(0) + atom("ilst", atom(tag, data(text))))),
                    mp4(mdta("com.apple.quicktime.comment" to data(text)))
                )
                for (bytes in variants) {
                    val result = scan(bytes).generation()
                    assertEquals(MetadataCoverage.MP4_METADATA, result.coverage)
                    assertEquals(text == raw, result.hasAiEvidence)
                    if (text == raw) assertEquals(positive, result.candidates.single().positive)
                    else assertTrue(result.candidates.isEmpty())
                }
            }
        }
    }

    @Test fun videoNovelAiNeedsSoftwareAndStructuredComment(): Unit = runBlocking {
        val comment = """{"prompt":"猫 🐈  ","uc":"bad","steps":28,"sampler":"k_euler","seed":9007199254740993}"""
        val fields = listOf("Software" to data("NovelAI"), "Comment" to data(comment))
        val result = scan(mp4(mdta(*fields.toTypedArray()))).generation()
        assertEquals("猫 🐈  ", result.candidates.single().positive)
        assertContains(result.candidates.single().settings!!, "9007199254740993")
        assertFalse(scan(mp4(mdta("Comment" to data(comment)))).generation().hasAiEvidence)
        assertFalse(scan(mp4(mdta("prompt" to data("meeting notes")))).generation().hasAiEvidence)
    }

    @Test fun sparseEightGigabyteMdatIsSkippedAndExtendedAtomSizeIsChecked(): Unit = runBlocking {
        val size = 8L * 1024 * 1024 * 1024
        val header = uint(1) + ascii("mdat") + uint(size, 8)
        val tail = mp4(mdta("parameters" to data(raw)))
        var bytesRead = 0
        val result = VideoMetadataReader().read(size + tail.size) { offset, count ->
            bytesRead += count
            when {
                offset + count <= header.size -> header.copyOfRange(offset.toInt(), offset.toInt() + count)
                offset >= size -> tail.copyOfRange((offset - size).toInt(), (offset - size).toInt() + count)
                else -> error("Encoded video payload was read at $offset")
            }
        }
        assertEquals(MetadataCoverage.MP4_METADATA, result.coverage)
        assertEquals(positive, result.generation().candidates.single().positive)
        assertTrue(bytesRead < 4096)
        assertEquals(MetadataCoverage.MALFORMED, scan(uint(1) + ascii("mdat") + uint(Long.MIN_VALUE, 8)).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(uint(1) + ascii("mdat") + uint(8, 8)).coverage)
    }

    @Test fun mp4TerminalSizeZeroAndTruncatedAtomsAreHandled(): Unit = runBlocking {
        val payload = atom("udta", mdta("parameters" to data(raw)))
        assertEquals(positive, scan(uint(0) + ascii("moov") + payload).generation().candidates.single().positive)
        val valid = mp4(mdta("parameters" to data(raw)))
        for (removed in listOf(1, 5, 15)) assertEquals(MetadataCoverage.MALFORMED, scan(valid.copyOf(valid.size - removed)).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(uint(7) + ascii("moov")).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(uint(1000) + ascii("moov")).coverage)
    }

    @Test fun invalidTextAndDeepOrOversizedMetadataAreBounded(): Unit = runBlocking {
        for (bytes in listOf(byteArrayOf(0xc0.toByte(), 0x80.toByte()), byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte()))) {
            assertEquals(MetadataCoverage.MALFORMED, scan(mp4(mdta("parameters" to atom("data", uint(1) + uint(0) + bytes)))).coverage)
        }
        assertEquals(MetadataCoverage.MALFORMED, scan(mp4(mdta("parameters" to atom("data", uint(2) + uint(0) + byteArrayOf(0))))).coverage)
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, scan(mp4(mdta("parameters" to data("a".repeat(MAX_METADATA_BYTES))))).coverage)
        var deep = atom("free", byteArrayOf())
        repeat(16) { deep = atom("moov", deep) }
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, scan(deep).coverage)
        val many = atom("moov", List(4097) { atom("free", byteArrayOf()) }.fold(byteArrayOf(), ByteArray::plus))
        assertEquals(MetadataCoverage.BUDGET_EXCEEDED, scan(many).coverage)
    }

    @Test fun xmpAndC2paAreReportedWithoutTreatingUnparsedSignatureAsAiEvidence(): Unit = runBlocking {
        val xmp = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:p="urn:test"><p:parameters>cat
Steps: 1, Sampler: Euler, Seed: 4</p:parameters></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val xmpBox = atom("uuid", hex("be7acfcb97a942e89c71999491e3afac") + xmp.encodeToByteArray())
        val c2pa = atom("uuid", hex("d8fec3d61b0e483c92975828877ec481") + byteArrayOf(1, 2, 3))
        val result = scan(mp4(xmpBox + c2pa)).generation()
        assertEquals("cat", result.candidates.single().positive)
        assertContains(result.limitations, MetadataLimitation.C2PA)
        assertFalse(scan(mp4(c2pa)).generation().hasAiEvidence)
    }

    @Test fun webmReadsSeparateNestedTagsWithoutReadingClusterPayload(): Unit = runBlocking {
        val nested = simple("PARENT", "ordinary", simple("parameters", raw))
        val bytes = webm(ebml(0x1f43b675, ByteArray(1024 * 1024)) + tags(nested))
        var bytesRead = 0
        val result = VideoMetadataReader().read(bytes.size.toLong()) { offset, count ->
            bytesRead += count
            bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
        }
        assertEquals(MetadataCoverage.WEBM_METADATA, result.coverage)
        assertEquals(setOf("PARENT", "parameters"), result.fields.map { it.key }.toSet())
        assertEquals(positive, result.generation().candidates.single().positive)
        assertTrue(bytesRead < 4096)
    }

    @Test fun webmSeekPositionsAreRelativeToSegmentPayloadAndTagsAreNotDuplicated(): Unit = runBlocking {
        val target = tags(simple("parameters", raw))
        val placeholder = seek(0x1254c367, 0)
        val cluster = ebml(0x1f43b675, ByteArray(8192))
        val head = seek(0x1254c367, (placeholder.size + cluster.size).toLong())
        for (unknown in listOf(false, true)) {
            val body = head + if (unknown) uint(0x1f43b675) + byteArrayOf(0xff.toByte()) + ByteArray(8192) else cluster
            val fixedHead = seek(0x1254c367, body.size.toLong())
            val bytes = webm(fixedHead + body.copyOfRange(head.size, body.size) + target, unknown = true)
            val result = scan(bytes)
            assertEquals(MetadataCoverage.WEBM_METADATA, result.coverage)
            assertEquals(1, result.fields.count { it.key == "parameters" })
            assertEquals(positive, result.generation().candidates.single().positive)
            assertEquals(unknown, MetadataLimitation.UNKNOWN_WEBM_CLUSTER in result.limitations)
        }
    }

    @Test fun webmRejectsInvalidSeekTargetsUnknownTagLengthsAndSizeOverflows(): Unit = runBlocking {
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(seek(0x1254c367, 0))).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(seek(0x1254c367, Long.MAX_VALUE))).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(uint(0x1254c367) + byteArrayOf(0xff.toByte()))).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(uint(0x1254c367) + byteArrayOf(0))).coverage)
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(uint(0x1254c367) + byteArrayOf(0xfe.toByte()))).coverage)
        val missingName = tags(ebml(0x67c8, ebml(0x4487, "orphan".encodeToByteArray())))
        assertEquals(MetadataCoverage.MALFORMED, scan(webm(missingName)).coverage)
    }

    @Test fun webmSeekCyclesTerminateAndUnknownClustersDoNotClaimFullCoverage(): Unit = runBlocking {
        val cycle = seek(0x114d9b74, 0)
        val result = scan(webm(cycle + tags(simple("parameters", raw))))
        assertEquals(MetadataCoverage.WEBM_METADATA, result.coverage)
        assertEquals(positive, result.generation().candidates.single().positive)
        val unknown = scan(webm(uint(0x1f43b675) + byteArrayOf(0xff.toByte()) + tags(simple("parameters", raw))))
        assertTrue(unknown.fields.isEmpty())
        assertContains(unknown.limitations, MetadataLimitation.UNKNOWN_WEBM_CLUSTER)
    }

    @Test fun partialFailureRetainsEarlierFieldsAndRawScanDoesNotInterpretThem(): Unit = runBlocking {
        val result = scan(mp4(mdta("parameters" to data(raw))) + byteArrayOf(0))
        assertEquals(MetadataCoverage.MALFORMED, result.coverage)
        assertEquals(raw, result.fields.single().value)
        assertEquals(positive, result.generation().candidates.single().positive)
        assertEquals(MetadataCoverage.MALFORMED, result.generation().coverage)
        val plain = scan(webm(tags(simple("DESCRIPTION", "ordinary family movie"))))
        assertEquals("ordinary family movie", plain.fields.single().value)
        assertTrue(plain.generation().candidates.isEmpty())
    }

    @Test fun cancellationAndIoFailurePropagateInsteadOfBecomingNoMetadata(): Unit = runBlocking {
        assertFailsWith<CancellationException> { VideoMetadataReader().read(100) { _, _ -> throw CancellationException("off") } }
        assertFailsWith<IllegalStateException> { VideoMetadataReader().read(100) { _, _ -> error("file closed") } }
        assertEquals(MetadataCoverage.MALFORMED, VideoMetadataReader().read(100) { _, _ -> byteArrayOf() }.coverage)
        assertEquals(MetadataCoverage.UNSUPPORTED, scan("ordinary file".encodeToByteArray()).coverage)
    }

    private suspend fun scan(bytes: ByteArray): VideoMetadataScan = VideoMetadataReader().read(bytes.size.toLong()) { offset, count ->
        bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
    }
    private fun uint(value: Long, count: Int = 4) = ByteArray(count) { (value ushr ((count - it - 1) * 8)).toByte() }
    private fun ascii(value: String) = value.map { it.code.toByte() }.toByteArray()
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun atom(type: String, data: ByteArray) = uint(data.size + 8L) + ascii(type) + data
    private fun mp4(metadata: ByteArray) = atom("ftyp", ascii("isom") + uint(0)) + atom("moov", atom("udta", metadata))
    private fun data(value: String, utf16: Boolean = false): ByteArray = atom("data", uint(if (utf16) 2 else 1) + uint(0) +
        if (utf16) value.flatMap { listOf((it.code ushr 8).toByte(), it.code.toByte()) }.toByteArray() else value.encodeToByteArray())
    private fun mdta(vararg fields: Pair<String, ByteArray>, headerless: Boolean = false): ByteArray {
        val keys = atom("keys", uint(0) + uint(fields.size.toLong()) + fields.map { atom("mdta", it.first.encodeToByteArray()) }.fold(byteArrayOf(), ByteArray::plus))
        val values = fields.mapIndexed { index, field -> atom(metadataLatin1(uint(index + 1L)), field.second) }.fold(byteArrayOf(), ByteArray::plus)
        return atom("meta", (if (headerless) byteArrayOf() else uint(0)) + atom("ilst", values) + keys)
    }
    private fun ebml(id: Long, payload: ByteArray): ByteArray {
        val idSize = (1..4).first { id ushr (it * 8) == 0L }
        val sizeLength = (1..8).first { payload.size < (1L shl (7 * it)) - 1 }
        return uint(id, idSize) + uint(payload.size.toLong() or (1L shl (sizeLength * 7)), sizeLength) + payload
    }
    private fun simple(name: String, value: String, nested: ByteArray = byteArrayOf()) = ebml(0x67c8,
        ebml(0x4487, value.encodeToByteArray()) + nested + ebml(0x45a3, name.encodeToByteArray()))
    private fun tags(simple: ByteArray) = ebml(0x1254c367, ebml(0x7373, simple))
    private fun seek(id: Long, position: Long) = ebml(0x114d9b74, ebml(0x4dbb, ebml(0x53ab, uint(id)) + ebml(0x53ac, uint(position, 8))))
    private fun webm(body: ByteArray, unknown: Boolean = false): ByteArray =
        ebml(0x1a45dfa3, ebml(0x4282, "webm".encodeToByteArray())) +
            if (unknown) uint(0x18538067) + byteArrayOf(0xff.toByte()) + body else ebml(0x18538067, body)
}
