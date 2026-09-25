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
}
