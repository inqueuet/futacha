package com.valoser.futacha.shared.ui.ftutter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtutterColorPickerTest {
    @Test fun aColourGoesToHsvAndBack() {
        listOf(0xFF4CAF50L, 0xFF800000L, 0xFFFFFFFFL, 0xFF000000L, 0x80336699L, 0xFF2196F3L).forEach { argb ->
            assertEquals(argb, ftutterHsvOf(argb).toArgb(), argb.toString(16))
        }
        val green = ftutterHsvOf(0xFF4CAF50L)
        assertTrue(green.hue in 120f..125f)
    }

    @Test fun theNumbersAreRgbAndAlphaPercent() {
        // The original shows 返信's default green as R 76, G 175, B 80, A 100%.
        assertEquals(listOf("76", "175", "80", "100%"), ftutterRgbaLabels(0xFF4CAF50L))
        assertEquals("50%", ftutterRgbaLabels(0x80000000L)[3])
    }

    @Test fun theColourPageHasTheOriginalsSectionsAndOrder() {
        val rows = ftutterColorRows(ftutterColors(null, FtutterBrightness.LIGHT))
        assertEquals(listOf("全般", "カタログ", "スレ", "その他の機能"), rows.map { it.first })
        assertEquals(listOf("背景", "文字", "ツールバー", "ツールバー上（文字、アイコン）", "ツールバー上（アイコン有効時）", "ポップアップ",
            "ハイライト", "ここまで読んだ、見出し", "ここまで読んだ、見出し（文字）", "ジェスチャー", "スクロールバー"), rows[0].second.map { it.first })
        assertEquals(listOf("カタログ（タブ内時）", "枠線", "既読", "レス数（背景）", "レス数（文字）", "未読数（背景）", "未読数（文字）",
            "ピックアップ", "ピックアップ（文字）"), rows[1].second.map { it.first })
        assertEquals(listOf("ヘッダー", "書き込み", "返信"), rows[2].second.map { it.first })
        // Every colour that could be set before is still on the page.
        assertEquals(24, rows.sumOf { it.second.size })
    }
}
