package com.valoser.futacha.shared.compat

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedExperienceProfileResultGateTest {
    private val controller = ExperienceProfileUiController(
        isAvailable = true,
        activeProfile = ExperienceProfile.TOSHIAKI_COMPAT,
        sessionGeneration = 12L,
        isSessionActive = true,
        isSessionAuthoritativelyCurrent = { true }
    )

    @Test
    fun resultAfterProcessRecreationIsDeliveredFromTheSavedToken() {
        val beforeDeath = SavedExperienceProfileResultGate(mutableStateOf(null))
        val saved = mutableStateOf<String?>(null)
        SavedExperienceProfileResultGate(saved).markLaunched(controller)

        // The previous in-memory gate starts empty after recreation and dropped the result.
        assertNull(beforeDeath.consumeIfCurrent(controller))

        val restored = SavedExperienceProfileResultGate(mutableStateOf(saved.value))
        assertEquals(
            ExperienceProfileSessionToken(ExperienceProfile.TOSHIAKI_COMPAT, 12L),
            restored.consumeIfCurrent(controller)
        )
        // Delivered once only.
        assertNull(restored.consumeIfCurrent(controller))
    }

    @Test
    fun restoredTokenIsStillRejectedAfterAProfileChange() {
        val saved = mutableStateOf<String?>(null)
        SavedExperienceProfileResultGate(saved).markLaunched(controller)

        val restored = SavedExperienceProfileResultGate(mutableStateOf(saved.value))

        assertNull(restored.consumeIfCurrent(controller.copy(sessionGeneration = 13L)))
    }

    @Test
    fun clearedOrMalformedTokensAuthorizeNothing() {
        val state = mutableStateOf<String?>(null)
        val gate = SavedExperienceProfileResultGate(state)
        gate.markLaunched(controller)
        gate.clear()
        assertNull(gate.consumeIfCurrent(controller))

        listOf("", "toshiaki_compat", "unknown:12", "toshiaki_compat:x", ":12").forEach { value ->
            assertNull(decodeExperienceProfileSessionToken(value), value)
        }
        assertEquals(
            ExperienceProfileSessionToken(ExperienceProfile.FUTACHA, 3L),
            decodeExperienceProfileSessionToken(
                encodeExperienceProfileSessionToken(ExperienceProfileSessionToken(ExperienceProfile.FUTACHA, 3L))
            )
        )
    }
}
