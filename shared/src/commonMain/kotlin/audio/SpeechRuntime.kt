package com.valoser.futacha.shared.audio

import androidx.compose.runtime.staticCompositionLocalOf
import com.valoser.futacha.shared.audio.voicevox.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal val LocalSpeechRuntime = staticCompositionLocalOf<SpeechRuntime?> { null }

internal fun <T> MutableStateFlow<T>.exchange(next: T): T {
    while (true) {
        val old = value
        if (compareAndSet(old, next)) return old
    }
}

internal class SpeechRuntime(context: Any?, val platform: VoicePlatform = createVoicePlatform(context),
    val assets: VoiceAssetStore = VoiceAssetStore(platform::directory, platform::availableBytes)) {
    private val closed = MutableStateFlow(false)
    private val engines = MutableStateFlow<Set<VoicevoxSpeechEngine>>(emptySet())
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    fun createEngine(): VoicevoxSpeechEngine {
        check(!closed.value) { "読み上げは終了しました" }
        return VoicevoxSpeechEngine(this).also { engine ->
            engines.update { it + engine }
            if (closed.value) engine.close()
        }
    }
    fun remove(engine: VoicevoxSpeechEngine) { engines.update { it - engine } }
    fun notifyFallback() {
        mutableNotice.value = if (!platform.available) "ずんだもんはこの端末では利用できないため、端末の音声で読み上げます。"
        else "ずんだもんの音声データが未準備のため、端末の音声で読み上げます。設定から追加できます。"
    }
    fun clearNotice() { mutableNotice.value = null }
    fun stop() { engines.value.forEach { it.stop() } }
    fun close() { closed.value = true; engines.value.toList().forEach { it.close() } }
}
