package com.valoser.futacha

import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.ModeSwitchRecoveryException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileSwitchThreadNavigationTest {
    /** Same single slot as AndroidExperienceProfileStore's pending thread navigation. */
    private class FakeSlot {
        var url: String? = null
        var profile: ExperienceProfile? = null

        fun read(target: ExperienceProfile): String? = url.takeIf { profile == target }

        val navigation = ProfileSwitchThreadNavigation(
            save = { url, target ->
                this.url = url
                profile = target
            },
            clear = { expectedUrl ->
                if (expectedUrl == null || url == expectedUrl) {
                    url = null
                    profile = null
                }
            }
        )
    }

    private val link = "https://may.2chan.net/b/res/100.htm"

    @Test
    fun failedSwitchDoesNotLeaveItsLinkForALaterSwitch() {
        val slot = FakeSlot()
        slot.navigation.beforeSwitch(link, ExperienceProfile.TOSHIAKI_COMPAT)
        assertEquals(link, slot.read(ExperienceProfile.TOSHIAKI_COMPAT))

        slot.navigation.afterFailedSwitch(link)

        // A later switch without a link must not open (and preapprove) the old one.
        slot.navigation.beforeSwitch(null, ExperienceProfile.TOSHIAKI_COMPAT)
        assertNull(slot.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @Test
    fun switchWithoutLinkRemovesALinkLeftByAnEarlierFailure() {
        // Left behind by a failed switch before this fix (or a lost cleanup).
        val slot = FakeSlot().apply {
            url = link
            profile = ExperienceProfile.TOSHIAKI_COMPAT
        }
        slot.navigation.beforeSwitch(null, ExperienceProfile.TOSHIAKI_COMPAT)
        assertNull(slot.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @Test
    fun switchWithLinkStoresItForTheTarget() {
        val slot = FakeSlot()
        slot.navigation.beforeSwitch(link, ExperienceProfile.FUTACHA)
        assertEquals(link, slot.read(ExperienceProfile.FUTACHA))
        assertNull(slot.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @Test
    fun failureCleanupKeepsANewerLink() {
        val slot = FakeSlot()
        slot.navigation.beforeSwitch(link, ExperienceProfile.TOSHIAKI_COMPAT)
        val newer = "https://may.2chan.net/b/res/200.htm"
        slot.navigation.beforeSwitch(newer, ExperienceProfile.TOSHIAKI_COMPAT)

        slot.navigation.afterFailedSwitch(link)
        assertEquals(newer, slot.read(ExperienceProfile.TOSHIAKI_COMPAT))

        // Nothing was stored for a switch without a link, so nothing is removed.
        slot.navigation.afterFailedSwitch(null)
        assertEquals(newer, slot.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @Test
    fun linkIsKeptWhenTheRollbackFailedBecauseTheNextLaunchCompletesTheSwitch() {
        val slot = FakeSlot()
        slot.navigation.beforeSwitch(link, ExperienceProfile.TOSHIAKI_COMPAT)
        val failure = IllegalStateException("alias").apply { addSuppressed(IllegalStateException("rollback")) }

        slot.navigation.afterFailedSwitch(link, failure)
        assertEquals(link, slot.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }

    @Test
    fun linkIsRemovedWhenTheSwitchWasRolledBackOrNeverStarted() {
        val rolledBack = FakeSlot()
        rolledBack.navigation.beforeSwitch(link, ExperienceProfile.TOSHIAKI_COMPAT)
        rolledBack.navigation.afterFailedSwitch(link, IllegalStateException("alias"))
        assertNull(rolledBack.read(ExperienceProfile.TOSHIAKI_COMPAT))

        // An earlier journal could not be finished: this switch never began (M4-1).
        val notStarted = FakeSlot()
        notStarted.navigation.beforeSwitch(link, ExperienceProfile.TOSHIAKI_COMPAT)
        notStarted.navigation.afterFailedSwitch(link, ModeSwitchRecoveryException(IllegalStateException("io")))
        assertNull(notStarted.read(ExperienceProfile.TOSHIAKI_COMPAT))
    }
}
