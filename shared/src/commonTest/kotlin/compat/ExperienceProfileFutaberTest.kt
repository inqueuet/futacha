package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperienceProfileFutaberTest {
    private inline fun <T> withPreviewModes(enabled: Boolean, block: () -> T): T {
        val saved = ExperienceProfileAvailability.previewModesEnabled
        ExperienceProfileAvailability.previewModesEnabled = enabled
        try {
            return block()
        } finally {
            ExperienceProfileAvailability.previewModesEnabled = saved
        }
    }

    @Test
    fun storedValuesOfTheExistingModesAreFrozen() {
        assertEquals("futacha", ExperienceProfile.FUTACHA.persistedValue)
        assertEquals("toshiaki_compat", ExperienceProfile.TOSHIAKI_COMPAT.persistedValue)
        assertEquals("futaber", ExperienceProfile.FUTABER.persistedValue)
        assertEquals("ふたばー風モード", ExperienceProfile.FUTABER.displayName)
        assertEquals(setOf("futacha", "toshiaki_compat", "futaber"), ExperienceProfile.entries.map { it.persistedValue }.toSet())
    }

    @Test
    fun existingModesParseWhetherOrNotPreviewModesAreOn() {
        listOf(false, true).forEach { preview ->
            withPreviewModes(preview) {
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue("futacha"))
                assertEquals(ExperienceProfile.TOSHIAKI_COMPAT, ExperienceProfile.fromPersistedValue("toshiaki_compat"))
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue(null))
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue("unknown"))
            }
        }
    }

    private inline fun <T> withMobileModes(enabled: Boolean, block: () -> T): T {
        val saved = ExperienceProfileAvailability.mobileModesEnabled
        ExperienceProfileAvailability.mobileModesEnabled = enabled
        try {
            return block()
        } finally {
            ExperienceProfileAvailability.mobileModesEnabled = saved
        }
    }

    @Test
    fun futaberIsListedAndRestoredOnMobileInEveryBuildButNotOnTheDesktop() {
        // Android and iOS release builds: preview modes are off, the mobile-only modes are on.
        withPreviewModes(false) {
            withMobileModes(true) {
                assertTrue(ExperienceProfile.FUTABER.isSelectable)
                assertEquals(
                    listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT, ExperienceProfile.FUTABER),
                    ExperienceProfile.selectableEntries
                )
                assertEquals(ExperienceProfile.FUTABER, ExperienceProfile.fromPersistedValue("futaber"))
            }
            // The desktop hosts: neither listed nor restored; a value saved elsewhere falls back to ふたちゃ.
            withMobileModes(false) {
                assertFalse(ExperienceProfile.FUTABER.isSelectable)
                assertEquals(
                    listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT),
                    ExperienceProfile.selectableEntries
                )
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue("futaber"))
            }
        }
    }

    @Test
    fun previewBuildsAlsoOfferTheMobileOnlyModeEvenOnTheDesktop() {
        // A debug build of any host shows everything; only the plain desktop release hides the mobile-only mode.
        withPreviewModes(true) { withMobileModes(false) { assertTrue(ExperienceProfile.FUTABER.isSelectable) } }
        withPreviewModes(false) { withMobileModes(false) { assertFalse(ExperienceProfile.FUTABER.isSelectable) } }
        // The two existing modes never depend on either switch.
        listOf(true, false).forEach { preview ->
            listOf(true, false).forEach { mobile ->
                withPreviewModes(preview) {
                    withMobileModes(mobile) {
                        assertTrue(ExperienceProfile.FUTACHA.isSelectable)
                        assertTrue(ExperienceProfile.TOSHIAKI_COMPAT.isSelectable)
                    }
                }
            }
        }
    }

    @Test
    fun dataOwnerFollowsTheStoreEachModeEdits() {
        assertEquals(ProfileDataOwner.APP_STATE, ExperienceProfile.FUTACHA.dataOwner)
        assertEquals(ProfileDataOwner.COMPAT_STORE, ExperienceProfile.TOSHIAKI_COMPAT.dataOwner)
        assertEquals(ProfileDataOwner.APP_STATE, ExperienceProfile.FUTABER.dataOwner)
        assertTrue(ExperienceProfile.FUTACHA.usesAppStateData)
        assertFalse(ExperienceProfile.TOSHIAKI_COMPAT.usesAppStateData)
        assertTrue(ExperienceProfile.FUTABER.usesAppStateData)
    }

    @Test
    fun onlyFutaberIsAMobileOnlyModeAndNoModeIsPreviewOnly() {
        assertEquals(
            listOf(ExperienceProfile.FUTABER),
            ExperienceProfile.entries.filter { it.availability == ModeAvailability.MOBILE }
        )
        assertEquals(emptyList(), ExperienceProfile.entries.filter { it.availability == ModeAvailability.PREVIEW })
    }
}
