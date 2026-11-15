package com.valoser.futacha.shared.ui.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimogeBoardIconsTest {
    @AfterTest fun reset() { AimogeBoardIcons.resetForTest(); AimogeAvailability.sitesEnabled = false }

    @Test
    fun aBoardsIconIsShownOnlyForAimogeBoardsWhileOnAndOnlyFromTheSite() {
        val saved = AimogeBoardIcons.learn(listOf(
            AimogeBoardInfo("ai2", "AI", "https://nijiurachan.net/b/ai2/", iconUrl = "https://nijiurachan.net/i/ai2.png"),
            AimogeBoardInfo("x", "X", "https://nijiurachan.net/b/x/", iconUrl = "https://evil.example/x.png")
        ))
        assertTrue("ai2.png" in saved && "evil" !in saved)
        val icons = AimogeBoardIcons.icons.value
        assertNull(AimogeBoardIcons.iconOf("https://nijiurachan.net/b/ai2/", icons), "nothing while あいもげ is off")
        AimogeAvailability.sitesEnabled = true
        assertEquals("https://nijiurachan.net/i/ai2.png", AimogeBoardIcons.iconOf("https://nijiurachan.net/b/ai2/", icons))
        assertNull(AimogeBoardIcons.iconOf("https://may.2chan.net/b/", icons), "Futaba boards keep their icon")
        assertFalse(AimogeBoardIcons.isSafeIcon("http://nijiurachan.net/a.png"))
        assertFalse(AimogeBoardIcons.isSafeIcon("https://nijiurachan.net.evil.example/a.png"))
    }
}
