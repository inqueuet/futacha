package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Audit 2026-11-08 C5・C7・C8. */
class FtutterLowAuditTest {
    @Test fun theSendingScrimGoesAfterItsLimit() {
        assertTrue(ftutterRemoteScrimShown(busy = true, expired = false))
        assertFalse(ftutterRemoteScrimShown(busy = true, expired = true))
        assertFalse(ftutterRemoteScrimShown(busy = false, expired = false))
    }

    @Test fun theAddedNgRuleIsFoundInALongList() {
        val before = (0 until 5_000).map { FtutterNgRule("w$it", 0) }
        val added = FtutterNgRule("新しい語", 0)
        assertEquals("新しい語 を追加しました", ftutterNgAddedNotice(before, before + added))
        assertNull(ftutterNgAddedNotice(before, before))
    }

    @Test fun twoPanesKeepTheThreadTheCatalogWouldOpenAgain() {
        assertTrue(ftutterTwoPaneKeepsThread(true, false, "b", "b", reopenId = "123", shownThreadId = "123"))
        assertFalse(ftutterTwoPaneKeepsThread(false, false, "b", "b", "123", "123"), "one pane: the catalog is shown")
        assertFalse(ftutterTwoPaneKeepsThread(true, true, "b", "b", "123", "123"), "the legacy empty pane")
        assertFalse(ftutterTwoPaneKeepsThread(true, false, "b", "other", "123", "123"), "another board's catalog")
        assertFalse(ftutterTwoPaneKeepsThread(true, false, "b", "b", null, "123"), "nothing would be opened beside it")
        assertFalse(ftutterTwoPaneKeepsThread(true, false, "b", "b", "456", "123"), "another thread would be opened")
    }
}
