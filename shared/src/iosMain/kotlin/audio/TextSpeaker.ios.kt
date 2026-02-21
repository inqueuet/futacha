@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.util.Logger

actual class TextSpeaker actual constructor(platformContext: Any?) {
    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        Logger.w("TextSpeaker", "iOS の読み上げ機能は現在利用できません")
    }

    actual fun stop() {
        // No-op on iOS when AVSpeechSynthesizer is unavailable in the toolchain.
    }

    actual fun close() {
        // No-op on iOS when AVSpeechSynthesizer is unavailable in the toolchain.
    }
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
