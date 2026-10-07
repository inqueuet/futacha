package com.valoser.futacha.shared.ui.privacy

import kotlin.test.Test
import kotlin.test.assertEquals

class PrivacyOptionsTest {
    @Test fun oldImagePrivacyPreferencesDoNotBecomeScreenStrengthOrTitleOverrides() {
        assertEquals(PrivacyOptions(), privacyOptions(mapOf(
            "compat.common.commonPrivacyAlpha" to "20%",
            "compat.common.commonPrivacy" to "ON"
        )))
    }

    @Test fun invalidBackupValuesFallBackAndCannotMakeTheScreenCompletelyBlack() {
        assertEquals(PrivacyOptions(), privacyOptions(mapOf(
            PRIVACY_FILTER_KEY to "future-filter", PRIVACY_STRENGTH_KEY to "NaN", PRIVACY_TITLE_KEY to "future-title"
        )))
        assertEquals(80, privacyOptions(mapOf(PRIVACY_STRENGTH_KEY to "100")).strength)
        assertEquals(20, privacyOptions(mapOf(PRIVACY_STRENGTH_KEY to "-1")).strength)
    }

    @Test fun titleChoiceIsIndependentOfScreenFilterAndImageTransparency() {
        val values = mapOf(PRIVACY_FILTER_KEY to "MESH", PRIVACY_STRENGTH_KEY to "70", PRIVACY_TITLE_KEY to "HIDDEN")
        assertEquals(PrivacyOptions(PrivacyFilter.MESH, 70, PrivateTitle.HIDDEN), privacyOptions(values))
        assertEquals(PrivateTitle.HIDDEN, privacyOptions(values + ("compat.common.commonPrivacy" to "OFF")).title)
    }
}
