package com.valoser.futacha.shared.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal const val MEDIA_FEATURE_SETTINGS_KEY = "media_feature_settings_v1"

@Serializable
enum class PromptPlacement {
    @SerialName("labels_and_inline") LABELS_AND_INLINE,
    @SerialName("labels_only") LABELS_ONLY,
    @SerialName("inline_only") INLINE_ONLY
}

@Serializable
data class MediaFeatureSettings(
    val version: Int = 1,
    @SerialName("prompt_display_enabled") val promptDisplayEnabled: Boolean = false,
    @SerialName("image_editor_enabled") val imageEditorEnabled: Boolean = false,
    @SerialName("video_editor_enabled") val videoEditorEnabled: Boolean = false,
    @SerialName("prompt_placement") val promptPlacement: PromptPlacement = PromptPlacement.LABELS_AND_INLINE
) {
    val showAiLabels: Boolean get() = version == 1 && promptDisplayEnabled && promptPlacement != PromptPlacement.INLINE_ONLY
    val showInlinePrompt: Boolean get() = version == 1 && promptDisplayEnabled && promptPlacement != PromptPlacement.LABELS_ONLY

    fun isEnabled(feature: MediaFeature): Boolean = version == 1 && when (feature) {
        MediaFeature.PROMPT -> promptDisplayEnabled
        MediaFeature.IMAGE_EDITOR -> imageEditorEnabled
        MediaFeature.VIDEO_EDITOR -> videoEditorEnabled
    }

    companion object {
        val Disabled = MediaFeatureSettings()
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        /**
         * Reads each field on its own: an unknown placement or a malformed field falls back to
         * that field's default instead of turning every media feature off (and the next save
         * then persisting that). Only an unreadable document or another version is Disabled.
         */
        fun decode(value: String?): MediaFeatureSettings {
            val root = value?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() } as? JsonObject
                ?: return Disabled
            val version = root["version"]?.let { (it as? JsonPrimitive)?.intOrNull } ?: 1
            if (version != 1) return Disabled
            fun flag(name: String): Boolean = (root[name] as? JsonPrimitive)?.booleanOrNull ?: false
            val placementName = (root["prompt_placement"] as? JsonPrimitive)?.contentOrNull
            val placement = PromptPlacement.entries.firstOrNull { entry ->
                runCatching { json.encodeToString(PromptPlacement.serializer(), entry) }.getOrNull() == "\"$placementName\""
            } ?: PromptPlacement.LABELS_AND_INLINE
            return MediaFeatureSettings(
                version = 1,
                promptDisplayEnabled = flag("prompt_display_enabled"),
                imageEditorEnabled = flag("image_editor_enabled"),
                videoEditorEnabled = flag("video_editor_enabled"),
                promptPlacement = placement
            )
        }
        fun encode(settings: MediaFeatureSettings): String {
            require(settings.version == 1)
            return json.encodeToString(settings)
        }
    }
}

enum class MediaFeature { PROMPT, IMAGE_EDITOR, VIDEO_EDITOR }
