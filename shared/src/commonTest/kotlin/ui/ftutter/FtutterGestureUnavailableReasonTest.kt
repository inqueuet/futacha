package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

/** L17・L23: why an operation chosen from the gesture menu did nothing. */
class FtutterGestureUnavailableReasonTest {
    @Test fun eachOperationNamesWhatIsMissing() {
        assertEquals("前のタブがありません", ftutterGestureUnavailableReason(FtutterGestureAction.PREVIOUS, isThread = true))
        assertEquals("次のタブがありません", ftutterGestureUnavailableReason(FtutterGestureAction.NEXT, isThread = true))
        assertEquals("次のお気に入りの板がありません", ftutterGestureUnavailableReason(FtutterGestureAction.NEXT, isThread = false))
        assertEquals("お気に入りの板がありません",
            ftutterGestureUnavailableReason(FtutterGestureAction.PREVIOUS, isThread = false, hasFavorites = false))
        assertEquals("閉じたタブはありません", ftutterGestureUnavailableReason(FtutterGestureAction.REOPEN_TAB, isThread = false))
        assertEquals("タブがありません", ftutterGestureUnavailableReason(FtutterGestureAction.MOST_RIGHT, isThread = true))
        assertEquals("閉じるタブがありません", ftutterGestureUnavailableReason(FtutterGestureAction.CLOSE_TAB, isThread = false))
        assertEquals("ドロワーは設定で無効です", ftutterGestureUnavailableReason(FtutterGestureAction.OPEN_DRAWER, isThread = true))
    }
}
