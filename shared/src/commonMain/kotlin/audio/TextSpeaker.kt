@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

expect class TextSpeaker(platformContext: Any?) {
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

expect fun createTextSpeaker(platformContext: Any?): TextSpeaker
