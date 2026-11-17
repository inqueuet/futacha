package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaLoadFailureSupportTest {
    @Test
    fun mediaFailureShowsBoundedSingleLineCauseChain() {
        val failure = IllegalStateException(
            "decode\nfailed",
            IllegalArgumentException("unsupported format")
        )

        assertEquals(
            "理由: decode failed / unsupported format",
            formatMediaLoadFailure(failure)
        )
        assertNull(formatMediaLoadFailure(null))
        assertTrue(formatMediaLoadFailure(IllegalStateException("x".repeat(400)))!!.length <= 224)
    }
}
