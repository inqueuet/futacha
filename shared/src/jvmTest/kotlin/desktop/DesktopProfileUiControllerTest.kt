package com.valoser.futacha.shared.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ExperienceProfileSessionToken
import com.valoser.futacha.shared.compat.ExperienceProfileUiController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * L123 (2026-10-02 M4-4): the Desktop window's profile controller is remembered like Android's and iOS's. A new one on
 * every recomposition of the host (a notification click, a notification setting change) was never equal to the last
 * (fresh lambdas), so the static local recomposed all of FutachaApp each time.
 */
class DesktopProfileUiControllerTest {
    @Test
    fun anUnrelatedRecompositionKeepsTheControllerAndAProfileChangeMakesANewOne() {
        var unrelated by mutableStateOf(0)
        var profile by mutableStateOf(ExperienceProfile.FUTACHA)
        var generation by mutableStateOf(1L)
        var switching by mutableStateOf(false)
        val requested = mutableListOf<String>()
        val seen = mutableListOf<ExperienceProfileUiController>()
        val scene = ImageComposeScene(10, 10, Density(1f)) {
            val tick = unrelated
            val controller = rememberDesktopProfileUiController(
                profile = profile,
                generation = generation,
                switching = switching,
                lastError = null,
                isSessionCurrent = { token -> !switching && token.profile == profile && token.generation == generation },
                // A new lambda on every recomposition, as the host's is; the latest one is called.
                requestSwitch = { target -> requested += "$tick:${target.persistedValue}" }
            )
            SideEffect { seen += controller }
        }
        var frame = 0L
        fun render() {
            Snapshot.sendApplyNotifications()
            frame += 16_000_000L
            scene.render(frame)
        }
        try {
            render()
            val first = seen.last()
            unrelated++
            render()
            assertTrue(seen.size >= 2, "the host did not recompose")
            assertSame(first, seen.last())
            first.requestSwitch(ExperienceProfile.TOSHIAKI_COMPAT)
            assertEquals(listOf("1:${ExperienceProfile.TOSHIAKI_COMPAT.persistedValue}"), requested)

            profile = ExperienceProfile.TOSHIAKI_COMPAT
            generation = 2L
            render()
            val switched = seen.last()
            assertNotSame(first, switched)
            assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, switched.activeProfile)
            assertEquals(2L, switched.sessionGeneration)
            val token = ExperienceProfileSessionToken(ExperienceProfile.TOSHIAKI_COMPAT, 2L)
            assertTrue(switched.isSessionAuthoritativelyCurrent!!(token))
            // The check reads the state now, not the state the controller was made with.
            assertFalse(first.isSessionAuthoritativelyCurrent!!(ExperienceProfileSessionToken(ExperienceProfile.FUTACHA, 1L)))
        } finally {
            scene.close()
        }
    }
}
