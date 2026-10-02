package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.valoser.futacha.shared.model.ThemePalette
import com.valoser.futacha.shared.ui.theme.resolveFutachaColorScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Round 3 U-8: the summary provider label used `outline` (about 4:1), below AGENTS.md's 4.5:1.
class ThreadSummaryContrastTest {
    private fun contrastRatio(foreground: Color, background: Color): Float {
        val fg = foreground.compositeOver(background).luminance()
        val bg = background.luminance()
        return (maxOf(fg, bg) + 0.05f) / (minOf(fg, bg) + 0.05f)
    }

    @Test
    fun providerLabelIsReadableOnTheSummaryCardInEveryPalette() {
        ThemePalette.entries.forEach { palette ->
            listOf(false, true).forEach { dark ->
                val base = resolveFutachaColorScheme(dark, palette)
                listOf(base, resolveFutabaThreadColorScheme(palette, base)).forEach { colors ->
                    val label = threadSummaryProviderLabelColor(colors)
                    assertEquals(1f, label.alpha, "$palette dark=$dark label must be opaque")
                    val card = colors.surfaceColorAtElevation(THREAD_SUMMARY_CARD_ELEVATION)
                    val ratio = contrastRatio(label, card)
                    assertTrue(ratio >= 4.5f, "$palette dark=$dark provider label: $ratio on $card")
                }
            }
        }
    }
}
