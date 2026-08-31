package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatDeletedPostPresentationTest {
    private fun post(
        no: String,
        isDeleted: Boolean = false,
        isIsolated: Boolean = false
    ) = CompatPostSnapshot(
        position = no.toInt(),
        postNo = no,
        timestamp = "now",
        messageHtml = "元の本文",
        imageUrl = "https://may.2chan.net/b/src/$no.jpg",
        thumbnailUrl = "https://may.2chan.net/b/thumb/${no}s.jpg",
        isDeleted = isDeleted,
        isIsolated = isIsolated
    )

    @Test
    fun hiddenDeletedContentKeepsRowsAndReplacesBodyAndMediaLikeReferenceApk() {
        val visible = post("1")
        val isolated = post("2", isIsolated = true)
        val deleted = post("3", isDeleted = true)

        val presented = presentCompatPostsForDeletedVisibility(
            listOf(visible, isolated, deleted),
            showDeletedContent = false
        )

        assertEquals(listOf("1", "2", "3"), presented.map(CompatPostSnapshot::postNo))
        assertEquals(COMPAT_ISOLATED_POST_NOTICE, presented[1].messageHtml)
        assertEquals(COMPAT_ADMIN_DELETED_POST_NOTICE, presented[2].messageHtml)
        assertNull(presented[1].imageUrl)
        assertNull(presented[2].thumbnailUrl)
        assertTrue(presented[1].isContentRedacted)
        assertFalse(presented[0].isContentRedacted)
    }

    @Test
    fun enabledDeletedContentPreservesOriginalBodyAndMedia() {
        val original = post("2", isIsolated = true)

        val presented = presentCompatPostsForDeletedVisibility(
            listOf(original),
            showDeletedContent = true
        )

        assertEquals(listOf(original), presented)
    }

    @Test
    fun aggregateDeletedResponseNoticeIsNotRenderedAboveTheThread() {
        assertNull(compatThreadNoticeForDisplay("削除された記事が1件あります.見る"))
        assertNull(compatThreadNoticeForDisplay(" 削除された記事が 12 件あります。 "))
        assertEquals(
            "このスレは管理者により削除されました",
            compatThreadNoticeForDisplay("このスレは管理者により削除されました")
        )
    }

    @Test
    fun visibleDeletedPostKeepsOriginalBodyColourAndLimitsRedToTheNotice() {
        val notice = COMPAT_ADMIN_DELETED_POST_NOTICE
        val originalBody = "元の本文"
        val visibleDeleted = post("3", isDeleted = true).copy(
            messageHtml = "$notice\n$originalBody"
        )

        assertFalse(compatPostBodyUsesAlertColor(visibleDeleted))
        assertEquals(
            listOf(CompatDeletedNoticeRange(0, notice.length)),
            compatDeletedNoticeRanges(visibleDeleted, visibleDeleted.messageHtml)
        )
        assertTrue(
            compatDeletedNoticeRanges(visibleDeleted, visibleDeleted.messageHtml)
                .all { range -> range.endExclusive <= visibleDeleted.messageHtml.indexOf(originalBody) }
        )

        val hiddenDeleted = presentCompatPostsForDeletedVisibility(
            listOf(visibleDeleted),
            showDeletedContent = false
        ).single()
        assertTrue(compatPostBodyUsesAlertColor(hiddenDeleted))
    }
}
