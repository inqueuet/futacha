package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatThreadUpdatePresentationTest {
    @Test
    fun closeToastDurationUsesConfiguredValueAndBoundsMalformedInput() {
        assertEquals(7_000L, resolveCompatCloseToastDurationMillis(null))
        assertEquals(7_000L, resolveCompatCloseToastDurationMillis("invalid"))
        assertEquals(0L, resolveCompatCloseToastDurationMillis("0ミリ秒"))
        assertEquals(2_500L, resolveCompatCloseToastDurationMillis("2500ミリ秒"))
        assertEquals(7_000L, resolveCompatCloseToastDurationMillis("999999ミリ秒"))
    }

    @Test
    fun closeToastIsSilentForOneThreadAndShownOnlyForBatchClose() {
        assertFalse(shouldShowCompatCloseToast(closedTabCount = 1, durationMillis = 7_000L))
        assertTrue(shouldShowCompatCloseToast(closedTabCount = 2, durationMillis = 7_000L))
        assertFalse(shouldShowCompatCloseToast(closedTabCount = 2, durationMillis = 0L))
    }

    @Test
    fun autoScrollMatchesReferenceTouchPauseAndBottomReloadPolicy() {
        assertEquals(5_000L, COMPAT_AUTO_SCROLL_TOUCH_PAUSE_MILLIS)
        assertEquals(12_000L, COMPAT_AUTO_SCROLL_RELOAD_WAIT_MILLIS)
        assertEquals(
            CompatAutoScrollAction.SCROLL,
            resolveCompatAutoScrollAction(canScrollForward = true, isDead = false)
        )
        assertEquals(
            CompatAutoScrollAction.WAIT_FOR_RELOAD,
            resolveCompatAutoScrollAction(canScrollForward = false, isDead = false)
        )
        assertEquals(
            CompatAutoScrollAction.STOP_DEAD,
            resolveCompatAutoScrollAction(canScrollForward = false, isDead = true)
        )
    }

    @Test
    fun detectsAppendedRepliesAndMarksTheFirstNewPosition() {
        val previous = snapshot("100", "101")
        val fetched = snapshot("100", "101", "102", "103")

        assertEquals(
            CompatNewReplyNotice(count = 2, firstNewPostPosition = 2),
            detectCompatNewReplyNotice(previous, fetched)
        )
    }

    @Test
    fun doesNotShowNoticeForInitialLoadShorterOrReorderedResponses() {
        val previous = snapshot("100", "101")

        assertNull(detectCompatNewReplyNotice(null, snapshot("100", "101", "102")))
        assertNull(detectCompatNewReplyNotice(previous, snapshot("100")))
        assertNull(detectCompatNewReplyNotice(previous, snapshot("100", "999", "102")))
    }

    @Test
    fun manualRefreshReportsNewRepliesAndNoNewRepliesButRejectsInvalidReplacement() {
        val previous = snapshot("100", "101")

        val appended = detectCompatManualRefreshNotice(previous, snapshot("100", "101", "102"))
        assertEquals("新着レス1件", appended?.message())
        assertEquals(
            CompatManualRefreshNotice.NoNewReplies,
            detectCompatManualRefreshNotice(previous, snapshot("100", "101"))
        )
        assertEquals("新着なし", CompatManualRefreshNotice.NoNewReplies.message())
        assertNull(detectCompatManualRefreshNotice(null, previous))
        assertNull(detectCompatManualRefreshNotice(previous, snapshot("100")))
        assertNull(detectCompatManualRefreshNotice(previous, snapshot("100", "999")))
    }

    @Test
    fun droppedThreadArchiveDoesNotCompeteWithNewReplyToast() {
        val notices = resolveCompatThreadUpdateNotices(
            previous = snapshot("100", "101"),
            fetched = snapshot("100", "101", "102"),
            manual = true,
            committed = true,
            primaryThreadGone = true
        )

        assertNull(notices.newReply)
        assertNull(notices.manualRefresh)
    }

    @Test
    fun liveManualRefreshStillKeepsMarkerAndSingleToast() {
        val notices = resolveCompatThreadUpdateNotices(
            previous = snapshot("100", "101"),
            fetched = snapshot("100", "101", "102"),
            manual = true,
            committed = true,
            primaryThreadGone = false
        )

        assertEquals(1, notices.newReply?.count)
        assertEquals("新着レス1件", notices.manualRefresh?.message())
    }

    @Test
    fun expirationLabelIsRenderedAtTheThreadEndAndDeadThreadsHaveFallback() {
        assertEquals(
            "消滅：08月02:03頃消えます",
            compatThreadFooterLabel(snapshot("100", expiresAtLabel = "08月02:03頃消えます"), isDead = false)
        )
        assertEquals(
            "スレッドは落ちました",
            compatThreadFooterLabel(snapshot("100"), isDead = true)
        )
        assertNull(compatThreadFooterLabel(snapshot("100"), isDead = false))
    }

    @Test
    fun ownDeletionReplacesOnlyTheTargetPostAndSupportsImageOnlyDeletion() {
        val original = snapshot("100", "101").copy(
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = "100",
                    timestamp = "",
                    messageHtml = "op",
                    imageUrl = "https://may.2chan.net/b/src/op.jpg",
                    thumbnailUrl = "https://may.2chan.net/b/thumb/ops.jpg"
                ),
                CompatPostSnapshot(
                    position = 1,
                    postNo = "101",
                    timestamp = "",
                    messageHtml = "reply",
                    imageUrl = "https://may.2chan.net/b/src/reply.jpg",
                    thumbnailUrl = "https://may.2chan.net/b/thumb/replys.jpg"
                )
            )
        )

        val imageDeleted = applyCompatOwnDeletion(original, "101", imageOnly = true, revision = 9)
        assertEquals("reply", imageDeleted?.posts?.get(1)?.messageHtml)
        assertFalse(imageDeleted?.posts?.get(1)?.isDeleted == true)
        assertNull(imageDeleted?.posts?.get(1)?.imageUrl)
        assertEquals(original.posts[0].imageUrl, imageDeleted?.posts?.get(0)?.imageUrl)

        val deleted = applyCompatOwnDeletion(original, "100", imageOnly = false, revision = 10)
        assertTrue(deleted?.posts?.get(0)?.isDeleted == true)
        assertEquals("削除されました", deleted?.posts?.get(0)?.messageHtml)
        assertNull(deleted?.posts?.get(0)?.thumbnailUrl)
        assertNull(applyCompatOwnDeletion(original, "999", imageOnly = false))
    }

    private fun snapshot(
        vararg postNos: String,
        expiresAtLabel: String? = null
    ): CompatThreadSnapshot = CompatThreadSnapshot(
        tabKey = "tab",
        revision = postNos.size.toLong(),
        fetchedAtEpochMillis = postNos.size.toLong(),
        expiresAtLabel = expiresAtLabel,
        posts = postNos.mapIndexed { index, postNo ->
            CompatPostSnapshot(
                position = index,
                postNo = postNo,
                timestamp = "",
                messageHtml = "body"
            )
        }
    )
}
