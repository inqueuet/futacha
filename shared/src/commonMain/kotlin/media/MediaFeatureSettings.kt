package com.valoser.futacha.shared.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
        fun decode(value: String?): MediaFeatureSettings = value?.let {
            runCatching { json.decodeFromString<MediaFeatureSettings>(it) }.getOrNull()?.takeIf { it.version == 1 }
        } ?: Disabled
        fun encode(settings: MediaFeatureSettings): String {
            require(settings.version == 1)
            return json.encodeToString(settings)
        }
    }
}

enum class MediaFeature { PROMPT, IMAGE_EDITOR, VIDEO_EDITOR }
