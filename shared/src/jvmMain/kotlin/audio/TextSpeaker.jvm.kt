package com.valoser.futacha.shared.audio

actual class TextSpeaker actual constructor(platformContext: Any?) {
    actual suspend fun speak(text: String) = Unit
    actual fun stop() = Unit
    actual fun close() = Unit
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
