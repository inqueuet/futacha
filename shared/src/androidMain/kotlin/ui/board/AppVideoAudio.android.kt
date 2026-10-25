package com.valoser.futacha.shared.ui.board

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.valoser.futacha.shared.audio.AndroidSpeechPlaybackService

/**
 * The audio focus of a video while it is heard (L117). The video takes the focus as Android's players do (other
 * apps' music stops, unplugging the headphones pauses it); this app's own reading aloud is not stopped by that but
 * paused, and goes on from where it was once the video is paused, ends, is muted or goes away. A muted video (or
 * at volume 0) takes no focus and pauses nothing.
 *
 * [player] is what the screen drives (its play button included), so the reading is paused before the video asks
 * for the focus. Main thread only.
 */
@UnstableApi
internal class AppVideoAudio(private val exo: ExoPlayer, audible: Boolean) {
    private var audible = audible
    private var hold: AutoCloseable? = null

    val player: Player = object : ForwardingPlayer(exo) {
        override fun play() {
            beforePlay()
            super.play()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) beforePlay()
            super.setPlayWhenReady(playWhenReady)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) releaseHold()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) releaseHold()
        }
    }

    init {
        exo.setHandleAudioBecomingNoisy(true)
        exo.setAudioAttributes(MOVIE_AUDIO, audible)
        exo.addListener(listener)
    }

    /** The volume or mute changed. */
    fun setAudible(value: Boolean) {
        if (audible == value) return
        audible = value
        if (value && exo.playWhenReady && exo.playbackState != Player.STATE_ENDED) takeHold()
        exo.setAudioAttributes(MOVIE_AUDIO, value)
        if (!value) releaseHold()
    }

    fun release() {
        exo.removeListener(listener)
        releaseHold()
    }

    private fun beforePlay() {
        if (audible) takeHold()
    }

    private fun takeHold() {
        if (hold == null) hold = AndroidSpeechPlaybackService.holdForAppVideo()
    }

    private fun releaseHold() {
        hold?.close()
        hold = null
    }

    private companion object {
        val MOVIE_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    }
}
