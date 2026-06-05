package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun buildThreadSummarySourceTextUsesOnlyPostBody() {
        val source = buildThreadSummarySourceText(
            input = ThreadSummaryInput(
                threadId = "100",
                title = "スレタイは含めない",
                posts = listOf(
                    Post(
                        id = "1",
                        author = "投稿者名",
                        subject = "レス件名は含めない",
                        timestamp = "2026/06/05",
                        messageHtml = "本文だけを読む&#12290;",
                        imageUrl = null,
                        thumbnailUrl = null
                    )
                )
            )
        )

        assertEquals("本文だけを読む。", source)
    }

    @Test
    fun buildThreadSummarySourceTextKeepsUpToTenThousandCharacters() {
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
                        messageHtml = "あ".repeat(10_500),
                        imageUrl = null,
                        thumbnailUrl = null
                    )
                )
            )
        )

        assertEquals(10_000, source.length)
    }

    @Test
    fun parseGeneratedThreadSummaryLimitsVisibleSummaryToOneThousandCharacters() {
        val summary = parseGeneratedThreadSummary(
            input = input(title = "見出し"),
            generatedText = listOf(
                "生成見出し",
                "あ".repeat(500),
                "い".repeat(500),
                "う".repeat(500),
                "え".repeat(500)
            ).joinToString(separator = "\n"),
            providerLabel = "端末AI",
            generatedTextHasHeadline = true
        )

        val visibleSummaryLength = summary.headline.length + summary.bullets.sumOf { it.length }
        assertTrue(visibleSummaryLength <= 1_000)
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
