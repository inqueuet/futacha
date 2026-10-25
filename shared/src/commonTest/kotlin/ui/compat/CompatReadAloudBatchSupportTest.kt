package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.buildCompatThreadNgRuleIndex
import com.valoser.futacha.shared.compat.filterCompatThreadPosts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatReadAloudBatchSupportTest {
    @Test
    fun visibleStartUsesPostIdentityAfterFilteringAndClampsTheListEdge() {
        val posts = (1..8).map { post(it.toString(), "本文$it") }
        val visible = listOf(posts[2], posts[5], posts[7])
        assertEquals(5, resolveCompatReadAloudVisibleIndex(posts, visible, 1))
        assertEquals(7, resolveCompatReadAloudVisibleIndex(posts, visible, 99))
        assertEquals(2, resolveCompatReadAloudVisibleIndex(posts, visible, -1))
        assertEquals(0, resolveCompatReadAloudVisibleIndex(posts, emptyList(), 0))
    }

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

    @Test
    fun speechUsesTheSameWordIdNumberImageAndAiFilteringAsTheDisplayedList() {
        val posts = listOf(
            post("1", "表示する本文"),
            post("2", "禁止語を含む本文"),
            post("3", "IDで隠す本文").copy(posterId = "AbCd"),
            post("4", "番号で隠す本文"),
            post("5", "画像で隠す本文").copy(imageUrl = "https://example.com/hidden.jpg"),
            post("6", "AIで隠す本文"),
            post("7", "類似画像で隠す本文"),
            post("8", "最後の本文")
        )
        val rules = listOf(
            CompatNgRule("word", CompatNgKind.THREAD_WORD, "thread", "禁止語", 0),
            CompatNgRule("id", CompatNgKind.THREAD_POSTER_ID, "thread", "AbCd", 0),
            CompatNgRule("number", CompatNgKind.THREAD_POST_NO, "thread", "4", 0),
            CompatNgRule("image", CompatNgKind.THREAD_IMAGE, "thread", "https://example.com/hidden.jpg", 0)
        )
        val visible = filterCompatThreadPosts(
            posts, true, buildCompatThreadNgRuleIndex(rules, "thread"),
            aiHiddenPostNos = setOf("6", "7")
        )
        assertEquals(listOf("1", "8"), visible.map { it.postNo })
        val byNo = visible.associateBy { it.postNo }
        val first = buildCompatReadAloudBatch(posts, 0, 0, visiblePostsByNo = byNo)
        val last = buildCompatReadAloudBatch(posts, first.nextPostIndex, 0, visiblePostsByNo = byNo)
        assertEquals("表示する本文", first.text)
        assertEquals("最後の本文", last.text)
        assertEquals(posts.size, last.nextPostIndex)
    }

    @Test
    fun deletedAndIsolatedBodiesAreNotReadWhenOnlyTheirPlaceholderIsDisplayed() {
        val posts = listOf(
            post("1", "非表示の削除本文").copy(isDeleted = true),
            post("2", "非表示の隔離本文").copy(isIsolated = true),
            post("3", "通常の本文")
        )
        val visible = presentCompatPostsForDeletedVisibility(posts, showDeletedContent = false)
        val batch = buildCompatReadAloudBatch(posts, 0, 0, visiblePostsByNo = visible.associateBy { it.postNo })
        assertEquals("通常の本文", batch.text)
        assertEquals(posts.size, batch.nextPostIndex)
        val shown = buildCompatReadAloudBatch(posts, 0, 0, visiblePostsByNo = posts.associateBy { it.postNo })
        assertEquals("非表示の削除本文", shown.text)
    }

    @Test
    fun noVisiblePostsProducesNoUtteranceAndAdvancesToTheEnd() {
        val posts = listOf(post("1", "隠した本文"))
        val batch = buildCompatReadAloudBatch(posts, 0, 0, visiblePostsByNo = emptyMap())
        assertEquals("", batch.text)
        assertEquals(posts.size, batch.nextPostIndex)
    }

    @Test
    fun hidingALongPostBetweenChunksSkipsItsRemainingBody() {
        val posts = listOf(post("1", "あ".repeat(7100)), post("2", "次の本文"))
        val first = buildCompatReadAloudBatch(posts, 0, 0, visiblePostsByNo = posts.associateBy { it.postNo })
        assertEquals(3000, first.nextCharacterOffset)
        val next = buildCompatReadAloudBatch(
            posts, first.nextPostIndex, first.nextCharacterOffset,
            visiblePostsByNo = mapOf("2" to posts[1])
        )
        assertEquals("次の本文", next.text)
        assertEquals(posts.size, next.nextPostIndex)
        assertEquals(0, next.nextCharacterOffset)
    }

    private fun post(no: String, body: String) = CompatPostSnapshot(
        position = no.toInt() - 1,
        postNo = no,
        timestamp = "",
        messageHtml = body
    )
}
