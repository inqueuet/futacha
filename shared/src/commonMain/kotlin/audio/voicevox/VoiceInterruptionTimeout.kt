package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

internal const val VOICE_INTERRUPTION_TIMEOUT_MILLIS = 25_000L
internal const val VOICE_INTERRUPTION_TIMEOUT_MESSAGE = "音声の割り込みが終わらないため停止しました"

/** Run as a child of PCM playback, including its wait for an interrupted session. */
internal suspend fun guardVoiceInterruption(
    interrupted: StateFlow<Boolean>,
    timeoutMillis: Long = VOICE_INTERRUPTION_TIMEOUT_MILLIS
) {
    interrupted.collectLatest { paused ->
        if (paused) {
            delay(timeoutMillis)
            throw IllegalStateException(VOICE_INTERRUPTION_TIMEOUT_MESSAGE)
        }
    }
}
