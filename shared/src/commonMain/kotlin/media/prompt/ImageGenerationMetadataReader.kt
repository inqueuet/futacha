package com.valoser.futacha.shared.media.prompt

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Container traversal only reads metadata; image/animation payloads are skipped by offset. */
internal class ImageGenerationMetadataReader {
    suspend fun read(size: Long, readAt: suspend (Long, Int) -> ByteArray): GenerationMetadata {
        return readFields(size, readAt, GenerationFieldCollector(), MetadataReadBudget())
    }

    internal suspend fun readFields(size: Long, readAt: suspend (Long, Int) -> ByteArray,
        collector: GenerationFieldCollector, budget: MetadataReadBudget): GenerationMetadata {
        suspend fun exact(offset: Long, count: Int): ByteArray {
            currentCoroutineContext().ensureActive()
            if (offset < 0 || count < 0 || offset > size - count) throw InvalidMetadata()
            return readAt(offset, count).also { if (it.size != count) throw InvalidMetadata() }
        }
        try {
            val signature = exact(0, minOf(12L, size.coerceAtLeast(0)).toInt())
            if (signature.size >= 8 && signature.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))) {
                return PngGenerationMetadataReader().readFields(size, readAt, collector, budget)
            }
            if (signature.size >= 2 && metadataUint(signature, 0, 2) == 0xffd8L) {
                var offset = 2L
                val preserved = ImageMetadataSegments()
                val preservedBudget = MetadataReadBudget(MAX_IMAGE_METADATA_BYTES + 4096)
                var hasPreserved = false
                repeat(4096) {
                    val marker = exact(offset, 2)
                    if (marker[0] != 0xff.toByte()) throw InvalidMetadata()
                    val code = marker[1].toInt() and 255
                    if (code == 0xff) { offset++; return@repeat }
                    // Metadata before the first scan only. Do not scan entropy-coded pixels.
                    if (code == 0xda || code == 0xd9) {
                        preserved.finish()?.let(collector::restore)
                        return collector.result(MetadataCoverage.JPEG_METADATA)
                    }
                    if (code == 1 || code in 0xd0..0xd7) { offset += 2; return@repeat }
                    if (code == 0 || code == 0xd8) throw InvalidMetadata()
                    val length = metadataUint(exact(offset + 2, 2), 0, 2).toInt()
                    if (length < 2 || offset + 2 + length > size) throw InvalidMetadata()
                    if (code == 0xef) {
                        val prefix = exact(offset + 4, minOf(length - 2, IMAGE_METADATA_SIGNATURE.size))
                        if (prefix.contentEquals(IMAGE_METADATA_SIGNATURE)) {
                            if (!hasPreserved) {
                                collector.fields.clear(); collector.evidence.clear()
                                hasPreserved = true
                            }
                            preservedBudget.consume(length - 2L)
                            preserved.add(exact(offset + 4, length - 2))
                        }
                    }
                    if (code == 0xe1 || code == 0xfe) {
                        budget.consume(length - 2L)
                        val data = exact(offset + 4, length - 2)
                        when {
                            code == 0xfe -> {
                                val value = try { data.decodeToString(throwOnInvalidSequence = true) }
                                    catch (_: CharacterCodingException) { metadataLatin1(data) }
                                budget.consume(value.length * 2L)
                                collector.text("JPEG comment", value, "JPEG COM")
                            }
                            data.startsWithBytes(EXIF_PREFIX) -> collector.readExif(data.copyOfRange(6, data.size), "JPEG EXIF", budget)
                            data.startsWithBytes(XMP_PREFIX) -> collector.readXmp(data.copyOfRange(XMP_PREFIX.size, data.size), "JPEG XMP", budget)
                            data.startsWithBytes(EXTENDED_XMP_PREFIX) -> collector.limitations += MetadataLimitation.EXTENDED_XMP
                        }
                    }
                    offset += length + 2
                }
                return collector.result(MetadataCoverage.BUDGET_EXCEEDED)
            }
            if (signature.size >= 12 && signature.decodeToString(0, 4) == "RIFF" && signature.decodeToString(8, 12) == "WEBP") {
                val end = metadataUint(signature, 4, 4, little = true) + 8
                if (end < 12 || end > size || end % 2 != 0L) throw InvalidMetadata()
                var offset = 12L
                repeat(4096) {
                    if (offset == end) return collector.result(MetadataCoverage.WEBP_METADATA)
                    if (offset > end - 8) throw InvalidMetadata()
                    val header = exact(offset, 8)
                    val length = metadataUint(header, 4, 4, little = true)
                    val padded = length + length % 2
                    if (padded > end - offset - 8) throw InvalidMetadata()
                    val type = header.decodeToString(0, 4)
                    if (type == "EXIF" || type == "XMP ") {
                        budget.consume(length)
                        val data = exact(offset + 8, length.toInt())
                        if (type == "EXIF") collector.readExif(
                            if (data.startsWithBytes(EXIF_PREFIX)) data.copyOfRange(6, data.size) else data, "WebP EXIF", budget
                        ) else collector.readXmp(data, "WebP XMP", budget)
                    }
                    if (length % 2 != 0L && exact(offset + 8 + length, 1)[0] != 0.toByte()) throw InvalidMetadata()
                    offset += 8 + padded
                }
                return collector.result(MetadataCoverage.BUDGET_EXCEEDED)
            }
            return collector.result(MetadataCoverage.UNSUPPORTED)
        } catch (_: InvalidMetadata) {
            return collector.result(MetadataCoverage.MALFORMED)
        } catch (_: MetadataBudgetExceeded) {
            return collector.result(MetadataCoverage.BUDGET_EXCEEDED)
        } catch (_: CharacterCodingException) {
            return collector.result(MetadataCoverage.MALFORMED)
        }
    }
}

private val EXIF_PREFIX = "Exif\u0000\u0000".encodeToByteArray()
private val XMP_PREFIX = "http://ns.adobe.com/xap/1.0/\u0000".encodeToByteArray()
private val EXTENDED_XMP_PREFIX = "http://ns.adobe.com/xmp/extension/\u0000".encodeToByteArray()
private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
