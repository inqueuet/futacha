package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.compat.toThreadPage
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.parser.ThreadHtmlParserCore
import com.valoser.futacha.shared.ui.board.ThreadOpeningSummary
import com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker
import kotlin.test.*

/** Raw live posts must give the same feedback as the former cache round trip. */
class CompatOpeningSummaryEquivalenceTest {
    private fun post(id: String, body: String = "本文$id") = Post(id, author = null, subject = null,
        timestamp = "", messageHtml = body, imageUrl = null, thumbnailUrl = null)

    private fun check(posts: List<Post>, baseline: Int, lastId: String, self: Set<String>, hidden: Set<String>, expected: ThreadOpeningSummary) {
        val page = ThreadPage("100", null, null, null, posts)
        val restored = page.toCompatThreadSnapshot("tab", 1).toThreadPage("100")
        for (candidate in listOf(page.posts, restored.posts)) {
            val tracker = ThreadOpeningSummaryTracker(baseline, lastId)
            assertEquals(expected, tracker.accept(candidate, self, hidden))
            assertNull(tracker.accept(candidate + post("9999"), self, hidden))
        }
    }

    @Test fun aThousandPostsKeepHiddenAndDirectReplyCountsWithoutASecondMediaNormalization() {
        val posts = (100..1099).map { id -> post("$id").copy(
            imageUrl = "https://may.2chan.net/b/src/$id.jpg?name=a.webp",
            thumbnailUrl = "https://may.2chan.net/b/thumb/${id}s.jpg",
            quoteReferences = if (id == 1098) listOf(QuoteReference(">No.100", listOf("100"))) else emptyList()
        ) }
        check(posts, 997, "1096", setOf("100"), setOf("1099"), ThreadOpeningSummary(2, 1))
    }

    @Test fun parserProducedCopiedAnchorsKeepTheirDirectParent() {
        val posts = ThreadHtmlParserCore.rebuildReferences(listOf(post("100", "元の本文")) +
            (101..103).map { post("$it", ">No.100<br>返信本文$it") } +
            post("104", ">>No.100<br>>返信本文103<br>さらに返信"))
        check(posts, 4, "103", setOf("103"), emptySet(), ThreadOpeningSummary(1, 1))
        check(posts, 4, "103", setOf("101", "102"), emptySet(), ThreadOpeningSummary(1, 0))
    }

    @Test fun deletedEarlierPostsDoNotChangeTheStableOpeningBoundary() {
        check(listOf(post("100"), post("103"), post("105")), 4, "104", emptySet(), emptySet(), ThreadOpeningSummary(1, null))
    }
}
