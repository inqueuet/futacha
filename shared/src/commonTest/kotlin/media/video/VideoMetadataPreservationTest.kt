package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.prompt.*
import com.valoser.futacha.testing.video.VideoEditFixtures
import kotlinx.coroutines.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class VideoMetadataPreservationTest {
    private val raw = "猫 🐈  \r\nNegative prompt: bad\r\nSteps: 2, Sampler: Euler, Seed: 9007199254740993"
    private val record = PreservedVideoMetadata(
        fields = listOf(PreservedVideoMetadata.Field("parameters", raw)),
        edits = listOf(PreservedVideoMetadata.Edit(atUtc = "2026-09-17T00:00:00Z", regionCount = 2))
    )
    private suspend fun scan(bytes: ByteArray) = VideoMetadataReader(VIDEO_PRESERVATION_SCAN_BUDGET).read(bytes.size.toLong()) { offset, size ->
        bytes.copyOfRange(offset.toInt(), offset.toInt() + size)
    }
    private class File(var bytes: ByteArray) {
        var reads = 0
        var writes = 0
        var flushes = 0
        suspend fun append(record: PreservedVideoMetadata) {
            appendMp4GenerationMetadata(bytes.size.toLong(), record, readAt = { offset, count ->
                reads += count
                bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            }, writeAt = { offset, value ->
                writes++
                if (offset + value.size > bytes.size) bytes = bytes.copyOf((offset + value.size).toInt())
                value.copyInto(bytes, offset.toInt())
            }, flush = { flushes++ })
        }
    }

    @Test fun replacementMoovKeepsEveryMediaByteAndTimingSampleTableUnchanged(): Unit = runBlocking {
        val input = VideoEditFixtures.bytes("landscape")
        val original = input.copyOf()
        val file = File(input.copyOf())
        val source = PreservedVideoMetadata.fromScan(scan(input)).copy(edits = record.edits)
        file.append(source)
        assertContentEquals(original, input, "Selected source bytes are read-only")
        // Only the old moov type is retired inside the original byte range; all mdat and
        // absolute chunk offsets stay in place, including files with tail metadata.
        val before = topBoxes(original)
        val after = topBoxes(file.bytes)
        assertEquals(1, after.count { it.type == "moov" })
        for (box in before.filter { it.type != "moov" }) {
            assertContentEquals(original.copyOfRange(box.start, box.end), file.bytes.copyOfRange(box.start, box.end))
        }
        val oldMoov = before.single { it.type == "moov" }
        val newMoov = after.single { it.type == "moov" }
        val oldChildren = topBoxes(original.copyOfRange(oldMoov.start + 8, oldMoov.end))
        val newBody = file.bytes.copyOfRange(newMoov.start + 8, newMoov.end)
        val newChildren = topBoxes(newBody)
        for (box in oldChildren.filter { it.type !in setOf("meta", "udta") }) {
            val previous = original.copyOfRange(oldMoov.start + 8 + box.start, oldMoov.start + 8 + box.end)
            assertTrue(newChildren.any { it.type == box.type && newBody.copyOfRange(it.start, it.end).contentEquals(previous) })
        }
        val scan = scan(file.bytes)
        assertEquals(source, PreservedVideoMetadata.fromScan(scan))
        val candidate = scan.generation().candidates.single()
        assertEquals("青い鳥\nDialogue: <Picture 1> says \"hello\" & goodbye.", candidate.positive)
        assertContains(candidate.source, "元動画 / 1回編集済み")
        assertContains(candidate.settings!!, "9007199254740993")
        assertEquals(2, file.flushes)
    }

    @Test fun reeditingKeepsOneOriginalRecordAndAppendsOnlyHistory(): Unit = runBlocking {
        val file = File(minimal())
        file.append(record)
        val second = PreservedVideoMetadata.fromScan(scan(file.bytes)).copy(edits = record.edits + record.edits.single())
        file.append(second)
        val restored = PreservedVideoMetadata.fromScan(scan(file.bytes))
        assertEquals(record.fields, restored.fields)
        assertEquals(2, restored.edits.size)
        assertEquals(1, scan(file.bytes).fields.count { isPreservedVideoMetadataKey(it.key) })
        assertFalse(restored.fields.any { isPreservedVideoMetadataKey(it.key) })
        assertEquals(raw, scan(file.bytes).generation().candidates.single().raw)
    }

    @Test fun sourceSignaturesRemainUnsignedRecordsAndExporterClaimsAreRetired(): Unit = runBlocking {
        val manifest = byteArrayOf(0, 1, 2, 3, 127, -1)
        val uuid = box("uuid", hex("d8fec3d61b0e483c92975828877ec481") + manifest)
        val xmp = box("uuid", hex("be7acfcb97a942e89c71999491e3afac") + "source claim".encodeToByteArray())
        val source = PreservedVideoMetadata.fromScan(scan(minimal() + uuid))
        assertContentEquals(manifest, Base64.decode(source.sourceC2pa.single()))
        val preserved = record.copy(sourceC2pa = source.sourceC2pa)
        val file = File(box("ftyp", "isom0000".encodeToByteArray()) + uuid + xmp +
            box("moov", uuid + box("trak", box("udta", byteArrayOf()) + xmp) + box("free", byteArrayOf())))
        file.append(preserved)
        val result = scan(file.bytes)
        assertEquals(preserved, PreservedVideoMetadata.fromScan(result))
        assertTrue(result.sourceC2pa.isEmpty(), "No active signature may claim authenticity of edited pixels")
        assertFalse(result.fields.any { it.key == "com.adobe.xmp" })
        assertContains(result.generation().limitations, MetadataLimitation.C2PA)
    }

    @Test fun opaqueLegacyGenerationIsPreservedWithoutSemanticParsing(): Unit = runBlocking {
        val previous = record.copy(generation = "{\"source\":\"Toshikari\",\"seed\":9007199254740993}")
        val scan = VideoMetadataScan(listOf(VideoMetadataField(LEGACY_PRESERVED_VIDEO_METADATA_KEY, previous.encode(), "legacy")),
            MetadataCoverage.MP4_METADATA, emptySet())
        assertEquals(previous, PreservedVideoMetadata.fromScan(scan))
        val file = File(minimal())
        file.append(previous)
        assertEquals(previous, PreservedVideoMetadata.fromScan(scan(file.bytes)))
    }

    @Test fun ordinaryCommentsArePreservedWithoutCreatingAiEvidence(): Unit = runBlocking {
        val ordinary = record.copy(fields = listOf(PreservedVideoMetadata.Field("Comment", "family movie\n猫 & 犬")))
        val file = File(minimal())
        file.append(ordinary)
        assertEquals(ordinary, PreservedVideoMetadata.fromScan(scan(file.bytes)))
        assertTrue(scan(file.bytes).generation().candidates.isEmpty())
    }

    @Test fun terminalTopLevelAndMoovChildLengthsAreMadeExplicitBeforeAppending(): Unit = runBlocking {
        val terminalChild = uint(0) + "free".encodeToByteArray() + byteArrayOf(7)
        val variants = listOf(
            minimal() + uint(0) + "mdat".encodeToByteArray() + ByteArray(100) { 42 },
            box("ftyp", "isom0000".encodeToByteArray()) + uint(0) + "moov".encodeToByteArray() + terminalChild
        )
        for (bytes in variants) {
            val file = File(bytes)
            file.append(record)
            assertEquals(record, PreservedVideoMetadata.fromScan(scan(file.bytes)))
        }
    }

    @Test fun incompleteOrAmbiguousSourceCannotSilentlyLoseInformation(): Unit = runBlocking {
        for (coverage in MetadataCoverage.entries.filter { it !in setOf(MetadataCoverage.MP4_METADATA, MetadataCoverage.WEBM_METADATA) }) {
            assertFailsWith<IllegalArgumentException> { PreservedVideoMetadata.fromScan(VideoMetadataScan(emptyList(), coverage, emptySet())) }
        }
        assertFailsWith<IllegalArgumentException> { PreservedVideoMetadata.fromScan(VideoMetadataScan(emptyList(), MetadataCoverage.WEBM_METADATA,
            setOf(MetadataLimitation.UNKNOWN_WEBM_CLUSTER))) }
        val field = VideoMetadataField(PRESERVED_VIDEO_METADATA_KEY, record.encode(), "record")
        assertFailsWith<IllegalArgumentException> { PreservedVideoMetadata.fromScan(VideoMetadataScan(listOf(field, field), MetadataCoverage.MP4_METADATA, emptySet())) }
        assertFailsWith<IllegalArgumentException> { PreservedVideoMetadata.fromScan(VideoMetadataScan(listOf(field.copy(value = "{}")), MetadataCoverage.MP4_METADATA, emptySet())) }
        assertEquals(MetadataCoverage.MALFORMED, VideoMetadataScan(listOf(field.copy(value = "{}")), MetadataCoverage.MP4_METADATA, emptySet()).generation().coverage)
    }

    @Test fun invalidRecordsAndMp4sFailBeforeAnyWrite(): Unit = runBlocking {
        assertNull(PreservedVideoMetadata.decode("{}"))
        assertNull(PreservedVideoMetadata.decode(record.encode().replace("\"version\":1", "\"version\":2")))
        val invalidRecords = listOf(
            record.copy(fields = listOf(PreservedVideoMetadata.Field(PRESERVED_VIDEO_METADATA_KEY, record.encode()))),
            record.copy(sourceC2pa = listOf("%not-base64%")),
            record.copy(edits = List(129) { record.edits.single() }),
            record.copy(fields = listOf(PreservedVideoMetadata.Field("prompt", "x".repeat(MAX_PRESERVED_VIDEO_METADATA_BYTES))))
        )
        for (invalid in invalidRecords) {
            val file = File(minimal())
            assertFailsWith<IllegalArgumentException> { file.append(invalid) }
            assertEquals(0, file.writes)
        }
        for (bytes in listOf(minimal() + box("moov", byteArrayOf()), minimal().copyOf(20), box("ftyp", byteArrayOf()))) {
            val file = File(bytes)
            assertFailsWith<IllegalArgumentException> { file.append(record) }
            assertEquals(0, file.writes)
        }
    }

    @Test fun cancellationAndDiskFullNeverReportSuccessfulMetadataWrite(): Unit = runBlocking {
        val bytes = minimal()
        for (cancel in listOf(false, true)) {
            var flushes = 0
            val failure = try {
                appendMp4GenerationMetadata(bytes.size.toLong(), record,
                    readAt = { offset, size -> bytes.copyOfRange(offset.toInt(), offset.toInt() + size) },
                    writeAt = { _, _ -> if (cancel) throw CancellationException("off") else throw IllegalStateException("disk full") },
                    flush = { flushes++ })
                null
            } catch (failure: Exception) { failure }
            assertNotNull(failure)
            assertEquals(cancel, failure is CancellationException)
            assertEquals(0, flushes)
        }
    }

    private data class Box(val start: Int, val end: Int, val type: String)
    private fun topBoxes(bytes: ByteArray): List<Box> = buildList {
        var p = 0
        while (p < bytes.size) {
            val size = metadataUint(bytes, p, 4).toInt()
            require(size >= 8 && size <= bytes.size - p)
            add(Box(p, p + size, metadataLatin1(bytes.copyOfRange(p + 4, p + 8))))
            p += size
        }
    }
    private fun uint(value: Long) = ByteArray(4) { (value ushr ((3 - it) * 8)).toByte() }
    private fun box(type: String, payload: ByteArray) = uint(payload.size + 8L) + type.encodeToByteArray() + payload
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun minimal() = box("ftyp", "isom0000".encodeToByteArray()) + box("moov", box("free", byteArrayOf()))
}
