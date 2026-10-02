@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.audio

import com.valoser.futacha.shared.util.Logger
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSLock

/** The AVAudioSession calls the coordinator makes; replaced in tests. */
internal interface IosAudioSessionDriver {
    fun configurePlayback(mixWithOthers: Boolean): Boolean
    fun configureRecording(): Boolean
    fun activate(): Boolean
    fun deactivateNotifyingOthers(): Boolean
}

/** One playback user of the shared audio session; [close] is idempotent. */
internal class IosPlaybackAudioLease internal constructor(
    private val coordinator: IosAudioSessionCoordinator,
    private val id: Long
) : AutoCloseable {
    /** Muted playback mixes with other apps' audio instead of interrupting it. */
    fun updateMixWithOthers(mixWithOthers: Boolean) = coordinator.update(id, mixWithOthers)

    override fun close() = coordinator.release(id)
}

/**
 * Owns the app's AVAudioSession configuration (G-4).
 *
 * Playback is audible under the silent switch (Playback category). Video
 * players hold a lease for as long as they are shown: while every holder is
 * muted the session mixes with other apps, and when the last one closes the
 * session is deactivated with notifyOthersOnDeactivation so the other app's
 * music resumes. Voice input switches to PlayAndRecord and, when it ends, the
 * playback configuration is restored, so a later preview is not routed to the
 * receiver. Activation can fail during a phone call; that is logged and does
 * not stop video playback.
 */
internal class IosAudioSessionCoordinator(private val driver: IosAudioSessionDriver) {
    private val lock = NSLock()
    private val holders = LinkedHashMap<Long, Boolean>()
    private var nextId = 0L
    private var recording = false
    private var recordingSession = 0L
    private var active = false

    /** Never throws: playback goes on even if the session cannot be activated. */
    fun acquire(mixWithOthers: Boolean): IosPlaybackAudioLease = withLock {
        val id = nextId++
        holders[id] = mixWithOthers
        applyLocked()
        IosPlaybackAudioLease(this, id)
    }

    fun update(id: Long, mixWithOthers: Boolean) = withLock {
        if (holders[id] == null || holders[id] == mixWithOthers) return@withLock
        holders[id] = mixWithOthers
        applyLocked()
    }

    fun release(id: Long) = withLock {
        if (holders.remove(id) != null) applyLocked()
    }

    /**
     * For read-aloud: an audible (non-mixing) lease, or null when the session
     * could not be configured and activated. Released when reading stops, so
     * other apps' audio resumes afterwards.
     */
    fun acquireSpeech(): IosPlaybackAudioLease? = withLock {
        val id = nextId++
        holders[id] = false
        if (applyLocked()) {
            IosPlaybackAudioLease(this, id)
        } else {
            holders.remove(id)
            applyLocked()
            null
        }
    }

    /** Switches to voice input; false when the microphone session cannot be started. */
    fun beginRecording(): Boolean = beginRecordingSession() != null

    /** [beginRecording] returning the session to pass to [endRecording], or null when it failed. */
    fun beginRecordingSession(): Long? = withLock {
        recording = true
        val started = driver.configureRecording() && driver.activate()
        active = active || started
        if (!started) {
            recording = false
            applyLocked(afterRecording = true)
            null
        } else {
            ++recordingSession
        }
    }

    /** Restores playback (or deactivates, letting other apps resume) after voice input. */
    fun endRecording() = withLock { endRecordingLocked() }

    /**
     * Ends voice input only if [session] is still the current one: the release runs off the main
     * thread (E4-3), so it may arrive after a newer voice input has already begun.
     */
    fun endRecording(session: Long) = withLock { if (session == recordingSession) endRecordingLocked() }

    private fun endRecordingLocked() {
        if (!recording) return
        recording = false
        applyLocked(afterRecording = true)
    }

    private fun applyLocked(afterRecording: Boolean = false): Boolean {
        if (recording) return true
        if (holders.isEmpty()) {
            if (!active && !afterRecording) return true
            // Restore the playback category for the next user before letting others resume.
            if (!driver.configurePlayback(mixWithOthers = false)) {
                Logger.w(TAG, "Could not restore the playback audio category")
            }
            if (!driver.deactivateNotifyingOthers()) {
                Logger.w(TAG, "Could not deactivate the audio session")
            }
            active = false
            return true
        }
        val mixWithOthers = holders.values.all { it }
        val configured = driver.configurePlayback(mixWithOthers)
        val activated = configured && driver.activate()
        if (!configured) Logger.w(TAG, "Could not set the playback audio category")
        if (configured && !activated) Logger.w(TAG, "Could not activate the playback audio session (another app or a call may hold it)")
        active = active || activated
        return activated
    }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }

    private companion object {
        const val TAG = "IosPlaybackAudioSession"
    }
}

private object AvAudioSessionDriver : IosAudioSessionDriver {
    private val session get() = AVAudioSession.sharedInstance()

    override fun configurePlayback(mixWithOthers: Boolean): Boolean = session.setCategory(
        AVAudioSessionCategoryPlayback,
        mode = AVAudioSessionModeDefault,
        options = if (mixWithOthers) AVAudioSessionCategoryOptionMixWithOthers else 0uL,
        error = null
    )

    override fun configureRecording(): Boolean = session.setCategory(
        AVAudioSessionCategoryPlayAndRecord,
        mode = AVAudioSessionModeMeasurement,
        options = 0uL,
        error = null
    )

    override fun activate(): Boolean = session.setActive(true, error = null)

    override fun deactivateNotifyingOthers(): Boolean = session.setActive(
        false,
        withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
        error = null
    )
}

internal val iosAudioSessionCoordinator = IosAudioSessionCoordinator(AvAudioSessionDriver)

/** Holds the session for one video player; call off the main thread. */
internal fun acquireIosPlaybackAudioSession(mixWithOthers: Boolean): IosPlaybackAudioLease =
    iosAudioSessionCoordinator.acquire(mixWithOthers)

/**
 * Read-aloud must stay audible under the silent switch and after a recording
 * session; held while reading. Call off the main thread.
 */
internal fun acquireIosSpeechAudioSession(): IosPlaybackAudioLease =
    checkNotNull(iosAudioSessionCoordinator.acquireSpeech()) { "音声の再生を準備できません" }
