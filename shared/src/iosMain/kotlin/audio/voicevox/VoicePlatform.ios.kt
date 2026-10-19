@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.voicevox.*
import cnames.structs.FutachaVoiceSession
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newSingleThreadContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.*

internal actual val voiceNativeDispatcher: CoroutineDispatcher by lazy { newSingleThreadContext("FutachaVoicevox") }
internal actual fun createVoicePlatform(context: Any?): VoicePlatform = IosVoicePlatform()

private class IosVoicePlatform : VoicePlatform {
    override val available: Boolean get() = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 16 }
    override val unavailableReason = "ずんだもんにはiOS 16以降が必要です"
    override fun directory(): Path {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("音声データの保存先を開けません")
        val path = "$base/futacha/voice_assets".toPath()
        FileSystem.SYSTEM.createDirectories(path)
        check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
        return path
    }
    override fun availableBytes(): Long? = (NSFileManager.defaultManager.attributesOfFileSystemForPath(
        NSHomeDirectory(), null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
    override fun createPlayer(): VoicePcmPlayer = IosVoicePcmPlayer()
    override fun createNative(pack: VoicePack): VoiceNativeSession {
        check(available) { unavailableReason }
        val base = NSBundle.mainBundle.privateFrameworksPath ?: error("音声ライブラリの保存先を確認できません")
        return IosVoiceNativeSession("$base/voicevox_core.framework/voicevox_core",
            "$base/voicevox_onnxruntime.framework/voicevox_onnxruntime", pack)
    }
}

internal class IosVoiceNativeSession(core: String, runtime: String, pack: VoicePack) : VoiceNativeSession {
    private val session: CPointer<FutachaVoiceSession> = memScoped {
        val output = alloc<CPointerVar<FutachaVoiceSession>>(); output.value = null
        checkVoice(futacha_voice_open(core, runtime, pack.dictionary.toString(), pack.model.toString(),
            if (NSProcessInfo.processInfo.physicalMemory <= 2uL * 1024u * 1024u * 1024u) 1u else 2u, output.ptr))
        requireNotNull(output.value)
    }
    override fun query(text: String): String = memScoped {
        val output = alloc<CPointerVar<ByteVar>>(); output.value = null
        checkVoice(futacha_voice_query(session, text, output.ptr))
        val json = requireNotNull(output.value)
        try { json.toKString() } finally { futacha_voice_json_free(json) }
    }
    override fun synthesize(query: String): ByteArray = memScoped {
        val output = alloc<CPointerVar<UByteVar>>(); output.value = null
        val length = alloc<ULongVar>(); length.value = 0u
        checkVoice(futacha_voice_synthesis(session, query, output.ptr, length.ptr))
        val wav = requireNotNull(output.value)
        try {
            check(length.value in 44uL..(16uL * 1024u * 1024u)) { "音声が長すぎます" }
            wav.reinterpret<ByteVar>().readBytes(length.value.toInt())
        } finally { futacha_voice_wav_free(wav) }
    }
    override fun close() = futacha_voice_close(session)
    private fun checkVoice(result: Int) {
        check(result == 0) { futacha_voice_error()?.toKString() ?: "音声の合成に失敗しました" }
    }
}
