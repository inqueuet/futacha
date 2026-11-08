package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals

/** The writing screen's "カナ" converts as the original did on the device (docs/thread-feedback-1444136530-2026-10-27.md §4). */
class FutaberKanaTest {
    @Test fun hiraganaBecomesHalfWidthKatakanaAsInTheOriginal() {
        assertEquals("ｽｹﾍﾞｼﾏﾙ", futaberToHalfWidthKana("すけべしまる"))
    }

    @Test fun katakanaVoicedAndSmallLettersAreSplitAndKept() {
        assertEquals("ｶﾞｷﾞｸﾞ ﾊﾟﾋﾟﾌﾟ ｳﾞｧｲｵﾘﾝ ｯｬｭｮｧ", futaberToHalfWidthKana("ガギグ パピプ ヴァイオリン ッャュョァ"))
        assertEquals("ﾜﾞｦﾞ", futaberToHalfWidthKana("ヷヺ"))
    }

    @Test fun punctuationWithAHalfWidthFormIsConverted() {
        assertEquals("｢ﾃｽﾄ｣､ｰ･｡", futaberToHalfWidthKana("「テスト」、ー・。"))
        // Combining and spacing sound marks.
        assertEquals("ｶﾞﾞﾟ", futaberToHalfWidthKana("ガ゛゜"))
    }

    @Test fun everythingWithoutAHalfWidthFormStays() {
        assertEquals("漢字ABCabc123！？ヶヵヮゎヰヱゝ\n", futaberToHalfWidthKana("漢字ABCabc123！？ヶヵヮゎヰヱゝ\n"))
        assertEquals("", futaberToHalfWidthKana(""))
        // Already half-width text is left as it is.
        assertEquals("ｽｹﾍﾞｼﾏﾙ", futaberToHalfWidthKana("ｽｹﾍﾞｼﾏﾙ"))
    }
}
