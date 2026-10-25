package com.valoser.futacha.shared.audio.voicevox

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** A paused focus lease can wait; stalled writes/playback while unpaused cannot. */
internal class VoicePcmPlaybackWatchdog(
    private val timeout: Duration = 10.seconds,
    private val clock: TimeSource = TimeSource.Monotonic
) {
    private var lastProgress = clock.markNow()
    private var written = 0
    private var played = 0L
    private var wasPaused = false

    fun observe(writtenBytes: Int, playedFrames: Long, paused: Boolean) {
        if (paused || wasPaused || writtenBytes > written || playedFrames > played) {
            lastProgress = clock.markNow()
        } else {
            check(lastProgress.elapsedNow() < timeout) { "音声の再生が進まないため停止しました" }
        }
        written = maxOf(written, writtenBytes)
        played = maxOf(played, playedFrames)
        wasPaused = paused
    }
}
