package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadCopiedAnchorAmbiguityTest {
    private fun post(id: String, body: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)
    private fun replies(body: String, shared: Boolean = false) = ThreadHtmlParserCore.rebuildReferences(
        listOf(post("100", "元の本文")) + (101..103).map { id ->
            post("$id", ">No.100<br>" + if (shared) "同じ返信本文" else "返信本文$id")
        } + post("104", body)
    )

    @Test fun theRestOfTheCopiedQuoteIdentifiesItsSourceWithoutNotifyingOtherAuthors() {
        for (body in listOf(">>No.100<br>>返信本文103<br>さらに返信", ">>No.100<br>\n\n>返信本文103<br>さらに返信")) {
            val posts = replies(body)
            assertEquals(listOf("103"), posts.last().quoteReferences.first().replyTargetPostIds)
            assertEquals(listOf("100"), posts.last().quoteReferences.first().targetPostIds)
            assertEquals(listOf(3, 0, 0, 1, 0), posts.map { it.referencedCount })
            assertEquals(0, ThreadOpeningSummaryTracker(4).accept(posts, setOf("101", "102"))?.repliesToSelf)
            assertEquals(1, ThreadOpeningSummaryTracker(4).accept(posts, setOf("103"))?.repliesToSelf)
        }
    }

    @Test fun anAmbiguousCopiedAnchorKeepsItsLinkWithoutInventingDirectParents() {
        val posts = replies(">>No.100")
        assertEquals(listOf("100"), posts.last().quoteReferences.single().targetPostIds)
        assertTrue(posts.last().quoteReferences.single().replyTargetPostIds.isEmpty())
        assertEquals(listOf(3, 0, 0, 0, 0), posts.map { it.referencedCount })
    }

    @Test fun identicalCopiedBodiesAlsoRemainAmbiguousForReplyCounts() {
        val posts = replies(">>No.100<br>>同じ返信本文", shared = true)
        assertTrue(posts.last().quoteReferences.all { it.replyTargetPostIds.isEmpty() })
        assertEquals(listOf(3, 0, 0, 0, 0), posts.map { it.referencedCount })
    }

    @Test fun aBareDoubleMarkerNumberStaysAnExplicitAnchorEvenAfterSomeoneQuotedThatNumber() {
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元"), post("101", ">100"), post("102", ">>100")))
        assertEquals(listOf("100"), posts.last().quoteReferences.single().replyTargetPostIds)
        assertTrue(posts.last().quoteReferences.single().isDirectReply)
        assertEquals(listOf(2, 0, 0), posts.map { it.referencedCount })
    }
}
