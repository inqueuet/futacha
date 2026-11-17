package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedThreadHistoryThumbnailSupportTest {
    private val boardUrl = "https://may.2chan.net/b/res/123.htm"

    @Test
    fun resolvesCurrentGenerationAndRejectsDeletedThumbnail() = runBlocking {
        val fs = InMemoryFileSystem()
        val repo = SavedThreadRepository(fs, "history")
        val saved = saved("b", "generation-new")
        repo.addThreadToIndex(saved).getOrThrow()
        fs.writeBytes("history/generation-new/thumb/1.jpg", byteArrayOf(1)).getOrThrow()
        assertEquals("/virtual/history/generation-new/thumb/1.jpg", repo.historyThumbnailPath("123", "b", boardUrl))
        fs.delete("history/generation-new/thumb/1.jpg").getOrThrow()
        assertNull(repo.historyThumbnailPath("123", "b", boardUrl))
    }

    @Test
    fun matchesDifferentModeBoardIdsOnlyAfterVerifyingBoardUrl() = runBlocking {
        val fs = InMemoryFileSystem()
        val repo = SavedThreadRepository(fs, "history")
        repo.addThreadToIndex(saved("legacy-id", "generation")).getOrThrow()
        fs.writeBytes("history/generation/thumb/1.jpg", byteArrayOf(1)).getOrThrow()
        val metadata = SavedThreadMetadata(
            threadId = "123", boardId = "legacy-id", boardName = "b", boardUrl = "https://may.2chan.net/b/",
            title = "title", storageId = "generation", savedAt = 1, expiresAtLabel = null,
            posts = emptyList(), totalSize = 1
        )
        fs.writeString("history/generation/metadata.json", Json.encodeToString(metadata)).getOrThrow()
        assertEquals("/virtual/history/generation/thumb/1.jpg", repo.historyThumbnailPath("123", "modern-id", boardUrl))
        assertNull(repo.historyThumbnailPath("123", "modern-id", "https://img.2chan.net/b/res/123.htm"))
    }

    @Test
    fun missingIndexOrThumbnailNeverInventsALocalPath() = runBlocking {
        val repo = SavedThreadRepository(InMemoryFileSystem(), "history")
        assertNull(repo.historyThumbnailPath("123", "b", boardUrl))
        repo.addThreadToIndex(saved("b", "generation").copy(thumbnailPath = null)).getOrThrow()
        assertNull(repo.historyThumbnailPath("123", "b", boardUrl))
    }

    @Test
    fun doesNotReadOutsideSavedGeneration() = runBlocking {
        val fs = InMemoryFileSystem()
        val repo = SavedThreadRepository(fs, "history")
        fs.writeBytes("history/secret.jpg", byteArrayOf(1)).getOrThrow()
        repo.addThreadToIndex(saved("b", "generation").copy(thumbnailPath = "../secret.jpg")).getOrThrow()
        assertNull(repo.historyThumbnailPath("123", "b", boardUrl))
    }

    private fun saved(boardId: String, storageId: String) = SavedThread(
        threadId = "123", boardId = boardId, boardName = "b", title = "title",
        storageId = storageId, thumbnailPath = "thumb/1.jpg", savedAt = 1,
        postCount = 1, imageCount = 1, videoCount = 0, totalSize = 1, status = SaveStatus.COMPLETED
    )
}
