package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GlobalSettingsLinksTest {
    @Test
    fun resolveGlobalSettingsActionTarget_returnsExpectedUrls() {
        assertEquals(
            "mailto:admin@valoser.com?subject=お問い合わせ",
            resolveGlobalSettingsActionTarget(GlobalSettingsAction.Email)
        )
        assertEquals(
            "https://x.com/inqueuet",
            resolveGlobalSettingsActionTarget(GlobalSettingsAction.X)
        )
        assertEquals(
            "https://github.com/inqueuet/futacha",
            resolveGlobalSettingsActionTarget(GlobalSettingsAction.Developer)
        )
        assertEquals(
            "https://note.com/inqueuet/n/nc6ebcc1d6a67",
            resolveGlobalSettingsActionTarget(GlobalSettingsAction.PrivacyPolicy)
        )
    }

    @Test
    fun resolveGlobalSettingsActionTarget_returnsNullForCookies() {
        assertNull(resolveGlobalSettingsActionTarget(GlobalSettingsAction.Cookies))
    }
}
