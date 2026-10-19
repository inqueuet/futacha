package com.valoser.futacha.shared.audio.voicevox

import android.content.Context
import android.media.AudioFormat
import android.media.AudioTrack
import com.valoser.futacha.shared.audio.AndroidSpeechPlaybackService
import com.valoser.futacha.shared.audio.exchange
import com.valoser.futacha.shared.audio.speechAudioAttributes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AndroidVoicePcmPlayer(private val context: Context) : VoicePcmPlayer {
    private val track = MutableStateFlow<AudioTrack?>(null)
    private val lease = MutableStateFlow<AutoCloseable?>(null)
    private val prepareLock = Mutex()
    @Volatile private var closed = false
    private val paused = MutableStateFlow(false)
    override suspend fun prepare(onStop: () -> Unit) = prepareLock.withLock {
        check(!closed)
        if (lease.value == null) {
            paused.value = false
            val caller = currentCoroutineContext()[Job]
            val held = AndroidSpeechPlaybackService.acquire(context,
                onFocusPause = { paused.value = true; runCatching { track.value?.pause() } },
                onFocusResume = { paused.value = false; runCatching { track.value?.play() } },
                onStop = { caller?.cancel(); onStop() })
            if (closed || !lease.compareAndSet(null, held)) held.close()
            try { currentCoroutineContext().ensureActive() }
            catch (cancelled: CancellationException) { stop(); throw cancelled }
        }
    }
    override suspend fun play(wav: ByteArray) = withContext(Dispatchers.IO) {
        val pcm = voicePcm(wav)
        val output = AudioTrack.Builder().setAudioAttributes(speechAudioAttributes())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(pcm.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(16384, AudioTrack.getMinBufferSize(pcm.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
            .build()
        try {
            ensureActive()
            check(!closed && track.compareAndSet(null, output))
            if (!paused.value) output.play()
            var written = 0
            while (written < pcm.bytes) {
                ensureActive()
                val count = output.write(wav, pcm.offset + written, minOf(8192, pcm.bytes - written), AudioTrack.WRITE_NON_BLOCKING)
                check(count >= 0) { "音声を再生できませんでした" }
                written += count
                if (count == 0) delay(10)
            }
            while ((output.playbackHeadPosition.toLong() and 0xffffffffL) < pcm.bytes / 2) { ensureActive(); delay(10) }
        } finally {
            track.compareAndSet(output, null)
            runCatching { output.stop() }
            output.release()
        }
    }
    override fun stop() {
        runCatching { track.value?.pause() }
        runCatching { track.value?.flush() }
        lease.exchange(null)?.close()
    }
    override fun close() { closed = true; stop() }
}
