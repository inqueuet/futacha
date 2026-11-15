package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.AimogeIncomingLink
import com.valoser.futacha.shared.compat.aimogeIncomingLinkOf
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AimogeDeepLinkManifestTest {
    private val thread = "https://nijiurachan.net/b/aimg/thread/01a0f76f-0000-7000-8000-000000000001"
    private val approval = "https://nijiurachan.net/b/aimg/app-auth?requestId=r1"

    @Test fun theMainManifestHasTheThreadFilterAndItTakesThreadsNotTheApprovalPage() {
        val debug = File("../app-android/src/main/AndroidManifest.xml").readText()
        assertTrue("android:host=\"nijiurachan.net\"" in debug)
        val pattern = Regex("android:pathPattern=\"(/b/[^\"]*)\"").find(debug)!!.groupValues[1]
        assertEquals("/b/.*/thread/.*", pattern)
        val path = Regex("^/b/.*/thread/.*$")
        assertTrue(path.matches("/b/aimg/thread/01a0f76f-0000-7000-8000-000000000001"))
        assertFalse(path.matches("/b/aimg/app-auth"), "the approval page stays in the browser")
        assertFalse(path.matches("/b/aimg/"))
        assertFalse("nijiurachan" in File("../app-android/src/debug/AndroidManifest.xml").readText())
        assertTrue("AimogeAvailability.sitesEnabled = true" in File("../app-android/src/main/java/com/valoser/futacha/FutachaApplication.kt").readText())
        assertFalse("nijiurachan" in File("../app-android/src/release/AndroidManifest.xml").let { if (it.exists()) it.readText() else "" })
    }

    @Test fun aThreadLinkOpensWhileOnAndIsRefusedWithTheMessageWhileOff() {
        withAimoge(enabled = false) {
            assertEquals(AimogeIncomingLink.UNAVAILABLE, aimogeIncomingLinkOf(thread))
            assertEquals(AimogeIncomingLink.NOT_AIMOGE, aimogeIncomingLinkOf(approval))
            assertEquals(AimogeIncomingLink.NOT_AIMOGE, aimogeIncomingLinkOf("https://may.2chan.net/b/res/1.htm"))
        }
        withAimoge(enabled = true) {
            assertEquals(AimogeIncomingLink.OPEN, aimogeIncomingLinkOf(thread))
            assertEquals(AimogeIncomingLink.NOT_AIMOGE, aimogeIncomingLinkOf(approval))
        }
        assertFalse(AimogeAvailability.sitesEnabled, "tests start with the shared default off")
    }
}
