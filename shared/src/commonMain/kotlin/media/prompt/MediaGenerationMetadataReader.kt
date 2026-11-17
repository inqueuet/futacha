package com.valoser.futacha.shared.media.prompt

/** Signature dispatch shares the original file; it never opens a network connection. */
internal class MediaGenerationMetadataReader {
    suspend fun read(size: Long, readAt: suspend (Long, Int) -> ByteArray): GenerationMetadata {
        val signature = readAt(0, minOf(size.coerceAtLeast(0), 12).toInt())
        val video = signature.size >= 4 && metadataUint(signature, 0, 4) == 0x1a45dfa3L ||
            signature.size >= 8 && metadataLatin1(signature.copyOfRange(4, 8)) in setOf("ftyp", "moov", "mdat", "free", "wide", "skip")
        return if (video) VideoMetadataReader().read(size, readAt).generation()
            else ImageGenerationMetadataReader().read(size, readAt)
    }
}
