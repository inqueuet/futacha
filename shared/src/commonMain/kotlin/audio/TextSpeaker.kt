@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

const val JAPANESE_TTS_UNAVAILABLE_MESSAGE = "日本語TTSが有効になっていません"

expect class TextSpeaker(platformContext: Any?) {
    suspend fun prepare()
    suspend fun speak(text: String)
    fun stop()
    fun close()
}

expect fun createTextSpeaker(platformContext: Any?): TextSpeaker
