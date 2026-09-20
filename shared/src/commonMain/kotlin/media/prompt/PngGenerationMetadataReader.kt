package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.Buffer
import okio.Inflater
import okio.InflaterSource
import okio.use

/** Bounded random access over encoded bytes. No bitmap, URL, HTTP client or platform API. */
internal class PngGenerationMetadataReader {
    suspend fun read(size: Long, readAt: suspend (Long, Int) -> ByteArray): GenerationMetadata {
        return readFields(size, readAt, GenerationFieldCollector(), MetadataReadBudget())
    }

    internal suspend fun readFields(size: Long, readAt: suspend (Long, Int) -> ByteArray,
        collector: GenerationFieldCollector, budget: MetadataReadBudget): GenerationMetadata {
        fun result(coverage: MetadataCoverage) = collector.result(coverage)
        suspend fun exact(offset: Long, count: Int): ByteArray {
            currentCoroutineContext().ensureActive()
            val bytes = readAt(offset, count)
            if (bytes.size != count) throw InvalidMetadata()
            return bytes
        }
        try {
            if (size < 8 || !exact(0, 8).contentEquals(PNG_SIGNATURE)) return result(MetadataCoverage.UNSUPPORTED)
            var offset = 8L
            repeat(4096) {
                if (offset > size - 12) throw InvalidMetadata()
                val header = exact(offset, 8)
                val length = uint32(header, 0)
                if (length > size - offset - 12) throw InvalidMetadata()
                val type = header.decodeToString(4, 8)
                if (type == "IEND") {
                    if (length != 0L) throw InvalidMetadata()
                    if (pngCrc(header.copyOfRange(4, 8)) != uint32(exact(offset + 8, 4), 0)) throw InvalidMetadata()
                    return result(MetadataCoverage.PNG_METADATA)
                }
                if (type in setOf("tEXt", "zTXt", "iTXt", "eXIf")) {
                    budget.consume(length)
                    val data = exact(offset + 8, length.toInt())
                    val crc = exact(offset + 8 + length, 4)
                    if (pngCrc(header.copyOfRange(4, 8) + data) != uint32(crc, 0)) throw InvalidMetadata()
                    if (type == "eXIf") {
                        collector.readExif(data, "PNG eXIf", budget)
                        offset += 12 + length
                        return@repeat
                    }
                    val zero = data.indexOf(0)
                    if (zero !in 1..79) throw InvalidMetadata()
                    val key = metadataLatin1(data.copyOfRange(0, zero))
                    if (key in setOf("parameters", "Software", "Comment", "Description", "prompt", "workflow", "XML:com.adobe.xmp")) {
                        val text = when (type) {
                            "tEXt" -> pngText(data.copyOfRange(zero + 1, data.size), key)
                            "zTXt" -> {
                                if (zero + 2 > data.size || data[zero + 1] != 0.toByte()) throw InvalidMetadata()
                                pngText(inflateBounded(data.copyOfRange(zero + 2, data.size), budget.remaining), key)
                            }
                            else -> {
                                if (zero + 3 > data.size || data[zero + 2] != 0.toByte()) throw InvalidMetadata()
                                val flag = data[zero + 1].toInt()
                                if (flag !in 0..1) throw InvalidMetadata()
                                var start = zero + 3
                                repeat(2) {
                                    val end = (start until data.size).firstOrNull { data[it] == 0.toByte() }
                                        ?: throw InvalidMetadata()
                                    start = end + 1
                                }
                                val value = data.copyOfRange(start, data.size)
                                (if (flag == 1) inflateBounded(value, budget.remaining) else value)
                                    .decodeToString(throwOnInvalidSequence = true)
                            }
                        }
                        // UTF-16 can use more space than the encoded Latin-1 text. Account for both.
                        budget.consume(text.length * 2L)
                        if (key == "XML:com.adobe.xmp") collector.readXmp(text.encodeToByteArray(), "PNG XMP", budget)
                        else collector.text(key, text, "PNG $type")
                    }
                }
                // IDAT is skipped without allocating/decompressing image pixels.
                offset += 12 + length
            }
            return result(MetadataCoverage.BUDGET_EXCEEDED)
        } catch (_: MetadataBudgetExceeded) {
            return result(MetadataCoverage.BUDGET_EXCEEDED)
        } catch (_: InvalidMetadata) {
            return result(MetadataCoverage.MALFORMED)
        } catch (_: okio.IOException) {
            return result(MetadataCoverage.MALFORMED)
        } catch (_: CharacterCodingException) {
            return result(MetadataCoverage.MALFORMED)
        }
    }
}

private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
private fun uint32(bytes: ByteArray, start: Int): Long = (0..3).fold(0L) { n, i -> (n shl 8) or (bytes[start + i].toLong() and 255) }
private fun pngText(bytes: ByteArray, key: String): String {
    // ComfyUI's graph and XMP packets use UTF-8. Other tEXt/zTXt fields
    // remain Latin-1 even when their bytes happen to look like UTF-8.
    if (key !in setOf("prompt", "workflow", "XML:com.adobe.xmp")) return metadataLatin1(bytes)
    return try { bytes.decodeToString(throwOnInvalidSequence = true) }
    catch (_: CharacterCodingException) { metadataLatin1(bytes) }
}

internal fun pngCrc(bytes: ByteArray): Long {
    var crc = -1
    bytes.forEach { value ->
        crc = crc xor (value.toInt() and 255)
        repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
    }
    return crc.inv().toLong() and 0xffffffffL
}

private suspend fun inflateBounded(bytes: ByteArray, limit: Int): ByteArray {
    val output = Buffer()
    InflaterSource(Buffer().write(bytes), Inflater()).use { source ->
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = source.read(output, minOf(8192L, limit + 1L - output.size))
            if (output.size > limit) throw MetadataBudgetExceeded()
            if (count == -1L) return output.readByteArray()
            if (count == 0L) throw InvalidMetadata()
        }
    }
}
