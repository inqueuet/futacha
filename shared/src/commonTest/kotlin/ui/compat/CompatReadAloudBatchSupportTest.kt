package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatReadAloudBatchSupportTest {
    @Test
    fun largePostIsSplitWithoutDroppingItsRemainder() {
        val body = "あ".repeat(7_100)
        val posts = listOf(post("1", body), post("2", "次のレス"))

        val first = buildCompatReadAloudBatch(posts, 0, 0)
        val second = buildCompatReadAloudBatch(posts, first.nextPostIndex, first.nextCharacterOffset)
        val third = buildCompatReadAloudBatch(posts, second.nextPostIndex, second.nextCharacterOffset)
        val fourth = buildCompatReadAloudBatch(posts, third.nextPostIndex, third.nextCharacterOffset)

        assertEquals(3_000, first.text.length)
        assertEquals(3_000, second.text.length)
        assertEquals(body, first.text + second.text + third.text)
        assertEquals("次のレス", fourth.text)
        assertEquals(posts.size, fourth.nextPostIndex)
        assertEquals(0, fourth.nextCharacterOffset)
    }

    @Test
    fun quotedAndBlankLinesRemainExcluded() {
        val result = buildCompatReadAloudBatch(
            posts = listOf(post("1", "本文<br>&gt;引用<br><br>続き")),
            startPostIndex = 0,
            startCharacterOffset = 0,
            maxChars = 100
        )

        assertEquals("本文 続き", result.text)
        assertTrue(result.nextPostIndex == 1)
    }

    @Test
    fun urlsAndReferenceSystemNoticesAreExcludedPerPost() {
        val result = buildCompatReadAloudBatch(
            posts = listOf(
                post(
                    "1",
                    "本文<br>https://example.com/path<br>リンク https://example.com/a 続き" +
                        "<br>IP:127.0.0.1<br>管理人によって削除されました<br>好き…"
                ),
                post("2", "次のレス")
            ),
            startPostIndex = 0,
            startCharacterOffset = 0,
            maxChars = 500
        )

        assertEquals("本文 リンク  続き スキ　", result.text)
        assertEquals(1, result.nextPostIndex)
    }

    @Test
    fun completedCursorRestartsFromFirstPostOnNextInvocation() {
        assertEquals(0, resolveCompatReadAloudStartIndex(requestedIndex = 5, postCount = 5))
        assertEquals(3, resolveCompatReadAloudStartIndex(requestedIndex = 3, postCount = 5))
        assertEquals(0, resolveCompatReadAloudStartIndex(requestedIndex = -1, postCount = 5))
    }

    @Test
    fun missingJapaneseVoiceKeepsTheReferenceErrorInsteadOfGenericFallback() {
        assertEquals(
            "日本語TTSが有効になっていません",
            IllegalStateException("日本語TTSが有効になっていません")
                .toCompatUserMessage("読み上げできませんでした")
        )
    }

    private fun post(no: String, body: String) = CompatPostSnapshot(
        position = no.toInt() - 1,
        postNo = no,
        timestamp = "",
        messageHtml = body
    )
}
