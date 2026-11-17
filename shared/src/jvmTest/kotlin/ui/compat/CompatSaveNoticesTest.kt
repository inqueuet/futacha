package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** L143: a notice arriving while another is shown waits behind it instead of replacing it. */
class CompatSaveNoticesTest {
    @Test fun resultsAreShownInTurn() {
        val notices = CompatSaveNotices()
        notices.offer("スレッドを保存しました")
        notices.offer("画像を保存しました")
        assertEquals("スレッドを保存しました", notices.current)
        notices.dismiss()
        assertEquals("画像を保存しました", notices.current)
        notices.dismiss()
        assertNull(notices.current)
    }

    @Test fun aLongRunKeepsTheShownOneAndTheNewest() {
        val notices = CompatSaveNotices()
        repeat(50) { notices.offer("保存$it") }
        assertEquals("保存0", notices.current)
        var last: String? = null
        while (notices.current != null) { last = notices.current; notices.dismiss() }
        assertEquals("保存49", last)
    }

    @Test fun generalNoticesWaitInTurnInsteadOfReplacingOneUnseen() {
        val notices = CompatSaveNotices()
        notices.offer("スレッドを保存しました")
        notices.offerTimed("巡回しました")
        notices.offerTimed("タブを更新しました")
        assertEquals("スレッドを保存しました", notices.current)
        assertTrue(notices.currentSticky)
        notices.dismiss()
        assertEquals("巡回しました", notices.current)
        assertFalse(notices.currentSticky)
        notices.dismiss(notices.currentToken) // its time ran out
        assertEquals("タブを更新しました", notices.current)
    }

    @Test fun aTimerOfANoticeAlreadyClosedDoesNotCloseTheNextOne() {
        val notices = CompatSaveNotices()
        notices.offerTimed("A")
        val first = notices.currentToken
        notices.offerTimed("B")
        notices.dismiss() // closed by a tap
        notices.dismiss(first) // A's timer ends late
        assertEquals("B", notices.current)
    }

    @Test fun theSameGeneralNoticeRightBehindItselfIsShownOnce() {
        val notices = CompatSaveNotices()
        notices.offerTimed("タブを全て更新中です")
        notices.offerTimed("タブを全て更新中です")
        notices.dismiss()
        assertNull(notices.current)
    }
}
