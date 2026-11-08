package com.valoser.futacha.shared.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlEntityDecoderTest {
    @Test
    fun decodesNamedDecimalHexAndSupplementaryCodePointsTogether() {
        assertEquals(
            "<引用> ♥ 😀 A",
            HtmlEntityDecoder.decode("&lt;引用&gt; &hearts; &#x1F600; &#65;")
        )
    }

    @Test
    fun namedEntitiesRemainCaseAwareBeforeLowercaseFallback() {
        assertEquals("″ ⇐ α", HtmlEntityDecoder.decode("&Prime; &lArr; &ALPHA;"))
    }

    @Test
    fun malformedUnknownControlSurrogateAndOutOfRangeEntitiesRemainVisible() {
        val value = "&unknown; &#xZZ; &#1; &#xD800; &#x110000;"
        assertEquals(value, HtmlEntityDecoder.decode(value))
    }

    @Test
    fun decodedAmpersandIsNotReadAsAnotherEntity() {
        assertEquals("&#60; &lt; &amp;", HtmlEntityDecoder.decode("&amp;#60; &amp;lt; &amp;amp;"))
    }

    @Test
    fun namedEntitiesContainingDigitsAreDecoded() {
        assertEquals("² ½ ¾", HtmlEntityDecoder.decode("&sup2; &frac12; &frac34;"))
    }

    @Test
    fun dashesAndTheRestOfHtml4AreDecoded() {
        assertEquals("(1523–1563) — ‹À é ß Ω›", HtmlEntityDecoder.decode("(1523&ndash;1563) &mdash; &lsaquo;&Agrave; &eacute; &szlig; &Omega;&rsaquo;"))
        assertEquals("a\u2009b\u200Dc", HtmlEntityDecoder.decode("a&thinsp;b&zwj;c"))
    }
}
