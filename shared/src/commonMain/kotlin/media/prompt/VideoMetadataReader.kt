package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Raw container fields remain independent of prompt interpretation and its feature permission. */
internal data class VideoMetadataField(val key: String, val value: String, val source: String)

internal data class VideoMetadataScan(
    val fields: List<VideoMetadataField>,
    val coverage: MetadataCoverage,
    val limitations: Set<MetadataLimitation>,
    val sourceC2pa: List<ByteArray> = emptyList()
) {
    suspend fun generation(): GenerationMetadata {
        val preserved = fields.filter { isPreservedVideoMetadataKey(it.key) }
        if (preserved.isNotEmpty()) {
            val record = preserved.singleOrNull()?.let { PreservedVideoMetadata.decode(it.value) }
            if (record != null) {
                val original = copy(
                    fields = record.fields.map { VideoMetadataField(it.key, it.value, "元動画 / ${record.edits.size}回編集済み") },
                    limitations = limitations + if (record.sourceC2pa.isEmpty()) emptySet() else setOf(MetadataLimitation.C2PA)
                )
                return original.generation()
            }
            // A broken preservation record must not be silently replaced by an ordinary tag.
            return GenerationMetadata(coverage = MetadataCoverage.MALFORMED, limitations = limitations)
        }
        val collector = GenerationFieldCollector()
        collector.limitations += limitations
        val budget = MetadataReadBudget()
        try {
            for (field in fields) {
                currentCoroutineContext().ensureActive()
                if (field.key == "com.adobe.xmp") {
                    val bytes = field.value.encodeToByteArray()
                    budget.consume(bytes.size.toLong())
                    collector.readXmp(bytes, field.source, budget)
                } else {
                    // Preserve original names in the raw scan; normalize only for interpretation.
                    val key = when (field.key.lowercase()) {
                        "prompt", "com.apple.quicktime.prompt" -> "prompt"
                        "parameters" -> "parameters"
                        "software", "encoder", "©too" -> "Software"
                        "comment", "©cmt", "com.apple.quicktime.comment" -> "Comment"
                        "description", "©des", "com.apple.quicktime.description" -> "Description"
                        else -> field.key
                    }
                    collector.text(key, field.value, field.source)
                }
            }
            return collector.result(coverage)
        } catch (_: MetadataBudgetExceeded) {
            return collector.result(MetadataCoverage.BUDGET_EXCEEDED)
        }
    }
}

/** Bounded random access: mdat, clusters and encoded audio/video are never copied or scanned. */
internal class VideoMetadataReader(private val maxMetadataBytes: Int = MAX_METADATA_BYTES) {
    init { require(maxMetadataBytes in MAX_METADATA_BYTES..16 * 1024 * 1024) }
    suspend fun read(size: Long, readAt: suspend (Long, Int) -> ByteArray): VideoMetadataScan = Scan(size, readAt, maxMetadataBytes).run()

    private class Scan(val size: Long, val readAt: suspend (Long, Int) -> ByteArray, maxMetadataBytes: Int) {
        val fields = mutableListOf<VideoMetadataField>()
        val sourceC2pa = mutableListOf<ByteArray>()
        val limitations = mutableSetOf<MetadataLimitation>()
        val budget = MetadataReadBudget(maxMetadataBytes)
        var elements = 0
        val visitedTags = mutableSetOf<Long>()
        val visitedSeekHeads = mutableSetOf<Long>()

        suspend fun bytes(offset: Long, count: Int): ByteArray {
            currentCoroutineContext().ensureActive()
            if (offset < 0 || count < 0 || offset > size - count) throw InvalidMetadata()
            budget.consume(count.toLong())
            return readAt(offset, count).also { if (it.size != count) throw InvalidMetadata() }
        }

        fun element(depth: Int) {
            if (++elements > 4096 || depth > 12) throw MetadataBudgetExceeded()
        }

        suspend fun text(offset: Long, length: Long, utf16: Boolean = false): String {
            if (length < 0) throw InvalidMetadata()
            if (length > budget.remaining) throw MetadataBudgetExceeded()
            val data = bytes(offset, length.toInt())
            val result = if (utf16) metadataUtf16(data, little = false) else data.decodeToString(throwOnInvalidSequence = true).trimEnd('\u0000')
            budget.consume(result.length * 2L)
            return result
        }

        fun field(key: String, value: String, source: String) {
            if (fields.size >= 256) throw MetadataBudgetExceeded()
            fields += VideoMetadataField(key, value, source)
        }

        suspend fun run(): VideoMetadataScan {
            fun result(coverage: MetadataCoverage) = VideoMetadataScan(fields.toList(), coverage, limitations.toSet(), sourceC2pa.toList())
            return try {
                val signature = bytes(0, minOf(size.coerceAtLeast(0), 12).toInt())
                when {
                    signature.size >= 4 && metadataUint(signature, 0, 4) == 0x1a45dfa3L -> {
                        val header = ebmlElement(0, size, 0)
                        if (header.unknown) throw InvalidMetadata()
                        var docType: String? = null
                        walkEbml(header.data, header.end, 1) { child ->
                            if (child.id == 0x4282L) docType = text(child.data, child.end - child.data)
                        }
                        if (docType !in setOf("webm", "matroska")) return result(MetadataCoverage.UNSUPPORTED)
                        walkEbml(header.end, size, 0) { segment ->
                            if (segment.id == SEGMENT) readSegment(segment)
                        }
                        result(MetadataCoverage.WEBM_METADATA)
                    }
                    signature.size >= 8 && metadataLatin1(signature.copyOfRange(4, 8)) in setOf("ftyp", "moov", "mdat", "free", "wide", "skip") -> {
                        mp4(0, size, 0)
                        result(MetadataCoverage.MP4_METADATA)
                    }
                    else -> result(MetadataCoverage.UNSUPPORTED)
                }
            } catch (_: InvalidMetadata) { result(MetadataCoverage.MALFORMED) }
            catch (_: CharacterCodingException) { result(MetadataCoverage.MALFORMED) }
            catch (_: MetadataBudgetExceeded) { result(MetadataCoverage.BUDGET_EXCEEDED) }
        }

        data class Atom(val type: String, val data: Long, val end: Long)
        suspend fun atom(offset: Long, end: Long, depth: Int): Atom {
            element(depth)
            if (offset > end - 8) throw InvalidMetadata()
            val header = bytes(offset, 8)
            val shortLength = metadataUint(header, 0, 4)
            val headerSize = if (shortLength == 1L) 16 else 8
            val length = when (shortLength) {
                0L -> end - offset
                1L -> {
                    if (offset > end - 16) throw InvalidMetadata()
                    val extended = bytes(offset + 8, 8)
                    if (extended[0] < 0) throw InvalidMetadata()
                    metadataUint(extended, 0, 8)
                }
                else -> shortLength
            }
            if (length < headerSize || length > end - offset) throw InvalidMetadata()
            return Atom(metadataLatin1(header.copyOfRange(4, 8)), offset + headerSize, offset + length)
        }

        suspend fun atoms(start: Long, end: Long, depth: Int, visit: suspend (Atom) -> Unit) {
            var p = start
            while (p < end) { val child = atom(p, end, depth); visit(child); p = child.end }
        }

        suspend fun mp4(start: Long, end: Long, depth: Int) {
            atoms(start, end, depth) { box -> when (box.type) {
                "moov", "udta", "trak", "mdia" -> mp4(box.data, box.end, depth + 1)
                "meta" -> meta(box, depth + 1)
                "©cmt", "©des" -> {
                    var p = box.data
                    while (p < box.end) {
                        if (p > box.end - 4) throw InvalidMetadata()
                        val length = metadataUint(bytes(p, 4), 0, 2)
                        if (length > box.end - p - 4) throw InvalidMetadata()
                        field(box.type, text(p + 4, length), "QuickTime ${box.type}")
                        p += length + 4
                    }
                }
                "uuid" -> {
                    if (box.end - box.data < 16) throw InvalidMetadata()
                    val id = bytes(box.data, 16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                    when (id) {
                        "be7acfcb97a942e89c71999491e3afac" -> field("com.adobe.xmp", text(box.data + 16, box.end - box.data - 16), "MP4 XMP")
                        "d8fec3d61b0e483c92975828877ec481" -> {
                            limitations += MetadataLimitation.C2PA
                            val length = box.end - box.data - 16
                            if (length > budget.remaining) throw MetadataBudgetExceeded()
                            // Kept only as unsigned SOURCE bytes after editing, never an active UUID box.
                            sourceC2pa += bytes(box.data + 16, length.toInt())
                        }
                    }
                }
            } }
        }

        suspend fun meta(meta: Atom, depth: Int) {
            if (meta.end - meta.data < 4) throw InvalidMetadata()
            // ISO meta has version/flags; QuickTime also permits a headerless meta container.
            val first = metadataUint(bytes(meta.data, 4), 0, 4)
            val start = if (first == 0L) meta.data + 4 else meta.data
            val children = mutableListOf<Atom>()
            atoms(start, meta.end, depth) { children += it }
            val keys = mutableListOf<String>()
            children.firstOrNull { it.type == "keys" }?.let { box ->
                if (box.end - box.data < 8) throw InvalidMetadata()
                val header = bytes(box.data, 8)
                if (metadataUint(header, 0, 4) != 0L) throw InvalidMetadata()
                val count = metadataUint(header, 4, 4)
                if (count > 256) throw MetadataBudgetExceeded()
                var p = box.data + 8
                repeat(count.toInt()) {
                    val key = atom(p, box.end, depth + 1)
                    keys += if (key.type == "mdta") text(key.data, key.end - key.data) else ""
                    p = key.end
                }
                if (p != box.end) throw InvalidMetadata()
            }
            children.filter { it.type == "ilst" }.forEach { list ->
                atoms(list.data, list.end, depth + 1) { item ->
                    val index = item.type.fold(0L) { n, c -> (n shl 8) or c.code.toLong() }
                    var name = keys.getOrNull((index - 1).toInt()).takeIf { index in 1..keys.size.toLong() } ?: item.type
                    val values = mutableListOf<String>()
                    atoms(item.data, item.end, depth + 2) { entry -> when (entry.type) {
                        "name" -> {
                            if (entry.end - entry.data < 4) throw InvalidMetadata()
                            name = text(entry.data + 4, entry.end - entry.data - 4)
                        }
                        "data" -> {
                            if (entry.end - entry.data < 8) throw InvalidMetadata()
                            val kind = metadataUint(bytes(entry.data, 8), 0, 4) and 0xffffff
                            if (kind in 0..2) values += text(entry.data + 8, entry.end - entry.data - 8, kind == 2L)
                        }
                    } }
                    values.forEach { field(name, it, "MP4 $name") }
                }
            }
        }

        data class Ebml(val id: Long, val start: Long, val data: Long, val end: Long, val unknown: Boolean)
        suspend fun vint(offset: Long, end: Long, id: Boolean): Pair<Long, Int> {
            if (offset >= end) throw InvalidMetadata()
            val first = bytes(offset, 1)[0].toInt() and 255
            if (first == 0) throw InvalidMetadata()
            val length = first.countLeadingZeroBits() - 24 + 1
            if (length !in 1..(if (id) 4 else 8) || length > end - offset) throw InvalidMetadata()
            val rest = bytes(offset + 1, length - 1)
            var value = (if (id) first else first and (255 ushr length)).toLong()
            for (byte in rest) value = (value shl 8) or (byte.toLong() and 255)
            if (!id && value == (1L shl (7 * length)) - 1) value = -1
            return value to length
        }
        suspend fun ebmlElement(start: Long, end: Long, depth: Int): Ebml {
            element(depth)
            val (id, a) = vint(start, end, true)
            val (length, b) = vint(start + a, end, false)
            val data = start + a + b
            if (length < 0 && id !in setOf(SEGMENT, CLUSTER)) throw InvalidMetadata()
            if (length > end - data) throw InvalidMetadata()
            return Ebml(id, start, data, if (length < 0) end else data + length, length < 0)
        }
        suspend fun walkEbml(start: Long, end: Long, depth: Int, visit: suspend (Ebml) -> Unit) {
            var p = start
            while (p < end) { val child = ebmlElement(p, end, depth); visit(child); p = child.end }
        }
        suspend fun readSegment(segment: Ebml) {
            walkEbml(segment.data, segment.end, 1) { child -> when (child.id) {
                TAGS -> tags(child, 2)
                SEEK_HEAD -> seekHead(child, segment, 2)
                CLUSTER -> if (child.unknown) limitations += MetadataLimitation.UNKNOWN_WEBM_CLUSTER
            } }
        }
        suspend fun tags(tags: Ebml, depth: Int) {
            if (!visitedTags.add(tags.start)) return
            walkEbml(tags.data, tags.end, depth) { tag ->
                if (tag.id == 0x7373L) walkEbml(tag.data, tag.end, depth + 1) { simple ->
                    if (simple.id == 0x67c8L) simpleTag(simple, depth + 2)
                }
            }
        }
        suspend fun simpleTag(tag: Ebml, depth: Int) {
            var name: String? = null
            val values = mutableListOf<String>()
            walkEbml(tag.data, tag.end, depth) { child -> when (child.id) {
                0x45a3L -> name = text(child.data, child.end - child.data)
                0x4487L -> values += text(child.data, child.end - child.data)
                0x67c8L -> simpleTag(child, depth + 1)
            } }
            if (values.isNotEmpty() && name == null) throw InvalidMetadata()
            name?.let { key -> values.forEach { field(key, it, "WebM $key") } }
        }
        suspend fun seekHead(head: Ebml, segment: Ebml, depth: Int) {
            if (!visitedSeekHeads.add(head.start)) return
            walkEbml(head.data, head.end, depth) { seek ->
                if (seek.id == 0x4dbbL) {
                    var target: Long? = null; var position: Long? = null
                    walkEbml(seek.data, seek.end, depth + 1) { child ->
                        if (child.id in setOf(0x53abL, 0x53acL)) {
                            val length = child.end - child.data
                            if (length !in 1..8) throw InvalidMetadata()
                            val data = bytes(child.data, length.toInt())
                            if (length == 8L && data[0] < 0) throw InvalidMetadata()
                            val value = metadataUint(data, 0, data.size)
                            if (child.id == 0x53abL) target = value else position = value
                        }
                    }
                    if (target in setOf(TAGS, SEEK_HEAD) && position != null) {
                        val offset = position!!
                        if (offset < 0 || offset >= segment.end - segment.data) throw InvalidMetadata()
                        val pointed = ebmlElement(segment.data + offset, segment.end, depth + 1)
                        if (pointed.id != target) throw InvalidMetadata()
                        if (pointed.id == TAGS) tags(pointed, depth + 1) else seekHead(pointed, segment, depth + 1)
                    }
                }
            }
        }
    }

    private companion object {
        const val SEGMENT = 0x18538067L
        const val CLUSTER = 0x1f43b675L
        const val TAGS = 0x1254c367L
        const val SEEK_HEAD = 0x114d9b74L
    }
}
