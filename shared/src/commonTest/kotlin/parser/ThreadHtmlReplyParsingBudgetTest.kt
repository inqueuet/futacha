package com.valoser.futacha.shared.parser

import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class ThreadHtmlReplyParsingBudgetTest {
    private val config = ThreadReplyParsingConfig(
        tag = "ThreadHtmlReplyParsingBudgetTest",
        maxChunkSize = 200_000,
        maxParseTimeMs = 9_000L,
        maxSingleBlockSize = 300_000,
        maxIterations = 2_000,
        maxPosts = 3_000,
        tableRegex = Regex("<table\\b[^>]{0,500}\\bborder\\s*=\\s*['\"]?0['\"]?[^>]{0,500}>", RegexOption.IGNORE_CASE),
        tableEndRegex = Regex("</table>", RegexOption.IGNORE_CASE)
    )

    private fun replies(count: Int): String = buildString {
        repeat(count) { index ->
            append("<table border=0><tr><td><span class=\"cno\">No.${index + 2}</span></td></tr></table>\n")
        }
    }

    private fun post(id: String) = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "",
        messageHtml = "",
        imageUrl = null,
        thumbnailUrl = null
    )

    @Test
    fun slowParseReturnsTheRepliesReadBeforeTheBudgetAsTruncated() = runBlocking {
        val clock = TestTimeSource()
        var parsed = 0
        val result = parseThreadReplyBlocks(
            repliesHtml = replies(10),
            initialSearchStart = 0,
            config = config,
            parseStartedAt = clock.markNow()
        ) {
            parsed += 1
            clock += 4.seconds
            post(parsed.toString())
        }

        // 3 replies take 12 s: the 9 s reply budget stops the scan with a partial result.
        assertEquals(3, result.posts.size)
        assertTrue(result.isTruncated)
        assertTrue(result.truncationReason.orEmpty().startsWith("Parse timeout exceeded"))
    }

    @Test
    fun replyBudgetIsMeasuredFromTheStartOfTheWholeParse() = runBlocking {
        val clock = TestTimeSource()
        val startedAt = clock.markNow()
        // The OP block and header extraction already used 10 s of the budget.
        clock += 10.seconds
        val result = parseThreadReplyBlocks(
            repliesHtml = replies(3),
            initialSearchStart = 0,
            config = config,
            parseStartedAt = startedAt
        ) { post("2") }

        assertTrue(result.posts.isEmpty())
        assertTrue(result.isTruncated)
    }
}
