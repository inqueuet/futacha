package com.valoser.futacha.shared.media.prompt

internal class InvalidMetadata : Exception()
internal class MetadataBudgetExceeded : Exception()

internal class MetadataReadBudget(private val maxBytes: Int = MAX_METADATA_BYTES) {
    private var used = 0L
    val remaining: Int get() = (maxBytes - used).toInt()
    fun consume(bytes: Long) {
        if (bytes < 0 || bytes > remaining) throw MetadataBudgetExceeded()
        used += bytes
    }
}

internal fun metadataLatin1(bytes: ByteArray): String = buildString(bytes.size) {
    bytes.forEach { append((it.toInt() and 255).toChar()) }
}

internal fun metadataUint(bytes: ByteArray, offset: Int, count: Int, little: Boolean = false): Long {
    if (offset < 0 || count > bytes.size - offset) throw InvalidMetadata()
    return (0 until count).fold(0L) { n, i ->
        (n shl 8) or (bytes[offset + if (little) count - 1 - i else i].toLong() and 255)
    }
}

/** Strict decoding; stripping storage terminators never trims prompt whitespace. */
internal fun metadataUtf16(bytes: ByteArray, little: Boolean): String {
    if (bytes.size % 2 != 0) throw InvalidMetadata()
    var start = 0
    var le = little
    if (bytes.size >= 2) when (metadataUint(bytes, 0, 2)) {
        0xfeffL -> { le = false; start = 2 }
        0xfffeL -> { le = true; start = 2 }
    }
    val text = buildString(bytes.size / 2) {
        for (i in start until bytes.size step 2) append(metadataUint(bytes, i, 2, le).toInt().toChar())
    }
    var i = 0
    while (i < text.length) {
        val char = text[i++]
        if (char.isHighSurrogate()) {
            if (i == text.length || !text[i++].isLowSurrogate()) throw InvalidMetadata()
        } else if (char.isLowSurrogate()) throw InvalidMetadata()
    }
    return text.trimEnd('\u0000')
}

internal class GenerationFieldCollector(val preserveOnly: Boolean = false) {
    val fields = mutableListOf<GenerationTextField>()
    val evidence = mutableListOf<GenerationCandidate>()
    val limitations = mutableSetOf<MetadataLimitation>()
    var malformed = false

    fun result(coverage: MetadataCoverage): GenerationMetadata = GenerationMetadata(
        if (preserveOnly) emptyList() else (classifyGenerationFields(fields) + evidence).take(16),
        if (malformed && coverage != MetadataCoverage.BUDGET_EXCEEDED) MetadataCoverage.MALFORMED else coverage,
        limitations = limitations.toSet()
    )

    fun text(key: String, value: String, source: String) {
        if (fields.size >= 256) throw MetadataBudgetExceeded()
        if (preserveOnly) {
            fields += GenerationTextField(key, value, source)
            return
        }
        if (key == IMAGE_SOURCE_DECLARATION_KEY) {
            declaration(value, source)
            return
        }
        when (key) {
            "parameters", "prompt", "Software", "Comment", "Description" -> fields += GenerationTextField(key, value, source)
            else -> {
                val namedKey = value.substringBefore(':').lowercase()
                if (namedKey == "prompt" && value.substringAfter(':', "").trimStart().startsWith("{")) {
                    fields += GenerationTextField("prompt", value.substringAfter(':'), source)
                } else if (key !in setOf("Make", "Model", "Software")) {
                    // Ordinary camera captions/comments must not become AI or ordinary-copy text.
                    if (parseA1111(value, source).isAi) fields += GenerationTextField("parameters", value, source)
                }
            }
        }
    }

    fun declaration(value: String, source: String) {
        if (preserveOnly) { text(IMAGE_SOURCE_DECLARATION_KEY, value, source); return }
        val kind = when (value) {
            "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia",
            "https://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia" -> AiDeclaration.GENERATED
            "http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia",
            "https://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia" -> AiDeclaration.EDITED
            else -> return
        }
        if (evidence.size >= 16) throw MetadataBudgetExceeded()
        val description = if (kind == AiDeclaration.GENERATED) "AI生成の申告" else "AI編集の申告"
        evidence += GenerationCandidate("$source / $description", null, raw = value, isAi = true, declaration = kind)
    }

    fun restore(record: PreservedImageMetadata) {
        fields.clear(); evidence.clear()
        malformed = record.partial
        record.fields.forEach { text(it.key, it.value, it.source) }
    }

    /** One broken EXIF/XMP block must not suppress later valid blocks in the container. */
    suspend fun readExif(data: ByteArray, source: String, budget: MetadataReadBudget) {
        try { TiffGenerationMetadataReader().read(data, source, this, budget) }
        catch (_: InvalidMetadata) { malformed = true }
        catch (_: CharacterCodingException) { malformed = true }
    }

    suspend fun readXmp(data: ByteArray, source: String, budget: MetadataReadBudget) {
        try { readXmpGenerationMetadata(data, source, this, budget) }
        catch (_: InvalidMetadata) { malformed = true }
        catch (_: CharacterCodingException) { malformed = true }
    }
}
