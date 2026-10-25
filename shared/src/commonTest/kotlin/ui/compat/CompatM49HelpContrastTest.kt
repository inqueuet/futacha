package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatM49HelpContrastTest {
    private val themes = listOf(null, "モノクロ", "ふたば", "ブルー", "ピンク", "ブラック")

    @Test fun theHelpEntryRemainsOpaqueAndReadableAcrossEveryThemeAndTextPreference() {
        for (theme in themes) for (preference in listOf(null, "白", "薄い灰", "濃い灰", "黒")) {
            val palette = compatibilityPaletteFor(theme, preference)
            val foreground = compatibilityHelpEntryColor(palette)
            val background = palette.background
            assertEquals(1f, foreground.alpha)
            val ratio = (maxOf(foreground.luminance(), background.luminance()) + 0.05f) /
                (minOf(foreground.luminance(), background.luminance()) + 0.05f)
            assertTrue(ratio >= 4.5f, "$theme/$preference help entry contrast=$ratio")
        }
    }

    @Test fun theBodyTextPreferenceIsPreservedEvenWhenItWouldMakeTheHelpEntryUnreadable() {
        val preferences = mapOf("白" to Color.White, "薄い灰" to Color(0xFFBDBDBD),
            "濃い灰" to Color(0xFF555555), "黒" to Color.Black)
        for (theme in themes) for ((preference, expected) in preferences) {
            val palette = compatibilityPaletteFor(theme, preference)
            compatibilityHelpEntryColor(palette)
            assertEquals(expected, palette.text, "$theme/$preference user text colour must remain intact")
        }
    }
}
