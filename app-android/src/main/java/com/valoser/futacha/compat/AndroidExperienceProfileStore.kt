package com.valoser.futacha.compat

import android.annotation.SuppressLint
import android.content.Context
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ModeSwitchJournal
import com.valoser.futacha.shared.compat.ModeSwitchPhase
import com.valoser.futacha.shared.compat.nextExperienceProfileGeneration
import com.valoser.futacha.shared.model.AppIconVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// These commits are deliberate: the journal is the crash-recovery barrier for a mode switch.
// Production callers enter through AndroidModeSwitchCoordinator, which moves the whole
// persistence phase to Dispatchers.IO before reaching this store.
@SuppressLint("ApplySharedPref")
class AndroidExperienceProfileStore(
    context: Context,
    preferencesName: String = PREFERENCES_NAME
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )
    private val lock = Any()
    private val generationCommitMutex = Mutex()
    private val activeProfileState = MutableStateFlow(readActiveProfile())
    private val generationState = MutableStateFlow(readGeneration())

    val activeProfile: StateFlow<ExperienceProfile> = activeProfileState.asStateFlow()
    val generation: StateFlow<Long> = generationState.asStateFlow()

    fun readActiveProfile(): ExperienceProfile = ExperienceProfile.fromPersistedValue(
        preferences.getString(KEY_ACTIVE_PROFILE, null)
    )

    fun readGeneration(): Long = preferences.getLong(KEY_GENERATION, 0L)

    fun readPreferredFutachaIcon(): AppIconVariant = runCatching {
        AppIconVariant.valueOf(preferences.getString(KEY_PREFERRED_FUTACHA_ICON, null).orEmpty())
    }.getOrDefault(AppIconVariant.Current)

    fun savePreferredFutachaIcon(variant: AppIconVariant) {
        preferences.edit().putString(KEY_PREFERRED_FUTACHA_ICON, variant.name).commit()
    }

    /**
     * Persists navigation that must survive the launcher-alias hand-off used by a
     * profile switch. The destination Activity acknowledges it only after the
     * requested screen has actually become active.
     */
    fun savePendingThreadNavigation(url: String, target: ExperienceProfile) {
        synchronized(lock) {
            check(
                preferences.edit()
                    .putString(KEY_PENDING_THREAD_URL, url)
                    .putString(KEY_PENDING_THREAD_PROFILE, target.persistedValue)
                    .commit()
            ) { "Failed to persist pending thread navigation" }
        }
    }

    fun readPendingThreadNavigation(target: ExperienceProfile): String? = synchronized(lock) {
        if (preferences.getString(KEY_PENDING_THREAD_PROFILE, null) != target.persistedValue) {
            return@synchronized null
        }
        preferences.getString(KEY_PENDING_THREAD_URL, null)?.takeIf(String::isNotBlank)
    }

    fun clearPendingThreadNavigation(expectedUrl: String? = null) {
        synchronized(lock) {
            if (
                expectedUrl != null &&
                preferences.getString(KEY_PENDING_THREAD_URL, null) != expectedUrl
            ) {
                return@synchronized
            }
            check(
                preferences.edit()
                    .remove(KEY_PENDING_THREAD_URL)
                    .remove(KEY_PENDING_THREAD_PROFILE)
                    .commit()
            ) { "Failed to clear pending thread navigation" }
        }
    }

    fun beginSwitch(from: ExperienceProfile, to: ExperienceProfile): ModeSwitchJournal = synchronized(lock) {
        val generation = nextExperienceProfileGeneration(readGeneration())
        val journal = ModeSwitchJournal(from, to, ModeSwitchPhase.SESSION_FLUSHED, generation)
        persistJournal(journal)
        journal
    }

    suspend fun beginSwitchWithCommitBarrier(
        from: ExperienceProfile,
        to: ExperienceProfile
    ): ModeSwitchJournal = generationCommitMutex.withLock {
        beginSwitch(from, to)
    }

    fun advanceSwitch(journal: ModeSwitchJournal, phase: ModeSwitchPhase): ModeSwitchJournal = synchronized(lock) {
        val advanced = journal.copy(phase = phase)
        persistJournal(advanced)
        advanced
    }

    fun persistRequestedProfile(journal: ModeSwitchJournal): ModeSwitchJournal = synchronized(lock) {
        val advanced = journal.copy(phase = ModeSwitchPhase.PROFILE_PERSISTED)
        check(
            preferences.edit()
                .putString(KEY_ACTIVE_PROFILE, journal.to.persistedValue)
                .putLong(KEY_GENERATION, journal.generation)
                .putString(KEY_JOURNAL_PHASE, advanced.phase.name)
                .commit()
        ) { "Failed to persist experience profile" }
        activeProfileState.value = journal.to
        generationState.value = journal.generation
        advanced
    }

    suspend fun persistRequestedProfileWithCommitBarrier(
        journal: ModeSwitchJournal
    ): ModeSwitchJournal = generationCommitMutex.withLock {
        persistRequestedProfile(journal)
    }

    suspend fun runIfGenerationCurrent(
        profile: ExperienceProfile,
        generation: Long,
        block: suspend () -> Unit
    ): Boolean = generationCommitMutex.withLock {
        if (!isGenerationCommitAllowed(profile, generation)) {
            false
        } else {
            block()
            true
        }
    }

    fun completeSwitch(journal: ModeSwitchJournal) = synchronized(lock) {
        check(
            preferences.edit()
                .remove(KEY_JOURNAL_FROM)
                .remove(KEY_JOURNAL_TO)
                .remove(KEY_JOURNAL_PHASE)
                .remove(KEY_JOURNAL_GENERATION)
                .commit()
        ) { "Failed to clear mode switch journal" }
        activeProfileState.value = journal.to
        generationState.value = journal.generation
    }

    fun readJournal(): ModeSwitchJournal? {
        val from = preferences.getString(KEY_JOURNAL_FROM, null) ?: return null
        val to = preferences.getString(KEY_JOURNAL_TO, null) ?: return null
        val phase = preferences.getString(KEY_JOURNAL_PHASE, null) ?: return null
        val generation = preferences.getLong(KEY_JOURNAL_GENERATION, -1L).takeIf { it >= 0L } ?: return null
        return runCatching {
            ModeSwitchJournal(
                from = ExperienceProfile.fromPersistedValue(from),
                to = ExperienceProfile.fromPersistedValue(to),
                phase = ModeSwitchPhase.valueOf(phase),
                generation = generation
            )
        }.getOrNull()
    }

    fun isGenerationCurrent(profile: ExperienceProfile, generation: Long): Boolean =
        readActiveProfile() == profile && readGeneration() == generation

    fun isGenerationCommitAllowed(profile: ExperienceProfile, generation: Long): Boolean =
        readJournal() == null && isGenerationCurrent(profile, generation)

    fun captureGenerationIfCommitAllowed(profile: ExperienceProfile): Long? {
        val generation = readGeneration()
        return generation.takeIf { isGenerationCommitAllowed(profile, it) }
    }

    private fun persistJournal(journal: ModeSwitchJournal) {
        check(
            preferences.edit()
                .putString(KEY_JOURNAL_FROM, journal.from.persistedValue)
                .putString(KEY_JOURNAL_TO, journal.to.persistedValue)
                .putString(KEY_JOURNAL_PHASE, journal.phase.name)
                .putLong(KEY_JOURNAL_GENERATION, journal.generation)
                .commit()
        ) { "Failed to persist mode switch journal" }
    }

    private companion object {
        const val PREFERENCES_NAME = "experience_profile_bootstrap"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_GENERATION = "profile_generation"
        const val KEY_PREFERRED_FUTACHA_ICON = "preferred_futacha_icon"
        const val KEY_JOURNAL_FROM = "switch_from"
        const val KEY_JOURNAL_TO = "switch_to"
        const val KEY_JOURNAL_PHASE = "switch_phase"
        const val KEY_JOURNAL_GENERATION = "switch_generation"
        const val KEY_PENDING_THREAD_URL = "pending_thread_url"
        const val KEY_PENDING_THREAD_PROFILE = "pending_thread_profile"
    }
}
