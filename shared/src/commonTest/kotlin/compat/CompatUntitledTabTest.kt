package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit round 5, N1: the placeholder of a tab made before its history row existed is not a title worth keeping. */
class CompatUntitledTabTest {
    private val url = "https://may.2chan.net/b/res/1.htm"
    private fun tab(title: String) = CompatTab("k", url, url, "b", "may", "1", title, insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L)

    @Test fun reopeningWithARealTitleReplacesThePlaceholder() {
        assertEquals("本当の題名", mergeCompatReopenedTab(tab("(無題)"), tab("本当の題名")).title)
    }

    @Test fun aBlankCandidateLeavesThePlaceholder() {
        assertEquals("(無題)", mergeCompatReopenedTab(tab("(無題)"), tab("")).title)
    }

    @Test fun aRealTitleIsStillKept() {
        assertEquals("前の題名", mergeCompatReopenedTab(tab("前の題名"), tab("本当の題名")).title)
    }
}
