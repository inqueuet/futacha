package com.valoser.futacha.shared.background

/**
 * Which BGTask kinds have a request, for the iOS scheduler (H4-5).
 *
 * A submit runs in a later main-queue block, and a block queued by an older
 * configuration generation is dropped. Counting such queued kinds as pending
 * made a configure() in between skip its own submit, so nothing was submitted
 * until the next configure. A queued kind now counts only for the generation
 * that queued it. Main-thread confined like the scheduler.
 */
internal class BackgroundRefreshSubmitBookkeeping {
    /** Kinds with a request submitted to BGTaskScheduler that has not started yet. */
    private val submitted = mutableSetOf<BackgroundRefreshTaskKind>()
    /** Kinds whose submit block is queued, with the generation that queued it. */
    private val queued = mutableMapOf<BackgroundRefreshTaskKind, Long>()

    fun kindsToSubmit(
        permittedKinds: Set<BackgroundRefreshTaskKind>,
        generation: Long
    ): List<BackgroundRefreshTaskKind> {
        val queuedNow = queued.filterValues { it == generation }.keys
        return resolveBackgroundRefreshKindsToSubmit(permittedKinds, submitted + queuedNow)
    }

    fun markQueued(kinds: List<BackgroundRefreshTaskKind>, generation: Long) {
        kinds.forEach { queued[it] = generation }
    }

    /** The block queued by [generation] runs: its kinds are no longer queued by it. */
    fun finishQueued(kinds: List<BackgroundRefreshTaskKind>, generation: Long) {
        kinds.forEach { kind -> if (queued[kind] == generation) queued.remove(kind) }
    }

    fun markSubmitted(kind: BackgroundRefreshTaskKind) {
        submitted += kind
    }

    /** iOS started the task of [kind], which used up its request. */
    fun markStarted(kind: BackgroundRefreshTaskKind) {
        submitted -= kind
    }

    fun clear() {
        submitted.clear()
        queued.clear()
    }
}
