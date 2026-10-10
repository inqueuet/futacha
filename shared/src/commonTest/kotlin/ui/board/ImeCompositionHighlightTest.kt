package com.valoser.futacha.shared.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import kotlin.test.*

class ImeCompositionHighlightTest {
    @Test fun decoratesTheActualCompositionWithoutChangingTextOrOffsets() {
        val input = AnnotatedString("日本語入力")
        val result = ImeCompositionHighlight(TextRange(1, 3), Color.Yellow).filter(input)
        assertEquals(input.text, result.text.text)
        assertEquals(1, result.text.spanStyles.single().start)
        assertEquals(3, result.text.spanStyles.single().end)
        (0..input.length).forEach { assertEquals(it, result.offsetMapping.originalToTransformed(it)); assertEquals(it, result.offsetMapping.transformedToOriginal(it)) }
    }
    @Test fun emptyCommittedAndStaleRangesAreSafe() {
        assertTrue(ImeCompositionHighlight(null, Color.Yellow).filter(AnnotatedString("確定")).text.spanStyles.isEmpty())
        assertTrue(ImeCompositionHighlight(TextRange(1, 50), Color.Yellow).filter(AnnotatedString("")).text.spanStyles.isEmpty())
        assertEquals(2, ImeCompositionHighlight(TextRange(1, 50), Color.Yellow).filter(AnnotatedString("入力")).text.spanStyles.single().end)
    }
}
