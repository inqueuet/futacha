package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatToolbarUpdateBadgeTest {
    @Test
    fun tabButtonShowsUpdateOnlyForUnreadLiveThreads() {
        val read = tab("read", replyCount = 10, checkedReplyCount = 10)
        val unread = tab("unread", replyCount = 12, checkedReplyCount = 10)
        val deadUnread = tab("dead", replyCount = 15, checkedReplyCount = 10).copy(isDead = true)

        assertFalse(hasCompatTabToolbarUpdate(emptyList()))
        assertFalse(hasCompatTabToolbarUpdate(listOf(read, deadUnread)))
        assertTrue(hasCompatTabToolbarUpdate(listOf(read, unread)))
    }

    private fun tab(key: String, replyCount: Int, checkedReplyCount: Int) = CompatTab(
        key = key,
        canonicalUrl = "https://may.2chan.net/b/res/$key.htm",
        originalUrl = "https://may.2chan.net/b/res/$key.htm",
        boardKey = "may-b",
        boardName = "二次元裏",
        threadNo = key,
        title = key,
        replyCount = replyCount,
        checkedReplyCount = checkedReplyCount,
        insertedAtEpochMillis = 1L,
        contentUpdatedAtEpochMillis = 1L
    )
}
