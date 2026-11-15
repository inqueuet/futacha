package com.valoser.futacha.shared.network.aimoge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Thread 01a1cd5d #68: the original's size was given as the thumbnail's, and ふたばー風 stretched the thumbnail. */
class AimogeThumbnailBoxTest {
    @Test fun theThumbnailSizeFitsTheServersBox() {
        // Pairs read from the live server: original → thumbnail.
        assertEquals(250 to 250, aimogeThumbnailBox(480, 480))
        assertEquals(115 to 250, aimogeThumbnailBox(1206, 2622))
        assertEquals(250 to 227, aimogeThumbnailBox(1361, 1234))
        assertEquals(120 to 80, aimogeThumbnailBox(120, 80))
        assertNull(aimogeThumbnailBox(null, 10))
        assertNull(aimogeThumbnailBox(0, 10))
    }
}
