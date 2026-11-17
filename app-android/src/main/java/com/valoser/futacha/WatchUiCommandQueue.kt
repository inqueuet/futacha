package com.valoser.futacha

import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Hands watch commands to the app UI only while a MainActivity is started
 * (C4-1). The app's command collector stays composed in the background and
 * received a command at once, so a command for a phone whose activity start
 * was blocked opened the thread in the background and was forwarded to the
 * thread screen, which ran it (read-aloud) whenever the user opened the app,
 * hours later. Now a command waits here, in receive order, until an activity
 * has started, for at most [maxAgeMillis] since it was received; it then
 * enters the bridge with the rest of its age as the bridge's limit.
 *
 * With [startedActivities] null every command enters the bridge at once (old
 * behavior, used where the activity count is unknown).
 */
internal class WatchUiCommandQueue(
    private val scope: CoroutineScope,
    private val startedActivities: StateFlow<Int>?,
    private val enqueue: (command: FutachaAiCommand, maxAgeMillis: Long) -> Boolean,
    private val nowElapsedMillis: () -> Long,
    private val maxAgeMillis: Long = WATCH_UI_COMMAND_MAX_AGE_MILLIS,
    private val warn: (String) -> Unit = { message -> Logger.w(TAG, message) }
) {
    private val lock = Any()
    private val pending = ArrayDeque<Pair<FutachaAiCommand, Long>>()
    private var flushJob: Job? = null

    /** False only when the bridge refused a command handed over at once. */
    fun submit(command: FutachaAiCommand): Boolean {
        val started = startedActivities ?: return enqueue(command, maxAgeMillis)
        synchronized(lock) {
            // Behind a waiting command it waits too, so the order is kept.
            if (pending.isEmpty() && started.value > 0) return enqueue(command, maxAgeMillis)
            if (pending.size >= WATCH_UI_COMMAND_QUEUE_MAX) {
                val dropped = pending.removeFirst()
                warn("Dropped waiting watch command because the queue is full: ${dropped.first.action.id}")
            }
            pending.addLast(command to nowElapsedMillis())
            if (flushJob?.isActive != true) {
                flushJob = scope.launch { flushWhenStarted(started) }
            }
        }
        return true
    }

    private suspend fun flushWhenStarted(started: StateFlow<Int>) {
        while (true) {
            val oldestAt = synchronized(lock) {
                pending.firstOrNull()?.second ?: run {
                    flushJob = null
                    return
                }
            }
            val waitMillis = maxAgeMillis - (nowElapsedMillis() - oldestAt)
            val isStarted = waitMillis > 0 &&
                withTimeoutOrNull(waitMillis) { started.first { it > 0 } } != null
            synchronized(lock) {
                val now = nowElapsedMillis()
                while (pending.isNotEmpty() &&
                    !isWithinWatchUiCommandMaxAge(pending.first().second, now, maxAgeMillis)
                ) {
                    val expired = pending.removeFirst()
                    warn("Dropped watch command because no app screen started in time: ${expired.first.action.id}")
                }
                if (isStarted) {
                    while (pending.isNotEmpty()) {
                        val (command, receivedAt) = pending.removeFirst()
                        val remaining = (maxAgeMillis - (now - receivedAt)).coerceAtLeast(0L)
                        if (!enqueue(command, remaining)) {
                            warn("Dropped watch command because AI command queue is full: ${command.action.id}")
                        }
                    }
                }
                if (pending.isEmpty()) {
                    flushJob = null
                    return
                }
            }
        }
    }

    private companion object {
        const val TAG = "WatchUiCommandQueue"
        const val WATCH_UI_COMMAND_QUEUE_MAX = 32
    }
}
