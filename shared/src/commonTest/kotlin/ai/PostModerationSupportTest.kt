package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostModerationSupportTest {
    @Test
    fun buildPostModerationSourceTextStripsHtmlAndKeepsAllPosts() {
        val posts = (1..100).map { index ->
            post(
                id = index.toString(),
                messageHtml = "<b>本文$index</b><br>  test   value"
            )
        }

        val source = buildPostModerationSourceText(
            PostModerationInput(threadId = "100", posts = posts)
        )
        val lines = source.lines()

        assertEquals(100, lines.size)
        assertEquals("1\t本文1 test value", lines.first())
        assertEquals("100\t本文100 test value", lines.last())
        assertTrue(source.contains("2\t本文2 test value"))
        assertTrue(source.contains("22\t本文22 test value"))
        assertFalse(source.contains("<b>"))
        assertFalse(source.contains("<br>"))
    }

    @Test
    fun buildPostModerationSourceChunksSplitsWithoutDroppingPosts() {
        val sourceChunks = buildPostModerationSourceChunks(
            input = PostModerationInput(
                threadId = "100",
                posts = (1..10).map { index ->
                    post(
                        id = index.toString(),
                        messageHtml = "本文$index ".repeat(12)
                    )
                }
            ),
            maxChunkChars = 80
        )
        val combinedLines = sourceChunks.flatMap { it.lines() }

        assertTrue(sourceChunks.size > 1)
        assertEquals((1..10).map { it.toString() }, combinedLines.map { it.substringBefore('\t') })
    }

    @Test
    fun buildPostModerationSourceTextDecodesBasicHtmlEntities() {
        val source = buildPostModerationSourceText(
            PostModerationInput(
                threadId = "100",
                posts = listOf(
                    post(
                        id = "1",
                        messageHtml = "A&amp;B &gt; C&nbsp;D"
                    )
                )
            )
        )

        assertEquals("1\tA&B > C D", source)
    }

    @Test
    fun buildPostModerationSourceTextStripsUrlsAndQuoteOnlyLines() {
        val source = buildPostModerationSourceText(
            PostModerationInput(
                threadId = "100",
                posts = listOf(
                    post(
                        id = "1",
                        messageHtml = ">引用です<br>本文です https://example.com/path<br>続きです www.example.net"
                    )
                )
            )
        )

        assertEquals("1\t本文です 続きです", source)
    }

    @Test
    fun buildPostModerationSourceTextSkipsBlankBodies() {
        val source = buildPostModerationSourceText(
            PostModerationInput(
                threadId = "100",
                posts = listOf(
                    post(id = "1", messageHtml = "<br>   "),
                    post(id = "2", messageHtml = "本文です")
                )
            )
        )

        assertEquals("2\t本文です", source)
    }

    @Test
    fun parsePostModerationResponseKeepsExplicitDecisions() {
        val parsed = parsePostModerationResponse(
            """
            123	HIDE	連投スパム
            124	KEEP	通常投稿
            125	hide	嫌がらせ
            broken
            """.trimIndent()
        )

        assertEquals(setOf("123", "124", "125"), parsed.keys)
        assertFalse(parsed.getValue("124").shouldHide)
        assertTrue(parsed.getValue("123").shouldHide)
        assertEquals("連投スパム", parsed.getValue("123").reason)
        assertEquals("嫌がらせ", parsed.getValue("125").reason)
    }

    @Test
    fun parsePostModerationResponseRejectsHideWithoutEvidence() {
        val parsed = parsePostModerationResponse("999\tHIDE")

        assertTrue(parsed.isEmpty())
    }

    @Test
    fun parsePostModerationResponseAcceptsWhitespaceFallbackRows() {
        val parsed = parsePostModerationResponse(
            """
            123 HIDE 連投スパム
            124 KEEP 通常投稿
            """.trimIndent()
        )

        assertEquals(setOf("123", "124"), parsed.keys)
        assertEquals("連投スパム", parsed.getValue("123").reason)
    }

    @Test
    fun parsePostModerationResponseNormalizesLongReasons() {
        val parsed = parsePostModerationResponse("123\tHIDE\t${"連投 ".repeat(80)}")
        val reason = parsed.getValue("123").reason.orEmpty()

        assertEquals(80, reason.length)
        assertEquals('…', reason.last())
        assertFalse(reason.contains("  "))
    }

    @Test fun boundedBatchesRetainEveryTargetAndLongBodyTailWithContext() {
        val posts = (1..25).map { post("$it", "先頭$it " + "長い本文".repeat(250) + " 末尾$it") }
        val context = LocalModerationContext("修理相談", posts)
        val batches = context.batches("thread", posts.drop(1))
        assertEquals((2..25).map(Int::toString), batches.flatMap { it.posts.map(Post::id) })
        assertTrue(batches.all { it.posts.size in 1..2 })
        val sources = batches.flatMap { buildPostModerationSourceChunks(it) }
        assertEquals(batches.size, sources.size)
        assertTrue(sources.all { it.length <= 3_000 && "スレ題: 修理相談" in it && "参考 No.1:" in it })
        assertTrue(sources.first().contains("末尾2"))
        assertTrue(sources.first().contains("［中略］"))
        val short = (1..25).map { post("$it", "短い正常な回答$it") }
        assertEquals(listOf(8, 8, 8, 1), LocalModerationContext(null, short).batches("t", short).map { it.posts.size })
    }

    @Test fun quotesDeletedPostsAndContextCannotBecomeHideTargets() {
        val quoted = post("10", "&#62;引用スパム<br>＞全角の引用<br>&gt;&gt;123")
        val reply = post("11", "&gt;引用スパム<br>通常の返答")
        val input = PostModerationInput("t", listOf(quoted, reply, post("12", "削除本文").copy(isDeleted = true)), "参考 No.1: 相談")
        val source = buildPostModerationSourceChunks(input).single()
        assertEquals(setOf("11"), postModerationTargetIds(source))
        assertFalse(source.contains("引用スパム"))
        assertTrue(source.contains("11\t通常の返答"))
        val parsed = parsePostModerationBatchResponse("1 HIDE 文脈\n10 HIDE 引用\n11 KEEP\n12 HIDE 削除", source)
        assertEquals(listOf(PostModerationResult("11", false)), parsed)
    }

    @Test fun uncertainMissingDuplicateAndInvalidDecisionsAreNotSafeCacheEntries() {
        val parsed = parsePostModerationResponse("1 KEEP\n2 UNCERTAIN\n3 HIDE 根拠\n3 KEEP\n4 HIDE\n5 INVALID\n6 HIDE 明確な連投\n3 HIDE 後の矛盾")
        assertEquals(setOf("1", "6"), parsed.keys)
        assertFalse(parsed.getValue("1").shouldHide)
        assertTrue(parsed.getValue("6").shouldHide)
    }

    @Test fun precedingContextSurvivesAppendButChangesWhenRelevantConversationChanges() {
        val posts = (1..15).map { post("$it", "本文$it") }
        val before = LocalModerationContext("相談", posts).forPosts(listOf(posts[9]))
        assertEquals(before, LocalModerationContext("相談", posts + post("16", "追加")).forPosts(listOf(posts[9])))
        val changed = posts.map { if (it.id == "8") it.copy(messageHtml = "相談の訂正") else it }
        assertFalse(before == LocalModerationContext("相談", changed).forPosts(listOf(posts[9])))
        assertFalse(before == LocalModerationContext("別の話題", posts).forPosts(listOf(posts[9])))
        assertTrue(before.contains("参考 No.8:") && before.contains("参考 No.9:"))
        assertFalse(before.contains("参考 No.10:"))
    }

    private fun post(id: String, messageHtml: String): Post {
        return Post(
            id = id,
            author = null,
            subject = null,
            timestamp = "",
            messageHtml = messageHtml,
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}
