@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

const val JAPANESE_TTS_UNAVAILABLE_MESSAGE = "日本語TTSが有効になっていません"

data class SpeechVoice(val id: String, val label: String)
data class SpeechSettings(val voiceId: String = "", val rate: Float = 1f, val pitch: Float = 1f)

const val SPEECH_VOICE_KEY = "compat.speech.voice"
const val SPEECH_RATE_KEY = "compat.speech.rate"
const val SPEECH_PITCH_KEY = "compat.speech.pitch"

fun speechSettingsFromPreferences(preferences: Map<String, String>): SpeechSettings = SpeechSettings(
    voiceId = preferences[SPEECH_VOICE_KEY].orEmpty().take(512),
    rate = preferences[SPEECH_RATE_KEY]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f,
    pitch = preferences[SPEECH_PITCH_KEY]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f
)

expect class TextSpeaker(platformContext: Any?) {
    val supportsPitch: Boolean
    suspend fun availableVoices(): List<SpeechVoice>
    fun configure(settings: SpeechSettings)
    suspend fun prepare()
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

expect fun createTextSpeaker(platformContext: Any?): TextSpeaker
