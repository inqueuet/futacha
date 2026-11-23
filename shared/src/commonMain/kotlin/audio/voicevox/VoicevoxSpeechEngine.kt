package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

// Includes old roots whose cancelled synchronous native call has not returned yet.
private val nativeExecution = Mutex()
private val voicePlayback = Mutex()
private class SharedVoiceModel(val key: Pair<VoicePlatform, VoicePack>, val session: VoiceNativeSession, var users: Int = 0)
// Multiple speakers in one root (thread and settings sample) share the loaded model.
// Access, reference counts and all C calls are serialized by nativeExecution.
private val nativeModels = mutableMapOf<Pair<VoicePlatform, VoicePack>, SharedVoiceModel>()

internal class VoicevoxSpeechEngine(
    private val runtime: SpeechRuntime,
    private val acquirePack: suspend () -> VoicePackLease = runtime.assets::acquireVerifiedPack
) : SpeechBackend {
    override val supportsPitch = true
    private val settings = MutableStateFlow(SpeechSettings())
    private class Utterance(val job: Job) { val retainModel = MutableStateFlow(false) }
    private data class Resources(val generation: Long, val pack: VoicePackLease, val native: SharedVoiceModel)
    private val active = MutableStateFlow<Utterance?>(null)
    private val player = MutableStateFlow<VoicePcmPlayer?>(null)
    private val closed = MutableStateFlow(false)
    private val generation = MutableStateFlow(0L)
    private val execution = Mutex()
    private val prepareLock = Mutex()
    // Resources are only read/changed under execution, on the native dispatcher.
    private var resources: Resources? = null
    // may 1449255847: the post after this one (hintNext), and its first sentence made while this post plays. One ahead
    // only; a stop, a skip or new settings give it a different key, so it is never spoken for another text.
    private data class PrefetchKey(val chunk: String, val rate: Float, val pitch: Float, val generation: Long)
    private val nextHint = MutableStateFlow<String?>(null)
    // Only read/changed under execution.
    private var prefetched: Pair<PrefetchKey, ByteArray?>? = null
    private val cleanup = CoroutineScope(SupervisorJob() + voiceNativeDispatcher + CoroutineExceptionHandler { _, failure ->
        Logger.e("VoicevoxSpeechEngine", "音声エンジンの解放に失敗しました", failure)
    })

    override fun configure(settings: SpeechSettings) { this.settings.value = settings }
    override fun hintNext(text: String?) { nextHint.value = text }
    private fun prefetchKeyOf(chunk: String, configuration: SpeechSettings, run: Long) =
        PrefetchKey(chunk, configuration.rate, configuration.voicevoxPitch, run)
    override suspend fun availableVoices(): List<SpeechVoice> = emptyList()
    override suspend fun prepare() = prepareLock.withLock {
        check(!closed.value && runtime.platform.available) { runtime.platform.unavailableReason }
        if (player.value == null) {
            val created = runtime.platform.createPlayer()
            if (closed.value || !player.compareAndSet(null, created)) created.close()
        }
        check(!closed.value)
    }

    override suspend fun speak(text: String) = coroutineScope {
        check(!closed.value)
        val call = Utterance(currentCoroutineContext()[Job]!!)
        active.exchange(call)?.job?.cancel()
        val configuration = settings.value
        val run = generation.value
        try {
            withContext(voiceNativeDispatcher) {
                execution.withLock {
                    voicePlayback.withLock {
                        ensureActive()
                        if (closed.value || run != generation.value) throw CancellationException("読み上げを停止しました")
                        val output = requireNotNull(player.value)
                        var completed = false
                        try {
                            output.prepare(::stop)
                            if (resources?.generation != run) {
                                releaseResources()
                                val pack = acquirePack()
                                try {
                                    nativeExecution.withLock {
                                        val key = runtime.platform to pack.pack
                                        val native = nativeModels.getOrPut(key) { SharedVoiceModel(key, runtime.platform.createNative(pack.pack)) }
                                        native.users++
                                        // Own synchronous creates before checking cancellation.
                                        resources = Resources(run, pack, native)
                                        try { ensureActive() }
                                        catch (cancelled: CancellationException) {
                                            if (!call.retainModel.value) releaseResourcesLocked()
                                            throw cancelled
                                        }
                                    }
                                } catch (failure: Throwable) {
                                    if (resources?.pack !== pack) pack.close()
                                    throw failure
                                }
                            }
                            // On playback failure, cancel/join every lookahead before the outer finally closes the model.
                            coroutineScope {
                            val native = requireNotNull(resources).native.session
                            suspend fun synthesizeChunk(chunk: String): ByteArray? = nativeExecution.withLock {
                                ensureActive()
                                val analyzed = try { native.query(chunk) }
                                catch (_: VoiceTextAnalysisException) { return@withLock null }
                                val original = Json.parseToJsonElement(analyzed).jsonObject
                                val query = JsonObject(original + voicevoxQueryOverrides(configuration)).toString()
                                ensureActive()
                                val result = native.synthesize(query)
                                ensureActive()
                                result
                            }
                            // may 1447418590: the next chunk is made while this one plays, so the voice does not
                            // stop for the whole synthesis time between sentences and posts.
                            val chunks = voicevoxTextChunks(text).filter(::voicevoxHasSpokenText)
                            val ready = prefetched?.takeIf { (key, _) ->
                                key == chunks.firstOrNull()?.let { prefetchKeyOf(it, configuration, run) }
                            }
                            prefetched = null
                            var next: Deferred<ByteArray?>? = chunks.firstOrNull()?.let { first ->
                                if (ready != null) CompletableDeferred(ready.second) else async { synthesizeChunk(first) }
                            }
                            // The following post's first sentence, started once this post's last one is made.
                            var ahead: Pair<PrefetchKey, Deferred<ByteArray?>>? = null
                            for (index in chunks.indices) {
                                ensureActive()
                                val wav = requireNotNull(next).await()
                                next = chunks.getOrNull(index + 1)?.let { following -> async { synthesizeChunk(following) } }
                                if (next == null && ahead == null) {
                                    ahead = nextHint.exchange(null)?.let(::voicevoxFirstSpokenChunk)?.let { chunk ->
                                        prefetchKeyOf(chunk, configuration, run) to async {
                                            // A failure of the made-ahead sentence is left to the next post, which makes it again.
                                            try { synthesizeChunk(chunk) } catch (cancelled: CancellationException) { throw cancelled }
                                            catch (failure: Exception) {
                                                Logger.w("VoicevoxSpeechEngine", "次のレスの先読みに失敗しました: ${failure.message}")
                                                null
                                            }
                                        }
                                    }
                                }
                                ensureActive() // A stopped call never emits its late result.
                                if (wav == null) continue
                                require(wav.size in 44..16 * 1024 * 1024) { "音声データの長さが不正です" }
                                output.play(wav)
                            }
                            ahead?.let { (key, made) -> made.await()?.let { prefetched = key to it } }
                            }
                            completed = true
                        } finally {
                            if (!completed) {
                                try { if (call.retainModel.value) output.interrupt() else output.stop() }
                                finally { if (!call.retainModel.value) releaseResources() }
                            }
                        }
                    }
                }
            }
        } finally { active.compareAndSet(call, null) }
    }

    /** Skip/seek cancels the current utterance while keeping the verified model. */
    override fun interrupt() {
        nextHint.value = null
        active.exchange(null)?.let { call ->
            call.retainModel.value = true
            call.job.cancel(CancellationException("読み上げ位置を変更しました"))
        }
        player.value?.interrupt()
    }

    override fun stop() {
        nextHint.value = null
        val stopped = generation.updateAndGet { it + 1 }
        active.exchange(null)?.job?.cancel(CancellationException("読み上げを停止しました"))
        player.value?.stop()
        cleanup.launch {
            execution.withLock {
                if (resources?.generation?.let { it < stopped } == true) withContext(NonCancellable) { releaseResources() }
            }
        }
    }

    // Native destruction and releasing the pack are ordered after any synchronous
    // query/synthesis. Deletion cannot remove files which that call is still using.
    private suspend fun releaseResources() = withContext(NonCancellable) {
        nativeExecution.withLock { releaseResourcesLocked() }
    }

    private fun releaseResourcesLocked() {
        val old = resources ?: return
        resources = null
        try {
            if (--old.native.users == 0) {
                nativeModels.remove(old.native.key)
                old.native.session.close()
            }
        } finally { old.pack.close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        stop()
        player.exchange(null)?.close()
        cleanup.launch {
            try { execution.withLock { withContext(NonCancellable) { releaseResources() } } }
            finally { cleanup.cancel() }
        }
        runtime.remove(this)
    }
}

/** The first sentence of [text] that is spoken; what is made ahead for the next post. */
internal fun voicevoxFirstSpokenChunk(text: String): String? =
    voicevoxTextChunks(text).firstOrNull(::voicevoxHasSpokenText)

internal fun voicevoxTextChunks(text: String, maximum: Int = 128): List<String> {
    require(maximum > 1)
    // C strings cannot contain NUL; preserve the remaining visible text.
    val value = text.replace('\u0000', ' ').encodeToByteArray().decodeToString()
    val result = ArrayList<String>()
    var start = 0
    while (start < value.length) {
        var end = start
        var boundary = -1
        var count = 0
        while (end < value.length && count < maximum) {
            val ch = value[end++]
            if (ch.isHighSurrogate() && end < value.length && value[end].isLowSurrogate()) end++
            count++
            if (ch in "。！？!?\n") { boundary = end; break }
            if (ch in "、，,　 ") boundary = end
        }
        if (end < value.length && boundary > start) end = boundary
        result += value.substring(start, end)
        start = end
    }
    return result
}

/** Open JTalk rejects emoji/symbol-only sentences instead of returning silence.
 * Keep their original positions in chunking, but do not submit those sentences.
 * Supplementary CJK ideographs occupy surrogate pairs and must remain readable. */
internal fun voicevoxHasSpokenText(text: String): Boolean {
    if (text.any { it.isLetterOrDigit() }) return true
    for (index in 0 until text.lastIndex) {
        val high = text[index]
        val low = text[index + 1]
        if (high.isHighSurrogate() && low.isLowSurrogate()) {
            val scalar = 0x10000 + ((high.code - 0xd800) shl 10) + low.code - 0xdc00
            if (scalar in 0x20000..0x323af) return true
        }
    }
    return false
}

/** The query settings put over the analysed sentence; the pauses around a sentence are shorter than VOICEVOX's 0.1 s. */
internal fun voicevoxQueryOverrides(configuration: SpeechSettings): Map<String, JsonPrimitive> = mapOf(
    "speedScale" to JsonPrimitive(configuration.rate.coerceIn(0.5f, 2f)),
    "pitchScale" to JsonPrimitive(configuration.voicevoxPitch.coerceIn(-0.15f, 0.15f)),
    "prePhonemeLength" to JsonPrimitive(VOICEVOX_EDGE_PAUSE_SECONDS),
    "postPhonemeLength" to JsonPrimitive(VOICEVOX_EDGE_PAUSE_SECONDS),
    "outputSamplingRate" to JsonPrimitive(24000),
    "outputStereo" to JsonPrimitive(false)
)

internal const val VOICEVOX_EDGE_PAUSE_SECONDS = 0.04f
