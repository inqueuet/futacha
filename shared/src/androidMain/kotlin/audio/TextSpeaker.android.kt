@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.valoser.futacha.shared.util.Logger
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
actual class TextSpeaker actual constructor(platformContext: Any?) {
    // FIX: Activity ContextではなくApplicationContextを使用してメモリリークを防止
    private val appContext = (platformContext as? Context)?.applicationContext
        ?: throw IllegalArgumentException("TextSpeaker requires an Android Context")
    private val lock = Any()
    // Replaced with a fresh deferred after a failed or timed-out initialization so a
    // slow engine (common on first boot) does not disable read-aloud until restart.
    private var initState = CompletableDeferred<Unit>()
    private val engineThread = HandlerThread("FutachaTextToSpeech").apply { start() }
    private val engineHandler = Handler(engineThread.looper)
    private val continuations = mutableMapOf<String, CancellableContinuation<Unit>>()
    private var tts: TextToSpeech? = null
    private var playbackLease: AutoCloseable? = null
    private var activeUtteranceId: String? = null
    private var activeUtteranceText: String? = null
    // Audio-focus pause: the playing utterance is stopped in the engine but its
    // continuation stays pending, and is spoken again from the start on resume.
    private var focusPaused = false
    private var replay: Pair<String, String>? = null
    private val stopsToIgnore = mutableMapOf<String, Int>()
    @Volatile
    private var closed = false
    private var initializationRequested = false

    companion object {
        private const val MAX_PENDING_UTTERANCES = 100
        private const val INITIALIZATION_TIMEOUT_MILLIS = 10_000L
    }

    /**
     * TextToSpeech construction performs a synchronous binder round trip on
     * some engines.  Constructing it from a composable therefore caused a
     * visible freeze when a thread was merely opened.  The engine is now
     * created only after the first speak request. A dedicated Looper keeps a
     * slow or broken engine binder from blocking Compose and its watchdog.
     */
    private fun requestInitialization(): CompletableDeferred<Unit> {
        val state: CompletableDeferred<Unit>
        var staleEngine: TextToSpeech? = null
        synchronized(lock) {
            if (closed) return initState
            if (initializationRequested && initState.isCancelled) {
                staleEngine = tts
                tts = null
                initState = CompletableDeferred()
                initializationRequested = false
            }
            if (initializationRequested) return initState
            initializationRequested = true
            state = initState
        }
        staleEngine?.let { engine ->
            engineHandler.post { runCatching { engine.shutdown() } }
        }
        engineHandler.post {
            val initState = state
            if (closed) {
                initState.completeExceptionally(CancellationException("TextSpeaker は既に閉じられています"))
                return@post
            }
            var createdEngine: TextToSpeech? = null
            val created = runCatching {
                TextToSpeech(appContext) { status ->
                    // Even if an engine invokes its callback during construction,
                    // posting one turn guarantees the created instance has been
                    // installed (or closed) before it is inspected.
                    engineHandler.post {
                        val engine = synchronized(lock) { tts?.takeIf { it === createdEngine } }
                        if (status == TextToSpeech.SUCCESS && engine != null) {
                            val result = engine.setLanguage(Locale.JAPAN)
                            if (result in TextToSpeech.LANG_AVAILABLE..TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                                initState.complete(Unit)
                            } else {
                                initState.completeExceptionally(IOException(JAPANESE_TTS_UNAVAILABLE_MESSAGE))
                            }
                        } else if (!initState.isCompleted) {
                            initState.completeExceptionally(IOException("TextToSpeech の初期化に失敗しました"))
                        }
                    }
                }
            }.getOrElse { error ->
                initState.completeExceptionally(error)
                return@post
            }
            createdEngine = created
            val keepEngine = synchronized(lock) {
                if (closed || this.initState !== state || state.isCompleted) false else {
                    tts = created
                    true
                }
            }
            if (!keepEngine) {
                runCatching { created.shutdown() }
                initState.completeExceptionally(CancellationException("TextSpeaker を閉じました"))
                return@post
            }
            created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // No-op
                }

                override fun onDone(utteranceId: String?) {
                    handleUtteranceResult(utteranceId, null)
                }

                @Deprecated(
                    message = "Legacy callback for older APIs",
                    replaceWith = ReplaceWith("onError(utteranceId, TextToSpeech.ERROR)", "android.speech.tts.TextToSpeech")
                )
                override fun onError(utteranceId: String?) {
                    invalidateEngine(created, utteranceId)
                    handleUtteranceResult(utteranceId, IOException("読み上げ中にエラーが発生しました"))
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (errorCode == TextToSpeech.ERROR_SERVICE || errorCode == TextToSpeech.ERROR_OUTPUT ||
                        errorCode == TextToSpeech.ERROR_SYNTHESIS) invalidateEngine(created, utteranceId)
                    handleUtteranceResult(
                        utteranceId,
                        IOException("読み上げ中にエラーが発生しました (code: $errorCode)")
                    )
                }

                // QUEUE_FLUSH で中断された発話は onDone/onError ではなく onStop で
                // 通知されるため、ここで解放しないと continuation が30秒タイムアウト
                // まで残り続ける(iOS 側の didCancelSpeechUtterance に相当)
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    handleUtteranceStopped(utteranceId)
                }
            })
        }
        return state
    }

    private suspend fun awaitTts(): TextToSpeech {
        val state = requestInitialization()
        try {
            withTimeout(INITIALIZATION_TIMEOUT_MILLIS) { state.await() }
        } catch (timeout: TimeoutCancellationException) {
            val failure = IOException("TextToSpeech の初期化がタイムアウトしました", timeout)
            // Fails only this attempt; the next request starts a new initialization.
            state.completeExceptionally(failure)
            throw failure
        }
        return synchronized(lock) { tts.takeIf { initState === state } }
            ?: throw IOException("TextToSpeech の初期化に失敗しました")
    }

    private suspend fun preparePlayback() {
        if (synchronized(lock) { playbackLease != null }) return
        val owner = currentCoroutineContext()[Job]
        val lease = AndroidSpeechPlaybackService.acquire(
            appContext,
            onStop = {
                owner?.cancel(CancellationException("読み上げを停止しました"))
                stop()
            },
            onFocusPause = ::pauseForFocus,
            onFocusResume = ::resumeAfterFocus
        )
        val keep = synchronized(lock) {
            if (closed || playbackLease != null) false else { playbackLease = lease; true }
        }
        if (!keep) lease.close()
    }

    actual suspend fun prepare() {
        preparePlayback()
        try { awaitTts() } catch (failure: Throwable) { releasePlayback(); throw failure }
    }

    private fun releasePlayback() {
        synchronized(lock) { playbackLease.also { playbackLease = null } }?.close()
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")
        // タイムアウトを追加してコルーチンがハングするのを防ぐ
        // 初期化待ちもタイムアウト内に含める: TTSエンジンが初期化コールバックを
        // 返さない端末で speak() が永久サスペンドするのを防ぐ
        try {
            // Capture the session job before entering the per-utterance timeout.
            preparePlayback()
            withTimeout(calculateTextSpeakerTimeoutMillis(text)) {
                val engine = awaitTts()
                suspendCancellableCoroutine<Unit> { continuation ->
                    val utteranceId = UUID.randomUUID().toString()
                    synchronized(lock) {
                        // 最大数チェックでメモリリークを防ぐ
                        if (continuations.size >= MAX_PENDING_UTTERANCES) {
                            Logger.w("TextSpeaker", "Too many pending utterances (${continuations.size}), clearing old ones")
                            // 古いcontinuationsをキャンセル
                            val oldContinuations = continuations.values.toList()
                            continuations.clear()
                            oldContinuations.forEach { it.cancel(CancellationException("Too many pending utterances")) }
                        }
                        continuations[utteranceId] = continuation
                    }
                    continuation.invokeOnCancellation {
                        synchronized(lock) { continuations.remove(utteranceId) }
                        engineHandler.post {
                            val shouldStop = synchronized(lock) {
                                if (activeUtteranceId == utteranceId) {
                                    activeUtteranceId = null
                                    true
                                } else false
                            }
                            if (shouldStop) runCatching { engine.stop() }
                        }
                    }
                    engineHandler.post {
                        if (!continuation.isActive) return@post
                        if (closed) {
                            handleUtteranceResult(
                                utteranceId,
                                CancellationException("TextSpeaker は既に閉じられています")
                            )
                            return@post
                        }
                        var deferredForFocus = false
                        val maySpeak = synchronized(lock) {
                            if (!continuation.isActive) false else {
                                activeUtteranceId = utteranceId
                                activeUtteranceText = text
                                if (focusPaused) {
                                    // Another app holds the audio focus for now: speak on resume.
                                    replay = utteranceId to text
                                    deferredForFocus = true
                                }
                                true
                            }
                        }
                        if (!maySpeak || deferredForFocus) return@post
                        startUtterance(engine, utteranceId, text)
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw IOException("読み上げがタイムアウトしました", timeout)
        }
    }

    private fun startUtterance(engine: TextToSpeech, utteranceId: String, text: String) {
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val speakResult = runCatching {
            engine.setAudioAttributes(speechAudioAttributes())
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }.getOrElse { error ->
            invalidateEngine(engine, utteranceId)
            handleUtteranceResult(utteranceId, error)
            return
        }
        if (speakResult == TextToSpeech.ERROR) {
            invalidateEngine(engine, utteranceId)
            handleUtteranceResult(utteranceId, IOException("読み上げの開始に失敗しました"))
        }
    }

    /** A short audio-focus loss (call, prompt): silence the engine but keep the reading alive. */
    private fun pauseForFocus() {
        synchronized(lock) {
            if (focusPaused || closed) return
            focusPaused = true
            val id = activeUtteranceId
            val text = activeUtteranceText
            if (id != null && text != null && continuations.containsKey(id)) {
                replay = id to text
                // engine.stop() reports onStop for this utterance; that is not a user stop.
                stopsToIgnore[id] = (stopsToIgnore[id] ?: 0) + 1
            }
        }
        engineHandler.post { synchronized(lock) { tts }?.stop() }
    }

    private fun resumeAfterFocus() {
        val pending = synchronized(lock) {
            if (!focusPaused) return
            focusPaused = false
            replay.also { replay = null }
        } ?: return
        val (utteranceId, text) = pending
        engineHandler.post {
            val engine = synchronized(lock) {
                if (closed || !continuations.containsKey(utteranceId)) null else {
                    activeUtteranceId = utteranceId
                    tts
                }
            } ?: return@post
            startUtterance(engine, utteranceId, text)
        }
    }

    actual fun stop() {
        releasePlayback()
        cancelPending(CancellationException("ユーザーにより読み上げが停止されました"))
        engineHandler.post { synchronized(lock) { tts }?.stop() }
    }

    actual fun close() {
        releasePlayback()
        val (engine, state) = synchronized(lock) {
            closed = true
            (tts to initState).also { tts = null }
        }
        if (!state.isCompleted) {
            state.completeExceptionally(CancellationException("TextSpeaker を閉じました"))
        }
        cancelPending(CancellationException("TextSpeaker を閉じました"))
        engineHandler.post {
            runCatching { engine?.shutdown() }
            engineThread.quitSafely()
        }
    }

    private fun invalidateEngine(engine: TextToSpeech, utteranceId: String?) {
        val stale = synchronized(lock) {
            if (tts !== engine || activeUtteranceId != utteranceId) null else {
                tts = null
                initState = CompletableDeferred()
                initializationRequested = false
                engine
            }
        } ?: return
        engineHandler.post { runCatching { stale.shutdown() } }
    }

    private fun handleUtteranceResult(utteranceId: String?, error: Throwable?) {
        if (utteranceId == null) {
            Logger.w("TextSpeaker", "Received callback with null utteranceId")
            return
        }
        val continuation = synchronized(lock) {
            if (activeUtteranceId == utteranceId) activeUtteranceId = null
            if (replay?.first == utteranceId) replay = null
            stopsToIgnore.remove(utteranceId)
            continuations.remove(utteranceId)
        }
        if (continuation == null) {
            Logger.w("TextSpeaker", "Received callback for unknown utteranceId: $utteranceId (possibly cancelled or timed out)")
            return
        }
        if (!continuation.isActive) return
        runCatching {
            if (error == null) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(error)
            }
        }.onFailure {
            // TTS may report completion just after the speak timeout/cancellation.
            // Never let a late engine callback crash the UI process.
            Logger.w("TextSpeaker", "Ignoring a late TTS callback: ${it.message}")
        }
    }

    private fun handleUtteranceStopped(utteranceId: String?) {
        if (utteranceId == null) return
        val continuation = synchronized(lock) {
            val ignored = stopsToIgnore[utteranceId] ?: 0
            if (ignored > 0) {
                // Our own pause for audio focus: the utterance is still wanted.
                if (ignored == 1) stopsToIgnore.remove(utteranceId) else stopsToIgnore[utteranceId] = ignored - 1
                return
            }
            if (activeUtteranceId == utteranceId) activeUtteranceId = null
            continuations.remove(utteranceId)
        }
        continuation?.cancel(CancellationException("読み上げが停止されました"))
    }

    private fun cancelPending(reason: CancellationException) {
        val pending = synchronized(lock) {
            val copy = continuations.values.toList()
            continuations.clear()
            // A stopped speaker must not stay paused for a focus loss that no
            // longer concerns it, nor replay an utterance nobody waits for.
            focusPaused = false
            replay = null
            stopsToIgnore.clear()
            copy
        }
        pending.forEach { it.cancel(reason) }
    }
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
