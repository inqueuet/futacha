package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** L-B2: the text styles (V28), the line icons (V04) and the sizes of the bars (V03・V05) of ふたばー風. */
class FutaberLookFoundationTest {
    private fun Typography.styles(): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to displayLarge, "displayMedium" to displayMedium, "displaySmall" to displaySmall,
        "headlineLarge" to headlineLarge, "headlineMedium" to headlineMedium, "headlineSmall" to headlineSmall,
        "titleLarge" to titleLarge, "titleMedium" to titleMedium, "titleSmall" to titleSmall,
        "bodyLarge" to bodyLarge, "bodyMedium" to bodyMedium, "bodySmall" to bodySmall,
        "labelLarge" to labelLarge, "labelMedium" to labelMedium, "labelSmall" to labelSmall
    )

    /** V28: no letter spacing anywhere (Material spaced letters by up to 0.5sp), and every style names its line height. */
    @Test
    fun theTextStylesHaveNoLetterSpacingAndAGivenLineHeight() {
        // What the mode inherited before: Material's spacing and a body line fixed at 24sp for every size.
        assertEquals(0.5.sp, Typography().bodyLarge.letterSpacing)
        futaberTypography(null).styles().forEach { (name, style) ->
            assertEquals(0f, style.letterSpacing.value, "$name letter spacing")
            assertEquals(TextUnitType.Em, style.lineHeight.type, "$name line height is relative to its size")
            assertTrue(style.lineHeight.value in 1.2f..1.45f, "$name line height ${style.lineHeight}")
            assertNotNull(style.lineHeightStyle, "$name line height style")
        }
        // A post's text: 14 on about 20 (futaber 20〜21pt for 14pt); a header, a label: 12 on about 15.
        assertEquals(FUTABER_BODY_LINE_HEIGHT, futaberTypography(null).bodyLarge.lineHeight.value)
        assertEquals(FUTABER_LABEL_LINE_HEIGHT, futaberTypography(null).labelSmall.lineHeight.value)
        assertTrue(FUTABER_BODY_LINE_HEIGHT * 14f in 20f..21f)
    }

    @Test
    fun theCustomFontIsCarriedIntoEveryStyle() {
        val font = FontFamily.Serif
        futaberTypography(font).styles().forEach { (name, style) -> assertEquals(font, style.fontFamily, name) }
        // Without one, the platform's font stays.
        futaberTypography(null).styles().zip(Typography().styles()).forEach { (mine, base) ->
            assertEquals(base.second.fontFamily, mine.second.fontFamily, mine.first)
        }
    }

    private fun ImageVector.paths(): List<VectorPath> {
        fun walk(node: VectorNode): List<VectorPath> = when (node) {
            is VectorPath -> listOf(node)
            is VectorGroup -> node.flatMap(::walk)
            else -> emptyList()
        }
        return walk(root)
    }

    /** V04: every glyph is drawn with thin lines (no filled rounded glyph is left), the filled star being the one shape. */
    @Test
    fun theIconsAreThinLinesAndTheModesOwnIconsAreKept() {
        val close = futaberIconFor(Icons.Outlined.Close)
        assertEquals("FutaberLine.Outlined.Close", close.name)
        assertTrue(futaberLineIconNames.isNotEmpty())
        (futaberLineIconNames.mapNotNull(::futaberLineIconFor) + FUTABER_OWN_ICONS).forEach { icon ->
            val strokes = icon.paths().filter { it.stroke != null }
            assertTrue(strokes.isNotEmpty(), "${icon.name} has lines")
            strokes.forEach { assertEquals(FUTABER_ICON_STROKE, it.strokeLineWidth, "${icon.name} line width") }
            assertTrue(icon.name.startsWith("Futaber"), icon.name)
        }
        // This mode's own icons are drawn as they are.
        FUTABER_OWN_ICONS.forEach { assertTrue(futaberIconFor(it) === it, it.name) }
        // The star shows its state by fill, as the original's: a line star, then a filled one.
        assertTrue(futaberIconFor(Icons.Outlined.StarOutline).paths().none { it.fill != null })
        assertTrue(futaberIconFor(Icons.Outlined.Star).paths().any { (it.fill as? SolidColor) != null })
        assertTrue(FUTABER_ICON_STROKE <= 1.3f)
    }

    /** V03・V05: the bars' heights, and where the manage panel starts on each screen. */
    @Test
    fun theBarsHaveTheOriginalsSizes() {
        assertEquals(44, FUTABER_BOTTOM_BAR_HEIGHT_DP)
        assertEquals(44, FUTABER_TOP_BAR_HEIGHT_DP)
        assertEquals(20, FUTABER_THREAD_TITLE_ROW_DP)
        assertEquals(44, FUTABER_TAB_STRIP_HEIGHT_DP)
        assertEquals(FUTABER_THREAD_TITLE_ROW_DP, futaberManagePanelTopDp(threadShown = true))
        assertEquals(FUTABER_TOP_BAR_HEIGHT_DP, futaberManagePanelTopDp(threadShown = false))
    }
}
