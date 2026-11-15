package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatReadAloudStopTest {
    @Test
    fun afterTheUsersStopTheNextStartIsNotTheStoppedReply() {
        // 「停止」 leaves no cursor: startReadAloud reads from the reply on screen (cursor < 0).
        assertEquals(-1, compatReadAloudCursorAfterStop())
        // 「一時停止」 keeps its cursor, which the next start resumes from.
        assertEquals(7, resolveCompatReadAloudStartIndex(requestedIndex = 7, postCount = 20))
    }
}
