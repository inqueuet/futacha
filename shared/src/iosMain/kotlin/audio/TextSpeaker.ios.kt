@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

actual class TextSpeaker actual constructor(platformContext: Any?) {
    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
    }

    actual fun stop() {
        // No-op on iOS when AVSpeechSynthesizer is unavailable in the toolchain.
    }

    actual fun close() {
        // No-op on iOS when AVSpeechSynthesizer is unavailable in the toolchain.
    }
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
