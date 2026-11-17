package com.valoser.futacha.shared.ai

import kotlin.time.TimeSource

/**
 * When host-queued platform AI commands first arrived (C-12). A host that keeps
 * its own queue in front of the app (iOS) records each command as it receives
 * it, so a command waiting behind the queue head is aged from its real arrival
 * instead of from when it reached the head. Keyed by identity and bounded;
 * main thread only.
 */
internal object FutachaAiCommandArrivals {
    private const val MAX_ENTRIES = 64

    private val entries = ArrayDeque<Pair<FutachaAiCommand, TimeSource.Monotonic.ValueTimeMark>>()

    fun record(
        command: FutachaAiCommand,
        at: TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()
    ) {
        if (entries.any { it.first === command }) return
        if (entries.size >= MAX_ENTRIES) entries.removeFirst()
        entries.addLast(command to at)
    }

    fun arrivalOf(command: FutachaAiCommand): TimeSource.Monotonic.ValueTimeMark? =
        entries.firstOrNull { it.first === command }?.second

    fun forget(command: FutachaAiCommand) {
        entries.removeAll { it.first === command }
    }

    internal fun clearForTest() {
        entries.clear()
    }
}
