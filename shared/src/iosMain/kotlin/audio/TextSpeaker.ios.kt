@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.util.Logger
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSLock
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val IOS_SPEAK_TIMEOUT_MS = 30_000L
private const val IOS_JAPANESE_VOICE = "ja-JP"
private val IOS_SPEECH_BOUNDARY_IMMEDIATE = AVSpeechBoundary.AVSpeechBoundaryImmediate

actual class TextSpeaker actual constructor(platformContext: Any?) {
    private val stateLock = NSLock()
    private val synthesizer = AVSpeechSynthesizer()
    private var activeContinuation: CancellableContinuation<Unit>? = null
    private var activeUtterance: AVSpeechUtterance? = null
    private var closed = false

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance
        ) {
            completeUtterance(didFinishSpeechUtterance, null)
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance
        ) {
            completeUtterance(
                didCancelSpeechUtterance,
                CancellationException("読み上げが停止されました")
            )
        }
    }

    init {
        synthesizer.delegate = delegate
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")

        withTimeout(IOS_SPEAK_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val utterance = AVSpeechUtterance(string = text).apply {
                    voice = AVSpeechSynthesisVoice.voiceWithLanguage(IOS_JAPANESE_VOICE)
                }

                val previousContinuation = stateLock.withLock {
                    val previous = activeContinuation
                    activeContinuation = continuation
                    activeUtterance = utterance
                    previous
                }

                previousContinuation?.cancel(
                    CancellationException("新しい読み上げに置き換えられました")
                )

                continuation.invokeOnCancellation {
                    val shouldStop = stateLock.withLock {
                        if (activeContinuation === continuation) {
                            activeContinuation = null
                            activeUtterance = null
                            true
                        } else {
                            false
                        }
                    }
                    if (shouldStop) {
                        synthesizer.stopSpeakingAtBoundary(IOS_SPEECH_BOUNDARY_IMMEDIATE)
                    }
                }

                runCatching {
                    synthesizer.stopSpeakingAtBoundary(IOS_SPEECH_BOUNDARY_IMMEDIATE)
                    synthesizer.speakUtterance(utterance)
                }.onFailure { error ->
                    clearActiveIfMatches(utterance)
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    actual fun stop() {
        synthesizer.stopSpeakingAtBoundary(IOS_SPEECH_BOUNDARY_IMMEDIATE)
        cancelActive(CancellationException("ユーザーにより読み上げが停止されました"))
    }

    actual fun close() {
        closed = true
        synthesizer.stopSpeakingAtBoundary(IOS_SPEECH_BOUNDARY_IMMEDIATE)
        synthesizer.delegate = null
        cancelActive(CancellationException("TextSpeaker を閉じました"))
    }

    private fun completeUtterance(
        utterance: AVSpeechUtterance,
        error: Throwable?
    ) {
        val continuation = stateLock.withLock {
            if (activeUtterance !== utterance) {
                null
            } else {
                activeUtterance = null
                activeContinuation.also { activeContinuation = null }
            }
        } ?: return

        if (error == null) {
            continuation.resume(Unit)
        } else {
            continuation.cancel(error as? CancellationException ?: CancellationException(error.message))
        }
    }

    private fun clearActiveIfMatches(utterance: AVSpeechUtterance) {
        stateLock.withLock {
            if (activeUtterance === utterance) {
                activeUtterance = null
                activeContinuation = null
            }
        }
    }

    private fun cancelActive(reason: CancellationException) {
        val continuation = stateLock.withLock {
            activeUtterance = null
            activeContinuation.also { activeContinuation = null }
        }
        continuation?.cancel(reason)
    }
}

private inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

actual fun createTextSpeaker(platformContext: Any?): TextSpeaker = TextSpeaker(platformContext)
