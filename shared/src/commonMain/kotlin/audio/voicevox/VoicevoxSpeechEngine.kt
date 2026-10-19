package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

// Includes old roots whose cancelled synchronous native call has not returned yet.
private val nativeExecution = Mutex()

internal class VoicevoxSpeechEngine(private val runtime: SpeechRuntime) : SpeechBackend {
    override val supportsPitch = true
    private var settings = SpeechSettings()
    private val active = MutableStateFlow<Job?>(null)
    private var player: VoicePcmPlayer? = null
    private var closed = false
    override fun configure(settings: SpeechSettings) { this.settings = settings }
    override suspend fun availableVoices(): List<SpeechVoice> = emptyList()
    override suspend fun prepare() {
        check(!closed && runtime.platform.available) { runtime.platform.unavailableReason }
        if (player == null) player = runtime.platform.createPlayer()
    }
    override suspend fun speak(text: String) = coroutineScope {
        check(!closed)
        val job = currentCoroutineContext()[Job]!!
        active.exchange(job)?.takeIf { it !== job }?.cancel()
        val configuration = settings
        try {
            runtime.assets.withVerifiedPack { pack ->
                nativeExecution.withLock {
                    withContext(voiceNativeDispatcher) {
                        // Local lifetime/finally also runs when a caller cancels
                        // during a synchronous C call. No native handle is returned
                        // across a cancellable dispatcher boundary.
                        val output = requireNotNull(player)
                        output.prepare(::stop)
                        var session: VoiceNativeSession? = null
                        try {
                            session = runtime.platform.createNative(pack)
                            for (chunk in voicevoxTextChunks(text)) {
                                ensureActive()
                                if (!voicevoxHasSpokenText(chunk)) continue
                                val original = Json.parseToJsonElement(session.query(chunk)).jsonObject
                                val query = JsonObject(original + mapOf(
                                    "speedScale" to JsonPrimitive(configuration.rate.coerceIn(0.5f, 2f)),
                                    "pitchScale" to JsonPrimitive(configuration.voicevoxPitch.coerceIn(-0.15f, 0.15f)),
                                    "outputSamplingRate" to JsonPrimitive(24000),
                                    "outputStereo" to JsonPrimitive(false)
                                )).toString()
                                ensureActive()
                                val wav = session.synthesize(query)
                                ensureActive() // A stopped call never emits its late result.
                                require(wav.size in 44..16 * 1024 * 1024) { "音声データの長さが不正です" }
                                output.play(wav)
                            }
                        } finally { session?.close(); output.stop() }
                    }
                }
            }
        } finally { active.compareAndSet(job, null) }
    }
    override fun stop() {
        active.exchange(null)?.cancel(CancellationException("読み上げを停止しました"))
        player?.stop()
    }
    override fun close() {
        closed = true
        stop()
        player?.close(); player = null
        runtime.remove(this)
    }
}

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
