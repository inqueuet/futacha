package com.valoser.futacha.shared.media.prompt

import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

internal const val PRESERVED_VIDEO_METADATA_KEY = "com.valoser.futacha.generation"
internal const val LEGACY_PRESERVED_VIDEO_METADATA_KEY = "com.valoser.toshikari.generation"
internal const val MAX_PRESERVED_VIDEO_METADATA_BYTES = 2 * 1024 * 1024
internal const val VIDEO_PRESERVATION_SCAN_BUDGET = 8 * 1024 * 1024
internal fun isPreservedVideoMetadataKey(key: String) = key == PRESERVED_VIDEO_METADATA_KEY || key == LEGACY_PRESERVED_VIDEO_METADATA_KEY

/** An unsigned source record. Reading this schema is not prompt/AI interpretation. */
@Serializable
internal data class PreservedVideoMetadata(
    @Required val version: Int = 1,
    // Retain Toshikari's previous opaque serialization verbatim; do not interpret it during export.
    val generation: String? = null,
    @Required val fields: List<Field> = emptyList(),
    @Required val sourceC2pa: List<String> = emptyList(),
    @Required val edits: List<Edit> = emptyList()
) {
    @Serializable data class Field(val key: String, val value: String)
    @Serializable data class Edit(
        val operation: String = "mosaic", val application: String = "Futacha", val atUtc: String, val regionCount: Int
    )

    fun encode(): String {
        validate()
        return codec.encodeToString(serializer(), this).also {
            require(it.encodeToByteArray().size <= MAX_PRESERVED_VIDEO_METADATA_BYTES) { "元動画の生成情報が保存上限を超えています" }
        }
    }

    private fun validate() {
        require(version == 1 && fields.size <= 256 && sourceC2pa.size <= 64 && edits.size <= 128)
        require(fields.none { isPreservedVideoMetadataKey(it.key) }) { "生成情報の保存記録が入れ子になっています" }
        require(edits.all { it.operation == "mosaic" && it.application.isNotBlank() && it.application.length <= 128 &&
            it.atUtc.length in 1..64 && it.regionCount in 1..16 })
        require(sourceC2pa.all { it.length <= MAX_PRESERVED_VIDEO_METADATA_BYTES && runCatching { Base64.decode(it) }.isSuccess })
    }

    companion object {
        private val codec = Json { encodeDefaults = true; explicitNulls = false }
        fun decode(text: String): PreservedVideoMetadata? = try {
            require(text.encodeToByteArray().size <= MAX_PRESERVED_VIDEO_METADATA_BYTES)
            // Opaque JSON strings can contain braces. Only structural nesting is counted.
            var depth = 0; var quoted = false; var escaped = false
            text.forEach { char ->
                if (quoted) {
                    if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> { depth++; require(depth <= 12) }
                    '}', ']' -> { depth--; require(depth >= 0) }
                }
            }
            require(!quoted && depth == 0)
            codec.decodeFromString(serializer(), text).also { it.validate() }
        } catch (_: IllegalArgumentException) { null }

        fun fromScan(scan: VideoMetadataScan): PreservedVideoMetadata {
            require(scan.coverage in setOf(MetadataCoverage.MP4_METADATA, MetadataCoverage.WEBM_METADATA) &&
                scan.limitations.all { it == MetadataLimitation.C2PA }) {
                "元動画の生成情報を最後まで読み取れませんでした。情報を保持して保存するため、別の元ファイルを選んでください"
            }
            val previous = scan.fields.filter { isPreservedVideoMetadataKey(it.key) }
            if (previous.isNotEmpty()) {
                require(previous.size == 1) { "元動画の生成情報の保存記録が重複しています" }
                return requireNotNull(decode(previous.single().value)) { "元動画に保存された生成情報を読み取れません" }
            }
            return PreservedVideoMetadata(fields = scan.fields.map { Field(it.key, it.value) }.distinct(),
                sourceC2pa = scan.sourceC2pa.map { Base64.encode(it) }.distinct())
        }
    }
}
