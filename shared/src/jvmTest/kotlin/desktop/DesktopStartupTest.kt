package com.valoser.futacha.shared.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * L125: a failed start of the Desktop window (the stores, the profile file) left only its message, with nothing to do
 * but quit; iOS offers 再試行. The retry runs the start again and the window opens when it succeeds.
 */
class DesktopStartupTest {
    @Test
    fun aFailedStartIsShownAndRunsAgainOnRetry() {
        var calls = 0
        var startup: DesktopStartup? = null
        val scene = ImageComposeScene(10, 10, Density(1f)) {
            startup = rememberDesktopStartup("graph") {
                calls++
                if (calls == 1) error("保存先に書き込めません")
            }
        }
        var frame = 0L
        fun render() = repeat(3) {
            Snapshot.sendApplyNotifications()
            frame += 16_000_000L
            scene.render(frame)
        }
        try {
            render()
            val shown = assertNotNull(startup)
            assertEquals(1, calls)
            assertFalse(shown.ready)
            assertEquals("保存先に書き込めません", shown.error)

            shown.retry()
            render()
            assertEquals(2, calls)
            assertTrue(shown.ready)
            assertNull(shown.error)

            // A retry once started does nothing more.
            shown.retry()
            render()
            assertEquals(2, calls)
        } finally {
            scene.close()
        }
    }
}
