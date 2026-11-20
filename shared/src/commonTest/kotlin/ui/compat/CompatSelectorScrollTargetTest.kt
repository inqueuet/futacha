package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** aimg seq45: the tab strip keeps the thread being read in view. */
class CompatSelectorScrollTargetTest {
    @Test
    fun aCurrentTabInViewDoesNotMoveTheRow() {
        assertNull(compatSelectorScrollTarget(3, listOf(2, 3, 4, 5), 6))
    }

    @Test
    fun aCurrentTabOffScreenComesToTheMiddle() {
        assertEquals(17, compatSelectorScrollTarget(20, listOf(0, 1, 2, 3, 4, 5), 6))
        assertEquals(0, compatSelectorScrollTarget(1, emptyList(), 6))
    }
}
