package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.compat.CompatTouchScrollAction
import com.valoser.futacha.shared.ui.compat.compatTouchScrollAction
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatTouchScrollTest {
    @Test
    fun upperAndLowerThirdsPageWithoutChangingCenterTaps() {
        assertEquals(CompatTouchScrollAction.PAGE_UP, compatTouchScrollAction(99f, 300f))
        assertEquals(CompatTouchScrollAction.NONE, compatTouchScrollAction(150f, 300f))
        assertEquals(CompatTouchScrollAction.PAGE_DOWN, compatTouchScrollAction(250f, 300f))
        assertEquals(CompatTouchScrollAction.NONE, compatTouchScrollAction(-1f, 300f))
    }

    // 参考アプリ はタップ（指がほぼ動かない）の時だけ1画面送る。ドラッグの後には送らない（あいもげスレ01a1e1d7）。
    @kotlin.test.Test
    fun onlyATapPages() {
        kotlin.test.assertFalse(com.valoser.futacha.shared.ui.compat.compatTouchScrollMoved(androidx.compose.ui.geometry.Offset(3f, 4f), 18f))
        kotlin.test.assertTrue(com.valoser.futacha.shared.ui.compat.compatTouchScrollMoved(androidx.compose.ui.geometry.Offset(0f, 300f), 18f))
        kotlin.test.assertTrue(com.valoser.futacha.shared.ui.compat.compatTouchScrollMoved(androidx.compose.ui.geometry.Offset(15f, 15f), 18f))
    }
}
