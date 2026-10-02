package com.valoser.futacha.shared.compat

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModeSwitchRollbackTest {
    private class FakeStore(
        var active: ExperienceProfile,
        var generation: Long,
        var journal: ModeSwitchJournal?
    ) : ModeSwitchRollbackStore {
        var failPersist = false
        override suspend fun readJournal() = journal
        override suspend fun readGeneration() = generation
        override suspend fun writeJournal(journal: ModeSwitchJournal) {
            this.journal = journal
        }
        override suspend fun persistProfile(journal: ModeSwitchJournal) {
            if (failPersist) error("disk full")
            active = journal.to
            generation = journal.generation
            this.journal = journal.copy(phase = ModeSwitchPhase.PROFILE_PERSISTED)
        }
        override suspend fun clearJournal(journal: ModeSwitchJournal) {
            this.journal = null
        }

        /** What commit gates check: no journal and the expected profile/generation. */
        fun commitAllowed(profile: ExperienceProfile, generation: Long) =
            journal == null && active == profile && this.generation == generation
    }

    private fun failedJournal(phase: ModeSwitchPhase) = ModeSwitchJournal(
        from = ExperienceProfile.FUTACHA,
        to = ExperienceProfile.TOSHIAKI_COMPAT,
        phase = phase,
        generation = 6L
    )

    @Test
    fun failureBeforeProfilePersistenceReturnsToOriginWithNewGeneration() = runBlocking {
        val store = FakeStore(ExperienceProfile.FUTACHA, 5L, failedJournal(ModeSwitchPhase.OLD_PROFILE_QUIESCED))
        val reconciled = mutableListOf<String>()
        // Before the fix the journal stayed and this gate refused every commit.
        assertEquals(false, store.commitAllowed(ExperienceProfile.FUTACHA, 5L))

        val generation = rollBackFailedModeSwitch(store, ExperienceProfile.FUTACHA) { reconciled += "origin" }

        assertEquals(7L, generation)
        assertNull(store.journal)
        assertEquals(ExperienceProfile.FUTACHA, store.active)
        assertEquals(true, store.commitAllowed(ExperienceProfile.FUTACHA, 7L))
        // Work captured for the abandoned switch or the quiesced old run stays invalid.
        assertEquals(false, store.commitAllowed(ExperienceProfile.FUTACHA, 5L))
        assertEquals(false, store.commitAllowed(ExperienceProfile.TOSHIAKI_COMPAT, 6L))
        assertEquals(listOf("origin"), reconciled)
    }

    @Test
    fun failureAfterProfilePersistenceRestoresTheOriginProfile() = runBlocking {
        val store = FakeStore(ExperienceProfile.TOSHIAKI_COMPAT, 6L, failedJournal(ModeSwitchPhase.PROFILE_PERSISTED))

        val generation = rollBackFailedModeSwitch(store, ExperienceProfile.FUTACHA) {}

        assertEquals(7L, generation)
        assertEquals(ExperienceProfile.FUTACHA, store.active)
        assertEquals(true, store.commitAllowed(ExperienceProfile.FUTACHA, 7L))
    }

    @Test
    fun iconReconcileFailureStillClearsTheJournal() = runBlocking {
        val store = FakeStore(ExperienceProfile.TOSHIAKI_COMPAT, 6L, failedJournal(ModeSwitchPhase.PROFILE_PERSISTED))

        rollBackFailedModeSwitch(store, ExperienceProfile.FUTACHA) { error("package manager") }

        assertNull(store.journal)
        assertEquals(true, store.commitAllowed(ExperienceProfile.FUTACHA, 7L))
    }

    @Test
    fun interruptedRollbackLeavesAJournalTowardsTheOrigin() = runBlocking {
        val store = FakeStore(ExperienceProfile.TOSHIAKI_COMPAT, 6L, failedJournal(ModeSwitchPhase.PROFILE_PERSISTED))
        store.failPersist = true

        assertFailsWith<IllegalStateException> {
            rollBackFailedModeSwitch(store, ExperienceProfile.FUTACHA) {}
        }

        // Next-launch recovery completes towards journal.to: the profile the user stayed in.
        assertEquals(ExperienceProfile.FUTACHA, store.journal?.to)
        assertEquals(7L, store.journal?.generation)
    }

    @Test
    fun nothingIsChangedWhenNoJournalWasWritten() = runBlocking {
        val store = FakeStore(ExperienceProfile.FUTACHA, 5L, journal = null)
        var reconciled = false

        assertNull(rollBackFailedModeSwitch(store, ExperienceProfile.FUTACHA) { reconciled = true })

        assertEquals(5L, store.generation)
        assertEquals(false, reconciled)
    }
}
