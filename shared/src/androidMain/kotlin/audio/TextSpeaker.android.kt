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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
actual class TextSpeaker actual constructor(platformContext: Any?) {
    // FIX: Activity ContextではなくApplicationContextを使用してメモリリークを防止
    private val appContext = (platformContext as? Context)?.applicationContext
        ?: throw IllegalArgumentException("TextSpeaker requires an Android Context")
    private val initState = CompletableDeferred<Unit>()
    private val lock = Any()
    private val engineThread = HandlerThread("FutachaTextToSpeech").apply { start() }
    private val engineHandler = Handler(engineThread.looper)
    private val continuations = mutableMapOf<String, CancellableContinuation<Unit>>()
    private var tts: TextToSpeech? = null
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
    private fun requestInitialization() {
        synchronized(lock) {
            if (initializationRequested || closed) return
            initializationRequested = true
        }
        engineHandler.post {
            if (closed) {
                initState.completeExceptionally(CancellationException("TextSpeaker は既に閉じられています"))
                return@post
            }
            val created = runCatching {
                TextToSpeech(appContext) { status ->
                    // Even if an engine invokes its callback during construction,
                    // posting one turn guarantees the created instance has been
                    // installed (or closed) before it is inspected.
                    engineHandler.post {
                        val engine = synchronized(lock) { tts }
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
            val keepEngine = synchronized(lock) {
                if (closed) false else {
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
                    handleUtteranceResult(utteranceId, IOException("読み上げ中にエラーが発生しました"))
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
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
    }

    private suspend fun awaitTts(): TextToSpeech {
        requestInitialization()
        try {
            withTimeout(INITIALIZATION_TIMEOUT_MILLIS) { initState.await() }
        } catch (timeout: TimeoutCancellationException) {
            val failure = IOException("TextToSpeech の初期化がタイムアウトしました", timeout)
            initState.completeExceptionally(failure)
            throw failure
        }
        return synchronized(lock) { tts }
            ?: throw IOException("TextToSpeech の初期化に失敗しました")
    }

    actual suspend fun prepare() {
        awaitTts()
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")
        // タイムアウトを追加してコルーチンがハングするのを防ぐ
        // 初期化待ちもタイムアウト内に含める: TTSエンジンが初期化コールバックを
        // 返さない端末で speak() が永久サスペンドするのを防ぐ
        try {
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
                        synchronized(lock) {
                            continuations.remove(utteranceId)
                        }
                    }
                    engineHandler.post {
                        if (closed) {
                            handleUtteranceResult(
                                utteranceId,
                                CancellationException("TextSpeaker は既に閉じられています")
                            )
                            return@post
                        }
                        val params = Bundle().apply {
                            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                        }
                        val speakResult = runCatching {
                            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                        }.getOrElse { error ->
                            handleUtteranceResult(utteranceId, error)
                            return@post
                        }
                        if (speakResult == TextToSpeech.ERROR) {
                            handleUtteranceResult(utteranceId, IOException("読み上げの開始に失敗しました"))
                        }
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw IOException("読み上げがタイムアウトしました", timeout)
        }
    }

    actual fun stop() {
        cancelPending(CancellationException("ユーザーにより読み上げが停止されました"))
        engineHandler.post { synchronized(lock) { tts }?.stop() }
    }

    actual fun close() {
        val engine = synchronized(lock) {
            closed = true
            tts.also { tts = null }
        }
        if (!initState.isCompleted) {
            initState.completeExceptionally(CancellationException("TextSpeaker を閉じました"))
        }
        cancelPending(CancellationException("TextSpeaker を閉じました"))
        engineHandler.post {
            runCatching { engine?.shutdown() }
            engineThread.quitSafely()
        }
    }

    private fun handleUtteranceResult(utteranceId: String?, error: Throwable?) {
        if (utteranceId == null) {
            Logger.w("TextSpeaker", "Received callback with null utteranceId")
            return
        }
        val continuation = synchronized(lock) {
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
            continuations.remove(utteranceId)
        }
        continuation?.cancel(CancellationException("読み上げが停止されました"))
    }

    private fun cancelPending(reason: CancellationException) {
        val pending = synchronized(lock) {
            val copy = continuations.values.toList()
            continuations.clear()
            copy
        }
        pending.forEach { it.cancel(reason) }
    }
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
