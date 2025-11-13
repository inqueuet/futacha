@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import platform.AVFoundation.AVSpeechBoundaryImmediate
import platform.AVFoundation.AVSpeechSynthesizer
import platform.AVFoundation.AVSpeechSynthesisVoice
import platform.AVFoundation.AVSpeechUtterance
import platform.Foundation.NSObject

actual class TextSpeaker actual constructor(platformContext: Any?) {
    private val synthesizer = AVSpeechSynthesizer()
    private val lock = Any()
    private val continuations = mutableMapOf<AVSpeechUtterance, CancellableContinuation<Unit>>()
    private val delegate = object : NSObject(), platform.AVFoundation.AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didFinishSpeechUtterance: AVSpeechUtterance) {
            handleCompletion(didFinishSpeechUtterance, null)
        }

        override fun speechSynthesizer(synthesizer: AVSpeechSynthesizer, didCancelSpeechUtterance: AVSpeechUtterance) {
            handleCompletion(didCancelSpeechUtterance, CancellationException("読み上げがキャンセルされました"))
        }
    }

    init {
        synthesizer.delegate = delegate
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        suspendCancellableCoroutine { continuation ->
            val utterance = AVSpeechUtterance(text)
            AVSpeechSynthesisVoice.voiceWithLanguage("ja-JP")?.let { utterance.voice = it }
            synchronized(lock) {
                continuations[utterance] = continuation
            }
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    continuations.remove(utterance)
                }
                synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
            }
            synthesizer.speakUtterance(utterance)
        }
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundaryImmediate)
        cancelPending(CancellationException("読み上げが停止されました"))
    }

    actual fun close() {
        stop()
    }

    private fun handleCompletion(utterance: AVSpeechUtterance, error: Throwable?) {
        val continuation = synchronized(lock) {
            continuations.remove(utterance)
        }
        continuation?.let {
            if (error == null) {
                it.resume(Unit)
            } else {
                it.resumeWithException(error)
            }
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
