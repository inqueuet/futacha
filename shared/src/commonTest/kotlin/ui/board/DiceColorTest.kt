package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiceColorTest {
    @Test
    fun futabaDiceResultIsADiceRange() {
        val html = "振るよ<br>dice2d100=<font color=\"#ff0000\">73 75 (148)</font>"
        val ranges = aimogeTextDecorations(html)
        assertEquals(1, ranges.size)
        assertEquals(AimogeDecorationKind.DICE, ranges[0].kind)
        assertEquals("73 75 (148)", ranges[0].text)
    }

    @Test
    fun aRedFontThatIsNotADiceStaysPlain() {
        assertNull(aimogeLineDecorations("dice<font color=\"#ff0000\">赤字</font>"))
        assertNull(aimogeLineDecorations("<font color=\"#789922\">&gt;引用</font>"))
    }

    @Test
    fun theChosenColourColoursTheResult() {
        val html = "dice1d6=<font color=\"#ff0000\">4 (4)</font>"
        val text = AnnotatedString("dice1d6=4 (4)")
        val blue = diceColorOption(mapOf(DICE_COLOR_KEY to "blue")).color
        val styled = text.withAimogeDecorations(aimogeTextDecorations(html), emptySet(), Color.Gray, diceColor = blue)
        assertEquals(blue, styled.spanStyles.single().item.color)
        val none = text.withAimogeDecorations(aimogeTextDecorations(html), emptySet(), Color.Gray, diceColor = null)
        assertEquals(Color.Unspecified, none.spanStyles.single().item.color)
    }

    @Test
    fun theDefaultIsRedAndUnknownValuesFallBack() {
        assertEquals("red", diceColorOption(emptyMap()).value)
        assertEquals("red", diceColorOption(mapOf(DICE_COLOR_KEY to "bogus")).value)
        assertEquals("none", diceColorOption(mapOf(DICE_COLOR_KEY to "none")).value)
    }
}
