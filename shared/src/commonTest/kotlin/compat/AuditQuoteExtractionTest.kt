package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.QuoteReference
import kotlin.test.*

class AuditQuoteExtractionTest {
    private fun post(i: Int, text: String) = CompatPostSnapshot(i, (100 + i).toString(), timestamp = "", messageHtml = text,
        quoteReferencesParsed = true)
    @Test fun aThreadWithoutAnyResolvableQuotesDoesNotUseLegacySubstringMatching() {
        val source = post(0, "今日は草の話です")
        val quote = post(1, "&gt;草")
        assertTrue(extractCompatHeaderPosts(listOf(source, quote), source, CompatHeaderExtractionKind.QUOTE).isEmpty())
    }
    @Test fun anIpQuoteStillMatchesWhenTheSamePostHasAnotherResolvedQuote() {
        val source = post(0, "source").copy(timestamp = "IP:192.0.2.10")
        val quote = post(2, "&gt;IP:192.0.2.10<br>&gt;other").copy(
            quoteReferences = listOf(QuoteReference("other", listOf("101"))))
        assertEquals(listOf(quote), extractCompatHeaderPosts(listOf(source, post(1, "other"), quote), source, CompatHeaderExtractionKind.QUOTE))
    }
}
