package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadTitleResolverTest {
    @Test
    fun extractFirstLineFromBody_stripsTagsAndDecodesEntities() {
        val line = extractFirstLineFromBody(
            post(
                messageHtml = "<p>&gt;引用</p><br>次の行<b>です</b>"
            )
        )

        assertEquals(">引用", line)
    }

    @Test
    fun extractFirstLineFromBody_returnsNullWhenBodyIsBlank() {
        assertNull(extractFirstLineFromBody(post(messageHtml = "<br><br>")))
    }

    @Test
    fun resolveThreadTitle_prefersFirstBodyLineOverSubjectAndFallbacks() {
        val title = resolveThreadTitle(
            firstPost = post(
                subject = "件名",
                messageHtml = "本文タイトル<br>二行目"
            ),
            "fallback"
        )

        assertEquals("本文タイトル", title)
    }

    @Test
    fun resolveThreadTitle_usesSubjectThenFallbackThenDefault() {
        val fromSubject = resolveThreadTitle(
            firstPost = post(subject = "件名だけ", messageHtml = ""),
            "fallback"
        )
        val fromFallback = resolveThreadTitle(
            firstPost = post(subject = null, messageHtml = ""),
            "",
            "fallback"
        )
        val fromDefault = resolveThreadTitle(
            firstPost = post(subject = null, messageHtml = ""),
            "",
            null
        )

        assertEquals("件名だけ", fromSubject)
        assertEquals("fallback", fromFallback)
        assertEquals("無題", fromDefault)
    }

    private fun post(
        subject: String? = null,
        messageHtml: String
    ): Post {
        return Post(
            id = "1",
            author = "author",
            subject = subject,
            timestamp = "now",
            messageHtml = messageHtml,
            imageUrl = null,
            thumbnailUrl = null
        )
    }
}
