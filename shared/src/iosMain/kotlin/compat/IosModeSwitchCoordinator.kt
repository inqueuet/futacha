package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Serializes iOS profile changes and makes an interrupted change converge on
 * the requested profile at next launch. A change that fails in-process is
 * rolled back to the original profile instead.  iOS has no launcher aliases; the
 * LAUNCHER_ALIAS_UPDATED phase means that the alternate-icon reconciliation
 * callback has returned.
 */
internal class IosModeSwitchCoordinator(
    private val store: IosExperienceProfileStore,
    private val reconcileIcon: suspend (ExperienceProfile, AppIconVariant) -> Unit
) {
    private val mutex = Mutex()

    suspend fun switchTo(
        target: ExperienceProfile,
        preferredFutachaIcon: AppIconVariant,
        quiesceOldProfile: suspend () -> Unit = {}
    ): Result<Long> = try {
        Result.success(mutex.withLock {
            // A failure here rolled nothing back, so it is reported as such (M4-1).
            if (store.readJournal() != null) recoverInterruptedModeSwitch { recoverLocked() }
            val current = store.readActiveProfile()
            if (current == target) return@withLock store.readGeneration()
            if (current == ExperienceProfile.FUTACHA) store.savePreferredFutachaIcon(preferredFutachaIcon)
            try {
                var journal = store.beginSwitchWithCommitBarrier(current, target)
                quiesceOldProfile()
                journal = store.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
                journal = store.persistRequestedProfileWithCommitBarrier(journal)
                reconcileIcon(target, store.readPreferredFutachaIcon())
                journal = store.advanceSwitch(journal, ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED)
                store.completeSwitch(journal.copy(phase = ModeSwitchPhase.ROOT_REBUILT))
                journal.generation
            } catch (failure: Throwable) {
                // Includes cancellation: an abandoned journal blocks every commit
                // gate (and the BGTask schedule) until the next launch.
                rollBackLocked(current, failure)
                throw failure
            }
        })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    suspend fun recoverIfNeeded(): Result<ExperienceProfile> = try {
        Result.success(mutex.withLock { recoverLocked() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    private suspend fun rollBackLocked(origin: ExperienceProfile, failure: Throwable) {
        try {
            withContext(NonCancellable) {
                rollBackFailedModeSwitch(rollbackStore, origin) {
                    reconcileIcon(origin, store.readPreferredFutachaIcon())
                }
            }
        } catch (rollbackFailure: Throwable) {
            // The journal stays; the next launch recovers it before the UI starts.
            failure.addSuppressed(rollbackFailure)
        }
    }

    private val rollbackStore = object : ModeSwitchRollbackStore {
        override suspend fun readJournal() = store.readJournal()
        override suspend fun readGeneration() = store.readGeneration()
        override suspend fun writeJournal(journal: ModeSwitchJournal) {
            store.advanceSwitch(journal, journal.phase)
        }
        override suspend fun persistProfile(journal: ModeSwitchJournal) {
            store.persistRequestedProfileWithCommitBarrier(journal)
        }
        override suspend fun clearJournal(journal: ModeSwitchJournal) = store.completeSwitch(journal)
    }

    private suspend fun recoverLocked(): ExperienceProfile {
        var journal = store.readJournal() ?: return store.readActiveProfile()
        val target = journal.to
        if (journal.phase < ModeSwitchPhase.PROFILE_PERSISTED) {
            journal = store.persistRequestedProfileWithCommitBarrier(journal)
        }
        reconcileIcon(target, store.readPreferredFutachaIcon())
        store.completeSwitch(journal.copy(phase = ModeSwitchPhase.ROOT_REBUILT))
        return target
    }
}
