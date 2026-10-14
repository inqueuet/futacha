package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.postDeletionKind
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatDeletedPostPresentationTest {
    @Test
    fun hidingRowsPreservesNumbersAndPositionsWithoutMutatingSource() = runBlocking {
        val rows = listOf(post("1"), post("2", isDeleted = true), post("3", isIsolated = true), post("4"))
        val hidden = presentCompatPostsForDeletedVisibilityOffMain(rows, showDeletedContent = false, hideDeletedRows = true)
        assertEquals(listOf("1", "4"), hidden.map { it.postNo })
        assertEquals(listOf(1, 4), hidden.map { it.position })
        assertEquals(4, rows.size)
        assertEquals("元の本文", rows[1].messageHtml)
        assertEquals(rows, presentCompatPostsForDeletedVisibilityOffMain(rows, showDeletedContent = true))
    }

    @Test
    fun deletionSettingKeepsLegacyValuesAndOffersIndependentRowHiding() {
        val entry = CompatSettingEntry("削除されたレスを表示", "OFF", preferenceKey = "threadAdminDeleteShow")
        assertFalse(compatIsBooleanPreference("thread", entry))
        assertEquals(listOf("通知のみ", "本文表示", "レス非表示"), compatPreferenceOptions("thread", entry))
        for ((stored, label) in listOf("OFF" to "通知のみ", "ON" to "本文表示", "HIDE" to "レス非表示")) {
            assertEquals(label, compatPreferenceDisplayValue(entry.preferenceKey, stored))
            assertEquals(stored, compatPreferenceStoredValue(entry.preferenceKey, label))
        }
    }
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
        assertEquals("削除されました", presented[2].messageHtml)
        assertNull(presented[1].imageUrl)
        assertNull(presented[2].thumbnailUrl)
        assertTrue(presented[1].isContentRedacted)
        assertFalse(presented[0].isContentRedacted)
    }

    @Test
    fun authorAndAdministratorNoticesStayRedAndKeepTheirActualKindWhenHidden() {
        listOf("書き込みをした人によって削除されました", "管理者によって削除されました").forEach { notice ->
            val original = post("3", isDeleted = true).copy(messageHtml = "$notice\n元の本文")
            assertEquals(listOf(CompatDeletedNoticeRange(0, notice.length)), compatDeletedNoticeRanges(original, original.messageHtml))
            assertEquals(notice, presentCompatPostsForDeletedVisibility(listOf(original), false).single().messageHtml)
            assertFalse(compatPostBodyUsesAlertColor(original))
        }
    }

    @Test
    fun deletionKindMatchesModelAndUnchangedListsKeepTheirInstance() {
        val notice = "管理者によって削除されました"
        val rows = listOf(
            post("1"),
            post("2", isIsolated = true),
            post("3", isDeleted = true).copy(messageHtml = "$notice<br>元の本文"),
            post("4", isDeleted = true, isIsolated = true),
            post("5").copy(messageHtml = notice)
        )
        rows.forEach { row ->
            assertEquals(
                postDeletionKind(
                    row.messageHtml.toCompatPlainText(),
                    row.isDeleted,
                    row.isIsolated
                ),
                compatPostDeletionKind(row)
            )
        }
        val plain = listOf(post("1"), post("2"))
        assertTrue(presentCompatPostsForDeletedVisibility(plain, showDeletedContent = false) === plain)
        runBlocking {
            assertTrue(presentCompatPostsForDeletedVisibilityOffMain(plain, false) === plain)
            assertEquals(
                presentCompatPostsForDeletedVisibility(rows, false),
                presentCompatPostsForDeletedVisibilityOffMain(rows, false)
            )
        }
    }

    @Test
    fun quotedDeletionNoticesDoNotBecomeRed() {
        val original = post("3", isDeleted = true).copy(messageHtml = ">管理者によって削除されました\n元の本文")
        assertTrue(compatDeletedNoticeRanges(original, original.messageHtml).isEmpty())
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
