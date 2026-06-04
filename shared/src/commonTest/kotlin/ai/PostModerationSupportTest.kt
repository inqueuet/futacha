package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostModerationSupportTest {
    @Test
    fun buildPostModerationSourceTextStripsHtmlAndLimitsPosts() {
        val posts = (1..45).map { index ->
            post(
                id = index.toString(),
                messageHtml = "<b>本文$index</b><br>  test   value"
            )
        }

        val source = buildPostModerationSourceText(
            PostModerationInput(threadId = "100", posts = posts)
        )
        val lines = source.lines()

        assertEquals(40, lines.size)
        assertEquals("1\t本文1 test value", lines.first())
        assertEquals("40\t本文40 test value", lines.last())
        assertFalse(source.contains("<b>"))
        assertFalse(source.contains("<br>"))
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
    fun parsePostModerationResponseKeepsOnlyHideRows() {
        val parsed = parsePostModerationResponse(
            """
            123	HIDE	連投スパム
            124	KEEP	通常投稿
            125	hide	嫌がらせ
            broken
            """.trimIndent()
        )

        assertEquals(setOf("123", "125"), parsed.keys)
        assertTrue(parsed.getValue("123").shouldHide)
        assertEquals("連投スパム", parsed.getValue("123").reason)
        assertEquals("嫌がらせ", parsed.getValue("125").reason)
    }

    @Test
    fun parsePostModerationResponseUsesFallbackReason() {
        val parsed = parsePostModerationResponse("999\tHIDE")

        assertEquals("端末AIが荒らしの可能性を検出しました。", parsed.getValue("999").reason)
    }

    @Test
    fun parsePostModerationResponseAcceptsWhitespaceFallbackRows() {
        val parsed = parsePostModerationResponse(
            """
            123 HIDE 連投スパム
            124 KEEP 通常投稿
            """.trimIndent()
        )

        assertEquals(setOf("123"), parsed.keys)
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
