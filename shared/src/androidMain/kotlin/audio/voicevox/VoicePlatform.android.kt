package com.valoser.futacha.shared.audio.voicevox

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.StatFs
import com.valoser.futacha.shared.util.detectDevicePerformanceProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import okio.Path
import okio.Path.Companion.toPath
import java.util.concurrent.Executors

internal actual val voiceNativeDispatcher: CoroutineDispatcher by lazy {
    Executors.newSingleThreadExecutor { task -> Thread(task, "FutachaVoicevox").apply { isDaemon = true } }.asCoroutineDispatcher()
}
internal actual fun createVoicePlatform(context: Any?): VoicePlatform = AndroidVoicePlatform(requireNotNull(context as? Context).applicationContext)

private class AndroidVoicePlatform(private val context: Context) : VoicePlatform {
    override val available: Boolean get() = Build.VERSION.SDK_INT >= 29 && Process.is64Bit()
    override val unavailableReason = "ずんだもんにはAndroid 10以降の64bit環境が必要です"
    override fun directory(): Path = context.noBackupFilesDir.absolutePath.toPath().resolve("voice_assets")
    override fun availableBytes(): Long? = runCatching { StatFs(context.noBackupFilesDir.absolutePath).availableBytes }.getOrNull()
    override fun createPlayer(): VoicePcmPlayer = AndroidVoicePcmPlayer(context)
    override fun createNative(pack: VoicePack): VoiceNativeSession {
        check(available) { unavailableReason }
        VoicevoxNative.ensureLoaded()
        val handle = VoicevoxNative.open("libvoicevox_core.so", "libvoicevox_onnxruntime.so", pack.dictionary.toString(),
            pack.model.toString(), if (detectDevicePerformanceProfile(context).isLowRam) 1 else 2)
        check(handle != 0L)
        return object : VoiceNativeSession {
            override fun query(text: String): String {
                val bytes = VoicevoxNative.query(handle, text.encodeToByteArray())
                if (bytes.isEmpty()) throw VoiceTextAnalysisException("入力テキストの解析に失敗しました")
                return bytes.decodeToString()
            }
            override fun synthesize(query: String): ByteArray = VoicevoxNative.synthesize(handle, query.encodeToByteArray())
            override fun close() = VoicevoxNative.close(handle)
        }
    }
}

internal object VoicevoxNative {
    private val loaded by lazy { System.loadLibrary("futacha_voicevox_bridge") }
    fun ensureLoaded() { loaded }
    external fun open(core: String, runtime: String, dictionary: String, model: String, threads: Int): Long
    external fun query(handle: Long, utf8: ByteArray): ByteArray
    external fun synthesize(handle: Long, utf8: ByteArray): ByteArray
    external fun close(handle: Long)
}
