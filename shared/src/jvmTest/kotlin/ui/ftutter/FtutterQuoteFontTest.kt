package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtutterQuoteFontTest {
    @Test
    fun theQuoteGreenFollowsTheFontLikeTheOriginal() {
        // 公式のHTML：引用の行は<font color="#789922">。
        val html = "<font color=\"#789922\">&gt;ねこ</font><br>かわいい<br>&gt;fontの無い行<br><font color='#789922'>緑の字</font>"
        assertEquals(setOf(0, 3), ftutterQuoteFontLines(html, 4))
        // 行の数が合わない時は今までの決め方に任せる。
        assertNull(ftutterQuoteFontLines(html, 3))
        assertEquals(emptySet(), ftutterQuoteFontLines("&gt;a<br>b", 2))
    }
}
