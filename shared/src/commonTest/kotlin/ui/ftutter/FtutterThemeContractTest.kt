package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FtutterThemeContractTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val a = foreground.luminance() + 0.05f
        val b = background.luminance() + 0.05f
        return maxOf(a, b) / minOf(a, b)
    }

    /** `RRGGBB`（共通コードで動く書き方。String.format はJVMだけ）。 */
    private fun hex6(argb: Int): String = (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= 4.5f, "$label: contrast $ratio < 4.5")
    }

    private fun assertNear(label: String, expected: Long, actual: Color, tolerance: Int = 2) {
        val e = expected.toInt()
        val a = actual.toArgb()
        listOf(16, 8, 0).forEach { shift ->
            val diff = abs(((e shr shift) and 0xFF) - ((a shr shift) and 0xFF))
            assertTrue(diff <= tolerance, "$label: expected #${hex6(e)}, got #${hex6(a)}")
        }
    }

    @Test
    fun everyMainColourHasALightAndADarkScheme() {
        assertEquals(19, FTUTTER_SEED_COLORS.size)
        assertEquals(19, FTUTTER_LIGHT_SCHEMES.size)
        assertEquals(19, FTUTTER_DARK_SCHEMES.size)
        (FTUTTER_LIGHT_SCHEMES + FTUTTER_DARK_SCHEMES).forEach { assertEquals(35, it.size) }
        assertEquals(FTUTTER_DEFAULT_MAIN_COLOR, FTUTTER_SEED_COLORS[5])
        assertEquals(FTUTTER_FUTABA_MAIN_COLOR, FTUTTER_SEED_COLORS.last())
    }

    @Test
    fun theDefaultBlueMatchesTheOriginalScreenshots() {
        // Pixels of docs/references/ftutter-2026-10-13/46-dedicated-catalog.png (ふたったー 3.1.1, default settings).
        val colors = ftutterColors(null, FtutterBrightness.LIGHT)
        assertNear("toolbar", 0xFFEBEFF7L, colors.toolBar)
        assertNear("background", 0xFFF8F9FFL, colors.background)
    }

    @Test
    fun theFutabaPaletteAppliesOnlyInLightMode() {
        val futaba = ftutterColors(FTUTTER_FUTABA_MAIN_COLOR, FtutterBrightness.LIGHT)
        assertEquals(Color(0xFFF0E0D6), futaba.background)
        assertEquals(Color(0xFF800000), futaba.text)
        assertEquals(Color(0xFFFFFFEE), futaba.toolBar)
        assertEquals(Color(0xFF800000), futaba.onToolBar)
        assertEquals(Color(0xFFEEAA88), futaba.dividerCard)
        val dark = ftutterColors(FTUTTER_FUTABA_MAIN_COLOR, FtutterBrightness.DARK)
        assertTrue(dark.background != Color(0xFFF0E0D6))
        assertEquals(Color.Black, ftutterColors(null, FtutterBrightness.BLACK).background)
    }

    @Test
    fun everyTextRoleIsReadableForEveryMainColourAndBrightness() {
        FTUTTER_SEED_COLORS.forEach { seed ->
            FtutterBrightness.entries.forEach { brightness ->
                val c = ftutterColors(seed, brightness)
                val label = "#${hex6(seed.toInt())}/$brightness"
                assertReadable("$label text", c.text, c.background)
                assertReadable("$label toolbar", c.onToolBar, c.toolBar)
                assertReadable("$label popup", c.text, c.popUp)
                assertReadable("$label divider card", c.onDividerCard, c.dividerCard)
                assertReadable("$label unread badge", c.onMidoku, c.midoku)
                assertReadable("$label reply count badge", c.onResuSuu, c.resuSuu)
                // ヘッダー（名前・日時）は本文より控えめな outline 色。操作文字ではないが、読める濃さは保つ。
                assertTrue(contrast(c.headerText, c.background) >= 3.0f, "$label header")
            }
        }
    }

    @Test
    fun theActiveTogglePillKeepsTheIconVisibleAndTheNotesReadable() {
        FTUTTER_SEED_COLORS.forEach { seed ->
            FtutterBrightness.entries.forEach { brightness ->
                val c = ftutterColors(seed, brightness)
                val label = "#${hex6(seed.toInt())}/$brightness"
                // 選んだボタンのアイコン（文字でない図形は3:1。WCAG 1.4.11）。カプセルを敷いた後も保つ。
                assertTrue(contrast(c.onToolBarActive, ftutterActiveIndicatorOnBar(c)) >= 3.0f, "$label active icon on pill")
                assertTrue(contrast(c.onToolBar, c.toolBar) >= 3.0f, "$label idle icon")
                // 設定の見出し（強調色）は背景に対して4.5:1。行の補足・値は本文の色（上の text）で書く。
                assertReadable("$label settings section", c.onToolBarActive, c.background)
            }
        }
    }

    @Test
    fun brokenOrUnknownSettingsFallBackToTheOriginalDefaults() {
        val defaults = parseFtutterDesign(emptyMap())
        assertEquals(FTUTTER_DEFAULT_MAIN_COLOR, defaults.mainColor)
        assertEquals(FtutterBrightness.LIGHT, defaults.brightness)
        assertEquals(15, defaults.catalogFontSize)
        assertEquals(14, defaults.catalogNumberFontSize)
        assertEquals(13, defaults.resuHeaderFontSize)
        assertEquals(15, defaults.resuContentFontSize)
        assertEquals(0.8, defaults.privacyThread)
        assertTrue(defaults.disableStretchAnimation)
        val broken = parseFtutterDesign(
            mapOf(
                FtutterKeys.MAIN_COLOR to "12345", // not one of the 19 colours
                FtutterKeys.THEME_MODE to "9",
                FtutterKeys.CATALOG_FONT_SIZE to "99",
                FtutterKeys.PRIVACY_THREAD to "NaN",
                FtutterKeys.colorOverride("text") to "not a colour"
            )
        )
        assertEquals(FTUTTER_DEFAULT_MAIN_COLOR, broken.mainColor)
        assertEquals(FtutterBrightness.LIGHT, broken.brightness)
        assertEquals(15, broken.catalogFontSize)
        assertEquals(0.8, broken.privacyThread)
        assertEquals(null, broken.overrides.text)
    }

    @Test
    fun storedValuesAreRead() {
        val design = parseFtutterDesign(
            mapOf(
                FtutterKeys.MAIN_COLOR to "#800000",
                FtutterKeys.THEME_MODE to "2",
                FtutterKeys.RESU_CONTENT_FONT_SIZE to "20",
                FtutterKeys.colorOverride("text") to "#FF112233"
            )
        )
        assertEquals(FTUTTER_FUTABA_MAIN_COLOR, design.mainColor)
        assertEquals(FtutterBrightness.BLACK, design.brightness)
        assertEquals(20, design.resuContentFontSize)
        assertEquals(0xFF112233L, design.overrides.text)
        assertEquals(Color(0xFF112233), ftutterColors(design.mainColor, design.brightness, design.overrides).text)
    }
}
