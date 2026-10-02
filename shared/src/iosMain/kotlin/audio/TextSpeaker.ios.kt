@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.util.Logger
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import com.valoser.futacha.shared.util.AppDispatchers
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSLock
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_time
import platform.darwin.dispatch_get_global_queue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val IOS_JAPANESE_VOICE = "ja-JP"
private val IOS_SPEECH_BOUNDARY_IMMEDIATE = AVSpeechBoundary.AVSpeechBoundaryImmediate
/** How long stop() keeps the session for a following speak (next/previous, N4-7). */
private const val IOS_SPEECH_RELEASE_GRACE_NANOS = 1_000_000_000L

actual class TextSpeaker actual constructor(platformContext: Any?) {
    private val stateLock = NSLock()
    private val synthesizer = AVSpeechSynthesizer()
    private var activeContinuation: CancellableContinuation<Unit>? = null
    private var activeUtterance: AVSpeechUtterance? = null
    private var closed = false
    // Held from the first speak until stop/close, so other apps' audio resumes
    // once reading ends instead of staying interrupted for the process (G-4).
    private var audioLease: IosPlaybackAudioLease? = null
    // Returned by stop() only after a short grace: next/previous stop and speak
    // again at once, and returning it in between let other apps' audio resume
    // for a moment and stop again (N4-7). A prepare in the grace reclaims it.
    private var pendingRelease: IosPlaybackAudioLease? = null
    private var releaseGeneration = 0L

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

    actual suspend fun prepare() {
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")
        val held = stateLock.withLock {
            audioLease ?: pendingRelease?.also {
                audioLease = it
                pendingRelease = null
                releaseGeneration++
            }
        }
        if (held == null) {
            val lease = withContext(AppDispatchers.io) { acquireIosSpeechAudioSession() }
            val keep = stateLock.withLock {
                if (closed || audioLease != null) {
                    false
                } else {
                    audioLease = lease
                    true
                }
            }
            if (!keep) releaseAudioLease(lease)
            if (closed) throw CancellationException("TextSpeaker は既に閉じられています")
        }
        AVSpeechSynthesisVoice.voiceWithLanguage(IOS_JAPANESE_VOICE)
            ?: throw IllegalStateException(JAPANESE_TTS_UNAVAILABLE_MESSAGE)
    }

    actual suspend fun speak(text: String) {
        if (text.isBlank()) return
        if (closed) throw CancellationException("TextSpeaker は既に閉じられています")

        withTimeout(calculateTextSpeakerTimeoutMillis(text)) {
            prepare()
            val japaneseVoice = checkNotNull(AVSpeechSynthesisVoice.voiceWithLanguage(IOS_JAPANESE_VOICE))
            suspendCancellableCoroutine<Unit> { continuation ->
                val utterance = AVSpeechUtterance(string = text).apply {
                    voice = japaneseVoice
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
        val generation = stateLock.withLock {
            val lease = audioLease ?: return
            audioLease = null
            // Only a prepare racing an earlier stop leaves an older pending lease: return it now.
            pendingRelease?.let(::releaseAudioLease)
            pendingRelease = lease
            ++releaseGeneration
        }
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, IOS_SPEECH_RELEASE_GRACE_NANOS),
            dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)
        ) {
            stateLock.withLock {
                if (releaseGeneration == generation) pendingRelease.also { pendingRelease = null } else null
            }?.close()
        }
    }

    actual fun close() {
        closed = true
        synthesizer.stopSpeakingAtBoundary(IOS_SPEECH_BOUNDARY_IMMEDIATE)
        synthesizer.delegate = null
        cancelActive(CancellationException("TextSpeaker を閉じました"))
        releaseHeldAudioLease()
    }

    private fun releaseHeldAudioLease() {
        val leases = stateLock.withLock {
            listOfNotNull(audioLease, pendingRelease).also {
                audioLease = null
                pendingRelease = null
                releaseGeneration++
            }
        }
        leases.forEach(::releaseAudioLease)
    }

    // Deactivating the session can block, so it never runs on the caller's (main) thread.
    private fun releaseAudioLease(lease: IosPlaybackAudioLease) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            lease.close()
        }
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
