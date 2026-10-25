package com.valoser.futacha.shared.audio

import androidx.compose.runtime.staticCompositionLocalOf
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.*
import com.valoser.futacha.shared.audio.voicevox.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal val LocalSpeechRuntime = staticCompositionLocalOf<SpeechRuntime?> { null }

internal fun <T> MutableStateFlow<T>.exchange(next: T): T {
    while (true) {
        val old = value
        if (compareAndSet(old, next)) return old
    }
}

internal data class VoiceDeletionState(val removing: Boolean = false, val error: String? = null)

internal const val VOICE_DELETE_PREFERENCE_FAILURE = "読み上げ設定を保存できなかったため、音声データを削除しませんでした。再試行してください"
internal const val VOICE_DELETE_FAILURE = "音声データを削除できませんでした"

internal class SpeechRuntime(context: Any?, val platform: VoicePlatform = createVoicePlatform(context),
    val assets: VoiceAssetStore = VoiceAssetStore(platform::directory, platform::availableBytes)) {
    private val closed = MutableStateFlow(false)
    // Explicit transfers belong to the app runtime, so leaving settings does not cancel them.
    private val assetTasks = CoroutineScope(SupervisorJob() + AppDispatchers.io)
    private var downloadJob: Job? = null
    fun downloadVoiceAssets(): Job {
        check(!closed.value) { "読み上げは終了しました" }
        downloadJob?.takeIf { it.isActive }?.let { return it }
        return assetTasks.launch {
            try { assets.install() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* AssetStore publishes the failure and keeps resumable bytes. */ }
        }.also { downloadJob = it }
    }
    fun cancelVoiceAssetDownload() { downloadJob?.cancel() }

    private val mutableDeletion = MutableStateFlow(VoiceDeletionState())
    val deletion = mutableDeletion.asStateFlow()
    private var deleteJob: Job? = null
    /** Deletion belongs to the app runtime like the download, so leaving the settings does not stop it (L10).
     * The engine preference returns to the system voice first: an interruption never leaves ずんだもん selected
     * without its data, and a preference that cannot be saved keeps the data. */
    fun deleteVoiceAssets(commitSystemEngine: suspend () -> Unit): Job {
        check(!closed.value) { "読み上げは終了しました" }
        deleteJob?.takeIf { it.isActive }?.let { return it }
        stop()
        val transfer = downloadJob?.also { it.cancel() }
        mutableDeletion.value = VoiceDeletionState(removing = true)
        return assetTasks.launch {
            var failure: String? = null
            try {
                transfer?.join()
                val committed = try { commitSystemEngine(); true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failure = VOICE_DELETE_PREFERENCE_FAILURE; false }
                if (committed) {
                    try { assets.delete() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failure = VOICE_DELETE_FAILURE }
                }
            } finally { mutableDeletion.value = VoiceDeletionState(error = failure) }
        }.also { deleteJob = it }
    }
    fun clearVoiceDeletionError() { mutableDeletion.update { it.copy(error = null) } }
    private val engines = MutableStateFlow<Set<VoicevoxSpeechEngine>>(emptySet())
    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice = mutableNotice.asStateFlow()
    fun createEngine(): VoicevoxSpeechEngine {
        check(!closed.value) { "読み上げは終了しました" }
        return VoicevoxSpeechEngine(this).also { engine ->
            engines.update { it + engine }
            if (closed.value) engine.close()
        }
    }
    fun remove(engine: VoicevoxSpeechEngine) { engines.update { it - engine } }
    fun notifyFallback() {
        mutableNotice.value = if (!platform.available) "ずんだもんはこの端末では利用できないため、端末の音声で読み上げます。"
        else "ずんだもんの音声データが未準備のため、端末の音声で読み上げます。設定から追加できます。"
    }
    fun clearNotice() { mutableNotice.value = null }
    fun stop() { engines.value.forEach { it.stop() } }
    fun close() { closed.value = true; assetTasks.cancel(); engines.value.toList().forEach { it.close() } }
}
