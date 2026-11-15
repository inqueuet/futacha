package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** スレ1件目の報告：ふたったー風のタブ・履歴からあいもげのスレを開くと「登録していない板です」と出た。 */
class FtutterAimogeBoardForUrlTest {
    @BeforeTest fun on() { AimogeAvailability.sitesEnabled = true }
    @AfterTest fun off() { AimogeAvailability.sitesEnabled = false }

    private val boards = listOf(
        BoardSummary(id = "img", name = "img", category = "", url = "https://img.2chan.net/b/futaba.php", description = ""),
        BoardSummary(id = "aimoge-aimg", name = "aimg", category = "", url = "https://nijiurachan.net/b/aimg/", description = ""),
    )

    @Test fun anAimogeThreadUrlFindsItsBoard() {
        val url = "https://nijiurachan.net/b/aimg/thread/01a1cd5d-1f35-7211-95fd-169bc7e91c39"
        assertEquals("aimoge-aimg", ftutterBoardForUrl(boards, url)?.id)
        assertEquals("01a1cd5d-1f35-7211-95fd-169bc7e91c39", ftutterThreadIdFromUrl(url))
    }

    @Test fun futabaThreadsAndUnknownAimogeBoardsKeepTheirAnswers() {
        assertEquals("img", ftutterBoardForUrl(boards, "https://img.2chan.net/b/res/123.htm")?.id)
        assertNull(ftutterBoardForUrl(boards, "https://nijiurachan.net/b/other/thread/01a1cd5d-1f35-7211-95fd-169bc7e91c39"))
    }
}
