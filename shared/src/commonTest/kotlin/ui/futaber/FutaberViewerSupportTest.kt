package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class FutaberViewerSupportTest {
    @Test fun counterIsOneBasedLikeTheOriginalApp() {
        assertEquals("1 of 9", futaberViewerCounter(0, 9))
        assertEquals("9 of 9", futaberViewerCounter(8, 9))
    }

    @Test fun headerJoinsPositionTimeIdAndNumber() {
        val post = CompatPostSnapshot(position = 16, postNo = "325019", timestamp = "26/08/31(月)07:49:43", posterId = "IP:133.149.*(aitai.ne.jp)", messageHtml = "")
        assertEquals("16 26/08/31(月)07:49:43 IP:133.149.*(aitai.ne.jp) No.325019", futaberViewerHeader(post))
        // An ID already inside the timestamp text is not repeated.
        assertEquals(
            "0 25/11/08(土)13:42:53 ID:Mua720AI No.5",
            futaberViewerHeader(post.copy(position = 0, postNo = "5", timestamp = "25/11/08(土)13:42:53 ID:Mua720AI", posterId = "ID:Mua720AI"))
        )
        assertEquals("1 26/08/31(月)07:49:43 No.1", futaberViewerHeader(post.copy(position = 1, postNo = "1", posterId = null)))
    }
}
