package com.valoser.futacha.shared.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.ui.board.futachaCatalogReadBackground
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** may 1449174385: the read-thread tint of the ふたちゃ catalog keeps every catalog text colour at 4.5:1. */
class FutachaCatalogReadColorContractTest {
    private fun ratio(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    @Test
    fun theReadTintKeepsTheCatalogTextReadableInEveryPalette() {
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val colors = resolveFutachaColorScheme(dark, palette)
                val read = futachaCatalogReadBackground(colors)
                assertNotEquals(colors.surface, read, "$palette dark=$dark: the tint must be visible")
                mapOf("title" to colors.onSurface, "supporting" to colors.onSurfaceVariant,
                    "count" to colors.primary, "watch" to colors.error).forEach { (role, text) ->
                    assertTrue(ratio(text, read) >= 4.5f, "$palette dark=$dark $role: ${ratio(text, read)}")
                }
            }
        }
    }
}

class FutachaCatalogReadColorHueTest {
    @Test
    fun theReadTintLeansLightBlueAndIsReportedWhenTooFaint() {
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val colors = resolveFutachaColorScheme(dark, palette)
                val read = futachaCatalogReadBackground(colors)
                // blue rises relative to red against the plain card: the hue moves toward light blue
                assertTrue(read.blue - read.red > colors.surface.blue - colors.surface.red, "$palette dark=$dark")
                // every palette reaches a clearly visible tint, so no extra 「既読」 mark is needed
                val distance = com.valoser.futacha.shared.ui.board.futachaCatalogReadDistance(colors)
                assertTrue(distance >= com.valoser.futacha.shared.ui.board.FUTACHA_CATALOG_READ_MIN_DISTANCE, "$palette dark=$dark: $distance")
            }
        }
    }
}
