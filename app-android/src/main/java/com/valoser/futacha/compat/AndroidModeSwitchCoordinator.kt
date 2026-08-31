package com.valoser.futacha.compat

import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ModeSwitchJournal
import com.valoser.futacha.shared.compat.ModeSwitchPhase
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class AndroidModeSwitchCoordinator(
    private val profileStore: AndroidExperienceProfileStore,
    private val aliasReconciler: AndroidLauncherAliasReconciler
) {
    private val mutex = Mutex()

    suspend fun switchTo(
        target: ExperienceProfile,
        preferredFutachaIcon: AppIconVariant,
        quiesceOldProfile: suspend () -> Unit = {}
    ): Result<Long> = try {
        Result.success(mutex.withLock {
            // Application recovery runs asynchronously. A user action can otherwise reach this
            // coordinator first and overwrite the durable journal left by the previous process.
            // Always finish that generation before deciding whether the new request is a no-op.
            if (withContext(Dispatchers.IO) { profileStore.readJournal() } != null) {
                recoverIncompleteSwitchLocked()
            }
            val current = withContext(Dispatchers.IO) { profileStore.readActiveProfile() }
            if (current == target) {
                return@withLock withContext(Dispatchers.IO) { profileStore.readGeneration() }
            }
            if (current == ExperienceProfile.FUTACHA) {
                withContext(Dispatchers.IO) {
                    profileStore.savePreferredFutachaIcon(preferredFutachaIcon)
                }
            }
            var journal = withContext(Dispatchers.IO) {
                profileStore.beginSwitchWithCommitBarrier(current, target)
            }
            quiesceOldProfile()
            journal = withContext(Dispatchers.IO) {
                profileStore.advanceSwitch(journal, ModeSwitchPhase.OLD_PROFILE_QUIESCED)
            }
            journal = withContext(Dispatchers.IO) {
                profileStore.persistRequestedProfileWithCommitBarrier(journal)
            }
            withContext(Dispatchers.IO) {
                aliasReconciler.reconcile(target, profileStore.readPreferredFutachaIcon())
            }
            journal = withContext(Dispatchers.IO) {
                profileStore.advanceSwitch(journal, ModeSwitchPhase.LAUNCHER_ALIAS_UPDATED)
            }
            withContext(Dispatchers.IO) {
                profileStore.completeSwitch(journal.copy(phase = ModeSwitchPhase.ROOT_REBUILT))
            }
            journal.generation
        })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

    suspend fun recoverIfNeeded(): Result<ExperienceProfile> = try {
        Result.success(mutex.withLock {
            recoverIncompleteSwitchLocked()
        })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun recoverIncompleteSwitchLocked(): ExperienceProfile {
        var journal = withContext(Dispatchers.IO) { profileStore.readJournal() }
        if (journal == null) {
            // A completed switch already selected the launcher alias. Do not
            // force it again on every process start: launchers and users can
            // retain a subsequently selected icon without it reverting to the
            // compatibility head icon after force-stop/relaunch.
            return withContext(Dispatchers.IO) { profileStore.readActiveProfile() }
        }
        val target = requireNotNull(journal).to
        if (requireNotNull(journal).phase < ModeSwitchPhase.PROFILE_PERSISTED) {
            journal = withContext(Dispatchers.IO) {
                profileStore.persistRequestedProfileWithCommitBarrier(journal)
            }
        }
        withContext(Dispatchers.IO) {
            aliasReconciler.reconcile(target, profileStore.readPreferredFutachaIcon())
        }
        withContext(Dispatchers.IO) {
            profileStore.completeSwitch(requireNotNull(journal).copy(phase = ModeSwitchPhase.ROOT_REBUILT))
        }
        return target
    }
}
