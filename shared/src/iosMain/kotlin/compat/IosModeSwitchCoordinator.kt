package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes iOS profile changes and makes an interrupted change converge on
 * the requested profile at next launch.  iOS has no launcher aliases; the
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
            if (store.readJournal() != null) recoverLocked()
            val current = store.readActiveProfile()
            if (current == target) return@withLock store.readGeneration()
            if (current == ExperienceProfile.FUTACHA) store.savePreferredFutachaIcon(preferredFutachaIcon)
            var journal = store.beginSwitchWithCommitBarrier(current, target)
            quiesceOldProfile()
            journal = store.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
            journal = store.persistRequestedProfileWithCommitBarrier(journal)
            reconcileIcon(target, store.readPreferredFutachaIcon())
            journal = store.advanceSwitch(journal, ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED)
            store.completeSwitch(journal.copy(phase = ModeSwitchPhase.ROOT_REBUILT))
            journal.generation
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
