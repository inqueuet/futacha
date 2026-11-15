package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** スレ01a1cd5d #61：タブ帯のタブは上へ払うと保護（原作どおり）、下へ払うと閉じる。 */
class FtutterTabStripSwipeTest {
    @Test fun upProtectsDownClosesAndAShortMoveDoesNothing() {
        assertEquals(FtutterTabSwipe.UP, ftutterTabStripSwipe(-60f, 50f))
        assertEquals(FtutterTabSwipe.DOWN, ftutterTabStripSwipe(60f, 50f))
        assertNull(ftutterTabStripSwipe(20f, 50f))
        assertNull(ftutterTabStripSwipe(-49f, 50f))
    }
}
