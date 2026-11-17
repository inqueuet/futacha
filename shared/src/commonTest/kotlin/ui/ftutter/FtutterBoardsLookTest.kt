package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterBoardsLookTest {
    private val neko = BoardSummary(id = "neko", name = "ねこ", category = "", url = "https://may.2chan.net/27/futaba.php", description = "")

    @Test fun openFromUrlAddsOnlyAFutabaBoard() {
        // スレッドのURLはその板を足す。板のURLも同じ。
        assertEquals("https://may.2chan.net/b/futaba.php", ftutterOpenUrlBoard("https://may.2chan.net/b/res/123.htm", listOf(neko)))
        assertEquals("https://may.2chan.net/b/futaba.php", ftutterOpenUrlBoard("http://may.2chan.net/b/futaba.htm", listOf(neko)))
        // 原作で「URLが対応していない形式です」の物。
        assertNull(ftutterOpenUrlBoard("https://www.example.com/t/", listOf(neko)))
        assertNull(ftutterOpenUrlBoard("abc", listOf(neko)))
        assertNull(ftutterOpenUrlBoard("https://www.2chan.net/", listOf(neko)))
    }

    @Test fun theLegacySwitchIsOffByDefault() {
        assertFalse(ftutterLegacyBoards(emptyMap()))
        assertTrue(ftutterLegacyBoards(mapOf(FTUTTER_LEGACY_BOARDS_KEY to "true")))
    }

    @Test fun theMenuAccentIsReadableOnThePopupForEveryColour() {
        FTUTTER_SEED_COLORS.forEach { seed ->
            FtutterBrightness.entries.forEach { brightness ->
                val c = ftutterColors(seed, brightness)
                assertTrue(ftutterContrast(c.menuAccent(), c.popUp) >= 4.5f, "#${seed.toString(16)}/$brightness")
            }
        }
        // 既定の青は原作どおりメインカラー。
        val blue = ftutterColors(null, FtutterBrightness.LIGHT)
        assertEquals(blue.highlight, blue.menuAccent())
    }
}
