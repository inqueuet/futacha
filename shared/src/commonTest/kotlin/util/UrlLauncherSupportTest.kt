package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertNull(resolveUrlLaunchRequest("javascript:alert(1)"))
        assertNull(resolveUrlLaunchRequest("intent://malicious"))
        assertNull(resolveUrlLaunchRequest("file:///private/data"))
        assertNull(resolveUrlLaunchRequest("data:text/html,content"))
        assertNull(resolveUrlLaunchRequest("https://example.com/path\nInjected"))
        assertNull(resolveUrlLaunchRequest("https://example.com/" + "x".repeat(8_192)))
    }

    @Test
    fun describeUrlForLog_omitsCredentialsPathQueryAndMailRecipient() {
        val webDescription = describeUrlForLog("https://user:secret@example.com/private?q=token#fragment")
        val mailUrl = "mailto:private@example.com?subject=secret"
        val mailDescription = describeUrlForLog(mailUrl)

        assertTrue(webDescription.contains("host=example.com"))
        assertFalse(webDescription.contains("user"))
        assertFalse(webDescription.contains("secret"))
        assertFalse(webDescription.contains("private"))
        assertEquals("scheme=mailto, length=${mailUrl.length}", mailDescription)
    }

    @Test
    fun describeFailureForLog_omitsExceptionMessage() {
        val description = describeFailureForLog(
            IllegalArgumentException("https://user:secret@example.com/private?q=token")
        )

        assertEquals("IllegalArgumentException", description)
        assertFalse(description.contains("secret"))
    }
}
