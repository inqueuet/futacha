package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

/** iOS equivalent of AndroidExperienceProfileStore with a durable switch journal. */
internal class IosExperienceProfileStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults()
) {
    private val mutex = Mutex()
    private val activeState = MutableStateFlow(readActiveProfile())
    private val generationState = MutableStateFlow(readGeneration())

    val activeProfile: StateFlow<ExperienceProfile> = activeState.asStateFlow()
    val generation: StateFlow<Long> = generationState.asStateFlow()

    fun readActiveProfile(): ExperienceProfile =
        ExperienceProfile.fromPersistedValue(defaults.stringForKey(KEY_ACTIVE_PROFILE))

    fun readGeneration(): Long = defaults.objectForKey(KEY_GENERATION)?.toString()?.toLongOrNull() ?: 0L

    fun readPreferredFutachaIcon(): AppIconVariant = runCatching {
        AppIconVariant.valueOf(defaults.stringForKey(KEY_PREFERRED_FUTACHA_ICON).orEmpty())
    }.getOrDefault(AppIconVariant.Current)

    suspend fun savePreferredFutachaIcon(variant: AppIconVariant) = mutex.withLock {
        defaults.setObject(variant.name, KEY_PREFERRED_FUTACHA_ICON)
    }

    suspend fun beginSwitchWithCommitBarrier(
        from: ExperienceProfile,
        to: ExperienceProfile
    ): ModeSwitchJournal = mutex.withLock {
        val journal = ModeSwitchJournal(
            from = from,
            to = to,
            phase = ModeSwitchPhase.SESSION_FLUSHED,
            generation = nextExperienceProfileGeneration(readGeneration())
        )
        persistJournal(journal)
        journal
    }

    suspend fun advanceSwitch(
        journal: ModeSwitchJournal,
        phase: ModeSwitchPhase
    ): ModeSwitchJournal = mutex.withLock {
        val advanced = journal.copy(phase = phase)
        persistJournal(advanced)
        advanced
    }

    suspend fun persistRequestedProfileWithCommitBarrier(
        journal: ModeSwitchJournal
    ): ModeSwitchJournal = mutex.withLock {
        val advanced = journal.copy(phase = ModeSwitchPhase.PROFILE_PERSISTED)
        defaults.setObject(journal.to.persistedValue, KEY_ACTIVE_PROFILE)
        defaults.setObject(journal.generation, KEY_GENERATION)
        persistJournal(advanced)
        activeState.value = journal.to
        generationState.value = journal.generation
        advanced
    }

    suspend fun runIfGenerationCurrent(
        profile: ExperienceProfile,
        generation: Long,
        block: suspend () -> Unit
    ): Boolean = mutex.withLock {
        if (!isGenerationCommitAllowedLocked(profile, generation)) return@withLock false
        block()
        true
    }

    fun isGenerationCommitAllowed(profile: ExperienceProfile, generation: Long): Boolean =
        readJournal() == null && readActiveProfile() == profile && readGeneration() == generation

    suspend fun completeSwitch(journal: ModeSwitchJournal) = mutex.withLock {
        defaults.removeObjectForKey(KEY_JOURNAL_FROM)
        defaults.removeObjectForKey(KEY_JOURNAL_TO)
        defaults.removeObjectForKey(KEY_JOURNAL_PHASE)
        defaults.removeObjectForKey(KEY_JOURNAL_GENERATION)
        activeState.value = journal.to
        generationState.value = journal.generation
    }

    fun readJournal(): ModeSwitchJournal? {
        val from = defaults.stringForKey(KEY_JOURNAL_FROM) ?: return null
        val to = defaults.stringForKey(KEY_JOURNAL_TO) ?: return null
        val phase = defaults.stringForKey(KEY_JOURNAL_PHASE) ?: return null
        val generation = defaults.objectForKey(KEY_JOURNAL_GENERATION)?.toString()?.toLongOrNull() ?: return null
        return runCatching {
            ModeSwitchJournal(
                from = ExperienceProfile.fromPersistedValue(from),
                to = ExperienceProfile.fromPersistedValue(to),
                phase = ModeSwitchPhase.valueOf(phase),
                generation = generation
            )
        }.getOrNull()
    }

    private fun isGenerationCommitAllowedLocked(profile: ExperienceProfile, generation: Long): Boolean =
        readJournal() == null && readActiveProfile() == profile && readGeneration() == generation

    private fun persistJournal(journal: ModeSwitchJournal) {
        defaults.setObject(journal.from.persistedValue, KEY_JOURNAL_FROM)
        defaults.setObject(journal.to.persistedValue, KEY_JOURNAL_TO)
        defaults.setObject(journal.phase.name, KEY_JOURNAL_PHASE)
        defaults.setObject(journal.generation, KEY_JOURNAL_GENERATION)
    }

    private companion object {
        const val KEY_ACTIVE_PROFILE = "experience.active_profile"
        const val KEY_GENERATION = "experience.profile_generation"
        const val KEY_PREFERRED_FUTACHA_ICON = "experience.preferred_futacha_icon"
        const val KEY_JOURNAL_FROM = "experience.switch_from"
        const val KEY_JOURNAL_TO = "experience.switch_to"
        const val KEY_JOURNAL_PHASE = "experience.switch_phase"
        const val KEY_JOURNAL_GENERATION = "experience.switch_generation"
    }
}
