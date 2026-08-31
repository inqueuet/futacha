package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExperienceProfileSessionTest {
    @Test
    fun nextGenerationIsPositiveAndRecoversFromInvalidOrExhaustedStorage() {
        assertEquals(2L, nextExperienceProfileGeneration(1L))
        assertEquals(1L, nextExperienceProfileGeneration(0L))
        assertEquals(1L, nextExperienceProfileGeneration(-1L))
        assertEquals(1L, nextExperienceProfileGeneration(Long.MAX_VALUE))
    }

    @Test
    fun sessionResultRequiresSameActiveProfileAndGeneration() {
        val active = ExperienceProfileUiController(
            activeProfile = ExperienceProfile.TOSHIAKI_COMPAT,
            sessionGeneration = 7L,
            isSessionActive = true
        )
        val token = captureExperienceProfileSession(active)

        assertTrue(isExperienceProfileSessionCurrent(token, active))
        assertFalse(isExperienceProfileSessionCurrent(token, active.copy(isSessionActive = false)))
        assertFalse(isExperienceProfileSessionCurrent(token, active.copy(sessionGeneration = 8L)))
        assertFalse(isExperienceProfileSessionCurrent(token, active.copy(activeProfile = ExperienceProfile.FUTACHA)))
    }

    @Test
    fun authoritativeStoreCanRejectAnOtherwiseCurrentComposeSnapshot() {
        val staleSnapshot = ExperienceProfileUiController(
            activeProfile = ExperienceProfile.TOSHIAKI_COMPAT,
            sessionGeneration = 7L,
            isSessionActive = true,
            isSessionAuthoritativelyCurrent = { false }
        )
        val token = captureExperienceProfileSession(staleSnapshot)

        assertFalse(isExperienceProfileSessionCurrent(token, staleSnapshot))
        assertTrue(
            isExperienceProfileSessionCurrent(
                token,
                staleSnapshot.copy(isSessionAuthoritativelyCurrent = { true })
            )
        )
    }

    @Test
    fun resultGateConsumesOnlyOnceAndDropsAfterSessionInvalidation() {
        val active = ExperienceProfileUiController(
            activeProfile = ExperienceProfile.FUTACHA,
            sessionGeneration = 12L,
            isSessionActive = true
        )
        val gate = ExperienceProfileResultGate()

        gate.markLaunched(active)
        assertNotNull(gate.consumeIfCurrent(active))
        assertNull(gate.consumeIfCurrent(active))

        gate.markLaunched(active)
        assertNull(gate.consumeIfCurrent(active.copy(isSessionActive = false)))

        gate.markLaunched(active)
        gate.clear()
        assertNull(gate.consumeIfCurrent(active))
    }
}
