package com.valoser.futacha.shared.media.prompt

/** Text is retained verbatim; presentation truncation must never become clipboard input. */
data class GenerationCandidate(
    val source: String,
    val positive: String?,
    val negative: String? = null,
    val settings: String? = null,
    val raw: String,
    val isAi: Boolean,
    val declaration: AiDeclaration? = null
)

enum class AiDeclaration { GENERATED, EDITED }

enum class MetadataCoverage { PNG_METADATA, JPEG_METADATA, WEBP_METADATA, MP4_METADATA, WEBM_METADATA, UNSUPPORTED, MALFORMED, BUDGET_EXCEEDED, SOURCE_UNAVAILABLE }

enum class MetadataLimitation { EXTENDED_XMP, EXIF_ENCODING, COMPLEX_XMP, C2PA, UNKNOWN_WEBM_CLUSTER }

data class GenerationMetadata(
    val candidates: List<GenerationCandidate> = emptyList(),
    val coverage: MetadataCoverage,
    val parserVersion: Int = 4,
    val limitations: Set<MetadataLimitation> = emptySet()
) {
    val hasAiEvidence: Boolean get() = candidates.any { it.isAi }
}

internal const val MAX_METADATA_BYTES = 256 * 1024
internal const val MAX_COPY_BYTES = 64 * 1024

internal fun generationInlineText(candidate: GenerationCandidate): String = candidate.positive ?: when (candidate.declaration) {
    AiDeclaration.GENERATED -> "AI生成の記録があります。プロンプト本文は確認できませんでした。"
    AiDeclaration.EDITED -> "AI編集の記録があります。プロンプト本文は確認できませんでした。"
    null -> candidate.raw
}

internal fun canCopyPrompt(text: String): Boolean = text.encodeToByteArray().size <= MAX_COPY_BYTES

/** Bound full text layout separately from clipboard size (including newline-heavy payloads). */
internal fun canSelectWholePrompt(text: String): Boolean = text.length <= 2_048 && text.count { it == '\n' || it == '\r' } <= 128

internal fun promptPreview(text: String): String {
    // Don't leave a lone UTF-16 surrogate at the end of the bounded layout input.
    var end = minOf(text.length, 600)
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end--
    return text.substring(0, end) + if (end < text.length) "…" else ""
}
