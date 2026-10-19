@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.valoser.futacha.shared.audio.voicevox

import com.valoser.futacha.shared.audio.IosPlaybackAudioLease
import com.valoser.futacha.shared.audio.acquireIosSpeechAudioSession
import com.valoser.futacha.shared.audio.exchange
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class IosVoicePcmPlayer : VoicePcmPlayer {
    private val lease = MutableStateFlow<IosPlaybackAudioLease?>(null)
    private var player: AVAudioPlayer? = null
    private var continuation: CancellableContinuation<Unit>? = null
    private var onStop: (() -> Unit)? = null
    private var interruption: NSObjectProtocol? = null
    private var route: NSObjectProtocol? = null
    private var interrupted = false
    private var closed = false
    private val generation = MutableStateFlow(0L)
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            if (this@IosVoicePcmPlayer.player != player) return
            val waiting = continuation; continuation = null
            if (successfully) waiting?.resume(Unit)
            else waiting?.resumeWithException(IllegalStateException("音声の再生に失敗しました"))
        }
        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
            if (this@IosVoicePcmPlayer.player != player) return
            val waiting = continuation; continuation = null
            waiting?.resumeWithException(IllegalStateException("音声の再生に失敗しました"))
        }
    }
    override suspend fun prepare(onStop: () -> Unit) {
        check(!closed)
        this.onStop = onStop
        try {
            withContext(AppDispatchers.io) {
                if (lease.value == null) {
                    val held = acquireIosSpeechAudioSession()
                    if (closed || !lease.compareAndSet(null, held)) held.close()
                    ensureActive()
                }
            }
            withContext(Dispatchers.Main) {
                if (interruption == null) interruption = NSNotificationCenter.defaultCenter.addObserverForName(
                    AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { note ->
                    val type = (note?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.intValue
                    if (type == 1) { interrupted = true; player?.pause() }
                    else if (interrupted) {
                        interrupted = false
                        val options = (note?.userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)?.intValue ?: 0
                        if (options and 1 != 0 && !closed) player?.play() else this@IosVoicePcmPlayer.onStop?.invoke()
                    }
                }
                if (route == null) route = NSNotificationCenter.defaultCenter.addObserverForName(
                    AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { note ->
                    if ((note?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.intValue == 2) this@IosVoicePcmPlayer.onStop?.invoke()
                }
            }
        } catch (failure: Throwable) { stop(); throw failure }
    }
    override suspend fun play(wav: ByteArray) = withContext(Dispatchers.Main) {
        generation.updateAndGet { it + 1 }
        voicePcm(wav)
        while (interrupted) { ensureActive(); delay(25) }
        check(!closed)
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        val output = AVAudioPlayer(data = data, error = null)
        output.delegate = delegate
        player = output
        try {
            suspendCancellableCoroutine<Unit> { waiting ->
                continuation = waiting
                waiting.invokeOnCancellation { dispatch_async(dispatch_get_main_queue()) {
                    output.stop()
                    if (continuation === waiting) continuation = null
                } }
                if (!output.prepareToPlay() || !output.play()) {
                    continuation = null
                    waiting.resumeWithException(IllegalStateException("音声を再生できませんでした"))
                }
            }
        } finally {
            output.stop(); output.delegate = null
            if (player == output) { player = null; continuation = null }
        }
    }
    override fun stop() {
        val stoppedGeneration = generation.updateAndGet { it + 1 }
        lease.exchange(null)?.close()
        dispatch_async(dispatch_get_main_queue()) {
            if (generation.value != stoppedGeneration) return@dispatch_async
            player?.stop()
            continuation?.cancel(CancellationException("読み上げを停止しました"))
            continuation = null
        }
    }
    override fun close() {
        closed = true; stop()
        dispatch_async(dispatch_get_main_queue()) {
            interruption?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            route?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            interruption = null; route = null; onStop = null
        }
    }
}
