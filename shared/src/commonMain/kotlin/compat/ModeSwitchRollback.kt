package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.Logger
import kotlin.coroutines.cancellation.CancellationException

/** The durable journal operations a mode-switch coordinator uses to undo a failed switch. */
interface ModeSwitchRollbackStore {
    suspend fun readJournal(): ModeSwitchJournal?
    suspend fun readGeneration(): Long
    suspend fun writeJournal(journal: ModeSwitchJournal)
    suspend fun persistProfile(journal: ModeSwitchJournal)
    suspend fun clearJournal(journal: ModeSwitchJournal)
}

/**
 * Returns the app to [origin] after a switch failed in this process (M-2).
 *
 * A journal left behind made every commit gate refuse work for the rest of the
 * process (picker results, settings, workers) and was silently completed on the
 * next launch. The rollback is itself journaled (towards [origin]), so a crash
 * while undoing still converges on the profile the user was told they are in.
 * [origin] gets a new generation: work quiesced or captured for the abandoned
 * switch is invalidated, and the schedulers keyed on the generation restart.
 *
 * Returns the new generation, or null when no journal was written.
 */
suspend fun rollBackFailedModeSwitch(
    store: ModeSwitchRollbackStore,
    origin: ExperienceProfile,
    reconcileOrigin: suspend () -> Unit
): Long? {
    val failed = store.readJournal() ?: return null
    val rollback = ModeSwitchJournal(
        from = failed.to,
        to = origin,
        phase = ModeSwitchPhase.OLD_PROFILE_QUIESCED,
        generation = nextExperienceProfileGeneration(maxOf(failed.generation, store.readGeneration()))
    )
    store.writeJournal(rollback)
    store.persistProfile(rollback)
    try {
        reconcileOrigin()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        // The launcher icon is not the profile. Keeping the journal over an icon
        // failure would block every commit again; the next switch reconciles it.
        Logger.w("ModeSwitchRollback", "Icon reconcile failed during rollback: ${failure.message}")
    }
    store.clearJournal(rollback.copy(phase = ModeSwitchPhase.ROOT_REBUILT))
    return rollback.generation
}

/**
 * Body of the dialog shown after a failed mode switch (M-2). The coordinator
 * rolled the switch back unless the rollback failed too, which it records as a
 * suppressed exception.
 */
fun modeSwitchFailureMessage(error: Throwable, rolledBack: Boolean = error.suppressedExceptions.isEmpty()): String {
    val reason = error.message?.takeIf(String::isNotBlank)?.let { "\n\n$it" }.orEmpty()
    return if (rolledBack) {
        "現在のモードのまま使用できます。もう一度お試しください。$reason"
    } else {
        "アプリを再起動してください。$reason"
    }
}
