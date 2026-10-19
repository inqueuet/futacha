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
        assertEquals(PrivacyOptions(scope = PrivacyScope.SCREEN), privacyOptions(mapOf(
            PRIVACY_FILTER_KEY to "future-filter", PRIVACY_STRENGTH_KEY to "NaN", PRIVACY_TITLE_KEY to "future-title"
        )))
        assertEquals(80, privacyOptions(mapOf(PRIVACY_STRENGTH_KEY to "100")).strength)
        assertEquals(20, privacyOptions(mapOf(PRIVACY_STRENGTH_KEY to "-1")).strength)
    }

    @Test fun titleChoiceIsIndependentOfScreenFilterAndImageTransparency() {
        val values = mapOf(PRIVACY_FILTER_KEY to "MESH", PRIVACY_STRENGTH_KEY to "70", PRIVACY_TITLE_KEY to "HIDDEN")
        assertEquals(PrivacyOptions(PrivacyFilter.MESH, 70, PrivateTitle.HIDDEN, scope = PrivacyScope.SCREEN), privacyOptions(values))
        assertEquals(PrivateTitle.HIDDEN, privacyOptions(values + ("compat.common.commonPrivacy" to "OFF")).title)
    }

    @Test fun imageOnlyIsTheDefaultAndExplicitScreenChoiceSurvivesMigration() {
        assertEquals(PrivacyScope.IMAGES, privacyOptions(emptyMap()).scope)
        assertEquals(PrivacyScope.IMAGES, privacyOptions(mapOf("compat.common.commonPrivacyAlpha" to "20")).scope)
        assertEquals(PrivacyScope.SCREEN, privacyOptions(mapOf(PRIVACY_FILTER_KEY to "MESH")).scope)
        assertEquals(PrivacyScope.IMAGES, privacyOptions(mapOf(PRIVACY_FILTER_KEY to "MESH", PRIVACY_SCOPE_KEY to "IMAGES")).scope)
    }

    @Test fun modeTitlesAndMediaStrengthStayIndependent() {
        val options = privacyOptions(mapOf(PRIVACY_TITLE_KEY to "HIDDEN", PRIVACY_COMPAT_TITLE_KEY to "SMALL", PRIVACY_IMAGE_STRENGTH_KEY to "60"))
        assertEquals(PrivateTitle.HIDDEN, options.title)
        assertEquals(PrivateTitle.SMALL, options.compatTitle)
        assertEquals(60, options.imageStrength)
        assertEquals(50, options.strength)
    }
}
