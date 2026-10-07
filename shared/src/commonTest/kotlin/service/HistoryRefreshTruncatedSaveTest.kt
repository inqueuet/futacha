package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.resolveHistoryReplyCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryRefreshTruncatedSaveTest {
    private fun page(posts: Int, truncated: Boolean) = ThreadPage(
        threadId = "1",
        boardTitle = null,
        expiresAtLabel = null,
        deletedNotice = null,
        posts = (1..posts).map {
            Post(
                id = it.toString(), author = null, subject = null, timestamp = "t",
                messageHtml = "body", imageUrl = null, thumbnailUrl = null
            )
        },
        isTruncated = truncated,
        truncationReason = if (truncated) "limit" else null
    )

    private fun saved(postCount: Int) = SavedThread(
        threadId = "1", boardId = "b", boardName = "board", title = "t", storageId = "s",
        thumbnailPath = null, savedAt = 1L, postCount = postCount, imageCount = 0, videoCount = 0,
        totalSize = 1L, status = SaveStatus.COMPLETED
    )

    @Test
    fun truncatedPageSmallerThanTheSavedGenerationDoesNotReplaceIt() {
        assertTrue(page(posts = 500, truncated = true).replacesFullerAutoSave(saved(postCount = 900)))
        // The history count keeps the larger previous value, so the entry is not "changed" either.
        assertEquals(900, page(posts = 500, truncated = true).resolveHistoryReplyCount(900))
    }

    @Test
    fun completeOrLargerPagesAndFirstSavesStillSave() {
        assertFalse(page(posts = 500, truncated = false).replacesFullerAutoSave(saved(postCount = 900)))
        assertFalse(page(posts = 950, truncated = true).replacesFullerAutoSave(saved(postCount = 900)))
        assertFalse(page(posts = 500, truncated = true).replacesFullerAutoSave(null))
    }
}
