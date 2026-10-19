package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.Path

internal actual val voiceNativeDispatcher: CoroutineDispatcher get() = Dispatchers.IO
internal actual fun createVoicePlatform(context: Any?): VoicePlatform = object : VoicePlatform {
    override val available = false
    override val unavailableReason = "ずんだもんはAndroid／iOS向けの機能です"
    override fun directory(): Path = error(unavailableReason)
    override fun availableBytes(): Long? = null
    override fun createNative(pack: VoicePack): VoiceNativeSession = error(unavailableReason)
    override fun createPlayer(): VoicePcmPlayer = error(unavailableReason)
}
