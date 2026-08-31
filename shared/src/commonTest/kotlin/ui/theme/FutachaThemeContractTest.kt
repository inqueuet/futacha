package com.valoser.futacha.shared.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.ui.board.resolveFutabaThreadColorScheme
import com.valoser.futacha.shared.ui.board.resolveFutabaThreadColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FutachaThemeContractTest {
    private fun contrastRatio(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    @Test
    fun systemModeFollowsDayNightWhileExplicitModesRemainFixed() {
        assertFalse(resolveFutachaDarkMode(ThemeMode.System, systemDarkTheme = false))
        assertTrue(resolveFutachaDarkMode(ThemeMode.System, systemDarkTheme = true))
        listOf(false, true).forEach { systemDarkTheme ->
            assertFalse(resolveFutachaDarkMode(ThemeMode.Light, systemDarkTheme))
            assertTrue(resolveFutachaDarkMode(ThemeMode.Dark, systemDarkTheme))
        }
    }

    @Test
    fun everyPaletteHasIndependentLightAndDarkColorContracts() {
        val expectedBackgrounds = mapOf(
            ThemePalette.Current to (Color(0xFFF4F2EA) to Color(0xFF151712)),
            ThemePalette.FutabaClassic to (Color(0xFFFFFFEE) to Color(0xFF201614)),
            ThemePalette.FutabaBlack to (Color(0xFFFFFFEE) to Color(0xFF17110F)),
            ThemePalette.Midnight to (Color(0xFFF2F6FA) to Color(0xFF081019))
        )

        expectedBackgrounds.forEach { (palette, expected) ->
            val light = resolveFutachaColorScheme(useDarkTheme = false, palette = palette)
            val dark = resolveFutachaColorScheme(useDarkTheme = true, palette = palette)

            assertEquals(expected.first, light.background, "$palette light background changed")
            assertEquals(expected.second, dark.background, "$palette dark background changed")
            assertNotEquals(light.background, dark.background, "$palette lost its dark branch")
            assertNotEquals(light.background, light.onBackground, "$palette light text became invisible")
            assertNotEquals(dark.background, dark.onBackground, "$palette dark text became invisible")
        }
    }

    @Test
    fun futabaBlackAlwaysUsesBlackChromeAndLightChromeContent() {
        listOf(false, true).forEach { useDarkTheme ->
            val colors = resolveFutachaColorScheme(useDarkTheme, ThemePalette.FutabaBlack)
            val chrome = resolveFutachaChromeColors(colors, useDarkTheme, ThemePalette.FutabaBlack)

            assertEquals(Color(0xFF050505), chrome.systemBar)
            assertEquals(Color(0xFF050505), chrome.topBar)
            assertEquals(Color(0xFF050505), chrome.bottomBar)
            assertEquals(Color(0xFFF4EFE6), chrome.onBar)
            assertFalse(chrome.useDarkSystemBarIcons)
        }
    }

    @Test
    fun nonBlackLightThemesUseDarkSystemBarIconsOnlyInLightMode() {
        ThemePalette.entries.filterNot { it == ThemePalette.FutabaBlack }.forEach { palette ->
            val light = resolveFutachaColorScheme(false, palette)
            val dark = resolveFutachaColorScheme(true, palette)

            assertTrue(resolveFutachaChromeColors(light, false, palette).useDarkSystemBarIcons)
            assertFalse(resolveFutachaChromeColors(dark, true, palette).useDarkSystemBarIcons)
        }
    }

    @Test
    fun classicThreadKeepsLegacyDayColorsButNeverForcesThemIntoNightMode() {
        val lightBase = resolveFutachaColorScheme(false, ThemePalette.FutabaClassic)
        val darkBase = resolveFutachaColorScheme(true, ThemePalette.FutabaClassic)
        val lightThread = resolveFutabaThreadColorScheme(ThemePalette.FutabaClassic, lightBase)
        val darkThread = resolveFutabaThreadColorScheme(ThemePalette.FutabaClassic, darkBase)

        assertEquals(Color(0xFFFFFFEE), lightThread.background)
        assertEquals(Color(0xFFF0E0D6), lightThread.surface)
        assertEquals(darkBase, darkThread)
        assertEquals(Color(0xFF201614), darkThread.background)
        assertEquals(Color(0xFF281B19), darkThread.surface)
    }

    @Test
    fun everyPaletteThreadHeaderBodyQuoteAndLinkTokensRemainReadableInDayAndNight() {
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val base = resolveFutachaColorScheme(dark, palette)
                val scheme = resolveFutabaThreadColorScheme(palette, base)
                val tokens = resolveFutabaThreadColors(palette, base)
                val labels = mapOf(
                    "footer" to tokens.footerText,
                    "accent" to tokens.accent,
                    "author" to tokens.author,
                    "quote" to tokens.quote,
                    "link" to tokens.link
                )
                labels.forEach { (label, color) ->
                    assertTrue(
                        // Futaba's historical #789922 quote green is 2.7:1 on
                        // #F0E0D6. Preserve that exact day-theme contract while
                        // ensuring night branches never fall below it.
                        contrastRatio(color, scheme.surface) >= 2.5f,
                        "$palette dark=$dark $label is unreadable on the thread surface"
                    )
                }
                assertTrue(
                    contrastRatio(scheme.onSurface, scheme.surface) >= 4.5f,
                    "$palette dark=$dark body text is unreadable"
                )
            }
        }
    }
}
