@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

const val JAPANESE_TTS_UNAVAILABLE_MESSAGE = "日本語TTSが有効になっていません"

data class SpeechVoice(val id: String, val label: String)
enum class SpeechEngineKind { SYSTEM, VOICEVOX_ZUNDAMON }
data class SpeechSettings(val voiceId: String = "", val rate: Float = 1f, val pitch: Float = 1f,
    val engine: SpeechEngineKind = SpeechEngineKind.SYSTEM, val voicevoxPitch: Float = 0f)

const val SPEECH_ENGINE_KEY = "compat.speech.engine"
const val SPEECH_VOICEVOX_PITCH_KEY = "compat.speech.voicevox.pitchScale"

const val SPEECH_VOICE_KEY = "compat.speech.voice"
const val SPEECH_RATE_KEY = "compat.speech.rate"
const val SPEECH_PITCH_KEY = "compat.speech.pitch"

fun speechSettingsFromPreferences(preferences: Map<String, String>): SpeechSettings = SpeechSettings(
    voiceId = preferences[SPEECH_VOICE_KEY].orEmpty().take(512),
    rate = preferences[SPEECH_RATE_KEY]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f,
    pitch = preferences[SPEECH_PITCH_KEY]?.toFloatOrNull()?.takeIf { it.isFinite() && it in 0.5f..2f } ?: 1f,
    engine = if (preferences[SPEECH_ENGINE_KEY] == "voicevox_zundamon") SpeechEngineKind.VOICEVOX_ZUNDAMON else SpeechEngineKind.SYSTEM,
    voicevoxPitch = preferences[SPEECH_VOICEVOX_PITCH_KEY]?.toFloatOrNull()?.takeIf { it.isFinite() && it in -0.15f..0.15f } ?: 0f
)

internal interface SpeechBackend {
    val supportsPitch: Boolean
    suspend fun availableVoices(): List<SpeechVoice>
    fun configure(settings: SpeechSettings)
    suspend fun prepare()
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

/** Existing one-argument construction remains an OS-only compatibility API. */
class TextSpeaker internal constructor(private val context: Any?, private val runtime: SpeechRuntime?) {
    constructor(platformContext: Any?) : this(platformContext, null)
    private val system = lazy { createSystemSpeechEngine(context) }
    private val voice = lazy { requireNotNull(runtime).createEngine() }
    private val configuration = kotlinx.coroutines.flow.MutableStateFlow(SpeechSettings())
    private val closed = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var effective: SpeechBackend? = null
    val supportsPitch: Boolean get() = if (configuration.value.engine == SpeechEngineKind.VOICEVOX_ZUNDAMON) true else system.value.supportsPitch
    suspend fun availableVoices(): List<SpeechVoice> = system.value.availableVoices()
    fun configure(settings: SpeechSettings) {
        if (configuration.value.engine != settings.engine) stop()
        configuration.value = settings
    }
    suspend fun prepare() {
        check(!closed.value) { "読み上げは終了しました" }
        val settings = configuration.value
        val chosen = if (settings.engine == SpeechEngineKind.VOICEVOX_ZUNDAMON && runtime != null) {
            if (runtime.platform.available) runtime.assets.refresh()
            if (runtime.platform.available && runtime.assets.state.value.ready) voice.value
            else {
                runtime.notifyFallback()
                system.value
            }
        } else system.value
        if (effective !== chosen) effective?.stop()
        effective = chosen
        chosen.configure(settings)
        chosen.prepare()
    }
    suspend fun speak(text: String) {
        prepare()
        try { requireNotNull(effective).speak(text) }
        catch (unavailable: com.valoser.futacha.shared.audio.voicevox.VoicePackUnavailableException) {
            // The pack check runs before any audio. A corrupt/restored pack must
            // never result in a partly repeated utterance or silent voice change.
            runtime?.notifyFallback()
            effective?.stop()
            val fallback = system.value
            effective = fallback
            fallback.configure(configuration.value)
            fallback.prepare()
            fallback.speak(text)
        }
    }
    fun stop() {
        if (system.isInitialized()) system.value.stop()
        if (voice.isInitialized()) voice.value.stop()
    }
    fun close() {
        closed.value = true
        if (system.isInitialized()) system.value.close()
        if (voice.isInitialized()) voice.value.close()
    }
}

internal expect fun createSystemSpeechEngine(platformContext: Any?): SpeechBackend
fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
internal fun createTextSpeaker(platformContext: Any?, runtime: SpeechRuntime?): TextSpeaker = TextSpeaker(platformContext, runtime)
