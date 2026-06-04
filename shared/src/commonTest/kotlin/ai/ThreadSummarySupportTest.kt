package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadSummarySupportTest {
    @Test
    fun parseGeneratedThreadSummaryUsesGeneratedHeadlineWhenPresent() {
        val summary = parseGeneratedThreadSummary(
            input = input(title = "元スレタイ"),
            generatedText = """
                話題の中心
                - 参加者が仕様案を比較している
                - 段階導入で進める流れになっている
            """.trimIndent(),
            providerLabel = "Apple Intelligence",
            generatedTextHasHeadline = true
        )

        assertEquals("話題の中心", summary.headline)
        assertEquals(
            listOf("参加者が仕様案を比較している", "段階導入で進める流れになっている"),
            summary.bullets
        )
        assertEquals("Apple Intelligence", summary.providerLabel)
    }

    @Test
    fun parseGeneratedThreadSummaryKeepsThreadTitleForBulletOnlyOutput() {
        val summary = parseGeneratedThreadSummary(
            input = input(title = "AI対応について"),
            generatedText = """
                1、端末AI対応機種だけで要約を有効化する
                • 荒らし非表示は個別に戻せるようにする
            """.trimIndent(),
            providerLabel = "Gemini Nano",
            generatedTextHasHeadline = false
        )

        assertEquals("AI対応について", summary.headline)
        assertEquals(
            listOf("端末AI対応機種だけで要約を有効化する", "荒らし非表示は個別に戻せるようにする"),
            summary.bullets
        )
    }

    @Test
    fun parseGeneratedThreadSummaryFallsBackWhenResponseIsBlank() {
        val summary = parseGeneratedThreadSummary(
            input = input(title = null),
            generatedText = "   ",
            providerLabel = "端末AI",
            generatedTextHasHeadline = true
        )

        assertEquals("本文から拾った要点です", summary.headline)
        assertEquals(listOf("本文から拾った要点です"), summary.bullets)
    }

    @Test
    fun buildThreadSummarySourceTextStripsUrlsFromPostBody() {
        val source = buildThreadSummarySourceText(
            input = ThreadSummaryInput(
                threadId = "100",
                title = null,
                posts = listOf(
                    Post(
                        id = "1",
                        author = null,
                        subject = null,
                        timestamp = "",
                        messageHtml = "本文です<br>https://example.com/path?query=1<br>続きです www.example.net",
                        imageUrl = null,
                        thumbnailUrl = null
                    )
                )
            )
        )

        assertEquals("本文です\n続きです", source)
    }

    private fun input(title: String?): ThreadSummaryInput {
        return ThreadSummaryInput(
            threadId = "100",
            title = title,
            posts = listOf(
                Post(
                    id = "1",
                    author = null,
                    subject = null,
                    timestamp = "",
                    messageHtml = "本文から拾った要点です",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
    }
}
