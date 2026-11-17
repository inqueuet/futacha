package com.valoser.futacha.shared.compat.aimoge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.network.aimoge.aimogeMessageHtml
import com.valoser.futacha.shared.ui.board.AimogeDecoration
import com.valoser.futacha.shared.ui.board.AimogeDecorationKind.DICE
import com.valoser.futacha.shared.ui.board.AimogeDecorationKind.SPOILER
import com.valoser.futacha.shared.ui.board.aimogeHtmlForSpeech
import com.valoser.futacha.shared.ui.board.aimogeLineDecorations
import com.valoser.futacha.shared.ui.board.aimogeSpoilerAt
import com.valoser.futacha.shared.ui.board.aimogeTextDecorations
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import com.valoser.futacha.shared.ui.board.toggleAimogeSpoiler
import com.valoser.futacha.shared.ui.board.withAimogeDecorations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AimogeBodyDecorationsTest {
    private val open = ''
    private val close = ''
    private val dice = "dice1d6=$open4$close"
    private val hidden = "答えは$openネタバレ$closeです"

    @Test fun theTextWithoutTagsIsTheBodyWithoutMarkers() {
        val html = aimogeMessageHtml("$dice\n$hidden\n>引用 $open6$close")
        assertEquals("dice1d6=4\n答えはネタバレです\n>引用 6", messageHtmlToPlainText(html))
        assertEquals("dice1d6=4\n答えはネタバレです\n>引用 6", html.toCompatPlainText())
        assertEquals(true, html.contains("<font color=\"#789922\">&gt;引用 <b class=\"aimoge-dice\">6</b></font>"))
    }

    @Test fun eachLineGetsItsDiceAndSpoilerRanges() {
        val html = aimogeMessageHtml("$dice\n$hidden")
        assertEquals(listOf(listOf(AimogeDecoration(8, 9, DICE, "4")), listOf(AimogeDecoration(3, 7, SPOILER, "ネタバレ"))), aimogeLineDecorations(html))
        assertEquals(listOf(AimogeDecoration(8, 9, DICE, "4"), AimogeDecoration(13, 17, SPOILER, "ネタバレ")), aimogeTextDecorations(html))
        val compat = aimogeTextDecorations(html, toLines = { it.toCompatPlainText().split('\n') }, lineLength = { _, l -> l.length })
        assertEquals(listOf(AimogeDecoration(8, 9, DICE, "4"), AimogeDecoration(13, 17, SPOILER, "ネタバレ")), compat)
    }

    @Test fun aSpoilerOverTwoLinesAndABrokenBody() {
        val html = aimogeMessageHtml("a$openb\nc$closed")
        assertEquals(listOf(listOf(AimogeDecoration(1, 2, SPOILER, "b")), listOf(AimogeDecoration(0, 1, SPOILER, "c"))), aimogeLineDecorations(html))
        val broken = aimogeMessageHtml("壊$openれ")
        assertEquals("壊れ", messageHtmlToPlainText(broken))
        assertNull(aimogeLineDecorations(broken))
    }

    @Test fun aFutabaBodyHasNoDecorationsEvenWithBoldTags() {
        assertNull(aimogeLineDecorations("<b>太字</b><span>x</span><br>&gt;引用"))
        assertEquals(emptyList(), aimogeTextDecorations("<b>太字</b>"))
        val text = AnnotatedString("abc")
        assertEquals(text, text.withAimogeDecorations(emptyList(), emptySet(), Color.Black))
    }

    @Test fun diceIsBoldHiddenTextIsCoveredUntilTappedAndNeverSpoken() {
        val html = aimogeMessageHtml("$dice\n$hidden")
        val plain = AnnotatedString(messageHtmlToPlainText(html))
        val decorations = aimogeTextDecorations(html)
        val covered = plain.withAimogeDecorations(decorations, emptySet(), Color.Black)
        assertEquals(FontWeight.Bold, covered.spanStyles.single { it.start == 8 }.item.fontWeight)
        assertEquals(Color.Black, covered.spanStyles.single { it.start == 13 }.item.background)
        assertEquals(0, covered.aimogeSpoilerAt(14))
        assertNull(covered.aimogeSpoilerAt(2))
        val shown = plain.withAimogeDecorations(decorations, toggleAimogeSpoiler(emptySet(), 0), Color.Black)
        assertEquals(null, shown.spanStyles.firstOrNull { it.start == 13 })
        assertEquals(emptySet(), toggleAimogeSpoiler(setOf(0), 0))
        assertEquals("dice1d6=4\n答えはです", messageHtmlToPlainText(aimogeHtmlForSpeech(html)))
        assertEquals("<b>x</b>", aimogeHtmlForSpeech("<b>x</b>"))
    }

    @Test fun whenTheShownTextDiffersTheSpoilerStillCoversTheHiddenWordsAndDiceIsNotMisplaced() {
        val html = aimogeMessageHtml("$dice\n$hidden")
        val decorations = aimogeTextDecorations(html)
        // The screen shows something before the body (a mark, a highlight) the ranges were not counted with.
        val shown = AnnotatedString("[削除] " + messageHtmlToPlainText(html))
        val covered = shown.withAimogeDecorations(decorations, emptySet(), Color.Black)
        val at = shown.text.indexOf("ネタバレ")
        val spoiler = covered.spanStyles.single { it.item.background == Color.Black }
        assertEquals(true, spoiler.start <= at && spoiler.end >= at + 4)
        val bold = covered.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(listOf("4"), bold.map { shown.text.substring(it.start, it.end) })
        // The hidden words gone from the shown text: the rest of that line is covered, never left visible.
        val cut = AnnotatedString("dice1d6=4\n答えはネタ…")
        val safe = cut.withAimogeDecorations(decorations, emptySet(), Color.Black)
        val cover = safe.spanStyles.single { it.item.background == Color.Black }
        assertEquals(cut.text.length, cover.end)
        assertEquals(true, cover.start <= cut.text.indexOf("ネタ"))
    }
}
