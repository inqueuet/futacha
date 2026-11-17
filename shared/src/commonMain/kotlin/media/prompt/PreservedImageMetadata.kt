package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.Buffer

internal const val IMAGE_SOURCE_DECLARATION_KEY = "XMP DigitalSourceType"
internal const val MAX_IMAGE_METADATA_BYTES = 2 * 1024 * 1024
internal val IMAGE_METADATA_SIGNATURE = "FutachaImageTags\u0000".encodeToByteArray()
private const val SEGMENT_PAYLOAD = 60_000
private const val MAX_SEGMENTS = (MAX_IMAGE_METADATA_BYTES + SEGMENT_PAYLOAD - 1) / SEGMENT_PAYLOAD

/** Text-only, unsigned source tags. No original pixels, thumbnails, offsets or AI interpretation. */
@Serializable
internal data class PreservedImageMetadata(
    @Required val version: Int = 1,
    @Required val fields: List<Field> = emptyList(),
    val partial: Boolean = false
) {
    @Serializable data class Field(val key: String, val value: String, val source: String)

    fun encode(): ByteArray {
        validate()
        return codec.encodeToString(serializer(), this).encodeToByteArray().also {
            require(it.size <= MAX_IMAGE_METADATA_BYTES)
        }
    }

    private fun validate() {
        require(version == 1 && fields.size <= 256)
        require(fields.all { it.key.length in 1..128 && it.source.length in 1..512 })
    }

    companion object {
        private val codec = Json { encodeDefaults = true }

        fun decode(bytes: ByteArray): PreservedImageMetadata {
            if (bytes.size > MAX_IMAGE_METADATA_BYTES) throw MetadataBudgetExceeded()
            val text = bytes.decodeToString(throwOnInvalidSequence = true)
            var depth = 0; var quoted = false; var escaped = false
            text.forEach { char ->
                if (quoted) {
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { if (++depth > 8) throw InvalidMetadata() }
                    '}', ']' -> { if (--depth < 0) throw InvalidMetadata() }
                }
            }
            if (quoted || depth != 0) throw InvalidMetadata()
            return try { codec.decodeFromString(serializer(), text).also { it.validate() } }
            catch (_: IllegalArgumentException) { throw InvalidMetadata() }
        }

        /** Reuses bounded container/TIFF/XML tokenization, without running the generation classifier. */
        suspend fun scan(bytes: ByteArray): PreservedImageMetadata {
            val collector = GenerationFieldCollector(preserveOnly = true)
            val result = ImageGenerationMetadataReader().readFields(bytes.size.toLong(), { offset, count ->
                bytes.copyOfRange(offset.toInt(), offset.toInt() + count)
            }, collector, MetadataReadBudget(8 * 1024 * 1024))
            val fields = collector.fields.map { Field(it.key, it.value, it.source) }
            val partial = result.coverage !in setOf(MetadataCoverage.PNG_METADATA, MetadataCoverage.JPEG_METADATA,
                MetadataCoverage.WEBP_METADATA) || result.limitations.isNotEmpty()
            // JPEG preservation is best effort. Never truncate a field or export a broken record.
            val retained = mutableListOf<Field>()
            var omitted = partial
            var estimate = 64L
            for (field in fields) {
                currentCoroutineContext().ensureActive()
                val cost = codec.encodeToString(Field.serializer(), field).encodeToByteArray().size + 1L
                if (estimate + cost <= MAX_IMAGE_METADATA_BYTES) { retained += field; estimate += cost }
                else omitted = true
            }
            return PreservedImageMetadata(fields = retained, partial = omitted)
        }
    }
}

/** APP15 segments use an application signature, a versioned JSON payload, and a whole-record CRC. */
internal class ImageMetadataSegments {
    private val bytes = Buffer()
    private var expectedParts = 0
    private var expectedSize = 0
    private var expectedCrc = 0L
    private var parts = 0

    fun add(segment: ByteArray) {
        val p = IMAGE_METADATA_SIGNATURE.size
        if (segment.size < p + 12) throw InvalidMetadata()
        val index = metadataUint(segment, p, 2).toInt()
        val count = metadataUint(segment, p + 2, 2).toInt()
        val size = metadataUint(segment, p + 4, 4)
        val crc = metadataUint(segment, p + 8, 4)
        if (count !in 1..MAX_SEGMENTS || size !in 1..MAX_IMAGE_METADATA_BYTES.toLong()) throw MetadataBudgetExceeded()
        if (parts == 0) { expectedParts = count; expectedSize = size.toInt(); expectedCrc = crc }
        if (index != parts || count != expectedParts || size != expectedSize.toLong() || crc != expectedCrc ||
            index >= count || segment.size == p + 12 || segment.size - p - 12 > SEGMENT_PAYLOAD ||
            bytes.size + segment.size - p - 12 > expectedSize) throw InvalidMetadata()
        bytes.write(segment, p + 12, segment.size - p - 12)
        parts++
    }

    fun finish(): PreservedImageMetadata? {
        if (parts == 0) return null
        if (parts != expectedParts || bytes.size != expectedSize.toLong()) throw InvalidMetadata()
        val payload = bytes.readByteArray()
        if (pngCrc(payload) != expectedCrc) throw InvalidMetadata()
        return PreservedImageMetadata.decode(payload)
    }
}

/** Inserts only freshly created metadata into an already encoded, upright JPEG. */
internal suspend fun preserveEditedImageMetadata(
    jpeg: ByteArray, metadata: PreservedImageMetadata, width: Int, height: Int
): ByteArray {
    require(jpeg.size >= 4 && metadataUint(jpeg, 0, 2) == 0xffd8L && width > 0 && height > 0)
    val output = Buffer().write(jpeg, 0, 2)
    // Keep JFIF's leading APP0 at the beginning. The encoder must not supply source EXIF.
    var offset = 2
    if (jpeg.size >= 6 && metadataUint(jpeg, offset, 2) == 0xffe0L) {
        val size = metadataUint(jpeg, offset + 2, 2).toInt()
        require(size >= 2 && offset + 2 + size <= jpeg.size)
        output.write(jpeg, offset, size + 2); offset += size + 2
    }
    fun segment(marker: Int, data: ByteArray) {
        require(data.size <= 65533)
        output.writeByte(255).writeByte(marker).writeShort(data.size + 2).write(data)
    }
    // A standard UserComment is also supplied when one unambiguous source field fits.
    // Choosing a tag does not parse A1111, ComfyUI or any AI schema.
    val comments = metadata.fields.filter { it.key in setOf("parameters", "UserComment", "XPComment") }
    val comment = comments.singleOrNull()?.value?.takeIf { it.length <= 30_000 }
    segment(0xe1, freshExif(width, height, comment))
    if (metadata.fields.isNotEmpty() || metadata.partial) {
        val payload = metadata.encode()
        val count = (payload.size + SEGMENT_PAYLOAD - 1) / SEGMENT_PAYLOAD
        val crc = pngCrc(payload)
        repeat(count) { index ->
            currentCoroutineContext().ensureActive()
            val begin = index * SEGMENT_PAYLOAD
            val length = minOf(SEGMENT_PAYLOAD, payload.size - begin)
            segment(0xef, Buffer().write(IMAGE_METADATA_SIGNATURE).writeShort(index).writeShort(count)
                .writeInt(payload.size).writeInt(crc.toInt()).write(payload, begin, length).readByteArray())
        }
    }
    currentCoroutineContext().ensureActive()
    return output.write(jpeg, offset, jpeg.size - offset).readByteArray()
}

private fun freshExif(width: Int, height: Int, comment: String?): ByteArray {
    // Big-endian classic TIFF: orientation=1, dimensions of the edited raster, no IFD1.
    val commentBytes = comment?.let { value ->
        Buffer().writeUtf8("UNICODE\u0000").writeShort(0xfeff).apply {
            value.forEach { writeShort(it.code) }
        }.readByteArray()
    }
    val exifOffset = 8 + 2 + 4 * 12 + 4
    val exifCount = if (commentBytes == null) 2 else 3
    val dataOffset = exifOffset + 2 + exifCount * 12 + 4
    val out = Buffer().writeUtf8("Exif\u0000\u0000MM").writeShort(42).writeInt(8).writeShort(4)
    fun entry(tag: Int, type: Int, count: Int, value: Int) {
        out.writeShort(tag).writeShort(type).writeInt(count).writeInt(value)
    }
    entry(0x0100, 4, 1, width); entry(0x0101, 4, 1, height)
    entry(0x0112, 3, 1, 1 shl 16); entry(0x8769, 4, 1, exifOffset)
    out.writeInt(0).writeShort(exifCount)
    if (commentBytes != null) entry(0x9286, 7, commentBytes.size, dataOffset)
    entry(0xa002, 4, 1, width); entry(0xa003, 4, 1, height)
    out.writeInt(0)
    if (commentBytes != null) out.write(commentBytes)
    return out.readByteArray()
}
