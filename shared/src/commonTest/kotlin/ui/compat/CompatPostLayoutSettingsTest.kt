package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals

class CompatPostLayoutSettingsTest {
    @Test fun theHeaderSizeAndTheGapReadAsNumbersAndKeepTheDefaultWord() {
        assertEquals("標準", compatPreferenceSummaryValue("threadPostSpacing", "標準"))
        assertEquals("標準", compatPreferenceSummaryValue("threadHeaderFontSize", "標準"))
        assertEquals("4dp", compatPreferenceSummaryValue("threadPostSpacing", "4"))
        assertEquals("12sp", compatPreferenceSummaryValue("threadHeaderFontSize", "12"))
        assertEquals("0dp", compatPreferenceSummaryValue("threadPostSpacing", "0"))
    }

    @Test fun theGapIsTheBlankUnderAPostSoItCanBeNarrowerThanTheStandard() {
        assertEquals(0 to 0, compatPostGapTrimDp(null) to compatPostGapExtraDp(null))
        assertEquals(8 to 0, compatPostGapTrimDp(0) to compatPostGapExtraDp(0))
        assertEquals(4 to 0, compatPostGapTrimDp(4) to compatPostGapExtraDp(4))
        assertEquals(0 to 0, compatPostGapTrimDp(8) to compatPostGapExtraDp(8))
        assertEquals(0 to 12, compatPostGapTrimDp(20) to compatPostGapExtraDp(20))
    }

    @Test fun theInfoDialogShowsThePostsWords() {
        assertEquals("本文\n二行目", compatViewerInfoComment("本文<br>二行目"))
        assertEquals("", compatViewerInfoComment("  "))
    }

    @Test fun theThreadTitleSizeIsAFontSettingFrom12To20() {
        val entry = CompatSettingEntry("スレッドのタイトルの文字サイズ", "標準", preferenceKey = "threadTitleFontSize")
        assertEquals(listOf("標準") + (12..20).map(Int::toString), compatPreferenceOptions("thread", entry))
        assertEquals("16sp", compatPreferenceSummaryValue("threadTitleFontSize", "16"))
        assertEquals("標準", compatPreferenceSummaryValue("threadTitleFontSize", "標準"))
    }

    @Test fun thePrivacyBlurIsChosenApartFromTheDarkness() {
        assertEquals(0f, compatPrivacyBlurRadiusDp(0.2f, CompatPrivacyBlur.OFF))
        assertEquals(12f, compatPrivacyBlurRadiusDp(1f, CompatPrivacyBlur.ON))
        assertEquals(0f, compatPrivacyBlurRadiusDp(1f, CompatPrivacyBlur.FOLLOW))
        assertEquals(14.4f, compatPrivacyBlurRadiusDp(0.2f, CompatPrivacyBlur.FOLLOW), 0.01f)
        assertEquals(CompatPrivacyBlur.OFF, CompatPrivacyBlur.of("入れない"))
        assertEquals(CompatPrivacyBlur.FOLLOW, CompatPrivacyBlur.of(null))
    }
}
