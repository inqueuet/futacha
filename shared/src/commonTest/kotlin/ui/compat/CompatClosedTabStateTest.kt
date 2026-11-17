package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatClosedTabStateTest {
    @Test
    fun closedTabStateIsKeptWhileUndoIsPossibleAndDroppedAfterwards() {
        val known = setOf("a", "b", "c")
        // "b" was just closed and can still be restored by Undo.
        assertEquals(setOf("c"), compatStaleThreadStateKeys(known, open = setOf("a"), pendingClose = setOf("b")))
        // Once the Undo batch expires, its state is dropped as well.
        assertEquals(setOf("b", "c"), compatStaleThreadStateKeys(known, open = setOf("a"), pendingClose = emptySet()))
        assertEquals(emptySet(), compatStaleThreadStateKeys(known, open = known, pendingClose = emptySet()))
    }
}
