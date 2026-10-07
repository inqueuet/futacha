package com.valoser.futacha.shared.ui.futaber

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FutaberThemeContractTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= 4.5f, "$label: contrast $ratio < 4.5")
    }

    private val themes = listOf(
        "light" to FutaberTokens.light, "dark" to FutaberTokens.dark,
        "oldDark" to FutaberTokens.oldDark, "futaba" to FutaberTokens.futaba
    )

    @Test
    fun everyTextRoleIsReadableOnItsBackground() {
        themes.forEach { (name, c) ->
            // Page and list rows.
            assertReadable("$name body", c.body, c.background)
            assertReadable("$name accent", c.accent, c.background)
            assertReadable("$name ordinal", c.ordinal, c.background)
            assertReadable("$name saidane", c.saidane, c.background)
            assertReadable("$name danger", c.danger, c.background)
            assertReadable("$name danger on thread page", c.danger, c.threadBackground)
            assertReadable("$name meta", c.meta, c.background)
            assertReadable("$name quote", c.quote, c.background)
            assertReadable("$name link", c.link, c.background)
            // Bars carry icons, the board address and the search cancel link.
            assertReadable("$name icon on bar", c.icon, c.bar)
            assertReadable("$name icon on top bar", c.onTopBar, c.topBar)
            assertReadable("$name title on top bar", c.topBarTitle, c.topBar)
            assertReadable("$name accent on bar", c.accent, c.bar)
            assertReadable("$name link on bar", c.link, c.bar)
            assertReadable("$name body on bar", c.body, c.bar)
            // Banner and filled controls.
            assertReadable("$name text on action", c.onAction, c.action)
            assertReadable("$name text on accent banner", c.onAction, c.accent)
            // Board list, always dark, with its facets behind the text.
            listOf(c.drawerBackground, c.drawerShape).forEach { drawerBg ->
                assertReadable("$name drawer text", c.drawerText, drawerBg)
                assertReadable("$name drawer meta", c.drawerMeta, drawerBg)
                assertReadable("$name drawer accent", c.drawerAccent, drawerBg)
            }
        }
    }

    @Test
    fun everyPosterColourIsReadableAndStablePerId() {
        themes.forEach { (name, c) ->
            futaberPosterColors(c.isDark).forEachIndexed { index, color ->
                assertReadable("$name poster colour #$index", color, c.background)
            }
        }
        assertEquals(futaberPosterColor("abcd1234", false), futaberPosterColor("abcd1234", false))
        assertEquals(futaberPosterColor("abcd1234", true), futaberPosterColor("abcd1234", true))
    }

    @Test
    fun themeModesResolveToTheirOwnPalette() {
        assertEquals(FutaberTokens.light, FutaberTokens.resolve(FutaberThemeMode.System, systemDark = false))
        assertEquals(FutaberTokens.dark, FutaberTokens.resolve(FutaberThemeMode.System, systemDark = true))
        listOf(false, true).forEach { systemDark ->
            assertEquals(FutaberTokens.light, FutaberTokens.resolve(FutaberThemeMode.Light, systemDark))
            assertEquals(FutaberTokens.dark, FutaberTokens.resolve(FutaberThemeMode.Dark, systemDark))
        }
        assertEquals(FutaberThemeMode.System, FutaberThemeMode.fromPersistedValue(null))
        assertEquals(FutaberThemeMode.System, FutaberThemeMode.fromPersistedValue("unknown"))
        FutaberThemeMode.entries.forEach {
            assertEquals(it, FutaberThemeMode.fromPersistedValue(it.persistedValue))
        }
    }

    @Test
    fun theNewThemesResolveAndRoundTrip() {
        assertEquals(FutaberTokens.oldDark, FutaberTokens.resolve(FutaberThemeMode.OldDark, systemDark = false))
        assertEquals(FutaberTokens.futaba, FutaberTokens.resolve(FutaberThemeMode.Futaba, systemDark = true))
        assertEquals(FutaberThemeMode.Futaba, FutaberThemeMode.fromPersistedValue("futaba"))
        assertEquals(FutaberThemeMode.OldDark, FutaberThemeMode.fromPersistedValue("olddark"))
        // The other themes keep one colour for the top bar and the plain bar.
        assertEquals(FutaberTokens.light.bar, FutaberTokens.light.topBar)
        assertEquals(FutaberTokens.dark.bar, FutaberTokens.dark.topBar)
    }
}
