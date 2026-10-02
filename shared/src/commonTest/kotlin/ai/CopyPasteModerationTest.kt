package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.resolveAiHiddenPostState
import kotlin.test.*

class CopyPasteModerationTest {
    private fun post(id: String, text: String) = Post(id = id, author = "", subject = null,
        timestamp = "", messageHtml = text, imageUrl = null, thumbnailUrl = null)
    private val copied = "契約資産だろうと経費だろうと会社が用意できる額は限られているよ"

    @Test fun repeatedArchiveBodyHidesOnlyLaterPostAndPreservesOwnAndOriginalPost() {
        val posts = listOf(post("1", copied), post("2", copied), post("3", copied), post("4", "別の話題です"))
        val results = detectCopyPastePosts(posts)
        assertEquals(listOf("2", "3"), results.map { it.postId })
        val hidden = resolveAiHiddenPostState(posts, results, setOf("3"))
        assertEquals(setOf("2"), hidden.postIds)
        assertContains(hidden.reasons.getValue("2"), "No.1")
    }

    @Test fun quotedCopiesAndQuoteWithReplyRemainVisibleAndCannotSeedSpamMatch() {
        val quoted = listOf(post("1", "&gt;$copied"), post("2", "&gt;$copied"),
            post("3", "　＞$copied<br>同意します"), post("4", "　＞$copied<br>同意します"),
            post("5", "&gt;引用<br>" + "繰り返し文です<br>".repeat(8)))
        assertTrue(detectCopyPastePosts(quoted).isEmpty())
        assertTrue(detectCopyPastePosts(quoted + post("6", copied)).isEmpty())
    }

    @Test fun shortAcknowledgmentsAndDifferentBodiesRemainVisible() {
        assertTrue(detectCopyPastePosts((1..20).map { post("$it", "そうだね") }).isEmpty())
        assertTrue(detectCopyPastePosts(listOf(post("1", copied), post("2", copied + "。別の意見です"))).isEmpty())
    }

    @Test fun repeatedLinesRequireDominanceAndQuotesAlwaysWin() {
        val repeated = "コピペ来た<br>".repeat(8)
        assertEquals(listOf("2"), detectCopyPastePosts(listOf(post("1", "スレ"), post("2", repeated))).map { it.postId })
        assertTrue(detectCopyPastePosts(listOf(post("2", repeated + "通常の説明文".repeat(30)))).isEmpty())
        assertTrue(detectCopyPastePosts(listOf(post("2", "&gt;引用<br>" + repeated))).isEmpty())
    }
}
