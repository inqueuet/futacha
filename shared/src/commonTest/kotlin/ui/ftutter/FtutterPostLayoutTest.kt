package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals

class FtutterPostLayoutTest {
    @Test fun theSummaryIsTheOriginalsSixLines() {
        assertEquals("上：5\n左：5\n右：5\n下：5\nヘッダーと本文の間：3\n本文と画像の間：8", ftutterPostLayoutSummary(emptyMap()))
    }

    @Test fun brokenOrOutOfRangeValuesFallBackToTheDefault() {
        val rows = FTUTTER_POST_LAYOUT_ROWS
        val p = mapOf(FtutterKeys.PADDING_TOP to "12", FtutterKeys.PADDING_LEFT to "x", FtutterKeys.PADDING_RIGHT to "31",
            FtutterKeys.PADDING_BOTTOM to "-1", FtutterKeys.PADDING_HEADER_TEXT to "7.0", FtutterKeys.PADDING_TEXT_FILE to "NaN")
        assertEquals(listOf(12, 5, 5, 5, 7, 8), rows.map { ftutterPostLayoutValue(p, it) })
    }
}
