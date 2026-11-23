package com.valoser.futacha.shared.repository

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedPost
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

    @Test
    fun newsHistoryUsesTheSameLocalOriginalForLegacyAndRecoveredIndexes() = runBlocking {
        val fs = InMemoryFileSystem()
        val repo = SavedThreadRepository(fs, "history")
        val news = "https://nijiurachan.net/b/news/"
        val threadId = "01a1f836-382c-771a-a8e6-cd3b6e8673db"
        val generation = "generation"
        val original = "https://publisher.example/thumb/article.jpg"
        val local = "news/src/article.jpg"
        val post = SavedPost(
            id = "1", order = 0, author = null, subject = null, timestamp = "", messageHtml = "",
            originalImageUrl = original, localImagePath = local, originalVideoUrl = null, localVideoPath = null,
            originalThumbnailUrl = original, localThumbnailPath = null
        )
        val metadata = SavedThreadMetadata(
            threadId = threadId, boardId = "aimoge-news", boardName = "ニュー速", boardUrl = news,
            title = "title", storageId = generation, savedAt = 1, expiresAtLabel = null,
            posts = listOf(post), totalSize = 1
        )
        repo.addThreadToIndex(saved("aimoge-news", generation).copy(threadId = threadId, thumbnailPath = null)).getOrThrow()
        fs.writeBytes("history/$generation/$local", byteArrayOf(1)).getOrThrow()
        fs.writeString("history/$generation/metadata.json", Json.encodeToString(metadata)).getOrThrow()

        assertEquals("/virtual/history/$generation/$local", repo.historyThumbnailPath(threadId, "aimoge-news", news))
        assertEquals(local, repo.recoverSavedThreadMetadata(generation)?.thumbnailPath)
        assertEquals("/virtual/history/$generation/$local", repo.historyThumbnailPath(
            threadId, "other-mode-news", "${news}thread/$threadId"
        ))
        assertNull(repo.historyThumbnailPath(threadId, "aimoge-news", boardUrl))

        val olderGeneration = "generation-with-missing-thumb"
        repo.addThreadToIndex(saved("aimoge-news", olderGeneration).copy(
            threadId = threadId, thumbnailPath = "news/thumb/missing.jpg", savedAt = 2
        )).getOrThrow()
        fs.writeString("history/$olderGeneration/metadata.json", Json.encodeToString(metadata.copy(
            storageId = olderGeneration, savedAt = 2, posts = listOf(post.copy(localThumbnailPath = "news/thumb/missing.jpg"))
        ))).getOrThrow()
        fs.writeBytes("history/$olderGeneration/$local", byteArrayOf(1)).getOrThrow()
        assertEquals("/virtual/history/$olderGeneration/$local", repo.historyThumbnailPath(threadId, "aimoge-news", news))
        fs.delete("history/$olderGeneration/$local").getOrThrow()
        fs.delete("history/$generation/$local").getOrThrow()
        assertNull(repo.historyThumbnailPath(threadId, "aimoge-news", news))
    }

    private fun saved(boardId: String, storageId: String) = SavedThread(
        threadId = "123", boardId = boardId, boardName = "b", title = "title",
        storageId = storageId, thumbnailPath = "thumb/1.jpg", savedAt = 1,
        postCount = 1, imageCount = 1, videoCount = 0, totalSize = 1, status = SaveStatus.COMPLETED
    )
}
