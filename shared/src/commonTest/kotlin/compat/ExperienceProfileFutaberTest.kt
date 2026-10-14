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
        assertEquals("ftutter", ExperienceProfile.FTUTTER.persistedValue)
        assertEquals("ふたったー風モード", ExperienceProfile.FTUTTER.displayName)
        assertEquals(
            setOf("futacha", "toshiaki_compat", "futaber", "ftutter"),
            ExperienceProfile.entries.map { it.persistedValue }.toSet()
        )
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
                    listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT, ExperienceProfile.FUTABER, ExperienceProfile.FTUTTER),
                    ExperienceProfile.selectableEntries
                )
                assertEquals(ExperienceProfile.FUTABER, ExperienceProfile.fromPersistedValue("futaber"))
                // ふたったー風 is released on mobile too (2026-10-14).
                assertTrue(ExperienceProfile.FTUTTER.isSelectable)
                assertEquals(ExperienceProfile.FTUTTER, ExperienceProfile.fromPersistedValue("ftutter"))
            }
            // The desktop hosts: neither listed nor restored; a value saved elsewhere falls back to ふたちゃ.
            withMobileModes(false) {
                assertFalse(ExperienceProfile.FUTABER.isSelectable)
                assertFalse(ExperienceProfile.FTUTTER.isSelectable)
                assertEquals(
                    listOf(ExperienceProfile.FUTACHA, ExperienceProfile.TOSHIAKI_COMPAT),
                    ExperienceProfile.selectableEntries
                )
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue("futaber"))
                assertEquals(ExperienceProfile.FUTACHA, ExperienceProfile.fromPersistedValue("ftutter"))
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
        assertEquals(ProfileDataOwner.APP_STATE, ExperienceProfile.FTUTTER.dataOwner)
        assertTrue(ExperienceProfile.FTUTTER.usesAppStateData)
    }

    @Test
    fun futaberAndFtutterAreTheMobileOnlyModesAndNoModeIsPreviewOnly() {
        assertEquals(
            listOf(ExperienceProfile.FUTABER, ExperienceProfile.FTUTTER),
            ExperienceProfile.entries.filter { it.availability == ModeAvailability.MOBILE }
        )
        // The PREVIEW mechanism stays for future unfinished modes.
        assertEquals(emptyList(), ExperienceProfile.entries.filter { it.availability == ModeAvailability.PREVIEW })
    }

    @Test
    fun ftutterIsOfferedOnMobileAndInPreviewBuildsButNotOnTheDesktopRelease() {
        listOf(true, false).forEach { mobile ->
            listOf(true, false).forEach { preview ->
                withMobileModes(mobile) {
                    withPreviewModes(preview) {
                        val offered = mobile || preview
                        assertEquals(offered, ExperienceProfile.FTUTTER.isSelectable)
                        assertEquals(
                            if (offered) ExperienceProfile.FTUTTER else ExperienceProfile.FUTACHA,
                            ExperienceProfile.fromPersistedValue("ftutter")
                        )
                        if (offered) assertEquals(ExperienceProfile.FTUTTER, ExperienceProfile.selectableEntries.last())
                    }
                }
            }
        }
    }
}
