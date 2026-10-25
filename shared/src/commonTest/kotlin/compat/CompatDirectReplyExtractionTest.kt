package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import kotlin.test.Test
import kotlin.test.assertEquals

class CompatDirectReplyExtractionTest {
    private fun posts(vararg bodies: String): List<CompatPostSnapshot> = ThreadHtmlParserCore.rebuildReferences(
        bodies.mapIndexed { index, body -> Post("${100 + index}", author = null, subject = null,
            timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null) }
    ).mapIndexed { index, post -> post.toCompatPostSnapshot(index) }

    private fun verify(original: List<CompatPostSnapshot>, expected: List<List<String>>) {
        for (legacy in listOf(false, true)) {
            val snapshots = if (legacy) original.map { it.copy(quoteReferences = emptyList()) } else original
            snapshots.forEachIndexed { index, source ->
                val actual = extractCompatHeaderPosts(snapshots, source, CompatHeaderExtractionKind.QUOTE)
                assertEquals(expected[index], actual.map { it.postNo }, "source=${source.postNo}, legacy=$legacy")
                assertEquals(source.referencedCount, actual.size, "The popup must match the displayed reply count")
                assertEquals(expected[index].isNotEmpty(), CompatHeaderExtractionKind.QUOTE in compatHeaderExtractionKinds(source, snapshots))
            }
        }
    }

    @Test fun nestedQuotesListOnlyDirectRepliesInsteadOfEveryMentionedAncestor() {
        verify(posts("元の本文", ">元の本文<br>返信本文", ">No.101<br>>>No.100<br>>>元の本文<br>>返信本文"),
            listOf(listOf("101"), listOf("102"), emptyList()))
    }

    @Test fun aCopiedNumberOnlyAnchorIsAReplyToItsSourceWhileItsLinkKeepsTheAncestor() {
        val posts = posts("元の本文", ">No.100", ">>No.100")
        assertEquals("no:100", compatQuoteQueryForPostLine(posts.last(), ">>No.100"))
        verify(posts, listOf(listOf("101"), listOf("102"), emptyList()))
    }

    @Test fun ambiguousCopiedAnchorsDoNotInventRepliesForAnyCandidateOrTheAncestor() {
        verify(posts("元の本文", ">No.100<br>同じ本文", ">No.100<br>同じ本文", ">>No.100<br>>同じ本文"),
            listOf(listOf("101", "102"), emptyList(), emptyList(), emptyList()))
    }

    @Test fun bareDoubleMarkerNumbersKeepTheirExplicitReplyBehavior() {
        verify(posts("元の本文", ">100", ">>100"), listOf(listOf("101", "102"), emptyList(), emptyList()))
    }

    /**
     * L138: a quote the parser could not resolve (a short ">草") is not listed by an unbounded partial text match, so
     * the list agrees with the "Nレス" count. An IP quote is still listed as before (the decision for L138).
     */
    @Test fun unresolvedQuotesAreNotListedByPartialTextButIpQuotesStillAre() {
        // A thread read by the current parser (its other quote is resolved): ">草" is not listed under the post it is
        // part of, and the list agrees with the count. Older caches keep their compatibility matching (M12).
        val parsed = posts("大草原不可避", ">大草原不可避<br>わかる", ">草")
        assertEquals(listOf("101"), extractCompatHeaderPosts(parsed, parsed[0], CompatHeaderExtractionKind.QUOTE).map { it.postNo })
        assertEquals(parsed[0].referencedCount, 1)
        val withIp = posts("本文", ">IP:203.0.113.*<br>同じ人").mapIndexed { index, post ->
            if (index == 0) post.copy(timestamp = "25/10/23(木)12:00:00 IP:203.0.113.*") else post
        }
        assertEquals(listOf("101"), extractCompatHeaderPosts(withIp, withIp[0], CompatHeaderExtractionKind.QUOTE).map { it.postNo })
        // In a thread read by the current parser too.
        val parsedWithIp = posts("本文", ">本文<br>引用", ">IP:203.0.113.*<br>同じ人").mapIndexed { index, post ->
            if (index == 0) post.copy(timestamp = "25/10/23(木)12:00:00 IP:203.0.113.*") else post
        }
        assertEquals(listOf("101", "102"), extractCompatHeaderPosts(parsedWithIp, parsedWithIp[0], CompatHeaderExtractionKind.QUOTE).map { it.postNo })
    }
}
