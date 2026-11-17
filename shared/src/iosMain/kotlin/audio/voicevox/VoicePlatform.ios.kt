@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.util.currentIosEquivalentVersion
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
    // The iOS release, also on an Apple-silicon Mac where operatingSystemVersion is the macOS one (L77).
    override val available: Boolean get() = currentIosEquivalentVersion().major >= 16
    override val unavailableReason = "ずんだもんにはiOS 16以降が必要です"
    override fun directory(): Path {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: error("音声データの保存先を開けません")
        val path = "$base/futacha/voice_assets".toPath()
        FileSystem.SYSTEM.createDirectories(path)
        check(NSURL.fileURLWithPath(path.toString()).setResourceValue(true, NSURLIsExcludedFromBackupKey, null))
        return path
    }
    override fun availableBytes(): Long? = iosVoiceAssetAvailableBytes()
    override fun createPlayer(): VoicePcmPlayer = IosVoicePcmPlayer()
    override fun createNative(pack: VoicePack): VoiceNativeSession {
        check(available) { unavailableReason }
        val base = NSBundle.mainBundle.privateFrameworksPath ?: error("音声ライブラリの保存先を確認できません")
        return IosVoiceNativeSession("$base/voicevox_core.framework/voicevox_core",
            "$base/voicevox_onnxruntime.framework/voicevox_onnxruntime", pack)
    }
}

/** Capacity lookup kept separate from the native voice runtime for deterministic admission tests. */
internal fun iosVoiceAssetAvailableBytes(
    importantUsageBytes: () -> Long? = {
        (NSURL.fileURLWithPath(NSHomeDirectory())
            .resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), null)
            ?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber)?.longLongValue
    },
    legacyFreeBytes: () -> Long? = {
        (NSFileManager.defaultManager.attributesOfFileSystemForPath(NSHomeDirectory(), null)
            ?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
    }
): Long? {
    // Important-usage capacity includes space iOS can reclaim (purgeable caches); 0 is a real full volume (L5).
    val important = try { importantUsageBytes() } catch (_: Exception) { null }
    if (important != null && important >= 0) return important
    return legacyFreeBytes()?.takeIf { it >= 0 }
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
        val status = futacha_voice_query(session, text, output.ptr)
        if (status == FUTACHA_VOICE_TEXT_ANALYSIS_ERROR) throw VoiceTextAnalysisException("入力テキストの解析に失敗しました")
        checkVoice(status)
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
