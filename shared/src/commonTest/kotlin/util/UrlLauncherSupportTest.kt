package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UrlLauncherSupportTest {
    @Test
    fun resolveUrlLaunchRequest_trims_and_routes_mailto() {
        assertEquals(
            UrlLaunchRequest(
                normalizedUrl = "mailto:admin@valoser.com?subject=test",
                target = UrlLaunchTarget.Mail
            ),
            resolveUrlLaunchRequest("  mailto:admin@valoser.com?subject=test  ")
        )
    }

    @Test
    fun resolveUrlLaunchRequest_routes_non_mail_schemes_to_browser() {
        assertEquals(
            UrlLaunchRequest(
                normalizedUrl = "https://example.com/path?q=1",
                target = UrlLaunchTarget.Browser
            ),
            resolveUrlLaunchRequest("https://example.com/path?q=1")
        )
        assertEquals(
            UrlLaunchTarget.Browser,
            resolveUrlLaunchRequest("futaba://thread/123")?.target
        )
    }

    @Test
    fun resolveUrlLaunchRequest_rejects_blank_and_scheme_less_input() {
        assertNull(resolveUrlLaunchRequest(""))
        assertNull(resolveUrlLaunchRequest("   "))
        assertNull(resolveUrlLaunchRequest("example.com/path"))
    }
}
