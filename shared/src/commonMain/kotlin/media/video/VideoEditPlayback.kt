package com.valoser.futacha.shared.media.video

import com.valoser.futacha.shared.media.video.model.MosaicDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/** One immutable edit preview. Native preparation/player holds keep its source alive on close. */
internal class VideoEditPlayback(
    val info: VideoEditInfo,
    val document: MosaicDocument,
    val startUs: Long,
    private val onPosition: (Long) -> Unit,
    private val onEnded: () -> Unit
) : AutoCloseable {
    private data class Lifetime(val active: Boolean = true, val readers: Int = 0)
    private val lifetime = MutableStateFlow(Lifetime())
    val isActive get() = lifetime.value.active
    var pausePlayer: (() -> Unit)? = null
    fun pause() { if (isActive) pausePlayer?.invoke() }

    fun retain(): AutoCloseable {
        lifetime.update {
            if (!it.active) throw CancellationException("編集プレビューは終了しました")
            it.copy(readers = it.readers + 1)
        }
        val released = MutableStateFlow(false)
        return AutoCloseable {
            if (released.compareAndSet(false, true)) lifetime.update { it.copy(readers = it.readers - 1) }
        }
    }

    fun position(timeUs: Long) {
        if (isActive) onPosition(info.frames.atOrBefore(timeUs.coerceIn(0, info.frames.durationUs)))
    }
    fun ended() { if (isActive) { position(info.frames.timeAt(info.frames.size - 1)); onEnded() } }
    override fun close() { lifetime.update { it.copy(active = false) } }
    suspend fun awaitReleased() { lifetime.first { !it.active && it.readers == 0 } }
}
