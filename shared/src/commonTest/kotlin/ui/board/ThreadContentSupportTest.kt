package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThreadContentSupportTest {
    @Test
    fun countThreadContentItemsBeforePostsIncludesSummaryHeaderAndDeletedNotice() {
        val page = threadPage(deletedNotice = "このスレは削除されました")
        val embeddedHtml = listOf(
            embeddedHtml(id = "header", placement = EmbeddedHtmlPlacement.Header),
            embeddedHtml(id = "footer", placement = EmbeddedHtmlPlacement.Footer)
        )

        assertEquals(
            3,
            countThreadContentItemsBeforePosts(
                page = page,
                embeddedHtml = embeddedHtml,
                hasSummary = true
            )
        )
    }

    @Test
    fun countThreadContentItemsBeforePostsIgnoresFooterAndNullPage() {
        val footerOnly = listOf(embeddedHtml(id = "footer", placement = EmbeddedHtmlPlacement.Footer))

        assertEquals(
            1,
            countThreadContentItemsBeforePosts(
                page = threadPage(deletedNotice = null),
                embeddedHtml = footerOnly,
                hasSummary = true
            )
        )
        assertEquals(
            0,
            countThreadContentItemsBeforePosts(
                page = null,
                embeddedHtml = footerOnly,
                hasSummary = true
            )
        )
    }

    @Test
    fun resolveThreadLazyListIndexForPostOffsetsPostIndexByInsertedContent() {
        val page = threadPage(deletedNotice = "deleted")
        val embeddedHtml = listOf(embeddedHtml(id = "header", placement = EmbeddedHtmlPlacement.Header))

        assertEquals(
            5,
            resolveThreadLazyListIndexForPost(
                postIndex = 2,
                page = page,
                embeddedHtml = embeddedHtml,
                hasSummary = true
            )
        )
        assertEquals(
            4,
            resolveThreadLazyListIndexForPost(
                postIndex = 2,
                page = page,
                embeddedHtml = embeddedHtml,
                hasSummary = false
            )
        )
    }

    @Test
    fun resolveThreadLazyListIndexForPostCoercesNegativeIndexToFirstItem() {
        assertEquals(
            0,
            resolveThreadLazyListIndexForPost(
                postIndex = -10,
                page = threadPage(deletedNotice = null),
                embeddedHtml = emptyList(),
                hasSummary = false
            )
        )
    }

    @Test
    fun buildThreadPostListFingerprintChangesWhenAiRelevantContentChanges() {
        val original = listOf(post(id = "1", subject = "件名", messageHtml = "本文"))
        val changedMessage = listOf(post(id = "1", subject = "件名", messageHtml = "本文 updated"))
        val changedSubject = listOf(post(id = "1", subject = "別件名", messageHtml = "本文"))
        val changedImage = listOf(post(id = "1", subject = "件名", messageHtml = "本文", imageUrl = "https://example.com/src/1.jpg"))

        val fingerprint = buildThreadPostListFingerprint(original)

        assertNotEquals(fingerprint, buildThreadPostListFingerprint(changedMessage))
        assertNotEquals(fingerprint, buildThreadPostListFingerprint(changedSubject))
        assertNotEquals(fingerprint, buildThreadPostListFingerprint(changedImage))
    }

    private fun threadPage(deletedNotice: String?): ThreadPage {
        return ThreadPage(
            threadId = "100",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = deletedNotice,
            posts = emptyList()
        )
    }

    private fun embeddedHtml(
        id: String,
        placement: EmbeddedHtmlPlacement
    ): EmbeddedHtmlContent {
        return EmbeddedHtmlContent(
            id = id,
            html = "<div>$id</div>",
            estimatedHeightDp = 48,
            placement = placement
        )
    }

    private fun post(
        id: String,
        subject: String?,
        messageHtml: String,
        imageUrl: String? = null
    ): Post {
        return Post(
            id = id,
            author = "としあき",
            subject = subject,
            timestamp = "2026/06/04",
            posterId = "ID:abc",
            messageHtml = messageHtml,
            imageUrl = imageUrl,
            thumbnailUrl = null
        )
    }
}
