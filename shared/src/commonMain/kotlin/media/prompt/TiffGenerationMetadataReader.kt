package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Classic TIFF/EXIF, with bounded IFD traversal and no JVM or image decoder dependency. */
internal class TiffGenerationMetadataReader {
    suspend fun read(data: ByteArray, source: String, collector: GenerationFieldCollector, budget: MetadataReadBudget) {
        if (data.size < 8) throw InvalidMetadata()
        val little = when (data.decodeToString(0, 2)) { "II" -> true; "MM" -> false; else -> throw InvalidMetadata() }
        fun u16(p: Int) = metadataUint(data, p, 2, little).toInt()
        fun u32(p: Int) = metadataUint(data, p, 4, little)
        if (u16(2) != 42) throw InvalidMetadata()
        val pending = ArrayDeque<Long>().apply { add(u32(4)) }
        val visited = mutableSetOf<Long>()
        var entries = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val offset = pending.removeFirst()
            if (offset == 0L) continue
            if (!visited.add(offset)) throw InvalidMetadata()
            if (visited.size > 32) throw MetadataBudgetExceeded()
            if (offset < 8 || offset > data.size - 2L) throw InvalidMetadata()
            val start = offset.toInt()
            val count = u16(start)
            entries += count
            if (entries > 4096) throw MetadataBudgetExceeded()
            val tableEnd = start + 2L + count * 12L
            if (tableEnd + 4 > data.size) throw InvalidMetadata()
            repeat(count) { index ->
                currentCoroutineContext().ensureActive()
                val p = start + 2 + index * 12
                val tag = u16(p)
                val type = u16(p + 2)
                val n = u32(p + 4)
                if (tag == 0x8769 || tag == 0xa005) {
                    if (type != 4 || n != 1L) throw InvalidMetadata()
                    pending.add(u32(p + 8))
                    return@repeat
                }
                val key = when (tag) {
                    0x010e -> "ImageDescription"
                    0x010f -> "Make"
                    0x0110 -> "Model"
                    0x0131 -> "Software"
                    0x9286 -> "UserComment"
                    0x9c9c -> "XPComment"
                    0x02bc -> "XMP"
                    else -> return@repeat
                }
                if (type !in setOf(1, 2, 7)) throw InvalidMetadata()
                if (n > MAX_METADATA_BYTES) throw MetadataBudgetExceeded()
                val position = if (n <= 4) p + 8L else u32(p + 8)
                if (position > data.size - n) throw InvalidMetadata()
                val bytes = data.copyOfRange(position.toInt(), (position + n).toInt())
                if (key == "XMP") {
                    collector.readXmp(bytes, "$source XMP", budget)
                    return@repeat
                }
                try {
                    val value = when (key) {
                        "XPComment" -> metadataUtf16(bytes, little = true)
                        "UserComment" -> userComment(bytes, little, collector)
                        else -> bytes.decodeToString(throwOnInvalidSequence = true).trimEnd('\u0000')
                    } ?: return@repeat
                    budget.consume(value.length * 2L)
                    collector.text(key, value, "$source $key")
                } catch (_: CharacterCodingException) { collector.malformed = true }
                catch (_: InvalidMetadata) { collector.malformed = true }
            }
            // IFD1 may contain the unedited thumbnail. Export copies only text from
            // IFD0/Exif/Interop and never follows the thumbnail directory.
            if (!collector.preserveOnly) pending.add(u32(tableEnd.toInt()))
        }
    }

    private fun userComment(bytes: ByteArray, little: Boolean, collector: GenerationFieldCollector): String? {
        if (bytes.size < 8) throw InvalidMetadata()
        val header = bytes.copyOfRange(0, 8)
        val body = bytes.copyOfRange(8, bytes.size)
        return when {
            header.contentEquals("UNICODE\u0000".encodeToByteArray()) -> {
                // piexif writes UTF-16BE regardless of TIFF byte order; BOM wins,
                // otherwise ASCII code units disambiguate before TIFF order is used.
                val evenZeros = body.indices.count { it % 2 == 0 && body[it] == 0.toByte() }
                val oddZeros = body.indices.count { it % 2 == 1 && body[it] == 0.toByte() }
                metadataUtf16(body, if (evenZeros == oddZeros) little else oddZeros > evenZeros)
            }
            header.contentEquals("ASCII\u0000\u0000\u0000".encodeToByteArray()) || header.all { it == 0.toByte() } ->
                body.decodeToString(throwOnInvalidSequence = true).trimEnd('\u0000')
            else -> { collector.limitations += MetadataLimitation.EXIF_ENCODING; null }
        }
    }
}
