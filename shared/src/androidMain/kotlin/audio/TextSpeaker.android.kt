@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import android.content.Context
import android.os.Bundle
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
    private val continuations = mutableMapOf<String, CancellableContinuation<Unit>>()
    private val tts: TextToSpeech
    private var closed = false

    companion object {
        private const val MAX_PENDING_UTTERANCES = 100
        private const val SPEAK_TIMEOUT_MS = 30000L // 30秒タイムアウト
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.JAPAN)
                if (result in TextToSpeech.LANG_AVAILABLE..TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE) {
                    // Language initialized
                }
                initState.complete(Unit)
            } else {
                initState.completeExceptionally(IOException("TextToSpeech の初期化に失敗しました"))
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        })
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")
        initState.await()

        // タイムアウトを追加してコルーチンがハングするのを防ぐ
        withTimeout(SPEAK_TIMEOUT_MS) {
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
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            }
        }
    }

    actual fun stop() {
        tts.stop()
        cancelPending(CancellationException("ユーザーにより読み上げが停止されました"))
    }

    actual fun close() {
        closed = true
        cancelPending(CancellationException("TextSpeaker を閉じました"))
        try {
            tts.shutdown()
        } catch (_: Exception) {
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
        if (error == null) {
            continuation.resume(Unit)
        } else {
            continuation.resumeWithException(error)
        }
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
