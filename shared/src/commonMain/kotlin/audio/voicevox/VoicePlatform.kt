package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.CoroutineDispatcher
import okio.Path

internal interface VoicePlatform {
    val available: Boolean
    val unavailableReason: String
    fun directory(): Path
    fun availableBytes(): Long?
    fun createNative(pack: VoicePack): VoiceNativeSession
    fun createPlayer(): VoicePcmPlayer
}
internal interface VoiceNativeSession : AutoCloseable {
    fun query(text: String): String
    fun synthesize(query: String): ByteArray
}
internal interface VoicePcmPlayer : AutoCloseable {
    suspend fun prepare(onStop: () -> Unit)
    suspend fun play(wav: ByteArray)
    fun stop()
}
internal expect fun createVoicePlatform(context: Any?): VoicePlatform
internal expect val voiceNativeDispatcher: CoroutineDispatcher
