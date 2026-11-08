package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** D-B6 (J6): one delete key for all the modes. */
class FtutterDeleteKeySyncTest {
    private fun sync(own: String?, shared: String?, last: String?) = ftutterDeleteKeySync(own, shared, last) { "NEWKEY12" }

    @Test
    fun aFreshInstallMakesOneKeyForBoth() = assertEquals(FtutterDeleteKeySync("NEWKEY12", "NEWKEY12"), sync(null, " ", null))

    @Test
    fun theSharedKeyWinsAtTheStartAndFillsTheEmptySide() {
        assertEquals(FtutterDeleteKeySync(own = "common"), sync("mine", "common", null))
        assertEquals(FtutterDeleteKeySync(own = "common"), sync(null, "common", null))
        assertEquals(FtutterDeleteKeySync(shared = "mine"), sync("mine", null, null))
        assertEquals(FtutterDeleteKeySync(), sync("same", "same", null))
    }

    @Test
    fun afterwardsWhicheverSideMovedWins() {
        // Another mode changed the shared key: this one follows.
        assertEquals(FtutterDeleteKeySync(own = "theirs"), sync("old", "theirs", "old"))
        // This mode's key was changed: the shared one follows.
        assertEquals(FtutterDeleteKeySync(shared = "edited"), sync("edited", "old", "old"))
    }
}
